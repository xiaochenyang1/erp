package com.tuowei.erp.finance.currency.service;

import com.tuowei.erp.finance.currency.web.BaseCurrencyRequest;
import com.tuowei.erp.finance.currency.web.CurrencyCreateRequest;
import com.tuowei.erp.finance.currency.web.CurrencyResponse;
import com.tuowei.erp.finance.currency.web.ExchangeRateRequest;
import com.tuowei.erp.finance.currency.web.ExchangeRateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade delegating currency/exchange-rate reads to the query service and writes to the command service. */
@Service
public class CurrencyAdminService {

    private final CurrencyAdminQueryService queryService;
    private final CurrencyAdminCommandService commandService;

    public CurrencyAdminService(
            CurrencyAdminQueryService queryService,
            CurrencyAdminCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        return queryService.baseCurrency();
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> currencies() {
        return queryService.currencies();
    }

    @Transactional
    public CurrencyResponse createCurrency(CurrencyCreateRequest request) {
        return commandService.createCurrency(request);
    }

    @Transactional
    public CurrencyResponse enableCurrency(Long id) {
        return commandService.enableCurrency(id);
    }

    @Transactional
    public CurrencyResponse disableCurrency(Long id) {
        return commandService.disableCurrency(id);
    }
    @Transactional
    public String setBaseCurrency(BaseCurrencyRequest request) {
        return commandService.setBaseCurrency(request);
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> rates(String from, String to) {
        return queryService.rates(from, to);
    }

    @Transactional
    public ExchangeRateResponse create(ExchangeRateRequest request) {
        return commandService.create(request);
    }

    public ExchangeRateResponse createRate(ExchangeRateRequest request) {
        return commandService.createRate(request);
    }

    @Transactional
    public ExchangeRateResponse disableRate(Long id) {
        return commandService.disableRate(id);
    }
}
