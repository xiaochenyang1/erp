package com.tuowei.erp.finance.subject.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.web.AccountSubjectCreateRequest;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import com.tuowei.erp.finance.subject.web.AccountSubjectResponse;
import com.tuowei.erp.finance.subject.web.AccountSubjectTreeNode;
import com.tuowei.erp.finance.subject.web.AccountSubjectUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade delegating account-subject reads to the query service and writes to the command service. */
@Service
public class AccountSubjectService {

    private final AccountSubjectQueryService queryService;
    private final AccountSubjectCommandService commandService;

    public AccountSubjectService(AccountSubjectQueryService queryService, AccountSubjectCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public AccountSubjectResponse create(AccountSubjectCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountSubjectResponse> list(AccountSubjectPageQuery query) {
        AccountSubjectPageQuery safeQuery = query == null ? new AccountSubjectPageQuery() : query;
        return queryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public AccountSubjectResponse detail(Long id) {
        return queryService.detail(id);
    }

    @Transactional(readOnly = true)
    public List<AccountSubjectTreeNode> tree() {
        return queryService.tree();
    }

    @Transactional
    public AccountSubjectResponse update(Long id, AccountSubjectUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional
    public AccountSubjectResponse enable(Long id) {
        return commandService.enable(id);
    }

    @Transactional
    public AccountSubjectResponse disable(Long id) {
        return commandService.disable(id);
    }

    /** Un-transactional tenant guard reused by finance posting flows; participates in the caller's transaction. */
    public AccountSubjectEntity requireActiveSubject(Long id, String message) {
        return queryService.requireActiveSubject(id, message);
    }
}
