package com.tuowei.erp.purchase.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptLineResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Read-side filtering, data scope, export and response mapping for purchase receipts. */
@Service
public class PurchaseReceiptQueryService {

    private static final List<String> RECEIPT_EXPORT_HEADERS = List.of(
            "receiptNo",
            "orderId",
            "warehouseId",
            "receiptDate",
            "status",
            "totalQuantity",
            "totalAmount",
            "totalTaxAmount",
            "remark"
    );

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public PurchaseReceiptQueryService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReceiptResponse> list(PurchaseReceiptPageQuery query) {
        PurchaseReceiptPageQuery safeQuery = query == null ? new PurchaseReceiptPageQuery() : query;
        Page<PurchaseReceiptEntity> result = purchaseReceiptMapper.selectPage(
                new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize())),
                scopedListQuery(safeQuery)
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    public StreamingResponseBody exportReceipts(PurchaseReceiptPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PurchaseReceiptPageQuery safeQuery = query == null ? new PurchaseReceiptPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(
                outputStream,
                RECEIPT_EXPORT_HEADERS,
                rowWriter -> {
                    for (PurchaseReceiptEntity entity : purchaseReceiptMapper.selectList(scopedListQuery(safeQuery))) {
                        rowWriter.write(receiptExportRow(entity));
                    }
                }
        ));
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse getById(Long id) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        assertCanView(receipt);
        List<PurchaseReceiptLineEntity> lines = purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId())
                        .orderByAsc(PurchaseReceiptLineEntity::getLineNo)
        );
        return toResponse(receipt, lines);
    }

    @Transactional(readOnly = true)
    public void assertCanView(PurchaseReceiptEntity receipt) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = receipt.getCreatedBy() == null ? null : userMapper.selectById(receipt.getCreatedBy());
        dataScopeService.assertCanViewPurchaseReceipt(
                receipt,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    @Transactional(readOnly = true)
    public void assertCanView(PurchaseOrderEntity order) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = order.getCreatedBy() == null ? null : userMapper.selectById(order.getCreatedBy());
        dataScopeService.assertCanViewPurchaseOrder(
                order,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    public PurchaseReceiptResponse toResponse(
            PurchaseReceiptEntity receipt,
            List<PurchaseReceiptLineEntity> lines
    ) {
        return new PurchaseReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getOrderId(),
                receipt.getWarehouseId(),
                receipt.getReceiptDate(),
                receipt.getStatus(),
                receipt.getTotalQuantity(),
                receipt.getTotalAmount(),
                receipt.getTotalTaxAmount(),
                receipt.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private LambdaQueryWrapper<PurchaseReceiptEntity> scopedListQuery(PurchaseReceiptPageQuery query) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = buildListQuery(
                normalizeNullableText(query.getKeyword()),
                query.getOrderId(),
                query.getWarehouseId(),
                normalizeStatus(query.getStatus()),
                query.getReceiptDateFrom(),
                query.getReceiptDateTo()
        );
        return dataScopeService.applyPurchaseReceiptScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private LambdaQueryWrapper<PurchaseReceiptEntity> buildListQuery(
            String keyword,
            Long orderId,
            Long warehouseId,
            String status,
            LocalDate receiptDateFrom,
            LocalDate receiptDateTo
    ) {
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseReceiptEntity::getReceiptNo, keyword);
        }
        if (orderId != null) {
            wrapper.eq(PurchaseReceiptEntity::getOrderId, orderId);
        }
        if (warehouseId != null) {
            wrapper.eq(PurchaseReceiptEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseReceiptEntity::getStatus, status);
        }
        if (receiptDateFrom != null) {
            wrapper.ge(PurchaseReceiptEntity::getReceiptDate, receiptDateFrom);
        }
        if (receiptDateTo != null) {
            wrapper.le(PurchaseReceiptEntity::getReceiptDate, receiptDateTo);
        }
        return wrapper.orderByDesc(PurchaseReceiptEntity::getId);
    }

    private List<?> receiptExportRow(PurchaseReceiptEntity entity) {
        return Arrays.asList(
                entity.getReceiptNo(),
                entity.getOrderId(),
                entity.getWarehouseId(),
                entity.getReceiptDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark()
        );
    }

    private PurchaseReceiptResponse toSummaryResponse(PurchaseReceiptEntity receipt) {
        return new PurchaseReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getOrderId(),
                receipt.getWarehouseId(),
                receipt.getReceiptDate(),
                receipt.getStatus(),
                receipt.getTotalQuantity(),
                receipt.getTotalAmount(),
                receipt.getTotalTaxAmount(),
                receipt.getRemark(),
                List.of()
        );
    }

    private PurchaseReceiptLineResponse toLineResponse(PurchaseReceiptLineEntity line) {
        return new PurchaseReceiptLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getOrderLineId(),
                line.getProductId(),
                line.getQty(),
                line.getPrice(),
                line.getTaxRate(),
                line.getAmount(),
                line.getTaxAmount(),
                line.getLotNo(),
                line.getProductionDate(),
                line.getExpiryDate(),
                line.getLocationId(),
                line.getSerialNos(),
                line.getRemark()
        );
    }

    private PurchaseReceiptEntity requireReceipt(Long id) {
        PurchaseReceiptEntity entity = purchaseReceiptMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购入库单不存在");
        }
        return entity;
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
