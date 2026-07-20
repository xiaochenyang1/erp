package com.tuowei.erp.finance.statement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.statement.web.PartnerStatementLineResponse;
import com.tuowei.erp.finance.statement.web.PartnerStatementResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PartnerStatementService {

    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final ReceiptMapper receiptMapper;
    private final PaymentMapper paymentMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public PartnerStatementService(
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            ReceiptMapper receiptMapper,
            PaymentMapper paymentMapper,
            CustomerMapper customerMapper,
            SupplierMapper supplierMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.receiptMapper = receiptMapper;
        this.paymentMapper = paymentMapper;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PartnerStatementResponse statement(String partnerType, Long partnerId, LocalDate dateFrom, LocalDate dateTo) {
        if (partnerId == null) {
            throw new IllegalArgumentException("partnerId不能为空");
        }
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("日期区间不合法");
        }
        String type = partnerType == null ? "" : partnerType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "CUSTOMER" -> customerStatement(partnerId, dateFrom, dateTo);
            case "SUPPLIER" -> supplierStatement(partnerId, dateFrom, dateTo);
            default -> throw new IllegalArgumentException("partnerType 仅支持 CUSTOMER 或 SUPPLIER");
        };
    }

    private PartnerStatementResponse customerStatement(Long customerId, LocalDate dateFrom, LocalDate dateTo) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Objects.equals(customer.getCompanyId(), audit.companyId())) {
            throw new IllegalArgumentException("客户不存在");
        }
        List<RawLine> all = new ArrayList<>();
        for (ReceivableEntity r : receivableMapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, audit.companyId())
                .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceivableEntity::getCustomerId, customerId)
                .eq(ReceivableEntity::getDeletedFlag, 0)
                .ne(ReceivableEntity::getStatus, "CANCELLED"))) {
            if (r.getBizDate() == null) continue;
            BigDecimal amt = signedAr(r);
            if (amt.compareTo(BigDecimal.ZERO) == 0) continue;
            all.add(new RawLine(r.getBizDate(), "RECEIVABLE", r.getReceivableNo(),
                    amt.signum() >= 0 ? "INCREASE" : "DECREASE", amt.abs(), r.getRemark()));
        }
        for (ReceiptEntity r : receiptMapper.selectList(new LambdaQueryWrapper<ReceiptEntity>()
                .eq(ReceiptEntity::getCompanyId, audit.companyId())
                .eq(ReceiptEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceiptEntity::getCustomerId, customerId)
                .eq(ReceiptEntity::getStatus, "POSTED"))) {
            if (r.getReceiptDate() == null) continue;
            BigDecimal amt = ScalePrecision.amount(ScalePrecision.zeroDefault(r.getAmount()));
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
            all.add(new RawLine(r.getReceiptDate(), "RECEIPT", r.getReceiptNo(), "DECREASE", amt, r.getRemark()));
        }
        return build("CUSTOMER", customerId, customer.getCustomerName(), dateFrom, dateTo, all);
    }

    private PartnerStatementResponse supplierStatement(Long supplierId, LocalDate dateFrom, LocalDate dateTo) {
        AuditMetadata audit = auditMetadataFactory.current();
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null || !Objects.equals(supplier.getCompanyId(), audit.companyId())) {
            throw new IllegalArgumentException("供应商不存在");
        }
        List<RawLine> all = new ArrayList<>();
        for (PayableEntity p : payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, audit.companyId())
                .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                .eq(PayableEntity::getSupplierId, supplierId)
                .eq(PayableEntity::getDeletedFlag, 0)
                .ne(PayableEntity::getStatus, "CANCELLED"))) {
            if (p.getBizDate() == null) continue;
            BigDecimal amt = signedAp(p);
            if (amt.compareTo(BigDecimal.ZERO) == 0) continue;
            all.add(new RawLine(p.getBizDate(), "PAYABLE", p.getPayableNo(),
                    amt.signum() >= 0 ? "INCREASE" : "DECREASE", amt.abs(), p.getRemark()));
        }
        for (PaymentEntity p : paymentMapper.selectList(new LambdaQueryWrapper<PaymentEntity>()
                .eq(PaymentEntity::getCompanyId, audit.companyId())
                .eq(PaymentEntity::getAccountBookId, audit.accountBookId())
                .eq(PaymentEntity::getSupplierId, supplierId)
                .eq(PaymentEntity::getStatus, "POSTED"))) {
            if (p.getPaymentDate() == null) continue;
            BigDecimal amt = ScalePrecision.amount(ScalePrecision.zeroDefault(p.getAmount()));
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
            all.add(new RawLine(p.getPaymentDate(), "PAYMENT", p.getPaymentNo(), "DECREASE", amt, p.getRemark()));
        }
        return build("SUPPLIER", supplierId, supplier.getSupplierName(), dateFrom, dateTo, all);
    }

    private PartnerStatementResponse build(
            String partnerType, Long partnerId, String partnerName,
            LocalDate dateFrom, LocalDate dateTo, List<RawLine> all
    ) {
        all.sort(Comparator.comparing(RawLine::date).thenComparing(RawLine::docNo, Comparator.nullsLast(String::compareTo)));
        BigDecimal opening = BigDecimal.ZERO;
        for (RawLine line : all) {
            if (line.date().isBefore(dateFrom)) {
                BigDecimal signed = "INCREASE".equals(line.direction()) ? line.amount() : line.amount().negate();
                opening = opening.add(signed);
            }
        }
        opening = ScalePrecision.amount(opening);
        BigDecimal running = opening;
        BigDecimal inc = BigDecimal.ZERO;
        BigDecimal dec = BigDecimal.ZERO;
        List<PartnerStatementLineResponse> periodLines = new ArrayList<>();
        for (RawLine line : all) {
            if (line.date().isBefore(dateFrom) || line.date().isAfter(dateTo)) {
                continue;
            }
            if ("INCREASE".equals(line.direction())) {
                inc = inc.add(line.amount());
                running = running.add(line.amount());
            } else {
                dec = dec.add(line.amount());
                running = running.subtract(line.amount());
            }
            running = ScalePrecision.amount(running);
            periodLines.add(new PartnerStatementLineResponse(
                    line.date(), line.docType(), line.docNo(), line.direction(),
                    ScalePrecision.amount(line.amount()), running, line.remark()
            ));
        }
        return new PartnerStatementResponse(
                partnerType, partnerId, partnerName, dateFrom, dateTo,
                opening, ScalePrecision.amount(inc), ScalePrecision.amount(dec), running, periodLines
        );
    }

    private BigDecimal signedAr(ReceivableEntity r) {
        BigDecimal original = ScalePrecision.zeroDefault(r.getOriginalAmount());
        return "DECREASE".equalsIgnoreCase(String.valueOf(r.getDirection())) ? original.negate() : original;
    }

    private BigDecimal signedAp(PayableEntity p) {
        BigDecimal original = ScalePrecision.zeroDefault(p.getOriginalAmount());
        return "DECREASE".equalsIgnoreCase(String.valueOf(p.getDirection())) ? original.negate() : original;
    }

    private record RawLine(LocalDate date, String docType, String docNo, String direction, BigDecimal amount, String remark) {
    }
}
