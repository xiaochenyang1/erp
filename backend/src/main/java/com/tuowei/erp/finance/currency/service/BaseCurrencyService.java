package com.tuowei.erp.finance.currency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.currency.mapper.AccountBookCurrencyMapper;
import com.tuowei.erp.finance.currency.model.AccountBookCurrencyEntity;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class BaseCurrencyService {
    private static final String CONFIG_CODE = "finance.base.currency";
    private final SystemConfigMapper systemConfigMapper;
    private final AccountBookCurrencyMapper accountBookCurrencyMapper;

    @Autowired
    public BaseCurrencyService(
            SystemConfigMapper systemConfigMapper,
            AccountBookCurrencyMapper accountBookCurrencyMapper
    ) {
        this.systemConfigMapper = systemConfigMapper;
        this.accountBookCurrencyMapper = accountBookCurrencyMapper;
    }

    /** Backward-compatible constructor used by older unit tests and integrations. */
    public BaseCurrencyService(SystemConfigMapper systemConfigMapper) {
        this(systemConfigMapper, null);
    }

    @Transactional(readOnly = true)
    public String current() {
        return current(null, null);
    }

    /**
     * Returns the enabled base currency for the supplied company/account book.
     *
     * <p>Older deployments only have the global {@code sys_config} value. They
     * continue to work through the fallback below until a scoped value is
     * configured.</p>
     */
    @Transactional(readOnly = true)
    public String current(Long companyId, Long accountBookId) {
        if (companyId != null && accountBookId != null && accountBookCurrencyMapper != null) {
            AccountBookCurrencyEntity scoped = accountBookCurrencyMapper.selectOne(
                    new LambdaQueryWrapper<AccountBookCurrencyEntity>()
                            .eq(AccountBookCurrencyEntity::getCompanyId, companyId)
                            .eq(AccountBookCurrencyEntity::getAccountBookId, accountBookId)
                            .eq(AccountBookCurrencyEntity::getStatus, "ENABLED")
                            .eq(AccountBookCurrencyEntity::getDeletedFlag, 0)
                            .last("LIMIT 1")
            );
            String scopedCurrency = normalize(scoped == null ? null : scoped.getCurrencyCode());
            if (scopedCurrency != null) {
                return scopedCurrency;
            }
        }
        return currentGlobal();
    }

    public String current(AuditMetadata audit) {
        return audit == null ? current() : current(audit.companyId(), audit.accountBookId());
    }

    private String currentGlobal() {
        SystemConfigEntity config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getConfigCode, CONFIG_CODE)
                .in(SystemConfigEntity::getStatus, "ENABLED", "ACTIVE")
                .eq(SystemConfigEntity::getDeletedFlag, 0)
                .last("LIMIT 1"));
        String configured = normalize(config == null ? null : config.getConfigValue());
        return configured == null ? "CNY" : configured;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }
}
