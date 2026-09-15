package com.tuowei.erp.finance.currency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.currency.mapper.CurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.ExchangeRateMapper;
import com.tuowei.erp.finance.currency.model.CurrencyEntity;
import com.tuowei.erp.finance.currency.model.ExchangeRateEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class SettlementCurrencyService {

    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.000000000001");

    private final BaseCurrencyService baseCurrencyService;
    private final CurrencyMapper currencyMapper;
    private final ExchangeRateMapper exchangeRateMapper;

    public SettlementCurrencyService(
            BaseCurrencyService baseCurrencyService,
            CurrencyMapper currencyMapper,
            ExchangeRateMapper exchangeRateMapper
    ) {
        this.baseCurrencyService = baseCurrencyService;
        this.currencyMapper = currencyMapper;
        this.exchangeRateMapper = exchangeRateMapper;
    }

    @Transactional(readOnly = true)
    public Resolution resolve(String requestedCurrency, BigDecimal requestedRate, LocalDate businessDate, AuditMetadata audit) {
        if (businessDate == null) {
            throw new IllegalArgumentException("业务日期不能为空");
        }

        String baseCurrency = normalizeCode(baseCurrencyService.current(audit));
        String currencyCode = requestedCurrency == null || requestedCurrency.isBlank()
                ? baseCurrency
                : normalizeCode(requestedCurrency);
        requireEnabledCurrency(currencyCode);

        if (currencyCode.equals(baseCurrency)) {
            if (requestedRate != null && requestedRate.compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("本位币汇率必须为1");
            }
            return new Resolution(currencyCode, BigDecimal.ONE);
        }

        List<ExchangeRateEntity> matches = exchangeRateMapper.selectList(
                new LambdaQueryWrapper<ExchangeRateEntity>()
                        .eq(ExchangeRateEntity::getCompanyId, audit.companyId())
                        .eq(ExchangeRateEntity::getAccountBookId, audit.accountBookId())
                        .eq(ExchangeRateEntity::getFromCurrencyCode, currencyCode)
                        .eq(ExchangeRateEntity::getToCurrencyCode, baseCurrency)
                        .eq(ExchangeRateEntity::getStatus, "ENABLED")
                        .le(ExchangeRateEntity::getEffectiveFrom, businessDate)
                        .and(wrapper -> wrapper.isNull(ExchangeRateEntity::getEffectiveTo)
                                .or()
                                .ge(ExchangeRateEntity::getEffectiveTo, businessDate))
                        .orderByDesc(ExchangeRateEntity::getEffectiveFrom)
        );
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("未找到业务日期对应的有效汇率");
        }

        BigDecimal configuredRate = matches.get(0).getRate();
        if (configuredRate == null || configuredRate.signum() <= 0) {
            throw new IllegalArgumentException("系统配置的汇率无效");
        }
        if (requestedRate != null) {
            if (requestedRate.signum() <= 0) {
                throw new IllegalArgumentException("汇率必须大于0");
            }
            if (requestedRate.subtract(configuredRate).abs().compareTo(RATE_TOLERANCE) > 0) {
                throw new IllegalArgumentException("提交汇率与系统有效汇率不一致，请刷新后重试");
            }
        }
        return new Resolution(currencyCode, configuredRate);
    }

    public BigDecimal toBaseAmount(BigDecimal amount, BigDecimal exchangeRate) {
        return amount.multiply(exchangeRate).setScale(6, RoundingMode.HALF_UP);
    }

    private void requireEnabledCurrency(String currencyCode) {
        Long count = currencyMapper.selectCount(new LambdaQueryWrapper<CurrencyEntity>()
                .eq(CurrencyEntity::getCurrencyCode, currencyCode)
                .eq(CurrencyEntity::getStatus, "ENABLED")
                .eq(CurrencyEntity::getDeletedFlag, 0));
        if (count == null || count == 0) {
            throw new IllegalArgumentException("币种不存在或未启用");
        }
    }

    private String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("币种编码必须为3位大写字母");
        }
        return code;
    }

    public record Resolution(String currencyCode, BigDecimal exchangeRate) {
    }
}
