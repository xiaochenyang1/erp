package com.tuowei.erp.finance.fx;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.fx.mapper.FxRevaluationMapper;
import com.tuowei.erp.finance.fx.model.FxRevaluationEntity;
import com.tuowei.erp.finance.fx.service.FxRevaluationService;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinanceVoucherPostingService;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxRevaluationServiceTest {

    private static final LocalDate PERIOD_END = LocalDate.of(2026, 6, 30);
    private static final LocalDate REVERSAL_DATE = LocalDate.of(2026, 7, 1);

    @BeforeAll
    static void initTableInfo() {
        init(ReceivableEntity.class);
        init(PayableEntity.class);
        init(FxRevaluationEntity.class);
        init(AccountPeriodEntity.class);
    }

    @Test
    void revaluesOpenForeignReceivableAndReversesItNextPeriod() {
        Harness harness = harness();
        when(harness.receivables.selectList(any())).thenReturn(List.of(usdReceivable()));
        when(harness.payables.selectList(any())).thenReturn(List.of());
        when(harness.revaluations.selectOne(any())).thenReturn(null);
        when(harness.revaluations.insert(any(FxRevaluationEntity.class))).thenReturn(1);
        when(harness.rates.resolve(eq("USD"), isNull(), eq(PERIOD_END), any()))
                .thenReturn(new SettlementCurrencyService.Resolution("USD", new BigDecimal("7.2")));

        var response = harness.service.revalue(11L);

        assertThat(response.alreadyPosted()).isFalse();
        assertThat(response.openOriginalTotal()).isEqualByComparingTo("100.00");
        assertThat(response.arAdjustment()).isEqualByComparingTo("20.00");
        assertThat(response.apAdjustment()).isEqualByComparingTo("0.00");
        assertThat(response.revaluationDate()).isEqualTo(PERIOD_END);
        assertThat(response.reversalDate()).isEqualTo(REVERSAL_DATE);
        verify(harness.vouchers).recordTwoSidedVoucher(
                eq("FX_REVALUATION_AR"), any(), any(), eq(PERIOD_END), eq(new BigDecimal("20.00")),
                any(), eq("1122"), eq("6061"), any(), any());
        verify(harness.vouchers).recordTwoSidedVoucher(
                eq("FX_REVALUATION_AR_REVERSAL"), any(), any(), eq(REVERSAL_DATE), eq(new BigDecimal("20.00")),
                any(), eq("6061"), eq("1122"), any(), any());
        verify(harness.vouchers, never()).recordTwoSidedVoucher(
                eq("FX_REVALUATION_AP"), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(harness.guard).requireOpen(PERIOD_END, "期末调汇");
        verify(harness.guard).requireOpen(REVERSAL_DATE, "期末调汇红冲");
    }

    @Test
    void secondCallWithTheSameOpenBalanceDoesNotPostAgain() {
        Harness harness = harness();
        when(harness.receivables.selectList(any())).thenReturn(List.of(usdReceivable()));
        when(harness.payables.selectList(any())).thenReturn(List.of());
        FxRevaluationEntity posted = postedRun(new BigDecimal("100.00"), new BigDecimal("20.00"));
        when(harness.revaluations.selectOne(any())).thenReturn(posted);
        when(harness.rates.resolve(eq("USD"), isNull(), eq(PERIOD_END), any()))
                .thenReturn(new SettlementCurrencyService.Resolution("USD", new BigDecimal("7.2")));

        var response = harness.service.revalue(11L);

        assertThat(response.alreadyPosted()).isTrue();
        assertThat(response.id()).isEqualTo(posted.getId());
        verify(harness.revaluations, never()).insert(any(FxRevaluationEntity.class));
        verify(harness.vouchers, never()).recordTwoSidedVoucher(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void changedOpenBalanceIsRejectedUntilThePostedRunIsCancelled() {
        Harness harness = harness();
        when(harness.receivables.selectList(any())).thenReturn(List.of(usdReceivable()));
        when(harness.payables.selectList(any())).thenReturn(List.of());
        when(harness.revaluations.selectOne(any())).thenReturn(postedRun(new BigDecimal("50.00"), new BigDecimal("10.00")));
        when(harness.rates.resolve(eq("USD"), isNull(), eq(PERIOD_END), any()))
                .thenReturn(new SettlementCurrencyService.Resolution("USD", new BigDecimal("7.2")));

        assertThatThrownBy(() -> harness.service.revalue(11L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("撤销");
        verify(harness.vouchers, times(0)).recordTwoSidedVoucher(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Harness harness() {
        FxRevaluationMapper revaluations = mock(FxRevaluationMapper.class);
        AccountPeriodMapper periods = mock(AccountPeriodMapper.class);
        ReceivableMapper receivables = mock(ReceivableMapper.class);
        PayableMapper payables = mock(PayableMapper.class);
        SettlementCurrencyService rates = mock(SettlementCurrencyService.class);
        BaseCurrencyService baseCurrency = mock(BaseCurrencyService.class);
        AccountPeriodGuard guard = mock(AccountPeriodGuard.class);
        FinanceVoucherPostingService vouchers = mock(FinanceVoucherPostingService.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        when(auditFactory.current()).thenReturn(new AuditMetadata(7L, 9L, 2L, LocalDateTime.parse("2026-06-30T18:00:00")));
        when(periods.selectById(11L)).thenReturn(period());
        when(baseCurrency.current(any())).thenReturn("CNY");
        FxRevaluationService service = new FxRevaluationService(
                revaluations, periods, receivables, payables, rates, baseCurrency, guard, vouchers, auditFactory);
        return new Harness(service, revaluations, receivables, payables, rates, guard, vouchers);
    }

    private static AccountPeriodEntity period() {
        AccountPeriodEntity period = new AccountPeriodEntity();
        period.setId(11L);
        period.setCompanyId(9L);
        period.setAccountBookId(2L);
        period.setPeriodMonth("2026-06");
        period.setEndDate(PERIOD_END);
        period.setStatus("OPEN");
        return period;
    }

    private static ReceivableEntity usdReceivable() {
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setCurrencyCode("USD");
        receivable.setDirection("INCREASE");
        receivable.setStatus("UNSETTLED");
        receivable.setOriginalAmount(new BigDecimal("100.00"));
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setExchangeRate(new BigDecimal("7"));
        receivable.setBaseOriginalAmount(new BigDecimal("700.00"));
        receivable.setBaseSettledAmount(BigDecimal.ZERO);
        return receivable;
    }

    private static FxRevaluationEntity postedRun(BigDecimal fingerprint, BigDecimal arAdjustment) {
        FxRevaluationEntity run = new FxRevaluationEntity();
        run.setId(8801L);
        run.setStatus("POSTED");
        run.setOpenOriginalTotal(fingerprint);
        run.setArAdjustment(arAdjustment);
        run.setApAdjustment(BigDecimal.ZERO);
        run.setRevaluationDate(PERIOD_END);
        run.setReversalDate(REVERSAL_DATE);
        run.setGeneration(0L);
        return run;
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
    }

    private record Harness(
            FxRevaluationService service,
            FxRevaluationMapper revaluations,
            ReceivableMapper receivables,
            PayableMapper payables,
            SettlementCurrencyService rates,
            AccountPeriodGuard guard,
            FinanceVoucherPostingService vouchers
    ) {
    }
}
