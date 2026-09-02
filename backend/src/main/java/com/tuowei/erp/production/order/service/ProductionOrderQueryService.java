package com.tuowei.erp.production.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.web.ProductionOrderMaterialResponse;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-side filtering, data scope and response mapping for production orders. */
@Service
public class ProductionOrderQueryService {

    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public ProductionOrderQueryService(
            ProductionOrderMapper orderMapper,
            ProductionOrderMaterialMapper materialMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public ProductionOrderResponse getById(Long id) {
        return toResponse(requireOrder(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionOrderResponse> list(ProductionOrderPageQuery query) {
        ProductionOrderPageQuery safeQuery = query == null ? new ProductionOrderPageQuery() : query;
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        Page<ProductionOrderEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = buildListQuery(
                safeQuery,
                currentUser.companyId(),
                currentUser.accountBookId()
        );
        wrapper = dataScopeService.applyProductionOrderScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<ProductionOrderEntity> result = orderMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    public ProductionOrderEntity requireOrder(Long id) {
        ProductionOrderEntity order = orderMapper.selectById(id);
        if (order == null || Integer.valueOf(1).equals(order.getDeletedFlag())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        assertCanView(order);
        return order;
    }

    public List<ProductionOrderMaterialEntity> selectMaterials(Long orderId) {
        return selectMaterials(requireOrder(orderId));
    }

    public List<ProductionOrderMaterialEntity> selectMaterials(ProductionOrderEntity order) {
        return materialMapper.selectList(new LambdaQueryWrapper<ProductionOrderMaterialEntity>()
                .eq(ProductionOrderMaterialEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderMaterialEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderMaterialEntity::getOrderId, order.getId())
                .orderByAsc(ProductionOrderMaterialEntity::getLineNo));
    }

    public ProductionOrderResponse toResponse(ProductionOrderEntity order) {
        return new ProductionOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getBomId(),
                order.getProductId(),
                order.getFinishedWarehouseId(),
                order.getMaterialWarehouseId(),
                order.getPlannedQty(),
                order.getCompletedQty(),
                order.getPlannedStartDate(),
                order.getPlannedFinishDate(),
                order.getStatus(),
                order.getIssuedAmount(),
                order.getFinishedAmount(),
                order.getRemark(),
                selectMaterials(order).stream()
                        .map(material -> new ProductionOrderMaterialResponse(
                                material.getId(),
                                material.getLineNo(),
                                material.getMaterialProductId(),
                                material.getRequiredQty(),
                                material.getIssuedQty(),
                                material.getIssuedAmount(),
                                material.getRemark()
                        ))
                        .toList()
        );
    }

    public void assertCanView(ProductionOrderEntity order) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = order.getCreatedBy() == null ? null : userMapper.selectById(order.getCreatedBy());
        dataScopeService.assertCanViewProductionOrder(
                order,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private LambdaQueryWrapper<ProductionOrderEntity> buildListQuery(
            ProductionOrderPageQuery query,
            Long companyId,
            Long accountBookId
    ) {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = new LambdaQueryWrapper<ProductionOrderEntity>()
                .eq(ProductionOrderEntity::getCompanyId, companyId)
                .eq(ProductionOrderEntity::getAccountBookId, accountBookId)
                .eq(ProductionOrderEntity::getDeletedFlag, 0);
        String keyword = normalizeNullableText(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ProductionOrderEntity::getOrderNo, keyword);
        }
        String status = normalizeStatus(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionOrderEntity::getStatus, status);
        }
        if (query.getBomId() != null) {
            wrapper.eq(ProductionOrderEntity::getBomId, query.getBomId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(ProductionOrderEntity::getProductId, query.getProductId());
        }
        if (query.getMaterialWarehouseId() != null) {
            wrapper.eq(ProductionOrderEntity::getMaterialWarehouseId, query.getMaterialWarehouseId());
        }
        if (query.getFinishedWarehouseId() != null) {
            wrapper.eq(ProductionOrderEntity::getFinishedWarehouseId, query.getFinishedWarehouseId());
        }
        if (query.getPlannedStartDateFrom() != null) {
            wrapper.ge(ProductionOrderEntity::getPlannedStartDate, query.getPlannedStartDateFrom());
        }
        if (query.getPlannedStartDateTo() != null) {
            wrapper.le(ProductionOrderEntity::getPlannedStartDate, query.getPlannedStartDateTo());
        }
        return wrapper.orderByDesc(ProductionOrderEntity::getId);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
