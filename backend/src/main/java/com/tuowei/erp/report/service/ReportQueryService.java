package com.tuowei.erp.report.service;

import com.tuowei.erp.common.web.PageResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Service
public class ReportQueryService {

    private final OrderReportQueryService orderReportQueryService;
    private final InventoryReportQueryService inventoryReportQueryService;
    private final FinanceSettlementReportQueryService financeSettlementReportQueryService;
    private final InventoryValuationReportService inventoryValuationReportService;
    private final ProductionCostReportService productionCostReportService;

    @Autowired
    public ReportQueryService(
            OrderReportQueryService orderReportQueryService,
            InventoryReportQueryService inventoryReportQueryService,
            FinanceSettlementReportQueryService financeSettlementReportQueryService,
            InventoryValuationReportService inventoryValuationReportService,
            ProductionCostReportService productionCostReportService
    ) {
        this.orderReportQueryService = orderReportQueryService;
        this.inventoryReportQueryService = inventoryReportQueryService;
        this.financeSettlementReportQueryService = financeSettlementReportQueryService;
        this.inventoryValuationReportService = inventoryValuationReportService;
        this.productionCostReportService = productionCostReportService;
    }

    public ReportQueryService(OrderReportQueryService orderReportQueryService,
                              InventoryReportQueryService inventoryReportQueryService,
                              FinanceSettlementReportQueryService financeSettlementReportQueryService) {
        this(orderReportQueryService, inventoryReportQueryService, financeSettlementReportQueryService, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderReportResponse> listPurchaseOrders(PurchaseOrderReportQuery query) {
        PurchaseOrderReportQuery safeQuery = query == null ? new PurchaseOrderReportQuery() : query;
        return orderReportQueryService.listPurchaseOrders(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertPurchaseOrderExportWithinLimit(PurchaseOrderReportQuery query) {
        orderReportQueryService.assertPurchaseOrderExportWithinLimit(query);
    }

    @Transactional(readOnly = true)
    public void streamPurchaseOrders(PurchaseOrderReportQuery query, Consumer<OrderReportResponse> consumer) {
        orderReportQueryService.streamPurchaseOrders(query, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderReportResponse> listSalesOrders(SalesOrderReportQuery query) {
        SalesOrderReportQuery safeQuery = query == null ? new SalesOrderReportQuery() : query;
        return orderReportQueryService.listSalesOrders(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertSalesOrderExportWithinLimit(SalesOrderReportQuery query) {
        orderReportQueryService.assertSalesOrderExportWithinLimit(query);
    }

    @Transactional(readOnly = true)
    public void streamSalesOrders(SalesOrderReportQuery query, Consumer<OrderReportResponse> consumer) {
        orderReportQueryService.streamSalesOrders(query, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceReportResponse> listInventoryBalances(InventoryBalanceReportQuery query) {
        InventoryBalanceReportQuery safeQuery = query == null ? new InventoryBalanceReportQuery() : query;
        return inventoryReportQueryService.listInventoryBalances(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertInventoryBalanceExportWithinLimit(InventoryBalanceReportQuery query) {
        inventoryReportQueryService.assertInventoryBalanceExportWithinLimit(query);
    }

    @Transactional(readOnly = true)
    public void streamInventoryBalances(InventoryBalanceReportQuery query, Consumer<InventoryBalanceReportResponse> consumer) {
        inventoryReportQueryService.streamInventoryBalances(query, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionReportResponse> listInventoryTransactions(InventoryTransactionReportQuery query) {
        InventoryTransactionReportQuery safeQuery = query == null ? new InventoryTransactionReportQuery() : query;
        return inventoryReportQueryService.listInventoryTransactions(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertInventoryTransactionExportWithinLimit(InventoryTransactionReportQuery query) {
        inventoryReportQueryService.assertInventoryTransactionExportWithinLimit(query);
    }

    @Transactional(readOnly = true)
    public void streamInventoryTransactions(InventoryTransactionReportQuery query, Consumer<InventoryTransactionReportResponse> consumer) {
        inventoryReportQueryService.streamInventoryTransactions(query, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceSettlementReportResponse> listFinanceSettlements(FinanceSettlementReportQuery query) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        return financeSettlementReportQueryService.listFinanceSettlements(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertFinanceSettlementExportWithinLimit(FinanceSettlementReportQuery query) {
        financeSettlementReportQueryService.assertFinanceSettlementExportWithinLimit(query);
    }

    @Transactional(readOnly = true)
    public void streamFinanceSettlements(FinanceSettlementReportQuery query, Consumer<FinanceSettlementReportResponse> consumer) {
        financeSettlementReportQueryService.streamFinanceSettlements(query, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryValuationReportResponse> listInventoryValuations(InventoryValuationReportQuery query) {
        return inventoryValuationReportService.list(query);
    }

    @Transactional(readOnly = true)
    public void assertInventoryValuationExportWithinLimit(InventoryValuationReportQuery query) {
        inventoryValuationReportService.assertExportWithinLimit(query);
    }

    @Transactional(readOnly = true)
    public void streamInventoryValuations(InventoryValuationReportQuery query, Consumer<InventoryValuationReportResponse> consumer) {
        inventoryValuationReportService.stream(query, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionCostReportResponse> listProductionCosts(ProductionCostReportQuery query) {
        return productionCostReportService.list(query);
    }

    @Transactional(readOnly = true)
    public void streamProductionCosts(ProductionCostReportQuery query, Consumer<ProductionCostReportResponse> consumer) {
        productionCostReportService.stream(query, consumer, 500);
    }
}
