package com.tuowei.erp.system.dict.web;

public record DictTypeResponse(
        Long id,
        String dictType,
        String dictName,
        String status,
        String remark
) {
}
