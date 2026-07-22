package com.tuowei.erp.masterdata.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.web.SupplierPayableExposureResponse;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SupplierPayableExposureService {

    private final SupplierMapper supplierMapper;
    private final PayableMapper payableMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderLineMapper lineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public SupplierPayableExposureService(SupplierMapper supplierMapper, PayableMapper payableMapper,
            PurchaseOrderMapper orderMapper, PurchaseOrderLineMapper lineMapper,
            AuditMetadataFactory auditMetadataFactory) {
        this.supplierMapper = supplierMapper;
        this.payableMapper = payableMapper;
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public SupplierPayableExposureResponse exposure(Long supplierId) {
        AuditMetadata audit = auditMetadataFactory.current();
        SupplierEntity supplier = supplierMapper.selectOne(new LambdaQueryWrapper<SupplierEntity>()
                .eq(SupplierEntity::getId, supplierId)
                .eq(SupplierEntity::getCompanyId, audit.companyId())
                .eq(SupplierEntity::getAccountBookId, audit.accountBookId())
                .eq(SupplierEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (supplier == null) {
            throw new IllegalArgumentException("供应商不存在");
        }

        BigDecimal outstanding = payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                        .eq(PayableEntity::getCompanyId, audit.companyId())
                        .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                        .eq(PayableEntity::getSupplierId, supplierId)
                        .eq(PayableEntity::getDirection, "INCREASE")
                        .eq(PayableEntity::getStatus, "UNSETTLED")
                        .eq(PayableEntity::getDeletedFlag, 0))
                .stream()
                .map(item -> ScalePrecision.zeroDefault(item.getOriginalAmount())
                        .subtract(ScalePrecision.zeroDefault(item.getSettledAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Long> orderIds = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrderEntity>()
                        .eq(PurchaseOrderEntity::getCompanyId, audit.companyId())
                        .eq(PurchaseOrderEntity::getAccountBookId, audit.accountBookId())
                        .eq(PurchaseOrderEntity::getSupplierId, supplierId)
                        .eq(PurchaseOrderEntity::getStatus, "APPROVED")
                        .eq(PurchaseOrderEntity::getDeletedFlag, 0))
                .stream().map(PurchaseOrderEntity::getId).toList();
        BigDecimal openOrders = orderIds.isEmpty() ? BigDecimal.ZERO
                : lineMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                                .in(PurchaseOrderLineEntity::getOrderId, orderIds)
                                .eq(PurchaseOrderLineEntity::getCompanyId, audit.companyId())
                                .eq(PurchaseOrderLineEntity::getAccountBookId, audit.accountBookId()))
                        .stream().map(this::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        outstanding = ScalePrecision.amount(outstanding.max(BigDecimal.ZERO));
        openOrders = ScalePrecision.amount(openOrders);
        return new SupplierPayableExposureResponse(supplierId, outstanding, openOrders,
                ScalePrecision.amount(outstanding.add(openOrders)));
    }

    private BigDecimal remainingAmount(PurchaseOrderLineEntity line) {
        BigDecimal qty = ScalePrecision.zeroDefault(line.getQty());
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal remainingQty = qty.subtract(ScalePrecision.zeroDefault(line.getReceivedQty())).max(BigDecimal.ZERO);
        BigDecimal grossAmount = ScalePrecision.zeroDefault(line.getAmount())
                .add(ScalePrecision.zeroDefault(line.getTaxAmount()));
        return grossAmount.multiply(remainingQty).divide(qty, 8, RoundingMode.HALF_UP);
    }
}
