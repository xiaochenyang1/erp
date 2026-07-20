package com.tuowei.erp.qc.inspection.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.qc.inspection.service.QcInspectionService;
import com.tuowei.erp.qc.inspection.web.QcInspectionCreateRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.qc.inspection.web.QcInspectionResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/qc/inspections")
public class QcInspectionController {

    private final QcInspectionService qcInspectionService;

    public QcInspectionController(QcInspectionService qcInspectionService) {
        this.qcInspectionService = qcInspectionService;
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_CREATE)
    @OperationLog(module = "qc", operation = "create", bizNo = "#result.data.inspectionNo")
    @PostMapping
    public ApiResponse<QcInspectionResponse> create(@Valid @RequestBody QcInspectionCreateRequest request) {
        return ApiResponse.success(qcInspectionService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<QcInspectionResponse>> list(QcInspectionPageQuery query) {
        return ApiResponse.success(qcInspectionService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(QcInspectionPageQuery query) {
        return csv("qc-inspections.csv", qcInspectionService.exportInspections(query));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<QcInspectionResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(qcInspectionService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_UPDATE)
    @OperationLog(module = "qc", operation = "update", bizNo = "#result.data.inspectionNo")
    @PutMapping("/{id}")
    public ApiResponse<QcInspectionResponse> update(@PathVariable Long id, @Valid @RequestBody QcInspectionUpdateRequest request) {
        return ApiResponse.success(qcInspectionService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_SUBMIT)
    @OperationLog(module = "qc", operation = "submit", bizNo = "#result.data.inspectionNo")
    @PostMapping("/{id}/submit")
    public ApiResponse<QcInspectionResponse> submit(@PathVariable Long id) {
        return ApiResponse.success(qcInspectionService.submit(id));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_JUDGE)
    @OperationLog(module = "qc", operation = "judge", bizNo = "#result.data.inspectionNo")
    @PostMapping("/{id}/judge")
    public ApiResponse<QcInspectionResponse> judge(@PathVariable Long id, @Valid @RequestBody QcInspectionJudgeRequest request) {
        return ApiResponse.success(qcInspectionService.judge(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_QC_INSPECTION_CANCEL)
    @OperationLog(module = "qc", operation = "cancel", bizNo = "#result.data.inspectionNo")
    @PostMapping("/{id}/cancel")
    public ApiResponse<QcInspectionResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(qcInspectionService.cancel(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "qc-inspections.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
