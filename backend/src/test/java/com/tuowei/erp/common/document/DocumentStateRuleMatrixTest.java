package com.tuowei.erp.common.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DocumentStateRuleMatrixTest {

    private final DocumentStateRuleService service = new DocumentStateRuleService();

    @Test
    void exposesPurchaseAndSalesOrderLifecycleRules() {
        List<DocumentStateRuleResponse> rules = service.list();

        assertThat(rules)
                .extracting(DocumentStateRuleResponse::documentType, DocumentStateRuleResponse::action)
                .contains(
                        tuple("PURCHASE_ORDER", "SUBMIT"),
                        tuple("PURCHASE_ORDER", "APPROVE"),
                        tuple("PURCHASE_ORDER", "UNAPPROVE"),
                        tuple("PURCHASE_ORDER", "REJECT"),
                        tuple("PURCHASE_ORDER", "CANCEL"),
                        tuple("PURCHASE_ORDER", "CLOSE"),
                        tuple("SALES_ORDER", "SUBMIT"),
                        tuple("SALES_ORDER", "APPROVE"),
                        tuple("SALES_ORDER", "UNAPPROVE"),
                        tuple("SALES_ORDER", "REJECT"),
                        tuple("SALES_ORDER", "CANCEL")
                );
    }

    @Test
    void purchaseOrderUnapproveRuleMatchesServiceGuards() {
        DocumentStateRuleResponse rule = findRule("PURCHASE_ORDER", "UNAPPROVE");

        assertThat(rule.method()).isEqualTo("POST");
        assertThat(rule.path()).isEqualTo("/api/purchase/orders/{id}/unapprove");
        assertThat(rule.permission()).isEqualTo("purchase:order:unapprove");
        assertThat(rule.allowedStatuses()).containsExactly("APPROVED");
        assertThat(rule.allowedApprovalStatuses()).containsExactly("APPROVED");
        assertThat(rule.executionStatusField()).isEqualTo("receiptStatus");
        assertThat(rule.allowedExecutionStatuses()).containsExactly("NOT_RECEIVED");
        assertThat(rule.blockedExecutionStatuses()).containsExactly("PARTIAL_RECEIVED", "RECEIVED");
        assertThat(rule.targetStatus()).isEqualTo("DRAFT");
        assertThat(rule.targetApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(rule.stateFailureMessage()).isEqualTo("当前采购订单状态不允许反审核");
        assertThat(rule.executionFailureMessage()).isEqualTo("已入库采购订单不允许反审核");
    }

    @Test
    void salesOrderUnapproveRuleMatchesServiceGuards() {
        DocumentStateRuleResponse rule = findRule("SALES_ORDER", "UNAPPROVE");

        assertThat(rule.method()).isEqualTo("POST");
        assertThat(rule.path()).isEqualTo("/api/sales/orders/{id}/unapprove");
        assertThat(rule.permission()).isEqualTo("sales:order:unapprove");
        assertThat(rule.allowedStatuses()).containsExactly("APPROVED");
        assertThat(rule.allowedApprovalStatuses()).containsExactly("APPROVED");
        assertThat(rule.executionStatusField()).isEqualTo("deliveryStatus");
        assertThat(rule.allowedExecutionStatuses()).containsExactly("NOT_DELIVERED");
        assertThat(rule.blockedExecutionStatuses()).containsExactly("PARTIAL_DELIVERED", "FULL_DELIVERED");
        assertThat(rule.targetStatus()).isEqualTo("DRAFT");
        assertThat(rule.targetApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(rule.stateFailureMessage()).isEqualTo("当前销售订单状态不允许反审核");
        assertThat(rule.executionFailureMessage()).isEqualTo("已出库销售订单不允许反审核");
    }

    private DocumentStateRuleResponse findRule(String documentType, String action) {
        return service.list().stream()
                .filter(rule -> documentType.equals(rule.documentType()) && action.equals(rule.action()))
                .findFirst()
                .orElseThrow();
    }
}
