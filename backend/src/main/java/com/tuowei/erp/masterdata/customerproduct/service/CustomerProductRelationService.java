package com.tuowei.erp.masterdata.customerproduct.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.customerproduct.model.CustomerProductRelationEntity;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationRequest;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade delegating customer-product reads and the shared lookup to the query service and writes to the command service. */
@Service
public class CustomerProductRelationService {

    private final CustomerProductRelationQueryService queryService;
    private final CustomerProductRelationCommandService commandService;

    public CustomerProductRelationService(
            CustomerProductRelationQueryService queryService,
            CustomerProductRelationCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public List<CustomerProductRelationResponse> list(Long customerId) {
        return queryService.list(customerId);
    }

    @Transactional
    public CustomerProductRelationResponse save(Long customerId, CustomerProductRelationRequest request) {
        return commandService.save(customerId, request);
    }

    @Transactional
    public void delete(Long customerId, Long id) {
        commandService.delete(customerId, id);
    }

    public CustomerProductRelationEntity find(Long customerId, Long productId, AuditMetadata metadata) {
        return queryService.find(customerId, productId, metadata);
    }
}
