package com.tuowei.erp.finance.currency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.mapper.CurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.ExchangeRateMapper;
import com.tuowei.erp.finance.currency.model.CurrencyEntity;
import com.tuowei.erp.finance.currency.model.ExchangeRateEntity;
import com.tuowei.erp.finance.currency.web.CurrencyResponse;
import com.tuowei.erp.finance.currency.web.ExchangeRateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/** Read side of currency/exchange-rate administration plus response mapping and code normalization shared with the command service. */
@Service
public class CurrencyAdminQueryService {

    private final CurrencyMapper currencies;
    private final ExchangeRateMapper rates;
    private final AuditMetadataFactory audit;
    private final BaseCurrencyService baseCurrencyService;

    public CurrencyAdminQueryService(
            CurrencyMapper currencies,
            ExchangeRateMapper rates,
            AuditMetadataFactory audit,
            BaseCurrencyService baseCurrencyService
    ) {
        this.currencies = currencies;
        this.rates = rates;
        this.audit = audit;
        this.baseCurrencyService = baseCurrencyService;
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        if (baseCurrencyService == null) {
            return "CNY";
        }
        AuditMetadata metadata = audit.current();
        return baseCurrencyService.current(metadata);
    }
    @Transactional(readOnly = true)
    public List<CurrencyResponse> currencies() {
        return currencies.selectList(new LambdaQueryWrapper<CurrencyEntity>()
                        .eq(CurrencyEntity::getDeletedFlag, 0)
                        .orderByAsc(CurrencyEntity::getCurrencyCode))
                .stream()
                .map(this::toCurrencyResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> rates(String from, String to) {
        AuditMetadata metadata = audit.current();
        String normalizedFrom = normalizeFilter(from);
        String normalizedTo = normalizeFilter(to);
        return rates.selectList(new LambdaQueryWrapper<ExchangeRateEntity>()
                        .eq(ExchangeRateEntity::getCompanyId, metadata.companyId())
                        .eq(ExchangeRateEntity::getAccountBookId, metadata.accountBookId())
                        .eq(normalizedFrom != null, ExchangeRateEntity::getFromCurrencyCode, normalizedFrom)
                        .eq(normalizedTo != null, ExchangeRateEntity::getToCurrencyCode, normalizedTo)
                        .orderByDesc(ExchangeRateEntity::getEffectiveFrom))
                .stream()
                .map(this::toRateResponse)
                .toList();
    }

    CurrencyResponse toCurrencyResponse(CurrencyEntity entity) {
        return new CurrencyResponse(entity.getId(), entity.getCurrencyCode(), entity.getCurrencyName(),
                entity.getCurrencySymbol(), entity.getDecimalPlaces(), entity.getStatus());
    }

    ExchangeRateResponse toRateResponse(ExchangeRateEntity entity) {
        return new ExchangeRateResponse(entity.getId(), entity.getFromCurrencyCode(), entity.getToCurrencyCode(),
                entity.getRate(), entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getStatus());
    }

    String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("币种编码必须为3位大写字母");
        }
        return code;
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? normalizeCode(value) : null;
    }
}
