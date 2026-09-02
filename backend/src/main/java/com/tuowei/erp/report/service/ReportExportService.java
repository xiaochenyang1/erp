package com.tuowei.erp.report.service;

import com.tuowei.erp.common.export.CsvExport;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class ReportExportService {

    private static final List<String> ORDER_HEADERS = List.of(
            "bizNo", "partnerId", "bizDate", "status", "approvalStatus", "fulfillmentStatus", "totalQuantity", "totalAmount", "totalTaxAmount"
    );
    private static final List<String> INVENTORY_BALANCE_HEADERS = List.of(
            "warehouseId", "productId", "qtyOnHand", "qtyReserved", "qtyAvailable", "amountOnHand", "updatedTime"
    );
    private static final List<String> INVENTORY_TRANSACTION_HEADERS = List.of(
            "warehouseId", "productId", "bizType", "bizNo", "bizLineId", "direction", "qty", "amount", "unitCost", "occurredTime", "remark"
    );
    private static final List<String> FINANCE_SETTLEMENT_HEADERS = List.of(
            "direction", "bizNo", "partnerId", "bizDate", "sourceType", "sourceNo", "originalAmount", "settledAmount", "remainingAmount", "status"
    );
    private static final List<String> INVENTORY_VALUATION_HEADERS = List.of(
            "periodStart", "asOfDate", "warehouseCode", "warehouseName", "productCode", "productName",
            "openingQty", "openingAmount", "inboundQty", "inboundAmount", "outboundQty", "outboundAmount",
            "closingQty", "closingAmount", "averageUnitCost"
    );
    private static final List<String> PRODUCTION_COST_HEADERS = List.of(
            "orderNo", "productCode", "productName", "status", "plannedStartDate", "plannedFinishDate",
            "plannedQty", "completedQty", "completionRate", "materialCost", "finishedGoodsCost", "workInProgressCost",
            "completionUnitCost", "costStatus"
    );

    private final ReportQueryService reportQueryService;

    public ReportExportService(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    public StreamingResponseBody exportPurchaseOrders(PurchaseOrderReportQuery query) {
        reportQueryService.assertPurchaseOrderExportWithinLimit(query);
        return csvBody(ORDER_HEADERS, consumer -> reportQueryService.streamPurchaseOrders(query, consumer), this::orderRow);
    }

    public StreamingResponseBody exportSalesOrders(SalesOrderReportQuery query) {
        reportQueryService.assertSalesOrderExportWithinLimit(query);
        return csvBody(ORDER_HEADERS, consumer -> reportQueryService.streamSalesOrders(query, consumer), this::orderRow);
    }

    public StreamingResponseBody exportInventoryBalances(InventoryBalanceReportQuery query) {
        reportQueryService.assertInventoryBalanceExportWithinLimit(query);
        return csvBody(INVENTORY_BALANCE_HEADERS, consumer -> reportQueryService.streamInventoryBalances(query, consumer), this::inventoryBalanceRow);
    }

    public StreamingResponseBody exportInventoryTransactions(InventoryTransactionReportQuery query) {
        reportQueryService.assertInventoryTransactionExportWithinLimit(query);
        return csvBody(INVENTORY_TRANSACTION_HEADERS, consumer -> reportQueryService.streamInventoryTransactions(query, consumer), this::inventoryTransactionRow);
    }

    public StreamingResponseBody exportFinanceSettlements(FinanceSettlementReportQuery query) {
        reportQueryService.assertFinanceSettlementExportWithinLimit(query);
        return csvBody(FINANCE_SETTLEMENT_HEADERS, consumer -> reportQueryService.streamFinanceSettlements(query, consumer), this::financeSettlementRow);
    }

    public StreamingResponseBody exportInventoryValuations(InventoryValuationReportQuery query) {
        reportQueryService.assertInventoryValuationExportWithinLimit(query);
        return csvBody(INVENTORY_VALUATION_HEADERS, consumer -> reportQueryService.streamInventoryValuations(query, consumer), this::inventoryValuationRow);
    }

    public StreamingResponseBody exportProductionCosts(ProductionCostReportQuery query) {
        return csvBody(PRODUCTION_COST_HEADERS, consumer -> reportQueryService.streamProductionCosts(query, consumer), this::productionCostRow);
    }

    private <T> StreamingResponseBody csvBody(List<String> headers, RowStreamer<T> rowStreamer, Function<T, List<?>> rowMapper) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, headers, rowWriter ->
                rowStreamer.stream(record -> {
                    try {
                        rowWriter.write(rowMapper.apply(record));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
        ));
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    private List<?> orderRow(OrderReportResponse record) {
        return Arrays.asList(
                record.bizNo(),
                record.partnerId(),
                record.bizDate(),
                record.status(),
                record.approvalStatus(),
                record.fulfillmentStatus(),
                record.totalQuantity(),
                record.totalAmount(),
                record.totalTaxAmount()
        );
    }

    private List<?> inventoryBalanceRow(InventoryBalanceReportResponse record) {
        return Arrays.asList(
                record.warehouseId(),
                record.productId(),
                record.qtyOnHand(),
                record.qtyReserved(),
                record.qtyAvailable(),
                record.amountOnHand(),
                record.updatedTime()
        );
    }

    private List<?> inventoryTransactionRow(InventoryTransactionReportResponse record) {
        return Arrays.asList(
                record.warehouseId(),
                record.productId(),
                record.bizType(),
                record.bizNo(),
                record.bizLineId(),
                record.direction(),
                record.qty(),
                record.amount(),
                record.unitCost(),
                record.occurredTime(),
                record.remark()
        );
    }

    private List<?> financeSettlementRow(FinanceSettlementReportResponse record) {
        return Arrays.asList(
                record.direction(),
                record.bizNo(),
                record.partnerId(),
                record.bizDate(),
                record.sourceType(),
                record.sourceNo(),
                record.originalAmount(),
                record.settledAmount(),
                record.remainingAmount(),
                record.status()
        );
    }

    private List<?> inventoryValuationRow(InventoryValuationReportResponse record) {
        return Arrays.asList(record.periodStart(), record.asOfDate(), record.warehouseCode(), record.warehouseName(),
                record.productCode(), record.productName(), record.openingQty(), record.openingAmount(), record.inboundQty(),
                record.inboundAmount(), record.outboundQty(), record.outboundAmount(), record.closingQty(), record.closingAmount(), record.averageUnitCost());
    }

    private List<?> productionCostRow(ProductionCostReportResponse record) {
        return Arrays.asList(record.orderNo(), record.productCode(), record.productName(), record.status(), record.plannedStartDate(),
                record.plannedFinishDate(), record.plannedQty(), record.completedQty(), record.completionRate(), record.materialCost(),
                record.finishedGoodsCost(), record.workInProgressCost(), record.completionUnitCost(), record.costStatus());
    }

    @FunctionalInterface
    private interface RowStreamer<T> {

        void stream(Consumer<T> consumer);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}
