package com.tuowei.erp.finance.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeContextResolver.ResolvedScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FinanceSettlementQueryScopeService {

    private static final List<String> SALES_SOURCE_TYPES = List.of("SALES_DELIVERY", "SALES_RETURN");
    private static final List<String> PURCHASE_SOURCE_TYPES = List.of("PURCHASE_RECEIPT", "PURCHASE_RETURN");

    private final FinanceSettlementScopeContextResolver scopeContextResolver;

    public FinanceSettlementQueryScopeService(FinanceSettlementScopeContextResolver scopeContextResolver) {
        this.scopeContextResolver = scopeContextResolver;
    }

    public LambdaQueryWrapper<ReceivableEntity> applyReceivableScope(LambdaQueryWrapper<ReceivableEntity> wrapper) {
        ResolvedScope scopedUsers = scopeContextResolver.resolve();
        wrapper.eq(ReceivableEntity::getCompanyId, scopedUsers.currentUser().companyId())
                .eq(ReceivableEntity::getAccountBookId, scopedUsers.currentUser().accountBookId())
                .eq(ReceivableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            return wrapper;
        }

        Set<Long> creatorIds = scopeContextResolver.visibleCreatorIds(scopedUsers);
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
        ResolvedScope scopedUsers = scopeContextResolver.resolve();
        wrapper.eq(PayableEntity::getCompanyId, scopedUsers.currentUser().companyId())
                .eq(PayableEntity::getAccountBookId, scopedUsers.currentUser().accountBookId())
                .eq(PayableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            return wrapper;
        }

        Set<Long> creatorIds = scopeContextResolver.visibleCreatorIds(scopedUsers);
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

    private boolean canViewSourceDocuments(Set<Long> creatorIds, Set<Long> warehouseIds) {
        return !creatorIds.isEmpty() || !warehouseIds.isEmpty();
    }

    private SqlPredicate sourceScopeSql(
            String tableName,
            String alias,
            ResolvedScope scopedUsers,
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

    private record SqlPredicate(String sql, Object[] params) {
    }
}
