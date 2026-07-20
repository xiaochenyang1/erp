package com.tuowei.erp.system.post.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostCreateRequest(
        @NotNull(message = "deptId不能为空") Long deptId,
        @NotBlank(message = "postCode不能为空") String postCode,
        @NotBlank(message = "postName不能为空") String postName,
        String remark
) {
}
