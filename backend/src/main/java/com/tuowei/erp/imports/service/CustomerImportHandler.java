package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class CustomerImportHandler extends AbstractImportHandler {

    private final CustomerMapper customerMapper;

    public CustomerImportHandler(ImportValidationSupport support, CustomerMapper customerMapper) {
        super(support);
        this.customerMapper = customerMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.CUSTOMER;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String customerCode = support.required(raw, "customer_code", errors);
        String customerName = support.required(raw, "customer_name", errors);
        if (customerCode != null) {
            support.duplicateInFile(seen(context, "customerCode"), customerCode, "customer_code", errors);
            Long count = customerMapper.selectCount(new LambdaQueryWrapper<CustomerEntity>()
                    .eq(CustomerEntity::getCompanyId, context.companyId())
                    .eq(CustomerEntity::getAccountBookId, context.accountBookId())
                    .eq(CustomerEntity::getCustomerCode, customerCode)
                    .eq(CustomerEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("customer_code", "客户编码已存在"));
            }
        }
        BigDecimal creditLimit = support.optionalAmount(raw, "credit_limit", BigDecimal.ZERO, errors);
        rejectNegative("credit_limit", creditLimit, errors);
        normalized.put("customerCode", customerCode);
        normalized.put("customerName", customerName);
        normalized.put("contactName", support.optionalText(raw, "contact_name"));
        normalized.put("contactPhone", support.optionalText(raw, "contact_phone"));
        normalized.put("settlementMethod", support.optionalText(raw, "settlement_method"));
        normalized.put("creditLimit", creditLimit);
        normalized.put("address", support.optionalText(raw, "address"));
        normalized.put("status", support.optionalText(raw, "status", "ACTIVE"));
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        LocalDateTime now = audit.now();
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            CustomerEntity entity = new CustomerEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setCustomerCode(text(normalized, "customerCode"));
            entity.setCustomerName(text(normalized, "customerName"));
            entity.setContactName(text(normalized, "contactName"));
            entity.setContactPhone(text(normalized, "contactPhone"));
            entity.setSettlementMethod(text(normalized, "settlementMethod"));
            entity.setCreditLimit(decimalValue(normalized, "creditLimit"));
            entity.setAddress(text(normalized, "address"));
            entity.setStatus(text(normalized, "status"));
            entity.setDeletedFlag(0);
            entity.setRemark(text(normalized, "remark"));
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            customerMapper.insert(entity);
        }
        return rows.size();
    }
}
