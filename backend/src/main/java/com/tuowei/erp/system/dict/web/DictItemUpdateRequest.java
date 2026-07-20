package com.tuowei.erp.system.dict.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictItemUpdateRequest(
        @NotBlank @Size(max = 128) String itemLabel,
        Integer sortNo,
        @Size(max = 512) String remark
) {
}
