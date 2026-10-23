package com.tuowei.erp.finance.statement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.statement.service.PartnerStatementService;
import com.tuowei.erp.finance.statement.service.PartnerStatementSettlementService;
import com.tuowei.erp.finance.statement.web.PartnerStatementResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises real MyBatis query predicates in an isolated in-memory database, without a Spring Boot context. */
class PartnerStatementServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(1L, 101L, 202L, LocalDateTime.of(2026, 9, 30, 12, 0));
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final long PARTNER_ID = 11L;

    private SqlSession session;
    private JdbcTemplate jdbc;
    private PartnerStatementService service;
    private BaseCurrencyService baseCurrencyService;

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:partner_statement_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("statement-test", new JdbcTransactionFactory(), datasource));
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig()).setSqlInjector(new DefaultSqlInjector()));
        for (Class<?> mapper : List.of(ReceivableMapper.class, PayableMapper.class, ReceiptMapper.class,
                PaymentMapper.class, ReceiptAllocationMapper.class, PaymentAllocationMapper.class,
                CustomerMapper.class, SupplierMapper.class)) {
            configuration.addMapper(mapper);
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        for (Class<?> entity : List.of(ReceivableEntity.class, PayableEntity.class, ReceiptEntity.class,
                PaymentEntity.class, ReceiptAllocationEntity.class, PaymentAllocationEntity.class,
                CustomerEntity.class, SupplierEntity.class)) {
            createReadModelTable(entity);
        }
        AuditMetadataFactory audit = mock(AuditMetadataFactory.class);
        when(audit.current()).thenReturn(AUDIT);
        baseCurrencyService = mock(BaseCurrencyService.class);
        when(baseCurrencyService.current(any(AuditMetadata.class))).thenReturn("CNY");
        service = new PartnerStatementService(session.getMapper(ReceivableMapper.class), session.getMapper(PayableMapper.class),
                session.getMapper(ReceiptMapper.class), session.getMapper(PaymentMapper.class),
                session.getMapper(CustomerMapper.class), session.getMapper(SupplierMapper.class), audit,
                new PartnerStatementSettlementService(session.getMapper(ReceiptAllocationMapper.class), session.getMapper(PaymentAllocationMapper.class)),
                baseCurrencyService);
        jdbc.update("insert into md_customer (id, company_id, account_book_id, customer_name, deleted_flag) values (?, ?, ?, 'Customer', 0)",
                PARTNER_ID, AUDIT.companyId(), AUDIT.accountBookId());
        jdbc.update("insert into md_supplier (id, company_id, account_book_id, supplier_name, deleted_flag) values (?, ?, ?, 'Supplier', 0)",
                PARTNER_ID, AUDIT.companyId(), AUDIT.accountBookId());
    }

    @AfterEach
    void closeDatabase() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void fullySettledForeignBalanceIsZeroAndActualCashRemainsVisible(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");

        PartnerStatementResponse result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0.00");
        assertThat(result.totalIncrease()).isEqualByComparingTo("720.00");
        assertThat(result.totalDecrease()).isEqualByComparingTo("720.00");
        assertThat(result.lines()).anySatisfy(line -> {
            assertThat(line.docType()).isEqualTo(cashType(side));
            assertThat(line.amount()).isEqualByComparingTo("720.00");
        });
        assertThat(result.lines()).anySatisfy(line -> {
            assertThat(line.docType()).isEqualTo("FX_ADJUSTMENT");
            assertThat(line.docNo()).isEqualTo("CASH-31");
            assertThat(line.direction()).isEqualTo("INCREASE");
            assertThat(line.amount()).isEqualByComparingTo("20.00");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void lowerSettlementRateReducesTheBalanceByTheExchangeDifference(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "6.8", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "6.8", "7");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        adjustment(result, "FX_ADJUSTMENT", "DECREASE", "20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void partialSettlementLeavesTheUnpaidAmountAtItsBookingRate(String side) {
        item(side, 21L, "100", "7", "40", FROM);
        settlement(side, 31L, "40", "40", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "40", "7.2", "7");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("420");
        adjustment(result, "FX_ADJUSTMENT", "INCREASE", "8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void overpaymentRemainsAsAnAdvanceAtTheSettlementRate(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "150", "100", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("-360");
        adjustment(result, "FX_ADJUSTMENT", "INCREASE", "20");
        assertThat(result.lines()).noneMatch(line -> line.docType().equals("ROUNDING_ADJUSTMENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void advanceWithoutAllocationsRemainsAnUnappliedPartnerBalance(String side) {
        settlement(side, 31L, "100", "0", "7.2", FROM.plusDays(1), "POSTED");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("-720");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).docType()).isEqualTo(cashType(side));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void oneSettlementCanRelieveItemsWithDifferentBookingRates(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        item(side, 22L, "100", "8", "100", FROM);
        settlement(side, 31L, "200", "200", "7.5", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.5", "7");
        allocation(side, 42L, 31L, 22L, "100", "7.5", "8");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines()).noneMatch(line -> line.docType().endsWith("ADJUSTMENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void priorPeriodSettlementsAndTheirAdjustmentsAreIncludedInOpeningBalance(String side) {
        item(side, 21L, "100", "7", "100", FROM.minusDays(10));
        settlement(side, 31L, "40", "40", "7.2", FROM.minusDays(1), "POSTED");
        settlement(side, 32L, "60", "60", "6.8", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "40", "7.2", "7");
        allocation(side, 42L, 32L, 21L, "60", "6.8", "7");

        var result = statement(side);

        assertThat(result.openingBalance()).isEqualByComparingTo("420");
        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        assertThat(result.lines()).allMatch(line -> !line.bizDate().isBefore(FROM));
        adjustment(result, "FX_ADJUSTMENT", "DECREASE", "12");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void settlementsAfterTheStatementEndDoNotReduceTheHistoricalBalance(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", TO.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("700");
        assertThat(result.lines()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void cancelledSettlementsAndTheirAllocationsDoNotAffectTheBalance(String side) {
        item(side, 21L, "100", "7", "0", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "CANCELLED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("700");
        assertThat(result.lines()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void returnsContinueToReduceTheNetPartnerBalance(String side) {
        item(side, 21L, "100", "7", "40", FROM);
        item(side, 22L, "20", "7", "0", FROM.plusDays(1));
        jdbc.update("update " + itemTable(side) + " set direction = 'DECREASE', status = 'OFFSET' where id = 22");
        settlement(side, 31L, "40", "40", "7.2", FROM.plusDays(2), "POSTED");
        allocation(side, 41L, 31L, 21L, "40", "7.2", "7");

        assertThat(statement(side).closingBalance()).isEqualByComparingTo("280");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void historicalZeroDefaultSnapshotsFallBackToDocumentRates(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");
        jdbc.update("update " + allocationTable(side) + " set base_amount = 0, base_settled_amount = 0, fx_gain_loss_amount = 0");
        jdbc.update("update " + itemTable(side) + " set base_original_amount = 0, base_settled_amount = 0");
        jdbc.update("update " + cashTable(side) + " set base_amount = 0");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        adjustment(result, "FX_ADJUSTMENT", "INCREASE", "20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void legacyBaseCurrencyDocumentsNeedNoAdjustmentRows(String side) {
        item(side, 21L, "100", "1", "100", FROM);
        settlement(side, 31L, "100", "100", "1", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "1", "1");
        jdbc.update("update " + itemTable(side) + " set currency_code = null, exchange_rate = null, base_original_amount = 0");
        jdbc.update("update " + cashTable(side) + " set currency_code = null, exchange_rate = null, base_amount = 0");
        jdbc.update("update " + allocationTable(side) + " set base_amount = 0, base_settled_amount = 0, fx_gain_loss_amount = 0");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        assertThat(result.lines()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void storedBaseAndExchangeSnapshotsArePreferredToRecalculation(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");
        jdbc.update("update " + itemTable(side) + " set exchange_rate = 9");
        jdbc.update("update " + cashTable(side) + " set exchange_rate = 10");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        adjustment(result, "FX_ADJUSTMENT", "INCREASE", "20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void repeatedPartialSettlementsDoNotAccumulateFractionalCentResiduals(String side) {
        item(side, 21L, "0.02", "2.5", "0.02", FROM);
        settlement(side, 31L, "0.01", "0.01", "2.5", FROM.plusDays(1), "POSTED");
        settlement(side, 32L, "0.01", "0.01", "2.5", FROM.plusDays(2), "POSTED");
        allocation(side, 41L, 31L, 21L, "0.01", "2.5", "2.5");
        allocation(side, 42L, 32L, 21L, "0.01", "2.5", "2.5");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        adjustment(result, "ROUNDING_ADJUSTMENT", "INCREASE", "0.01");
        assertThat(result.lines()).noneMatch(line -> line.docType().equals("FX_ADJUSTMENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void settlementStillAppearsWhenItsBaseCurrencyCashAmountRoundsToZero(String side) {
        item(side, 21L, "0.01", "0.6", "0.01", FROM);
        settlement(side, 31L, "0.01", "0.01", "0.4", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "0.01", "0.4", "0.6");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        assertThat(result.lines()).anySatisfy(line -> {
            assertThat(line.docType()).isEqualTo(cashType(side));
            assertThat(line.amount()).isEqualByComparingTo("0");
        });
        adjustment(result, "ROUNDING_ADJUSTMENT", "DECREASE", "0.01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void onePaymentForManySmallItemsShowsTheEntireRoundingDifference(String side) {
        for (int i = 0; i < 12; i++) {
            item(side, 100L + i, "0.01", "2.5", "0.01", FROM);
            allocation(side, 200L + i, 31L, 100L + i, "0.01", "2.5", "2.5");
        }
        settlement(side, 31L, "0.12", "0.12", "2.5", FROM.plusDays(1), "POSTED");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        adjustment(result, "ROUNDING_ADJUSTMENT", "DECREASE", "0.06");
        assertThat(result.totalIncrease()).isEqualByComparingTo("0.36");
        assertThat(result.totalDecrease()).isEqualByComparingTo("0.36");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void allocationsFromOtherAccountsAreExcludedByTheActualSql(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");
        allocation(side, 42L, 31L, 21L, "100", "99", "7");
        allocation(side, 43L, 31L, 21L, "100", "99", "7");
        jdbc.update("update " + allocationTable(side) + " set account_book_id = 999 where id = 42");
        jdbc.update("update " + allocationTable(side) + " set company_id = 999 where id = 43");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("0");
        adjustment(result, "FX_ADJUSTMENT", "INCREASE", "20");
        assertThat(result.lines()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void deletedAndOtherPartyOrTenantSettlementsAreExcluded(String side) {
        item(side, 21L, "100", "7", "0", FROM);
        for (long id = 31; id <= 34; id++) {
            settlement(side, id, "100", "100", "7.2", FROM.plusDays(1), "POSTED");
            allocation(side, id + 10, id, 21L, "100", "7.2", "7");
        }
        jdbc.update("update " + cashTable(side) + " set deleted_flag = 1 where id = 31");
        jdbc.update("update " + cashTable(side) + " set account_book_id = 999 where id = 32");
        jdbc.update("update " + cashTable(side) + " set company_id = 999 where id = 33");
        jdbc.update("update " + cashTable(side) + " set " + (side.equals("CUSTOMER") ? "customer_id" : "supplier_id") + " = 999 where id = 34");

        var result = statement(side);

        assertThat(result.closingBalance()).isEqualByComparingTo("700");
        assertThat(result.lines()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void missingAllocationsAreReportedInsteadOfBeingDisguisedAsFx(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "POSTED");

        assertThatThrownBy(() -> statement(side)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("核销明细与收付款金额不一致");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "SUPPLIER"})
    void allocationsCannotRelieveAnotherPartnersItem(String side) {
        item(side, 21L, "100", "7", "100", FROM);
        settlement(side, 31L, "100", "100", "7.2", FROM.plusDays(1), "POSTED");
        allocation(side, 41L, 31L, 21L, "100", "7.2", "7");
        jdbc.update("update " + itemTable(side) + " set " + (side.equals("CUSTOMER") ? "customer_id" : "supplier_id") + " = 999");

        assertThatThrownBy(() -> statement(side)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("核销来源不属于当前往来单位");
    }

    @Test
    void responseIdentifiesTheAccountBookBaseCurrency() {
        when(baseCurrencyService.current(AUDIT)).thenReturn("EUR");

        assertThat(statement("CUSTOMER").baseCurrencyCode()).isEqualTo("EUR");
    }

    private void adjustment(PartnerStatementResponse statement, String type, String direction, String amount) {
        assertThat(statement.lines()).anySatisfy(line -> {
            assertThat(line.docType()).isEqualTo(type);
            assertThat(line.direction()).isEqualTo(direction);
            assertThat(line.amount()).isEqualByComparingTo(amount);
        });
    }

    private PartnerStatementResponse statement(String side) {
        session.clearCache();
        var result = service.statement(side, PARTNER_ID, FROM, TO);
        BigDecimal running = result.openingBalance();
        for (var line : result.lines()) {
            running = line.direction().equals("INCREASE") ? running.add(line.amount()) : running.subtract(line.amount());
            assertThat(line.balance()).as("running balance after %s %s", line.docType(), line.docNo()).isEqualByComparingTo(running);
        }
        assertThat(result.closingBalance()).isEqualByComparingTo(running);
        assertThat(result.openingBalance().add(result.totalIncrease()).subtract(result.totalDecrease()))
                .isEqualByComparingTo(result.closingBalance());
        return result;
    }

    private static String itemTable(String side) {
        return side.equals("CUSTOMER") ? "fin_receivable" : "fin_payable";
    }

    private static String cashTable(String side) {
        return side.equals("CUSTOMER") ? "fin_receipt" : "fin_payment";
    }

    private static String allocationTable(String side) {
        return side.equals("CUSTOMER") ? "fin_receipt_allocation" : "fin_payment_allocation";
    }

    private void item(String side, long id, String original, String rate, String settled, LocalDate date) {
        String table = side.equals("CUSTOMER") ? "fin_receivable" : "fin_payable";
        String numberColumn = side.equals("CUSTOMER") ? "receivable_no" : "payable_no";
        String partnerColumn = side.equals("CUSTOMER") ? "customer_id" : "supplier_id";
        jdbc.update("insert into " + table + " (id, company_id, account_book_id, " + partnerColumn + ", " + numberColumn
                        + ", direction, biz_date, original_amount, settled_amount, currency_code, exchange_rate, base_original_amount,"
                        + " base_settled_amount, status, deleted_flag) values (?, ?, ?, ?, ?, 'INCREASE', ?, ?, ?, 'USD', ?, ?, ?, ?, 0)",
                id, AUDIT.companyId(), AUDIT.accountBookId(), PARTNER_ID, "ITEM-" + id, date,
                decimal(original), decimal(settled), decimal(rate), base(original, rate), base(settled, rate),
                decimal(original).compareTo(decimal(settled)) == 0 ? "SETTLED" : "PARTIALLY_SETTLED");
    }

    private void settlement(String side, long id, String amount, String allocated, String rate, LocalDate date, String status) {
        String table = side.equals("CUSTOMER") ? "fin_receipt" : "fin_payment";
        String prefix = side.equals("CUSTOMER") ? "receipt" : "payment";
        String partnerColumn = side.equals("CUSTOMER") ? "customer_id" : "supplier_id";
        jdbc.update("insert into " + table + " (id, company_id, account_book_id, " + partnerColumn + ", " + prefix + "_no, "
                        + prefix + "_date, amount, allocated_amount, currency_code, exchange_rate, base_amount, status, deleted_flag)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, 'USD', ?, ?, ?, 0)",
                id, AUDIT.companyId(), AUDIT.accountBookId(), PARTNER_ID, "CASH-" + id, date,
                decimal(amount), decimal(allocated), decimal(rate), base(amount, rate), status);
    }

    private void allocation(String side, long id, long settlementId, long itemId, String amount, String settlementRate, String bookingRate) {
        String table = side.equals("CUSTOMER") ? "fin_receipt_allocation" : "fin_payment_allocation";
        String cashColumn = side.equals("CUSTOMER") ? "receipt_id" : "payment_id";
        String itemColumn = side.equals("CUSTOMER") ? "receivable_id" : "payable_id";
        jdbc.update("insert into " + table + " (id, company_id, account_book_id, " + cashColumn + ", " + itemColumn
                        + ", amount, base_amount, base_settled_amount, fx_gain_loss_amount) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, AUDIT.companyId(), AUDIT.accountBookId(), settlementId, itemId, decimal(amount), base(amount, settlementRate),
                base(amount, bookingRate), base(amount, settlementRate).subtract(base(amount, bookingRate)));
    }

    private static String cashType(String side) {
        return side.equals("CUSTOMER") ? "RECEIPT" : "PAYMENT";
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal base(String amount, String rate) {
        return decimal(amount).multiply(decimal(rate)).setScale(6, RoundingMode.HALF_UP);
    }

    /** Only the queried entity schema is needed here; MySQL/Flyway migrations have separate integration coverage. */
    private void createReadModelTable(Class<?> entity) {
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
