package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptNumberService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptPostingService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptQueryService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReceiptServiceDecompositionTest {

    @Test
    void facadeKeepsReadSideSecurityBehindQueryService() {
        assertThat(constructorDependencies(PurchaseReceiptService.class))
                .hasSize(8)
                .contains(PurchaseReceiptQueryService.class, PurchaseReceiptPostingService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        ProductValidator.class,
                        PurchaseOrderMapper.class,
                        PurchaseOrderLineMapper.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        PurchaseOrderReceiptStatusService.class,
                        FinancePostingService.class,
                        AccountPeriodGuard.class,
                        QcInspectionGate.class
                );
        assertThat(constructorDependencies(PurchaseReceiptQueryService.class))
                .contains(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(
                        PurchaseReceiptService.class,
                        ProductMapper.class,
                        PurchaseOrderMapper.class
                );
        assertThat(constructorDependencies(PurchaseReceiptPostingService.class))
                .doesNotContain(
                        PurchaseReceiptService.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        PurchaseOrderMapper.class,
                        PurchaseReceiptNumberService.class
                );
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(PurchaseReceiptService.class.getDeclaredMethod("list", PurchaseReceiptPageQuery.class));
        assertReadOnly(PurchaseReceiptService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod("list", PurchaseReceiptPageQuery.class));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod(
                "assertCanView",
                PurchaseReceiptEntity.class
        ));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod(
                "assertCanView",
                com.tuowei.erp.purchase.order.model.PurchaseOrderEntity.class
        ));
    }

    @Test
    void receiptPostingKeepsRequiredWriteTransactionsOnFacadeAndCollaborator() throws NoSuchMethodException {
        assertRequiredWriteTransaction(PurchaseReceiptService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(PurchaseReceiptPostingService.class.getDeclaredMethod("post", Long.class));
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

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
