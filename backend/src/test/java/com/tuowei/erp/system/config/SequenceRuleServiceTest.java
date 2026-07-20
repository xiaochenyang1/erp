package com.tuowei.erp.system.config;

import com.tuowei.erp.system.config.service.SequenceRuleService;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SequenceRuleServiceTest {

    private static final long COMPANY_ID = 98001L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long OTHER_ACCOUNT_BOOK_ID = 2L;
    private static final long RULE_ID = 9800101L;
    private static final long OTHER_RULE_ID = 9800102L;
    private static final String BIZ_TYPE = "RULE_UPDATE_GUARD_DOC";

    @Autowired
    private SequenceRuleService sequenceRuleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from sys_audit_log where business_type = ? and business_no = ?",
                "SEQUENCE_RULE", BIZ_TYPE);
        jdbcTemplate.update("delete from sys_sequence_counter where company_id = ? and biz_type = ?",
                COMPANY_ID, BIZ_TYPE);
        jdbcTemplate.update("delete from sys_sequence_rule where company_id = ? and biz_type = ?",
                COMPANY_ID, BIZ_TYPE);
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void createsRuleInCurrentAccountBook() {
        SequenceRuleResponse response = sequenceRuleService.create(new SequenceRuleCreateRequest(
                BIZ_TYPE,
                "ORD-",
                "yyyyMMdd",
                3,
                0L
        ));

        Assertions.assertThat(response.accountBookId()).isEqualTo(ACCOUNT_BOOK_ID);
        Assertions.assertThat(storedAccountBookId(response.id())).isEqualTo(ACCOUNT_BOOK_ID);
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void listOnlyReturnsRulesFromCurrentAccountBook() {
        seedRule(RULE_ID, ACCOUNT_BOOK_ID, 8L);
        seedRule(OTHER_RULE_ID, OTHER_ACCOUNT_BOOK_ID, 9L);

        PageResponse<SequenceRuleResponse> response = sequenceRuleService.list(null);

        Assertions.assertThat(response.records())
                .extracting(SequenceRuleResponse::id)
                .containsExactly(RULE_ID);
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void getByIdRejectsRuleFromOtherAccountBook() {
        seedRule(OTHER_RULE_ID, OTHER_ACCOUNT_BOOK_ID, 8L);

        Assertions.assertThatThrownBy(() -> sequenceRuleService.getById(OTHER_RULE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("编号规则不存在");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void updateGuardIgnoresGeneratedCountersFromOtherAccountBooks() {
        seedRule(8L);
        seedCounter(OTHER_ACCOUNT_BOOK_ID, "20260601", 999L);

        SequenceRuleResponse response = sequenceRuleService.update(RULE_ID, new SequenceRuleUpdateRequest(
                "ORD-",
                "yyyyMMdd",
                3,
                8L
        ));

        Assertions.assertThat(response.seqLength()).isEqualTo(3);
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void recordsAuditLogWhenCreatingRule() {
        SequenceRuleResponse response = sequenceRuleService.create(new SequenceRuleCreateRequest(
                BIZ_TYPE,
                "ORD-",
                "yyyyMMdd",
                3,
                0L
        ));

        Assertions.assertThat(auditLogCount("CREATE", response.id())).isEqualTo(1);
        Assertions.assertThat(auditSnapshot("CREATE", response.id()))
                .contains("\"bizType\":\"" + BIZ_TYPE + "\"")
                .contains("\"prefix\":\"ORD-\"")
                .contains("\"seqLength\":3")
                .contains("\"currentValue\":0");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void recordsAuditLogWhenUpdatingRule() {
        seedRule(8L);

        sequenceRuleService.update(RULE_ID, new SequenceRuleUpdateRequest(
                "SO-",
                "yyyyMMdd",
                4,
                8L
        ));

        Assertions.assertThat(auditLogCount("UPDATE", RULE_ID)).isEqualTo(1);
        Assertions.assertThat(auditSnapshot("UPDATE", RULE_ID))
                .contains("\"before\"")
                .contains("\"after\"")
                .contains("\"prefix\":\"ORD-\"")
                .contains("\"prefix\":\"SO-\"")
                .contains("\"seqLength\":4");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void recordsAuditLogWhenChangingRuleStatus() {
        seedRule(8L);

        sequenceRuleService.disable(RULE_ID);
        sequenceRuleService.enable(RULE_ID);

        Assertions.assertThat(auditLogCount("DISABLE", RULE_ID)).isEqualTo(1);
        Assertions.assertThat(auditLogCount("ENABLE", RULE_ID)).isEqualTo(1);
        Assertions.assertThat(auditSnapshot("DISABLE", RULE_ID))
                .contains("\"before\"")
                .contains("\"after\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"status\":\"DISABLED\"");
        Assertions.assertThat(auditSnapshot("ENABLE", RULE_ID))
                .contains("\"status\":\"ACTIVE\"");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void rejectsInvalidDatePatternWhenCreatingRule() {
        Assertions.assertThatThrownBy(() -> sequenceRuleService.create(new SequenceRuleCreateRequest(
                        BIZ_TYPE,
                        "ORD-",
                        "yyyy-MM-dd-#",
                        3,
                        0L
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("datePattern不是有效的日期格式");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void rejectsCurrentValueThatExceedsSeqLengthWhenCreatingRule() {
        Assertions.assertThatThrownBy(() -> sequenceRuleService.create(new SequenceRuleCreateRequest(
                        BIZ_TYPE,
                        "ORD-",
                        "yyyyMMdd",
                        3,
                        1000L
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seqLength不能小于当前流水位数");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void rejectsSeqLengthBelowGeneratedCounterMaximumWhenUpdatingRule() {
        seedRule(101L);
        seedCounter("20260601", 101L);

        Assertions.assertThatThrownBy(() -> sequenceRuleService.update(RULE_ID, new SequenceRuleUpdateRequest(
                        "ORD-",
                        "yyyyMMdd",
                        2,
                        101L
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seqLength不能小于已产生的最大流水位数");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void rejectsDatePatternChangeAfterCountersExist() {
        seedRule(8L);
        seedCounter("20260601", 8L);

        Assertions.assertThatThrownBy(() -> sequenceRuleService.update(RULE_ID, new SequenceRuleUpdateRequest(
                        "ORD-",
                        "yyyyMM",
                        3,
                        8L
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已产生编号的规则不能修改日期格式");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void rejectsCurrentValueBelowGeneratedCounterMaximum() {
        seedRule(8L);
        seedCounter("20260601", 8L);
        seedCounter("20260602", 11L);

        Assertions.assertThatThrownBy(() -> sequenceRuleService.update(RULE_ID, new SequenceRuleUpdateRequest(
                        "ORD-",
                        "yyyyMMdd",
                        4,
                        10L
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currentValue不能小于已产生的最大流水");
    }

    @Test
    @WithErpUser(userId = 98002L, companyId = COMPANY_ID, accountBookId = 1L)
    void allowsNonDangerousChangesAfterCountersExist() {
        seedRule(8L);
        seedCounter("20260601", 8L);

        SequenceRuleResponse response = sequenceRuleService.update(RULE_ID, new SequenceRuleUpdateRequest(
                "SO-",
                "yyyyMMdd",
                4,
                8L
        ));

        Assertions.assertThat(response.prefix()).isEqualTo("SO-");
        Assertions.assertThat(response.datePattern()).isEqualTo("yyyyMMdd");
        Assertions.assertThat(response.seqLength()).isEqualTo(4);
        Assertions.assertThat(response.currentValue()).isEqualTo(8L);
    }

    private void seedRule(long currentValue) {
        seedRule(RULE_ID, ACCOUNT_BOOK_ID, currentValue);
    }

    private void seedRule(long id, long accountBookId, long currentValue) {
        jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value, status,
                     created_by, updated_by, version)
                values
                    (?, ?, ?, ?, 'ORD-', 'yyyyMMdd', 3, ?, 'ACTIVE', 98002, 98002, 0)
                """,
                id,
                COMPANY_ID,
                accountBookId,
                BIZ_TYPE,
                currentValue);
    }

    private void seedCounter(String periodKey, long currentValue) {
        seedCounter(ACCOUNT_BOOK_ID, periodKey, currentValue);
    }

    private void seedCounter(long accountBookId, String periodKey, long currentValue) {
        jdbcTemplate.update("""
                insert into sys_sequence_counter
                    (id, company_id, account_book_id, biz_type, period_key, current_value, created_by, updated_by, version)
                values
                    (?, ?, ?, ?, ?, ?, 98002, 98002, 0)
                """,
                9801000L + accountBookId * 100L + Long.parseLong(periodKey.substring(periodKey.length() - 2)),
                COMPANY_ID,
                accountBookId,
                BIZ_TYPE,
                periodKey,
                currentValue);
    }

    private Long storedAccountBookId(Long ruleId) {
        return jdbcTemplate.queryForObject("""
                select account_book_id
                from sys_sequence_rule
                where id = ?
                """, Long.class, ruleId);
    }

    private Integer auditLogCount(String action, Long ruleId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from sys_audit_log
                where company_id = ?
                  and account_book_id = 1
                  and audit_type = 'CONFIG'
                  and business_type = 'SEQUENCE_RULE'
                  and business_id = ?
                  and business_no = ?
                  and action = ?
                  and operator_id = 98002
                """, Integer.class, COMPANY_ID, ruleId, BIZ_TYPE, action);
    }

    private String auditSnapshot(String action, Long ruleId) {
        return jdbcTemplate.queryForObject("""
                select snapshot_json
                from sys_audit_log
                where company_id = ?
                  and account_book_id = 1
                  and audit_type = 'CONFIG'
                  and business_type = 'SEQUENCE_RULE'
                  and business_id = ?
                  and business_no = ?
                  and action = ?
                """, String.class, COMPANY_ID, ruleId, BIZ_TYPE, action);
    }
}
