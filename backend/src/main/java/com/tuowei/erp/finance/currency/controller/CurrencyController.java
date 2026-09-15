package com.tuowei.erp.finance.currency.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.currency.service.CurrencyAdminService;
import com.tuowei.erp.finance.currency.web.BaseCurrencyRequest;
import com.tuowei.erp.finance.currency.web.CurrencyCreateRequest;
import com.tuowei.erp.finance.currency.web.CurrencyResponse;
import com.tuowei.erp.finance.currency.web.ExchangeRateRequest;
import com.tuowei.erp.finance.currency.web.ExchangeRateResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/currencies")
public class CurrencyController {

    private final CurrencyAdminService service;

    public CurrencyController(CurrencyAdminService service) {
        this.service = service;
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_VIEW)
    @GetMapping
    public ApiResponse<?> currencies() {
        return ApiResponse.success(service.currencies());
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_MANAGE)
    @PostMapping
    public ApiResponse<CurrencyResponse> createCurrency(@Valid @RequestBody CurrencyCreateRequest request) {
        return ApiResponse.success(service.createCurrency(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_MANAGE)
    @PostMapping("/{id}/enable")
    public ApiResponse<CurrencyResponse> enableCurrency(@PathVariable Long id) {
        return ApiResponse.success(service.enableCurrency(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_MANAGE)
    @PostMapping("/{id}/disable")
    public ApiResponse<CurrencyResponse> disableCurrency(@PathVariable Long id) {
        return ApiResponse.success(service.disableCurrency(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_VIEW)
    @GetMapping("/base")
    public ApiResponse<String> base() {
        return ApiResponse.success(service.baseCurrency());
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_MANAGE)
    @PutMapping("/base")
    public ApiResponse<String> setBase(@Valid @RequestBody BaseCurrencyRequest request) {
        return ApiResponse.success(service.setBaseCurrency(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_VIEW)
    @GetMapping("/rates")
    public ApiResponse<?> rates(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ApiResponse.success(service.rates(from, to));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_MANAGE)
    @PostMapping("/rates")
    public ApiResponse<ExchangeRateResponse> createRate(@Valid @RequestBody ExchangeRateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CURRENCY_MANAGE)
    @PostMapping("/rates/{id}/disable")
    public ApiResponse<ExchangeRateResponse> disableRate(@PathVariable Long id) {
        return ApiResponse.success(service.disableRate(id));
    }
}
