package com.tuowei.erp.finance.fund.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.fund.web.BankStatementCreateRequest;
import com.tuowei.erp.finance.fund.web.BankStatementMatchRequest;
import com.tuowei.erp.finance.fund.web.BankStatementPageQuery;
import com.tuowei.erp.finance.fund.web.BankStatementResponse;
import com.tuowei.erp.finance.fund.web.BankStatementUnmatchRequest;
import com.tuowei.erp.finance.fund.web.FundAccountCreateRequest;
import com.tuowei.erp.finance.fund.web.FundAccountPageQuery;
import com.tuowei.erp.finance.fund.web.FundAccountResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for fund account and bank statement queries and commands. */
@Service
public class FundService {

    private final FundQueryService fundQueryService;
    private final FundCommandService fundCommandService;

    public FundService(FundQueryService fundQueryService, FundCommandService fundCommandService) {
        this.fundQueryService = fundQueryService;
        this.fundCommandService = fundCommandService;
    }

    @Transactional
    public FundAccountResponse createAccount(FundAccountCreateRequest request) {
        return fundCommandService.createAccount(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<FundAccountResponse> listAccounts(FundAccountPageQuery query) {
        FundAccountPageQuery safeQuery = query == null ? new FundAccountPageQuery() : query;
        return fundQueryService.listAccounts(safeQuery);
    }

    @Transactional(readOnly = true)
    public FundAccountResponse accountDetail(Long id) {
        return fundQueryService.accountDetail(id);
    }

    @Transactional
    public BankStatementResponse createStatement(BankStatementCreateRequest request) {
        return fundCommandService.createStatement(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<BankStatementResponse> listStatements(BankStatementPageQuery query) {
        BankStatementPageQuery safeQuery = query == null ? new BankStatementPageQuery() : query;
        return fundQueryService.listStatements(safeQuery);
    }

    @Transactional(readOnly = true)
    public BankStatementResponse statementDetail(Long id) {
        return fundQueryService.statementDetail(id);
    }

    @Transactional
    public BankStatementResponse matchStatement(Long id, BankStatementMatchRequest request) {
        return fundCommandService.matchStatement(id, request);
    }

    @Transactional
    public BankStatementResponse unmatchStatement(Long id, BankStatementUnmatchRequest request) {
        return fundCommandService.unmatchStatement(id, request);
    }
}
