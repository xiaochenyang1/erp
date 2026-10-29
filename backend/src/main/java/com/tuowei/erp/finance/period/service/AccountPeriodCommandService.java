package com.tuowei.erp.finance.period.service;

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

/** Write side of account-period administration: generate periods and drive lock/close/reopen state transitions with optimistic locking. */
@Service
public class AccountPeriodCommandService {

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodCloseChecker closeChecker;
    private final AccountPeriodCloseSnapshotService closeSnapshotService;
    private final AccountPeriodQueryService queryService;

    public AccountPeriodCommandService(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodCloseChecker closeChecker,
            AccountPeriodCloseSnapshotService closeSnapshotService,
            AccountPeriodQueryService queryService
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.closeChecker = closeChecker;
        this.closeSnapshotService = closeSnapshotService;
        this.queryService = queryService;
    }

    @Transactional
    public List<AccountPeriodResponse> generate(Integer year) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            String periodMonth = yearMonth.toString();
            AccountPeriodEntity existing = accountPeriodMapper.selectOne(queryService.baseWrapper(audit)
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
        return queryService.list(year);
    }

    @Transactional
    public AccountPeriodResponse lock(Long id) {
        AccountPeriodEntity entity = queryService.requirePeriod(id);
        if (!"OPEN".equals(entity.getStatus())) {
            throw new BusinessConflictException("只有打开期间可以锁定");
        }
        var check = closeChecker.check(id);
        if (!check.passed()) {
            throw new BusinessConflictException("期间月结检查未通过，不能锁定");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        closeSnapshotService.capture("LOCK", check, audit);
        entity.setStatus("LOCKED");
        entity.setLockedBy(audit.userId());
        entity.setLockedTime(audit.now());
        setUpdated(entity, audit);
        OptimisticLockGuard.requireUpdated(accountPeriodMapper.updateById(entity), "会计期间已被其他操作修改，请刷新后重试");
        return queryService.toResponse(queryService.requirePeriod(id));
    }

    @Transactional
    public AccountPeriodResponse close(Long id) {
        AccountPeriodEntity entity = queryService.requirePeriod(id);
        if (!"LOCKED".equals(entity.getStatus())) {
            throw new BusinessConflictException("只有已锁定期间可以结账");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        ensureEarlierPeriodsClosed(entity, audit);
        var check = closeChecker.check(id);
        if (!check.passed()) {
            throw new BusinessConflictException("期间月结检查未通过，不能结账");
        }
        closeSnapshotService.capture("CLOSE", check, audit);
        entity.setStatus("CLOSED");
        entity.setClosedBy(audit.userId());
        entity.setClosedTime(audit.now());
        setUpdated(entity, audit);
        OptimisticLockGuard.requireUpdated(accountPeriodMapper.updateById(entity), "会计期间已被其他操作修改，请刷新后重试");
        return queryService.toResponse(queryService.requirePeriod(id));
    }

    @Transactional
    public AccountPeriodResponse reopen(Long id) {
        AccountPeriodEntity entity = queryService.requirePeriod(id);
        if (!"LOCKED".equals(entity.getStatus())) {
            throw new BusinessConflictException("只有已锁定期间可以解锁");
        }
        AccountPeriodEntity latestLocked = accountPeriodMapper.selectOne(queryService.baseWrapper(auditMetadataFactory.current())
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
        return queryService.toResponse(queryService.requirePeriod(id));
    }

    private void setUpdated(AccountPeriodEntity entity, AuditMetadata audit) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private void ensureEarlierPeriodsClosed(AccountPeriodEntity entity, AuditMetadata audit) {
        long earlierUnclosed = accountPeriodMapper.selectCount(queryService.baseWrapper(audit)
                .lt(AccountPeriodEntity::getPeriodMonth, entity.getPeriodMonth())
                .ne(AccountPeriodEntity::getStatus, "CLOSED"));
        if (earlierUnclosed > 0) {
            throw new BusinessConflictException("存在更早的未结账期间，请按期间顺序结账");
        }
    }
}
