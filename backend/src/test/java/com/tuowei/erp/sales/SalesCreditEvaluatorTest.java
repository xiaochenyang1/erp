package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.service.SalesCreditEvaluator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesCreditEvaluatorTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long CUSTOMER_ID = 3101L;
    private static final Long CURRENT_ORDER_ID = 4001L;

    private final ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
    private final SalesOrderMapper salesOrderMapper = mock(SalesOrderMapper.class);
    private final SalesOrderLineMapper salesOrderLineMapper = mock(SalesOrderLineMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ReceivableEntity.class);
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void skipsCheckWhenCreditLimitIsNull() {
        CustomerEntity customer = customer(null);

        assertThatCode(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("999999"), BigDecimal.ZERO)))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsCheckWhenCreditLimitIsZero() {
        CustomerEntity customer = customer(BigDecimal.ZERO);

        assertThatCode(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("999999"), BigDecimal.ZERO)))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsWhenExposureWithinLimit() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        stubEmptyReceivables();
        stubNoOtherApprovedOrders();

        assertThatCode(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("800"), new BigDecimal("104"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenCurrentOrderAloneExceedsLimit() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        stubEmptyReceivables();
        stubNoOtherApprovedOrders();

        assertThatThrownBy(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("1000"), new BigDecimal("130"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("信用额度不足");
    }

    @Test
    void accumulatesOutstandingReceivableWithCurrentOrder() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        // 未结应收 600 (originalAmount 800 - settledAmount 200)
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable("INCREASE", new BigDecimal("800"), new BigDecimal("200"))
        ));
        stubNoOtherApprovedOrders();

        // 600 + 本单 500 = 1100 > 1000
        assertThatThrownBy(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("500"), BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("信用额度不足");
    }

    @Test
    void salesReturnReceivableReducesExposure() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        // 净应收 = 900(INCREASE) - 400(DECREASE 退货) = 500
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable("INCREASE", new BigDecimal("900"), BigDecimal.ZERO),
                receivable("DECREASE", new BigDecimal("400"), BigDecimal.ZERO)
        ));
        stubNoOtherApprovedOrders();

        // 500 + 本单 400 = 900 <= 1000，放行
        assertThatCode(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("400"), BigDecimal.ZERO)))
                .doesNotThrowAnyException();
    }

    @Test
    void includesUndeliveredAmountOfOtherApprovedOrders() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        stubEmptyReceivables();
        // 另一张已审批未发货订单，未发货金额 700
        SalesOrderEntity other = approvedOrder(5002L, "NOT_DELIVERED", new BigDecimal("700"), BigDecimal.ZERO);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(other));

        // 700 + 本单 400 = 1100 > 1000
        assertThatThrownBy(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("400"), BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("信用额度不足");
    }

    @Test
    void excludesCurrentOrderFromOpenOrderExposure() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        stubEmptyReceivables();
        // 查询已审批订单时返回了当前订单自身（当前单可能已处于流程中），应被排除，不重复计
        SalesOrderEntity self = approvedOrder(CURRENT_ORDER_ID, "NOT_DELIVERED", new BigDecimal("900"), BigDecimal.ZERO);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(self));

        // 仅本单 900 <= 1000，若未排除自身则会变成 1800 而误拒
        assertThatCode(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("900"), BigDecimal.ZERO)))
                .doesNotThrowAnyException();
    }

    @Test
    void partiallyDeliveredOrderCountsOnlyUndeliveredPortion() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        stubEmptyReceivables();
        // 另一张部分发货订单：整单含税 1000，已发一半 -> 未发货 500
        SalesOrderEntity other = approvedOrder(5003L, "PARTIAL_DELIVERED", new BigDecimal("1000"), BigDecimal.ZERO);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(other));
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(
                orderLine(new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("1000"), BigDecimal.ZERO)
        ));

        // 未发货 500 + 本单 400 = 900 <= 1000，放行
        assertThatCode(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("400"), BigDecimal.ZERO)))
                .doesNotThrowAnyException();

        // 未发货 500 + 本单 550 = 1050 > 1000，拦截
        assertThatThrownBy(() -> evaluator().assertWithinCreditLimit(customer, currentOrder(new BigDecimal("550"), BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("信用额度不足");
    }

    @Test
    void exposesReceivableAndOpenOrderComponentsForCustomerOverview() {
        CustomerEntity customer = customer(new BigDecimal("2000"));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable("INCREASE", new BigDecimal("800"), new BigDecimal("200"))));
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(
                approvedOrder(5004L, "NOT_DELIVERED", new BigDecimal("700"), BigDecimal.ZERO)));

        var exposure = evaluator().evaluate(customer);

        assertThat(exposure.outstandingReceivable()).isEqualByComparingTo("600.00");
        assertThat(exposure.openOrderExposure()).isEqualByComparingTo("700.00");
        assertThat(exposure.totalExposure()).isEqualByComparingTo("1300.00");
    }

    @Test
    void previewReportsProjectedExposureAndRemainingCredit() {
        CustomerEntity customer = customer(new BigDecimal("2000"));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable("INCREASE", new BigDecimal("800"), new BigDecimal("200"))));
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(
                approvedOrder(5004L, "NOT_DELIVERED", new BigDecimal("700"), BigDecimal.ZERO)));

        var preview = evaluator().preview(customer, new BigDecimal("260"));

        assertThat(preview.creditLimit()).isEqualByComparingTo("2000.00");
        assertThat(preview.currentExposure()).isEqualByComparingTo("1300.00");
        assertThat(preview.orderAmount()).isEqualByComparingTo("260.00");
        assertThat(preview.projectedExposure()).isEqualByComparingTo("1560.00");
        assertThat(preview.availableCredit()).isEqualByComparingTo("700.00");
        assertThat(preview.projectedAvailableCredit()).isEqualByComparingTo("440.00");
        assertThat(preview.exceeded()).isFalse();
    }

    @Test
    void customActionLabelAppearsInCreditLimitError() {
        CustomerEntity customer = customer(new BigDecimal("1000"));
        stubEmptyReceivables();
        stubNoOtherApprovedOrders();

        assertThatThrownBy(() -> evaluator().assertWithinCreditLimit(
                customer,
                currentOrder(new BigDecimal("1000"), new BigDecimal("130")),
                "提交"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("提交后敞口")
                .hasMessageContaining("信用额度不足");
    }

    private SalesCreditEvaluator evaluator() {
        return new SalesCreditEvaluator(receivableMapper, salesOrderMapper, salesOrderLineMapper);
    }

    private void stubEmptyReceivables() {
        when(receivableMapper.selectList(any())).thenReturn(List.of());
    }

    private void stubNoOtherApprovedOrders() {
        when(salesOrderMapper.selectList(any())).thenReturn(List.of());
    }

    private CustomerEntity customer(BigDecimal creditLimit) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setCompanyId(COMPANY_ID);
        customer.setAccountBookId(ACCOUNT_BOOK_ID);
        customer.setCustomerName("credit customer");
        customer.setCreditLimit(creditLimit);
        customer.setStatus("ACTIVE");
        customer.setDeletedFlag(0);
        return customer;
    }

    private SalesOrderEntity currentOrder(BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(CURRENT_ORDER_ID);
        order.setCompanyId(COMPANY_ID);
        order.setAccountBookId(ACCOUNT_BOOK_ID);
        order.setCustomerId(CUSTOMER_ID);
        order.setTotalAmount(totalAmount);
        order.setTotalTaxAmount(totalTaxAmount);
        return order;
    }

    private SalesOrderEntity approvedOrder(Long id, String deliveryStatus, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(id);
        order.setCompanyId(COMPANY_ID);
        order.setAccountBookId(ACCOUNT_BOOK_ID);
        order.setCustomerId(CUSTOMER_ID);
        order.setStatus("APPROVED");
        order.setApprovalStatus("APPROVED");
        order.setDeliveryStatus(deliveryStatus);
        order.setTotalAmount(totalAmount);
        order.setTotalTaxAmount(totalTaxAmount);
        order.setDeletedFlag(0);
        return order;
    }

    private SalesOrderLineEntity orderLine(BigDecimal qty, BigDecimal deliveredQty, BigDecimal amount, BigDecimal taxAmount) {
        SalesOrderLineEntity line = new SalesOrderLineEntity();
        line.setCompanyId(COMPANY_ID);
        line.setAccountBookId(ACCOUNT_BOOK_ID);
        line.setQty(qty);
        line.setDeliveredQty(deliveredQty);
        line.setAmount(amount);
        line.setTaxAmount(taxAmount);
        return line;
    }

    private ReceivableEntity receivable(String direction, BigDecimal originalAmount, BigDecimal settledAmount) {
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setCompanyId(COMPANY_ID);
        receivable.setAccountBookId(ACCOUNT_BOOK_ID);
        receivable.setCustomerId(CUSTOMER_ID);
        receivable.setDirection(direction);
        receivable.setOriginalAmount(originalAmount);
        receivable.setSettledAmount(settledAmount);
        receivable.setDeletedFlag(0);
        return receivable;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
