package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
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
public class OpeningReceivableImportHandler extends AbstractImportHandler {

    private final CustomerMapper customerMapper;
    private final ReceivableMapper receivableMapper;

    public OpeningReceivableImportHandler(
            ImportValidationSupport support,
            CustomerMapper customerMapper,
            ReceivableMapper receivableMapper
    ) {
        super(support);
        this.customerMapper = customerMapper;
        this.receivableMapper = receivableMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.OPENING_RECEIVABLE;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String customerCode = support.required(raw, "customer_code", errors);
        String receivableNo = support.optionalText(raw, "receivable_no");
        BigDecimal originalAmount = support.positiveAmount(raw, "original_amount", errors);
        BigDecimal settledAmount = support.optionalAmount(raw, "settled_amount", BigDecimal.ZERO, errors);
        support.date(raw, "biz_date", errors);
        if (settledAmount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse("settled_amount", "已核销金额不能小于0"));
        }
        if (originalAmount != null && settledAmount.compareTo(originalAmount) > 0) {
            errors.add(new ImportRowErrorResponse("settled_amount", "已核销金额不能超过原始金额"));
        }
        CustomerEntity customer = null;
        if (customerCode != null) {
            customer = customerMapper.selectOne(new LambdaQueryWrapper<CustomerEntity>()
                    .eq(CustomerEntity::getCompanyId, context.companyId())
                    .eq(CustomerEntity::getAccountBookId, context.accountBookId())
                    .eq(CustomerEntity::getCustomerCode, customerCode)
                    .eq(CustomerEntity::getStatus, "ACTIVE")
                    .eq(CustomerEntity::getDeletedFlag, 0));
            if (customer == null) {
                errors.add(new ImportRowErrorResponse("customer_code", "客户不存在或已停用"));
            }
        }
        if (receivableNo != null) {
            support.duplicateInFile(seen(context, "receivableNo"), receivableNo, "receivable_no", errors);
            Long count = receivableMapper.selectCount(new LambdaQueryWrapper<ReceivableEntity>()
                    .eq(ReceivableEntity::getCompanyId, context.companyId())
                    .eq(ReceivableEntity::getAccountBookId, context.accountBookId())
                    .eq(ReceivableEntity::getReceivableNo, receivableNo)
                    .eq(ReceivableEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("receivable_no", "应收单号已存在"));
            }
        }
        normalized.put("customerId", customer == null ? null : customer.getId());
        normalized.put("receivableNo", receivableNo);
        normalized.put("bizDate", support.optionalText(raw, "biz_date"));
        normalized.put("originalAmount", originalAmount);
        normalized.put("settledAmount", settledAmount);
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        rejectExistingNormalReceivables(audit);
        LocalDateTime now = audit.now();
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            String receivableNo = text(normalized, "receivableNo");
            if (receivableNo == null) {
                receivableNo = "AR-OPENING-" + job.getId() + "-" + row.getRowNo();
            }
            ReceivableEntity entity = new ReceivableEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setReceivableNo(receivableNo);
            entity.setSourceType(ImportConstants.OPENING_RECEIVABLE);
            entity.setSourceId(row.getId());
            entity.setSourceNo(receivableNo);
            entity.setDirection("INCREASE");
            entity.setCustomerId(longValue(normalized, "customerId"));
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
            receivableMapper.insert(entity);
        }
        return rows.size();
    }

    private void rejectExistingNormalReceivables(AuditMetadata audit) {
        Long count = receivableMapper.selectCount(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, audit.companyId())
                .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                .ne(ReceivableEntity::getSourceType, ImportConstants.OPENING_RECEIVABLE));
        if (exists(count)) {
            throw new IllegalArgumentException("已有正常应收数据，不能再导入期初应收");
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
