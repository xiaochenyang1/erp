package com.tuowei.erp.finance.subject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.web.AccountSubjectCreateRequest;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import com.tuowei.erp.finance.subject.web.AccountSubjectResponse;
import com.tuowei.erp.finance.subject.web.AccountSubjectTreeNode;
import com.tuowei.erp.finance.subject.web.AccountSubjectUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AccountSubjectService {

    private final AccountSubjectMapper accountSubjectMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public AccountSubjectService(AccountSubjectMapper accountSubjectMapper, AuditMetadataFactory auditMetadataFactory) {
        this.accountSubjectMapper = accountSubjectMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public AccountSubjectResponse create(AccountSubjectCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        String subjectCode = normalizeText(request.subjectCode());
        if (accountSubjectMapper.selectCount(new LambdaQueryWrapper<AccountSubjectEntity>()
                .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountSubjectEntity::getSubjectCode, subjectCode)
                .eq(AccountSubjectEntity::getDeletedFlag, 0)) > 0) {
            throw new IllegalArgumentException("科目编码已存在");
        }
        requireParent(request.parentId(), audit);

        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setSubjectCode(subjectCode);
        entity.setSubjectName(normalizeText(request.subjectName()));
        entity.setParentId(request.parentId());
        entity.setSubjectType(normalizeUpper(request.subjectType()));
        entity.setBalanceDirection(normalizeUpper(request.balanceDirection()));
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        setAudit(entity, audit, now);
        accountSubjectMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountSubjectResponse> list(AccountSubjectPageQuery query) {
        AccountSubjectPageQuery safeQuery = query == null ? new AccountSubjectPageQuery() : query;
        Page<AccountSubjectEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<AccountSubjectEntity> wrapper = baseWrapper();
        if (StringUtils.hasText(safeQuery.getSubjectCode())) {
            wrapper.like(AccountSubjectEntity::getSubjectCode, safeQuery.getSubjectCode().trim());
        }
        if (StringUtils.hasText(safeQuery.getSubjectName())) {
            wrapper.like(AccountSubjectEntity::getSubjectName, safeQuery.getSubjectName().trim());
        }
        if (StringUtils.hasText(safeQuery.getSubjectType())) {
            wrapper.eq(AccountSubjectEntity::getSubjectType, normalizeUpper(safeQuery.getSubjectType()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(AccountSubjectEntity::getStatus, normalizeUpper(safeQuery.getStatus()));
        }
        wrapper.orderByAsc(AccountSubjectEntity::getSubjectCode).orderByAsc(AccountSubjectEntity::getId);
        Page<AccountSubjectEntity> result = accountSubjectMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public AccountSubjectResponse detail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requireSubject(id, audit));
    }

    @Transactional(readOnly = true)
    public List<AccountSubjectTreeNode> tree() {
        List<AccountSubjectEntity> subjects = accountSubjectMapper.selectList(baseWrapper()
                .orderByAsc(AccountSubjectEntity::getSubjectCode)
                .orderByAsc(AccountSubjectEntity::getId));
        Map<Long, AccountSubjectTreeNode> nodes = new LinkedHashMap<>();
        for (AccountSubjectEntity subject : subjects) {
            nodes.put(subject.getId(), toTreeNode(subject));
        }
        List<AccountSubjectTreeNode> roots = new java.util.ArrayList<>();
        for (AccountSubjectEntity subject : subjects) {
            AccountSubjectTreeNode node = nodes.get(subject.getId());
            AccountSubjectTreeNode parent = subject.getParentId() == null ? null : nodes.get(subject.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Transactional
    public AccountSubjectResponse update(Long id, AccountSubjectUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity entity = requireSubject(id, audit);
        requireParent(request.parentId(), audit);
        if (Objects.equals(id, request.parentId())) {
            throw new IllegalArgumentException("上级科目不能选择自己");
        }
        entity.setSubjectName(normalizeText(request.subjectName()));
        entity.setParentId(request.parentId());
        entity.setSubjectType(normalizeUpper(request.subjectType()));
        entity.setBalanceDirection(normalizeUpper(request.balanceDirection()));
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(accountSubjectMapper.updateById(entity), "会计科目已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    @Transactional
    public AccountSubjectResponse enable(Long id) {
        return changeStatus(id, "ACTIVE");
    }

    @Transactional
    public AccountSubjectResponse disable(Long id) {
        return changeStatus(id, "DISABLED");
    }

    public AccountSubjectEntity requireActiveSubject(Long id, String message) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity subject = requireSubject(id, audit);
        if (!"ACTIVE".equals(subject.getStatus())) {
            throw new IllegalArgumentException(message);
        }
        return subject;
    }

    private AccountSubjectResponse changeStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity entity = requireSubject(id, audit);
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(accountSubjectMapper.updateById(entity), "会计科目已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    private AccountSubjectEntity requireSubject(Long id, AuditMetadata audit) {
        AccountSubjectEntity subject = accountSubjectMapper.selectById(id);
        if (subject == null || subject.getDeletedFlag() == null || subject.getDeletedFlag() != 0
                || !Objects.equals(subject.getCompanyId(), audit.companyId())
                || !Objects.equals(subject.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计科目不存在");
        }
        return subject;
    }

    private void requireParent(Long parentId, AuditMetadata audit) {
        if (parentId == null) {
            return;
        }
        AccountSubjectEntity parent = accountSubjectMapper.selectById(parentId);
        if (parent == null || parent.getDeletedFlag() == null || parent.getDeletedFlag() != 0
                || !Objects.equals(parent.getCompanyId(), audit.companyId())
                || !Objects.equals(parent.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("上级科目不存在");
        }
    }

    private LambdaQueryWrapper<AccountSubjectEntity> baseWrapper() {
        AuditMetadata audit = auditMetadataFactory.current();
        return new LambdaQueryWrapper<AccountSubjectEntity>()
                .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountSubjectEntity::getDeletedFlag, 0);
    }

    private AccountSubjectResponse toResponse(AccountSubjectEntity subject) {
        return new AccountSubjectResponse(
                subject.getId(),
                subject.getSubjectCode(),
                subject.getSubjectName(),
                subject.getParentId(),
                subject.getSubjectType(),
                subject.getBalanceDirection(),
                subject.getStatus(),
                subject.getRemark()
        );
    }

    private AccountSubjectTreeNode toTreeNode(AccountSubjectEntity subject) {
        return new AccountSubjectTreeNode(
                subject.getId(),
                subject.getSubjectCode(),
                subject.getSubjectName(),
                subject.getParentId(),
                subject.getSubjectType(),
                subject.getBalanceDirection(),
                subject.getStatus(),
                subject.getRemark()
        );
    }

    private void setAudit(AccountSubjectEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private String normalizeText(String value) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("科目字段不能为空");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
