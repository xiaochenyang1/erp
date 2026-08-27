package com.tuowei.erp.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.tuowei.erp.common.security.ErpPrincipal;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    private static final Set<String> TENANT_TABLES = Set.of(
            "md_product",
            "md_customer",
            "md_supplier",
            "md_warehouse",
            "md_location",
            "pur_order",
            "pur_order_line",
            "pur_receipt",
            "pur_receipt_line",
            "pur_return",
            "pur_return_line",
            "sal_order",
            "sal_order_line",
            "sal_quote",
            "sal_quote_line",
            "sal_delivery",
            "sal_delivery_line",
            "sal_return",
            "sal_return_line",
            "inv_balance",
            "inv_lot_balance",
            "inv_reservation",
            "inv_reservation_event",
            "inv_txn",
            "inv_transfer",
            "inv_transfer_line",
            "inv_adjustment",
            "inv_adjustment_line",
            "inv_stock_check",
            "inv_stock_check_line",
            "inv_alert_rule",
            "inv_alert_disposition",
            "inv_replenishment_suggestion",
            "fin_payable",
            "fin_payment",
            "fin_payment_allocation",
            "fin_fund_account",
            "fin_bank_statement",
            "fin_receivable",
            "fin_receipt",
            "fin_receipt_allocation",
            "fin_voucher",
            "fin_manual_voucher",
            "fin_manual_voucher_line",
            "fin_account_subject",
            "fin_account_period",
            "fin_period_close_snapshot",
            "fin_period_close_snapshot_item",
            "fin_expense",
            "fin_budget",
            "fin_budget_line",
            "fin_invoice_register",
            "fin_voucher_entry",
            "sys_dept",
            "sys_post",
            "sys_import_job",
            "sys_import_job_row",
            "sys_sequence_rule",
            "sys_sequence_counter",
            "sys_attachment",
            "sys_notification",
            "sys_notification_recipient",
            "sys_readiness_run",
            "sys_readiness_item",
            "sys_readiness_evidence",
            "biz_exception_ticket",
            "biz_exception_ticket_event",
            "biz_exception_rule",
            "biz_exception_rule_hit",
            "biz_exception_sla_policy",
            "biz_business_timeline",
            "wf_approval_config",
            "wf_approval_node",
            "wf_approval_node_approver",
            "prd_bom",
            "prd_bom_line",
            "prd_work_center",
            "prd_routing",
            "prd_routing_operation",
            "prd_order",
            "prd_order_material",
            "prd_order_operation",
            "prd_issue",
            "prd_issue_line",
            "prd_completion",
            "prd_completion_reversal",
            "prd_return",
            "prd_return_line",
            "wf_approval_instance",
            "wf_approval_task",
            "wf_approval_record",
            "qc_inspection_order",
            "qc_inspection_line",
            "pur_inquiry",
            "pur_inquiry_line",
            "pur_inquiry_quote",
            "pur_inquiry_quote_line",
            "pur_requisition",
            "pur_requisition_line",
            "md_sales_price",
            "md_purchase_price",
            "inv_mrp_run",
            "inv_mrp_run_line",
            "inv_serial_number"
    );

    private static final Map<String, String> TENANT_TABLE_EXEMPTIONS = Map.of(
            "sys_user", "认证和JWT解析按username/userId加载用户时尚未建立ErpPrincipal，业务查询已手动校验company_id",
            "sys_role", "登录期间构建权限和数据范围快照时尚未建立ErpPrincipal，角色查询已手动按company_id过滤",
            "sys_refresh_token", "刷新和登出接口使用token hash定位会话且认证前无ErpPrincipal，后台会话管理已手动按company_id过滤",
            "sys_login_log", "登录成功/失败日志可能发生在认证前，日志服务写入和查询已手动按company_id/account_book_id过滤",
            "sys_operation_log", "操作日志由响应增强统一写入，日志服务写入和查询已手动按company_id/account_book_id过滤",
            "sys_audit_log", "审计日志由业务服务显式记录，日志服务写入和查询已手动按company_id/account_book_id过滤",
            "sys_idempotency_request", "幂等过滤器在请求认证链路早期运行，IdempotencyService已按company_id/account_book_id/user_id显式过滤"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new CurrentUserTenantLineHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    private static class CurrentUserTenantLineHandler implements TenantLineHandler {

        @Override
        public Expression getTenantId() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof ErpPrincipal principal) {
                return new LongValue(principal.companyId());
            }
            throw new IllegalStateException("租户表查询缺少当前登录用户");
        }

        @Override
        public String getTenantIdColumn() {
            return "company_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return !TENANT_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
        }
    }
}
