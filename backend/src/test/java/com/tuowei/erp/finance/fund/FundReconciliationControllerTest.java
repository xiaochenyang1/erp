package com.tuowei.erp.finance.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FundReconciliationControllerTest {

    private static final String FUND_VIEW = "finance:fund:view";
    private static final String FUND_MANAGE = "finance:fund:manage";
    private static final String FUND_RECONCILE = "finance:fund:reconcile";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        deleteIfExists("fin_bank_statement", """
                id between 864000 and 864999
                or remark = 'fund test'
                or matched_biz_id between 864000 and 864999
                or matched_biz_no in ('RC-FUND-001', 'PAY-FUND-001', 'RC-FUND-MISMATCH', 'RC-FUND-DUP')
                """);
        deleteIfExists("fin_fund_account", "id between 864000 and 864999 or account_code like 'FUND_TEST_%' or remark = 'fund test'");
        jdbcTemplate.update("""
                delete from fin_receipt
                where id between 864000 and 864999
                   or receipt_no in ('RC-FUND-001', 'RC-FUND-MISMATCH', 'RC-FUND-DUP')
                   or remark = 'fund test'
                """);
        jdbcTemplate.update("""
                delete from fin_payment
                where id between 864000 and 864999
                   or payment_no = 'PAY-FUND-001'
                   or remark = 'fund test'
                """);
    }

    @Test
    @WithErpUser(authorities = {FUND_MANAGE, FUND_VIEW})
    void createsAndListsFundAccountsAndBankStatements() throws Exception {
        long accountId = createFundAccount("FUND_TEST_001");
        long statementId = createStatement(accountId, "IN", "128.50");

        mockMvc.perform(get("/api/finance/fund/accounts")
                        .param("keyword", "FUND_TEST_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].accountCode").value("FUND_TEST_001"));

        mockMvc.perform(get("/api/finance/fund/statements/{id}", statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementNo").exists())
                .andExpect(jsonPath("$.data.status").value("UNMATCHED"))
                .andExpect(jsonPath("$.data.amount").value(128.50));
    }

    @Test
    @WithErpUser(authorities = {FUND_MANAGE, FUND_VIEW, FUND_RECONCILE})
    void matchesReceiptAndCanUnmatchWithReason() throws Exception {
        long accountId = createFundAccount("FUND_TEST_RECEIPT");
        long statementId = createStatement(accountId, "IN", "300.00");
        seedReceipt(864201L, "RC-FUND-001", "300.00", "POSTED");

        mockMvc.perform(post("/api/finance/fund/statements/{id}/match", statementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bizType": "RECEIPT",
                                  "bizId": 864201
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.matchedBizType").value("RECEIPT"))
                .andExpect(jsonPath("$.data.matchedBizNo").value("RC-FUND-001"));

        mockMvc.perform(post("/api/finance/fund/statements/{id}/unmatch", statementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "银行流水录入错误"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNMATCHED"))
                .andExpect(jsonPath("$.data.matchedBizType").doesNotExist())
                .andExpect(jsonPath("$.data.unmatchReason").value("银行流水录入错误"));
    }

    @Test
    @WithErpUser(authorities = {FUND_MANAGE, FUND_RECONCILE})
    void matchesPaymentAndRejectsWrongDirectionOrAmountMismatch() throws Exception {
        long accountId = createFundAccount("FUND_TEST_PAYMENT");
        long outStatementId = createStatement(accountId, "OUT", "88.00");
        long inStatementId = createStatement(accountId, "IN", "88.00");
        seedPayment(864301L, "PAY-FUND-001", "88.00", "POSTED");
        seedReceipt(864302L, "RC-FUND-MISMATCH", "90.00", "POSTED");

        mockMvc.perform(post("/api/finance/fund/statements/{id}/match", outStatementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bizType": "PAYMENT",
                                  "bizId": 864301
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.matchedBizNo").value("PAY-FUND-001"));

        matchExpectingBadRequest(inStatementId, "PAYMENT", 864301L, "收入流水只能匹配收款单");
        matchExpectingBadRequest(inStatementId, "RECEIPT", 864302L, "银行流水金额与业务单据金额不一致");
    }

    @Test
    @WithErpUser(authorities = {FUND_MANAGE, FUND_RECONCILE})
    void preventsDuplicateBusinessMatch() throws Exception {
        long accountId = createFundAccount("FUND_TEST_DUP");
        long firstStatementId = createStatement(accountId, "IN", "50.00");
        long secondStatementId = createStatement(accountId, "IN", "50.00");
        seedReceipt(864401L, "RC-FUND-DUP", "50.00", "POSTED");

        mockMvc.perform(post("/api/finance/fund/statements/{id}/match", firstStatementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bizType": "RECEIPT",
                                  "bizId": 864401
                                }
                                """))
                .andExpect(status().isOk());

        matchExpectingBadRequest(secondStatementId, "RECEIPT", 864401L, "业务单据已匹配银行流水");
    }

    @Test
    @WithErpUser(authorities = {FUND_VIEW})
    void manageEndpointsRequireManagePermission() throws Exception {
        mockMvc.perform(post("/api/finance/fund/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAccountBody("FUND_TEST_FORBIDDEN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));
    }

    private long createFundAccount(String accountCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/finance/fund/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAccountBody(accountCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCode").value(accountCode))
                .andReturn();
        return extractId(result);
    }

    private long createStatement(long accountId, String direction, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/finance/fund/statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fundAccountId": %d,
                                  "externalTxnNo": "EXT-%s-%s",
                                  "transactionDate": "2034-05-10",
                                  "direction": "%s",
                                  "amount": %s,
                                  "counterpartyName": "测试往来方",
                                  "summary": "测试银行流水",
                                  "remark": "fund test"
                                }
                                """.formatted(accountId, direction, amount.replace(".", ""), direction, amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.direction").value(direction))
                .andExpect(jsonPath("$.data.status").value("UNMATCHED"))
                .andReturn();
        return extractId(result);
    }

    private static String validAccountBody(String accountCode) {
        return """
                {
                  "accountCode": "%s",
                  "accountName": "测试银行账户",
                  "accountType": "BANK",
                  "bankName": "测试银行",
                  "bankAccountNo": "6222000000000001",
                  "currencyCode": "CNY",
                  "openingBalance": 0.00,
                  "remark": "fund test"
                }
                """.formatted(accountCode);
    }

    private void matchExpectingBadRequest(long statementId, String bizType, long bizId, String message) throws Exception {
        mockMvc.perform(post("/api/finance/fund/statements/{id}/match", statementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bizType": "%s",
                                  "bizId": %d
                                }
                                """.formatted(bizType, bizId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
    }

    private long extractId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private void seedReceipt(long id, String receiptNo, String amount, String status) {
        jdbcTemplate.update("""
                insert into fin_receipt
                (id, company_id, account_book_id, receipt_no, customer_id, receipt_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 5001, ?, ?, ?, ?, 0, 'fund test', 0, ?, 0, ?, 0)
                """,
                id,
                receiptNo,
                LocalDate.of(2034, 5, 10),
                new BigDecimal(amount),
                new BigDecimal(amount),
                status,
                LocalDateTime.of(2034, 5, 10, 9, 0),
                LocalDateTime.of(2034, 5, 10, 9, 0));
    }

    private void seedPayment(long id, String paymentNo, String amount, String status) {
        jdbcTemplate.update("""
                insert into fin_payment
                (id, company_id, account_book_id, payment_no, supplier_id, payment_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 5002, ?, ?, ?, ?, 0, 'fund test', 0, ?, 0, ?, 0)
                """,
                id,
                paymentNo,
                LocalDate.of(2034, 5, 10),
                new BigDecimal(amount),
                new BigDecimal(amount),
                status,
                LocalDateTime.of(2034, 5, 10, 9, 0),
                LocalDateTime.of(2034, 5, 10, 9, 0));
    }

    private void deleteIfExists(String tableName, String condition) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select count(*) > 0
                from information_schema.tables
                where table_schema = database()
                  and lower(table_name) = lower(?)
                """, Boolean.class, tableName);
        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.update("delete from " + tableName + " where " + condition);
        }
    }
}
