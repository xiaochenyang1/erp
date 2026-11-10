package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.web.ContractPageQuery;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractSaveRequest;
import com.tuowei.erp.common.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
    private final ContractQueryService queryService;
    private final ContractCommandService commandService;
    private final ContractVersionService versionService;

    public ContractService(ContractQueryService queryService, ContractCommandService commandService,
                           ContractVersionService versionService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.versionService = versionService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> list(ContractPageQuery query) {
        ContractPageQuery safeQuery = query == null ? new ContractPageQuery() : query;
        return queryService.list(safeQuery);
    }
    @Transactional(readOnly = true) public ContractResponse detail(Long id) { return queryService.detail(id); }
    @Transactional public ContractResponse create(ContractSaveRequest request) { return commandService.create(request); }
    @Transactional public ContractResponse update(Long id, ContractSaveRequest request) { return commandService.update(id, request); }
    @Transactional public ContractResponse submit(Long id) { return commandService.submit(id); }
    @Transactional public ContractResponse approve(Long id) { return commandService.approve(id); }
    @Transactional public ContractResponse reject(Long id) { return commandService.reject(id); }
    @Transactional public ContractResponse close(Long id) { return commandService.close(id); }
    @Transactional public ContractResponse cancel(Long id) { return commandService.cancel(id); }
    @Transactional(readOnly = true) public java.util.List<com.tuowei.erp.commercial.contract.web.ContractVersionResponse> versions(Long id) { return versionService.list(id); }
    @Transactional(readOnly = true) public com.tuowei.erp.commercial.contract.web.ContractVersionResponse version(Long id, Long versionId) { return versionService.detail(id, versionId); }
    @Transactional public ContractResponse restoreVersion(Long id, Long versionId) { return versionService.restoreAsDraft(id, versionId); }
}
