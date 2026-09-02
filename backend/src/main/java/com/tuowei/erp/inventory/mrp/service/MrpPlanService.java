package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpRunPageQuery;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.inventory.mrp.web.MrpRunSummaryResponse;
import com.tuowei.erp.inventory.mrp.web.MrpSuggestionLineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for persisted MRP run queries and commands. */
@Service
public class MrpPlanService {

    private final MrpPlanQueryService mrpPlanQueryService;
    private final MrpPlanCommandService mrpPlanCommandService;

    public MrpPlanService(
            MrpPlanQueryService mrpPlanQueryService,
            MrpPlanCommandService mrpPlanCommandService
    ) {
        this.mrpPlanQueryService = mrpPlanQueryService;
        this.mrpPlanCommandService = mrpPlanCommandService;
    }

    @Transactional
    public MrpRunResponse run() {
        return mrpPlanCommandService.run();
    }

    @Transactional(readOnly = true)
    public PageResponse<MrpRunSummaryResponse> listRuns(MrpRunPageQuery query) {
        MrpRunPageQuery safeQuery = query == null ? new MrpRunPageQuery() : query;
        return mrpPlanQueryService.listRuns(safeQuery);
    }

    @Transactional(readOnly = true)
    public MrpRunResponse getById(Long id) {
        return mrpPlanQueryService.getById(id);
    }

    @Transactional
    public MrpSuggestionLineResponse convertLine(Long runId, Long lineId, MrpConvertLineRequest request) {
        return mrpPlanCommandService.convertLine(runId, lineId, request);
    }
}
