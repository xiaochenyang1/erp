package com.tuowei.erp.purchase.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnLineEntity;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnLineResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.support.PurchaseReturnLineViewData;
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
import java.util.Objects;

/** Read-side filtering, data scope, export and response mapping for purchase returns. */
@Service
public class PurchaseReturnQueryService {

    private static final List<String> RETURN_EXPORT_HEADERS = List.of(
            "returnNo",
            "receiptId",
            "warehouseId",
            "returnDate",
            "status",
            "totalQuantity",
            "totalAmount",
            "totalTaxAmount",
            "remark"
    );

    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnLineMapper purchaseReturnLineMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductValidator productValidator;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public PurchaseReturnQueryService(
            PurchaseReturnMapper purchaseReturnMapper,
            PurchaseReturnLineMapper purchaseReturnLineMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.purchaseReturnLineMapper = purchaseReturnLineMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.warehouseMapper = warehouseMapper;
        this.productValidator = productValidator;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReturnResponse> list(PurchaseReturnPageQuery query) {
        PurchaseReturnPageQuery safeQuery = query == null ? new PurchaseReturnPageQuery() : query;
        Page<PurchaseReturnEntity> result = purchaseReturnMapper.selectPage(
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

    public StreamingResponseBody exportReturns(PurchaseReturnPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PurchaseReturnPageQuery safeQuery = query == null ? new PurchaseReturnPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(
                outputStream,
                RETURN_EXPORT_HEADERS,
                rowWriter -> {
                    for (PurchaseReturnEntity entity : purchaseReturnMapper.selectList(scopedListQuery(safeQuery))) {
                        rowWriter.write(returnExportRow(entity));
                    }
                }
        ));
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse getById(Long id) {
        PurchaseReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        PurchaseReceiptEntity receipt = requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        );
        List<PurchaseReturnLineEntity> lines = purchaseReturnLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                        .eq(PurchaseReturnLineEntity::getCompanyId, entity.getCompanyId())
                        .eq(PurchaseReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                        .eq(PurchaseReturnLineEntity::getReturnId, entity.getId())
                        .orderByAsc(PurchaseReturnLineEntity::getLineNo)
        ).stream().map(this::enrichLine).toList();
        return toResponse(entity, receipt, lines);
    }

    @Transactional(readOnly = true)
    public void assertCanView(PurchaseReturnEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseReturn(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    @Transactional(readOnly = true)
    public void assertCanView(PurchaseReceiptEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseReceipt(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
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

    public PurchaseReturnResponse toResponse(
            PurchaseReturnEntity entity,
            PurchaseReceiptEntity receipt,
            List<PurchaseReturnLineEntity> lines
    ) {
        return toResponse(entity, loadContext(receipt), lines);
    }

    PurchaseReturnResponse toResponse(
            PurchaseReturnEntity entity,
            ReceiptContext context,
            List<PurchaseReturnLineEntity> lines
    ) {
        return new PurchaseReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getReceiptId(),
                context.receiptNo(),
                context.orderNo(),
                entity.getWarehouseId(),
                context.warehouseName(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private LambdaQueryWrapper<PurchaseReturnEntity> scopedListQuery(PurchaseReturnPageQuery query) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = buildListQuery(
                normalizeNullableText(query.getKeyword()),
                query.getReceiptId(),
                query.getWarehouseId(),
                normalizeStatus(query.getStatus()),
                query.getReturnDateFrom(),
                query.getReturnDateTo()
        );
        return dataScopeService.applyPurchaseReturnScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private LambdaQueryWrapper<PurchaseReturnEntity> buildListQuery(
            String keyword,
            Long receiptId,
            Long warehouseId,
            String status,
            LocalDate returnDateFrom,
            LocalDate returnDateTo
    ) {
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = new LambdaQueryWrapper<PurchaseReturnEntity>()
                .eq(PurchaseReturnEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseReturnEntity::getReturnNo, keyword);
        }
        if (receiptId != null) {
            wrapper.eq(PurchaseReturnEntity::getReceiptId, receiptId);
        }
        if (warehouseId != null) {
            wrapper.eq(PurchaseReturnEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseReturnEntity::getStatus, status);
        }
        if (returnDateFrom != null) {
            wrapper.ge(PurchaseReturnEntity::getReturnDate, returnDateFrom);
        }
        if (returnDateTo != null) {
            wrapper.le(PurchaseReturnEntity::getReturnDate, returnDateTo);
        }
        return wrapper.orderByDesc(PurchaseReturnEntity::getId);
    }

    private PurchaseReturnResponse toSummaryResponse(PurchaseReturnEntity entity) {
        PurchaseReceiptEntity receipt = requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        );
        ReceiptContext context = loadContext(receipt);
        return new PurchaseReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getReceiptId(),
                context.receiptNo(),
                context.orderNo(),
                entity.getWarehouseId(),
                context.warehouseName(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                List.of()
        );
    }

    private PurchaseReturnLineResponse toLineResponse(PurchaseReturnLineEntity line) {
        return new PurchaseReturnLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getReceiptLineId(),
                line.getOrderLineId(),
                line.getProductId(),
                line.getProductName(),
                line.getQty(),
                line.getPrice(),
                line.getTaxRate(),
                line.getAmount(),
                line.getTaxAmount(),
                line.getReceiptQty(),
                line.getReturnedQty(),
                line.getAvailableReturnQty(),
                line.getLotNo(),
                line.getProductionDate(),
                line.getExpiryDate(),
                line.getLocationId(),
                line.getSerialNos(),
                line.getRemark()
        );
    }

    private PurchaseReturnLineEntity enrichLine(PurchaseReturnLineEntity line) {
        PurchaseReceiptLineEntity receiptLine = purchaseReceiptLineMapper.selectOne(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, line.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, line.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getId, line.getReceiptLineId())
        );
        if (receiptLine == null) {
            return line;
        }
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        ProductEntity product = productValidator.requireProduct(
                receiptLine.getProductId(),
                currentUser.companyId(),
                currentUser.accountBookId()
        );
        PurchaseReturnLineViewData.from(receiptLine, product).applyTo(line);
        return line;
    }

    ReceiptContext loadContext(PurchaseReceiptEntity receipt) {
        PurchaseOrderEntity order = purchaseOrderMapper.selectById(receipt.getOrderId());
        WarehouseEntity warehouse = warehouseMapper.selectById(receipt.getWarehouseId());
        return new ReceiptContext(
                receipt.getReceiptNo(),
                order == null ? null : order.getOrderNo(),
                warehouse == null ? null : warehouse.getWarehouseName()
        );
    }

    private PurchaseReturnEntity requireReturn(Long id) {
        PurchaseReturnEntity entity = purchaseReturnMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购退货单不存在");
        }
        return entity;
    }

    private PurchaseReceiptEntity requirePostedReceipt(Long id, Long companyId, Long accountBookId) {
        PurchaseReceiptEntity entity = purchaseReceiptMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !"POSTED".equals(entity.getStatus())
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("采购入库单未过账，不能创建采购退货单");
        }
        return entity;
    }

    private List<?> returnExportRow(PurchaseReturnEntity entity) {
        return Arrays.asList(
                entity.getReturnNo(),
                entity.getReceiptId(),
                entity.getWarehouseId(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark()
        );
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

    record ReceiptContext(String receiptNo, String orderNo, String warehouseName) {
    }
}
