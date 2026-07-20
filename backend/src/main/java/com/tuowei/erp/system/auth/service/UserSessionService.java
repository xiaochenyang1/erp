package com.tuowei.erp.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.auth.mapper.RefreshTokenMapper;
import com.tuowei.erp.system.auth.model.RefreshTokenEntity;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.auth.web.UserSessionResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserSessionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";

    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final Clock clock;

    public UserSessionService(
            RefreshTokenMapper refreshTokenMapper,
            UserMapper userMapper,
            AuditMetadataFactory auditMetadataFactory,
            Clock clock
    ) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.userMapper = userMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSessionResponse> list(UserSessionPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        UserSessionPageQuery safeQuery = query == null ? new UserSessionPageQuery() : query;
        Page<RefreshTokenEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        Page<RefreshTokenEntity> result = refreshTokenMapper.selectPage(page, buildQuery(audit, safeQuery));
        Map<Long, UserEntity> users = loadUsers(result.getRecords(), audit.companyId(), audit.accountBookId());

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(entity -> toResponse(entity, users.get(entity.getUserId()))).toList()
        );
    }

    @Transactional
    public void revokeSession(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        RefreshTokenEntity entity = refreshTokenMapper.selectById(id);
        if (entity == null
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        entity.setStatus(STATUS_REVOKED);
        entity.setRevokedAt(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(refreshTokenMapper.updateById(entity), "会话已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        AuditMetadata audit = auditMetadataFactory.current();
        UserEntity user = userMapper.selectById(userId);
        if (user == null
                || user.getDeletedFlag() == null
                || user.getDeletedFlag() != 0
                || !audit.companyId().equals(user.getCompanyId())
                || !audit.accountBookId().equals(user.getAccountBookId())) {
            throw new IllegalArgumentException("用户不存在");
        }
        refreshTokenMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getCompanyId, audit.companyId())
                .eq(RefreshTokenEntity::getAccountBookId, audit.accountBookId())
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getStatus, STATUS_ACTIVE)
                .set(RefreshTokenEntity::getStatus, STATUS_REVOKED)
                .set(RefreshTokenEntity::getRevokedAt, audit.now())
                .set(RefreshTokenEntity::getUpdatedBy, audit.userId())
                .set(RefreshTokenEntity::getUpdatedTime, audit.now()));
    }

    private LambdaQueryWrapper<RefreshTokenEntity> buildQuery(AuditMetadata audit, UserSessionPageQuery query) {
        LambdaQueryWrapper<RefreshTokenEntity> wrapper = new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getCompanyId, audit.companyId())
                .eq(RefreshTokenEntity::getAccountBookId, audit.accountBookId());

        if (query.getUserId() != null) {
            wrapper.eq(RefreshTokenEntity::getUserId, query.getUserId());
        }
        String status = normalizeNullable(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(RefreshTokenEntity::getStatus, status.toUpperCase(Locale.ROOT));
        }
        if (query.getIssuedAtFrom() != null) {
            wrapper.ge(RefreshTokenEntity::getIssuedAt, query.getIssuedAtFrom());
        }
        if (query.getIssuedAtTo() != null) {
            wrapper.le(RefreshTokenEntity::getIssuedAt, query.getIssuedAtTo());
        }
        List<Long> matchedUserIds = findUserIds(audit.companyId(), audit.accountBookId(), query.getUsername());
        if (matchedUserIds != null) {
            if (matchedUserIds.isEmpty()) {
                wrapper.eq(RefreshTokenEntity::getUserId, -1L);
            } else {
                wrapper.in(RefreshTokenEntity::getUserId, matchedUserIds);
            }
        }

        return wrapper.orderByDesc(RefreshTokenEntity::getIssuedAt).orderByDesc(RefreshTokenEntity::getId);
    }

    private List<Long> findUserIds(Long companyId, Long accountBookId, String username) {
        String normalized = normalizeNullable(username);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getCompanyId, companyId)
                        .eq(UserEntity::getAccountBookId, accountBookId)
                        .eq(UserEntity::getDeletedFlag, 0)
                        .like(UserEntity::getUsername, normalized))
                .stream()
                .map(UserEntity::getId)
                .toList();
    }

    private Map<Long, UserEntity> loadUsers(List<RefreshTokenEntity> sessions, Long companyId, Long accountBookId) {
        List<Long> userIds = sessions.stream()
                .map(RefreshTokenEntity::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getCompanyId, companyId)
                        .eq(UserEntity::getAccountBookId, accountBookId)
                        .in(UserEntity::getId, userIds))
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private UserSessionResponse toResponse(RefreshTokenEntity entity, UserEntity user) {
        return new UserSessionResponse(
                entity.getId(),
                entity.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getRealName(),
                entity.getStatus(),
                entity.getLoginIp(),
                entity.getUserAgent(),
                entity.getIssuedAt(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
