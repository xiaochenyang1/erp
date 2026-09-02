package com.tuowei.erp.finance.period.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.period.mapper.AccountPeriodCloseSnapshotItemMapper;
import com.tuowei.erp.finance.period.mapper.AccountPeriodCloseSnapshotMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodCloseSnapshotEntity;
import com.tuowei.erp.finance.period.model.AccountPeriodCloseSnapshotItemEntity;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseCheckResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseSnapshotItemResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseSnapshotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountPeriodCloseSnapshotService {
    private final AccountPeriodCloseSnapshotMapper snapshotMapper;
    private final AccountPeriodCloseSnapshotItemMapper itemMapper;

    public AccountPeriodCloseSnapshotService(
            AccountPeriodCloseSnapshotMapper snapshotMapper,
            AccountPeriodCloseSnapshotItemMapper itemMapper
    ) {
        this.snapshotMapper = snapshotMapper;
        this.itemMapper = itemMapper;
    }

    @Transactional
    public void capture(String actionType, AccountPeriodCloseCheckResponse check, AuditMetadata audit) {
        AccountPeriodCloseSnapshotEntity snapshot = new AccountPeriodCloseSnapshotEntity();
        snapshot.setCompanyId(audit.companyId());
        snapshot.setAccountBookId(audit.accountBookId());
        snapshot.setPeriodId(check.periodId());
        snapshot.setActionType(actionType);
        snapshot.setPassedFlag(check.passed() ? 1 : 0);
        snapshot.setIssueCount(check.issues().size());
        snapshot.setCheckedBy(audit.userId());
        snapshot.setCheckedTime(audit.now());
        snapshot.setCreatedBy(audit.userId());
        snapshot.setCreatedTime(audit.now());
        snapshot.setUpdatedBy(audit.userId());
        snapshot.setUpdatedTime(audit.now());
        snapshot.setVersion(0);
        snapshotMapper.insert(snapshot);

        for (var checkItem : check.checks()) {
            AccountPeriodCloseSnapshotItemEntity item = new AccountPeriodCloseSnapshotItemEntity();
            item.setCompanyId(audit.companyId());
            item.setAccountBookId(audit.accountBookId());
            item.setSnapshotId(snapshot.getId());
            item.setCheckCode(checkItem.code());
            item.setCheckTitle(checkItem.title());
            item.setCheckCategory(checkItem.category());
            item.setPassedFlag(checkItem.passed() ? 1 : 0);
            item.setCheckMessage(checkItem.message());
            item.setMetric(checkItem.metric());
            item.setCreatedBy(audit.userId());
            item.setCreatedTime(audit.now());
            item.setUpdatedBy(audit.userId());
            item.setUpdatedTime(audit.now());
            item.setVersion(0);
            itemMapper.insert(item);
        }
    }

    @Transactional(readOnly = true)
    public List<AccountPeriodCloseSnapshotResponse> list(Long periodId, Long companyId, Long accountBookId) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<AccountPeriodCloseSnapshotEntity>()
                        .eq(AccountPeriodCloseSnapshotEntity::getCompanyId, companyId)
                        .eq(AccountPeriodCloseSnapshotEntity::getAccountBookId, accountBookId)
                        .eq(AccountPeriodCloseSnapshotEntity::getPeriodId, periodId)
                        .orderByDesc(AccountPeriodCloseSnapshotEntity::getCheckedTime)
                        .orderByDesc(AccountPeriodCloseSnapshotEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AccountPeriodCloseSnapshotResponse toResponse(AccountPeriodCloseSnapshotEntity snapshot) {
        List<AccountPeriodCloseSnapshotItemResponse> items = itemMapper.selectList(
                        new LambdaQueryWrapper<AccountPeriodCloseSnapshotItemEntity>()
                                .eq(AccountPeriodCloseSnapshotItemEntity::getCompanyId, snapshot.getCompanyId())
                                .eq(AccountPeriodCloseSnapshotItemEntity::getAccountBookId, snapshot.getAccountBookId())
                                .eq(AccountPeriodCloseSnapshotItemEntity::getSnapshotId, snapshot.getId())
                                .orderByAsc(AccountPeriodCloseSnapshotItemEntity::getId))
                .stream()
                .map(item -> new AccountPeriodCloseSnapshotItemResponse(
                        item.getCheckCode(),
                        item.getCheckTitle(),
                        item.getCheckCategory(),
                        Integer.valueOf(1).equals(item.getPassedFlag()),
                        item.getCheckMessage(),
                        item.getMetric()
                ))
                .toList();
        return new AccountPeriodCloseSnapshotResponse(
                snapshot.getId(),
                snapshot.getPeriodId(),
                snapshot.getActionType(),
                Integer.valueOf(1).equals(snapshot.getPassedFlag()),
                snapshot.getIssueCount(),
                snapshot.getCheckedBy(),
                snapshot.getCheckedTime(),
                items
        );
    }
}
