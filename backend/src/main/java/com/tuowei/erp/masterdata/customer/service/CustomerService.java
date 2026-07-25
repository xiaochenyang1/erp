package com.tuowei.erp.masterdata.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.customer.web.CustomerResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class CustomerService {

    private static final List<String> CUSTOMER_EXPORT_HEADERS = List.of(
            "customerCode",
            "customerName",
            "customerType",
            "contactName",
            "contactPhone",
            "email",
            "settlementMethod",
            "creditLimit",
            "creditPeriod",
            "address",
            "status",
            "remark"
    );

    private final CustomerMapper customerMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public CustomerService(CustomerMapper customerMapper, AuditMetadataFactory auditMetadataFactory) {
        this.customerMapper = customerMapper;
        this.auditMetadataFactory = auditMetadataFactory;
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
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return toResponse(requireCustomer(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(CustomerPageQuery query) {
        CustomerPageQuery safeQuery = query == null ? new CustomerPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String type = normalizeNullableText(safeQuery.getType());
        String status = normalizeStatus(safeQuery.getStatus());
        String settlementMethod = normalizeNullableText(safeQuery.getSettlementMethod());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<CustomerEntity> page = new Page<>(pageNo, pageSize);
        Page<CustomerEntity> result = customerMapper.selectPage(
                page,
                buildListQuery(audit.companyId(), audit.accountBookId(), keyword, type, status, settlementMethod)
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    public StreamingResponseBody exportCustomers(CustomerPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomerPageQuery safeQuery = query == null ? new CustomerPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, CUSTOMER_EXPORT_HEADERS, rowWriter -> {
            String keyword = normalizeNullableText(safeQuery.getKeyword());
            String type = normalizeNullableText(safeQuery.getType());
            String status = normalizeStatus(safeQuery.getStatus());
            String settlementMethod = normalizeNullableText(safeQuery.getSettlementMethod());
            AuditMetadata audit = auditMetadataFactory.current();
            List<CustomerEntity> customers = customerMapper.selectList(
                    buildListQuery(audit.companyId(), audit.accountBookId(), keyword, type, status, settlementMethod)
            );
            for (CustomerEntity entity : customers) {
                rowWriter.write(customerExportRow(toResponse(entity)));
            }
        }));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        CustomerEntity entity = requireCustomer(id);
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
        return toResponse(entity);
    }

    @Transactional
    public CustomerResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public CustomerResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private CustomerEntity requireCustomer(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity entity = customerMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("客户不存在");
        }
        return entity;
    }

    private CustomerResponse updateStatus(Long id, String status) {
        CustomerEntity entity = requireCustomer(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(customerMapper.updateById(entity), "客户已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private LambdaQueryWrapper<CustomerEntity> buildListQuery(
            Long companyId,
            Long accountBookId,
            String keyword,
            String type,
            String status,
            String settlementMethod
    ) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getCompanyId, companyId)
                .eq(CustomerEntity::getAccountBookId, accountBookId)
                .eq(CustomerEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(CustomerEntity::getCustomerCode, keyword)
                    .or()
                    .like(CustomerEntity::getCustomerName, keyword)
                    .or()
                    .like(CustomerEntity::getContactName, keyword));
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(CustomerEntity::getCustomerType, type);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(CustomerEntity::getStatus, status);
        }
        if (StringUtils.hasText(settlementMethod)) {
            wrapper.eq(CustomerEntity::getSettlementMethod, settlementMethod);
        }
        return wrapper.orderByAsc(CustomerEntity::getCustomerCode);
    }

    private List<?> customerExportRow(CustomerResponse record) {
        return Arrays.asList(
                record.customerCode(),
                record.customerName(),
                record.customerType(),
                record.contactName(),
                record.contactPhone(),
                record.email(),
                record.settlementMethod(),
                record.creditLimit(),
                record.creditPeriod(),
                record.address(),
                record.status(),
                record.remark()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private CustomerResponse toResponse(CustomerEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getCustomerType(),
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getEmail(),
                entity.getSettlementMethod(),
                entity.getCreditLimit(),
                entity.getCreditPeriod(),
                entity.getAddress(),
                entity.getStatus(),
                entity.getRemark()
        );
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

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }

}
