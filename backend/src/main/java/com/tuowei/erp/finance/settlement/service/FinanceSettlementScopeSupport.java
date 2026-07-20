package com.tuowei.erp.finance.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FinanceSettlementScopeSupport {

    private static final List<String> SALES_SOURCE_TYPES = List.of("SALES_DELIVERY", "SALES_RETURN");
    private static final List<String> PURCHASE_SOURCE_TYPES = List.of("PURCHASE_RECEIPT", "PURCHASE_RETURN");

    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesReturnMapper salesReturnMapper;

    public FinanceSettlementScopeSupport(
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesReturnMapper salesReturnMapper
    ) {
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesReturnMapper = salesReturnMapper;
    }

    public LambdaQueryWrapper<ReceivableEntity> applyReceivableScope(LambdaQueryWrapper<ReceivableEntity> wrapper) {
        ScopedUsers scopedUsers = scopedUsers();
        wrapper.eq(ReceivableEntity::getCompanyId, scopedUsers.currentUser().companyId())
                .eq(ReceivableEntity::getAccountBookId, scopedUsers.currentUser().accountBookId())
                .eq(ReceivableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            return wrapper;
        }

        Set<Long> creatorIds = visibleCreatorIds(scopedUsers);
        Set<Long> warehouseIds = scopedUsers.snapshot().warehouseIds();
        boolean canViewSourceDocuments = canViewSourceDocuments(creatorIds, warehouseIds);
        if (!canViewSourceDocuments && creatorIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.and(scope -> {
            boolean appended = false;
            if (canViewSourceDocuments) {
                SqlPredicate deliveryScope = sourceScopeSql("sal_delivery", "sd", scopedUsers, creatorIds, warehouseIds);
                scope.nested(item -> item
                        .eq(ReceivableEntity::getSourceType, "SALES_DELIVERY")
                        .exists(true, deliveryScope.sql(), deliveryScope.params()));
                appended = true;
                if (appended) {
                    scope.or();
                }
                SqlPredicate returnScope = sourceScopeSql("sal_return", "sr", scopedUsers, creatorIds, warehouseIds);
                scope.nested(item -> item
                        .eq(ReceivableEntity::getSourceType, "SALES_RETURN")
                        .exists(true, returnScope.sql(), returnScope.params()));
            }
            if (!creatorIds.isEmpty()) {
                if (appended) {
                    scope.or();
                }
                scope.nested(item -> item
                        .notIn(ReceivableEntity::getSourceType, SALES_SOURCE_TYPES)
                        .in(ReceivableEntity::getCreatedBy, creatorIds));
            }
        });
    }

    public LambdaQueryWrapper<PayableEntity> applyPayableScope(LambdaQueryWrapper<PayableEntity> wrapper) {
        ScopedUsers scopedUsers = scopedUsers();
        wrapper.eq(PayableEntity::getCompanyId, scopedUsers.currentUser().companyId())
                .eq(PayableEntity::getAccountBookId, scopedUsers.currentUser().accountBookId())
                .eq(PayableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            return wrapper;
        }

        Set<Long> creatorIds = visibleCreatorIds(scopedUsers);
        Set<Long> warehouseIds = scopedUsers.snapshot().warehouseIds();
        boolean canViewSourceDocuments = canViewSourceDocuments(creatorIds, warehouseIds);
        if (!canViewSourceDocuments && creatorIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.and(scope -> {
            boolean appended = false;
            if (canViewSourceDocuments) {
                SqlPredicate receiptScope = sourceScopeSql("pur_receipt", "pr", scopedUsers, creatorIds, warehouseIds);
                scope.nested(item -> item
                        .eq(PayableEntity::getSourceType, "PURCHASE_RECEIPT")
                        .exists(true, receiptScope.sql(), receiptScope.params()));
                appended = true;
                if (appended) {
                    scope.or();
                }
                SqlPredicate returnScope = sourceScopeSql("pur_return", "prt", scopedUsers, creatorIds, warehouseIds);
                scope.nested(item -> item
                        .eq(PayableEntity::getSourceType, "PURCHASE_RETURN")
                        .exists(true, returnScope.sql(), returnScope.params()));
            }
            if (!creatorIds.isEmpty()) {
                if (appended) {
                    scope.or();
                }
                scope.nested(item -> item
                        .notIn(PayableEntity::getSourceType, PURCHASE_SOURCE_TYPES)
                        .in(PayableEntity::getCreatedBy, creatorIds));
            }
        });
    }

    public void assertCanViewReceivable(ReceivableEntity entity) {
        ScopedUsers scopedUsers = scopedUsers();
        if (!Objects.equals(entity.getCompanyId(), scopedUsers.currentUser().companyId())) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (!Objects.equals(entity.getAccountBookId(), scopedUsers.currentUser().accountBookId())) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (scopedUsers.snapshot().hasAllScope()) {
            return;
        }
        if ("SALES_DELIVERY".equals(entity.getSourceType())
                && canViewSalesDelivery(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if ("SALES_RETURN".equals(entity.getSourceType())
                && canViewSalesReturn(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if (!SALES_SOURCE_TYPES.contains(entity.getSourceType())
                && visibleCreatorIds(scopedUsers).contains(entity.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("无权访问该应收记录");
    }

    public void assertCanViewPayable(PayableEntity entity) {
        ScopedUsers scopedUsers = scopedUsers();
        if (!Objects.equals(entity.getCompanyId(), scopedUsers.currentUser().companyId())) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (!Objects.equals(entity.getAccountBookId(), scopedUsers.currentUser().accountBookId())) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (scopedUsers.snapshot().hasAllScope()) {
            return;
        }
        if ("PURCHASE_RECEIPT".equals(entity.getSourceType())
                && canViewPurchaseReceipt(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if ("PURCHASE_RETURN".equals(entity.getSourceType())
                && canViewPurchaseReturn(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if (!PURCHASE_SOURCE_TYPES.contains(entity.getSourceType())
                && visibleCreatorIds(scopedUsers).contains(entity.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("无权访问该应付记录");
    }

    private Set<Long> visibleCreatorIds(ScopedUsers scopedUsers) {
        Set<Long> creatorIds = new LinkedHashSet<>();
        if (scopedUsers.snapshot().selfScoped()) {
            creatorIds.add(scopedUsers.currentUser().userId());
        }
        if (scopedUsers.snapshot().deptScoped()) {
            creatorIds.addAll(scopedUsers.deptUserIds());
        }
        if (scopedUsers.snapshot().postScoped()) {
            creatorIds.addAll(scopedUsers.postUserIds());
        }
        return creatorIds;
    }

    private boolean canViewSourceDocuments(Set<Long> creatorIds, Set<Long> warehouseIds) {
        return !creatorIds.isEmpty() || !warehouseIds.isEmpty();
    }

    private SqlPredicate sourceScopeSql(
            String tableName,
            String alias,
            ScopedUsers scopedUsers,
            Set<Long> creatorIds,
            Set<Long> warehouseIds
    ) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder()
                .append("select 1 from ")
                .append(tableName)
                .append(' ')
                .append(alias)
                .append(" where ")
                .append(alias)
                .append(".id = source_id and ")
                .append(alias)
                .append(".company_id = ")
                .append(addParam(params, scopedUsers.currentUser().companyId()))
                .append(" and ")
                .append(alias)
                .append(".account_book_id = ")
                .append(addParam(params, scopedUsers.currentUser().accountBookId()))
                .append(" and ")
                .append(alias)
                .append(".deleted_flag = 0 and ")
                .append(sourceVisibilitySql(alias, creatorIds, warehouseIds, params));
        return new SqlPredicate(sql.toString(), params.toArray());
    }

    private String sourceVisibilitySql(
            String alias,
            Set<Long> creatorIds,
            Set<Long> warehouseIds,
            List<Object> params
    ) {
        if (creatorIds.isEmpty()) {
            return inSql(alias + ".warehouse_id", warehouseIds, params);
        }
        if (warehouseIds.isEmpty()) {
            return inSql(alias + ".created_by", creatorIds, params);
        }
        return "("
                + inSql(alias + ".created_by", creatorIds, params)
                + " or "
                + inSql(alias + ".warehouse_id", warehouseIds, params)
                + ")";
    }

    private String inSql(String columnName, Collection<Long> values, List<Object> params) {
        return columnName + " in (" + values.stream()
                .map(value -> addParam(params, value))
                .collect(Collectors.joining(", ")) + ")";
    }

    private String addParam(List<Object> params, Object value) {
        String placeholder = "{" + params.size() + "}";
        params.add(value);
        return placeholder;
    }

    private boolean canViewSalesDelivery(Long sourceId, ScopedUsers scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = salesDeliveryMapper.selectCount(dataScopeService.applySalesDeliveryScope(
                new LambdaQueryWrapper<SalesDeliveryEntity>()
                        .eq(SalesDeliveryEntity::getId, sourceId)
                        .eq(SalesDeliveryEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private boolean canViewSalesReturn(Long sourceId, ScopedUsers scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = salesReturnMapper.selectCount(dataScopeService.applySalesReturnScope(
                new LambdaQueryWrapper<SalesReturnEntity>()
                        .eq(SalesReturnEntity::getId, sourceId)
                        .eq(SalesReturnEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private boolean canViewPurchaseReceipt(Long sourceId, ScopedUsers scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = purchaseReceiptMapper.selectCount(dataScopeService.applyPurchaseReceiptScope(
                new LambdaQueryWrapper<PurchaseReceiptEntity>()
                        .eq(PurchaseReceiptEntity::getId, sourceId)
                        .eq(PurchaseReceiptEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private boolean canViewPurchaseReturn(Long sourceId, ScopedUsers scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = purchaseReturnMapper.selectCount(dataScopeService.applyPurchaseReturnScope(
                new LambdaQueryWrapper<PurchaseReturnEntity>()
                        .eq(PurchaseReturnEntity::getId, sourceId)
                        .eq(PurchaseReturnEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private ScopedUsers scopedUsers() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        return new ScopedUsers(currentUser, snapshot, scopedUserIds.deptUserIds(), scopedUserIds.postUserIds());
    }

    private record ScopedUsers(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
    }

    private record SqlPredicate(String sql, Object[] params) {
    }
}
