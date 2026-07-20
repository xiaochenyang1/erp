package com.tuowei.erp.finance.period.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.web.AccountPeriodResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
public class AccountPeriodService {

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodCloseChecker closeChecker;

    public AccountPeriodService(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodCloseChecker closeChecker
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.closeChecker = closeChecker;
    }

    @Transactional
    public List<AccountPeriodResponse> generate(Integer year) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            String periodMonth = yearMonth.toString();
            AccountPeriodEntity existing = accountPeriodMapper.selectOne(baseWrapper(audit)
                    .eq(AccountPeriodEntity::getPeriodMonth, periodMonth));
            if (existing == null) {
                AccountPeriodEntity entity = new AccountPeriodEntity();
                entity.setCompanyId(audit.companyId());
                entity.setAccountBookId(audit.accountBookId());
                entity.setPeriodYear(year);
                entity.setPeriodMonth(periodMonth);
                entity.setStartDate(yearMonth.atDay(1));
                entity.setEndDate(yearMonth.atEndOfMonth());
                entity.setStatus("OPEN");
                entity.setCreatedBy(audit.userId());
                entity.setCreatedTime(now);
                entity.setUpdatedBy(audit.userId());
                entity.setUpdatedTime(now);
                entity.setVersion(0);
                accountPeriodMapper.insert(entity);
            }
        }
        return list(year);
    }

    @Transactional(readOnly = true)
    public List<AccountPeriodResponse> list(Integer year) {
        AuditMetadata audit = auditMetadataFactory.current();
        LambdaQueryWrapper<AccountPeriodEntity> wrapper = baseWrapper(audit);
        if (year != null) {
            wrapper.eq(AccountPeriodEntity::getPeriodYear, year);
        }
        wrapper.orderByAsc(AccountPeriodEntity::getPeriodMonth);
        return accountPeriodMapper.selectList(wrapper).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccountPeriodResponse lock(Long id) {
        AccountPeriodEntity entity = requirePeriod(id);
        if (!"OPEN".equals(entity.getStatus())) {
            throw new BusinessConflictException("只有打开期间可以锁定");
        }
        if (!closeChecker.check(id).passed()) {
            throw new BusinessConflictException("期间月结检查未通过，不能锁定");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus("LOCKED");
        entity.setLockedBy(audit.userId());
        entity.setLockedTime(audit.now());
        setUpdated(entity, audit);
        OptimisticLockGuard.requireUpdated(accountPeriodMapper.updateById(entity), "会计期间已被其他操作修改，请刷新后重试");
        return toResponse(requirePeriod(id));
    }

    @Transactional
    public AccountPeriodResponse close(Long id) {
        AccountPeriodEntity entity = requirePeriod(id);
        if (!"LOCKED".equals(entity.getStatus())) {
            throw new BusinessConflictException("只有已锁定期间可以结账");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus("CLOSED");
        entity.setClosedBy(audit.userId());
        entity.setClosedTime(audit.now());
        setUpdated(entity, audit);
        OptimisticLockGuard.requireUpdated(accountPeriodMapper.updateById(entity), "会计期间已被其他操作修改，请刷新后重试");
        return toResponse(requirePeriod(id));
    }

    @Transactional
    public AccountPeriodResponse reopen(Long id) {
        AccountPeriodEntity entity = requirePeriod(id);
        if (!"LOCKED".equals(entity.getStatus())) {
            throw new BusinessConflictException("只有已锁定期间可以解锁");
        }
        AccountPeriodEntity latestLocked = accountPeriodMapper.selectOne(baseWrapper(auditMetadataFactory.current())
                .eq(AccountPeriodEntity::getStatus, "LOCKED")
                .orderByDesc(AccountPeriodEntity::getPeriodMonth)
                .last("limit 1"));
        if (latestLocked != null && !Objects.equals(latestLocked.getId(), id)) {
            throw new BusinessConflictException("只能解锁最新锁定期间");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus("OPEN");
        entity.setReopenedBy(audit.userId());
        entity.setReopenedTime(audit.now());
        setUpdated(entity, audit);
        OptimisticLockGuard.requireUpdated(accountPeriodMapper.updateById(entity), "会计期间已被其他操作修改，请刷新后重试");
        return toResponse(requirePeriod(id));
    }

    private AccountPeriodEntity requirePeriod(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity entity = accountPeriodMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        return entity;
    }

    private LambdaQueryWrapper<AccountPeriodEntity> baseWrapper(AuditMetadata audit) {
        return new LambdaQueryWrapper<AccountPeriodEntity>()
                .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId());
    }

    private void setUpdated(AccountPeriodEntity entity, AuditMetadata audit) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private AccountPeriodResponse toResponse(AccountPeriodEntity entity) {
        return new AccountPeriodResponse(
                entity.getId(),
                entity.getPeriodYear(),
                entity.getPeriodMonth(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getLockedBy(),
                entity.getLockedTime(),
                entity.getClosedBy(),
                entity.getClosedTime(),
                entity.getReopenedBy(),
                entity.getReopenedTime(),
                entity.getRemark()
        );
    }
}
