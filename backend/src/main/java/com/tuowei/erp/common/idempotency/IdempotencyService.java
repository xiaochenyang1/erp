package com.tuowei.erp.common.idempotency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tuowei.erp.common.config.IdempotencyProperties;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.idempotency.mapper.IdempotencyRequestMapper;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class IdempotencyService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_PATH_LENGTH = 512;

    private final IdempotencyRequestMapper mapper;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyRequestMapper mapper,
            IdempotencyProperties properties,
            Clock clock
    ) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public int maxReplayBodyBytes() {
        return properties.maxReplayBodyBytes();
    }

    public int maxRequestBodyBytes() {
        return properties.maxRequestBodyBytes();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DuplicateKeyException.class)
    public BeginResult begin(
            ErpPrincipal principal,
            String idempotencyKey,
            String requestMethod,
            String requestPath,
            String requestBodyHash
    ) {
        String normalizedKey = normalizeKey(idempotencyKey);
        String normalizedMethod = normalizeMethod(requestMethod);
        String normalizedPath = normalizePath(requestPath);
        LocalDateTime now = LocalDateTime.now(clock);
        deleteExpired(principal.companyId(), principal.accountBookId(), now);

        IdempotencyRequestEntity existing = find(
                principal.companyId(),
                principal.accountBookId(),
                principal.userId(),
                normalizedMethod,
                normalizedPath,
                normalizedKey
        );
        if (existing != null) {
            return resolveExisting(existing, requestBodyHash, now);
        }

        IdempotencyRequestEntity entity = new IdempotencyRequestEntity();
        entity.setCompanyId(principal.companyId());
        entity.setAccountBookId(principal.accountBookId());
        entity.setUserId(principal.userId());
        entity.setIdempotencyKey(normalizedKey);
        entity.setRequestMethod(normalizedMethod);
        entity.setRequestPath(normalizedPath);
        entity.setRequestBodyHash(requestBodyHash);
        entity.setStatus(STATUS_PROCESSING);
        entity.setExpiresAt(now.plusSeconds(properties.ttlSeconds()));
        entity.setCreatedBy(principal.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(principal.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        try {
            if (mapper.insert(entity) != 1) {
                throw new IllegalStateException("保存幂等请求失败");
            }
            return BeginResult.proceed(entity.getId());
        } catch (DuplicateKeyException ex) {
            IdempotencyRequestEntity duplicated = find(
                    principal.companyId(),
                    principal.accountBookId(),
                    principal.userId(),
                    normalizedMethod,
                    normalizedPath,
                    normalizedKey
            );
            if (duplicated == null) {
                throw ex;
            }
            return resolveExisting(duplicated, requestBodyHash, now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, int responseStatus, String responseContentType, String responseBody) {
        LocalDateTime now = LocalDateTime.now(clock);
        mapper.update(null, new LambdaUpdateWrapper<IdempotencyRequestEntity>()
                .eq(IdempotencyRequestEntity::getId, id)
                .eq(IdempotencyRequestEntity::getStatus, STATUS_PROCESSING)
                .set(IdempotencyRequestEntity::getStatus, STATUS_COMPLETED)
                .set(IdempotencyRequestEntity::getResponseStatus, responseStatus)
                .set(IdempotencyRequestEntity::getResponseContentType, responseContentType)
                .set(IdempotencyRequestEntity::getResponseBody, responseBody)
                .set(IdempotencyRequestEntity::getUpdatedTime, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(Long id) {
        mapper.delete(new LambdaQueryWrapper<IdempotencyRequestEntity>()
                .eq(IdempotencyRequestEntity::getId, id)
                .eq(IdempotencyRequestEntity::getStatus, STATUS_PROCESSING));
    }

    private BeginResult resolveExisting(
            IdempotencyRequestEntity existing,
            String requestBodyHash,
            LocalDateTime now
    ) {
        if (existing.getExpiresAt() != null && !existing.getExpiresAt().isAfter(now)) {
            mapper.deleteById(existing.getId());
            throw new BusinessConflictException("Idempotency-Key 已过期，请重新生成后再提交");
        }
        if (!Objects.equals(existing.getRequestBodyHash(), requestBodyHash)) {
            throw new BusinessConflictException("Idempotency-Key 已用于不同请求，请重新生成后再提交");
        }
        if (STATUS_COMPLETED.equals(existing.getStatus())) {
            return BeginResult.replay(
                    existing.getResponseStatus(),
                    existing.getResponseContentType(),
                    existing.getResponseBody()
            );
        }
        throw new BusinessConflictException("请求正在处理中，请稍后重试");
    }

    private IdempotencyRequestEntity find(
            Long companyId,
            Long accountBookId,
            Long userId,
            String method,
            String path,
            String key
    ) {
        return mapper.selectOne(new LambdaQueryWrapper<IdempotencyRequestEntity>()
                .eq(IdempotencyRequestEntity::getCompanyId, companyId)
                .eq(IdempotencyRequestEntity::getAccountBookId, accountBookId)
                .eq(IdempotencyRequestEntity::getUserId, userId)
                .eq(IdempotencyRequestEntity::getRequestMethod, method)
                .eq(IdempotencyRequestEntity::getRequestPath, path)
                .eq(IdempotencyRequestEntity::getIdempotencyKey, key));
    }

    private void deleteExpired(Long companyId, Long accountBookId, LocalDateTime now) {
        mapper.delete(new LambdaQueryWrapper<IdempotencyRequestEntity>()
                .eq(IdempotencyRequestEntity::getCompanyId, companyId)
                .eq(IdempotencyRequestEntity::getAccountBookId, accountBookId)
                .lt(IdempotencyRequestEntity::getExpiresAt, now));
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Idempotency-Key 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key 长度不能超过128");
        }
        return normalized;
    }

    private String normalizeMethod(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请求方法不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请求路径不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("请求路径长度不能超过512");
        }
        return normalized;
    }

    public record BeginResult(
            boolean replay,
            Long requestId,
            Integer responseStatus,
            String responseContentType,
            String responseBody
    ) {

        static BeginResult proceed(Long requestId) {
            return new BeginResult(false, requestId, null, null, null);
        }

        static BeginResult replay(Integer responseStatus, String responseContentType, String responseBody) {
            return new BeginResult(true, null, responseStatus, responseContentType, responseBody);
        }
    }
}
