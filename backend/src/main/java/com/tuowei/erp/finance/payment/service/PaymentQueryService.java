package com.tuowei.erp.finance.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.payment.web.PaymentAllocationResponse;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PaymentQueryService {
    private final PaymentMapper paymentMapper;
    private final PaymentAllocationMapper paymentAllocationMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public PaymentQueryService(PaymentMapper paymentMapper, PaymentAllocationMapper paymentAllocationMapper, AuditMetadataFactory auditMetadataFactory) {
        this.paymentMapper = paymentMapper; this.paymentAllocationMapper = paymentAllocationMapper; this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PaymentResponse detail(Long id) { PaymentEntity payment = requirePayment(id); return toResponse(payment, allocationResponses(payment)); }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(PaymentPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current(); PaymentPageQuery safe = query == null ? new PaymentPageQuery() : query;
        Page<PaymentEntity> page = new Page<>(normalizePageNo(safe.getPageNo()), normalizePageSize(safe.getPageSize()));
        LambdaQueryWrapper<PaymentEntity> wrapper = new LambdaQueryWrapper<PaymentEntity>()
                .eq(PaymentEntity::getCompanyId, audit.companyId()).eq(PaymentEntity::getAccountBookId, audit.accountBookId()).eq(PaymentEntity::getDeletedFlag, 0);
        if (safe.getSupplierId() != null) wrapper.eq(PaymentEntity::getSupplierId, safe.getSupplierId());
        if (StringUtils.hasText(safe.getStatus())) wrapper.eq(PaymentEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        wrapper.orderByDesc(PaymentEntity::getPaymentDate).orderByDesc(PaymentEntity::getId);
        Page<PaymentEntity> result = paymentMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream().map(payment -> toResponse(payment, allocationResponses(payment))).toList());
    }

    PaymentEntity requirePayment(Long id) {
        PaymentEntity payment = paymentMapper.selectById(id); AuditMetadata audit = auditMetadataFactory.current();
        if (payment == null || payment.getDeletedFlag() == null || payment.getDeletedFlag() != 0 || !Objects.equals(payment.getCompanyId(), audit.companyId()) || !Objects.equals(payment.getAccountBookId(), audit.accountBookId())) throw new IllegalArgumentException("付款单不存在");
        return payment;
    }
    List<PaymentAllocationEntity> allocations(PaymentEntity payment) { return paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>().eq(PaymentAllocationEntity::getCompanyId, payment.getCompanyId()).eq(PaymentAllocationEntity::getAccountBookId, payment.getAccountBookId()).eq(PaymentAllocationEntity::getPaymentId, payment.getId()).orderByAsc(PaymentAllocationEntity::getId)); }
    PaymentResponse toResponse(PaymentEntity payment) { return toResponse(payment, allocationResponses(payment)); }
    private List<PaymentAllocationResponse> allocationResponses(PaymentEntity payment) { return allocations(payment).stream().map(entity -> new PaymentAllocationResponse(entity.getId(), entity.getPayableId(), entity.getAmount())).toList(); }
    private PaymentResponse toResponse(PaymentEntity payment, List<PaymentAllocationResponse> allocations) { return new PaymentResponse(payment.getId(), payment.getPaymentNo(), payment.getSupplierId(), payment.getPaymentDate(), payment.getAmount(), payment.getAllocatedAmount(), payment.getStatus(), payment.getRemark(), payment.getCancelReason(), payment.getCancelledBy(), payment.getCancelledTime(), allocations); }
    private long normalizePageNo(Integer value) { return value == null || value < 1 ? 1L : value; }
    private long normalizePageSize(Integer value) { return value == null || value < 1 ? 20L : Math.min(value, 200); }
}
