package com.tuowei.erp.finance.subject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import com.tuowei.erp.finance.subject.web.AccountSubjectResponse;
import com.tuowei.erp.finance.subject.web.AccountSubjectTreeNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Read-side account-subject listing, tree assembly, detail lookup and shared tenant guards/mapping reused by the command side. */
@Service
public class AccountSubjectQueryService {

    private final AccountSubjectMapper accountSubjectMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public AccountSubjectQueryService(AccountSubjectMapper accountSubjectMapper, AuditMetadataFactory auditMetadataFactory) {
        this.accountSubjectMapper = accountSubjectMapper;
        this.auditMetadataFactory = auditMetadataFactory;
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
        List<AccountSubjectTreeNode> roots = new ArrayList<>();
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

    /** Un-transactional tenant guard reused by finance posting flows; participates in the caller's transaction. */
    public AccountSubjectEntity requireActiveSubject(Long id, String message) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity subject = requireSubject(id, audit);
        if (!"ACTIVE".equals(subject.getStatus())) {
            throw new IllegalArgumentException(message);
        }
        return subject;
    }
    AccountSubjectEntity requireSubject(Long id, AuditMetadata audit) {
        AccountSubjectEntity subject = accountSubjectMapper.selectById(id);
        if (subject == null || subject.getDeletedFlag() == null || subject.getDeletedFlag() != 0
                || !Objects.equals(subject.getCompanyId(), audit.companyId())
                || !Objects.equals(subject.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计科目不存在");
        }
        return subject;
    }

    void requireParent(Long parentId, AuditMetadata audit) {
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
    AccountSubjectResponse toResponse(AccountSubjectEntity subject) {
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

    String normalizeText(String value) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("科目字段不能为空");
        }
        return normalized;
    }

    String normalizeUpper(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
