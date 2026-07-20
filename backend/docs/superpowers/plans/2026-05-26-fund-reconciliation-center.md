# Fund Reconciliation Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend-only fund account and bank statement reconciliation center that links bank-side cash evidence to posted receipts and payments, and blocks period close when statements remain unmatched.

**Architecture:** Add a focused `finance/fund` module with two tables: fund account and bank statement. Keep bank statement reconciliation separate from AR/AP settlement facts: matching a statement records cash evidence only, while receipts, payments, receivables, payables, and vouchers remain unchanged. Extend period close checks so unmatched bank statements inside the period prevent locking or closing.

**Tech Stack:** Spring Boot 3.5.x, Spring Security `@PreAuthorize`, MyBatis-Plus, Flyway SQL migrations, H2/MySQL-compatible schema, MockMvc, JUnit 5, Java 17.

---

## File Map

**Create**

- `src/main/resources/db/migration/V53__finance_fund_reconciliation_schema.sql`: fund account and bank statement tables, indexes, sequence rule, menu permissions.
- `src/main/java/com/tuowei/erp/finance/fund/controller/FundController.java`: REST API for accounts, statements, match, and unmatch.
- `src/main/java/com/tuowei/erp/finance/fund/service/FundService.java`: tenant filtering, account creation, statement creation, matching rules, unmatch rules.
- `src/main/java/com/tuowei/erp/finance/fund/service/FundStatementNumberService.java`: statement number generation via `SequenceNumberGenerator`.
- `src/main/java/com/tuowei/erp/finance/fund/mapper/FundAccountMapper.java`
- `src/main/java/com/tuowei/erp/finance/fund/mapper/BankStatementMapper.java`
- `src/main/java/com/tuowei/erp/finance/fund/model/FundAccountEntity.java`
- `src/main/java/com/tuowei/erp/finance/fund/model/BankStatementEntity.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/FundAccountCreateRequest.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/FundAccountPageQuery.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/FundAccountResponse.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/BankStatementCreateRequest.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/BankStatementPageQuery.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/BankStatementResponse.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/BankStatementMatchRequest.java`
- `src/main/java/com/tuowei/erp/finance/fund/web/BankStatementUnmatchRequest.java`
- `src/test/java/com/tuowei/erp/finance/fund/FundReconciliationControllerTest.java`

**Modify**

- `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`: add `fin_fund_account` and `fin_bank_statement` to tenant plugin tables.
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`: add fund permissions and `HAS_` expressions.
- `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java`: add unmatched bank statement close-check issue.
- `src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java`: cover unmatched statement period close blocking.

## Task 1: Schema, Permissions, Tenant Registration

**Files:**

- Create: `src/main/resources/db/migration/V53__finance_fund_reconciliation_schema.sql`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Test: `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

- [ ] **Step 1: Write the migration**

Create `V53__finance_fund_reconciliation_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS fin_fund_account (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    bank_name VARCHAR(128),
    bank_account_no VARCHAR(128),
    currency_code VARCHAR(16) NOT NULL DEFAULT 'CNY',
    opening_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fin_fund_account_type CHECK (account_type IN ('BANK', 'CASH')),
    CONSTRAINT chk_fin_fund_account_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS fin_bank_statement (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    fund_account_id BIGINT NOT NULL,
    statement_no VARCHAR(64) NOT NULL,
    external_txn_no VARCHAR(128),
    transaction_date DATE NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    counterparty_name VARCHAR(128),
    summary VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED',
    matched_biz_type VARCHAR(32),
    matched_biz_id BIGINT,
    matched_biz_no VARCHAR(64),
    matched_time TIMESTAMP,
    matched_by BIGINT,
    unmatch_reason VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fin_bank_statement_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_fin_bank_statement_status CHECK (status IN ('UNMATCHED', 'MATCHED', 'CANCELLED')),
    CONSTRAINT chk_fin_bank_statement_biz_type CHECK (matched_biz_type IS NULL OR matched_biz_type IN ('RECEIPT', 'PAYMENT'))
);

CREATE UNIQUE INDEX uk_fin_fund_account_code
    ON fin_fund_account (company_id, account_book_id, account_code);
CREATE INDEX idx_fin_fund_account_status_type
    ON fin_fund_account (company_id, account_book_id, status, account_type);

CREATE UNIQUE INDEX uk_fin_bank_statement_no
    ON fin_bank_statement (company_id, account_book_id, statement_no);
CREATE INDEX idx_fin_bank_statement_account_date
    ON fin_bank_statement (company_id, account_book_id, fund_account_id, transaction_date);
CREATE INDEX idx_fin_bank_statement_status_date
    ON fin_bank_statement (company_id, account_book_id, status, transaction_date);
CREATE INDEX idx_fin_bank_statement_biz
    ON fin_bank_statement (company_id, account_book_id, matched_biz_type, matched_biz_id);
CREATE INDEX idx_fin_bank_statement_external
    ON fin_bank_statement (company_id, account_book_id, external_txn_no);

INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2013, 'FIN_BANK_STATEMENT', 'BS', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5041, 5030, 'MENU', 'FINANCE_FUND', '资金对账', '/finance/funds',
     'finance/fund/index', 'finance:fund:view', 8, 1, 'ACTIVE', 0, 0, 0, 0),
    (5042, 5041, 'BUTTON', 'FINANCE_FUND_MANAGE', '维护资金流水', NULL,
     NULL, 'finance:fund:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5043, 5041, 'BUTTON', 'FINANCE_FUND_RECONCILE', '资金对账', NULL,
     NULL, 'finance:fund:reconcile', 2, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7129, 3002, 5041, 0),
    (7130, 3002, 5042, 0),
    (7131, 3002, 5043, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

- [ ] **Step 2: Add permission constants**

Modify `PermissionCodes.java` near finance permissions:

```java
public static final String FINANCE_FUND_VIEW = "finance:fund:view";
public static final String FINANCE_FUND_MANAGE = "finance:fund:manage";
public static final String FINANCE_FUND_RECONCILE = "finance:fund:reconcile";
```

Modify the `HAS_` section near other finance permissions:

```java
public static final String HAS_FINANCE_FUND_VIEW = "hasAuthority('" + FINANCE_FUND_VIEW + "')";
public static final String HAS_FINANCE_FUND_MANAGE = "hasAuthority('" + FINANCE_FUND_MANAGE + "')";
public static final String HAS_FINANCE_FUND_RECONCILE = "hasAuthority('" + FINANCE_FUND_RECONCILE + "')";
```

- [ ] **Step 3: Register tenant tables**

Modify `MybatisPlusConfig.java` `TENANT_TABLES` and place the new tables near other `fin_*` tables:

```java
"fin_fund_account",
"fin_bank_statement",
```

- [ ] **Step 4: Run migration smoke test**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 5: Commit schema and permissions**

Run:

```powershell
git add src/main/resources/db/migration/V53__finance_fund_reconciliation_schema.sql src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java src/main/java/com/tuowei/erp/common/security/PermissionCodes.java
git commit -m "feat: add fund reconciliation schema"
```

## Task 2: Models, Mappers, DTOs, and Number Service

**Files:**

- Create all `finance/fund/model`, `mapper`, `web`, and `FundStatementNumberService` files listed in File Map.
- Test: compile through focused Flyway smoke test.

- [ ] **Step 1: Create fund account entity**

Create `src/main/java/com/tuowei/erp/finance/fund/model/FundAccountEntity.java`:

```java
package com.tuowei.erp.finance.fund.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("fin_fund_account")
public class FundAccountEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private String bankName;
    private String bankAccountNo;
    private String currencyCode;
    private BigDecimal openingBalance;
    private String status;
    private Integer deletedFlag;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getAccountBookId() { return accountBookId; }
    public void setAccountBookId(Long accountBookId) { this.accountBookId = accountBookId; }
    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankAccountNo() { return bankAccountNo; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(Integer deletedFlag) { this.deletedFlag = deletedFlag; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
```

- [ ] **Step 2: Create bank statement entity**

Create `src/main/java/com/tuowei/erp/finance/fund/model/BankStatementEntity.java` with all columns from `fin_bank_statement`, including:

```java
@TableName("fin_bank_statement")
public class BankStatementEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long fundAccountId;
    private String statementNo;
    private String externalTxnNo;
    private LocalDate transactionDate;
    private String direction;
    private BigDecimal amount;
    private String counterpartyName;
    private String summary;
    private String status;
    private String matchedBizType;
    private Long matchedBizId;
    private String matchedBizNo;
    private LocalDateTime matchedTime;
    private Long matchedBy;
    private String unmatchReason;
    private Integer deletedFlag;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer version;
}
```

Add explicit getters and setters for every field. Do not use Lombok.

- [ ] **Step 3: Create mappers**

Create `FundAccountMapper.java`:

```java
package com.tuowei.erp.finance.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.finance.fund.model.FundAccountEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FundAccountMapper extends BaseMapper<FundAccountEntity> {
}
```

Create `BankStatementMapper.java`:

```java
package com.tuowei.erp.finance.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.finance.fund.model.BankStatementEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BankStatementMapper extends BaseMapper<BankStatementEntity> {
}
```

- [ ] **Step 4: Create request DTOs**

Create these records:

```java
public record FundAccountCreateRequest(
        String accountCode,
        String accountName,
        String accountType,
        String bankName,
        String bankAccountNo,
        String currencyCode,
        BigDecimal openingBalance,
        String remark
) {
}
```

```java
public record BankStatementCreateRequest(
        Long fundAccountId,
        String externalTxnNo,
        LocalDate transactionDate,
        String direction,
        BigDecimal amount,
        String counterpartyName,
        String summary,
        String remark
) {
}
```

```java
public record BankStatementMatchRequest(
        String bizType,
        Long bizId,
        String remark
) {
}
```

```java
public record BankStatementUnmatchRequest(String reason) {
}
```

- [ ] **Step 5: Create query DTO JavaBeans**

Create `FundAccountPageQuery` with fields and getters/setters:

```java
private String accountType;
private String status;
private String keyword;
private Integer pageNo;
private Integer pageSize;
```

Create `BankStatementPageQuery` with fields and getters/setters:

```java
private Long fundAccountId;
private String direction;
private String status;
private LocalDate transactionDateFrom;
private LocalDate transactionDateTo;
private String matchedBizType;
private String matchedBizNo;
private Integer pageNo;
private Integer pageSize;
```

- [ ] **Step 6: Create response DTOs**

Create:

```java
public record FundAccountResponse(
        Long id,
        String accountCode,
        String accountName,
        String accountType,
        String bankName,
        String bankAccountNo,
        String currencyCode,
        BigDecimal openingBalance,
        String status,
        String remark,
        LocalDateTime createdTime
) {
}
```

```java
public record BankStatementResponse(
        Long id,
        Long fundAccountId,
        String statementNo,
        String externalTxnNo,
        LocalDate transactionDate,
        String direction,
        BigDecimal amount,
        String counterpartyName,
        String summary,
        String status,
        String matchedBizType,
        Long matchedBizId,
        String matchedBizNo,
        LocalDateTime matchedTime,
        Long matchedBy,
        String unmatchReason,
        String remark,
        LocalDateTime createdTime
) {
}
```

- [ ] **Step 7: Create statement number service**

Create `FundStatementNumberService.java`:

```java
package com.tuowei.erp.finance.fund.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class FundStatementNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public FundStatementNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextStatementNo(LocalDate transactionDate) {
        return sequenceNumberGenerator.nextNumber("FIN_BANK_STATEMENT", "银行流水", transactionDate);
    }
}
```

- [ ] **Step 8: Compile**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test
```

Expected: compile succeeds and smoke test passes.

- [ ] **Step 9: Commit models and DTOs**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/fund
git commit -m "feat: add fund reconciliation models"
```

## Task 3: Write Failing Controller Tests

**Files:**

- Create: `src/test/java/com/tuowei/erp/finance/fund/FundReconciliationControllerTest.java`

- [ ] **Step 1: Create controller test skeleton**

Create the test class:

```java
package com.tuowei.erp.finance.fund;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_bank_statement where id between 890000 and 890999 or statement_no like 'BS209805%'");
        jdbcTemplate.update("delete from fin_fund_account where id between 890000 and 890999 or account_code like 'FUND_TEST_%'");
        jdbcTemplate.update("delete from fin_receipt where id between 890000 and 890999");
        jdbcTemplate.update("delete from fin_payment where id between 890000 and 890999");
    }
}
```

- [ ] **Step 2: Add happy-path account and statement test**

Add:

```java
@Test
@WithErpUser(authorities = {FUND_VIEW, FUND_MANAGE})
void createsFundAccountAndIncomeStatement() throws Exception {
    long accountId = createFundAccount();

    mockMvc.perform(get("/api/finance/fund/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountCode").value("FUND_TEST_001"))
            .andExpect(jsonPath("$.data.status").value("ENABLED"));

    MvcResult result = mockMvc.perform(post("/api/finance/fund/statements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "fundAccountId": %d,
                              "externalTxnNo": "EXT-IN-890001",
                              "transactionDate": "2098-05-10",
                              "direction": "IN",
                              "amount": 100.00,
                              "counterpartyName": "测试客户",
                              "summary": "客户回款",
                              "remark": "fund test"
                            }
                            """.formatted(accountId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.direction").value("IN"))
            .andExpect(jsonPath("$.data.status").value("UNMATCHED"))
            .andExpect(jsonPath("$.data.amount").value(100.00))
            .andReturn();

    long statementId = extractId(result);

    mockMvc.perform(get("/api/finance/fund/statements/{id}", statementId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalTxnNo").value("EXT-IN-890001"));
}
```

Add this helper for extracting `$.data.id` from a response:

```java
private long extractId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();
}
```

- [ ] **Step 3: Add receipt matching test**

Add:

```java
@Test
@WithErpUser(authorities = {FUND_VIEW, FUND_MANAGE, FUND_RECONCILE})
void matchesIncomeStatementToPostedReceiptAndUnmatches() throws Exception {
    long accountId = createFundAccount();
    long statementId = createStatement(accountId, "IN", "100.00");
    seedReceipt(890101L, "FR-890101", "POSTED", "100.00", 1L, 1L);

    mockMvc.perform(post("/api/finance/fund/statements/{id}/match", statementId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "bizType": "RECEIPT",
                              "bizId": 890101,
                              "remark": "match receipt"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MATCHED"))
            .andExpect(jsonPath("$.data.matchedBizType").value("RECEIPT"))
            .andExpect(jsonPath("$.data.matchedBizNo").value("FR-890101"));

    mockMvc.perform(post("/api/finance/fund/statements/{id}/unmatch", statementId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"银行流水匹配错误\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNMATCHED"))
            .andExpect(jsonPath("$.data.matchedBizType").isEmpty());
}
```

- [ ] **Step 4: Add payment matching test**

Add:

```java
@Test
@WithErpUser(authorities = {FUND_VIEW, FUND_MANAGE, FUND_RECONCILE})
void matchesOutcomeStatementToPostedPayment() throws Exception {
    long accountId = createFundAccount();
    long statementId = createStatement(accountId, "OUT", "80.00");
    seedPayment(890201L, "FP-890201", "POSTED", "80.00", 1L, 1L);

    mockMvc.perform(post("/api/finance/fund/statements/{id}/match", statementId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "bizType": "PAYMENT",
                              "bizId": 890201,
                              "remark": "match payment"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MATCHED"))
            .andExpect(jsonPath("$.data.matchedBizType").value("PAYMENT"))
            .andExpect(jsonPath("$.data.matchedBizNo").value("FP-890201"));
}
```

- [ ] **Step 5: Add validation tests**

Add one test method:

```java
@Test
@WithErpUser(authorities = {FUND_VIEW, FUND_MANAGE, FUND_RECONCILE})
void rejectsDirectionAmountCancelledAndDuplicateMatch() throws Exception {
    long accountId = createFundAccount();
    long statementId = createStatement(accountId, "IN", "100.00");
    seedReceipt(890301L, "FR-890301", "POSTED", "99.00", 1L, 1L);
    seedPayment(890302L, "FP-890302", "POSTED", "100.00", 1L, 1L);
    seedReceipt(890303L, "FR-890303", "CANCELLED", "100.00", 1L, 1L);
    seedReceipt(890304L, "FR-890304", "POSTED", "100.00", 1L, 1L);

    matchExpectingBadRequest(statementId, "RECEIPT", 890301L, "银行流水金额与业务单据金额不一致");
    matchExpectingBadRequest(statementId, "PAYMENT", 890302L, "收入流水只能匹配收款单");
    matchExpectingBadRequest(statementId, "RECEIPT", 890303L, "只有已过账收款单可以匹配");

    mockMvc.perform(post("/api/finance/fund/statements/{id}/match", statementId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bizType\":\"RECEIPT\",\"bizId\":890304}"))
            .andExpect(status().isOk());

    matchExpectingBadRequest(statementId, "RECEIPT", 890304L, "银行流水已匹配，不能重复匹配");
}
```

- [ ] **Step 6: Add tenant and permission tests**

Add:

```java
@Test
@WithErpUser(companyId = 1L, accountBookId = 1L, authorities = {FUND_VIEW, FUND_MANAGE, FUND_RECONCILE})
void tenantIsolationPreventsCrossCompanyAccessAndMatch() throws Exception {
    long accountId = createFundAccount();
    long statementId = createStatement(accountId, "IN", "100.00");
    seedReceipt(890401L, "FR-890401", "POSTED", "100.00", 2L, 1L);

    matchExpectingBadRequest(statementId, "RECEIPT", 890401L, "收款单不存在");
}

@Test
@WithErpUser(authorities = {FUND_VIEW})
void userWithoutManagePermissionGetsForbidden() throws Exception {
    mockMvc.perform(post("/api/finance/fund/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "accountCode": "FUND_TEST_FORBIDDEN",
                              "accountName": "Forbidden",
                              "accountType": "BANK"
                            }
                            """))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 7: Add seed helpers**

Add helpers with deterministic IDs:

```java
private void seedReceipt(long id, String receiptNo, String status, String amount, long companyId, long accountBookId) {
    jdbcTemplate.update("""
            insert into fin_receipt
            (id, company_id, account_book_id, receipt_no, customer_id, receipt_date, amount, allocated_amount,
             status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
            values (?, ?, ?, ?, 8601, '2098-05-10', ?, ?, ?, 0, 'fund test',
                    0, ?, 0, ?, 0)
            """,
            id,
            companyId,
            accountBookId,
            receiptNo,
            new BigDecimal(amount),
            new BigDecimal(amount),
            status,
            LocalDateTime.of(2098, 5, 10, 9, 0),
            LocalDateTime.of(2098, 5, 10, 9, 0));
}

private void seedPayment(long id, String paymentNo, String status, String amount, long companyId, long accountBookId) {
    jdbcTemplate.update("""
            insert into fin_payment
            (id, company_id, account_book_id, payment_no, supplier_id, payment_date, amount, allocated_amount,
             status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
            values (?, ?, ?, ?, 8701, '2098-05-10', ?, ?, ?, 0, 'fund test',
                    0, ?, 0, ?, 0)
            """,
            id,
            companyId,
            accountBookId,
            paymentNo,
            new BigDecimal(amount),
            new BigDecimal(amount),
            status,
            LocalDateTime.of(2098, 5, 10, 9, 0),
            LocalDateTime.of(2098, 5, 10, 9, 0));
}
```

Add request helpers used by the tests:

```java
private long createFundAccount() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/finance/fund/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "accountCode": "FUND_TEST_001",
                              "accountName": "测试银行账户",
                              "accountType": "BANK",
                              "bankName": "测试银行",
                              "bankAccountNo": "6222000000000001",
                              "currencyCode": "CNY",
                              "openingBalance": 0.00,
                              "remark": "fund test"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountCode").value("FUND_TEST_001"))
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
                              "transactionDate": "2098-05-10",
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
```

- [ ] **Step 8: Run focused test and confirm RED**

Run:

```powershell
.\mvnw.cmd "-Dtest=FundReconciliationControllerTest" test
```

Expected before implementation:

```text
BUILD FAILURE
```

Acceptable failure reasons are missing `FundController` routes, missing service beans, or missing classes. If the test fails because the test itself does not compile, fix the test imports or helper methods before implementing production code.

- [ ] **Step 9: Commit failing tests only if the project policy allows RED commits**

If committing RED tests is acceptable in the current branch:

```powershell
git add src/test/java/com/tuowei/erp/finance/fund/FundReconciliationControllerTest.java
git commit -m "test: cover fund reconciliation center"
```

If RED commits are not desired, keep the test uncommitted and continue to Task 4. Do not weaken assertions.

## Task 4: Implement Fund Service and REST APIs

**Files:**

- Create: `src/main/java/com/tuowei/erp/finance/fund/service/FundService.java`
- Create: `src/main/java/com/tuowei/erp/finance/fund/controller/FundController.java`
- Modify if needed: DTOs from Task 2
- Test: `FundReconciliationControllerTest`

- [ ] **Step 1: Implement service skeleton and constants**

Create `FundService`:

```java
@Service
public class FundService {
    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);
    private static final String ACCOUNT_ENABLED = "ENABLED";
    private static final String STATEMENT_UNMATCHED = "UNMATCHED";
    private static final String STATEMENT_MATCHED = "MATCHED";
    private static final String BIZ_RECEIPT = "RECEIPT";
    private static final String BIZ_PAYMENT = "PAYMENT";

    private final FundAccountMapper fundAccountMapper;
    private final BankStatementMapper bankStatementMapper;
    private final ReceiptMapper receiptMapper;
    private final PaymentMapper paymentMapper;
    private final FundStatementNumberService statementNumberService;
    private final AuditMetadataFactory auditMetadataFactory;

    public FundService(
            FundAccountMapper fundAccountMapper,
            BankStatementMapper bankStatementMapper,
            ReceiptMapper receiptMapper,
            PaymentMapper paymentMapper,
            FundStatementNumberService statementNumberService,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.fundAccountMapper = fundAccountMapper;
        this.bankStatementMapper = bankStatementMapper;
        this.receiptMapper = receiptMapper;
        this.paymentMapper = paymentMapper;
        this.statementNumberService = statementNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
    }
}
```

- [ ] **Step 2: Implement account create/list/detail**

Add:

```java
@Transactional
public FundAccountResponse createAccount(FundAccountCreateRequest request)

public PageResponse<FundAccountResponse> listAccounts(FundAccountPageQuery query)

public FundAccountResponse accountDetail(Long id)
```

Rules:

- `accountCode`, `accountName`, and `accountType` are required.
- `accountType` must be `BANK` or `CASH`.
- `currencyCode` defaults to `CNY`.
- `openingBalance` uses `ScalePrecision.amount`; null becomes zero.
- New accounts have `status = ENABLED` and `deletedFlag = 0`.
- Queries filter `companyId`, `accountBookId`, and `deletedFlag = 0`.

- [ ] **Step 3: Implement statement create/list/detail**

Add:

```java
@Transactional
public BankStatementResponse createStatement(BankStatementCreateRequest request)

public PageResponse<BankStatementResponse> listStatements(BankStatementPageQuery query)

public BankStatementResponse statementDetail(Long id)
```

Rules:

- Fund account must exist in current tenant and account book.
- Disabled account rejects new statements with `资金账户已停用，不能录入流水`.
- `transactionDate`, `direction`, `amount`, and `summary` are required.
- `direction` must be `IN` or `OUT`.
- `amount` must be greater than zero.
- `statementNo` comes from `FundStatementNumberService.nextStatementNo(transactionDate)`.
- New statements have `status = UNMATCHED` and no matched business fields.

- [ ] **Step 4: Implement statement matching**

Add:

```java
@Transactional
public BankStatementResponse matchStatement(Long id, BankStatementMatchRequest request)
```

Rules:

- Statement must exist in current tenant and account book.
- Statement status must be `UNMATCHED`; if `MATCHED`, throw `银行流水已匹配，不能重复匹配`.
- `IN` can only match `RECEIPT`; otherwise throw `收入流水只能匹配收款单`.
- `OUT` can only match `PAYMENT`; otherwise throw `支出流水只能匹配付款单`.
- Receipt must exist in current tenant and account book, status `POSTED`, and amount equal to statement amount.
- Payment must exist in current tenant and account book, status `POSTED`, and amount equal to statement amount.
- If another non-deleted statement already matches the same business type and ID, throw `业务单据已匹配银行流水`.
- On success, set `status = MATCHED`, `matchedBizType`, `matchedBizId`, `matchedBizNo`, `matchedBy`, `matchedTime`, `updatedBy`, `updatedTime`.
- Do not update receipt, payment, receivable, payable, or voucher tables.

- [ ] **Step 5: Implement statement unmatch**

Add:

```java
@Transactional
public BankStatementResponse unmatchStatement(Long id, BankStatementUnmatchRequest request)
```

Rules:

- Statement must exist in current tenant and account book.
- Statement status must be `MATCHED`; otherwise throw `只有已匹配银行流水可以取消匹配`.
- `reason` is required.
- Set `status = UNMATCHED`.
- Clear `matchedBizType`, `matchedBizId`, `matchedBizNo`, `matchedTime`, `matchedBy`.
- Store trimmed reason in `unmatchReason`.

- [ ] **Step 6: Add validation and response helpers**

Add private helpers:

```java
private FundAccountEntity requireAccount(Long id, AuditMetadata audit)
private BankStatementEntity requireStatement(Long id, AuditMetadata audit)
private ReceiptEntity requireReceipt(Long id, AuditMetadata audit)
private PaymentEntity requirePayment(Long id, AuditMetadata audit)
private String normalizeRequired(String value, String message)
private String normalizeCode(String value, String message)
private void ensurePositive(BigDecimal amount, String message)
private void setAudit(FundAccountEntity entity, AuditMetadata audit, LocalDateTime now)
private void setAudit(BankStatementEntity entity, AuditMetadata audit, LocalDateTime now)
private FundAccountResponse toAccountResponse(FundAccountEntity entity)
private BankStatementResponse toStatementResponse(BankStatementEntity entity)
```

- [ ] **Step 7: Implement controller**

Create `FundController`:

```java
@RestController
@RequestMapping("/api/finance/fund")
public class FundController {
    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_MANAGE)
    @PostMapping("/accounts")
    public ApiResponse<FundAccountResponse> createAccount(@RequestBody FundAccountCreateRequest request) {
        return ApiResponse.success(fundService.createAccount(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/accounts")
    public ApiResponse<PageResponse<FundAccountResponse>> listAccounts(FundAccountPageQuery query) {
        return ApiResponse.success(fundService.listAccounts(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/accounts/{id}")
    public ApiResponse<FundAccountResponse> accountDetail(@PathVariable Long id) {
        return ApiResponse.success(fundService.accountDetail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_MANAGE)
    @PostMapping("/statements")
    public ApiResponse<BankStatementResponse> createStatement(@RequestBody BankStatementCreateRequest request) {
        return ApiResponse.success(fundService.createStatement(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/statements")
    public ApiResponse<PageResponse<BankStatementResponse>> listStatements(BankStatementPageQuery query) {
        return ApiResponse.success(fundService.listStatements(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/statements/{id}")
    public ApiResponse<BankStatementResponse> statementDetail(@PathVariable Long id) {
        return ApiResponse.success(fundService.statementDetail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_RECONCILE)
    @PostMapping("/statements/{id}/match")
    public ApiResponse<BankStatementResponse> matchStatement(@PathVariable Long id, @RequestBody BankStatementMatchRequest request) {
        return ApiResponse.success(fundService.matchStatement(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_RECONCILE)
    @PostMapping("/statements/{id}/unmatch")
    public ApiResponse<BankStatementResponse> unmatchStatement(@PathVariable Long id, @RequestBody BankStatementUnmatchRequest request) {
        return ApiResponse.success(fundService.unmatchStatement(id, request));
    }
}
```

- [ ] **Step 8: Run focused fund tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=FundReconciliationControllerTest" test
```

Expected:

```text
Tests run: 6, Failures: 0, Errors: 0
BUILD SUCCESS
```

- [ ] **Step 9: Commit service and API**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/fund src/test/java/com/tuowei/erp/finance/fund/FundReconciliationControllerTest.java
git commit -m "feat: add fund reconciliation APIs"
```

## Task 5: Period Close Check Integration

**Files:**

- Modify: `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java`
- Modify: `src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java`

- [ ] **Step 1: Add failing period close test**

Modify `AccountPeriodControllerTest.cleanup()`:

```java
jdbcTemplate.update("delete from fin_bank_statement where id between 863000 and 863999");
jdbcTemplate.update("delete from fin_fund_account where id between 863000 and 863999");
```

Add test:

```java
@Test
@WithErpUser(authorities = {PERIOD_CLOSE, PERIOD_VIEW})
void closeCheckReportsUnmatchedBankStatementsAndBlocksLock() throws Exception {
    seedPeriod(863701L, 2097, "2097-07", "OPEN");
    seedFundAccount(863801L);
    seedBankStatement(863901L, 863801L, "UNMATCHED");

    mockMvc.perform(get("/api/finance/periods/{id}/close-check", 863701L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.passed").value(false))
            .andExpect(jsonPath("$.data.issues[0].type").value("BANK_STATEMENT_UNMATCHED"))
            .andExpect(jsonPath("$.data.issues[0].message").value("存在未匹配银行流水"));

    mockMvc.perform(post("/api/finance/periods/{id}/lock", 863701L))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("期间月结检查未通过，不能锁定"));
}
```

Add helpers:

```java
private void seedFundAccount(long id) {
    jdbcTemplate.update("""
            insert into fin_fund_account
            (id, company_id, account_book_id, account_code, account_name, account_type, currency_code,
             opening_balance, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
            values (?, 1, 1, ?, '月结测试账户', 'BANK', 'CNY',
                    0, 'ENABLED', 0, 'period close fund test', 0, ?, 0, ?, 0)
            """,
            id,
            "PERIOD_FUND_" + id,
            LocalDateTime.of(2026, 5, 26, 9, 0),
            LocalDateTime.of(2026, 5, 26, 9, 0));
}

private void seedBankStatement(long id, long accountId, String status) {
    jdbcTemplate.update("""
            insert into fin_bank_statement
            (id, company_id, account_book_id, fund_account_id, statement_no, external_txn_no,
             transaction_date, direction, amount, counterparty_name, summary, status,
             deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
            values (?, 1, 1, ?, ?, ?, '2097-07-12', 'IN', 100.00, '月结客户',
                    '未匹配银行流水', ?, 0, 'period close fund test', 0, ?, 0, ?, 0)
            """,
            id,
            accountId,
            "BS-" + id,
            "EXT-" + id,
            status,
            LocalDateTime.of(2026, 5, 26, 9, 0),
            LocalDateTime.of(2026, 5, 26, 9, 0));
}
```

- [ ] **Step 2: Run period test and confirm RED**

Run:

```powershell
.\mvnw.cmd "-Dtest=AccountPeriodControllerTest#closeCheckReportsUnmatchedBankStatementsAndBlocksLock" test
```

Expected before implementation:

```text
BUILD FAILURE
```

The expected failure is that the close-check does not report `BANK_STATEMENT_UNMATCHED`.

- [ ] **Step 3: Implement unmatched bank statement check**

Modify `AccountPeriodCloseChecker.check(Long periodId)` and call the new method before returning:

```java
checkUnmatchedBankStatements(period, issues);
```

Add:

```java
private void checkUnmatchedBankStatements(AccountPeriodEntity period, List<AccountPeriodCloseIssueResponse> issues) {
    Long unmatchedCount = jdbcTemplate.queryForObject("""
            select count(*)
            from fin_bank_statement
            where company_id = ?
              and account_book_id = ?
              and deleted_flag = 0
              and status = 'UNMATCHED'
              and transaction_date >= ?
              and transaction_date <= ?
            """, Long.class, period.getCompanyId(), period.getAccountBookId(), period.getStartDate(), period.getEndDate());
    if (unmatchedCount != null && unmatchedCount > 0) {
        issues.add(new AccountPeriodCloseIssueResponse(
                "BANK_STATEMENT_UNMATCHED",
                "存在未匹配银行流水",
                ScalePrecision.amount(BigDecimal.valueOf(unmatchedCount))
        ));
    }
}
```

- [ ] **Step 4: Run period test and focused fund tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=AccountPeriodControllerTest#closeCheckReportsUnmatchedBankStatementsAndBlocksLock,FundReconciliationControllerTest" test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 5: Commit period close integration**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java
git commit -m "feat: block period close on unmatched bank statements"
```

## Task 6: Full Verification and Cleanup

**Files:**

- Verify whole repository.

- [ ] **Step 1: Run migration and focused tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest,FundReconciliationControllerTest,AccountPeriodControllerTest" test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 2: Run full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 3: Check worktree status**

Run:

```powershell
git status --short --branch
```

Expected after all commits:

```text
## <current-branch>
```

There should be no unstaged, staged, or untracked files related to this feature.

- [ ] **Step 4: If feature work remains uncommitted, commit only fund reconciliation files**

Run:

```powershell
git add src/main/resources/db/migration/V53__finance_fund_reconciliation_schema.sql src/main/java/com/tuowei/erp/finance/fund src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java src/main/java/com/tuowei/erp/common/security/PermissionCodes.java src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java src/test/java/com/tuowei/erp/finance/fund/FundReconciliationControllerTest.java src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java
git commit -m "feat: add fund reconciliation center"
```

Skip this step if all feature changes are already committed.

## Self-Review

- Spec coverage: schema, permissions, tenant isolation, APIs, matching rules, unmatch rules, no mutation of settlement facts, period close blocking, and full verification are covered by Tasks 1-6.
- Placeholder scan: no placeholder markers or vague catch-all instructions remain.
- Type consistency: package names, endpoint paths, table names, permission names, DTO names, and migration version all match the design document.
