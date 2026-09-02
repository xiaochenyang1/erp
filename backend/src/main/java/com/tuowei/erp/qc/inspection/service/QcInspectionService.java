package com.tuowei.erp.qc.inspection.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionCreateRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.qc.inspection.web.QcInspectionResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Compatibility facade for quality inspection commands and queries. */
@Service
public class QcInspectionService {

    private final QcInspectionCreateService qcInspectionCreateService;
    private final QcInspectionQueryService qcInspectionQueryService;
    private final QcInspectionCommandService qcInspectionCommandService;

    public QcInspectionService(
            QcInspectionCreateService qcInspectionCreateService,
            QcInspectionQueryService qcInspectionQueryService,
            QcInspectionCommandService qcInspectionCommandService
    ) {
        this.qcInspectionCreateService = qcInspectionCreateService;
        this.qcInspectionQueryService = qcInspectionQueryService;
        this.qcInspectionCommandService = qcInspectionCommandService;
    }

    @Transactional
    public QcInspectionResponse create(QcInspectionCreateRequest request) {
        QcInspectionCreateService.CreationResult result = qcInspectionCreateService.create(request);
        return qcInspectionQueryService.toResponse(result.inspection(), result.lines());
    }

    @Transactional(readOnly = true)
    public PageResponse<QcInspectionResponse> list(QcInspectionPageQuery query) {
        QcInspectionPageQuery safeQuery = query == null ? new QcInspectionPageQuery() : query;
        return qcInspectionQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportInspections(QcInspectionPageQuery query) {
        QcInspectionPageQuery safeQuery = query == null ? new QcInspectionPageQuery() : query;
        return qcInspectionQueryService.exportInspections(safeQuery);
    }

    @Transactional(readOnly = true)
    public QcInspectionResponse getById(Long id) {
        return qcInspectionQueryService.getById(id);
    }

    @Transactional
    public QcInspectionResponse update(Long id, QcInspectionUpdateRequest request) {
        return qcInspectionCommandService.update(id, request);
    }

    @Transactional
    public QcInspectionResponse submit(Long id) {
        return qcInspectionCommandService.submit(id);
    }

    @Transactional
    public QcInspectionResponse judge(Long id, QcInspectionJudgeRequest request) {
        return qcInspectionCommandService.judge(id, request);
    }

    @Transactional
    public QcInspectionResponse cancel(Long id) {
        return qcInspectionCommandService.cancel(id);
    }
}
