package com.tuowei.erp.finance.currency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseCurrencyServiceTest {
    @Test
    void returnsNormalizedConfiguredCurrency() {
        SystemConfigEntity config = new SystemConfigEntity();
        config.setConfigValue(" usd ");
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        assertEquals("USD", new BaseCurrencyService(mapper).current());
    }

    @Test
    void fallsBackToCnyWhenConfigMissingOrBlank() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertEquals("CNY", new BaseCurrencyService(mapper).current());
        SystemConfigEntity blank = new SystemConfigEntity();
        blank.setConfigValue("  ");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(blank);
        assertEquals("CNY", new BaseCurrencyService(mapper).current());
    }
}
