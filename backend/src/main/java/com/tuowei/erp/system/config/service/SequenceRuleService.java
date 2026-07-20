package com.tuowei.erp.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class SequenceRuleService {

    private final SequenceRuleMapper sequenceRuleMapper;
    private final SequenceCounterMapper sequenceCounterMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    public SequenceRuleService(
            SequenceRuleMapper sequenceRuleMapper,
            SequenceCounterMapper sequenceCounterMapper,
            AuditMetadataFactory auditMetadataFactory,
            SystemLogService systemLogService,
            ObjectMapper objectMapper
    ) {
        this.sequenceRuleMapper = sequenceRuleMapper;
        this.sequenceCounterMapper = sequenceCounterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.systemLogService = systemLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SequenceRuleResponse create(SequenceRuleCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        long currentValue = request.currentValue() == null ? 0L : request.currentValue();
        validateRuleDefinition(request.datePattern(), request.seqLength(), currentValue);

        SequenceRuleEntity entity = new SequenceRuleEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setBizType(request.bizType());
        entity.setPrefix(request.prefix());
        entity.setDatePattern(request.datePattern());
        entity.setSeqLength(request.seqLength());
        entity.setCurrentValue(currentValue);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        sequenceRuleMapper.insert(entity);
        recordAudit("CREATE", entity, toAuditSnapshot(entity), "创建编号规则");
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<SequenceRuleResponse> list(SequenceRulePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        SequenceRulePageQuery safeQuery = query == null ? new SequenceRulePageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());

        Page<SequenceRuleEntity> page = new Page<>(pageNo, pageSize);
        Page<SequenceRuleEntity> result = sequenceRuleMapper.selectPage(
                page,
                buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status)
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SequenceRuleResponse getById(Long id) {
        return toResponse(requireSequenceRule(id));
    }

    @Transactional
    public SequenceRuleResponse update(Long id, SequenceRuleUpdateRequest request) {
        SequenceRuleEntity entity = requireSequenceRule(id);
        SequenceRuleAuditSnapshot before = snapshot(entity);
        AuditMetadata audit = auditMetadataFactory.current();
        long currentValue = request.currentValue() == null ? 0L : request.currentValue();
        guardGeneratedCounterRules(entity, request, currentValue);
        validateRuleDefinition(request.datePattern(), request.seqLength(), currentValue);
        entity.setPrefix(request.prefix());
        entity.setDatePattern(request.datePattern());
        entity.setSeqLength(request.seqLength());
        entity.setCurrentValue(currentValue);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(sequenceRuleMapper.updateById(entity), "编号规则已被其他操作修改，请刷新后重试");
        recordAudit("UPDATE", entity, toChangeSnapshot(before, snapshot(entity)), "更新编号规则");
        return toResponse(entity);
    }

    private void guardGeneratedCounterRules(SequenceRuleEntity entity, SequenceRuleUpdateRequest request, long requestedCurrentValue) {
        Long maxCounterValue = sequenceCounterMapper.selectMaxCurrentValue(
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getBizType()
        );
        if (maxCounterValue == null) {
            return;
        }
        if (!entity.getDatePattern().equals(request.datePattern())) {
            throw new IllegalArgumentException("已产生编号的规则不能修改日期格式");
        }
        if (requestedCurrentValue < maxCounterValue) {
            throw new IllegalArgumentException("currentValue不能小于已产生的最大流水");
        }
        if (digitCount(maxCounterValue) > request.seqLength()) {
            throw new IllegalArgumentException("seqLength不能小于已产生的最大流水位数");
        }
    }

    private void validateRuleDefinition(String datePattern, Integer seqLength, long currentValue) {
        try {
            DateTimeFormatter.ofPattern(datePattern);
        } catch (java.lang.IllegalArgumentException ex) {
            throw new IllegalArgumentException("datePattern不是有效的日期格式");
        }
        if (digitCount(currentValue) > seqLength) {
            throw new IllegalArgumentException("seqLength不能小于当前流水位数");
        }
    }

    private int digitCount(long value) {
        return Long.toString(Math.abs(value)).length();
    }

    @Transactional
    public SequenceRuleResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public SequenceRuleResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private SequenceRuleResponse toResponse(SequenceRuleEntity entity) {
        return new SequenceRuleResponse(
                entity.getId(),
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getBizType(),
                entity.getPrefix(),
                entity.getDatePattern(),
                entity.getSeqLength(),
                entity.getCurrentValue(),
                entity.getStatus()
        );
    }

    private SequenceRuleEntity requireSequenceRule(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        SequenceRuleEntity entity = sequenceRuleMapper.selectOne(new LambdaQueryWrapper<SequenceRuleEntity>()
                .eq(SequenceRuleEntity::getId, id)
                .eq(SequenceRuleEntity::getCompanyId, audit.companyId())
                .eq(SequenceRuleEntity::getAccountBookId, audit.accountBookId()));
        if (entity == null) {
            throw new IllegalArgumentException("编号规则不存在");
        }
        return entity;
    }

    private SequenceRuleResponse updateStatus(Long id, String status) {
        SequenceRuleEntity entity = requireSequenceRule(id);
        SequenceRuleAuditSnapshot before = snapshot(entity);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(sequenceRuleMapper.updateById(entity), "编号规则已被其他操作修改，请刷新后重试");
        recordAudit(statusAuditAction(status), entity, toChangeSnapshot(before, snapshot(entity)), "更新编号规则状态");
        return toResponse(entity);
    }

    private void recordAudit(String action, SequenceRuleEntity entity, String snapshotJson, String message) {
        AuditMetadata audit = auditMetadataFactory.current();
        systemLogService.recordAudit(
                "CONFIG",
                "SEQUENCE_RULE",
                entity.getId(),
                entity.getBizType(),
                action,
                audit.userId(),
                null,
                snapshotJson,
                message,
                audit.now()
        );
    }

    private String statusAuditAction(String status) {
        return "ACTIVE".equals(status) ? "ENABLE" : "DISABLE";
    }

    private String toAuditSnapshot(SequenceRuleEntity entity) {
        return writeSnapshot(snapshot(entity));
    }

    private String toChangeSnapshot(SequenceRuleAuditSnapshot before, SequenceRuleAuditSnapshot after) {
        return writeSnapshot(new SequenceRuleChangeAuditSnapshot(before, after));
    }

    private String writeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("编号规则审计快照序列化失败", ex);
        }
    }

    private SequenceRuleAuditSnapshot snapshot(SequenceRuleEntity entity) {
        return new SequenceRuleAuditSnapshot(
                entity.getId(),
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getBizType(),
                entity.getPrefix(),
                entity.getDatePattern(),
                entity.getSeqLength(),
                entity.getCurrentValue(),
                entity.getStatus()
        );
    }

    private record SequenceRuleAuditSnapshot(
            Long id,
            Long companyId,
            Long accountBookId,
            String bizType,
            String prefix,
            String datePattern,
            Integer seqLength,
            Long currentValue,
            String status
    ) {
    }

    private record SequenceRuleChangeAuditSnapshot(
            SequenceRuleAuditSnapshot before,
            SequenceRuleAuditSnapshot after
    ) {
    }

    private LambdaQueryWrapper<SequenceRuleEntity> buildListQuery(Long companyId, Long accountBookId, String keyword, String status) {
        LambdaQueryWrapper<SequenceRuleEntity> wrapper = new LambdaQueryWrapper<SequenceRuleEntity>()
                .eq(SequenceRuleEntity::getCompanyId, companyId)
                .eq(SequenceRuleEntity::getAccountBookId, accountBookId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SequenceRuleEntity::getBizType, keyword)
                    .or()
                    .like(SequenceRuleEntity::getPrefix, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SequenceRuleEntity::getStatus, status);
        }
        return wrapper.orderByAsc(SequenceRuleEntity::getBizType);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

}
