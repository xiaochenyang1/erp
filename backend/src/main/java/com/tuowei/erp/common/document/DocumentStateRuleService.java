package com.tuowei.erp.common.document;

import com.tuowei.erp.common.security.PermissionCodes;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentStateRuleService {

    private static final List<DocumentStateRuleResponse> RULES = List.of(
            rule(
                    "PURCHASE_ORDER",
                    "采购订单",
                    "SUBMIT",
                    "提交",
                    "/api/purchase/orders/{id}/submit",
                    PermissionCodes.PURCHASE_ORDER_SUBMIT,
                    List.of("DRAFT", "REJECTED"),
                    List.of("NOT_SUBMITTED", "REJECTED"),
                    "",
                    List.of(),
                    List.of(),
                    "SUBMITTED",
                    "IN_APPROVAL",
                    "当前采购订单状态不允许提交审批",
                    ""
            ),
            rule(
                    "PURCHASE_ORDER",
                    "采购订单",
                    "APPROVE",
                    "审核通过",
                    "/api/purchase/orders/{id}/approve",
                    PermissionCodes.PURCHASE_ORDER_APPROVE,
                    List.of("SUBMITTED"),
                    List.of("IN_APPROVAL"),
                    "",
                    List.of(),
                    List.of(),
                    "APPROVED",
                    "APPROVED",
                    "当前采购订单状态不允许审批通过",
                    ""
            ),
            rule(
                    "PURCHASE_ORDER",
                    "采购订单",
                    "UNAPPROVE",
                    "反审核",
                    "/api/purchase/orders/{id}/unapprove",
                    PermissionCodes.PURCHASE_ORDER_UNAPPROVE,
                    List.of("APPROVED"),
                    List.of("APPROVED"),
                    "receiptStatus",
                    List.of("NOT_RECEIVED"),
                    List.of("PARTIAL_RECEIVED", "RECEIVED"),
                    "DRAFT",
                    "NOT_SUBMITTED",
                    "当前采购订单状态不允许反审核",
                    "已入库采购订单不允许反审核"
            ),
            rule(
                    "PURCHASE_ORDER",
                    "采购订单",
                    "REJECT",
                    "驳回",
                    "/api/purchase/orders/{id}/reject",
                    PermissionCodes.PURCHASE_ORDER_REJECT,
                    List.of("SUBMITTED"),
                    List.of("IN_APPROVAL"),
                    "",
                    List.of(),
                    List.of(),
                    "REJECTED",
                    "REJECTED",
                    "当前采购订单状态不允许驳回",
                    ""
            ),
            rule(
                    "PURCHASE_ORDER",
                    "采购订单",
                    "CANCEL",
                    "作废",
                    "/api/purchase/orders/{id}/cancel",
                    PermissionCodes.PURCHASE_ORDER_CANCEL,
                    List.of("DRAFT", "REJECTED", "SUBMITTED"),
                    List.of("NOT_SUBMITTED", "REJECTED", "IN_APPROVAL"),
                    "",
                    List.of(),
                    List.of(),
                    "CANCELLED",
                    "CANCELLED",
                    "当前采购订单状态不允许作废",
                    ""
            ),
            rule(
                    "PURCHASE_ORDER",
                    "采购订单",
                    "CLOSE",
                    "关闭",
                    "/api/purchase/orders/{id}/close",
                    PermissionCodes.PURCHASE_ORDER_CLOSE,
                    List.of("APPROVED"),
                    List.of("APPROVED"),
                    "receiptStatus",
                    List.of("NOT_RECEIVED", "PARTIAL_RECEIVED"),
                    List.of("RECEIVED"),
                    "CLOSED",
                    "APPROVED",
                    "当前采购订单状态不允许关闭",
                    "已完全入库的采购订单不允许关闭"
            ),
            rule(
                    "SALES_ORDER",
                    "销售订单",
                    "SUBMIT",
                    "提交",
                    "/api/sales/orders/{id}/submit",
                    PermissionCodes.SALES_ORDER_SUBMIT,
                    List.of("DRAFT", "REJECTED"),
                    List.of("NOT_SUBMITTED", "REJECTED"),
                    "",
                    List.of(),
                    List.of(),
                    "SUBMITTED",
                    "IN_APPROVAL",
                    "当前销售订单状态不允许提交审批",
                    ""
            ),
            rule(
                    "SALES_ORDER",
                    "销售订单",
                    "APPROVE",
                    "审核通过",
                    "/api/sales/orders/{id}/approve",
                    PermissionCodes.SALES_ORDER_APPROVE,
                    List.of("SUBMITTED"),
                    List.of("IN_APPROVAL"),
                    "",
                    List.of(),
                    List.of(),
                    "APPROVED",
                    "APPROVED",
                    "当前销售订单状态不允许审批通过",
                    ""
            ),
            rule(
                    "SALES_ORDER",
                    "销售订单",
                    "UNAPPROVE",
                    "反审核",
                    "/api/sales/orders/{id}/unapprove",
                    PermissionCodes.SALES_ORDER_UNAPPROVE,
                    List.of("APPROVED"),
                    List.of("APPROVED"),
                    "deliveryStatus",
                    List.of("NOT_DELIVERED"),
                    List.of("PARTIAL_DELIVERED", "FULL_DELIVERED"),
                    "DRAFT",
                    "NOT_SUBMITTED",
                    "当前销售订单状态不允许反审核",
                    "已出库销售订单不允许反审核"
            ),
            rule(
                    "SALES_ORDER",
                    "销售订单",
                    "REJECT",
                    "驳回",
                    "/api/sales/orders/{id}/reject",
                    PermissionCodes.SALES_ORDER_REJECT,
                    List.of("SUBMITTED"),
                    List.of("IN_APPROVAL"),
                    "",
                    List.of(),
                    List.of(),
                    "REJECTED",
                    "REJECTED",
                    "当前销售订单状态不允许驳回",
                    ""
            ),
            rule(
                    "SALES_ORDER",
                    "销售订单",
                    "CANCEL",
                    "作废",
                    "/api/sales/orders/{id}/cancel",
                    PermissionCodes.SALES_ORDER_CANCEL,
                    List.of("DRAFT", "REJECTED", "SUBMITTED", "APPROVED"),
                    List.of("NOT_SUBMITTED", "REJECTED", "IN_APPROVAL", "APPROVED"),
                    "deliveryStatus",
                    List.of("NOT_DELIVERED"),
                    List.of("PARTIAL_DELIVERED", "FULL_DELIVERED"),
                    "CANCELLED",
                    "CANCELLED",
                    "当前销售订单状态不允许作废",
                    "已出库销售订单不允许作废"
            )
    );

    public List<DocumentStateRuleResponse> list() {
        return RULES;
    }

    private static DocumentStateRuleResponse rule(
            String documentType,
            String documentName,
            String action,
            String actionName,
            String path,
            String permission,
            List<String> allowedStatuses,
            List<String> allowedApprovalStatuses,
            String executionStatusField,
            List<String> allowedExecutionStatuses,
            List<String> blockedExecutionStatuses,
            String targetStatus,
            String targetApprovalStatus,
            String stateFailureMessage,
            String executionFailureMessage
    ) {
        return new DocumentStateRuleResponse(
                documentType,
                documentName,
                action,
                actionName,
                "POST",
                path,
                permission,
                allowedStatuses,
                allowedApprovalStatuses,
                executionStatusField,
                allowedExecutionStatuses,
                blockedExecutionStatuses,
                targetStatus,
                targetApprovalStatus,
                stateFailureMessage,
                executionFailureMessage
        );
    }
}
