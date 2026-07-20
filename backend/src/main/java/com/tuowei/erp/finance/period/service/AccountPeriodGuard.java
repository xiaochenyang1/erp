package com.tuowei.erp.finance.period.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AccountPeriodGuard {

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public AccountPeriodGuard(AccountPeriodMapper accountPeriodMapper, AuditMetadataFactory auditMetadataFactory) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    public void requireOpen(LocalDate bizDate, String action) {
        if (bizDate == null) {
            throw new IllegalArgumentException("业务日期不能为空");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity period = accountPeriodMapper.selectOne(new LambdaQueryWrapper<AccountPeriodEntity>()
                .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId())
                .le(AccountPeriodEntity::getStartDate, bizDate)
                .ge(AccountPeriodEntity::getEndDate, bizDate));
        if (period == null) {
            throw new BusinessConflictException("业务日期 " + bizDate + " 未生成会计期间，不能执行" + action);
        }
        if ("OPEN".equals(period.getStatus())) {
            return;
        }
        throw new BusinessConflictException(
                "业务日期 " + bizDate + " 所属期间 " + period.getPeriodMonth()
                        + " 已" + statusText(period.getStatus()) + "，不能执行" + action
        );
    }

    private String statusText(String status) {
        return switch (status) {
            case "LOCKED" -> "锁定";
            case "CLOSED" -> "结账";
            default -> "冻结";
        };
    }
}
