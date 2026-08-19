package com.tuowei.erp.common.document;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStateRuleMatrixTest {

    private final DocumentStateRuleService service = new DocumentStateRuleService();

    @Test
    void exposesEachOrderLifecycleActionExactlyOnce() {
        assertThat(service.list())
                .extracting(rule -> rule.documentType() + ":" + rule.action())
                .containsExactly(
                        "PURCHASE_ORDER:SUBMIT",
                        "PURCHASE_ORDER:APPROVE",
                        "PURCHASE_ORDER:UNAPPROVE",
                        "PURCHASE_ORDER:REJECT",
                        "PURCHASE_ORDER:CANCEL",
                        "PURCHASE_ORDER:CLOSE",
                        "SALES_ORDER:SUBMIT",
                        "SALES_ORDER:APPROVE",
                        "SALES_ORDER:UNAPPROVE",
                        "SALES_ORDER:REJECT",
                        "SALES_ORDER:CANCEL"
                );
    }

    @Test
    void purchaseOrderRulesMatchLifecycleContract() {
        assertOrderRule(
                "PURCHASE_ORDER",
                "采购订单",
                "purchase",
                "SUBMIT",
                "提交",
                List.of("DRAFT", "REJECTED"),
                List.of("NOT_SUBMITTED", "REJECTED"),
                "",
                List.of(),
                List.of(),
                "SUBMITTED",
                "IN_APPROVAL",
                "当前采购订单状态不允许提交审批",
                ""
        );
        assertOrderRule(
                "PURCHASE_ORDER",
                "采购订单",
                "purchase",
                "APPROVE",
                "审核通过",
                List.of("SUBMITTED"),
                List.of("IN_APPROVAL"),
                "",
                List.of(),
                List.of(),
                "APPROVED",
                "APPROVED",
                "当前采购订单状态不允许审批通过",
                ""
        );
        assertOrderRule(
                "PURCHASE_ORDER",
                "采购订单",
                "purchase",
                "UNAPPROVE",
                "反审核",
                List.of("APPROVED"),
                List.of("APPROVED"),
                "receiptStatus",
                List.of("NOT_RECEIVED"),
                List.of("PARTIAL_RECEIVED", "RECEIVED"),
                "DRAFT",
                "NOT_SUBMITTED",
                "当前采购订单状态不允许反审核",
                "已入库采购订单不允许反审核"
        );
        assertOrderRule(
                "PURCHASE_ORDER",
                "采购订单",
                "purchase",
                "REJECT",
                "驳回",
                List.of("SUBMITTED"),
                List.of("IN_APPROVAL"),
                "",
                List.of(),
                List.of(),
                "REJECTED",
                "REJECTED",
                "当前采购订单状态不允许驳回",
                ""
        );
        assertOrderRule(
                "PURCHASE_ORDER",
                "采购订单",
                "purchase",
                "CANCEL",
                "作废",
                List.of("DRAFT", "REJECTED", "SUBMITTED"),
                List.of("NOT_SUBMITTED", "REJECTED", "IN_APPROVAL"),
                "",
                List.of(),
                List.of(),
                "CANCELLED",
                "CANCELLED",
                "当前采购订单状态不允许作废",
                ""
        );
        assertOrderRule(
                "PURCHASE_ORDER",
                "采购订单",
                "purchase",
                "CLOSE",
                "关闭",
                List.of("APPROVED"),
                List.of("APPROVED"),
                "receiptStatus",
                List.of("NOT_RECEIVED", "PARTIAL_RECEIVED"),
                List.of("RECEIVED"),
                "CLOSED",
                "APPROVED",
                "当前采购订单状态不允许关闭",
                "已完全入库的采购订单不允许关闭"
        );
    }

    @Test
    void salesOrderRulesMatchLifecycleContract() {
        assertOrderRule(
                "SALES_ORDER",
                "销售订单",
                "sales",
                "SUBMIT",
                "提交",
                List.of("DRAFT", "REJECTED"),
                List.of("NOT_SUBMITTED", "REJECTED"),
                "",
                List.of(),
                List.of(),
                "SUBMITTED",
                "IN_APPROVAL",
                "当前销售订单状态不允许提交审批",
                ""
        );
        assertOrderRule(
                "SALES_ORDER",
                "销售订单",
                "sales",
                "APPROVE",
                "审核通过",
                List.of("SUBMITTED"),
                List.of("IN_APPROVAL"),
                "",
                List.of(),
                List.of(),
                "APPROVED",
                "APPROVED",
                "当前销售订单状态不允许审批通过",
                ""
        );
        assertOrderRule(
                "SALES_ORDER",
                "销售订单",
                "sales",
                "UNAPPROVE",
                "反审核",
                List.of("APPROVED"),
                List.of("APPROVED"),
                "deliveryStatus",
                List.of("NOT_DELIVERED"),
                List.of("PARTIAL_DELIVERED", "FULL_DELIVERED"),
                "DRAFT",
                "NOT_SUBMITTED",
                "当前销售订单状态不允许反审核",
                "已出库销售订单不允许反审核"
        );
        assertOrderRule(
                "SALES_ORDER",
                "销售订单",
                "sales",
                "REJECT",
                "驳回",
                List.of("SUBMITTED"),
                List.of("IN_APPROVAL"),
                "",
                List.of(),
                List.of(),
                "REJECTED",
                "REJECTED",
                "当前销售订单状态不允许驳回",
                ""
        );
        assertOrderRule(
                "SALES_ORDER",
                "销售订单",
                "sales",
                "CANCEL",
                "作废",
                List.of("DRAFT", "REJECTED", "SUBMITTED", "APPROVED"),
                List.of("NOT_SUBMITTED", "REJECTED", "IN_APPROVAL", "APPROVED"),
                "deliveryStatus",
                List.of("NOT_DELIVERED"),
                List.of("PARTIAL_DELIVERED", "FULL_DELIVERED"),
                "CANCELLED",
                "CANCELLED",
                "当前销售订单状态不允许作废",
                "已出库销售订单不允许作废"
        );
    }

    private void assertOrderRule(
            String documentType,
            String documentName,
            String apiDomain,
            String action,
            String actionName,
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
        String actionPath = action.toLowerCase(Locale.ROOT);
        String permission = apiDomain + ":order:" + actionPath;
        DocumentStateRuleResponse expected = new DocumentStateRuleResponse(
                documentType,
                documentName,
                action,
                actionName,
                "POST",
                "/api/" + apiDomain + "/orders/{id}/" + actionPath,
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

        assertThat(findRule(documentType, action)).isEqualTo(expected);
    }

    private DocumentStateRuleResponse findRule(String documentType, String action) {
        return service.list().stream()
                .filter(rule -> documentType.equals(rule.documentType()) && action.equals(rule.action()))
                .findFirst()
                .orElseThrow();
    }
}
