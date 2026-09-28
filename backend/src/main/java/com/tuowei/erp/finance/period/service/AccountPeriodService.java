package com.tuowei.erp.finance.period.service;

import com.tuowei.erp.finance.period.web.AccountPeriodResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseSnapshotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade delegating account-period reads to the query service and generate/lock/close/reopen writes to the command service. */
@Service
public class AccountPeriodService {

    private final AccountPeriodQueryService queryService;
    private final AccountPeriodCommandService commandService;

    public AccountPeriodService(AccountPeriodQueryService queryService, AccountPeriodCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public List<AccountPeriodResponse> generate(Integer year) {
        return commandService.generate(year);
    }

    @Transactional(readOnly = true)
    public List<AccountPeriodResponse> list(Integer year) {
        return queryService.list(year);
    }

    @Transactional(readOnly = true)
    public List<AccountPeriodCloseSnapshotResponse> closeSnapshots(Long id) {
        return queryService.closeSnapshots(id);
    }

    @Transactional
    public AccountPeriodResponse lock(Long id) {
        return commandService.lock(id);
    }

    @Transactional
    public AccountPeriodResponse close(Long id) {
        return commandService.close(id);
    }

    @Transactional
    public AccountPeriodResponse reopen(Long id) {
        return commandService.reopen(id);
    }
}
