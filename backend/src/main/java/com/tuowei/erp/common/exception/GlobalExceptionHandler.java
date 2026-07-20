package com.tuowei.erp.common.exception;

import com.tuowei.erp.common.ratelimit.RateLimitExceededException;
import com.tuowei.erp.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DUPLICATE_KEY_FALLBACK_MESSAGE = "数据唯一约束冲突";
    private static final Map<String, String> DUPLICATE_KEY_MESSAGES = Map.ofEntries(
            Map.entry("uk_sys_user_username", "用户名已存在"),
            Map.entry("uk_sys_user_employee_no", "员工编号已存在"),
            Map.entry("uk_sys_user_mobile", "手机号已存在"),
            Map.entry("uk_sys_config_config_code", "参数编码已存在"),
            Map.entry("uk_sys_sequence_rule_biz_type", "业务类型已存在"),
            Map.entry("uk_sys_sequence_rule_company_biz_type", "业务类型已存在"),
            Map.entry("uk_sys_sequence_rule_company_book_biz_type", "业务类型已存在"),
            Map.entry("uk_sys_dept_dept_code", "部门编码已存在"),
            Map.entry("uk_sys_dept_company_dept_code", "部门编码已存在"),
            Map.entry("uk_sys_dept_company_book_dept_code", "部门编码已存在"),
            Map.entry("uk_sys_post_post_code", "岗位编码已存在"),
            Map.entry("uk_sys_post_company_post_code", "岗位编码已存在"),
            Map.entry("uk_sys_post_company_book_post_code", "岗位编码已存在"),
            Map.entry("uk_sys_role_role_code", "角色编码已存在"),
            Map.entry("uk_sys_role_company_role_code", "角色编码已存在"),
            Map.entry("uk_sys_role_company_book_role_code", "角色编码已存在"),
            Map.entry("uk_sys_menu_menu_code", "菜单编码已存在"),
            Map.entry("uk_sys_dict_type_type", "字典类型已存在"),
            Map.entry("uk_sys_dict_item_type_value", "字典项值已存在"),
            Map.entry("uk_md_warehouse_warehouse_code", "仓库编码已存在"),
            Map.entry("uk_md_warehouse_company_warehouse_code", "仓库编码已存在"),
            Map.entry("uk_md_warehouse_company_book_warehouse_code", "仓库编码已存在"),
            Map.entry("uk_md_product_product_code", "商品编码已存在"),
            Map.entry("uk_md_product_company_product_code", "商品编码已存在"),
            Map.entry("uk_md_product_company_book_product_code", "商品编码已存在"),
            Map.entry("uk_md_customer_customer_code", "客户编码已存在"),
            Map.entry("uk_md_customer_company_customer_code", "客户编码已存在"),
            Map.entry("uk_md_customer_company_book_customer_code", "客户编码已存在"),
            Map.entry("uk_md_supplier_supplier_code", "供应商编码已存在"),
            Map.entry("uk_md_supplier_company_supplier_code", "供应商编码已存在"),
            Map.entry("uk_md_supplier_company_book_supplier_code", "供应商编码已存在"),
            Map.entry("uk_prd_work_center_company_book_code", "工作中心编码已存在"),
            Map.entry("uk_pur_order_order_no", "采购订单号已存在"),
            Map.entry("uk_pur_order_company_order_no", "采购订单号已存在"),
            Map.entry("uk_pur_order_company_book_order_no", "采购订单号已存在"),
            Map.entry("uk_pur_receipt_receipt_no", "采购入库单号已存在"),
            Map.entry("uk_pur_receipt_company_receipt_no", "采购入库单号已存在"),
            Map.entry("uk_pur_receipt_company_book_receipt_no", "采购入库单号已存在"),
            Map.entry("uk_pur_return_return_no", "采购退货单号已存在"),
            Map.entry("uk_pur_return_company_return_no", "采购退货单号已存在"),
            Map.entry("uk_pur_return_company_book_return_no", "采购退货单号已存在"),
            Map.entry("uk_sal_order_order_no", "销售订单号已存在"),
            Map.entry("uk_sal_order_company_order_no", "销售订单号已存在"),
            Map.entry("uk_sal_order_company_book_order_no", "销售订单号已存在"),
            Map.entry("uk_sal_delivery_delivery_no", "销售出库单号已存在"),
            Map.entry("uk_sal_delivery_company_delivery_no", "销售出库单号已存在"),
            Map.entry("uk_sal_delivery_company_book_delivery_no", "销售出库单号已存在"),
            Map.entry("uk_sal_return_return_no", "销售退货单号已存在"),
            Map.entry("uk_sal_return_company_return_no", "销售退货单号已存在"),
            Map.entry("uk_sal_return_company_book_return_no", "销售退货单号已存在"),
            Map.entry("uk_inv_adjustment_adjustment_no", "库存调整单号已存在"),
            Map.entry("uk_inv_adjustment_company_adjustment_no", "库存调整单号已存在"),
            Map.entry("uk_inv_adjustment_company_book_adjustment_no", "库存调整单号已存在"),
            Map.entry("uk_inv_stock_check_check_no", "库存盘点单号已存在"),
            Map.entry("uk_inv_stock_check_company_check_no", "库存盘点单号已存在"),
            Map.entry("uk_inv_stock_check_company_book_check_no", "库存盘点单号已存在"),
            Map.entry("uk_inv_transfer_transfer_no", "库存调拨单号已存在"),
            Map.entry("uk_inv_transfer_company_transfer_no", "库存调拨单号已存在"),
            Map.entry("uk_inv_transfer_company_book_transfer_no", "库存调拨单号已存在"),
            Map.entry("uk_inv_alert_rule_company_book_product_warehouse", "库存预警规则已存在"),
            Map.entry("uk_inv_balance_company_book_warehouse_product", "库存余额已存在"),
            Map.entry("uk_inv_txn_company_book_biz_line_direction_lot_key", "库存流水已存在"),
            Map.entry("uk_inv_reservation_company_book_source_line", "库存预占已存在"),
            Map.entry("uk_fin_payable_payable_no", "应付单号已存在"),
            Map.entry("uk_fin_payable_company_payable_no", "应付单号已存在"),
            Map.entry("uk_fin_payable_company_book_payable_no", "应付单号已存在"),
            Map.entry("uk_fin_payable_company_book_source", "来源应付单已存在"),
            Map.entry("uk_fin_payment_payment_no", "付款单号已存在"),
            Map.entry("uk_fin_payment_company_payment_no", "付款单号已存在"),
            Map.entry("uk_fin_payment_company_book_payment_no", "付款单号已存在"),
            Map.entry("uk_fin_receivable_receivable_no", "应收单号已存在"),
            Map.entry("uk_fin_receivable_company_receivable_no", "应收单号已存在"),
            Map.entry("uk_fin_receivable_company_book_receivable_no", "应收单号已存在"),
            Map.entry("uk_fin_receivable_company_book_source", "来源应收单已存在"),
            Map.entry("uk_fin_receipt_receipt_no", "收款单号已存在"),
            Map.entry("uk_fin_receipt_company_receipt_no", "收款单号已存在"),
            Map.entry("uk_fin_receipt_company_book_receipt_no", "收款单号已存在"),
            Map.entry("uk_fin_voucher_voucher_no", "凭证号已存在"),
            Map.entry("uk_fin_voucher_company_voucher_no", "凭证号已存在"),
            Map.entry("uk_fin_voucher_company_book_voucher_no", "凭证号已存在"),
            Map.entry("uk_fin_voucher_company_book_source", "来源凭证已存在"),
            Map.entry("uk_fin_account_subject_company_code", "科目编码已存在"),
            Map.entry("uk_fin_account_subject_company_book_code", "科目编码已存在"),
            Map.entry("uk_fin_expense_company_no", "费用单号已存在"),
            Map.entry("uk_fin_expense_company_book_no", "费用单号已存在"),
            Map.entry("uk_prd_bom_company_bom_no", "BOM编号已存在"),
            Map.entry("uk_prd_bom_company_book_bom_no", "BOM编号已存在"),
            Map.entry("uk_prd_order_company_order_no", "生产工单号已存在"),
            Map.entry("uk_prd_order_company_book_order_no", "生产工单号已存在"),
            Map.entry("uk_prd_issue_company_no", "生产领料单号已存在"),
            Map.entry("uk_prd_issue_company_book_no", "生产领料单号已存在"),
            Map.entry("uk_prd_completion_company_no", "生产完工单号已存在"),
            Map.entry("uk_prd_completion_company_book_no", "生产完工单号已存在"),
            Map.entry("uk_prd_return_company_no", "生产退料单号已存在"),
            Map.entry("uk_prd_return_company_book_no", "生产退料单号已存在"),
            Map.entry("uk_prd_completion_reversal_company_book_no", "生产完工冲销单号已存在"),
            Map.entry("uk_wf_approval_config_business", "审批配置已存在")
    );

    @Value("${erp.error.expose-unexpected-message:false}")
    private boolean exposeUnexpectedMessage;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("请求参数错误");
        return new ApiResponse<>("400", message, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleIllegalArgument(IllegalArgumentException ex) {
        return new ApiResponse<>("400", messageOrDefault(ex.getMessage(), "请求参数错误"), null);
    }

    @ExceptionHandler(BusinessConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<String> handleBusinessConflict(BusinessConflictException ex) {
        return new ApiResponse<>("409", messageOrDefault(ex.getMessage(), "业务冲突"), null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleDuplicateKey(DuplicateKeyException ex) {
        return new ApiResponse<>("400", resolveDuplicateMessage(ex), null);
    }

    @ExceptionHandler(LockedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<String> handleLockedAuthentication(LockedException ex) {
        return new ApiResponse<>("429", messageOrDefault(ex.getMessage(), "登录失败次数过多，请稍后重试"), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<String> handleAuthentication(AuthenticationException ex) {
        return new ApiResponse<>("401", "用户名或密码错误", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<String> handleAccessDenied(AccessDeniedException ex) {
        return new ApiResponse<>("403", "权限不足", null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<String> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return new ApiResponse<>("429", messageOrDefault(ex.getMessage(), "请求过于频繁，请稍后再试"), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleUnexpected(Exception ex) {
        log.error("Unhandled server exception", ex);
        String message = exposeUnexpectedMessage ? messageOrDefault(ex.getMessage(), "服务器内部错误") : "服务器内部错误";
        return new ApiResponse<>("500", message, null);
    }

    private String messageOrDefault(String message, String defaultMessage) {
        return StringUtils.hasText(message) ? message : defaultMessage;
    }

    private String resolveDuplicateMessage(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            String message = current.getMessage();
            if (message != null) {
                String resolved = resolveDuplicateMessage(message);
                if (!DUPLICATE_KEY_FALLBACK_MESSAGE.equals(resolved)) {
                    return resolved;
                }
            }
            current = current.getCause();
            depth++;
        }
        return DUPLICATE_KEY_FALLBACK_MESSAGE;
    }

    private String resolveDuplicateMessage(String message) {
        return DUPLICATE_KEY_MESSAGES.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DUPLICATE_KEY_FALLBACK_MESSAGE);
    }
}
