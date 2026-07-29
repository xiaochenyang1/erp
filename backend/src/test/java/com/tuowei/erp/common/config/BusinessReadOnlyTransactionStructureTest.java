package com.tuowei.erp.common.config;

import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.period.service.AccountPeriodService;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.finance.voucher.web.VoucherPageQuery;
import com.tuowei.erp.imports.service.ImportJobService;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferService;
import com.tuowei.erp.masterdata.customer.service.CustomerService;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.supplier.service.SupplierService;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseService;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderTraceService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.qc.inspection.service.QcInspectionService;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessReadOnlyTransactionStructureTest {

    private static final Pattern LIST_METHOD_PATTERN =
            Pattern.compile("public\\s+PageResponse<.*>\\s+list\\((\\w+)\\s+(\\w+)\\)");

    @Test
    void masterdataQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(CustomerService.class, "list", CustomerPageQuery.class);
        assertReadOnly(CustomerService.class, "getById", Long.class);
        assertReadOnly(SupplierService.class, "list", SupplierPageQuery.class);
        assertReadOnly(SupplierService.class, "getById", Long.class);
        assertReadOnly(ProductService.class, "list", ProductPageQuery.class);
        assertReadOnly(ProductService.class, "getById", Long.class);
        assertReadOnly(WarehouseService.class, "list", WarehousePageQuery.class);
        assertReadOnly(WarehouseService.class, "getById", Long.class);
    }

    @Test
    void documentQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(PurchaseOrderService.class, "list", PurchaseOrderPageQuery.class);
        assertReadOnly(PurchaseOrderService.class, "getById", Long.class);
        assertReadOnly(PurchaseOrderService.class, "trace", Long.class);
        assertReadOnly(PurchaseOrderQueryService.class, "list", PurchaseOrderPageQuery.class);
        assertReadOnly(PurchaseOrderQueryService.class, "assertCanView", PurchaseOrderEntity.class);
        assertReadOnly(PurchaseOrderTraceService.class, "trace", PurchaseOrderResponse.class);
        assertReadOnly(PurchaseReceiptService.class, "list", PurchaseReceiptPageQuery.class);
        assertReadOnly(PurchaseReceiptService.class, "getById", Long.class);
        assertReadOnly(PurchaseReturnService.class, "list", PurchaseReturnPageQuery.class);
        assertReadOnly(PurchaseReturnService.class, "getById", Long.class);
        assertReadOnly(QcInspectionService.class, "list", QcInspectionPageQuery.class);
        assertReadOnly(QcInspectionService.class, "getById", Long.class);

        assertReadOnly(SalesOrderService.class, "list", SalesOrderPageQuery.class);
        assertReadOnly(SalesOrderService.class, "getById", Long.class);
        assertReadOnly(SalesDeliveryService.class, "list", SalesDeliveryPageQuery.class);
        assertReadOnly(SalesDeliveryService.class, "getById", Long.class);
        assertReadOnly(SalesDeliveryQueryService.class, "list", SalesDeliveryPageQuery.class);
        assertReadOnly(SalesDeliveryQueryService.class, "getById", Long.class);
        assertReadOnly(SalesDeliveryQueryService.class, "assertCanView", SalesDeliveryEntity.class);
        assertReadOnly(SalesDeliveryQueryService.class, "assertCanView", SalesOrderEntity.class);
        assertReadOnly(SalesReturnService.class, "list", SalesReturnPageQuery.class);
        assertReadOnly(SalesReturnService.class, "getById", Long.class);

        assertReadOnly(InventoryTransferService.class, "getById", Long.class);
        assertReadOnly(InventoryAdjustmentService.class, "getById", Long.class);
        assertReadOnly(InventoryStockCheckService.class, "getById", Long.class);
    }

    @Test
    void productionAndFinanceQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ProductionBomService.class, "list", ProductionBomPageQuery.class);
        assertReadOnly(ProductionBomService.class, "getById", Long.class);
        assertReadOnly(ProductionOrderService.class, "list", ProductionOrderPageQuery.class);
        assertReadOnly(ProductionOrderService.class, "getById", Long.class);

        assertReadOnly(VoucherQueryService.class, "list", VoucherPageQuery.class);
        assertReadOnly(VoucherQueryService.class, "detail", Long.class);
        assertReadOnly(ReceiptService.class, "list", ReceiptPageQuery.class);
        assertReadOnly(ReceiptService.class, "detail", Long.class);
        assertReadOnly(PaymentService.class, "list", PaymentPageQuery.class);
        assertReadOnly(PaymentService.class, "detail", Long.class);
        assertReadOnly(ExpenseService.class, "list", ExpensePageQuery.class);
        assertReadOnly(ExpenseService.class, "detail", Long.class);
        assertReadOnly(AccountSubjectService.class, "list", AccountSubjectPageQuery.class);
        assertReadOnly(AccountSubjectService.class, "detail", Long.class);
        assertReadOnly(AccountSubjectService.class, "tree");
        assertReadOnly(AccountPeriodService.class, "list", Integer.class);
    }

    @Test
    void importAndWorkflowQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ImportJobService.class, "list", ImportJobPageQuery.class);
        assertReadOnly(ImportJobService.class, "detail", Long.class);
        assertReadOnly(ImportJobService.class, "exportErrorRows", Long.class);

        assertReadOnly(WorkflowService.class, "listTasks", WorkflowTaskPageQuery.class);
        assertReadOnly(WorkflowService.class, "listRecords", WorkflowRecordPageQuery.class);
        assertReadOnly(WorkflowService.class, "approvalInfo", String.class, Long.class);
        assertReadOnly(WorkflowApprovalConfigService.class, "getByBusinessType", String.class);
    }

    @Test
    void listPageQueriesHandleNullAsDefaultQuery() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java/com/tuowei/erp"))) {
            for (Path path : paths.filter(path -> path.getFileName().toString().endsWith("Service.java")).toList()) {
                List<String> lines = Files.readAllLines(path);
                for (int index = 0; index < lines.size(); index++) {
                    Matcher matcher = LIST_METHOD_PATTERN.matcher(lines.get(index));
                    if (!matcher.find()) {
                        continue;
                    }
                    String queryType = matcher.group(1);
                    String queryName = matcher.group(2);
                    String methodStart = String.join("\n", lines.subList(
                            index,
                            Math.min(index + 15, lines.size())
                    ));
                    String expectedGuard = queryName + " == null ? new " + queryType + "() : " + queryName;
                    if (!methodStart.contains(expectedGuard)) {
                        violations.add(path + ":" + (index + 1) + " missing null-safe default for " + queryName);
                    }
                }
            }
        }

        assertThat(violations)
                .as("All list(PageQuery) service methods should tolerate null query objects")
                .isEmpty();
    }

    @Test
    void stockBalanceHelperQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(InventoryPostingService.class, "getQtyOnHand", Long.class, Long.class, Long.class, Long.class);
        assertReadOnly(InventoryPostingService.class, "getQtyAvailable", Long.class, Long.class, Long.class, Long.class);
    }

    private static void assertReadOnly(Class<?> serviceClass, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = serviceClass.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s should declare @Transactional(readOnly = true)", method)
                .isNotNull();
        assertThat(transactional.readOnly())
                .as("%s should be read-only", method)
                .isTrue();
    }
}
