package com.tuowei.erp.finance.currency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.mapper.AccountBookCurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.CurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.ExchangeRateMapper;
import com.tuowei.erp.finance.currency.model.AccountBookCurrencyEntity;
import com.tuowei.erp.finance.currency.model.CurrencyEntity;
import com.tuowei.erp.finance.currency.model.ExchangeRateEntity;
import com.tuowei.erp.finance.currency.web.BaseCurrencyRequest;
import com.tuowei.erp.finance.currency.web.CurrencyCreateRequest;
import com.tuowei.erp.finance.currency.web.CurrencyResponse;
import com.tuowei.erp.finance.currency.web.ExchangeRateRequest;
import com.tuowei.erp.finance.currency.web.ExchangeRateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Objects;

/** Write side of currency/exchange-rate administration; reuses the query service for base-currency lookups and response mapping. */
@Service
public class CurrencyAdminCommandService {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private final CurrencyMapper currencies;
    private final ExchangeRateMapper rates;
    private final AuditMetadataFactory audit;
    private final AccountBookCurrencyMapper accountBookCurrencies;
    private final CurrencyAdminQueryService queryService;
    private final BaseCurrencyDataGuard baseCurrencyDataGuard;

    public CurrencyAdminCommandService(
            CurrencyMapper currencies,
            ExchangeRateMapper rates,
            AuditMetadataFactory audit,
            AccountBookCurrencyMapper accountBookCurrencies,
            CurrencyAdminQueryService queryService,
            BaseCurrencyDataGuard baseCurrencyDataGuard
    ) {
        this.currencies = currencies;
        this.rates = rates;
        this.audit = audit;
        this.accountBookCurrencies = accountBookCurrencies;
        this.queryService = queryService;
        this.baseCurrencyDataGuard = baseCurrencyDataGuard;
    }
    @Transactional
    public CurrencyResponse createCurrency(CurrencyCreateRequest request) {
        String code = queryService.normalizeCode(request.currencyCode());
        String name = requireText(request.currencyName(), "币种名称不能为空");
        Integer decimalPlaces = request.decimalPlaces() == null ? 2 : request.decimalPlaces();
        if (decimalPlaces < 0 || decimalPlaces > 6) {
            throw new IllegalArgumentException("小数位数必须在0到6之间");
        }
        CurrencyEntity existing = currencies.selectOne(new LambdaQueryWrapper<CurrencyEntity>()
                .eq(CurrencyEntity::getCurrencyCode, code));
        if (existing != null && (existing.getDeletedFlag() == null || existing.getDeletedFlag() == 0)) {
            throw new IllegalArgumentException("币种编码已存在");
        }

        CurrencyEntity entity = existing == null ? new CurrencyEntity() : existing;
        entity.setCurrencyCode(code);
        entity.setCurrencyName(name);
        entity.setCurrencySymbol(trimToNull(request.currencySymbol()));
        entity.setDecimalPlaces(decimalPlaces);
        entity.setStatus(ENABLED);
        entity.setDeletedFlag(0);
        if (existing == null) {
            currencies.insert(entity);
        } else {
            OptimisticLockGuard.requireUpdated(currencies.updateById(entity), "币种已被其他操作修改，请刷新后重试");
        }
        return queryService.toCurrencyResponse(entity);
    }

    @Transactional
    public CurrencyResponse enableCurrency(Long id) {
        return toggleCurrency(id, ENABLED);
    }

    @Transactional
    public CurrencyResponse disableCurrency(Long id) {
        return toggleCurrency(id, DISABLED);
    }
    @Transactional
    public String setBaseCurrency(BaseCurrencyRequest request) {
        String code = queryService.normalizeCode(request.currencyCode());
        requireEnabledCurrency(code);
        if (accountBookCurrencies == null) {
            throw new IllegalStateException("当前部署不支持按账套设置本位币");
        }
        AuditMetadata metadata = audit.current();
        AccountBookCurrencyEntity entity = accountBookCurrencies.selectOne(new LambdaQueryWrapper<AccountBookCurrencyEntity>()
                .eq(AccountBookCurrencyEntity::getCompanyId, metadata.companyId())
                .eq(AccountBookCurrencyEntity::getAccountBookId, metadata.accountBookId())
                .last("LIMIT 1"));
        if (!code.equals(queryService.baseCurrency())) {
            baseCurrencyDataGuard.requireNoCurrencySensitiveData(metadata.companyId(), metadata.accountBookId());
        }
        if (entity == null) {
            entity = new AccountBookCurrencyEntity();
            entity.setCompanyId(metadata.companyId());
            entity.setAccountBookId(metadata.accountBookId());
            entity.setCreatedBy(metadata.userId());
            entity.setCreatedTime(metadata.now());
            entity.setVersion(0);
            entity.setDeletedFlag(0);
            entity.setStatus(ENABLED);
            entity.setCurrencyCode(code);
            entity.setUpdatedBy(metadata.userId());
            entity.setUpdatedTime(metadata.now());
            accountBookCurrencies.insert(entity);
        } else {
            entity.setCurrencyCode(code);
            entity.setStatus(ENABLED);
            entity.setDeletedFlag(0);
            entity.setUpdatedBy(metadata.userId());
            entity.setUpdatedTime(metadata.now());
            OptimisticLockGuard.requireUpdated(accountBookCurrencies.updateById(entity), "本位币已被其他操作修改，请刷新后重试");
        }
        return code;
    }
    @Transactional
    public ExchangeRateResponse create(ExchangeRateRequest request) {
        String from = queryService.normalizeCode(request.fromCurrencyCode());
        String to = queryService.normalizeCode(request.toCurrencyCode());
        if (from.equals(to)) {
            throw new IllegalArgumentException("源币种和目标币种不能相同");
        }
        if (request.rate() == null || request.rate().signum() <= 0) {
            throw new IllegalArgumentException("汇率必须大于0");
        }
        LocalDate effectiveFrom = Objects.requireNonNull(request.effectiveFrom(), "生效日期不能为空");
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        }
        requireEnabledCurrency(from);
        requireEnabledCurrency(to);

        AuditMetadata metadata = audit.current();
        LambdaQueryWrapper<ExchangeRateEntity> query = new LambdaQueryWrapper<ExchangeRateEntity>()
                .eq(ExchangeRateEntity::getCompanyId, metadata.companyId())
                .eq(ExchangeRateEntity::getAccountBookId, metadata.accountBookId())
                .eq(ExchangeRateEntity::getFromCurrencyCode, from)
                .eq(ExchangeRateEntity::getToCurrencyCode, to)
                .eq(ExchangeRateEntity::getStatus, ENABLED);
        for (ExchangeRateEntity existing : rates.selectList(query)) {
            if (rangesOverlap(existing.getEffectiveFrom(), existing.getEffectiveTo(), effectiveFrom, request.effectiveTo())) {
                throw new IllegalArgumentException("汇率日期范围重叠");
            }
        }

        ExchangeRateEntity entity = new ExchangeRateEntity();
        entity.setCompanyId(metadata.companyId());
        entity.setAccountBookId(metadata.accountBookId());
        entity.setFromCurrencyCode(from);
        entity.setToCurrencyCode(to);
        entity.setRate(request.rate());
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(request.effectiveTo());
        entity.setStatus(ENABLED);
        rates.insert(entity);
        return queryService.toRateResponse(entity);
    }

    public ExchangeRateResponse createRate(ExchangeRateRequest request) {
        return create(request);
    }
    @Transactional
    public ExchangeRateResponse disableRate(Long id) {
        AuditMetadata metadata = audit.current();
        ExchangeRateEntity entity = rates.selectOne(new LambdaQueryWrapper<ExchangeRateEntity>()
                .eq(ExchangeRateEntity::getId, id)
                .eq(ExchangeRateEntity::getCompanyId, metadata.companyId())
                .eq(ExchangeRateEntity::getAccountBookId, metadata.accountBookId()));
        if (entity == null) {
            throw new IllegalArgumentException("汇率不存在");
        }
        if (DISABLED.equals(entity.getStatus())) {
            return queryService.toRateResponse(entity);
        }
        entity.setStatus(DISABLED);
        OptimisticLockGuard.requireUpdated(rates.updateById(entity), "汇率已被其他操作修改，请刷新后重试");
        return queryService.toRateResponse(entity);
    }

    private CurrencyResponse toggleCurrency(Long id, String status) {
        CurrencyEntity entity = currencies.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("币种不存在");
        }
        if (DISABLED.equals(status) && entity.getCurrencyCode().equalsIgnoreCase(queryService.baseCurrency())) {
            throw new IllegalArgumentException("当前本位币不能停用");
        }
        entity.setStatus(status);
        OptimisticLockGuard.requireUpdated(currencies.updateById(entity), "币种已被其他操作修改，请刷新后重试");
        return queryService.toCurrencyResponse(entity);
    }

    private void requireEnabledCurrency(String code) {
        Long count = currencies.selectCount(new LambdaQueryWrapper<CurrencyEntity>()
                .eq(CurrencyEntity::getCurrencyCode, code)
                .eq(CurrencyEntity::getStatus, ENABLED)
                .eq(CurrencyEntity::getDeletedFlag, 0));
        if (count == null || count == 0) {
            throw new IllegalArgumentException("币种不存在或未启用");
        }
    }
    private boolean rangesOverlap(LocalDate existingFrom, LocalDate existingTo, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate existingEnd = existingTo == null ? LocalDate.MAX : existingTo;
        LocalDate requestedEnd = requestedTo == null ? LocalDate.MAX : requestedTo;
        return !requestedFrom.isAfter(existingEnd) && !requestedEnd.isBefore(existingFrom);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
