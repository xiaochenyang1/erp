package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.payment.service.PaymentCommandService;
import com.tuowei.erp.finance.payment.service.PaymentNumberService;
import com.tuowei.erp.finance.payment.service.PaymentQueryService;
import com.tuowei.erp.finance.payment.web.PaymentAllocationRequest;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.service.ReceiptCommandService;
import com.tuowei.erp.finance.receipt.service.ReceiptNumberService;
import com.tuowei.erp.finance.receipt.service.ReceiptQueryService;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Actual command transactions, MyBatis persistence and voucher entries in an isolated H2 database. */
class SettlementPostingWorkflowTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(1L, 9L, 2L, LocalDateTime.of(2026, 9, 30, 12, 0));
    private static final LocalDate DATE = LocalDate.of(2026, 9, 20);
    private JdbcTemplate jdbc;
    private SqlSessionTemplate session;
    private ReceiptCommandService receipts;
    private PaymentCommandService payments;
    private FinanceVoucherPostingService vouchers;

    enum Side {
        RECEIPT("receivable", "customer", "1122", "2203"), PAYMENT("payable", "supplier", "2202", "1123");
        final String item;
        final String partner;
        final String subject;
        final String advanceSubject;
        Side(String item, String partner, String subject, String advanceSubject) {
            this.item = item;
            this.partner = partner;
            this.subject = subject;
            this.advanceSubject = advanceSubject;
        }
        String cash() { return name().toLowerCase(java.util.Locale.ROOT); }
        String itemTable() { return "fin_" + item; }
        String cashTable() { return "fin_" + cash(); }
    }

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:settlement_posting_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("settlement-test", new SpringManagedTransactionFactory(), datasource));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig()).setSqlInjector(new DefaultSqlInjector()));
        for (Class<?> mapper : List.of(ReceivableMapper.class, PayableMapper.class, ReceiptMapper.class,
                PaymentMapper.class, ReceiptAllocationMapper.class, PaymentAllocationMapper.class,
                VoucherMapper.class, VoucherEntryMapper.class, AccountSubjectMapper.class)) configuration.addMapper(mapper);
        session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        for (Class<?> entity : List.of(ReceivableEntity.class, PayableEntity.class, ReceiptEntity.class,
                PaymentEntity.class, ReceiptAllocationEntity.class, PaymentAllocationEntity.class,
                VoucherEntity.class, VoucherEntryEntity.class, AccountSubjectEntity.class)) createTable(entity);
        var audit = mock(AuditMetadataFactory.class);
        when(audit.current()).thenReturn(AUDIT);
        var rates = mock(SettlementCurrencyService.class);
        when(rates.resolve(any(), any(), any(), any())).thenAnswer(call ->
                new SettlementCurrencyService.Resolution(call.getArgument(0), call.getArgument(1)));
        var receiptNumbers = mock(ReceiptNumberService.class);
        var paymentNumbers = mock(PaymentNumberService.class);
        var sequence = new AtomicLong();
        when(receiptNumbers.nextReceiptNo(any())).thenAnswer(call -> "FR-" + sequence.incrementAndGet());
        when(paymentNumbers.nextPaymentNo(any())).thenAnswer(call -> "FP-" + sequence.incrementAndGet());
        vouchers = new FinanceVoucherPostingService(session.getMapper(VoucherMapper.class),
                session.getMapper(VoucherEntryMapper.class), session.getMapper(AccountSubjectMapper.class));
        var posting = new FinancePostingService(mock(FinanceSubledgerPostingService.class), vouchers);
        var transactions = new TransactionInterceptor(new DataSourceTransactionManager(datasource),
                new AnnotationTransactionAttributeSource());
        receipts = proxy(new ReceiptCommandService(session.getMapper(ReceiptMapper.class),
                session.getMapper(ReceiptAllocationMapper.class), session.getMapper(ReceivableMapper.class),
                receiptNumbers, audit, mock(AccountPeriodGuard.class),
                new ReceiptQueryService(session.getMapper(ReceiptMapper.class), session.getMapper(ReceiptAllocationMapper.class), audit),
                posting, rates), transactions);
        payments = proxy(new PaymentCommandService(session.getMapper(PaymentMapper.class),
                session.getMapper(PaymentAllocationMapper.class), session.getMapper(PayableMapper.class),
                paymentNumbers, audit, mock(AccountPeriodGuard.class),
                new PaymentQueryService(session.getMapper(PaymentMapper.class), session.getMapper(PaymentAllocationMapper.class), audit),
                posting, rates), transactions);
    }

    @AfterEach
    void close() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }

    @ParameterizedTest
    @EnumSource(Side.class)
    void twelveSmallAllocationsMustPostTheActualCashTotal(Side side) {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            item(side, id, "0.01", "2.5");
            ids.add(id);
        }

        long settlement = create(side, "0.12", "2.5", ids, "0.01");

        assertVoucher(side, settlement, "0.30", "0.36", "0", "-0.06");
        cancel(side, settlement);
        assertNet(side, "0.36", "0");
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void partialSettlementsRelieveTheCarryingBalanceAndReverseTheirOwnAmounts(Side side) {
        item(side, 1L, "0.02", "2.5");
        long first = create(side, "0.01", "2.5", List.of(1L), "0.01");
        long second = create(side, "0.01", "2.5", List.of(1L), "0.01");

        assertVoucher(side, first, "0.03", "0.03", "0", "0");
        assertVoucher(side, second, "0.03", "0.02", "0", "0.01");
        assertNet(side, "0", "0.06");
        cancel(side, first);
        assertNet(side, "0.03", "0.03");
        long third = create(side, "0.01", "2.5", List.of(1L), "0.01");
        assertVoucher(side, third, "0.03", "0.03", "0", "0");
        assertNet(side, "0", "0.06");
        cancel(side, second);
        cancel(side, third);
        assertNet(side, "0.05", "0");
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void aPositiveOriginalAmountCanHaveZeroBaseCash(Side side) {
        item(side, 1L, "0.01", "2.5");
        long settlement = create(side, "0.01", "0.1", List.of(1L), "0.01");
        assertVoucher(side, settlement, "0", "0.03", "0", "-0.03");
        cancel(side, settlement);
        assertNet(side, "0.03", "0");
    }

    private void item(Side side, long id, String amount, String rate) {
        BigDecimal original = decimal(amount);
        BigDecimal base = original.multiply(decimal(rate));
        jdbc.update("insert into " + side.itemTable() + " (id, company_id, account_book_id, " + side.item
                        + "_no, source_type, source_id, source_no, direction, " + side.partner + "_id, biz_date,"
                        + " original_amount, settled_amount, currency_code, exchange_rate, base_original_amount,"
                        + " base_settled_amount, status, deleted_flag, version)"
                        + " values (?, ?, ?, ?, 'OPENING', ?, ?, 'INCREASE', 11, ?, ?, 0, 'USD', ?, ?, 0, 'UNSETTLED', 0, 0)",
                id, AUDIT.companyId(), AUDIT.accountBookId(), "ITEM-" + id, id, "ITEM-" + id, DATE.minusDays(1),
                original, decimal(rate), base);
        vouchers.recordTwoSidedVoucher("OPENING_" + side.name(), id, "ITEM-" + id, DATE.minusDays(1),
                base.setScale(2, RoundingMode.HALF_UP), "opening", side == Side.RECEIPT ? side.subject : "1001",
                side == Side.RECEIPT ? "6001" : side.subject, "opening", AUDIT);
    }

    private long create(Side side, String total, String rate, List<Long> ids, String allocation) {
        if (side == Side.RECEIPT) return receipts.create(new ReceiptCreateRequest(11L, DATE, decimal(total), "USD", decimal(rate),
                null, ids.stream().map(id -> new ReceiptAllocationRequest(id, decimal(allocation))).toList())).id();
        return payments.create(new PaymentCreateRequest(11L, DATE, decimal(total), "USD", decimal(rate),
                null, ids.stream().map(id -> new PaymentAllocationRequest(id, decimal(allocation))).toList())).id();
    }

    private void cancel(Side side, long id) {
        if (side == Side.RECEIPT) receipts.cancel(id, new ReceiptCancelRequest("test reversal"));
        else payments.cancel(id, new PaymentCancelRequest("test reversal"));
    }

    private void assertVoucher(Side side, long id, String cash, String relief, String advance, String fx) {
        Long voucher = jdbc.queryForObject("select id from fin_voucher where source_type = ? and source_id = ?", Long.class, side.name(), id);
        assertThat(jdbc.queryForObject("select amount from fin_voucher where id = ?", BigDecimal.class, voucher)).isEqualByComparingTo(cash);
        assertThat(leg(voucher, "1002", side == Side.RECEIPT)).isEqualByComparingTo(cash);
        assertThat(leg(voucher, side.subject, side == Side.PAYMENT)).isEqualByComparingTo(relief);
        assertThat(leg(voucher, side.advanceSubject, side == Side.PAYMENT)).isEqualByComparingTo(advance);
        assertThat(leg(voucher, "6061", side == Side.PAYMENT)).isEqualByComparingTo(fx);
        assertThat(jdbc.queryForObject("select coalesce(sum(debit_amount - credit_amount), 0) from fin_voucher_entry where voucher_id = ?",
                BigDecimal.class, voucher)).isEqualByComparingTo("0");
    }

    private BigDecimal leg(Long voucher, String subject, boolean debit) {
        return jdbc.queryForObject("select coalesce(sum(" + (debit ? "debit_amount - credit_amount" : "credit_amount - debit_amount")
                + "), 0) from fin_voucher_entry where voucher_id = ? and subject_code = ?", BigDecimal.class, voucher, subject);
    }

    private void assertNet(Side side, String balance, String cash) {
        BigDecimal net = jdbc.queryForObject("select coalesce(sum(debit_amount - credit_amount), 0) from fin_voucher_entry where subject_code = ?",
                BigDecimal.class, side.subject);
        assertThat(side == Side.RECEIPT ? net : net.negate()).isEqualByComparingTo(balance);
        BigDecimal netCash = jdbc.queryForObject("select coalesce(sum(debit_amount - credit_amount), 0) from fin_voucher_entry where subject_code = '1002'", BigDecimal.class);
        assertThat(side == Side.RECEIPT ? netCash : netCash.negate()).isEqualByComparingTo(cash);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, TransactionInterceptor transactions) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transactions);
        return (T) factory.getProxy();
    }

    private void createTable(Class<?> entity) {
        var table = TableInfoHelper.getTableInfo(entity);
        List<String> columns = new ArrayList<>();
        columns.add(table.getKeyColumn() + " bigint primary key");
        for (var field : table.getFieldList()) {
            Class<?> type = field.getPropertyType();
            String sqlType = type == BigDecimal.class ? "decimal(20, 6)" : type == Long.class ? "bigint"
                    : type == Integer.class ? "integer" : type == LocalDate.class ? "date"
                    : type == LocalDateTime.class ? "timestamp" : "varchar(1000)";
            columns.add(field.getColumn() + " " + sqlType);
        }
        jdbc.execute("create table " + table.getTableName() + " (" + String.join(", ", columns) + ")");
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
