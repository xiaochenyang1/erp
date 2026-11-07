package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade delegating contract-version reads to the query service and writes to the command service. */
@Service
public class ContractVersionService {

    private final ContractVersionQueryService queryService;
    private final ContractVersionCommandService commandService;

    public ContractVersionService(ContractVersionQueryService queryService, ContractVersionCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public void record(ContractEntity contract, List<ContractLineEntity> lines, String eventType) {
        commandService.record(contract, lines, eventType);
    }

    @Transactional(readOnly = true)
    public List<ContractVersionResponse> list(Long contractId) {
        return queryService.list(contractId);
    }

    @Transactional(readOnly = true)
    public ContractVersionResponse detail(Long contractId, Long versionId) {
        return queryService.detail(contractId, versionId);
    }

    @Transactional
    public ContractResponse restoreAsDraft(Long contractId, Long versionId) {
        return commandService.restoreAsDraft(contractId, versionId);
    }
}
