package com.tuowei.erp.issue.rule.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleHitMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitPageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRulePageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleScanResultResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for exception rule queries, commands and scans. */
@Service
public class ExceptionRuleService {

    private final ExceptionRuleQueryService queryService;
    private final ExceptionRuleCommandService commandService;

    @Autowired
    public ExceptionRuleService(
            ExceptionRuleQueryService queryService,
            ExceptionRuleCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing tests compatible. */
    public ExceptionRuleService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleHitMapper hitMapper,
            ExceptionRuleScanService scanService
    ) {
        this.queryService = new ExceptionRuleQueryService(auditMetadataFactory, ruleMapper, hitMapper);
        this.commandService = new ExceptionRuleCommandService(
                auditMetadataFactory, ruleMapper, scanService, queryService
        );
    }

    @Transactional
    public PageResponse<ExceptionRuleResponse> list(ExceptionRulePageQuery query) {
        commandService.ensureBuiltInRules();
        return queryService.list(query == null ? new ExceptionRulePageQuery() : query);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionRuleHitResponse> listHits(ExceptionRuleHitPageQuery query) {
        return queryService.listHits(query == null ? new ExceptionRuleHitPageQuery() : query);
    }

    @Transactional
    public ExceptionRuleResponse update(Long id, ExceptionRuleUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional
    public ExceptionRuleResponse enable(Long id) { return commandService.enable(id); }

    @Transactional
    public ExceptionRuleResponse disable(Long id) { return commandService.disable(id); }

    @Transactional
    public ExceptionRuleScanResultResponse scanRule(Long id) { return commandService.scanRule(id); }

    @Transactional
    public List<ExceptionRuleScanResultResponse> scanAll() { return commandService.scanAll(); }

    @Transactional
    public List<ExceptionRuleScanResultResponse> scanDueRules() { return commandService.scanDueRules(); }
}
