package com.tuowei.erp.system.readiness.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.readiness.web.ReadinessDecisionRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemResultRequest;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessRunDetailResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import com.tuowei.erp.system.readiness.web.ReadinessRunResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for readiness queries and commands. */
@Service
public class ReadinessService {

    private final ReadinessQueryService readinessQueryService;
    private final ReadinessCommandService readinessCommandService;

    public ReadinessService(
            ReadinessQueryService readinessQueryService,
            ReadinessCommandService readinessCommandService
    ) {
        this.readinessQueryService = readinessQueryService;
        this.readinessCommandService = readinessCommandService;
    }

    @Transactional
    public ReadinessRunResponse createRun(ReadinessRunCreateRequest request) {
        return readinessCommandService.createRun(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadinessRunResponse> listRuns(ReadinessRunPageQuery query) {
        return readinessQueryService.listRuns(query);
    }

    @Transactional(readOnly = true)
    public ReadinessRunDetailResponse detail(Long id) {
        return readinessQueryService.detail(id);
    }

    @Transactional
    public ReadinessItemResponse addItem(Long runId, ReadinessItemCreateRequest request) {
        return readinessCommandService.addItem(runId, request);
    }

    @Transactional
    public ReadinessEvidenceResponse addEvidence(Long itemId, ReadinessEvidenceCreateRequest request) {
        return readinessCommandService.addEvidence(itemId, request);
    }

    @Transactional
    public ReadinessItemResponse markItemResult(Long itemId, ReadinessItemResultRequest request) {
        return readinessCommandService.markItemResult(itemId, request);
    }

    @Transactional
    public ReadinessRunResponse decide(Long runId, ReadinessDecisionRequest request) {
        return readinessCommandService.decide(runId, request);
    }

    @Transactional
    public ReadinessPreflightResponse recordPreflightEvidence(Long runId, ReadinessPreflightResponse preflight) {
        return readinessCommandService.recordPreflightEvidence(runId, preflight);
    }
}
