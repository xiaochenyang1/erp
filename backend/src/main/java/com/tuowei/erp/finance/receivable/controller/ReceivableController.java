package com.tuowei.erp.finance.receivable.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.finance.receivable.service.ReceivableQueryService;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/finance/receivables")
public class ReceivableController {

    private final ReceivableQueryService receivableQueryService;

    public ReceivableController(ReceivableQueryService receivableQueryService) {
        this.receivableQueryService = receivableQueryService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIVABLE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ReceivableResponse>> list(ReceivablePageQuery query) {
        return ApiResponse.success(receivableQueryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIVABLE_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(ReceivablePageQuery query) {
        return csv("receivables.csv", receivableQueryService.exportReceivables(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIVABLE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ReceivableResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(receivableQueryService.detail(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "receivables.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
