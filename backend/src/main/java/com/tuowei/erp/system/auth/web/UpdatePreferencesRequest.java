package com.tuowei.erp.system.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePreferencesRequest(
        @NotBlank @Size(max = 16) String locale,
        @NotBlank @Size(max = 64) String timeZone
) {
}
