package com.tuowei.erp.system.readiness.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessEvidenceMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessRunMapper;
import com.tuowei.erp.system.readiness.model.ReadinessEvidenceEntity;
import com.tuowei.erp.system.readiness.model.ReadinessItemEntity;
import com.tuowei.erp.system.readiness.model.ReadinessRunEntity;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunDetailResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import com.tuowei.erp.system.readiness.web.ReadinessRunResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReadinessQueryService {

    private final ReadinessRunMapper runMapper;
    private final ReadinessItemMapper itemMapper;
    private final ReadinessEvidenceMapper evidenceMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ReadinessQueryService(
            ReadinessRunMapper runMapper,
            ReadinessItemMapper itemMapper,
            ReadinessEvidenceMapper evidenceMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.runMapper = runMapper;
        this.itemMapper = itemMapper;
        this.evidenceMapper = evidenceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadinessRunResponse> listRuns(ReadinessRunPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReadinessRunPageQuery safeQuery = query == null ? new ReadinessRunPageQuery() : query;
        Page<ReadinessRunEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ReadinessRunEntity> result = runMapper.selectPage(page, buildRunQuery(safeQuery, audit));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toRunResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public ReadinessRunDetailResponse detail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReadinessRunEntity run = requireRun(id, audit);
        List<ReadinessItemEntity> items = itemMapper.selectList(baseItemQuery(audit)
                .eq(ReadinessItemEntity::getRunId, run.getId())
                .orderByAsc(ReadinessItemEntity::getCreatedTime)
                .orderByAsc(ReadinessItemEntity::getId));
        Map<Long, List<ReadinessEvidenceResponse>> evidence = loadEvidenceByItemId(run, items, audit);
        return new ReadinessRunDetailResponse(
                toRunResponse(run),
                items.stream()
                        .map(item -> toItemResponse(item, evidence.getOrDefault(item.getId(), List.of())))
                        .toList()
        );
    }

    ReadinessRunEntity requireRun(Long id, AuditMetadata audit) {
        ReadinessRunEntity run = runMapper.selectOne(baseRunQuery(audit)
                .eq(ReadinessRunEntity::getId, id)
                .last("limit 1"));
        if (run == null) {
            throw new IllegalArgumentException("验收运行单不存在");
        }
        return run;
    }

    ReadinessItemEntity requireItem(Long id, AuditMetadata audit) {
        ReadinessItemEntity item = itemMapper.selectOne(baseItemQuery(audit)
                .eq(ReadinessItemEntity::getId, id)
                .last("limit 1"));
        if (item == null) {
            throw new IllegalArgumentException("验收项不存在");
        }
        return item;
    }

    List<ReadinessEvidenceResponse> loadEvidenceForItem(ReadinessItemEntity item, AuditMetadata audit) {
        return evidenceMapper.selectList(baseEvidenceQuery(audit)
                        .eq(ReadinessEvidenceEntity::getRunId, item.getRunId())
                        .eq(ReadinessEvidenceEntity::getItemId, item.getId())
                        .orderByAsc(ReadinessEvidenceEntity::getRecordedTime)
                        .orderByAsc(ReadinessEvidenceEntity::getId))
                .stream()
                .map(this::toEvidenceResponse)
                .toList();
    }

    ReadinessRunResponse toRunResponse(ReadinessRunEntity entity) {
        return new ReadinessRunResponse(
                entity.getId(),
                entity.getRunNo(),
                entity.getReleaseCommit(),
                entity.getReleaseVersion(),
                entity.getEnvironment(),
                entity.getDatabaseInstance(),
                entity.getRedisInstance(),
                entity.getDockerProfile(),
                entity.getStatus(),
                entity.getDecision(),
                entity.getDecisionComment(),
                entity.getRemark(),
                entity.getStartedBy(),
                entity.getStartedTime(),
                entity.getDecidedBy(),
                entity.getDecidedTime(),
                entity.getCreatedTime()
        );
    }

    ReadinessItemResponse toItemResponse(
            ReadinessItemEntity entity,
            List<ReadinessEvidenceResponse> evidence
    ) {
        return new ReadinessItemResponse(
                entity.getId(),
                entity.getRunId(),
                entity.getItemCode(),
                entity.getItemName(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getExpectedResult(),
                entity.getActualResult(),
                entity.getFailureReason(),
                entity.getExecutedBy(),
                entity.getExecutedTime(),
                entity.getCreatedTime(),
                evidence
        );
    }

    ReadinessEvidenceResponse toEvidenceResponse(ReadinessEvidenceEntity entity) {
        return new ReadinessEvidenceResponse(
                entity.getId(),
                entity.getRunId(),
                entity.getItemId(),
                entity.getEvidenceType(),
                entity.getRequestMethod(),
                entity.getRequestUri(),
                entity.getHttpStatus(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getSummary(),
                entity.getDetail(),
                entity.getAttachmentBusinessType(),
                entity.getAttachmentBusinessId(),
                entity.getRecordedBy(),
                entity.getRecordedTime()
        );
    }

    private LambdaQueryWrapper<ReadinessRunEntity> buildRunQuery(
            ReadinessRunPageQuery query,
            AuditMetadata audit
    ) {
        LambdaQueryWrapper<ReadinessRunEntity> wrapper = baseRunQuery(audit);
        String releaseCommit = normalizeNullable(query.getReleaseCommit());
        if (StringUtils.hasText(releaseCommit)) {
            wrapper.eq(ReadinessRunEntity::getReleaseCommit, releaseCommit);
        }
        String environment = normalizeCodeNullable(query.getEnvironment());
        if (StringUtils.hasText(environment)) {
            wrapper.eq(ReadinessRunEntity::getEnvironment, environment);
        }
        String status = normalizeCodeNullable(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ReadinessRunEntity::getStatus, status);
        }
        String decision = normalizeCodeNullable(query.getDecision());
        if (StringUtils.hasText(decision)) {
            wrapper.eq(ReadinessRunEntity::getDecision, decision);
        }
        if (query.getCreatedTimeFrom() != null) {
            wrapper.ge(ReadinessRunEntity::getCreatedTime, query.getCreatedTimeFrom());
        }
        if (query.getCreatedTimeTo() != null) {
            wrapper.le(ReadinessRunEntity::getCreatedTime, query.getCreatedTimeTo());
        }
        return wrapper.orderByDesc(ReadinessRunEntity::getCreatedTime).orderByDesc(ReadinessRunEntity::getId);
    }

    private Map<Long, List<ReadinessEvidenceResponse>> loadEvidenceByItemId(
            ReadinessRunEntity run,
            List<ReadinessItemEntity> items,
            AuditMetadata audit
    ) {
        List<Long> itemIds = items.stream().map(ReadinessItemEntity::getId).toList();
        if (itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return evidenceMapper.selectList(baseEvidenceQuery(audit)
                        .eq(ReadinessEvidenceEntity::getRunId, run.getId())
                        .in(ReadinessEvidenceEntity::getItemId, itemIds)
                        .orderByAsc(ReadinessEvidenceEntity::getRecordedTime)
                        .orderByAsc(ReadinessEvidenceEntity::getId))
                .stream()
                .map(this::toEvidenceResponse)
                .collect(Collectors.groupingBy(ReadinessEvidenceResponse::itemId));
    }

    private LambdaQueryWrapper<ReadinessRunEntity> baseRunQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReadinessRunEntity>()
                .eq(ReadinessRunEntity::getCompanyId, audit.companyId())
                .eq(ReadinessRunEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessRunEntity::getDeletedFlag, 0);
    }

    private LambdaQueryWrapper<ReadinessItemEntity> baseItemQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReadinessItemEntity>()
                .eq(ReadinessItemEntity::getCompanyId, audit.companyId())
                .eq(ReadinessItemEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessItemEntity::getDeletedFlag, 0);
    }

    private LambdaQueryWrapper<ReadinessEvidenceEntity> baseEvidenceQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReadinessEvidenceEntity>()
                .eq(ReadinessEvidenceEntity::getCompanyId, audit.companyId())
                .eq(ReadinessEvidenceEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessEvidenceEntity::getDeletedFlag, 0);
    }

    private String normalizeCodeNullable(String value) {
        String normalized = normalizeNullable(value);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : null;
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
