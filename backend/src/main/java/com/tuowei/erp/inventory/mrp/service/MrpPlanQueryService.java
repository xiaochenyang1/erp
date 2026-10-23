package com.tuowei.erp.inventory.mrp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunLineMapper;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunMapper;
import com.tuowei.erp.inventory.mrp.model.MrpRunEntity;
import com.tuowei.erp.inventory.mrp.model.MrpRunLineEntity;
import com.tuowei.erp.inventory.mrp.web.MrpRunPageQuery;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.inventory.mrp.web.MrpRunSummaryResponse;
import com.tuowei.erp.inventory.mrp.web.MrpSuggestionLineResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Read-side tenant guards, line hydration and response mapping for persisted MRP runs. */
@Service
@NativeSqlTenantScoped("MRP query scoped by current tenant")
public class MrpPlanQueryService {

    private static final String TYPE_PRODUCTION = "PRODUCTION";

    private final JdbcTemplate jdbcTemplate;
    private final AuditMetadataFactory auditMetadataFactory;
    private final MrpRunMapper mrpRunMapper;
    private final MrpRunLineMapper mrpRunLineMapper;

    public MrpPlanQueryService(
            JdbcTemplate jdbcTemplate,
            AuditMetadataFactory auditMetadataFactory,
            MrpRunMapper mrpRunMapper,
            MrpRunLineMapper mrpRunLineMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditMetadataFactory = auditMetadataFactory;
        this.mrpRunMapper = mrpRunMapper;
        this.mrpRunLineMapper = mrpRunLineMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<MrpRunSummaryResponse> listRuns(MrpRunPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        MrpRunPageQuery safeQuery = query == null ? new MrpRunPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(
                safeQuery.getPageNo() == null ? null : safeQuery.getPageNo().intValue()
        );
        long pageSize = PageQueryNormalizer.normalizePageSize(
                safeQuery.getPageSize() == null ? null : safeQuery.getPageSize().intValue()
        );
        LambdaQueryWrapper<MrpRunEntity> wrapper = new LambdaQueryWrapper<MrpRunEntity>()
                .eq(MrpRunEntity::getCompanyId, audit.companyId())
                .eq(MrpRunEntity::getAccountBookId, audit.accountBookId())
                .eq(MrpRunEntity::getDeletedFlag, 0)
                .orderByDesc(MrpRunEntity::getCreatedTime)
                .orderByDesc(MrpRunEntity::getId);
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(MrpRunEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        Page<MrpRunEntity> page = mrpRunMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResponse<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toSummary).toList()
        );
    }

    @Transactional(readOnly = true)
    public MrpRunResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        MrpRunEntity run = requireRun(id, audit);
        List<MrpRunLineEntity> lines = mrpRunLineMapper.selectList(new LambdaQueryWrapper<MrpRunLineEntity>()
                .eq(MrpRunLineEntity::getCompanyId, audit.companyId())
                .eq(MrpRunLineEntity::getAccountBookId, audit.accountBookId())
                .eq(MrpRunLineEntity::getRunId, run.getId())
                .eq(MrpRunLineEntity::getDeletedFlag, 0)
                .orderByAsc(MrpRunLineEntity::getLineNo)
                .orderByAsc(MrpRunLineEntity::getId));
        Map<Long, String[]> names = loadProductNames(audit.companyId(), audit.accountBookId());
        List<MrpSuggestionLineResponse> purchaseLines = new ArrayList<>();
        List<MrpSuggestionLineResponse> productionLines = new ArrayList<>();
        for (MrpRunLineEntity line : lines) {
            MrpSuggestionLineResponse response = toLineResponse(line, names);
            if (TYPE_PRODUCTION.equals(line.getSuggestionType())) {
                productionLines.add(response);
            } else {
                purchaseLines.add(response);
            }
        }
        return new MrpRunResponse(
                run.getId(),
                run.getRunNo(),
                run.getAsOfDate() == null ? null : run.getAsOfDate().toString(),
                run.getStatus(),
                run.getPurchaseCount() == null ? purchaseLines.size() : run.getPurchaseCount(),
                run.getProductionCount() == null ? productionLines.size() : run.getProductionCount(),
                run.getCreatedTime(),
                purchaseLines,
                productionLines
        );
    }

    @Transactional(readOnly = true)
    public MrpRunEntity requireRun(Long id, AuditMetadata audit) {
        MrpRunEntity run = mrpRunMapper.selectById(id);
        if (run == null
                || run.getDeletedFlag() == null
                || run.getDeletedFlag() != 0
                || !Objects.equals(run.getCompanyId(), audit.companyId())
                || !Objects.equals(run.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("MRP计划不存在");
        }
        return run;
    }

    @Transactional(readOnly = true)
    public MrpRunLineEntity requireLine(Long runId, Long lineId, AuditMetadata audit) {
        MrpRunLineEntity line = mrpRunLineMapper.selectById(lineId);
        if (line == null
                || line.getDeletedFlag() == null
                || line.getDeletedFlag() != 0
                || !Objects.equals(line.getCompanyId(), audit.companyId())
                || !Objects.equals(line.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(line.getRunId(), runId)) {
            throw new IllegalArgumentException("MRP建议行不存在");
        }
        return line;
    }

    @Transactional(readOnly = true)
    public MrpSuggestionLineResponse toLineResponse(MrpRunLineEntity line, AuditMetadata audit) {
        return toLineResponse(line, loadProductNames(audit.companyId(), audit.accountBookId()));
    }

    private MrpRunSummaryResponse toSummary(MrpRunEntity run) {
        return new MrpRunSummaryResponse(
                run.getId(),
                run.getRunNo(),
                run.getAsOfDate() == null ? null : run.getAsOfDate().toString(),
                run.getStatus(),
                run.getPurchaseCount() == null ? 0 : run.getPurchaseCount(),
                run.getProductionCount() == null ? 0 : run.getProductionCount(),
                run.getCreatedTime()
        );
    }

    private MrpSuggestionLineResponse toLineResponse(
            MrpRunLineEntity line,
            Map<Long, String[]> names
    ) {
        String[] name = names.get(line.getProductId());
        return new MrpSuggestionLineResponse(
                line.getId(),
                line.getRunId(),
                line.getLineNo(),
                line.getProductId(),
                name == null ? null : name[0],
                name == null ? null : name[1],
                line.getSuggestionType(),
                ScalePrecision.quantity(line.getDemandQty()),
                ScalePrecision.quantity(line.getOnHandQty()),
                ScalePrecision.quantity(line.getOpenSupplyQty()),
                ScalePrecision.quantity(line.getNetQty()),
                line.getBomId(),
                line.getReason(),
                line.getStatus(),
                line.getConvertedBizType(),
                line.getConvertedBizId(),
                line.getConvertedBizNo(),
                line.getConvertedTime()
        );
    }

    private Map<Long, String[]> loadProductNames(Long companyId, Long accountBookId) {
        Map<Long, String[]> map = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select id, product_code, product_name from md_product
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                """, companyId, accountBookId)) {
            map.put(toLong(row.get("id")), new String[]{
                    row.get("product_code") == null ? null : row.get("product_code").toString(),
                    row.get("product_name") == null ? null : row.get("product_name").toString()
            });
        }
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }
}
