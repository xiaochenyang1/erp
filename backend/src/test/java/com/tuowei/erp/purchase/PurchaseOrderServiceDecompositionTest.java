package com.tuowei.erp.purchase;

import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderTraceService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderServiceDecompositionTest {

    @Test
    void purchaseOrderServiceKeepsTracePersistenceBehindTheTraceService() {
        Set<Class<?>> constructorDependencies = Arrays.stream(PurchaseOrderService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());

        assertThat(constructorDependencies)
                .contains(PurchaseOrderTraceService.class)
                .doesNotContain(
                        PurchaseReceiptMapper.class,
                        PurchaseReturnMapper.class,
                        PayableMapper.class,
                        PaymentAllocationMapper.class,
                        PaymentMapper.class,
                        VoucherMapper.class
                );
    }
}
