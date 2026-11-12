package com.tuowei.erp.finance.cashflow.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.finance.cashflow.service.CashFlowConfigurationService;
import com.tuowei.erp.finance.cashflow.service.CashFlowExportService;
import com.tuowei.erp.finance.cashflow.service.CashFlowService;
import com.tuowei.erp.finance.cashflow.web.CashFlowConfigurationResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowQuery;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowSubjectCodesRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/finance/financial-statements/cash-flow")
public class CashFlowController {

    private final CashFlowService cashFlowService;
    private final CashFlowConfigurationService configurationService;
    private final CashFlowExportService exportService;

    public CashFlowController(
            CashFlowService cashFlowService,
            CashFlowConfigurationService configurationService,
            CashFlowExportService exportService
    ) {
        this.cashFlowService = cashFlowService;
        this.configurationService = configurationService;
        this.exportService = exportService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping
    public ApiResponse<CashFlowResponse> cashFlow(CashFlowQuery query) {
        return ApiResponse.success(cashFlowService.cashFlow(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(CashFlowQuery query) {
        String filename = SafeFilename.normalize("cash-flow.csv", "cash-flow.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(exportService.export(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @GetMapping("/configuration")
    public ApiResponse<CashFlowConfigurationResponse> configuration() {
        return ApiResponse.success(configurationService.configuration());
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PutMapping("/configuration/cash-subjects")
    @OperationLog(
            module = "finance",
            operation = "update-cash-equivalent-subjects",
            message = "更新现金及现金等价物科目范围",
            bizNo = "#result.data.formulaVersion"
    )
    public ApiResponse<CashFlowConfigurationResponse> replaceCashSubjects(
            @Valid @RequestBody CashFlowSubjectCodesRequest request
    ) {
        return ApiResponse.success(configurationService.replaceCashSubjects(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PutMapping("/configuration/items/{itemCode}/subjects")
    @OperationLog(
            module = "finance",
            operation = "update-cash-flow-item-mapping",
            message = "更新现金流量表项目科目映射",
            bizNo = "#result.data.formulaVersion"
    )
    public ApiResponse<CashFlowConfigurationResponse> replaceItemSubjects(
            @PathVariable String itemCode,
            @Valid @RequestBody CashFlowSubjectCodesRequest request
    ) {
        return ApiResponse.success(configurationService.replaceItemSubjects(itemCode, request));
    }
}
