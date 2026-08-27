package com.tuowei.erp.production.bom.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.production.bom.mapper.ProductionBomLineMapper;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
import com.tuowei.erp.production.bom.web.ProductionBomUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for production BOM queries and commands. */
@Service
public class ProductionBomService {
    private final ProductionBomQueryService queryService;
    private final ProductionBomCommandService commandService;

    @Autowired
    public ProductionBomService(ProductionBomQueryService queryService, ProductionBomCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionBomService(
            ProductionBomMapper bomMapper, ProductionBomLineMapper lineMapper,
            ProductionBomNumberService numberService, ProductMapper productMapper,
            ProductValidator productValidator, AuditMetadataFactory auditMetadataFactory
    ) {
        this.queryService = new ProductionBomQueryService(bomMapper, lineMapper, auditMetadataFactory);
        this.commandService = new ProductionBomCommandService(
                bomMapper, lineMapper, numberService, productValidator, auditMetadataFactory, queryService
        );
    }

    @Transactional
    public ProductionBomResponse create(ProductionBomCreateRequest request) { return commandService.create(request); }

    @Transactional
    public ProductionBomResponse update(Long id, ProductionBomUpdateRequest request) { return commandService.update(id, request); }

    @Transactional(readOnly = true)
    public ProductionBomResponse getById(Long id) { return queryService.getById(id); }

    @Transactional(readOnly = true)
    public PageResponse<ProductionBomResponse> list(ProductionBomPageQuery query) {
        return queryService.list(query == null ? new ProductionBomPageQuery() : query);
    }

    public ProductionBomEntity requireBom(Long id, Long companyId, Long accountBookId) {
        return queryService.requireBom(id, companyId, accountBookId);
    }

    public List<ProductionBomLineEntity> selectLines(Long bomId) { return queryService.selectLines(bomId); }
}
