package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class OpeningPayableImportHandler extends AbstractImportHandler {

    private final SupplierMapper supplierMapper;
    private final PayableMapper payableMapper;

    public OpeningPayableImportHandler(
            ImportValidationSupport support,
            SupplierMapper supplierMapper,
            PayableMapper payableMapper
    ) {
        super(support);
        this.supplierMapper = supplierMapper;
        this.payableMapper = payableMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.OPENING_PAYABLE;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String supplierCode = support.required(raw, "supplier_code", errors);
        String payableNo = support.optionalText(raw, "payable_no");
        BigDecimal originalAmount = support.positiveAmount(raw, "original_amount", errors);
        BigDecimal settledAmount = support.optionalAmount(raw, "settled_amount", BigDecimal.ZERO, errors);
        support.date(raw, "biz_date", errors);
        if (settledAmount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse("settled_amount", "已核销金额不能小于0"));
        }
        if (originalAmount != null && settledAmount.compareTo(originalAmount) > 0) {
            errors.add(new ImportRowErrorResponse("settled_amount", "已核销金额不能超过原始金额"));
        }
        SupplierEntity supplier = null;
        if (supplierCode != null) {
            supplier = supplierMapper.selectOne(new LambdaQueryWrapper<SupplierEntity>()
                    .eq(SupplierEntity::getCompanyId, context.companyId())
                    .eq(SupplierEntity::getAccountBookId, context.accountBookId())
                    .eq(SupplierEntity::getSupplierCode, supplierCode)
                    .eq(SupplierEntity::getStatus, "ACTIVE")
                    .eq(SupplierEntity::getDeletedFlag, 0));
            if (supplier == null) {
                errors.add(new ImportRowErrorResponse("supplier_code", "供应商不存在或已停用"));
            }
        }
        if (payableNo != null) {
            support.duplicateInFile(seen(context, "payableNo"), payableNo, "payable_no", errors);
            Long count = payableMapper.selectCount(new LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getCompanyId, context.companyId())
                    .eq(PayableEntity::getAccountBookId, context.accountBookId())
                    .eq(PayableEntity::getPayableNo, payableNo)
                    .eq(PayableEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("payable_no", "应付单号已存在"));
            }
        }
        normalized.put("supplierId", supplier == null ? null : supplier.getId());
        normalized.put("payableNo", payableNo);
        normalized.put("bizDate", support.optionalText(raw, "biz_date"));
        normalized.put("originalAmount", originalAmount);
        normalized.put("settledAmount", settledAmount);
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        rejectExistingNormalPayables(audit);
        LocalDateTime now = audit.now();
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            String payableNo = text(normalized, "payableNo");
            if (payableNo == null) {
                payableNo = "AP-OPENING-" + job.getId() + "-" + row.getRowNo();
            }
            PayableEntity entity = new PayableEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setPayableNo(payableNo);
            entity.setSourceType(ImportConstants.OPENING_PAYABLE);
            entity.setSourceId(row.getId());
            entity.setSourceNo(payableNo);
            entity.setDirection("INCREASE");
            entity.setSupplierId(longValue(normalized, "supplierId"));
            entity.setBizDate(dateValue(normalized, "bizDate"));
            entity.setOriginalAmount(decimalValue(normalized, "originalAmount"));
            entity.setSettledAmount(decimalValue(normalized, "settledAmount"));
            entity.setStatus(settlementStatus(entity.getOriginalAmount(), entity.getSettledAmount()));
            entity.setDeletedFlag(0);
            entity.setRemark(text(normalized, "remark"));
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            payableMapper.insert(entity);
        }
        return rows.size();
    }

    private void rejectExistingNormalPayables(AuditMetadata audit) {
        Long count = payableMapper.selectCount(new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, audit.companyId())
                .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                .ne(PayableEntity::getSourceType, ImportConstants.OPENING_PAYABLE));
        if (exists(count)) {
            throw new IllegalArgumentException("已有正常应付数据，不能再导入期初应付");
        }
    }

    private String settlementStatus(BigDecimal originalAmount, BigDecimal settledAmount) {
        BigDecimal settled = support.scaleAmount(settledAmount);
        if (settled.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNSETTLED";
        }
        if (settled.compareTo(support.scaleAmount(originalAmount)) >= 0) {
            return "SETTLED";
        }
        return "PARTIALLY_SETTLED";
    }
}
