package com.tuowei.erp.finance.subject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.web.AccountSubjectCreateRequest;
import com.tuowei.erp.finance.subject.web.AccountSubjectResponse;
import com.tuowei.erp.finance.subject.web.AccountSubjectUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** Write-side account-subject creation, configuration updates and enable/disable, reusing the query service for guards and mapping. */
@Service
public class AccountSubjectCommandService {

    private final AccountSubjectMapper accountSubjectMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountSubjectQueryService queryService;

    public AccountSubjectCommandService(
            AccountSubjectMapper accountSubjectMapper,
            AuditMetadataFactory auditMetadataFactory,
            AccountSubjectQueryService queryService
    ) {
        this.accountSubjectMapper = accountSubjectMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public AccountSubjectResponse create(AccountSubjectCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        String subjectCode = queryService.normalizeText(request.subjectCode());
        if (accountSubjectMapper.selectCount(new LambdaQueryWrapper<AccountSubjectEntity>()
                .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountSubjectEntity::getSubjectCode, subjectCode)
                .eq(AccountSubjectEntity::getDeletedFlag, 0)) > 0) {
            throw new IllegalArgumentException("科目编码已存在");
        }
        queryService.requireParent(request.parentId(), audit);

        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setSubjectCode(subjectCode);
        entity.setSubjectName(queryService.normalizeText(request.subjectName()));
        entity.setParentId(request.parentId());
        entity.setSubjectType(queryService.normalizeUpper(request.subjectType()));
        entity.setBalanceDirection(queryService.normalizeUpper(request.balanceDirection()));
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        setAudit(entity, audit, now);
        accountSubjectMapper.insert(entity);
        return queryService.toResponse(entity);
    }
    @Transactional
    public AccountSubjectResponse update(Long id, AccountSubjectUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity entity = queryService.requireSubject(id, audit);
        queryService.requireParent(request.parentId(), audit);
        if (Objects.equals(id, request.parentId())) {
            throw new IllegalArgumentException("上级科目不能选择自己");
        }
        entity.setSubjectName(queryService.normalizeText(request.subjectName()));
        entity.setParentId(request.parentId());
        entity.setSubjectType(queryService.normalizeUpper(request.subjectType()));
        entity.setBalanceDirection(queryService.normalizeUpper(request.balanceDirection()));
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(accountSubjectMapper.updateById(entity), "会计科目已被其他操作修改，请刷新后重试");
        return queryService.detail(id);
    }

    @Transactional
    public AccountSubjectResponse enable(Long id) {
        return changeStatus(id, "ACTIVE");
    }

    @Transactional
    public AccountSubjectResponse disable(Long id) {
        return changeStatus(id, "DISABLED");
    }

    private AccountSubjectResponse changeStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity entity = queryService.requireSubject(id, audit);
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(accountSubjectMapper.updateById(entity), "会计科目已被其他操作修改，请刷新后重试");
        return queryService.detail(id);
    }

    private void setAudit(AccountSubjectEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
