package com.tuowei.erp.system.dict.web;

public record DictItemResponse(
        Long id,
        Long typeId,
        String dictType,
        String itemLabel,
        String itemValue,
        Integer sortNo,
        String status,
        String remark
) {
}
