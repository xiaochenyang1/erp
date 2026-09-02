package com.tuowei.erp.production.order.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionOrderUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for production order queries, commands and posting transitions. */
@Service
public class ProductionOrderService {
    public static final String STATUS_DRAFT = ProductionOrderConstants.STATUS_DRAFT;
    public static final String STATUS_RELEASED = ProductionOrderConstants.STATUS_RELEASED;
    public static final String STATUS_MATERIAL_ISSUED = ProductionOrderConstants.STATUS_MATERIAL_ISSUED;
    public static final String STATUS_COMPLETED = ProductionOrderConstants.STATUS_COMPLETED;
    public static final String STATUS_CANCELLED = ProductionOrderConstants.STATUS_CANCELLED;
    public static final String SOURCE_TYPE = ProductionOrderConstants.SOURCE_TYPE;

    private final ProductionOrderQueryService queryService;
    private final ProductionOrderCommandService commandService;
    private final ProductionOrderPostingService postingService;

    @Autowired
    public ProductionOrderService(
            ProductionOrderQueryService queryService,
            ProductionOrderCommandService commandService,
            ProductionOrderPostingService postingService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.postingService = postingService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionOrderService(
            ProductionOrderMapper orderMapper, ProductionOrderMaterialMapper materialMapper,
            ProductionOrderNumberService numberService, ProductionBomService bomService,
            ProductValidator productValidator, WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory, ProductionOrderQueryService queryService,
            ProductionOrderPostingService postingService
    ) {
        this.queryService = queryService;
        this.commandService = new ProductionOrderCommandService(
                orderMapper, materialMapper, numberService, bomService, productValidator,
                warehouseMapper, auditMetadataFactory, queryService
        );
        this.postingService = postingService;
    }

    @Transactional
    public ProductionOrderResponse create(ProductionOrderCreateRequest request) { return commandService.create(request); }

    @Transactional
    public ProductionOrderResponse update(Long id, ProductionOrderUpdateRequest request) { return commandService.update(id, request); }

    @Transactional(readOnly = true)
    public ProductionOrderResponse getById(Long id) { return queryService.getById(id); }

    @Transactional(readOnly = true)
    public PageResponse<ProductionOrderResponse> list(ProductionOrderPageQuery query) {
        return queryService.list(query == null ? new ProductionOrderPageQuery() : query);
    }

    @Transactional
    public ProductionOrderResponse release(Long id) { return postingService.release(id); }

    @Transactional
    public ProductionOrderResponse cancel(Long id) { return postingService.cancel(id); }

    public ProductionOrderEntity requireOrder(Long id) { return queryService.requireOrder(id); }
    public List<ProductionOrderMaterialEntity> selectMaterials(Long orderId) { return queryService.selectMaterials(orderId); }
    public List<ProductionOrderMaterialEntity> selectMaterials(ProductionOrderEntity order) { return queryService.selectMaterials(order); }
    public ProductionOrderResponse toResponse(ProductionOrderEntity order) { return queryService.toResponse(order); }
}
