package com.tuowei.erp.masterdata.supplierproduct.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.supplierproduct.model.SupplierProductRelationEntity;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationRequest;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade delegating supplier-product reads and the shared lookup to the query service and writes to the command service. */
@Service
public class SupplierProductRelationService {

    private final SupplierProductRelationQueryService queryService;
    private final SupplierProductRelationCommandService commandService;

    public SupplierProductRelationService(
            SupplierProductRelationQueryService queryService,
            SupplierProductRelationCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public List<SupplierProductRelationResponse> list(Long supplierId) {
        return queryService.list(supplierId);
    }

    @Transactional
    public SupplierProductRelationResponse save(Long supplierId, SupplierProductRelationRequest request) {
        return commandService.save(supplierId, request);
    }

    @Transactional
    public void delete(Long supplierId, Long id) {
        commandService.delete(supplierId, id);
    }

    public SupplierProductRelationEntity find(Long supplierId, Long productId, AuditMetadata metadata) {
        return queryService.find(supplierId, productId, metadata);
    }
}
