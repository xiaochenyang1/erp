package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class SupplierImportHandler extends AbstractImportHandler {

    private final SupplierMapper supplierMapper;

    public SupplierImportHandler(ImportValidationSupport support, SupplierMapper supplierMapper) {
        super(support);
        this.supplierMapper = supplierMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.SUPPLIER;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String supplierCode = support.required(raw, "supplier_code", errors);
        String supplierName = support.required(raw, "supplier_name", errors);
        if (supplierCode != null) {
            support.duplicateInFile(seen(context, "supplierCode"), supplierCode, "supplier_code", errors);
            Long count = supplierMapper.selectCount(new LambdaQueryWrapper<SupplierEntity>()
                    .eq(SupplierEntity::getCompanyId, context.companyId())
                    .eq(SupplierEntity::getAccountBookId, context.accountBookId())
                    .eq(SupplierEntity::getSupplierCode, supplierCode)
                    .eq(SupplierEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("supplier_code", "供应商编码已存在"));
            }
        }
        normalized.put("supplierCode", supplierCode);
        normalized.put("supplierName", supplierName);
        normalized.put("contactName", support.optionalText(raw, "contact_name"));
        normalized.put("contactPhone", support.optionalText(raw, "contact_phone"));
        normalized.put("settlementMethod", support.optionalText(raw, "settlement_method"));
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
            SupplierEntity entity = new SupplierEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setSupplierCode(text(normalized, "supplierCode"));
            entity.setSupplierName(text(normalized, "supplierName"));
            entity.setContactName(text(normalized, "contactName"));
            entity.setContactPhone(text(normalized, "contactPhone"));
            entity.setSettlementMethod(text(normalized, "settlementMethod"));
            entity.setAddress(text(normalized, "address"));
            entity.setStatus(text(normalized, "status"));
            entity.setDeletedFlag(0);
            entity.setRemark(text(normalized, "remark"));
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            supplierMapper.insert(entity);
        }
        return rows.size();
    }
}
