package com.tuowei.erp.finance.fx;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.fx.mapper.FxRevaluationMapper;
import com.tuowei.erp.finance.fx.mapper.FxRevaluationBalanceMapper;
import com.tuowei.erp.finance.fx.service.FxRevaluationBasisService;
import com.tuowei.erp.finance.fx.service.FxRevaluationService;
import com.tuowei.erp.finance.fx.web.FxRevaluationResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.period.service.AccountPeriodCloseChecker;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationService;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseCheckResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import com.tuowei.erp.finance.posting.FinanceVoucherPostingService;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real source queries, FX migrations and run persistence; other close checks, rates and GL posting are isolated. */
class FxRevaluationWorkflowTest {
    private static final LocalDate END = LocalDate.of(2026, 6, 30);
    private static final AuditMetadata AUDIT = new AuditMetadata(7L, 9L, 2L, LocalDateTime.of(2026, 7, 20, 12, 0));
    private static final long PERIOD = 11L;

    private SqlSession session;
    private JdbcTemplate jdbc;
    private FxRevaluationService service;
    private AccountPeriodCloseChecker closeChecker;
    private FinanceVoucherPostingService vouchers;
    private AccountPeriodGuard guard;
    private BaseCurrencyService baseCurrency;
    private final Map<String, BigDecimal> closingRates = new HashMap<>();

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:fx_revaluation_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("fx-test", new JdbcTransactionFactory(), datasource));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig()).setSqlInjector(new DefaultSqlInjector()));
        for (Class<?> mapper : List.of(ReceivableMapper.class, PayableMapper.class, ReceiptMapper.class,
                PaymentMapper.class, ReceiptAllocationMapper.class, PaymentAllocationMapper.class,
                FxRevaluationMapper.class, AccountPeriodMapper.class, FxRevaluationBalanceMapper.class)) {
            configuration.addMapper(mapper);
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        for (Class<?> entity : List.of(ReceivableEntity.class, PayableEntity.class, ReceiptEntity.class,
                PaymentEntity.class, ReceiptAllocationEntity.class, PaymentAllocationEntity.class, AccountPeriodEntity.class)) {
            createTable(entity);
        }
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V166__fx_revaluation.sql"),
                new ClassPathResource("db/migration/V167__fx_revaluation_basis.sql")).execute(datasource);
        jdbc.update("insert into fin_account_period (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status)"
                        + " values (?, ?, ?, 2026, '2026-06', ?, ?, 'OPEN')",
                PERIOD, AUDIT.companyId(), AUDIT.accountBookId(), END.withDayOfMonth(1), END);
        AuditMetadataFactory audit = mock(AuditMetadataFactory.class);
        when(audit.current()).thenReturn(AUDIT);
        baseCurrency = mock(BaseCurrencyService.class);
        when(baseCurrency.current(AUDIT)).thenReturn("CNY");
        SettlementCurrencyService rates = mock(SettlementCurrencyService.class);
        closingRates.put("USD", new BigDecimal("7.2"));
        closingRates.put("EUR", new BigDecimal("8.5"));
        when(rates.resolve(any(), isNull(), eq(END), eq(AUDIT))).thenAnswer(invocation -> {
            String currency = invocation.getArgument(0);
            BigDecimal rate = closingRates.get(currency);
            if (rate == null) throw new IllegalArgumentException("未找到业务日期对应的有效汇率");
            return new SettlementCurrencyService.Resolution(currency, rate);
        });
        vouchers = mock(FinanceVoucherPostingService.class);
        guard = mock(AccountPeriodGuard.class);
        service = new FxRevaluationService(session.getMapper(FxRevaluationMapper.class), session.getMapper(AccountPeriodMapper.class),
                new FxRevaluationBasisService(session.getMapper(FxRevaluationBalanceMapper.class), rates, baseCurrency),
                guard, vouchers, audit);
        InventoryFinanceReconciliationService reconciliation = mock(InventoryFinanceReconciliationService.class);
        when(reconciliation.summary(PERIOD)).thenReturn(new InventoryFinanceReconciliationResponse(
                PERIOD, "2026-06", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true));
        closeChecker = new AccountPeriodCloseChecker(session.getMapper(AccountPeriodMapper.class), audit, reconciliation,
                new JdbcTemplate() {
                    @Override
                    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                        // Unrelated close checks only count other business rows; FX must go through the real service.
                        assertThat(requiredType).isEqualTo(Long.class);
                        return requiredType.cast(0L);
                    }
                }, service);
    }

    @AfterEach
    void closeDatabase() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void documentsDatedAfterThePeriodEndAreExcluded(String side) {
        item(side, 21L, "USD", "100", "7", "0", END.plusDays(1));

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("0");
        assertThat(adjustment(result, side)).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void currentlySettledItemsAreRestoredWhenSettlementOccurredAfterPeriodEnd(String side) {
        item(side, 21L, "USD", "100", "7", "100", END.minusDays(5));
        settlement(side, 31L, 21L, "100", "7", END.plusDays(1), "POSTED");

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("100");
        assertThat(adjustment(result, side)).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void onlySettlementsOnOrBeforeThePeriodEndReduceItsBalance(String side) {
        item(side, 21L, "USD", "100", "7", "80", END.minusDays(5));
        settlement(side, 31L, 21L, "30", "7", END, "POSTED");
        settlement(side, 32L, 21L, "50", "7", END.plusDays(1), "POSTED");

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("70");
        assertThat(adjustment(result, side)).isEqualByComparingTo("14");
    }

    @Test
    void changedClosingRateRequiresCancellationEvenWhenOriginalBalancesAreUnchanged() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        assertThat(revalue().arAdjustment()).isEqualByComparingTo("20");
        closingRates.put("USD", new BigDecimal("7.5"));

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class).hasMessageContaining("撤销");
        assertFxBlocked("汇率");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void itemsSettledOnThePeriodEndNeedNoRevaluationOrRate(String side) {
        item(side, 21L, "USD", "100", "7", "100", END.minusDays(1));
        settlement(side, 31L, 21L, "100", "7", END, "POSTED");
        closingRates.clear();

        assertThat(revalue().openOriginalTotal()).isEqualByComparingTo("0");
        assertThat(closeCheck().passed()).isTrue();
        verifyNoInteractions(vouchers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void importedOpeningSettlementsAreRetainedWithoutAllocationHistory(String side) {
        item(side, 21L, "USD", "100", "7", "50", END.minusMonths(1));
        settlement(side, 31L, 21L, "30", "7", END.plusDays(1), "POSTED");

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("80");
        assertThat(adjustment(result, side)).isEqualByComparingTo("16");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void cancelledSettlementsAreIgnoredBecauseTheyReverseOnTheirOriginalBusinessDate(String side) {
        item(side, 21L, "USD", "100", "7", "0", END.minusDays(1));
        settlement(side, 31L, 21L, "40", "7", END, "CANCELLED");
        settlement(side, 32L, 21L, "60", "7", END.plusDays(1), "CANCELLED");
        jdbc.update("update " + cashTable(side) + " set cancelled_time = ?", AUDIT.now());

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("100");
        assertThat(adjustment(result, side)).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void legacyZeroBaseSnapshotsUseTheBookingRateForPeriodEndRestoration(String side) {
        item(side, 21L, "USD", "100", "7", "40", END);
        settlement(side, 31L, 21L, "40", "7", END.plusDays(1), "POSTED");
        jdbc.update("update " + itemTable(side) + " set base_original_amount = 0, base_settled_amount = 0");
        jdbc.update("update " + allocationTable(side) + " set base_settled_amount = 0");

        assertThat(adjustment(revalue(), side)).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void savedBookingSnapshotsArePreferredToRecalculation(String side) {
        item(side, 21L, "USD", "100", "7", "40", END);
        settlement(side, 31L, 21L, "40", "7", END.plusDays(1), "POSTED");
        jdbc.update("update " + itemTable(side) + " set exchange_rate = 9");

        assertThat(adjustment(revalue(), side)).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void restoringPartialSettlementsRetainsSubCentSnapshotPrecision(String side) {
        item(side, 21L, "USD", "0.02", "2.5", "0.02", END.minusDays(1));
        settlement(side, 31L, 21L, "0.01", "2.5", END, "POSTED");
        settlement(side, 32L, 21L, "0.01", "2.5", END.plusDays(1), "POSTED");
        closingRates.put("USD", new BigDecimal("2.5"));

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("0.01");
        assertThat(adjustment(result, side)).isEqualByComparingTo("0.01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void offsetReturnsParticipateWithTheOppositeSign(String side) {
        item(side, 21L, "USD", "100", "7", "0", END);
        item(side, 22L, "USD", "20", "7", "0", END);
        jdbc.update("update " + itemTable(side) + " set status = 'OFFSET', direction = 'DECREASE' where id = 22");

        var result = revalue();

        assertThat(result.openOriginalTotal()).isEqualByComparingTo("80");
        assertThat(adjustment(result, side)).isEqualByComparingTo("16");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void sourceQueryExcludesOtherTenantsDeletedCancelledAndBaseCurrencyItems(String side) {
        for (long id = 21; id <= 26; id++) item(side, id, "USD", "100", "7", "0", END);
        jdbc.update("update " + itemTable(side) + " set company_id = 999 where id = 22");
        jdbc.update("update " + itemTable(side) + " set account_book_id = 999 where id = 23");
        jdbc.update("update " + itemTable(side) + " set deleted_flag = 1 where id = 24");
        jdbc.update("update " + itemTable(side) + " set status = 'CANCELLED' where id = 25");
        jdbc.update("update " + itemTable(side) + " set currency_code = 'CNY' where id = 26");

        assertThat(revalue().openOriginalTotal()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void futureAllocationJoinsAreScopedToActiveParentsInTheSameTenantAndPartner(String side) {
        item(side, 21L, "USD", "100", "7", "0", END);
        for (long id = 31; id <= 37; id++) settlement(side, id, 21L, "10", "7", END.plusDays(1), "POSTED");
        jdbc.update("update " + cashTable(side) + " set company_id = 999 where id = 31");
        jdbc.update("update " + cashTable(side) + " set account_book_id = 999 where id = 32");
        jdbc.update("update " + cashTable(side) + " set deleted_flag = 1 where id = 33");
        jdbc.update("update " + cashTable(side) + " set " + (side.equals("AR") ? "customer_id" : "supplier_id") + " = 999 where id = 34");
        jdbc.update("update " + cashTable(side) + " set status = 'CANCELLED' where id = 35");
        jdbc.update("update " + allocationTable(side) + " set company_id = 999 where id = 1036");
        jdbc.update("update " + allocationTable(side) + " set account_book_id = 999 where id = 1037");

        assertThat(revalue().openOriginalTotal()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void lowerClosingRatePostsTheCorrectDebitCreditAndOpeningReversal(String side) {
        item(side, 21L, "USD", "100", "7", "0", END);
        closingRates.put("USD", new BigDecimal("6.8"));

        var result = revalue();

        assertThat(adjustment(result, side)).isEqualByComparingTo("-20");
        String debit = side.equals("AR") ? "6061" : "2202";
        String credit = side.equals("AR") ? "1122" : "6061";
        assertPosting("FX_REVALUATION_" + side, result.id(), END, "20", debit, credit);
        assertPosting("FX_REVALUATION_" + side + "_REVERSAL", result.id(), END.plusDays(1), "20", credit, debit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void laterSettlementAndItsCancellationDoNotInvalidateTheSamePeriodEndBasis(String side) {
        item(side, 21L, "USD", "100", "7", "0", END);
        var original = revalue();
        settlement(side, 31L, 21L, "100", "7", END.plusDays(1), "POSTED");
        setSettled(side, "100", "7", "SETTLED");
        clearInvocations(vouchers);

        assertThat(revalue().alreadyPosted()).isTrue();
        assertThat(closeCheck().passed()).isTrue();
        jdbc.update("update " + cashTable(side) + " set status = 'CANCELLED'");
        setSettled(side, "0", "7", "UNSETTLED");

        var repeated = revalue();
        assertThat(repeated.id()).isEqualTo(original.id());
        assertThat(repeated.alreadyPosted()).isTrue();
        assertThat(closeCheck().passed()).isTrue();
        verifyNoInteractions(vouchers);
        assertThat(runCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "AP"})
    void cancellingAnInPeriodSettlementInvalidatesTheOldBasis(String side) {
        item(side, 21L, "USD", "100", "7", "40", END.minusDays(1));
        settlement(side, 31L, 21L, "40", "7", END, "POSTED");
        var original = revalue();
        assertThat(adjustment(original, side)).isEqualByComparingTo("12");
        jdbc.update("update " + cashTable(side) + " set status = 'CANCELLED', cancelled_time = ?", AUDIT.now());
        setSettled(side, "0", "7", "UNSETTLED");

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        assertFxBlocked("撤销");
        clearInvocations(vouchers);
        service.cancel(PERIOD);
        String debit = side.equals("AR") ? "6061" : "2202";
        String credit = side.equals("AR") ? "1122" : "6061";
        assertPosting("FX_REVALUATION_" + side + "_CANCEL", original.id(), END, "12", debit, credit);
        assertPosting("FX_REVALUATION_" + side + "_REVERSAL_CANCEL", original.id(), END.plusDays(1), "12", credit, debit);
        assertThat(adjustment(revalue(), side)).isEqualByComparingTo("20");
        assertThat(closeCheck().passed()).isTrue();
    }

    @Test
    void persistsReviewablePeriodEndItemsRatesAndTheirHash() throws Exception {
        item("AR", 21L, "USD", "100", "7", "100", END.minusDays(1));
        settlement("AR", 31L, 21L, "100", "7", END.plusDays(1), "POSTED");
        var result = revalue();

        String json = jdbc.queryForObject("select basis_json from fin_fx_revaluation where id = ?", String.class, result.id());
        var basis = new ObjectMapper().readTree(json);
        assertThat(basis.path("periodEnd").asText()).isEqualTo("2026-06-30");
        assertThat(basis.path("baseCurrency").asText()).isEqualTo("CNY");
        assertThat(basis.path("items").size()).isEqualTo(1);
        assertThat(basis.at("/items/0/itemId").asText()).isEqualTo("21");
        assertThat(basis.at("/items/0/openOriginal").asText()).isEqualTo("100");
        assertThat(basis.at("/items/0/carryingBase").asText()).isEqualTo("700");
        assertThat(basis.at("/items/0/closingRate").asText()).isEqualTo("7.2");
        assertThat(basis.at("/items/0/adjustment").asText()).isEqualTo("20");
        assertThat(jdbc.queryForObject("select basis_hash from fin_fx_revaluation where id = ?", String.class, result.id()))
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void changedCurrencyCompositionIsRejectedEvenWithTheSameOriginalTotalAndAdjustment() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        revalue();
        closingRates.put("EUR", new BigDecimal("8.2"));
        jdbc.update("update fin_receivable set currency_code = 'EUR', exchange_rate = 8, base_original_amount = 800");

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        assertFxBlocked("币种");
    }

    @Test
    void changedCarryingAmountInvalidatesThePostedBasis() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        revalue();
        jdbc.update("update fin_receivable set base_original_amount = 650");

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        assertFxBlocked("撤销");
    }

    @Test
    void changedAccountBookBaseCurrencyInvalidatesThePostedBasis() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        revalue();
        when(baseCurrency.current(AUDIT)).thenReturn("USD");

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        assertFxBlocked("撤销");
    }

    @Test
    void movingAnAmountFromReceivableToPayableRequiresRevaluation() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        revalue();
        jdbc.update("update fin_receivable set deleted_flag = 1");
        item("AP", 21L, "USD", "100", "7", "0", END);

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        assertFxBlocked("撤销");
    }

    @Test
    void zeroCrossCurrencyOriginalTotalDoesNotBypassMonthEndRevaluation() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        item("AP", 22L, "EUR", "100", "8", "0", END);
        jdbc.update("update fin_payable set direction = 'DECREASE', status = 'OFFSET'");

        assertFxBlocked("尚未执行");
        var result = revalue();
        assertThat(result.openOriginalTotal()).isEqualByComparingTo("0");
        assertThat(result.arAdjustment()).isEqualByComparingTo("20");
        assertThat(result.apAdjustment()).isEqualByComparingTo("-50");
        assertThat(closeCheck().passed()).isTrue();
    }

    @Test
    void anObsoletePostedAdjustmentStillBlocksCloseWhenNoOpenItemsRemain() {
        item("AR", 21L, "USD", "100", "7", "0", END.minusDays(1));
        revalue();
        settlement("AR", 31L, 21L, "100", "7", END, "POSTED");
        setSettled("AR", "100", "7", "SETTLED");

        assertFxBlocked("撤销");
        service.cancel(PERIOD);
        assertThat(closeCheck().passed()).isTrue();
    }

    @Test
    void zeroAdjustmentStillRecordsRatesAndDetectsChangesThatRoundToTheSameAmount() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        closingRates.put("USD", new BigDecimal("7"));
        assertThat(revalue().arAdjustment()).isEqualByComparingTo("0");
        assertThat(closeCheck().passed()).isTrue();
        closingRates.put("USD", new BigDecimal("7.000000001"));

        assertFxBlocked("汇率");
        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        verifyNoInteractions(vouchers);
    }

    @Test
    void oldRunsWithoutABasisCanBeCancelledAndReplacedWithoutReusingTheirAggregate() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        var original = revalue();
        jdbc.update("update fin_fx_revaluation set basis_hash = null, basis_json = null");
        closingRates.put("USD", new BigDecimal("7.5"));

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class).hasMessageContaining("缺少");
        assertFxBlocked("缺少");
        clearInvocations(vouchers);
        assertThat(service.cancel(PERIOD).status()).isEqualTo("CANCELLED");
        assertPosting("FX_REVALUATION_AR_CANCEL", original.id(), END, "20", "6061", "1122");
        assertPosting("FX_REVALUATION_AR_REVERSAL_CANCEL", original.id(), END.plusDays(1), "20", "1122", "6061");
        var replacement = revalue();
        assertThat(replacement.id()).isNotEqualTo(original.id());
        assertThat(replacement.arAdjustment()).isEqualByComparingTo("50");
        assertThat(jdbc.queryForObject("select generation from fin_fx_revaluation where id = ?", Long.class, original.id())).isEqualTo(original.id());
        assertThat(runCount()).isEqualTo(2);
        assertThat(closeCheck().passed()).isTrue();
    }

    @Test
    void missingRateIsAnExplicitCloseBlockAndDoesNotPersistAPartialRun() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        closingRates.clear();

        assertFxBlocked("有效汇率");
        assertThatThrownBy(this::revalue).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("有效汇率");
        assertThat(runCount()).isZero();
        verifyNoInteractions(vouchers);
    }

    @Test
    void inconsistentCurrentAndFutureAllocationsBlockRevaluationAndClose() {
        item("AR", 21L, "USD", "100", "7", "10", END);
        settlement("AR", 31L, 21L, "40", "7", END.plusDays(1), "POSTED");

        assertFxBlocked("无法还原");
        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class).hasMessageContaining("无法还原");
        assertThat(runCount()).isZero();
    }

    @Test
    void closedReversalPeriodPreventsPostingAnAdjustment() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        doThrow(new BusinessConflictException("下期已关闭")).when(guard).requireOpen(END.plusDays(1), "期末调汇红冲");

        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        assertThat(runCount()).isZero();
        verifyNoInteractions(vouchers);
    }

    @Test
    void closedReversalPeriodPreventsCancellingAPostedAdjustment() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        revalue();
        clearInvocations(vouchers);
        doThrow(new BusinessConflictException("下期已关闭")).when(guard).requireOpen(END.plusDays(1), "撤销期末调汇红冲");

        assertThatThrownBy(() -> service.cancel(PERIOD)).isInstanceOf(BusinessConflictException.class);
        assertThat(jdbc.queryForObject("select status from fin_fx_revaluation", String.class)).isEqualTo("POSTED");
        verifyNoInteractions(vouchers);
    }

    @Test
    void closedOrOtherAccountPeriodsCannotBeRevalued() {
        jdbc.update("update fin_account_period set status = 'CLOSED'");
        assertThatThrownBy(this::revalue).isInstanceOf(BusinessConflictException.class);
        jdbc.update("update fin_account_period set status = 'OPEN', account_book_id = 999");
        assertThatThrownBy(this::revalue).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
        assertThatThrownBy(this::closeCheck).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
        assertThat(runCount()).isZero();
    }

    @Test
    void baseCurrencyAndEmptyStatementsNeedNoRunToClose() {
        when(baseCurrency.current(AUDIT)).thenReturn("USD");
        item("AR", 21L, "USD", "100", "1", "0", END);
        closingRates.clear();

        assertThat(closeCheck().passed()).isTrue();
        assertThat(runCount()).isZero();
    }

    @Test
    void decimalScaleAndCurrencySpellingDoNotCreateFalseChanges() {
        item("AR", 21L, "USD", "100", "7", "0", END);
        var original = revalue();
        closingRates.put("USD", new BigDecimal("7.200000"));
        jdbc.update("update fin_receivable set currency_code = ' usd '");

        var repeated = revalue();
        assertThat(repeated.id()).isEqualTo(original.id());
        assertThat(repeated.alreadyPosted()).isTrue();
        assertThat(closeCheck().passed()).isTrue();
    }

    private FxRevaluationResponse revalue() {
        session.clearCache();
        return service.revalue(PERIOD);
    }

    private AccountPeriodCloseCheckResponse closeCheck() {
        session.clearCache();
        return closeChecker.check(PERIOD);
    }

    private void assertFxBlocked(String message) {
        var result = closeCheck();
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.type()).isEqualTo("FX_REVALUATION");
            assertThat(issue.message()).contains(message);
        });
    }

    private int runCount() {
        return jdbc.queryForObject("select count(*) from fin_fx_revaluation", Integer.class);
    }

    private void setSettled(String side, String amount, String rate, String status) {
        jdbc.update("update " + itemTable(side) + " set settled_amount = ?, base_settled_amount = ?, status = ?",
                decimal(amount), base(amount, rate), status);
    }

    private void assertPosting(String type, Long runId, LocalDate date, String amount, String debit, String credit) {
        verify(vouchers).recordTwoSidedVoucher(eq(type), eq(runId), eq(runId.toString()), eq(date),
                eq(decimal(amount).setScale(2)), any(), eq(debit), eq(credit), any(), eq(AUDIT));
    }

    private static BigDecimal adjustment(FxRevaluationResponse result, String side) {
        return side.equals("AR") ? result.arAdjustment() : result.apAdjustment();
    }

    private static String itemTable(String side) { return side.equals("AR") ? "fin_receivable" : "fin_payable"; }
    private static String cashTable(String side) { return side.equals("AR") ? "fin_receipt" : "fin_payment"; }
    private static String allocationTable(String side) { return cashTable(side) + "_allocation"; }

    private void item(String side, long id, String currency, String original, String rate, String settled, LocalDate date) {
        String kind = side.equals("AR") ? "receivable" : "payable";
        String partner = side.equals("AR") ? "customer_id" : "supplier_id";
        jdbc.update("insert into " + itemTable(side) + " (id, company_id, account_book_id, " + partner + ", " + kind
                        + "_no, biz_date, direction, currency_code, exchange_rate, original_amount, settled_amount,"
                        + " base_original_amount, base_settled_amount, status, deleted_flag) values (?, ?, ?, 101, ?, ?, 'INCREASE', ?, ?, ?, ?, ?, ?, ?, 0)",
                id, AUDIT.companyId(), AUDIT.accountBookId(), side + "-" + id, date, currency, decimal(rate), decimal(original), decimal(settled),
                base(original, rate), base(settled, rate), decimal(original).compareTo(decimal(settled)) == 0 ? "SETTLED"
                        : decimal(settled).signum() == 0 ? "UNSETTLED" : "PARTIALLY_SETTLED");
    }

    private void settlement(String side, long id, long itemId, String amount, String bookingRate, LocalDate date, String status) {
        String cash = side.equals("AR") ? "receipt" : "payment";
        String item = side.equals("AR") ? "receivable" : "payable";
        String partner = side.equals("AR") ? "customer_id" : "supplier_id";
        jdbc.update("insert into " + cashTable(side) + " (id, company_id, account_book_id, " + partner + ", " + cash
                        + "_no, " + cash + "_date, amount, allocated_amount, currency_code, exchange_rate, base_amount, status, deleted_flag)"
                        + " values (?, ?, ?, 101, ?, ?, ?, ?, 'USD', 7.2, ?, ?, 0)",
                id, AUDIT.companyId(), AUDIT.accountBookId(), "CASH-" + id, date, decimal(amount), decimal(amount), base(amount, "7.2"), status);
        jdbc.update("insert into " + allocationTable(side) + " (id, company_id, account_book_id, " + cash + "_id, " + item
                        + "_id, amount, base_amount, base_settled_amount, fx_gain_loss_amount) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id + 1000, AUDIT.companyId(), AUDIT.accountBookId(), id, itemId, decimal(amount), base(amount, "7.2"),
                base(amount, bookingRate), base(amount, "7.2").subtract(base(amount, bookingRate)));
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
    private static BigDecimal base(String amount, String rate) { return decimal(amount).multiply(decimal(rate)).setScale(6, RoundingMode.HALF_UP); }

    private void createTable(Class<?> entity) {
        TableInfo table = TableInfoHelper.getTableInfo(entity);
        List<String> columns = new ArrayList<>();
        columns.add(table.getKeyColumn() + " bigint primary key");
        table.getFieldList().forEach(field -> columns.add(field.getColumn() + " " + sqlType(field.getPropertyType())));
        jdbc.execute("create table " + table.getTableName() + " (" + String.join(", ", columns) + ")");
    }

    private static String sqlType(Class<?> type) {
        if (type == BigDecimal.class) return "decimal(24, 6)";
        if (type == Long.class) return "bigint";
        if (type == Integer.class || type == Boolean.class) return "integer";
        if (type == LocalDate.class) return "date";
        if (type == LocalDateTime.class) return "timestamp";
        return "varchar(4096)";
    }
}
