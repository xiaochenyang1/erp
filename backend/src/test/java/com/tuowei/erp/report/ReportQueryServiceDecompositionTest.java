package com.tuowei.erp.report;

import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.report.service.FinanceSettlementReportDataScopeService;
import com.tuowei.erp.report.service.InventoryReportDataScopeService;
import com.tuowei.erp.report.service.InventoryReportQueryService;
import com.tuowei.erp.report.service.OrderReportDataScopeService;
import com.tuowei.erp.report.service.OrderReportQueryService;
import com.tuowei.erp.report.service.FinanceSettlementReportQueryService;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.report.web.SalesOrderReportQuery;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReportQueryServiceDecompositionTest {

    @Test
    void facadeDelegatesInventoryReportsWithoutRetainingInventoryMappers() {
        assertThat(constructorDependencies(ReportQueryService.class))
                .contains(
                        InventoryReportQueryService.class,
                        OrderReportQueryService.class,
                        FinanceSettlementReportQueryService.class
                )
                .doesNotContain(
                        InventoryBalanceMapper.class,
                        InventoryTransactionMapper.class,
                        com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper.class,
                        com.tuowei.erp.sales.order.mapper.SalesOrderMapper.class,
                        com.tuowei.erp.finance.payable.mapper.PayableMapper.class,
                        com.tuowei.erp.finance.receivable.mapper.ReceivableMapper.class,
                        com.tuowei.erp.report.mapper.FinanceSettlementReportMapper.class
                );
        assertThat(constructorDependencies(InventoryReportQueryService.class))
                .contains(InventoryBalanceMapper.class, InventoryTransactionMapper.class)
                .doesNotContain(ReportQueryService.class);
        assertThat(constructorDependencies(FinanceSettlementReportQueryService.class))
                .contains(
                        com.tuowei.erp.finance.payable.mapper.PayableMapper.class,
                        com.tuowei.erp.finance.receivable.mapper.ReceivableMapper.class,
                        com.tuowei.erp.report.mapper.FinanceSettlementReportMapper.class,
                        com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport.class
                )
                .doesNotContain(ReportQueryService.class);
        assertThat(constructorDependencies(OrderReportQueryService.class))
                .contains(
                        com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper.class,
                        com.tuowei.erp.sales.order.mapper.SalesOrderMapper.class,
                        com.tuowei.erp.common.security.ScopedUserResolver.class
                )
                .doesNotContain(ReportQueryService.class);
    }

    @Test
    void orderReportQueryUsesDedicatedScopePolicyAndKeepsPreviousConstructor() {
        assertThat(Arrays.stream(OrderReportQueryService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .contains(OrderReportDataScopeService.class)
                .doesNotContain(DataScopeService.class);
        assertThat(Arrays.stream(OrderReportQueryService.class.getDeclaredConstructors())
                .filter(constructor -> !constructor.isAnnotationPresent(Autowired.class))
                .map(Constructor::getParameterCount))
                .contains(6);
        assertThat(Arrays.stream(OrderReportDataScopeService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName))
                .noneMatch(type -> type.endsWith("Mapper"));
    }

    @Test
    void inventoryReportQueryUsesDedicatedScopePolicyAndKeepsPreviousConstructor() {
        assertThat(Arrays.stream(InventoryReportQueryService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .contains(InventoryReportDataScopeService.class)
                .doesNotContain(DataScopeService.class);
        assertThat(Arrays.stream(InventoryReportQueryService.class.getDeclaredConstructors())
                .filter(constructor -> !constructor.isAnnotationPresent(Autowired.class))
                .map(Constructor::getParameterCount))
                .contains(5);
        assertThat(Arrays.stream(InventoryReportDataScopeService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName))
                .noneMatch(type -> type.endsWith("Mapper"));
    }

    @Test
    void financeSettlementReportQueryUsesDedicatedScopePolicyAndKeepsPreviousConstructor() {
        assertThat(Arrays.stream(FinanceSettlementReportQueryService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .contains(FinanceSettlementReportDataScopeService.class)
                .doesNotContain(com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport.class);
        assertThat(Arrays.stream(FinanceSettlementReportQueryService.class.getDeclaredConstructors())
                .filter(constructor -> !constructor.isAnnotationPresent(Autowired.class))
                .map(Constructor::getParameterCount))
                .contains(5);
        assertThat(Arrays.stream(FinanceSettlementReportDataScopeService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName))
                .noneMatch(type -> type.endsWith("Mapper"));
    }

    @Test
    void facadeAndInventoryCollaboratorKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "listPurchaseOrders",
                PurchaseOrderReportQuery.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "listSalesOrders",
                SalesOrderReportQuery.class
        ));
        assertReadOnly(OrderReportQueryService.class.getDeclaredMethod(
                "listPurchaseOrders",
                PurchaseOrderReportQuery.class
        ));
        assertReadOnly(OrderReportQueryService.class.getDeclaredMethod(
                "assertPurchaseOrderExportWithinLimit",
                PurchaseOrderReportQuery.class
        ));
        assertReadOnly(OrderReportQueryService.class.getDeclaredMethod(
                "streamPurchaseOrders",
                PurchaseOrderReportQuery.class,
                java.util.function.Consumer.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "listFinanceSettlements",
                FinanceSettlementReportQuery.class
        ));
        assertReadOnly(FinanceSettlementReportQueryService.class.getDeclaredMethod(
                "listFinanceSettlements",
                FinanceSettlementReportQuery.class
        ));
        assertReadOnly(FinanceSettlementReportQueryService.class.getDeclaredMethod(
                "assertFinanceSettlementExportWithinLimit",
                FinanceSettlementReportQuery.class
        ));
        assertReadOnly(FinanceSettlementReportQueryService.class.getDeclaredMethod(
                "streamFinanceSettlements",
                FinanceSettlementReportQuery.class,
                java.util.function.Consumer.class
        ));
        assertReadOnly(OrderReportQueryService.class.getDeclaredMethod(
                "listSalesOrders",
                SalesOrderReportQuery.class
        ));
        assertReadOnly(OrderReportQueryService.class.getDeclaredMethod(
                "assertSalesOrderExportWithinLimit",
                SalesOrderReportQuery.class
        ));
        assertReadOnly(OrderReportQueryService.class.getDeclaredMethod(
                "streamSalesOrders",
                SalesOrderReportQuery.class,
                java.util.function.Consumer.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "listInventoryBalances",
                InventoryBalanceReportQuery.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "assertInventoryBalanceExportWithinLimit",
                InventoryBalanceReportQuery.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "streamInventoryBalances",
                InventoryBalanceReportQuery.class,
                java.util.function.Consumer.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "listInventoryTransactions",
                InventoryTransactionReportQuery.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "assertInventoryTransactionExportWithinLimit",
                InventoryTransactionReportQuery.class
        ));
        assertReadOnly(ReportQueryService.class.getDeclaredMethod(
                "streamInventoryTransactions",
                InventoryTransactionReportQuery.class,
                java.util.function.Consumer.class
        ));
        assertReadOnly(InventoryReportQueryService.class.getDeclaredMethod(
                "listInventoryBalances",
                InventoryBalanceReportQuery.class
        ));
        assertReadOnly(InventoryReportQueryService.class.getDeclaredMethod(
                "assertInventoryBalanceExportWithinLimit",
                InventoryBalanceReportQuery.class
        ));
        assertReadOnly(InventoryReportQueryService.class.getDeclaredMethod(
                "streamInventoryBalances",
                InventoryBalanceReportQuery.class,
                java.util.function.Consumer.class
        ));
        assertReadOnly(InventoryReportQueryService.class.getDeclaredMethod(
                "listInventoryTransactions",
                InventoryTransactionReportQuery.class
        ));
        assertReadOnly(InventoryReportQueryService.class.getDeclaredMethod(
                "assertInventoryTransactionExportWithinLimit",
                InventoryTransactionReportQuery.class
        ));
        assertReadOnly(InventoryReportQueryService.class.getDeclaredMethod(
                "streamInventoryTransactions",
                InventoryTransactionReportQuery.class,
                java.util.function.Consumer.class
        ));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
