package com.tuowei.erp.system.dept.web;

import jakarta.validation.constraints.NotBlank;

public record DeptUpdateRequest(
        @NotBlank(message = "deptName不能为空") String deptName,
        Long leaderUserId,
        Integer sortNo,
        String remark
) {
}
