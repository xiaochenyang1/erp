package com.tuowei.erp.system.post.web;

import jakarta.validation.constraints.NotBlank;

public record PostUpdateRequest(
        @NotBlank(message = "postName不能为空") String postName,
        String remark
) {
}
