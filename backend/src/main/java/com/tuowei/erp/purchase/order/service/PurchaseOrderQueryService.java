package com.tuowei.erp.purchase.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-side data scope, summary mapping and CSV export for purchase orders. */
@Service
public class PurchaseOrderQueryService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SupplierMapper supplierMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public PurchaseOrderQueryService(
            PurchaseOrderMapper purchaseOrderMapper,
            SupplierMapper supplierMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.supplierMapper = supplierMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(PurchaseOrderPageQuery query) {
        PurchaseOrderPageQuery safeQuery = query == null ? new PurchaseOrderPageQuery() : query;
        Page<PurchaseOrderEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<PurchaseOrderEntity> result = purchaseOrderMapper.selectPage(page, scopedListQuery(safeQuery));
        Map<Long, String> supplierNames = loadSupplierNames(result.getRecords());

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toSummaryResponse(entity, supplierNames.get(entity.getSupplierId())))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertCanView(PurchaseOrderEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseOrder(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    public StreamingResponseBody exportOrders(PurchaseOrderPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> exportToCsv(query, outputStream));
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> scopedListQuery(PurchaseOrderPageQuery query) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = buildListQuery(
                normalizeNullableText(query.getKeyword()),
                normalizeStatus(query.getStatus()),
                normalizeStatus(query.getApprovalStatus()),
                query.getSupplierId()
        );
        return dataScopeService.applyPurchaseOrderScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> buildListQuery(
            String keyword,
            String status,
            String approvalStatus,
            Long supplierId
    ) {
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseOrderEntity::getOrderNo, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseOrderEntity::getStatus, status);
        }
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(PurchaseOrderEntity::getApprovalStatus, approvalStatus);
        }
        if (supplierId != null) {
            wrapper.eq(PurchaseOrderEntity::getSupplierId, supplierId);
        }
        return wrapper.orderByDesc(PurchaseOrderEntity::getId);
    }

    private void exportToCsv(PurchaseOrderPageQuery query, OutputStream outputStream) throws IOException {
        PurchaseOrderPageQuery safeQuery = query == null ? new PurchaseOrderPageQuery() : query;
        List<String> headers = List.of(
                "订单编号", "供应商", "订单日期", "交货日期",
                "订单金额", "状态", "创建人", "创建时间", "备注"
        );
        List<PurchaseOrderEntity> orders = purchaseOrderMapper.selectList(scopedListQuery(safeQuery));
        Map<Long, String> supplierNames = loadSupplierNames(orders);
        Map<Long, String> userNames = loadUserNames(orders);
        List<List<String>> rows = orders.stream()
                .map(order -> List.of(
                        order.getOrderNo() != null ? order.getOrderNo() : "",
                        supplierNames.getOrDefault(order.getSupplierId(), ""),
                        order.getOrderDate() != null ? order.getOrderDate().toString() : "",
                        order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : "",
                        order.getTotalAmount() != null ? order.getTotalAmount().toString() : "",
                        order.getStatus() != null ? order.getStatus() : "",
                        userNames.getOrDefault(order.getCreatedBy(), ""),
                        order.getCreatedTime() != null ? order.getCreatedTime().toString() : "",
                        order.getRemark() != null ? order.getRemark() : ""
                ))
                .toList();
        CsvExport.write(outputStream, headers, rows);
    }

    private PurchaseOrderResponse toSummaryResponse(PurchaseOrderEntity entity, String supplierName) {
        return new PurchaseOrderResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getSupplierId(),
                supplierName,
                entity.getOrderDate(),
                entity.getDeliveryDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getReceiptStatus(),
                entity.getSourceInquiryId(),
                entity.getSourceInquiryNo(),
                entity.getSourceQuoteId(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                List.of()
        );
    }

    private Map<Long, String> loadSupplierNames(List<PurchaseOrderEntity> orders) {
        List<Long> supplierIds = orders.stream()
                .map(PurchaseOrderEntity::getSupplierId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(SupplierEntity::getId, SupplierEntity::getSupplierName));
    }

    private Map<Long, String> loadUserNames(List<PurchaseOrderEntity> orders) {
        Set<Long> userIds = orders.stream()
                .map(PurchaseOrderEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
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
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}
