package com.tuowei.erp.sales.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 销售信用额度评估。审批销售订单时校验客户信用敞口是否超限。
 *
 * <p>敞口口径（均为含税金额）：
 * <ul>
 *   <li>净未结应收：已过账应收 originalAmount - settledAmount，INCREASE 记正、DECREASE 记负；</li>
 *   <li>在途订单：其它已审批（APPROVED）订单尚未发货的金额（按行级未发货数量折算）；</li>
 *   <li>本单金额：当前待审批订单的含税总额。</li>
 * </ul>
 *
 * <p>发货过账会把已发货部分转为应收，为避免重复计算，在途订单只计未发货部分。
 * 客户 creditLimit 为空或非正数时视为不限额，跳过校验。
 */
@Component
public class SalesCreditEvaluator {

    private final ReceivableMapper receivableMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;

    public SalesCreditEvaluator(
            ReceivableMapper receivableMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper
    ) {
        this.receivableMapper = receivableMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
    }

    /**
     * 校验审批当前订单后客户信用敞口是否超限，超限则抛出 {@link IllegalArgumentException}。
     */
    public void assertWithinCreditLimit(CustomerEntity customer, SalesOrderEntity currentOrder) {
        BigDecimal creditLimit = customer.getCreditLimit();
        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            // 未设额度视为不限额
            return;
        }

        SalesCreditExposure currentExposure = evaluate(customer, currentOrder.getId());
        BigDecimal currentOrderAmount = documentAmount(currentOrder.getTotalAmount(), currentOrder.getTotalTaxAmount());

        BigDecimal exposure = ScalePrecision.amount(
                currentExposure.totalExposure().add(currentOrderAmount)
        );

        if (exposure.compareTo(creditLimit) > 0) {
            throw new IllegalArgumentException(String.format(
                    "客户信用额度不足，审批后敞口 %s 将超过信用额度 %s",
                    exposure.toPlainString(),
                    ScalePrecision.amount(creditLimit).toPlainString()
            ));
        }
    }

    public SalesCreditExposure evaluate(CustomerEntity customer) {
        return evaluate(customer, null);
    }

    private SalesCreditExposure evaluate(CustomerEntity customer, Long excludeOrderId) {
        BigDecimal receivable = outstandingReceivable(customer);
        BigDecimal orders = openOrderExposure(customer, excludeOrderId);
        return new SalesCreditExposure(receivable, orders, ScalePrecision.amount(receivable.add(orders)));
    }

    /**
     * 净未结应收：INCREASE 记正、DECREASE 记负，取 originalAmount - settledAmount。
     */
    private BigDecimal outstandingReceivable(CustomerEntity customer) {
        List<ReceivableEntity> receivables = receivableMapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, customer.getCompanyId())
                .eq(ReceivableEntity::getAccountBookId, customer.getAccountBookId())
                .eq(ReceivableEntity::getCustomerId, customer.getId())
                .eq(ReceivableEntity::getDeletedFlag, 0));

        BigDecimal total = BigDecimal.ZERO;
        for (ReceivableEntity receivable : receivables) {
            BigDecimal remaining = ScalePrecision.zeroDefault(receivable.getOriginalAmount())
                    .subtract(ScalePrecision.zeroDefault(receivable.getSettledAmount()));
            if ("DECREASE".equals(receivable.getDirection())) {
                total = total.subtract(remaining);
            } else {
                total = total.add(remaining);
            }
        }
        return ScalePrecision.amount(total);
    }

    /**
     * 在途订单敞口：其它已审批订单中尚未发货的金额，排除当前订单自身。
     * 已发货部分已通过应收计入，不重复累加。
     */
    private BigDecimal openOrderExposure(CustomerEntity customer, Long excludeOrderId) {
        List<SalesOrderEntity> approvedOrders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getCompanyId, customer.getCompanyId())
                .eq(SalesOrderEntity::getAccountBookId, customer.getAccountBookId())
                .eq(SalesOrderEntity::getCustomerId, customer.getId())
                .eq(SalesOrderEntity::getStatus, "APPROVED")
                .eq(SalesOrderEntity::getDeletedFlag, 0));

        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderEntity order : approvedOrders) {
            if (excludeOrderId != null && excludeOrderId.equals(order.getId())) {
                continue;
            }
            total = total.add(undeliveredAmount(order));
        }
        return ScalePrecision.amount(total);
    }

    /**
     * 订单未发货金额：按明细行 (qty - deliveredQty) 折算行含税金额后汇总。
     * 快路径：未发货整单取总额、已完全发货取 0。
     */
    private BigDecimal undeliveredAmount(SalesOrderEntity order) {
        if ("FULL_DELIVERED".equals(order.getDeliveryStatus())) {
            return BigDecimal.ZERO;
        }
        if ("NOT_DELIVERED".equals(order.getDeliveryStatus())) {
            return documentAmount(order.getTotalAmount(), order.getTotalTaxAmount());
        }

        // 部分发货：逐行按未发货比例折算行含税金额
        List<SalesOrderLineEntity> lines = salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, order.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, order.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, order.getId()));

        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLineEntity line : lines) {
            BigDecimal qty = ScalePrecision.zeroDefault(line.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal deliveredQty = ScalePrecision.zeroDefault(line.getDeliveredQty());
            BigDecimal undeliveredQty = qty.subtract(deliveredQty);
            if (undeliveredQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal lineAmount = ScalePrecision.zeroDefault(line.getAmount())
                    .add(ScalePrecision.zeroDefault(line.getTaxAmount()));
            total = total.add(lineAmount.multiply(undeliveredQty).divide(qty, 2, RoundingMode.HALF_UP));
        }
        return ScalePrecision.amount(total);
    }

    private BigDecimal documentAmount(BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(totalAmount).add(ScalePrecision.zeroDefault(totalTaxAmount))
        );
    }
}
