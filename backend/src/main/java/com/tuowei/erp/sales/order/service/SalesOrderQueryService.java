package com.tuowei.erp.sales.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderLineResponse;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Read-side filtering, data scope and response mapping for sales orders. */
@Service
public class SalesOrderQueryService {

    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final CustomerMapper customerMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public SalesOrderQueryService(
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            CustomerMapper customerMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.customerMapper = customerMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse getById(Long id) {
        SalesOrderEntity entity = requireOrder(id);
        return toResponse(entity, findCustomerName(entity.getCustomerId()), selectLines(entity));
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> list(SalesOrderPageQuery query) {
        SalesOrderPageQuery safeQuery = query == null ? new SalesOrderPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        String approvalStatus = normalizeStatus(safeQuery.getApprovalStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        LambdaQueryWrapper<SalesOrderEntity> wrapper = buildListQuery(keyword, status, approvalStatus, safeQuery.getCustomerId());
        wrapper = dataScopeService.applySalesOrderScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<SalesOrderEntity> result = salesOrderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, String> customerNames = loadCustomerNames(result.getRecords());

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toSummaryResponse(entity, customerNames.get(entity.getCustomerId())))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertCanView(SalesOrderEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesOrder(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    public SalesOrderResponse toResponse(
            SalesOrderEntity entity,
            String customerName,
            List<SalesOrderLineEntity> lines
    ) {
        return new SalesOrderResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getContractId(),
                entity.getCustomerId(),
                entity.getWarehouseId(),
                customerName,
                entity.getOrderDate(),
                entity.getDeliveryDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getDeliveryStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private LambdaQueryWrapper<SalesOrderEntity> buildListQuery(
            String keyword,
            String status,
            String approvalStatus,
            Long customerId
    ) {
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SalesOrderEntity::getOrderNo, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesOrderEntity::getStatus, status);
        }
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(SalesOrderEntity::getApprovalStatus, approvalStatus);
        }
        if (customerId != null) {
            wrapper.eq(SalesOrderEntity::getCustomerId, customerId);
        }
        return wrapper.orderByDesc(SalesOrderEntity::getId);
    }

    public SalesOrderEntity requireOrder(Long id) {
        SalesOrderEntity entity = salesOrderMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        assertCanView(entity);
        return entity;
    }

    public List<SalesOrderLineEntity> selectLines(SalesOrderEntity entity) {
        return salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, entity.getId())
                .orderByAsc(SalesOrderLineEntity::getLineNo));
    }

    private String findCustomerName(Long customerId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        return customer == null ? null : customer.getCustomerName();
    }

    private SalesOrderResponse toSummaryResponse(SalesOrderEntity entity, String customerName) {
        return new SalesOrderResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getContractId(),
                entity.getCustomerId(),
                entity.getWarehouseId(),
                customerName,
                entity.getOrderDate(),
                entity.getDeliveryDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getDeliveryStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                List.of()
        );
    }

    private Map<Long, String> loadCustomerNames(List<SalesOrderEntity> orders) {
        List<Long> customerIds = orders.stream()
                .map(SalesOrderEntity::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(CustomerEntity::getId, CustomerEntity::getCustomerName));
    }

    private SalesOrderLineResponse toLineResponse(SalesOrderLineEntity entity) {
        return new SalesOrderLineResponse(
                entity.getId(),
                entity.getLineNo(),
                entity.getContractLineId(),
                entity.getProductId(),
                entity.getQty(),
                entity.getAuxQty(),
                entity.getAuxUnitName(),
                entity.getConversionFactor(),
                entity.getPrice(),
                entity.getTaxRate(),
                entity.getAmount(),
                entity.getTaxAmount(),
                entity.getDeliveredQty(),
                entity.getRemark()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }
}
