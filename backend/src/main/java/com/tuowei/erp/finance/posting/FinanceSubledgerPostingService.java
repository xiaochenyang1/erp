package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class FinanceSubledgerPostingService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;

    public FinanceSubledgerPostingService(
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            CustomerMapper customerMapper,
            SupplierMapper supplierMapper
    ) {
        this.payableMapper = payableMapper;
        this.receivableMapper = receivableMapper;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPayableIfAbsent(
            String sourceType,
            Long sourceId,
            String sourceNo,
            String direction,
            Long supplierId,
            LocalDate bizDate,
            BigDecimal amount,
            String remark,
            AuditMetadata audit
    ) {
        if (payableMapper.selectCount(sourceWrapper(
                audit,
                sourceType,
                sourceId,
                PayableEntity::getCompanyId,
                PayableEntity::getAccountBookId,
                PayableEntity::getSourceType,
                PayableEntity::getSourceId
        )) > 0) {
            return;
        }
        LocalDateTime now = audit.now();
        PayableEntity entity = new PayableEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setPayableNo("AP-" + sourceType + "-" + sourceId);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setSourceNo(sourceNo);
        entity.setDirection(direction);
        entity.setSupplierId(supplierId);
        entity.setBizDate(bizDate);
        entity.setDueDate(resolveSupplierDueDate(supplierId, bizDate, audit));
        entity.setOriginalAmount(amount);
        entity.setSettledAmount(ZERO_AMOUNT);
        entity.setStatus("INCREASE".equals(direction) ? "UNSETTLED" : "OFFSET");
        setAudit(entity, remark, audit, now);
        payableMapper.insert(entity);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordReceivableIfAbsent(
            String sourceType,
            Long sourceId,
            String sourceNo,
            String direction,
            Long customerId,
            LocalDate bizDate,
            BigDecimal amount,
            String remark,
            AuditMetadata audit
    ) {
        if (receivableMapper.selectCount(sourceWrapper(
                audit,
                sourceType,
                sourceId,
                ReceivableEntity::getCompanyId,
                ReceivableEntity::getAccountBookId,
                ReceivableEntity::getSourceType,
                ReceivableEntity::getSourceId
        )) > 0) {
            return;
        }
        LocalDateTime now = audit.now();
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setReceivableNo("AR-" + sourceType + "-" + sourceId);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setSourceNo(sourceNo);
        entity.setDirection(direction);
        entity.setCustomerId(customerId);
        entity.setBizDate(bizDate);
        entity.setDueDate(resolveCustomerDueDate(customerId, bizDate, audit));
        entity.setOriginalAmount(amount);
        entity.setSettledAmount(ZERO_AMOUNT);
        entity.setStatus("INCREASE".equals(direction) ? "UNSETTLED" : "OFFSET");
        setAudit(entity, remark, audit, now);
        receivableMapper.insert(entity);
    }

    private LocalDate resolveCustomerDueDate(Long customerId, LocalDate bizDate, AuditMetadata audit) {
        if (bizDate == null) {
            return null;
        }
        if (customerId == null) {
            return bizDate;
        }
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null
                || !Objects.equals(customer.getCompanyId(), audit.companyId())
                || !Objects.equals(customer.getAccountBookId(), audit.accountBookId())) {
            return bizDate;
        }
        return addCreditPeriod(bizDate, customer.getCreditPeriod());
    }

    private LocalDate resolveSupplierDueDate(Long supplierId, LocalDate bizDate, AuditMetadata audit) {
        if (bizDate == null) {
            return null;
        }
        if (supplierId == null) {
            return bizDate;
        }
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null
                || !Objects.equals(supplier.getCompanyId(), audit.companyId())
                || !Objects.equals(supplier.getAccountBookId(), audit.accountBookId())) {
            return bizDate;
        }
        return addCreditPeriod(bizDate, supplier.getCreditPeriod());
    }

    private LocalDate addCreditPeriod(LocalDate bizDate, Integer creditPeriod) {
        int days = creditPeriod == null ? 0 : Math.max(creditPeriod, 0);
        return bizDate.plusDays(days);
    }

    private <T> LambdaQueryWrapper<T> sourceWrapper(
            AuditMetadata audit,
            String sourceType,
            Long sourceId,
            SFunction<T, Long> companyIdColumn,
            SFunction<T, Long> accountBookIdColumn,
            SFunction<T, String> sourceTypeColumn,
            SFunction<T, Long> sourceIdColumn
    ) {
        return new LambdaQueryWrapper<T>()
                .eq(companyIdColumn, audit.companyId())
                .eq(accountBookIdColumn, audit.accountBookId())
                .eq(sourceTypeColumn, sourceType)
                .eq(sourceIdColumn, sourceId);
    }

    private void setAudit(PayableEntity entity, String remark, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setRemark(remark);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void setAudit(ReceivableEntity entity, String remark, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setRemark(remark);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
