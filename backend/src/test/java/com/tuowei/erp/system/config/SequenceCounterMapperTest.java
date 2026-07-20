package com.tuowei.erp.system.config;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.model.SequenceCounterEntity;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SequenceCounterMapperTest {

    private static final long COMPANY_ID = 97001L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long OTHER_ACCOUNT_BOOK_ID = 2L;
    private static final String BIZ_TYPE = "ATOMIC_COUNTER_DOC";
    private static final String PERIOD_KEY = "20260601";

    @Autowired
    private SequenceCounterMapper sequenceCounterMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from sys_sequence_counter
                where company_id in (?, ?)
                  and biz_type in (?, ?)
                """,
                COMPANY_ID,
                COMPANY_ID + 1L,
                BIZ_TYPE,
                BIZ_TYPE + "_OTHER");
    }

    @Test
    void incrementCurrentValueAtomicallyAddsOneAndReturnsUpdatedValue() {
        TestSecurityContexts.useUser(
                97002L,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                1L,
                1L,
                "sequence_counter_mapper_user",
                "sequence_counter_mapper_user",
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
        seedCounter(7L);
        seedCounter(OTHER_ACCOUNT_BOOK_ID, PERIOD_KEY, 30L);

        int updatedRows = sequenceCounterMapper.incrementCurrentValue(
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                BIZ_TYPE,
                PERIOD_KEY,
                97002L,
                LocalDateTime.of(2026, 6, 1, 9, 0)
        );
        Long currentValue = sequenceCounterMapper.selectCurrentValue(COMPANY_ID, ACCOUNT_BOOK_ID, BIZ_TYPE, PERIOD_KEY);
        Long otherBookCurrentValue = sequenceCounterMapper.selectCurrentValue(
                COMPANY_ID,
                OTHER_ACCOUNT_BOOK_ID,
                BIZ_TYPE,
                PERIOD_KEY
        );

        assertThat(updatedRows).isEqualTo(1);
        assertThat(currentValue).isEqualTo(8L);
        assertThat(otherBookCurrentValue).isEqualTo(30L);
    }

    @Test
    void selectForUpdateReturnsCounterRowForAtomicNumberAllocation() {
        TestSecurityContexts.useUser(
                97002L,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                1L,
                1L,
                "sequence_counter_mapper_user",
                "sequence_counter_mapper_user",
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
        seedCounter(11L);

        SequenceCounterEntity counter = sequenceCounterMapper.selectForUpdate(
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                BIZ_TYPE,
                PERIOD_KEY
        );

        assertThat(counter).isNotNull();
        assertThat(counter.getAccountBookId()).isEqualTo(ACCOUNT_BOOK_ID);
        assertThat(counter.getCurrentValue()).isEqualTo(11L);
    }

    @Test
    void selectMaxCurrentValueReturnsMaximumWithinCompanyAccountBookAndBizType() {
        TestSecurityContexts.useUser(
                97002L,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                1L,
                1L,
                "sequence_counter_mapper_user",
                "sequence_counter_mapper_user",
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
        seedCounter("20260601", 7L);
        seedCounter("20260602", 12L);
        seedCounter(OTHER_ACCOUNT_BOOK_ID, "20260602", 99L);
        seedCounter(COMPANY_ID + 1L, BIZ_TYPE, "20260602", 99L);
        seedCounter(COMPANY_ID, BIZ_TYPE + "_OTHER", "20260602", 88L);

        Long maxCurrentValue = sequenceCounterMapper.selectMaxCurrentValue(COMPANY_ID, ACCOUNT_BOOK_ID, BIZ_TYPE);

        assertThat(maxCurrentValue).isEqualTo(12L);
    }

    private void seedCounter(long currentValue) {
        seedCounter(PERIOD_KEY, currentValue);
    }

    private void seedCounter(String periodKey, long currentValue) {
        seedCounter(ACCOUNT_BOOK_ID, periodKey, currentValue);
    }

    private void seedCounter(long accountBookId, String periodKey, long currentValue) {
        seedCounter(COMPANY_ID, accountBookId, BIZ_TYPE, periodKey, currentValue);
    }

    private void seedCounter(long companyId, String bizType, String periodKey, long currentValue) {
        seedCounter(companyId, ACCOUNT_BOOK_ID, bizType, periodKey, currentValue);
    }

    private void seedCounter(long companyId, long accountBookId, String bizType, String periodKey, long currentValue) {
        SequenceCounterEntity counter = new SequenceCounterEntity();
        counter.setCompanyId(companyId);
        counter.setAccountBookId(accountBookId);
        counter.setBizType(bizType);
        counter.setPeriodKey(periodKey);
        counter.setCurrentValue(currentValue);
        counter.setCreatedBy(97002L);
        counter.setCreatedTime(LocalDateTime.of(2026, 6, 1, 8, 0));
        counter.setUpdatedBy(97002L);
        counter.setUpdatedTime(LocalDateTime.of(2026, 6, 1, 8, 0));
        counter.setVersion(0);
        sequenceCounterMapper.insert(counter);
    }
}
