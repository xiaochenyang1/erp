package com.tuowei.erp.system.config.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for sequence-rule queries and commands. */
@Service
public class SequenceRuleService {

    private final SequenceRuleQueryService sequenceRuleQueryService;
    private final SequenceRuleCommandService sequenceRuleCommandService;

    public SequenceRuleService(
            SequenceRuleQueryService sequenceRuleQueryService,
            SequenceRuleCommandService sequenceRuleCommandService
    ) {
        this.sequenceRuleQueryService = sequenceRuleQueryService;
        this.sequenceRuleCommandService = sequenceRuleCommandService;
    }

    @Transactional
    public SequenceRuleResponse create(SequenceRuleCreateRequest request) {
        return sequenceRuleCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<SequenceRuleResponse> list(SequenceRulePageQuery query) {
        return sequenceRuleQueryService.list(query == null ? new SequenceRulePageQuery() : query);
    }

    @Transactional(readOnly = true)
    public SequenceRuleResponse getById(Long id) {
        return sequenceRuleQueryService.getById(id);
    }

    @Transactional
    public SequenceRuleResponse update(Long id, SequenceRuleUpdateRequest request) {
        return sequenceRuleCommandService.update(id, request);
    }

    @Transactional
    public SequenceRuleResponse enable(Long id) {
        return sequenceRuleCommandService.enable(id);
    }

    @Transactional
    public SequenceRuleResponse disable(Long id) {
        return sequenceRuleCommandService.disable(id);
    }
}
