package com.tuowei.erp.system.dept.web;

import java.util.List;

public record DeptResponse(
        Long id,
        Long parentId,
        String deptCode,
        String deptName,
        Long leaderUserId,
        Integer sortNo,
        String status,
        String remark,
        List<DeptResponse> children
) {
}
