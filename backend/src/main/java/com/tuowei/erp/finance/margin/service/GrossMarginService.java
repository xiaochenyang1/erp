package com.tuowei.erp.finance.margin.service;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Compatibility facade for gross-margin reporting. */
@Service
@NativeSqlTenantScoped("margin report delegates to tenant-scoped query service")
public class GrossMarginService {

    private final GrossMarginQueryService queryService;
    private final GrossMarginAssemblyService assemblyService;

    @Autowired
    public GrossMarginService(
            GrossMarginQueryService queryService,
            GrossMarginAssemblyService assemblyService
    ) {
        this.queryService = queryService;
        this.assemblyService = assemblyService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public GrossMarginService(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.queryService = new GrossMarginQueryService(jdbcTemplate, auditMetadataFactory);
        this.assemblyService = new GrossMarginAssemblyService();
    }

    @Transactional(readOnly = true)
    public GrossMarginSummaryResponse summary(LocalDate dateFrom, LocalDate dateTo) {
        return assemblyService.assemble(queryService.load(dateFrom, dateTo));
    }
}
