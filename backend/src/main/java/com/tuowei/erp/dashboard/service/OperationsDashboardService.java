package com.tuowei.erp.dashboard.service;

import com.tuowei.erp.dashboard.web.OperationsDashboardResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsDashboardService {

    private final OperationsDashboardQueryService queryService;
    private final OperationsDashboardPresentationService presentationService;

    public OperationsDashboardService(
            OperationsDashboardQueryService queryService,
            OperationsDashboardPresentationService presentationService
    ) {
        this.queryService = queryService;
        this.presentationService = presentationService;
    }

    @Transactional(readOnly = true)
    public OperationsDashboardResponse getOperationsDashboard() {
        return presentationService.present(queryService.load());
    }
}
