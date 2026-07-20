# Finance AR/AP Entrypoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Current status:** 应收/应付查询入口、收付款作废回滚、共享数据范围支撑和当前最小回归测试已落地。下方逐步清单保留原始执行配方和 commit 粒度；当前发布边界以 `release-check`、`ReceivableControllerTest`、`PayableControllerTest`、`FinanceSettlementCancelControllerTest` 和人工预生产验收为准。

**Goal:** 补齐财务应收/应付正式查询入口，并新增收款单、付款单作废回滚闭环，同时让报表和财务主入口复用同一套应收应付数据权限逻辑。

**Architecture:** 保留现有 `payment / receipt / report` 模块边界，新增 `receivable / payable` 入口和一个专门负责应收应付可见范围判断的共享支撑服务。收款/付款作废仍放在各自服务内，通过头表状态改为 `CANCELLED` 并根据核销明细回退 `settledAmount`，不引入红冲单或新事件表。

**Tech Stack:** Spring Boot 3.5.x, Spring Security, MyBatis-Plus, Flyway, MockMvc, JUnit 5, H2

---

## File Map

**Modify:**
- `pom.xml`
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- `src/main/java/com/tuowei/erp/finance/payment/controller/PaymentController.java`
- `src/main/java/com/tuowei/erp/finance/payment/model/PaymentEntity.java`
- `src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java`
- `src/main/java/com/tuowei/erp/finance/payment/web/PaymentResponse.java`
- `src/main/java/com/tuowei/erp/finance/receipt/controller/ReceiptController.java`
- `src/main/java/com/tuowei/erp/finance/receipt/model/ReceiptEntity.java`
- `src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java`
- `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptResponse.java`
- `src/main/java/com/tuowei/erp/report/service/ReportQueryService.java`

**Create:**
- `src/main/resources/db/migration/V36__finance_ar_ap_entrypoints.sql`
- `src/main/java/com/tuowei/erp/finance/payment/web/PaymentCancelRequest.java`
- `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptCancelRequest.java`
- `src/main/java/com/tuowei/erp/finance/settlement/service/FinanceSettlementScopeSupport.java`
- `src/main/java/com/tuowei/erp/finance/receivable/controller/ReceivableController.java`
- `src/main/java/com/tuowei/erp/finance/receivable/service/ReceivableQueryService.java`
- `src/main/java/com/tuowei/erp/finance/receivable/web/ReceivablePageQuery.java`
- `src/main/java/com/tuowei/erp/finance/receivable/web/ReceivableResponse.java`
- `src/main/java/com/tuowei/erp/finance/payable/controller/PayableController.java`
- `src/main/java/com/tuowei/erp/finance/payable/service/PayableQueryService.java`
- `src/main/java/com/tuowei/erp/finance/payable/web/PayablePageQuery.java`
- `src/main/java/com/tuowei/erp/finance/payable/web/PayableResponse.java`
- `src/test/resources/application-test.yml`
- `src/test/java/com/tuowei/erp/TestRedisConfiguration.java`
- `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`
- `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`
- `src/test/java/com/tuowei/erp/finance/FinanceSettlementCancelControllerTest.java`
- `src/test/java/com/tuowei/erp/report/FinanceSettlementReportScopeTest.java`
- `src/test/java/com/tuowei/erp/finance/receivable/ReceivableControllerTest.java`
- `src/test/java/com/tuowei/erp/finance/payable/PayableControllerTest.java`

## Task 1: Restore Minimal Test Foundation

**Files:**
- Modify: `pom.xml`
- Create: `src/test/resources/application-test.yml`
- Create: `src/test/java/com/tuowei/erp/TestRedisConfiguration.java`
- Create: `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`
- Create: `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`

- [ ] **Step 1: Add test dependencies to `pom.xml`**

Add these dependencies under `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Restore the test profile and security support classes**

Create `src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:erp_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 5s
  jackson:
    time-zone: Asia/Shanghai
erp:
  app:
    code: erp-server
    name: ERP Server Test
    timezone: Asia/Shanghai
  error:
    expose-unexpected-message: true
  security:
    public-api-docs-enabled: true
    jwt:
      secret: test-secret-key-for-unit-tests-32bytes-min
      access-token-ttl-seconds: 7200
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

Create `src/test/java/com/tuowei/erp/TestRedisConfiguration.java`:

```java
package com.tuowei.erp;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Configuration
@Profile("test")
public class TestRedisConfiguration {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate testStringRedisTemplate() {
        Map<String, String> store = new ConcurrentHashMap<>();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);

        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation ->
                store.containsKey(invocation.getArgument(0, String.class)));
        when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation -> {
            Collection<String> keys = invocation.getArgument(0);
            long removed = 0;
            for (String key : keys) {
                if (store.remove(key) != null) {
                    removed++;
                }
            }
            return removed;
        });
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return Long.parseLong(store.merge(key, "1", (oldValue, ignored) ->
                    String.valueOf(Long.parseLong(oldValue) + 1L)));
        });
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        return redisTemplate;
    }
}
```

Create `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`:

```java
package com.tuowei.erp.testsupport;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithErpUserSecurityContextFactory.class)
public @interface WithErpUser {

    long userId() default 1L;
    long companyId() default 1L;
    long accountBookId() default 1L;
    long deptId() default 1L;
    long postId() default 1L;
    String username() default "admin";
    String realName() default "系统管理员";
    String[] authorities() default {};
    boolean allScope() default true;
    boolean deptScoped() default false;
    boolean postScoped() default false;
    boolean selfScoped() default false;
    long[] warehouseIds() default {};
}
```

Create `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`:

```java
package com.tuowei.erp.testsupport;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class WithErpUserSecurityContextFactory implements WithSecurityContextFactory<WithErpUser> {

    @Override
    public SecurityContext createSecurityContext(WithErpUser annotation) {
        ErpPrincipal principal = new ErpPrincipal(
                annotation.userId(),
                annotation.companyId(),
                annotation.accountBookId(),
                annotation.deptId(),
                annotation.postId(),
                annotation.username(),
                annotation.realName(),
                "N/A",
                new LinkedHashSet<>(Arrays.asList(annotation.authorities())),
                dataScopeSnapshot(annotation)
        );

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        "N/A",
                        principal.getAuthorities()
                );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

    private DataScopeSnapshot dataScopeSnapshot(WithErpUser annotation) {
        if (annotation.allScope()) {
            return DataScopeSnapshot.all();
        }
        Set<Long> warehouseIds = Arrays.stream(annotation.warehouseIds())
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new DataScopeSnapshot(
                false,
                annotation.deptScoped(),
                annotation.postScoped(),
                annotation.selfScoped(),
                warehouseIds
        );
    }
}
```

- [ ] **Step 3: Verify the test classpath compiles**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -DskipTests test-compile
```

Expected:

- `BUILD SUCCESS`
- H2 and Spring test dependencies resolve successfully
- `src/test` support classes compile

- [ ] **Step 4: Commit the test foundation**

Run:

```powershell
git add pom.xml src/test/resources/application-test.yml src/test/java/com/tuowei/erp/TestRedisConfiguration.java src/test/java/com/tuowei/erp/testsupport/WithErpUser.java src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java
git commit -m "test: restore finance integration test foundation"
```

## Task 2: Add Payment Cancel Flow

**Files:**
- Create: `src/main/resources/db/migration/V36__finance_ar_ap_entrypoints.sql`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/model/PaymentEntity.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/web/PaymentResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/payment/web/PaymentCancelRequest.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/controller/PaymentController.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java`
- Create: `src/test/java/com/tuowei/erp/finance/FinanceSettlementCancelControllerTest.java`

- [ ] **Step 1: Write the failing payment-cancel integration test**

Create `src/test/java/com/tuowei/erp/finance/FinanceSettlementCancelControllerTest.java` with this first test plus the shared seed/read methods that Task 3 will reuse:

```java
package com.tuowei.erp.finance;

import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceSettlementCancelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_receipt_allocation where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_receipt where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_receivable where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_payment_allocation where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_payment where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_payable where id between 840000 and 840999");
    }

    @Test
    @WithErpUser(authorities = {"finance:payment:create", "finance:payment:view"})
    void cancelsPostedPaymentAndRevertsPayableSettlement() throws Exception {
        seedPayable(840101L, "AP-840101", "120.00", "60.00", "PARTIALLY_SETTLED");
        seedPayment(840201L, "FP202605180001", "50.00", "50.00", "POSTED");
        seedPaymentAllocation(840301L, 840201L, 840101L, "50.00");

        mockMvc.perform(post("/api/finance/payments/{id}/cancel", 840201L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"付款录入错误"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("付款录入错误"));

        Assertions.assertThat(readAmount("fin_payable", "settled_amount", 840101L)).isEqualByComparingTo("10.00");
        Assertions.assertThat(readText("fin_payable", "status", 840101L)).isEqualTo("PARTIALLY_SETTLED");
    }

    private void seedPayable(long id, String payableNo, String originalAmount, String settledAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'TEST_PAYABLE', ?, ?, 'INCREASE',
                        7001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test',
                        9001, 9001, 0)
                """, id, payableNo, id, "SRC-" + id, new BigDecimal(originalAmount), new BigDecimal(settledAmount), status);
    }

    private void seedPayment(long id, String paymentNo, String amount, String allocatedAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_payment
                (id, company_id, account_book_id, payment_no, supplier_id, payment_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 7001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test', 9001, 9001, 0)
                """, id, paymentNo, new BigDecimal(amount), new BigDecimal(allocatedAmount), status);
    }

    private void seedPaymentAllocation(long id, long paymentId, long payableId, String amount) {
        jdbcTemplate.update("""
                insert into fin_payment_allocation
                (id, payment_id, payable_id, amount, created_by, updated_by, version)
                values (?, ?, ?, ?, 9001, 9001, 0)
                """, id, paymentId, payableId, new BigDecimal(amount));
    }

    private void seedReceivable(
            long id,
            String receivableNo,
            String originalAmount,
            String settledAmount,
            String status,
            long createdBy
    ) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'TEST_RECEIVABLE', ?, ?, 'INCREASE',
                        8001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test',
                        ?, ?, 0)
                """, id, receivableNo, id, "SRC-" + id, new BigDecimal(originalAmount), new BigDecimal(settledAmount), status, createdBy, createdBy);
    }

    private void seedReceipt(long id, String receiptNo, String amount, String allocatedAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_receipt
                (id, company_id, account_book_id, receipt_no, customer_id, receipt_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 8001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test', 9001, 9001, 0)
                """, id, receiptNo, new BigDecimal(amount), new BigDecimal(allocatedAmount), status);
    }

    private void seedReceiptAllocation(long id, long receiptId, long receivableId, String amount) {
        jdbcTemplate.update("""
                insert into fin_receipt_allocation
                (id, receipt_id, receivable_id, amount, created_by, updated_by, version)
                values (?, ?, ?, ?, 9001, 9001, 0)
                """, id, receiptId, receivableId, new BigDecimal(amount));
    }

    private BigDecimal readAmount(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
                "select " + columnName + " from " + tableName + " where id = ?",
                BigDecimal.class,
                id
        );
    }

    private String readText(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
                "select " + columnName + " from " + tableName + " where id = ?",
                String.class,
                id
        );
    }
}
```

- [ ] **Step 2: Run the targeted test and verify it fails for the missing endpoint**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementCancelControllerTest#cancelsPostedPaymentAndRevertsPayableSettlement" test
```

Expected:

- Test compiles
- Request fails with `404` or handler-not-found because `/api/finance/payments/{id}/cancel` does not exist yet

- [ ] **Step 3: Implement the payment cancel contracts and minimal production code**

Create `src/main/resources/db/migration/V36__finance_ar_ap_entrypoints.sql`:

```sql
ALTER TABLE fin_payment
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER remark,
    ADD COLUMN cancelled_by BIGINT NULL AFTER cancel_reason,
    ADD COLUMN cancelled_time TIMESTAMP NULL AFTER cancelled_by;

ALTER TABLE fin_receipt
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER remark,
    ADD COLUMN cancelled_by BIGINT NULL AFTER cancel_reason,
    ADD COLUMN cancelled_time TIMESTAMP NULL AFTER cancelled_by;

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5035, 5030, 'MENU', 'FINANCE_RECEIVABLE', '应收管理', '/finance/receivables',
     'finance/receivable/index', 'finance:receivable:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5036, 5030, 'MENU', 'FINANCE_PAYABLE', '应付管理', '/finance/payables',
     'finance/payable/index', 'finance:payable:view', 6, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
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
    (7055, 3002, 5035, 0),
    (7056, 3002, 5036, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

Add permission constants to `PermissionCodes.java`:

```java
public static final String FINANCE_RECEIVABLE_VIEW = "finance:receivable:view";
public static final String FINANCE_PAYABLE_VIEW = "finance:payable:view";

public static final String HAS_FINANCE_RECEIVABLE_VIEW = "hasAuthority('" + FINANCE_RECEIVABLE_VIEW + "')";
public static final String HAS_FINANCE_PAYABLE_VIEW = "hasAuthority('" + FINANCE_PAYABLE_VIEW + "')";
```

Create `PaymentCancelRequest.java`:

```java
package com.tuowei.erp.finance.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
        @NotBlank(message = "作废原因不能为空")
        @Size(max = 200, message = "作废原因长度不能超过200个字符")
        String reason
) {
}
```

Extend `PaymentEntity` with:

```java
private String cancelReason;
private Long cancelledBy;
private LocalDateTime cancelledTime;
```

Extend `PaymentResponse` to:

```java
public record PaymentResponse(
        Long id,
        String paymentNo,
        Long supplierId,
        LocalDate paymentDate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String remark,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime cancelledTime,
        List<PaymentAllocationResponse> allocations
) {
}
```

Add endpoint to `PaymentController.java`:

```java
@PreAuthorize(PermissionCodes.HAS_FINANCE_PAYMENT_CREATE)
@PostMapping("/{id}/cancel")
@OperationLog(module = "finance", operation = "cancel-payment", message = "作废付款单", bizNo = "#id")
public ApiResponse<PaymentResponse> cancel(@PathVariable Long id, @Valid @RequestBody PaymentCancelRequest request) {
    return ApiResponse.success(paymentService.cancel(id, request));
}
```

Add service method to `PaymentService.java`:

```java
@Transactional
public PaymentResponse cancel(Long id, PaymentCancelRequest request) {
    PaymentEntity payment = requirePayment(id);
    if ("CANCELLED".equals(payment.getStatus())) {
        return detail(id);
    }
    if (!"POSTED".equals(payment.getStatus())) {
        throw new IllegalArgumentException("只有已过账付款单可以作废");
    }

    AuditMetadata audit = auditMetadataFactory.current();
    LocalDateTime now = audit.now();
    String reason = request.reason().trim();
    List<PaymentAllocationEntity> allocations = paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>()
            .eq(PaymentAllocationEntity::getPaymentId, id)
            .orderByAsc(PaymentAllocationEntity::getId));
    for (PaymentAllocationEntity allocation : allocations) {
        PayableEntity payable = payableMapper.selectById(allocation.getPayableId());
        if (payable == null || payable.getDeletedFlag() == null || payable.getDeletedFlag() != 0
                || !Objects.equals(payable.getCompanyId(), payment.getCompanyId())) {
            throw new IllegalArgumentException("应付记录不存在");
        }
        BigDecimal amount = ScalePrecision.amount(allocation.getAmount());
        BigDecimal settled = ScalePrecision.amount(ScalePrecision.zeroDefault(payable.getSettledAmount()));
        if (settled.compareTo(amount) < 0) {
            throw new IllegalArgumentException("应付已核销金额不足，无法作废当前付款单");
        }
        payable.setSettledAmount(ScalePrecision.amount(settled.subtract(amount)));
        payable.setStatus(settlementStatus(payable.getOriginalAmount(), payable.getSettledAmount()));
        payable.setUpdatedBy(audit.userId());
        payable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(payableMapper.updateById(payable), "应付记录已被其他操作修改，请刷新后重试");
    }

    payment.setStatus("CANCELLED");
    payment.setCancelReason(reason);
    payment.setCancelledBy(audit.userId());
    payment.setCancelledTime(now);
    payment.setUpdatedBy(audit.userId());
    payment.setUpdatedTime(now);
    OptimisticLockGuard.requireUpdated(paymentMapper.updateById(payment), "付款单已被其他操作修改，请刷新后重试");
    return detail(id);
}
```

Update `toResponse` in `PaymentService`:

```java
private PaymentResponse toResponse(PaymentEntity payment, List<PaymentAllocationResponse> allocations) {
    return new PaymentResponse(
            payment.getId(),
            payment.getPaymentNo(),
            payment.getSupplierId(),
            payment.getPaymentDate(),
            payment.getAmount(),
            payment.getAllocatedAmount(),
            payment.getStatus(),
            payment.getRemark(),
            payment.getCancelReason(),
            payment.getCancelledBy(),
            payment.getCancelledTime(),
            allocations
    );
}
```

- [ ] **Step 4: Re-run the targeted test and verify it passes**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementCancelControllerTest#cancelsPostedPaymentAndRevertsPayableSettlement" test
```

Expected:

- `BUILD SUCCESS`
- The payment row returns `CANCELLED`
- The linked payable row is rolled back to the expected `settled_amount`

- [ ] **Step 5: Commit the payment cancel slice**

Run:

```powershell
git add src/main/resources/db/migration/V36__finance_ar_ap_entrypoints.sql src/main/java/com/tuowei/erp/common/security/PermissionCodes.java src/main/java/com/tuowei/erp/finance/payment/model/PaymentEntity.java src/main/java/com/tuowei/erp/finance/payment/web/PaymentResponse.java src/main/java/com/tuowei/erp/finance/payment/web/PaymentCancelRequest.java src/main/java/com/tuowei/erp/finance/payment/controller/PaymentController.java src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java src/test/java/com/tuowei/erp/finance/FinanceSettlementCancelControllerTest.java
git commit -m "feat: add payment cancel flow"
```

## Task 3: Add Receipt Cancel Flow

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/model/ReceiptEntity.java`
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptCancelRequest.java`
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/controller/ReceiptController.java`
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java`
- Modify: `src/test/java/com/tuowei/erp/finance/FinanceSettlementCancelControllerTest.java`

- [ ] **Step 1: Add the failing receipt-cancel integration test**

Append this second test to `FinanceSettlementCancelControllerTest.java`, reusing the same seed/read methods from Task 2:

```java
@Test
@WithErpUser(authorities = {"finance:receipt:create", "finance:receipt:view"})
void cancelsPostedReceiptAndRevertsReceivableSettlement() throws Exception {
    seedReceivable(840102L, "AR-840102", "200.00", "120.00", "PARTIALLY_SETTLED", 9001L);
    seedReceipt(840202L, "FR202605180001", "80.00", "80.00", "POSTED");
    seedReceiptAllocation(840302L, 840202L, 840102L, "80.00");

    mockMvc.perform(post("/api/finance/receipts/{id}/cancel", 840202L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason":"收款录入错误"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.cancelReason").value("收款录入错误"));

    Assertions.assertThat(readAmount("fin_receivable", "settled_amount", 840102L)).isEqualByComparingTo("40.00");
    Assertions.assertThat(readText("fin_receivable", "status", 840102L)).isEqualTo("PARTIALLY_SETTLED");
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementCancelControllerTest#cancelsPostedReceiptAndRevertsReceivableSettlement" test
```

Expected:

- Request fails with `404` because `/api/finance/receipts/{id}/cancel` does not exist yet

- [ ] **Step 3: Implement the receipt cancel endpoint and service**

Create `ReceiptCancelRequest.java`:

```java
package com.tuowei.erp.finance.receipt.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceiptCancelRequest(
        @NotBlank(message = "作废原因不能为空")
        @Size(max = 200, message = "作废原因长度不能超过200个字符")
        String reason
) {
}
```

Extend `ReceiptEntity` with:

```java
private String cancelReason;
private Long cancelledBy;
private LocalDateTime cancelledTime;
```

Extend `ReceiptResponse` to:

```java
public record ReceiptResponse(
        Long id,
        String receiptNo,
        Long customerId,
        LocalDate receiptDate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String remark,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime cancelledTime,
        List<ReceiptAllocationResponse> allocations
) {
}
```

Add endpoint to `ReceiptController.java`:

```java
@PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIPT_CREATE)
@PostMapping("/{id}/cancel")
@OperationLog(module = "finance", operation = "cancel-receipt", message = "作废收款单", bizNo = "#id")
public ApiResponse<ReceiptResponse> cancel(@PathVariable Long id, @Valid @RequestBody ReceiptCancelRequest request) {
    return ApiResponse.success(receiptService.cancel(id, request));
}
```

Add `cancel` to `ReceiptService.java`:

```java
@Transactional
public ReceiptResponse cancel(Long id, ReceiptCancelRequest request) {
    ReceiptEntity receipt = requireReceipt(id);
    if ("CANCELLED".equals(receipt.getStatus())) {
        return detail(id);
    }
    if (!"POSTED".equals(receipt.getStatus())) {
        throw new IllegalArgumentException("只有已过账收款单可以作废");
    }

    AuditMetadata audit = auditMetadataFactory.current();
    LocalDateTime now = audit.now();
    String reason = request.reason().trim();
    List<ReceiptAllocationEntity> allocations = receiptAllocationMapper.selectList(new LambdaQueryWrapper<ReceiptAllocationEntity>()
            .eq(ReceiptAllocationEntity::getReceiptId, id)
            .orderByAsc(ReceiptAllocationEntity::getId));
    for (ReceiptAllocationEntity allocation : allocations) {
        ReceivableEntity receivable = receivableMapper.selectById(allocation.getReceivableId());
        if (receivable == null || receivable.getDeletedFlag() == null || receivable.getDeletedFlag() != 0
                || !Objects.equals(receivable.getCompanyId(), receipt.getCompanyId())) {
            throw new IllegalArgumentException("应收记录不存在");
        }
        BigDecimal amount = ScalePrecision.amount(allocation.getAmount());
        BigDecimal settled = ScalePrecision.amount(ScalePrecision.zeroDefault(receivable.getSettledAmount()));
        if (settled.compareTo(amount) < 0) {
            throw new IllegalArgumentException("应收已核销金额不足，无法作废当前收款单");
        }
        receivable.setSettledAmount(ScalePrecision.amount(settled.subtract(amount)));
        receivable.setStatus(settlementStatus(receivable.getOriginalAmount(), receivable.getSettledAmount()));
        receivable.setUpdatedBy(audit.userId());
        receivable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receivableMapper.updateById(receivable), "应收记录已被其他操作修改，请刷新后重试");
    }

    receipt.setStatus("CANCELLED");
    receipt.setCancelReason(reason);
    receipt.setCancelledBy(audit.userId());
    receipt.setCancelledTime(now);
    receipt.setUpdatedBy(audit.userId());
    receipt.setUpdatedTime(now);
    OptimisticLockGuard.requireUpdated(receiptMapper.updateById(receipt), "收款单已被其他操作修改，请刷新后重试");
    return detail(id);
}
```

Update `toResponse` in `ReceiptService.java`:

```java
private ReceiptResponse toResponse(ReceiptEntity receipt, List<ReceiptAllocationResponse> allocations) {
    return new ReceiptResponse(
            receipt.getId(),
            receipt.getReceiptNo(),
            receipt.getCustomerId(),
            receipt.getReceiptDate(),
            receipt.getAmount(),
            receipt.getAllocatedAmount(),
            receipt.getStatus(),
            receipt.getRemark(),
            receipt.getCancelReason(),
            receipt.getCancelledBy(),
            receipt.getCancelledTime(),
            allocations
    );
}
```

- [ ] **Step 4: Re-run the targeted receipt cancel test**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementCancelControllerTest#cancelsPostedReceiptAndRevertsReceivableSettlement" test
```

Expected:

- `BUILD SUCCESS`
- The receipt row returns `CANCELLED`
- The linked receivable row is rolled back correctly

- [ ] **Step 5: Commit the receipt cancel slice**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/receipt/model/ReceiptEntity.java src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptResponse.java src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptCancelRequest.java src/main/java/com/tuowei/erp/finance/receipt/controller/ReceiptController.java src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java src/test/java/com/tuowei/erp/finance/FinanceSettlementCancelControllerTest.java
git commit -m "feat: add receipt cancel flow"
```

## Task 4: Extract Shared Settlement Scope Support And Refactor Report Queries

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/settlement/service/FinanceSettlementScopeSupport.java`
- Modify: `src/main/java/com/tuowei/erp/report/service/ReportQueryService.java`
- Create: `src/test/java/com/tuowei/erp/report/FinanceSettlementReportScopeTest.java`

- [ ] **Step 1: Write the failing report regression test for opening receivable visibility**

Create `src/test/java/com/tuowei/erp/report/FinanceSettlementReportScopeTest.java`:

```java
package com.tuowei.erp.report;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceSettlementReportScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_receivable where id between 850000 and 850999");
    }

    @Test
    @WithErpUser(
            userId = 9201L,
            companyId = 1L,
            accountBookId = 1L,
            authorities = {"report:view"},
            allScope = false,
            selfScoped = true
    )
    void reportShouldKeepOpeningReceivablesVisibleForSelfScopedCreator() throws Exception {
        seedOpeningReceivable(850101L, 9201L, "AR-OPEN-850101");
        seedOpeningReceivable(850102L, 9202L, "AR-OPEN-850102");

        mockMvc.perform(get("/api/reports/finance-settlements")
                        .param("direction", "RECEIVABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].bizNo").value("AR-OPEN-850101"));
    }

    private void seedOpeningReceivable(long id, long createdBy, String receivableNo) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'OPENING_RECEIVABLE', ?, ?, 'INCREASE',
                        8101, '2026-05-18', ?, ?, 'UNSETTLED', 0, 'opening receivable scope test',
                        ?, ?, 0)
                """, id, receivableNo, id, receivableNo, new BigDecimal("100.00"), new BigDecimal("0.00"), createdBy, createdBy);
    }
}
```

- [ ] **Step 2: Run the report regression test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementReportScopeTest#reportShouldKeepOpeningReceivablesVisibleForSelfScopedCreator" test
```

Expected:

- The test fails because current `ReportQueryService` filters non purchase/sales source receivables down to zero rows

- [ ] **Step 3: Implement `FinanceSettlementScopeSupport` and refactor the report service**

Create `FinanceSettlementScopeSupport.java` with the shared implementation:

```java
package com.tuowei.erp.finance.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinanceSettlementScopeSupport {

    private static final List<String> SALES_SOURCE_TYPES = List.of("SALES_DELIVERY", "SALES_RETURN");
    private static final List<String> PURCHASE_SOURCE_TYPES = List.of("PURCHASE_RECEIPT", "PURCHASE_RETURN");

    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final UserMapper userMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesReturnMapper salesReturnMapper;

    public FinanceSettlementScopeSupport(
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            UserMapper userMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesReturnMapper salesReturnMapper
    ) {
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.userMapper = userMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesReturnMapper = salesReturnMapper;
    }

    public LambdaQueryWrapper<ReceivableEntity> applyReceivableScope(LambdaQueryWrapper<ReceivableEntity> wrapper) {
        ScopedUsers scopedUsers = scopedUsers();
        wrapper.eq(ReceivableEntity::getCompanyId, scopedUsers.currentUser().companyId())
               .eq(ReceivableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            return wrapper;
        }
        List<Long> deliveryIds = visibleSalesDeliveryIds(scopedUsers);
        List<Long> returnIds = visibleSalesReturnIds(scopedUsers);
        Set<Long> visibleCreators = visibleCreatorIds(scopedUsers);
        return wrapper.and(query -> {
            boolean appended = false;
            if (!deliveryIds.isEmpty()) {
                query.nested(scope -> scope
                        .eq(ReceivableEntity::getSourceType, "SALES_DELIVERY")
                        .in(ReceivableEntity::getSourceId, deliveryIds));
                appended = true;
            }
            if (!returnIds.isEmpty()) {
                if (appended) {
                    query.or();
                }
                query.nested(scope -> scope
                        .eq(ReceivableEntity::getSourceType, "SALES_RETURN")
                        .in(ReceivableEntity::getSourceId, returnIds));
                appended = true;
            }
            if (!visibleCreators.isEmpty()) {
                if (appended) {
                    query.or();
                }
                query.nested(scope -> scope
                        .notIn(ReceivableEntity::getSourceType, SALES_SOURCE_TYPES)
                        .in(ReceivableEntity::getCreatedBy, visibleCreators));
                appended = true;
            }
            if (!appended) {
                query.apply("1 = 0");
            }
        });
    }

    public LambdaQueryWrapper<PayableEntity> applyPayableScope(LambdaQueryWrapper<PayableEntity> wrapper) {
        ScopedUsers scopedUsers = scopedUsers();
        wrapper.eq(PayableEntity::getCompanyId, scopedUsers.currentUser().companyId())
               .eq(PayableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            return wrapper;
        }
        List<Long> receiptIds = visiblePurchaseReceiptIds(scopedUsers);
        List<Long> returnIds = visiblePurchaseReturnIds(scopedUsers);
        Set<Long> visibleCreators = visibleCreatorIds(scopedUsers);
        return wrapper.and(query -> {
            boolean appended = false;
            if (!receiptIds.isEmpty()) {
                query.nested(scope -> scope
                        .eq(PayableEntity::getSourceType, "PURCHASE_RECEIPT")
                        .in(PayableEntity::getSourceId, receiptIds));
                appended = true;
            }
            if (!returnIds.isEmpty()) {
                if (appended) {
                    query.or();
                }
                query.nested(scope -> scope
                        .eq(PayableEntity::getSourceType, "PURCHASE_RETURN")
                        .in(PayableEntity::getSourceId, returnIds));
                appended = true;
            }
            if (!visibleCreators.isEmpty()) {
                if (appended) {
                    query.or();
                }
                query.nested(scope -> scope
                        .notIn(PayableEntity::getSourceType, PURCHASE_SOURCE_TYPES)
                        .in(PayableEntity::getCreatedBy, visibleCreators));
                appended = true;
            }
            if (!appended) {
                query.apply("1 = 0");
            }
        });
    }

    public void assertCanViewReceivable(ReceivableEntity entity) {
        ScopedUsers scopedUsers = scopedUsers();
        if (!Objects.equals(entity.getCompanyId(), scopedUsers.currentUser().companyId())) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (scopedUsers.snapshot().hasAllScope()) {
            return;
        }
        if ("SALES_DELIVERY".equals(entity.getSourceType())
                && visibleSalesDeliveryIds(scopedUsers).contains(entity.getSourceId())) {
            return;
        }
        if ("SALES_RETURN".equals(entity.getSourceType())
                && visibleSalesReturnIds(scopedUsers).contains(entity.getSourceId())) {
            return;
        }
        if (!SALES_SOURCE_TYPES.contains(entity.getSourceType())
                && visibleCreatorIds(scopedUsers).contains(entity.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("无权访问该应收记录");
    }

    public void assertCanViewPayable(PayableEntity entity) {
        ScopedUsers scopedUsers = scopedUsers();
        if (!Objects.equals(entity.getCompanyId(), scopedUsers.currentUser().companyId())) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (scopedUsers.snapshot().hasAllScope()) {
            return;
        }
        if ("PURCHASE_RECEIPT".equals(entity.getSourceType())
                && visiblePurchaseReceiptIds(scopedUsers).contains(entity.getSourceId())) {
            return;
        }
        if ("PURCHASE_RETURN".equals(entity.getSourceType())
                && visiblePurchaseReturnIds(scopedUsers).contains(entity.getSourceId())) {
            return;
        }
        if (!PURCHASE_SOURCE_TYPES.contains(entity.getSourceType())
                && visibleCreatorIds(scopedUsers).contains(entity.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("无权访问该应付记录");
    }

    private List<Long> visibleSalesDeliveryIds(ScopedUsers scopedUsers) {
        return salesDeliveryMapper.selectList(dataScopeService.applySalesDeliveryScope(
                        new LambdaQueryWrapper<SalesDeliveryEntity>().eq(SalesDeliveryEntity::getDeletedFlag, 0),
                        scopedUsers.currentUser(),
                        scopedUsers.snapshot(),
                        scopedUsers.deptUserIds(),
                        scopedUsers.postUserIds()
                ))
                .stream()
                .map(SalesDeliveryEntity::getId)
                .toList();
    }

    private List<Long> visibleSalesReturnIds(ScopedUsers scopedUsers) {
        return salesReturnMapper.selectList(dataScopeService.applySalesReturnScope(
                        new LambdaQueryWrapper<SalesReturnEntity>().eq(SalesReturnEntity::getDeletedFlag, 0),
                        scopedUsers.currentUser(),
                        scopedUsers.snapshot(),
                        scopedUsers.deptUserIds(),
                        scopedUsers.postUserIds()
                ))
                .stream()
                .map(SalesReturnEntity::getId)
                .toList();
    }

    private List<Long> visiblePurchaseReceiptIds(ScopedUsers scopedUsers) {
        return purchaseReceiptMapper.selectList(dataScopeService.applyPurchaseReceiptScope(
                        new LambdaQueryWrapper<PurchaseReceiptEntity>().eq(PurchaseReceiptEntity::getDeletedFlag, 0),
                        scopedUsers.currentUser(),
                        scopedUsers.snapshot(),
                        scopedUsers.deptUserIds(),
                        scopedUsers.postUserIds()
                ))
                .stream()
                .map(PurchaseReceiptEntity::getId)
                .toList();
    }

    private List<Long> visiblePurchaseReturnIds(ScopedUsers scopedUsers) {
        return purchaseReturnMapper.selectList(dataScopeService.applyPurchaseReturnScope(
                        new LambdaQueryWrapper<PurchaseReturnEntity>().eq(PurchaseReturnEntity::getDeletedFlag, 0),
                        scopedUsers.currentUser(),
                        scopedUsers.snapshot(),
                        scopedUsers.deptUserIds(),
                        scopedUsers.postUserIds()
                ))
                .stream()
                .map(PurchaseReturnEntity::getId)
                .toList();
    }

    private Set<Long> visibleCreatorIds(ScopedUsers scopedUsers) {
        Set<Long> visibleCreatorIds = new LinkedHashSet<>();
        if (scopedUsers.snapshot().selfScoped()) {
            visibleCreatorIds.add(scopedUsers.currentUser().userId());
        }
        if (scopedUsers.snapshot().deptScoped()) {
            visibleCreatorIds.addAll(scopedUsers.deptUserIds());
        }
        if (scopedUsers.snapshot().postScoped()) {
            visibleCreatorIds.addAll(scopedUsers.postUserIds());
        }
        return visibleCreatorIds;
    }

    private ScopedUsers scopedUsers() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        Set<Long> deptUserIds = loadScopedUserIds(snapshot.deptScoped(), UserEntity::getDeptId, currentUser.deptId());
        Set<Long> postUserIds = loadScopedUserIds(snapshot.postScoped(), UserEntity::getPostId, currentUser.postId());
        return new ScopedUsers(currentUser, snapshot, deptUserIds, postUserIds);
    }

    private Set<Long> loadScopedUserIds(boolean scoped, Function<UserEntity, Long> scopeGetter, Long scopeValue) {
        if (!scoped || scopeValue == null) {
            return Set.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getDeletedFlag, 0)
                        .eq(UserEntity::getStatus, "ACTIVE"))
                .stream()
                .filter(user -> Objects.equals(scopeGetter.apply(user), scopeValue))
                .map(UserEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record ScopedUsers(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
    }
}
```

Refactor `ReportQueryService.java` to inject `FinanceSettlementScopeSupport`, route payable/receivable wrappers through it, and then delete the extracted local scope methods:

```java
private final FinanceSettlementScopeSupport financeSettlementScopeSupport;
```

Add the constructor parameter and assignment:

```java
public ReportQueryService(
        PurchaseOrderMapper purchaseOrderMapper,
        SalesOrderMapper salesOrderMapper,
        InventoryBalanceMapper inventoryBalanceMapper,
        InventoryTransactionMapper inventoryTransactionMapper,
        PayableMapper payableMapper,
        ReceivableMapper receivableMapper,
        PurchaseReceiptMapper purchaseReceiptMapper,
        PurchaseReturnMapper purchaseReturnMapper,
        SalesDeliveryMapper salesDeliveryMapper,
        SalesReturnMapper salesReturnMapper,
        CurrentUserContext currentUserContext,
        DataScopeService dataScopeService,
        UserMapper userMapper,
        FinanceSettlementScopeSupport financeSettlementScopeSupport
) {
    this.purchaseOrderMapper = purchaseOrderMapper;
    this.salesOrderMapper = salesOrderMapper;
    this.inventoryBalanceMapper = inventoryBalanceMapper;
    this.inventoryTransactionMapper = inventoryTransactionMapper;
    this.payableMapper = payableMapper;
    this.receivableMapper = receivableMapper;
    this.purchaseReceiptMapper = purchaseReceiptMapper;
    this.purchaseReturnMapper = purchaseReturnMapper;
    this.salesDeliveryMapper = salesDeliveryMapper;
    this.salesReturnMapper = salesReturnMapper;
    this.currentUserContext = currentUserContext;
    this.dataScopeService = dataScopeService;
    this.userMapper = userMapper;
    this.financeSettlementScopeSupport = financeSettlementScopeSupport;
}
```

Update the two wrapper builders:

```java
private LambdaQueryWrapper<PayableEntity> payableWrapper(FinanceSettlementReportQuery query) {
    LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
    if (query.getPartnerId() != null) {
        wrapper.eq(PayableEntity::getSupplierId, query.getPartnerId());
    }
    String status = normalizeUpper(query.getStatus());
    if (StringUtils.hasText(status)) {
        wrapper.eq(PayableEntity::getStatus, status);
    }
    String sourceType = normalizeUpper(query.getSourceType());
    if (StringUtils.hasText(sourceType)) {
        wrapper.eq(PayableEntity::getSourceType, sourceType);
    }
    if (query.getBizDateFrom() != null) {
        wrapper.ge(PayableEntity::getBizDate, query.getBizDateFrom());
    }
    if (query.getBizDateTo() != null) {
        wrapper.le(PayableEntity::getBizDate, query.getBizDateTo());
    }
    return financeSettlementScopeSupport.applyPayableScope(wrapper);
}

private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(FinanceSettlementReportQuery query) {
    LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
    if (query.getPartnerId() != null) {
        wrapper.eq(ReceivableEntity::getCustomerId, query.getPartnerId());
    }
    String status = normalizeUpper(query.getStatus());
    if (StringUtils.hasText(status)) {
        wrapper.eq(ReceivableEntity::getStatus, status);
    }
    String sourceType = normalizeUpper(query.getSourceType());
    if (StringUtils.hasText(sourceType)) {
        wrapper.eq(ReceivableEntity::getSourceType, sourceType);
    }
    if (query.getBizDateFrom() != null) {
        wrapper.ge(ReceivableEntity::getBizDate, query.getBizDateFrom());
    }
    if (query.getBizDateTo() != null) {
        wrapper.le(ReceivableEntity::getBizDate, query.getBizDateTo());
    }
    return financeSettlementScopeSupport.applyReceivableScope(wrapper);
}
```

Delete `applyPayableSourceScope` and `applyReceivableSourceScope` after the wrapper methods above are green so the report service no longer owns duplicated scope logic.

- [ ] **Step 4: Re-run the report regression test and verify it passes**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementReportScopeTest#reportShouldKeepOpeningReceivablesVisibleForSelfScopedCreator" test
```

Expected:

- `BUILD SUCCESS`
- The report endpoint now returns exactly one opening receivable for the self-scoped creator

- [ ] **Step 5: Commit the shared scope refactor**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/settlement/service/FinanceSettlementScopeSupport.java src/main/java/com/tuowei/erp/report/service/ReportQueryService.java src/test/java/com/tuowei/erp/report/FinanceSettlementReportScopeTest.java
git commit -m "refactor: share finance settlement scope logic"
```

## Task 5: Add Receivable Query Entrypoints

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/receivable/web/ReceivablePageQuery.java`
- Create: `src/main/java/com/tuowei/erp/finance/receivable/web/ReceivableResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/receivable/service/ReceivableQueryService.java`
- Create: `src/main/java/com/tuowei/erp/finance/receivable/controller/ReceivableController.java`
- Create: `src/test/java/com/tuowei/erp/finance/receivable/ReceivableControllerTest.java`

- [ ] **Step 1: Write the failing receivable controller test**

Create `ReceivableControllerTest.java`:

```java
package com.tuowei.erp.finance.receivable;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceivableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_receivable where id between 860000 and 860999");
    }

    @Test
    @WithErpUser(
            userId = 9301L,
            companyId = 1L,
            accountBookId = 1L,
            authorities = {"finance:receivable:view"},
            allScope = false,
            selfScoped = true
    )
    void listsOwnOpeningReceivablesAndBlocksForeignDetail() throws Exception {
        seedOpeningReceivable(860101L, 9301L, "AR-OPEN-860101");
        seedOpeningReceivable(860102L, 9302L, "AR-OPEN-860102");

        mockMvc.perform(get("/api/finance/receivables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].receivableNo").value("AR-OPEN-860101"));

        mockMvc.perform(get("/api/finance/receivables/{id}", 860102L))
                .andExpect(status().isForbidden());
    }

    private void seedOpeningReceivable(long id, long createdBy, String receivableNo) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'OPENING_RECEIVABLE', ?, ?, 'INCREASE',
                        8201, '2026-05-18', ?, ?, 'UNSETTLED', 0, 'receivable entrypoint test',
                        ?, ?, 0)
                """, id, receivableNo, id, receivableNo, new BigDecimal("100.00"), new BigDecimal("0.00"), createdBy, createdBy);
    }
}
```

- [ ] **Step 2: Run the receivable controller test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ReceivableControllerTest#listsOwnOpeningReceivablesAndBlocksForeignDetail" test
```

Expected:

- Test fails with `404` because `/api/finance/receivables` does not exist yet

- [ ] **Step 3: Implement the receivable query endpoint**

Create `ReceivablePageQuery.java`:

```java
package com.tuowei.erp.finance.receivable.web;

import java.time.LocalDate;

public class ReceivablePageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private Long customerId;
    private String status;
    private String sourceType;
    private LocalDate bizDateFrom;
    private LocalDate bizDateTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDate getBizDateFrom() {
        return bizDateFrom;
    }

    public void setBizDateFrom(LocalDate bizDateFrom) {
        this.bizDateFrom = bizDateFrom;
    }

    public LocalDate getBizDateTo() {
        return bizDateTo;
    }

    public void setBizDateTo(LocalDate bizDateTo) {
        this.bizDateTo = bizDateTo;
    }
}
```

Create `ReceivableResponse.java`:

```java
package com.tuowei.erp.finance.receivable.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivableResponse(
        Long id,
        String receivableNo,
        Long customerId,
        LocalDate bizDate,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String direction,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String status,
        String remark
) {
}
```

Create `ReceivableQueryService.java`:

```java
package com.tuowei.erp.finance.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class ReceivableQueryService {

    private final ReceivableMapper receivableMapper;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;

    public ReceivableQueryService(
            ReceivableMapper receivableMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport
    ) {
        this.receivableMapper = receivableMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceivableResponse> list(ReceivablePageQuery query) {
        ReceivablePageQuery safeQuery = query == null ? new ReceivablePageQuery() : query;
        Page<ReceivableEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        if (safeQuery.getCustomerId() != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, safeQuery.getCustomerId());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(ReceivableEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safeQuery.getSourceType())) {
            wrapper.eq(ReceivableEntity::getSourceType, safeQuery.getSourceType().trim().toUpperCase(Locale.ROOT));
        }
        if (safeQuery.getBizDateFrom() != null) {
            wrapper.ge(ReceivableEntity::getBizDate, safeQuery.getBizDateFrom());
        }
        if (safeQuery.getBizDateTo() != null) {
            wrapper.le(ReceivableEntity::getBizDate, safeQuery.getBizDateTo());
        }
        wrapper = financeSettlementScopeSupport.applyReceivableScope(wrapper);
        wrapper.orderByDesc(ReceivableEntity::getBizDate).orderByDesc(ReceivableEntity::getId);
        Page<ReceivableEntity> result = receivableMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ReceivableResponse detail(Long id) {
        ReceivableEntity entity = requireReceivable(id);
        financeSettlementScopeSupport.assertCanViewReceivable(entity);
        return toResponse(entity);
    }

    private ReceivableEntity requireReceivable(Long id) {
        ReceivableEntity entity = receivableMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("应收记录不存在");
        }
        return entity;
    }

    private ReceivableResponse toResponse(ReceivableEntity entity) {
        return new ReceivableResponse(
                entity.getId(),
                entity.getReceivableNo(),
                entity.getCustomerId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getSourceNo(),
                entity.getDirection(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
```

Create `ReceivableController.java`:

```java
package com.tuowei.erp.finance.receivable.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receivable.service.ReceivableQueryService;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/receivables")
public class ReceivableController {

    private final ReceivableQueryService receivableQueryService;

    public ReceivableController(ReceivableQueryService receivableQueryService) {
        this.receivableQueryService = receivableQueryService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIVABLE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ReceivableResponse>> list(ReceivablePageQuery query) {
        return ApiResponse.success(receivableQueryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIVABLE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ReceivableResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(receivableQueryService.detail(id));
    }
}
```

- [ ] **Step 4: Re-run the receivable controller test**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ReceivableControllerTest#listsOwnOpeningReceivablesAndBlocksForeignDetail" test
```

Expected:

- `BUILD SUCCESS`
- The list endpoint returns only the creator-visible opening receivable
- The foreign detail request returns `403`

- [ ] **Step 5: Commit the receivable entrypoint**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/receivable/controller/ReceivableController.java src/main/java/com/tuowei/erp/finance/receivable/service/ReceivableQueryService.java src/main/java/com/tuowei/erp/finance/receivable/web/ReceivablePageQuery.java src/main/java/com/tuowei/erp/finance/receivable/web/ReceivableResponse.java src/test/java/com/tuowei/erp/finance/receivable/ReceivableControllerTest.java
git commit -m "feat: add receivable query entrypoints"
```

## Task 6: Add Payable Query Entrypoints

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/payable/web/PayablePageQuery.java`
- Create: `src/main/java/com/tuowei/erp/finance/payable/web/PayableResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/payable/service/PayableQueryService.java`
- Create: `src/main/java/com/tuowei/erp/finance/payable/controller/PayableController.java`
- Create: `src/test/java/com/tuowei/erp/finance/payable/PayableControllerTest.java`

- [ ] **Step 1: Write the failing payable controller test**

Create `PayableControllerTest.java`:

```java
package com.tuowei.erp.finance.payable;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_payable where id between 870000 and 870999");
    }

    @Test
    @WithErpUser(
            userId = 9401L,
            companyId = 1L,
            accountBookId = 1L,
            authorities = {"finance:payable:view"},
            allScope = false,
            selfScoped = true
    )
    void listsOwnOpeningPayablesAndBlocksForeignDetail() throws Exception {
        seedOpeningPayable(870101L, 9401L, "AP-OPEN-870101");
        seedOpeningPayable(870102L, 9402L, "AP-OPEN-870102");

        mockMvc.perform(get("/api/finance/payables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].payableNo").value("AP-OPEN-870101"));

        mockMvc.perform(get("/api/finance/payables/{id}", 870102L))
                .andExpect(status().isForbidden());
    }

    private void seedOpeningPayable(long id, long createdBy, String payableNo) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'OPENING_PAYABLE', ?, ?, 'INCREASE',
                        8301, '2026-05-18', ?, ?, 'UNSETTLED', 0, 'payable entrypoint test',
                        ?, ?, 0)
                """, id, payableNo, id, payableNo, new BigDecimal("100.00"), new BigDecimal("0.00"), createdBy, createdBy);
    }
}
```

- [ ] **Step 2: Run the payable controller test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PayableControllerTest#listsOwnOpeningPayablesAndBlocksForeignDetail" test
```

Expected:

- Test fails with `404` because `/api/finance/payables` does not exist yet

- [ ] **Step 3: Implement the payable query endpoint**

Create `PayablePageQuery.java`:

```java
package com.tuowei.erp.finance.payable.web;

import java.time.LocalDate;

public class PayablePageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private Long supplierId;
    private String status;
    private String sourceType;
    private LocalDate bizDateFrom;
    private LocalDate bizDateTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDate getBizDateFrom() {
        return bizDateFrom;
    }

    public void setBizDateFrom(LocalDate bizDateFrom) {
        this.bizDateFrom = bizDateFrom;
    }

    public LocalDate getBizDateTo() {
        return bizDateTo;
    }

    public void setBizDateTo(LocalDate bizDateTo) {
        this.bizDateTo = bizDateTo;
    }
}
```

Create `PayableResponse.java`:

```java
package com.tuowei.erp.finance.payable.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayableResponse(
        Long id,
        String payableNo,
        Long supplierId,
        LocalDate bizDate,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String direction,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String status,
        String remark
) {
}
```

Create `PayableQueryService.java`:

```java
package com.tuowei.erp.finance.payable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class PayableQueryService {

    private final PayableMapper payableMapper;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;

    public PayableQueryService(
            PayableMapper payableMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport
    ) {
        this.payableMapper = payableMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayableResponse> list(PayablePageQuery query) {
        PayablePageQuery safeQuery = query == null ? new PayablePageQuery() : query;
        Page<PayableEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        if (safeQuery.getSupplierId() != null) {
            wrapper.eq(PayableEntity::getSupplierId, safeQuery.getSupplierId());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(PayableEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safeQuery.getSourceType())) {
            wrapper.eq(PayableEntity::getSourceType, safeQuery.getSourceType().trim().toUpperCase(Locale.ROOT));
        }
        if (safeQuery.getBizDateFrom() != null) {
            wrapper.ge(PayableEntity::getBizDate, safeQuery.getBizDateFrom());
        }
        if (safeQuery.getBizDateTo() != null) {
            wrapper.le(PayableEntity::getBizDate, safeQuery.getBizDateTo());
        }
        wrapper = financeSettlementScopeSupport.applyPayableScope(wrapper);
        wrapper.orderByDesc(PayableEntity::getBizDate).orderByDesc(PayableEntity::getId);
        Page<PayableEntity> result = payableMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public PayableResponse detail(Long id) {
        PayableEntity entity = requirePayable(id);
        financeSettlementScopeSupport.assertCanViewPayable(entity);
        return toResponse(entity);
    }

    private PayableEntity requirePayable(Long id) {
        PayableEntity entity = payableMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("应付记录不存在");
        }
        return entity;
    }

    private PayableResponse toResponse(PayableEntity entity) {
        return new PayableResponse(
                entity.getId(),
                entity.getPayableNo(),
                entity.getSupplierId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getSourceNo(),
                entity.getDirection(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
```

Create `PayableController.java`:

```java
package com.tuowei.erp.finance.payable.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.service.PayableQueryService;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/payables")
public class PayableController {

    private final PayableQueryService payableQueryService;

    public PayableController(PayableQueryService payableQueryService) {
        this.payableQueryService = payableQueryService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYABLE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PayableResponse>> list(PayablePageQuery query) {
        return ApiResponse.success(payableQueryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYABLE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PayableResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(payableQueryService.detail(id));
    }
}
```

- [ ] **Step 4: Re-run the payable controller test**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PayableControllerTest#listsOwnOpeningPayablesAndBlocksForeignDetail" test
```

Expected:

- `BUILD SUCCESS`
- The list endpoint returns only the creator-visible opening payable
- The foreign detail request returns `403`

- [ ] **Step 5: Commit the payable entrypoint**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/payable/controller/PayableController.java src/main/java/com/tuowei/erp/finance/payable/service/PayableQueryService.java src/main/java/com/tuowei/erp/finance/payable/web/PayablePageQuery.java src/main/java/com/tuowei/erp/finance/payable/web/PayableResponse.java src/test/java/com/tuowei/erp/finance/payable/PayableControllerTest.java
git commit -m "feat: add payable query entrypoints"
```

## Task 7: Final Regression And Package Verification

**Files:**
- Existing files from Tasks 1-6

- [ ] **Step 1: Run the focused finance/report regression suite**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSettlementCancelControllerTest,FinanceSettlementReportScopeTest,ReceivableControllerTest,PayableControllerTest" test
```

Expected:

- `BUILD SUCCESS`
- All four targeted test classes pass

- [ ] **Step 2: Run compile verification for the full main codebase**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -DskipTests compile
```

Expected:

- `BUILD SUCCESS`

- [ ] **Step 3: Run the full package build**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package
```

Expected:

- `BUILD SUCCESS`
- Test phase runs the restored targeted test suite successfully
- `target/erp-server-1.0.0.jar` exists

- [ ] **Step 4: Commit the final integrated state**

Run:

```powershell
git add pom.xml src/main/java src/main/resources/db/migration src/test docs/superpowers/specs/2026-05-18-finance-ar-ap-entrypoints-design.md
git commit -m "feat: add finance ar ap entrypoints and cancel flows"
```

## Self-Review

**Spec coverage:** Covers the formal receivable/payable entrypoints, payment/receipt cancel endpoints, cancel metadata persistence, shared scope logic for finance-native opening records, report reuse, permission constants, menu seed, and targeted automation restoration.

**Placeholder scan:** 已清掉占位语、偷懒转述和未定义引用；每个任务都给了明确文件、代码、命令和预期结果。

**Type consistency:** `PaymentCancelRequest` / `ReceiptCancelRequest`, `ReceivablePageQuery` / `PayablePageQuery`, `ReceivableResponse` / `PayableResponse`, and `FinanceSettlementScopeSupport` naming are consistent across controller, service, DTO, and test tasks.
