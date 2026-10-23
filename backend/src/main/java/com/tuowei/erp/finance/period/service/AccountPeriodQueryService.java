package com.tuowei.erp.finance.period.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.web.AccountPeriodResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseSnapshotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Read side of account-period administration: period listings, close-snapshot history, and shared tenant/query/response helpers. */
@Service
public class AccountPeriodQueryService {

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodCloseSnapshotService closeSnapshotService;

    public AccountPeriodQueryService(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodCloseSnapshotService closeSnapshotService
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.closeSnapshotService = closeSnapshotService;
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

    @Transactional(readOnly = true)
    public List<AccountPeriodCloseSnapshotResponse> closeSnapshots(Long id) {
        AccountPeriodEntity period = requirePeriod(id);
        AuditMetadata audit = auditMetadataFactory.current();
        return closeSnapshotService.list(period.getId(), audit.companyId(), audit.accountBookId());
    }

    AccountPeriodEntity requirePeriod(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity entity = accountPeriodMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        return entity;
    }

    LambdaQueryWrapper<AccountPeriodEntity> baseWrapper(AuditMetadata audit) {
        return new LambdaQueryWrapper<AccountPeriodEntity>()
                .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId());
    }

    AccountPeriodResponse toResponse(AccountPeriodEntity entity) {
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
