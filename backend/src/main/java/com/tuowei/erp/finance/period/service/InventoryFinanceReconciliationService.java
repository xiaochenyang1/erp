package com.tuowei.erp.finance.period.service;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceQuery;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for inventory and finance reconciliation reporting. */
@Service
@NativeSqlTenantScoped("reconciliation facade delegates to the tenant-scoped native query service")
public class InventoryFinanceReconciliationService {

    private final InventoryFinanceReconciliationQueryService queryService;
    private final InventoryFinanceReconciliationAssemblyService assemblyService;

    @Autowired
    public InventoryFinanceReconciliationService(
            InventoryFinanceReconciliationQueryService queryService,
            InventoryFinanceReconciliationAssemblyService assemblyService
    ) {
        this.queryService = queryService;
        this.assemblyService = assemblyService;
    }

    /** Keeps direct construction compatible with the original service signature. */
    public InventoryFinanceReconciliationService(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            JdbcTemplate jdbcTemplate
    ) {
        this.queryService = new InventoryFinanceReconciliationQueryService(
                accountPeriodMapper,
                auditMetadataFactory,
                jdbcTemplate
        );
        this.assemblyService = new InventoryFinanceReconciliationAssemblyService();
    }

    @Transactional(readOnly = true)
    public InventoryFinanceReconciliationResponse summary(Long periodId) {
        return assemblyService.assembleSummary(queryService.loadSummary(periodId));
    }

    @Transactional(readOnly = true)
    public List<InventoryFinanceDifferenceResponse> differences(
            Long periodId,
            InventoryFinanceDifferenceQuery query
    ) {
        return assemblyService.assembleDifferences(queryService.loadDifferences(periodId), query);
    }

    @Transactional(readOnly = true)
    public InventoryFinanceDifferenceDetailResponse differenceDetail(
            Long periodId,
            String sourceType,
            String sourceNo
    ) {
        return assemblyService.assembleDetail(queryService.loadDifferenceDetail(periodId, sourceType, sourceNo));
    }
}
