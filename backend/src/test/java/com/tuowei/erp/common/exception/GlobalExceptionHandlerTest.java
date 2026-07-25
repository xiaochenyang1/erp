package com.tuowei.erp.common.exception;

import com.tuowei.erp.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @CsvSource({
            "uk_fin_payable_payable_no, 应付单号已存在",
            "uk_fin_receivable_receivable_no, 应收单号已存在",
            "uk_fin_voucher_voucher_no, 凭证号已存在",
            "uk_inv_transfer_transfer_no, 库存调拨单号已存在",
            "uk_sys_sequence_rule_company_book_biz_type, 业务类型已存在",
            "uk_pur_order_company_book_order_no, 采购订单号已存在",
            "uk_pur_order_company_book_source_inquiry, 询价单已转换为采购订单",
            "uk_pur_inquiry_quote_line_company_book_quote_line, 报价明细不能重复提交询价行",
            "uk_inv_alert_rule_company_book_product_warehouse, 库存预警规则已存在",
            "uk_inv_balance_company_book_warehouse_product, 库存余额已存在",
            "uk_inv_txn_company_book_biz_line_direction_lot_key, 库存流水已存在",
            "uk_inv_reservation_company_book_source_line, 库存预占已存在",
            "uk_fin_payable_company_book_source, 来源应付单已存在",
            "uk_fin_expense_company_book_no, 费用单号已存在",
            "uk_prd_order_company_book_order_no, 生产工单号已存在",
            "uk_sys_role_company_book_role_code, 角色编码已存在",
            "uk_sys_dept_company_book_dept_code, 部门编码已存在",
            "uk_sys_post_company_book_post_code, 岗位编码已存在",
            "uk_md_product_company_book_product_code, 商品编码已存在",
            "uk_md_customer_company_book_customer_code, 客户编码已存在",
            "uk_md_supplier_company_book_supplier_code, 供应商编码已存在",
            "uk_md_warehouse_company_book_warehouse_code, 仓库编码已存在",
            "uk_prd_work_center_company_book_code, 工作中心编码已存在",
            "uk_fin_account_subject_company_book_code, 科目编码已存在"
    })
    void duplicateKeyUsesBusinessMessageForKnownConstraints(String constraintName, String expectedMessage) {
        ApiResponse<String> response = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry 'NO-001' for key '" + constraintName + "'"));

        assertThat(response.code()).isEqualTo("400");
        assertThat(response.message()).isEqualTo(expectedMessage);
        assertThat(response.data()).isNull();
    }

    @Test
    void duplicateKeyFallsBackWhenConstraintIsUnknown() {
        ApiResponse<String> response = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '1' for key 'uk_internal_unknown'"));

        assertThat(response.message()).isEqualTo("数据唯一约束冲突");
    }

    @Test
    void duplicateKeyFallsBackWhenMessageIsNull() {
        ApiResponse<String> response = handler.handleDuplicateKey(new DuplicateKeyException(null));

        assertThat(response.message()).isEqualTo("数据唯一约束冲突");
    }

    @Test
    void duplicateKeyUsesBusinessMessageFromNestedCause() {
        ApiResponse<String> response = handler.handleDuplicateKey(
                new DuplicateKeyException("duplicate key", new RuntimeException(
                        "Duplicate entry 'V-001' for key 'uk_fin_voucher_company_book_voucher_no'")));

        assertThat(response.code()).isEqualTo("400");
        assertThat(response.message()).isEqualTo("凭证号已存在");
        assertThat(response.data()).isNull();
    }

    @Test
    void duplicateKeyResolutionAcceptsThrowableForNestedJdbcMessages() {
        Method[] methods = GlobalExceptionHandler.class.getDeclaredMethods();

        assertThat(methods)
                .filteredOn(method -> method.getName().equals("resolveDuplicateMessage"))
                .anySatisfy(method -> assertThat(method.getParameterTypes()).containsExactly(Throwable.class));
    }

    @Test
    void illegalArgumentFallsBackWhenMessageIsBlank() {
        ApiResponse<String> response = handler.handleIllegalArgument(new IllegalArgumentException(" "));

        assertThat(response.code()).isEqualTo("400");
        assertThat(response.message()).isEqualTo("请求参数错误");
    }

    @Test
    void workflowEscalationErrorsUseRequestLocale() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        ReflectionTestUtils.setField(handler, "messageSource", messageSource);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            ApiResponse<String> response = handler.handleIllegalArgument(new IllegalArgumentException("审批任务尚未超时"));
            assertThat(response.message()).isEqualTo("The approval task is not overdue");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void accessDeniedDoesNotExposeInternalSecurityMessage() {
        ApiResponse<String> response = handler.handleAccessDenied(
                new AccessDeniedException("Access is denied by hasAuthority('system:user:delete')"));

        assertThat(response.code()).isEqualTo("403");
        assertThat(response.message()).isEqualTo("权限不足");
    }

    @Test
    void unexpectedFallsBackWhenExposedMessageIsBlank() {
        ReflectionTestUtils.setField(handler, "exposeUnexpectedMessage", true);

        ApiResponse<String> response = handler.handleUnexpected(new RuntimeException(" "));

        assertThat(response.code()).isEqualTo("500");
        assertThat(response.message()).isEqualTo("服务器内部错误");
    }

    @Test
    void lockedAuthenticationUsesRateLimitResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LockedAuthenticationController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/test/locked-login"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("429"))
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请15分钟后重试"));
    }

    @RestController
    private static class LockedAuthenticationController {

        @GetMapping("/test/locked-login")
        ApiResponse<String> lockedLogin() {
            throw new LockedException("登录失败次数过多，请15分钟后重试");
        }
    }
}
