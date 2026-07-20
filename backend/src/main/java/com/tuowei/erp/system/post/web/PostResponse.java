package com.tuowei.erp.system.post.web;

public record PostResponse(
        Long id,
        Long deptId,
        String postCode,
        String postName,
        String status,
        String remark
) {
}
