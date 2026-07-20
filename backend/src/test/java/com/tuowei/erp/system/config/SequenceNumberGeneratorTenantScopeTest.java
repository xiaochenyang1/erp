package com.tuowei.erp.system.config;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SequenceNumberGeneratorTenantScopeTest {

    private static final String BIZ_TYPE = "TENANT_SCOPED_DOC";
    private static final String PERIOD_BIZ_TYPE = "TENANT_PERIOD_DOC";
    private static final String CONCURRENT_BIZ_TYPE = "TENANT_CONCURRENT_DOC";
    private static final String ACCOUNT_BOOK_BIZ_TYPE = "ACCOUNT_BOOK_SCOPED_DOC";

    @Autowired
    private SequenceNumberGenerator sequenceNumberGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from sys_sequence_counter where biz_type in (?, ?, ?, ?)",
                BIZ_TYPE, PERIOD_BIZ_TYPE, CONCURRENT_BIZ_TYPE, ACCOUNT_BOOK_BIZ_TYPE);
        jdbcTemplate.update("delete from sys_sequence_rule where biz_type in (?, ?, ?, ?)",
                BIZ_TYPE, PERIOD_BIZ_TYPE, CONCURRENT_BIZ_TYPE, ACCOUNT_BOOK_BIZ_TYPE);
    }

    @Test
    @WithErpUser(userId = 96005L, companyId = 5L, accountBookId = 2L)
    void nextNumberUsesRuleAndCounterForCurrentAccountBookWithinSameCompany() {
        seedRule(960051L, 5L, 1L, ACCOUNT_BOOK_BIZ_TYPE, "B1-", 40L);
        seedRule(960052L, 5L, 2L, ACCOUNT_BOOK_BIZ_TYPE, "B2-", 7L);

        String nextNumber = sequenceNumberGenerator.nextNumber(
                ACCOUNT_BOOK_BIZ_TYPE,
                "账套编号测试单据",
                LocalDate.of(2026, 6, 1)
        );

        assertThat(nextNumber).isEqualTo("B2-20260601008");
        assertThat(currentValue(5L, 1L, ACCOUNT_BOOK_BIZ_TYPE)).isEqualTo(40L);
        assertThat(currentValue(5L, 2L, ACCOUNT_BOOK_BIZ_TYPE)).isEqualTo(8L);
    }

    @Test
    @WithErpUser(userId = 96002L, companyId = 2L, accountBookId = 1L)
    void nextNumberUsesRuleForCurrentCompany() {
        seedRule(96001L, 1L, BIZ_TYPE, "A-", 0L);
        seedRule(96002L, 2L, BIZ_TYPE, "B-", 7L);

        String nextNumber = sequenceNumberGenerator.nextNumber(
                BIZ_TYPE,
                "租户编号测试单据",
                LocalDate.of(2026, 6, 1)
        );

        assertThat(nextNumber).isEqualTo("B-20260601008");
        assertThat(currentValue(1L)).isZero();
        assertThat(currentValue(2L)).isEqualTo(8L);
    }

    @Test
    @WithErpUser(userId = 96003L, companyId = 3L, accountBookId = 1L)
    void nextNumberResetsForEachFormattedPeriod() {
        seedRule(96003L, 3L, PERIOD_BIZ_TYPE, "PD-", 0L);

        assertThat(sequenceNumberGenerator.nextNumber(
                PERIOD_BIZ_TYPE,
                "周期编号测试单据",
                LocalDate.of(2026, 6, 1)
        )).isEqualTo("PD-20260601001");
        assertThat(sequenceNumberGenerator.nextNumber(
                PERIOD_BIZ_TYPE,
                "周期编号测试单据",
                LocalDate.of(2026, 6, 1)
        )).isEqualTo("PD-20260601002");
        assertThat(sequenceNumberGenerator.nextNumber(
                PERIOD_BIZ_TYPE,
                "周期编号测试单据",
                LocalDate.of(2026, 6, 2)
        )).isEqualTo("PD-20260602001");
    }

    @Test
    @WithErpUser(userId = 96004L, companyId = 4L, accountBookId = 1L)
    void nextNumberGeneratesUniqueContiguousNumbersUnderConcurrency() throws Exception {
        seedRule(96004L, 4L, CONCURRENT_BIZ_TYPE, "CC-", 0L);

        int requestCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                tasks.add(() -> {
                    start.await();
                    TestSecurityContexts.useUser(
                            96004L,
                            4L,
                            1L,
                            1L,
                            1L,
                            "sequence_concurrent_user",
                            "sequence_concurrent_user",
                            PermissionCodes.allPermissions(),
                            DataScopeSnapshot.all()
                    );
                    return sequenceNumberGenerator.nextNumber(
                            CONCURRENT_BIZ_TYPE,
                            "并发编号测试单据",
                            LocalDate.of(2026, 6, 1)
                    );
                });
            }

            List<Future<String>> futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            start.countDown();
            for (Future<String> future : futures) {
                numbers.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(numbers).hasSize(requestCount);
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(expectedNumbers(requestCount));
    }

    private List<String> expectedNumbers(int requestCount) {
        List<String> numbers = new ArrayList<>();
        for (int index = 1; index <= requestCount; index++) {
            numbers.add("CC-20260601%03d".formatted(index));
        }
        return numbers;
    }

    private void seedRule(long id, long companyId, String bizType, String prefix, long currentValue) {
        seedRule(id, companyId, 1L, bizType, prefix, currentValue);
    }

    private void seedRule(long id, long companyId, long accountBookId, String bizType, String prefix, long currentValue) {
        jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value, status,
                     created_by, updated_by, version)
                values
                    (?, ?, ?, ?, ?, 'yyyyMMdd', 3, ?, 'ACTIVE', 0, 0, 0)
                """,
                id,
                companyId,
                accountBookId,
                bizType,
                prefix,
                currentValue);
    }

    private Long currentValue(long companyId) {
        return currentValue(companyId, 1L, BIZ_TYPE);
    }

    private Long currentValue(long companyId, long accountBookId, String bizType) {
        return jdbcTemplate.queryForObject("""
                select current_value
                from sys_sequence_rule
                where company_id = ?
                  and account_book_id = ?
                  and biz_type = ?
                """, Long.class, companyId, accountBookId, bizType);
    }
}
