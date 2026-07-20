package com.tuowei.erp.finance.ledger.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.finance.ledger.service.FinanceLedgerService;
import com.tuowei.erp.finance.ledger.web.DetailLedgerResponse;
import com.tuowei.erp.finance.ledger.web.GeneralLedgerResponse;
import com.tuowei.erp.finance.ledger.web.LedgerQuery;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/finance/ledger")
public class FinanceLedgerController {

    private final FinanceLedgerService financeLedgerService;

    public FinanceLedgerController(FinanceLedgerService financeLedgerService) {
        this.financeLedgerService = financeLedgerService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/general")
    public ApiResponse<List<GeneralLedgerResponse>> general(LedgerQuery query) {
        return ApiResponse.success(financeLedgerService.general(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/detail")
    public ApiResponse<List<DetailLedgerResponse>> detail(LedgerQuery query) {
        return ApiResponse.success(financeLedgerService.detail(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(LedgerQuery query) {
        return csv("finance-ledger.csv", financeLedgerService.exportLedger(query));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "finance-ledger.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
