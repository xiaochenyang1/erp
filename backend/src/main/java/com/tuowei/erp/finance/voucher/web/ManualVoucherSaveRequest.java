package com.tuowei.erp.finance.voucher.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 手工凭证保存请求（新增/编辑草稿共用）。借贷平衡、金额、行数由服务层校验。
 */
public record ManualVoucherSaveRequest(
        @NotNull(message = "业务日期不能为空") LocalDate bizDate,
        String remark,
        @NotEmpty(message = "凭证分录不能为空")
        @Valid
        List<ManualVoucherLineRequest> lines
) {
}
