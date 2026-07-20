package com.tuowei.erp.system.dict.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictItemCreateRequest(
        @NotBlank @Size(max = 64) String dictType,
        @NotBlank @Size(max = 128) String itemLabel,
        @NotBlank @Size(max = 128) String itemValue,
        Integer sortNo,
        @Size(max = 512) String remark
) {
}
