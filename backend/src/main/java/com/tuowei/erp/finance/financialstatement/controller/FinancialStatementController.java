package com.tuowei.erp.finance.financialstatement.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementConfigurationService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementExportService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementConfigurationResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementQuery;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.finance.financialstatement.web.StatementMappingUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/finance/financial-statements")
public class FinancialStatementController {

    private final FinancialStatementService financialStatementService;
    private final FinancialStatementConfigurationService configurationService;
    private final FinancialStatementExportService exportService;

    public FinancialStatementController(
            FinancialStatementService financialStatementService,
            FinancialStatementConfigurationService configurationService,
            FinancialStatementExportService exportService
    ) {
        this.financialStatementService = financialStatementService;
        this.configurationService = configurationService;
        this.exportService = exportService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/balance-sheet")
    public ApiResponse<BalanceSheetResponse> balanceSheet(BalanceSheetQuery query) {
        return ApiResponse.success(financialStatementService.balanceSheet(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/income-statement")
    public ApiResponse<IncomeStatementResponse> incomeStatement(IncomeStatementQuery query) {
        return ApiResponse.success(financialStatementService.incomeStatement(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/balance-sheet/export")
    public ResponseEntity<StreamingResponseBody> exportBalanceSheet(BalanceSheetQuery query) {
        return csv("balance-sheet.csv", exportService.exportBalanceSheet(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/income-statement/export")
    public ResponseEntity<StreamingResponseBody> exportIncomeStatement(IncomeStatementQuery query) {
        return csv("income-statement.csv", exportService.exportIncomeStatement(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @GetMapping("/configuration")
    public ApiResponse<FinancialStatementConfigurationResponse> configuration(
            @RequestParam String statementType
    ) {
        return ApiResponse.success(configurationService.configuration(statementType));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PutMapping("/configuration/{statementType}/{itemCode}/subjects")
    @OperationLog(
            module = "finance",
            operation = "update-financial-statement-mapping",
            message = "更新财务报表科目映射",
            bizNo = "#result.data.statementType"
    )
    public ApiResponse<FinancialStatementConfigurationResponse> replaceSubjects(
            @PathVariable String statementType,
            @PathVariable String itemCode,
            @Valid @RequestBody StatementMappingUpdateRequest request
    ) {
        return ApiResponse.success(configurationService.replaceSubjects(statementType, itemCode, request));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "financial-statement.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
