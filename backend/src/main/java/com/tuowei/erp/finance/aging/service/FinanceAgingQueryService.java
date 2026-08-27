package com.tuowei.erp.finance.aging.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Tenant-scoped reads for receivable/payable aging analysis. */
@Service
public class FinanceAgingQueryService {

    private static final Set<String> CLOSED_STATUSES = Set.of("SETTLED", "CANCELLED", "CLOSED");

    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public FinanceAgingQueryService(
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            CustomerMapper customerMapper,
            SupplierMapper supplierMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public AgingData load(LocalDate asOfDate) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
        List<ReceivableEntity> receivables = receivableMapper.selectList(
                new LambdaQueryWrapper<ReceivableEntity>()
                        .eq(ReceivableEntity::getCompanyId, audit.companyId())
                        .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                        .eq(ReceivableEntity::getDeletedFlag, 0)
                        .notIn(ReceivableEntity::getStatus, CLOSED_STATUSES)
        );
        List<PayableEntity> payables = payableMapper.selectList(
                new LambdaQueryWrapper<PayableEntity>()
                        .eq(PayableEntity::getCompanyId, audit.companyId())
                        .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                        .eq(PayableEntity::getDeletedFlag, 0)
                        .notIn(PayableEntity::getStatus, CLOSED_STATUSES)
        );
        return new AgingData(
                asOf,
                receivables,
                payables,
                loadCustomerNames(receivables, audit),
                loadSupplierNames(payables, audit)
        );
    }

    private Map<Long, String> loadCustomerNames(List<ReceivableEntity> receivables, AuditMetadata audit) {
        Set<Long> ids = receivables.stream()
                .map(ReceivableEntity::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectBatchIds(ids).stream()
                .filter(customer -> Objects.equals(customer.getCompanyId(), audit.companyId())
                        && Objects.equals(customer.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(
                        CustomerEntity::getId,
                        CustomerEntity::getCustomerName,
                        (first, ignored) -> first,
                        HashMap::new
                ));
    }

    private Map<Long, String> loadSupplierNames(List<PayableEntity> payables, AuditMetadata audit) {
        Set<Long> ids = payables.stream()
                .map(PayableEntity::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(ids).stream()
                .filter(supplier -> Objects.equals(supplier.getCompanyId(), audit.companyId())
                        && Objects.equals(supplier.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(
                        SupplierEntity::getId,
                        SupplierEntity::getSupplierName,
                        (first, ignored) -> first,
                        HashMap::new
                ));
    }

    public record AgingData(
            LocalDate asOfDate,
            List<ReceivableEntity> receivables,
            List<PayableEntity> payables,
            Map<Long, String> customerNames,
            Map<Long, String> supplierNames
    ) {
    }
}
