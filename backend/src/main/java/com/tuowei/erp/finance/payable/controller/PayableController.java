package com.tuowei.erp.finance.payable.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.finance.payable.service.PayableQueryService;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
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
@RequestMapping("/api/finance/payables")
public class PayableController {

    private final PayableQueryService payableQueryService;

    public PayableController(PayableQueryService payableQueryService) {
        this.payableQueryService = payableQueryService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYABLE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PayableResponse>> list(PayablePageQuery query) {
        return ApiResponse.success(payableQueryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYABLE_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(PayablePageQuery query) {
        return csv("payables.csv", payableQueryService.exportPayables(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYABLE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PayableResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(payableQueryService.detail(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "payables.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
