package com.tuowei.erp.system.dict.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictTypeCreateRequest(
        @NotBlank @Size(max = 64) String dictType,
        @NotBlank @Size(max = 128) String dictName,
        @Size(max = 512) String remark
) {
}
