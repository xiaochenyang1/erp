package com.tuowei.erp.masterdata.customer.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** Write-side create/update/state-transition for customers. */
@Service
public class CustomerPostingService {

    private final CustomerMapper customerMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CustomerQueryService customerQueryService;

    public CustomerPostingService(
            CustomerMapper customerMapper,
            AuditMetadataFactory auditMetadataFactory,
            CustomerQueryService customerQueryService
    ) {
        this.customerMapper = customerMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.customerQueryService = customerQueryService;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        CustomerEntity entity = new CustomerEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setCustomerCode(request.customerCode());
        entity.setCustomerName(request.customerName());
        entity.setCustomerType(request.customerType());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setEmail(request.email());
        entity.setSettlementMethod(request.settlementMethod());
        entity.setCreditLimit(request.creditLimit());
        entity.setCreditPeriod(normalizeCreditPeriod(request.creditPeriod()));
        entity.setAddress(request.address());
        entity.setStatus(StringUtils.hasText(request.status()) ? request.status() : "ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        customerMapper.insert(entity);
        return customerQueryService.toResponse(entity);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        CustomerEntity entity = customerQueryService.requireCustomer(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setCustomerName(request.customerName());
        entity.setCustomerType(request.customerType());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setEmail(request.email());
        entity.setSettlementMethod(request.settlementMethod());
        entity.setCreditLimit(request.creditLimit());
        entity.setCreditPeriod(normalizeCreditPeriod(request.creditPeriod()));
        entity.setAddress(request.address());
        if (StringUtils.hasText(request.status())) {
            entity.setStatus(request.status());
        }
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(customerMapper.updateById(entity), "客户已被其他操作修改，请刷新后重试");
        return customerQueryService.toResponse(entity);
    }

    @Transactional
    public CustomerResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public CustomerResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private CustomerResponse updateStatus(Long id, String status) {
        CustomerEntity entity = customerQueryService.requireCustomer(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(customerMapper.updateById(entity), "客户已被其他操作修改，请刷新后重试");
        return customerQueryService.toResponse(entity);
    }

    private Integer normalizeCreditPeriod(Integer creditPeriod) {
        if (creditPeriod == null) {
            return null;
        }
        if (creditPeriod < 0) {
            throw new IllegalArgumentException("creditPeriod不能小于0");
        }
        return creditPeriod;
    }
}
