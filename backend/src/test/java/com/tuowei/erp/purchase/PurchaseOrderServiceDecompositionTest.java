package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderTraceService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderServiceDecompositionTest {

    @Test
    void purchaseOrderServiceKeepsQueryAndTracePersistenceBehindDedicatedServices() {
        assertThat(constructorDependencies(PurchaseOrderService.class))
                .contains(
                        PurchaseOrderLineMapper.class,
                        PurchaseOrderQueryService.class,
                        PurchaseOrderTraceService.class
                )
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        PurchaseReceiptMapper.class,
                        PurchaseReturnMapper.class,
                        PayableMapper.class,
                        PaymentAllocationMapper.class,
                        PaymentMapper.class,
                        VoucherMapper.class
                );
        assertThat(constructorDependencies(PurchaseOrderQueryService.class))
                .contains(PurchaseOrderLineMapper.class)
                .doesNotContain(PurchaseOrderService.class);
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}
