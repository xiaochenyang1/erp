package com.tuowei.erp.report.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.report.service.ReportExportService;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryBalanceReportResponse;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportResponse;
import com.tuowei.erp.report.web.InventoryValuationReportQuery;
import com.tuowei.erp.report.web.InventoryValuationReportResponse;
import com.tuowei.erp.report.web.ProductionCostReportQuery;
import com.tuowei.erp.report.web.ProductionCostReportResponse;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.report.web.SalesOrderReportQuery;
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

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ReportExportService reportExportService;

    public ReportController(ReportQueryService reportQueryService, ReportExportService reportExportService) {
        this.reportQueryService = reportQueryService;
        this.reportExportService = reportExportService;
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/purchase-orders")
    public ApiResponse<PageResponse<OrderReportResponse>> purchaseOrders(PurchaseOrderReportQuery query) {
        return ApiResponse.success(reportQueryService.listPurchaseOrders(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/purchase-orders/export")
    public ResponseEntity<StreamingResponseBody> exportPurchaseOrders(PurchaseOrderReportQuery query) {
        return csv("purchase-orders.csv", reportExportService.exportPurchaseOrders(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/sales-orders")
    public ApiResponse<PageResponse<OrderReportResponse>> salesOrders(SalesOrderReportQuery query) {
        return ApiResponse.success(reportQueryService.listSalesOrders(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/sales-orders/export")
    public ResponseEntity<StreamingResponseBody> exportSalesOrders(SalesOrderReportQuery query) {
        return csv("sales-orders.csv", reportExportService.exportSalesOrders(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/inventory-balances")
    public ApiResponse<PageResponse<InventoryBalanceReportResponse>> inventoryBalances(InventoryBalanceReportQuery query) {
        return ApiResponse.success(reportQueryService.listInventoryBalances(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/inventory-balances/export")
    public ResponseEntity<StreamingResponseBody> exportInventoryBalances(InventoryBalanceReportQuery query) {
        return csv("inventory-balances.csv", reportExportService.exportInventoryBalances(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/inventory-transactions")
    public ApiResponse<PageResponse<InventoryTransactionReportResponse>> inventoryTransactions(InventoryTransactionReportQuery query) {
        return ApiResponse.success(reportQueryService.listInventoryTransactions(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/inventory-transactions/export")
    public ResponseEntity<StreamingResponseBody> exportInventoryTransactions(InventoryTransactionReportQuery query) {
        return csv("inventory-transactions.csv", reportExportService.exportInventoryTransactions(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/finance-settlements")
    public ApiResponse<PageResponse<FinanceSettlementReportResponse>> financeSettlements(FinanceSettlementReportQuery query) {
        return ApiResponse.success(reportQueryService.listFinanceSettlements(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/finance-settlements/export")
    public ResponseEntity<StreamingResponseBody> exportFinanceSettlements(FinanceSettlementReportQuery query) {
        return csv("finance-settlements.csv", reportExportService.exportFinanceSettlements(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/inventory-valuations")
    public ApiResponse<PageResponse<InventoryValuationReportResponse>> inventoryValuations(InventoryValuationReportQuery query) {
        return ApiResponse.success(reportQueryService.listInventoryValuations(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/inventory-valuations/export")
    public ResponseEntity<StreamingResponseBody> exportInventoryValuations(InventoryValuationReportQuery query) {
        return csv("inventory-valuations.csv", reportExportService.exportInventoryValuations(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/production-costs")
    public ApiResponse<PageResponse<ProductionCostReportResponse>> productionCosts(ProductionCostReportQuery query) {
        return ApiResponse.success(reportQueryService.listProductionCosts(query));
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping("/production-costs/export")
    public ResponseEntity<StreamingResponseBody> exportProductionCosts(ProductionCostReportQuery query) {
        return csv("production-costs.csv", reportExportService.exportProductionCosts(query));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "report.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
