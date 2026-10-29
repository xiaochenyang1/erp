package com.tuowei.erp.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Read-side sequence-rule queries and tenant-scoped entity lookup. */
@Service
public class SequenceRuleQueryService {

    private final SequenceRuleMapper sequenceRuleMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public SequenceRuleQueryService(
            SequenceRuleMapper sequenceRuleMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.sequenceRuleMapper = sequenceRuleMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<SequenceRuleResponse> list(SequenceRulePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        SequenceRulePageQuery safeQuery = query == null ? new SequenceRulePageQuery() : query;
        Page<SequenceRuleEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<SequenceRuleEntity> result = sequenceRuleMapper.selectPage(page, buildListQuery(
                audit.companyId(), audit.accountBookId(), normalizeNullableText(safeQuery.getKeyword()),
                normalizeStatus(safeQuery.getStatus())
        ));
        return new PageResponse<>(
                result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SequenceRuleResponse getById(Long id) {
        return toResponse(requireSequenceRule(id));
    }

    SequenceRuleEntity requireSequenceRule(Long id) {
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

    SequenceRuleResponse toResponse(SequenceRuleEntity entity) {
        return new SequenceRuleResponse(
                entity.getId(), entity.getCompanyId(), entity.getAccountBookId(), entity.getBizType(),
                entity.getPrefix(), entity.getDatePattern(), entity.getSeqLength(), entity.getCurrentValue(),
                entity.getStatus()
        );
    }

    private LambdaQueryWrapper<SequenceRuleEntity> buildListQuery(
            Long companyId, Long accountBookId, String keyword, String status
    ) {
        LambdaQueryWrapper<SequenceRuleEntity> wrapper = new LambdaQueryWrapper<SequenceRuleEntity>()
                .eq(SequenceRuleEntity::getCompanyId, companyId)
                .eq(SequenceRuleEntity::getAccountBookId, accountBookId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SequenceRuleEntity::getBizType, keyword)
                    .or().like(SequenceRuleEntity::getPrefix, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SequenceRuleEntity::getStatus, status);
        }
        return wrapper.orderByAsc(SequenceRuleEntity::getBizType);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }
}
