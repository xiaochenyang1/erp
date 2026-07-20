# ERP Phase1 Context Audit Sequence Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不扩大业务范围的前提下，先把关键链路里的当前登录用户上下文、审计字段写入和编号并发安全补齐，消灭 `1L/0L` 审计硬编码在高风险模块里的继续扩散。

**Architecture:** 继续沿用现有 `Spring Security + ErpPrincipal + service` 架构，不新搞一套审计框架。测试侧先把 `@WithAdminUser` 升级为真实 `ErpPrincipal` 登录态，再通过一个轻量 `AuditMetadataFactory` 统一提供 `userId/companyId/accountBookId/now`；业务侧第一阶段只覆盖 `UserService`、采购订单、采购入库、采购退货和库存回写。编号生成通过 MyBatis-Plus 乐观锁保护 `sys_sequence_rule`，三个编号服务改成统一重试逻辑，保证并发下不撞号。

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Security 6, MyBatis-Plus 3.5.7, Flyway, H2/MySQL, JUnit 5, MockMvc, JdbcTemplate

---

## File Map

**Create:**
- `src/main/java/com/tuowei/erp/common/security/AuditMetadata.java`
- `src/main/java/com/tuowei/erp/common/security/AuditMetadataFactory.java`
- `src/main/java/com/tuowei/erp/system/config/service/SequenceNumberGenerator.java`
- `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`
- `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`
- `src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java`
- `src/test/java/com/tuowei/erp/system/user/UserAuditFieldsIntegrationTest.java`
- `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderAuditFieldsTest.java`
- `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptAuditInventoryTest.java`
- `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnAuditInventoryTest.java`
- `src/test/java/com/tuowei/erp/system/config/SequenceNumberGeneratorTest.java`

**Modify:**
- `src/test/java/com/tuowei/erp/testsupport/WithAdminUser.java`
- `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- `src/main/java/com/tuowei/erp/system/config/model/SequenceRuleEntity.java`
- `src/main/java/com/tuowei/erp/system/user/service/UserService.java`
- `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderNumberService.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptNumberService.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnNumberService.java`

**Out of Scope For This Plan:**
- `masterdata` 下商品、客户、供应商、仓库等剩余 CRUD 模块的审计字段清理
- `system/menu|dept|post|config|role` 等剩余后台 CRUD 模块的审计字段清理
- 数据权限落地
- 操作日志、登录日志、审计日志表写入

---

### Task 1: Make Test Authentication Use Real `ErpPrincipal`

**Files:**
- Create: `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`
- Create: `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`
- Create: `src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java`
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithAdminUser.java`

- [x] **Step 1: Write the failing current-user test**

Create `src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java`:

```java
package com.tuowei.erp.common.security;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CurrentUserContextTest {

    @Autowired
    private CurrentUserContext currentUserContext;

    @Test
    @WithErpUser(
            userId = 9001L,
            companyId = 88L,
            accountBookId = 66L,
            username = "planner",
            realName = "计划员王五"
    )
    void returnsErpPrincipalSnapshot() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();

        assertThat(currentUser.userId()).isEqualTo(9001L);
        assertThat(currentUser.companyId()).isEqualTo(88L);
        assertThat(currentUser.accountBookId()).isEqualTo(66L);
        assertThat(currentUser.username()).isEqualTo("planner");
        assertThat(currentUser.realName()).isEqualTo("计划员王五");
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(() -> currentUserContext.requireCurrentUser())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前用户未登录");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=CurrentUserContextTest" test
```

Expected:

```text
FAIL ... cannot find symbol: class WithErpUser
```

- [x] **Step 3: Add a test-only ERP principal annotation**

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

    String username() default "admin";

    String realName() default "系统管理员";

    String[] authorities() default {};
}
```

Create `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`:

```java
package com.tuowei.erp.testsupport;

import com.tuowei.erp.common.security.ErpPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class WithErpUserSecurityContextFactory implements WithSecurityContextFactory<WithErpUser> {

    @Override
    public SecurityContext createSecurityContext(WithErpUser annotation) {
        ErpPrincipal principal = new ErpPrincipal(
                annotation.userId(),
                annotation.companyId(),
                annotation.accountBookId(),
                annotation.username(),
                annotation.realName(),
                "N/A",
                new LinkedHashSet<>(Arrays.asList(annotation.authorities()))
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
}
```

- [x] **Step 4: Rewire `WithAdminUser` to the real principal**

Modify `src/test/java/com/tuowei/erp/testsupport/WithAdminUser.java` to replace `@WithMockUser` with `@WithErpUser` and keep the existing authority list:

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithErpUser(
        userId = 1L,
        companyId = 1L,
        accountBookId = 1L,
        username = "admin",
        realName = "系统管理员",
        authorities = {
                PermissionCodes.SYSTEM_PROFILE_VIEW,
                PermissionCodes.SYSTEM_USER_VIEW,
                PermissionCodes.SYSTEM_USER_CREATE,
                PermissionCodes.SYSTEM_USER_UPDATE,
                PermissionCodes.SYSTEM_USER_ENABLE,
                PermissionCodes.SYSTEM_USER_DISABLE,
                PermissionCodes.SYSTEM_USER_ASSIGN_ROLE,
                PermissionCodes.SYSTEM_ROLE_VIEW,
                PermissionCodes.SYSTEM_ROLE_CREATE,
                PermissionCodes.SYSTEM_ROLE_UPDATE,
                PermissionCodes.SYSTEM_ROLE_ENABLE,
                PermissionCodes.SYSTEM_ROLE_DISABLE,
                PermissionCodes.SYSTEM_ROLE_ASSIGN_MENU,
                PermissionCodes.SYSTEM_MENU_VIEW,
                PermissionCodes.SYSTEM_MENU_CREATE,
                PermissionCodes.SYSTEM_MENU_UPDATE,
                PermissionCodes.SYSTEM_MENU_ENABLE,
                PermissionCodes.SYSTEM_MENU_DISABLE,
                PermissionCodes.SYSTEM_DEPT_VIEW,
                PermissionCodes.SYSTEM_DEPT_CREATE,
                PermissionCodes.SYSTEM_DEPT_UPDATE,
                PermissionCodes.SYSTEM_DEPT_ENABLE,
                PermissionCodes.SYSTEM_DEPT_DISABLE,
                PermissionCodes.SYSTEM_POST_VIEW,
                PermissionCodes.SYSTEM_POST_CREATE,
                PermissionCodes.SYSTEM_POST_UPDATE,
                PermissionCodes.SYSTEM_POST_ENABLE,
                PermissionCodes.SYSTEM_POST_DISABLE,
                PermissionCodes.SYSTEM_CONFIG_VIEW,
                PermissionCodes.SYSTEM_CONFIG_CREATE,
                PermissionCodes.SYSTEM_CONFIG_UPDATE,
                PermissionCodes.SYSTEM_CONFIG_ENABLE,
                PermissionCodes.SYSTEM_CONFIG_DISABLE,
                PermissionCodes.SYSTEM_SEQUENCE_RULE_VIEW,
                PermissionCodes.SYSTEM_SEQUENCE_RULE_CREATE,
                PermissionCodes.SYSTEM_SEQUENCE_RULE_UPDATE,
                PermissionCodes.SYSTEM_SEQUENCE_RULE_ENABLE,
                PermissionCodes.SYSTEM_SEQUENCE_RULE_DISABLE,
                PermissionCodes.MASTERDATA_PRODUCT_VIEW,
                PermissionCodes.MASTERDATA_PRODUCT_CREATE,
                PermissionCodes.MASTERDATA_PRODUCT_UPDATE,
                PermissionCodes.MASTERDATA_PRODUCT_ENABLE,
                PermissionCodes.MASTERDATA_PRODUCT_DISABLE,
                PermissionCodes.MASTERDATA_CUSTOMER_VIEW,
                PermissionCodes.MASTERDATA_CUSTOMER_CREATE,
                PermissionCodes.MASTERDATA_CUSTOMER_UPDATE,
                PermissionCodes.MASTERDATA_CUSTOMER_ENABLE,
                PermissionCodes.MASTERDATA_CUSTOMER_DISABLE,
                PermissionCodes.MASTERDATA_SUPPLIER_VIEW,
                PermissionCodes.MASTERDATA_SUPPLIER_CREATE,
                PermissionCodes.MASTERDATA_SUPPLIER_UPDATE,
                PermissionCodes.MASTERDATA_SUPPLIER_ENABLE,
                PermissionCodes.MASTERDATA_SUPPLIER_DISABLE,
                PermissionCodes.MASTERDATA_WAREHOUSE_VIEW,
                PermissionCodes.MASTERDATA_WAREHOUSE_CREATE,
                PermissionCodes.MASTERDATA_WAREHOUSE_UPDATE,
                PermissionCodes.MASTERDATA_WAREHOUSE_ENABLE,
                PermissionCodes.MASTERDATA_WAREHOUSE_DISABLE,
                PermissionCodes.PURCHASE_ORDER_VIEW,
                PermissionCodes.PURCHASE_ORDER_CREATE,
                PermissionCodes.PURCHASE_ORDER_UPDATE,
                PermissionCodes.PURCHASE_ORDER_SUBMIT,
                PermissionCodes.PURCHASE_ORDER_APPROVE,
                PermissionCodes.PURCHASE_ORDER_REJECT,
                PermissionCodes.PURCHASE_ORDER_CANCEL,
                PermissionCodes.PURCHASE_RECEIPT_VIEW,
                PermissionCodes.PURCHASE_RECEIPT_CREATE,
                PermissionCodes.PURCHASE_RECEIPT_UPDATE,
                PermissionCodes.PURCHASE_RECEIPT_CANCEL,
                PermissionCodes.PURCHASE_RECEIPT_POST,
                PermissionCodes.PURCHASE_RETURN_VIEW,
                PermissionCodes.PURCHASE_RETURN_CREATE,
                PermissionCodes.PURCHASE_RETURN_UPDATE,
                PermissionCodes.PURCHASE_RETURN_CANCEL,
                PermissionCodes.PURCHASE_RETURN_POST
        }
)
public @interface WithAdminUser {
}
```

- [x] **Step 5: Re-run the targeted tests**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=CurrentUserContextTest,UserControllerCreateTest" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 6: Commit**

```powershell
git add src/test/java/com/tuowei/erp/testsupport/WithErpUser.java src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java src/test/java/com/tuowei/erp/testsupport/WithAdminUser.java src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java
git commit -m "test: back test auth with erp principal"
```

---

### Task 2: Introduce Shared Audit Metadata And Fix `UserService`

**Files:**
- Create: `src/main/java/com/tuowei/erp/common/security/AuditMetadata.java`
- Create: `src/main/java/com/tuowei/erp/common/security/AuditMetadataFactory.java`
- Create: `src/test/java/com/tuowei/erp/system/user/UserAuditFieldsIntegrationTest.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/service/UserService.java`

- [x] **Step 1: Write the failing user audit integration test**

Create `src/test/java/com/tuowei/erp/system/user/UserAuditFieldsIntegrationTest.java`:

```java
package com.tuowei.erp.system.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuditFieldsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithErpUser(
            userId = 9201L,
            companyId = 18L,
            accountBookId = 28L,
            username = "creator_user",
            realName = "创建人赵六",
            authorities = {
                    "system:user:create",
                    "system:user:view"
            }
    )
    void createWritesAuthenticatedAuditFields() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "audit_user_01",
                                  "password": "P@ssw0rd123",
                                  "realName": "审计用户",
                                  "mobile": "13877770001",
                                  "remark": "审计字段校验"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = body.path("data").path("id").asLong();

        Long companyId = jdbcTemplate.queryForObject("select company_id from sys_user where id = ?", Long.class, id);
        Long accountBookId = jdbcTemplate.queryForObject("select account_book_id from sys_user where id = ?", Long.class, id);
        Long createdBy = jdbcTemplate.queryForObject("select created_by from sys_user where id = ?", Long.class, id);
        Long updatedBy = jdbcTemplate.queryForObject("select updated_by from sys_user where id = ?", Long.class, id);

        assertThat(companyId).isEqualTo(18L);
        assertThat(accountBookId).isEqualTo(28L);
        assertThat(createdBy).isEqualTo(9201L);
        assertThat(updatedBy).isEqualTo(9201L);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=UserAuditFieldsIntegrationTest" test
```

Expected:

```text
FAIL ... expected: 18L but was: 1L
```

- [x] **Step 3: Add a lightweight audit metadata helper**

Create `src/main/java/com/tuowei/erp/common/security/AuditMetadata.java`:

```java
package com.tuowei.erp.common.security;

import java.time.LocalDateTime;

public record AuditMetadata(
        Long userId,
        Long companyId,
        Long accountBookId,
        LocalDateTime now
) {
}
```

Create `src/main/java/com/tuowei/erp/common/security/AuditMetadataFactory.java`:

```java
package com.tuowei.erp.common.security;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetadataFactory {

    private final CurrentUserContext currentUserContext;

    public AuditMetadataFactory(CurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    public AuditMetadata current() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        return new AuditMetadata(
                currentUser.userId(),
                currentUser.companyId(),
                currentUser.accountBookId(),
                LocalDateTime.now()
        );
    }
}
```

- [x] **Step 4: Refactor `UserService` to stop writing fake audit values**

Modify `src/main/java/com/tuowei/erp/system/user/service/UserService.java`:

```java
private final AuditMetadataFactory auditMetadataFactory;
```

Constructor addition:

```java
                   PasswordEncoder passwordEncoder,
                   AuditMetadataFactory auditMetadataFactory) {
    this.userMapper = userMapper;
    this.userRoleMapper = userRoleMapper;
    this.roleMapper = roleMapper;
    this.deptMapper = deptMapper;
    this.postMapper = postMapper;
    this.passwordEncoder = passwordEncoder;
    this.auditMetadataFactory = auditMetadataFactory;
}
```

In `create(...)`:

```java
AuditMetadata audit = auditMetadataFactory.current();
LocalDateTime now = audit.now();

entity.setCompanyId(audit.companyId());
entity.setAccountBookId(audit.accountBookId());
entity.setCreatedBy(audit.userId());
entity.setCreatedTime(now);
entity.setUpdatedBy(audit.userId());
entity.setUpdatedTime(now);
```

In `update(...)` and `updateStatus(...)`:

```java
AuditMetadata audit = auditMetadataFactory.current();
entity.setUpdatedBy(audit.userId());
entity.setUpdatedTime(audit.now());
```

In `assignRoles(...)`:

```java
AuditMetadata audit = auditMetadataFactory.current();
LocalDateTime now = audit.now();

userRoleEntity.setCreatedBy(audit.userId());
userRoleEntity.setCreatedTime(now);
```
```

- [x] **Step 5: Run the user audit regression**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=UserAuditFieldsIntegrationTest,UserControllerLifecycleTest,UserRoleAssignmentTest" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/AuditMetadata.java src/main/java/com/tuowei/erp/common/security/AuditMetadataFactory.java src/main/java/com/tuowei/erp/system/user/service/UserService.java src/test/java/com/tuowei/erp/system/user/UserAuditFieldsIntegrationTest.java
git commit -m "feat: apply authenticated audit fields to user service"
```

---

### Task 3: Apply Authenticated Audit Metadata To Purchase And Inventory Flows

**Files:**
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderAuditFieldsTest.java`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptAuditInventoryTest.java`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnAuditInventoryTest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`

- [x] **Step 1: Write the failing purchase-order audit test**

Create `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderAuditFieldsTest.java` by copying the seed helpers from `PurchaseOrderControllerCreateDetailTest` and adding DB assertions:

```java
@Test
@WithErpUser(
        userId = 9301L,
        companyId = 11L,
        accountBookId = 22L,
        username = "buyer_audit",
        realName = "采购审计员",
        authorities = {
                "purchase:order:create",
                "purchase:order:view"
        }
)
void createWritesAuthenticatedHeaderAndLineAuditFields() throws Exception {
    long seed = Math.abs(System.nanoTime());
    long supplierId = seed;
    long productId = seed + 1L;
    long ruleId = seed + 2L;
    LocalDate orderDate = LocalDate.of(2026, 5, 6);

    prepareSequenceRule(ruleId);
    prepareSupplier(supplierId, "SUPP_AUDIT_" + seed);
    prepareProduct(productId, "SKU_AUDIT_" + seed, "审计采购商品");

    MvcResult result = mockMvc.perform(post("/api/purchase/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "supplierId": %d,
                              "orderDate": "%s",
                              "deliveryDate": "2026-05-08",
                              "remark": "采购审计校验",
                              "lines": [
                                {
                                  "productId": %d,
                                  "qty": 2.0000,
                                  "price": 50.00,
                                  "taxRate": 13.0000,
                                  "remark": "采购行"
                                }
                              ]
                            }
                            """.formatted(supplierId, orderDate, productId)))
            .andExpect(status().isOk())
            .andReturn();

    long orderId = readId(result);

    assertThat(jdbcTemplate.queryForObject("select company_id from pur_order where id = ?", Long.class, orderId)).isEqualTo(11L);
    assertThat(jdbcTemplate.queryForObject("select account_book_id from pur_order where id = ?", Long.class, orderId)).isEqualTo(22L);
    assertThat(jdbcTemplate.queryForObject("select created_by from pur_order where id = ?", Long.class, orderId)).isEqualTo(9301L);
    assertThat(jdbcTemplate.queryForObject("select updated_by from pur_order where id = ?", Long.class, orderId)).isEqualTo(9301L);
    assertThat(jdbcTemplate.queryForObject("select created_by from pur_order_line where order_id = ?", Long.class, orderId)).isEqualTo(9301L);
    assertThat(jdbcTemplate.queryForObject("select updated_by from pur_order_line where order_id = ?", Long.class, orderId)).isEqualTo(9301L);
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseOrderAuditFieldsTest" test
```

Expected:

```text
FAIL ... expected: 11L but was: 1L
```

- [x] **Step 3: Refactor `PurchaseOrderService` to consume `AuditMetadataFactory`**

Inject `AuditMetadataFactory` and replace hardcoded values in `create(...)`, `update(...)`, and `touch(...)`:

```java
AuditMetadata audit = auditMetadataFactory.current();
LocalDateTime now = audit.now();

entity.setCompanyId(audit.companyId());
entity.setAccountBookId(audit.accountBookId());
entity.setCreatedBy(audit.userId());
entity.setCreatedTime(now);
entity.setUpdatedBy(audit.userId());
entity.setUpdatedTime(now);
```

For order lines:

```java
line.setCreatedBy(audit.userId());
line.setCreatedTime(now);
line.setUpdatedBy(audit.userId());
line.setUpdatedTime(now);
```

For state transitions:

```java
private void touch(PurchaseOrderEntity entity) {
    AuditMetadata audit = auditMetadataFactory.current();
    entity.setUpdatedBy(audit.userId());
    entity.setUpdatedTime(audit.now());
}
```

- [x] **Step 4: Extend the same audit assertions to receipt and return**

Create `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptAuditInventoryTest.java` using the seed helpers from `PurchaseReceiptControllerPostTest` and add assertions after `POST /api/purchase/receipts/{id}/post`:

```java
assertThat(queryLong("select created_by from pur_receipt where id = ?", receiptId)).isEqualTo(9401L);
assertThat(queryLong("select updated_by from pur_receipt where id = ?", receiptId)).isEqualTo(9401L);
assertThat(queryLong("select created_by from pur_receipt_line where receipt_id = ?", receiptId)).isEqualTo(9401L);
assertThat(queryLong("select updated_by from inv_balance where warehouse_id = ? and product_id = ?", warehouseId, productId)).isEqualTo(9401L);
assertThat(queryLong("select created_by from inv_txn where biz_type = 'PURCHASE_RECEIPT' and biz_no = ?", receiptNo)).isEqualTo(9401L);
```

Create `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnAuditInventoryTest.java` using the seed helpers from `PurchaseReturnControllerPostTest` and add assertions after `POST /api/purchase/returns/{id}/post`:

```java
assertThat(queryLong("select created_by from pur_return where id = ?", returnId)).isEqualTo(9501L);
assertThat(queryLong("select updated_by from pur_return where id = ?", returnId)).isEqualTo(9501L);
assertThat(queryLong("select created_by from pur_return_line where return_id = ?", returnId)).isEqualTo(9501L);
assertThat(queryLong("select updated_by from pur_receipt_line where id = ?", receiptLineId)).isEqualTo(9501L);
assertThat(queryLong("select updated_by from pur_order_line where id = ?", orderLineId)).isEqualTo(9501L);
assertThat(queryLong("select created_by from inv_txn where biz_type = 'PURCHASE_RETURN' and biz_no = ?", returnNo)).isEqualTo(9501L);
```

- [x] **Step 5: Refactor `PurchaseReceiptService` and `PurchaseReturnService`**

Inject `AuditMetadataFactory` into both services and replace every `setCreatedBy(0L)`, `setUpdatedBy(0L)`, `setCompanyId(1L)`, `setAccountBookId(1L)` with `audit.userId()`, `audit.companyId()`, `audit.accountBookId()` and `audit.now()`.

This includes:
- receipt/return header create
- receipt/return line create
- cancel/post/update actions
- inventory balance insert/update
- inventory transaction insert
- receipt-line and order-line writeback during return post

Example replacement in `PurchaseReceiptService.post(...)`:

```java
AuditMetadata audit = auditMetadataFactory.current();
LocalDateTime now = audit.now();

receipt.setUpdatedBy(audit.userId());
receipt.setUpdatedTime(now);

orderLine.setUpdatedBy(audit.userId());
orderLine.setUpdatedTime(now);

balance.setCreatedBy(audit.userId());
balance.setUpdatedBy(audit.userId());

transaction.setCreatedBy(audit.userId());
transaction.setUpdatedBy(audit.userId());
```

- [x] **Step 6: Run purchase audit regression**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseOrderAuditFieldsTest,PurchaseReceiptAuditInventoryTest,PurchaseReturnAuditInventoryTest,PurchaseOrderControllerWorkflowTest,PurchaseReceiptControllerPostTest,PurchaseReturnControllerPostTest" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderAuditFieldsTest.java src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptAuditInventoryTest.java src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnAuditInventoryTest.java
git commit -m "feat: write authenticated audit fields in purchase flows"
```

---

### Task 4: Harden Sequence Number Generation With Optimistic Lock Retry

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/config/service/SequenceNumberGenerator.java`
- Create: `src/test/java/com/tuowei/erp/system/config/SequenceNumberGeneratorTest.java`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Modify: `src/main/java/com/tuowei/erp/system/config/model/SequenceRuleEntity.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderNumberService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptNumberService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnNumberService.java`

- [x] **Step 1: Write the failing concurrent number-generation test**

Create `src/test/java/com/tuowei/erp/system/config/SequenceNumberGeneratorTest.java`:

```java
package com.tuowei.erp.system.config;

import com.tuowei.erp.purchase.order.service.PurchaseOrderNumberService;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SequenceNumberGeneratorTest {

    @Autowired
    private PurchaseOrderNumberService purchaseOrderNumberService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from sys_sequence_rule where biz_type = 'PURCHASE_ORDER'");
    }

    @Test
    @WithErpUser(
            userId = 9601L,
            companyId = 1L,
            accountBookId = 1L,
            username = "sequence_user",
            realName = "编号操作员"
    )
    void concurrentCallsProduceUniqueOrderNumbers() throws Exception {
        jdbcTemplate.update("""
                insert into sys_sequence_rule
                (id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
                values (?, 'PURCHASE_ORDER', 'PO', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
                """, Math.abs(System.nanoTime()));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> purchaseOrderNumberService.nextOrderNo(LocalDate.of(2026, 5, 6)));
            }

            List<Future<String>> futures = executor.invokeAll(tasks);
            Set<String> numbers = new LinkedHashSet<>();
            for (Future<String> future : futures) {
                numbers.add(future.get());
            }

            assertThat(numbers).hasSize(8);
            assertThat(jdbcTemplate.queryForObject(
                    "select current_value from sys_sequence_rule where biz_type = 'PURCHASE_ORDER'",
                    Long.class
            )).isEqualTo(8L);
        } finally {
            executor.shutdownNow();
        }
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=SequenceNumberGeneratorTest" test
```

Expected:

```text
FAIL ... expected size: 8 but was smaller
```

- [x] **Step 3: Turn on optimistic locking for sequence rules**

Modify `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`:

```java
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
```

Add the interceptor:

```java
interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
```

Modify `src/main/java/com/tuowei/erp/system/config/model/SequenceRuleEntity.java`:

```java
import com.baomidou.mybatisplus.annotation.Version;
```

Annotate the field:

```java
@Version
private Integer version;
```

- [x] **Step 4: Centralize sequence increment with retry**

Create `src/main/java/com/tuowei/erp/system/config/service/SequenceNumberGenerator.java`:

```java
package com.tuowei.erp.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class SequenceNumberGenerator {

    private final SequenceRuleMapper sequenceRuleMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public SequenceNumberGenerator(SequenceRuleMapper sequenceRuleMapper, AuditMetadataFactory auditMetadataFactory) {
        this.sequenceRuleMapper = sequenceRuleMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    public String nextNumber(String bizType, String bizLabel, LocalDate bizDate) {
        for (int attempt = 0; attempt < 8; attempt++) {
            SequenceRuleEntity rule = sequenceRuleMapper.selectOne(new LambdaQueryWrapper<SequenceRuleEntity>()
                    .eq(SequenceRuleEntity::getBizType, bizType));
            if (rule == null) {
                throw new IllegalArgumentException(bizLabel + "编号规则不存在");
            }
            if (!"ACTIVE".equalsIgnoreCase(rule.getStatus())) {
                throw new IllegalArgumentException(bizLabel + "编号规则已停用");
            }

            long nextValue = (rule.getCurrentValue() == null ? 0L : rule.getCurrentValue()) + 1L;
            AuditMetadata audit = auditMetadataFactory.current();
            rule.setCurrentValue(nextValue);
            rule.setUpdatedBy(audit.userId());
            rule.setUpdatedTime(audit.now());

            if (sequenceRuleMapper.updateById(rule) == 1) {
                String datePart = bizDate.format(DateTimeFormatter.ofPattern(rule.getDatePattern()));
                String numberPart = String.format("%0" + rule.getSeqLength() + "d", nextValue);
                return rule.getPrefix() + datePart + numberPart;
            }
        }
        throw new IllegalStateException(bizLabel + "编号生成冲突，请重试");
    }
}
```

- [x] **Step 5: Delegate the three number services**

Modify `PurchaseOrderNumberService.java`:

```java
private final SequenceNumberGenerator sequenceNumberGenerator;

public PurchaseOrderNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
    this.sequenceNumberGenerator = sequenceNumberGenerator;
}

public String nextOrderNo(LocalDate bizDate) {
    return sequenceNumberGenerator.nextNumber("PURCHASE_ORDER", "采购订单", bizDate);
}
```

Modify `PurchaseReceiptNumberService.java`:

```java
public String nextReceiptNo(LocalDate bizDate) {
    return sequenceNumberGenerator.nextNumber("PURCHASE_RECEIPT", "采购入库单", bizDate);
}
```

Modify `PurchaseReturnNumberService.java`:

```java
public String nextReturnNo(LocalDate bizDate) {
    return sequenceNumberGenerator.nextNumber("PURCHASE_RETURN", "采购退货单", bizDate);
}
```

- [x] **Step 6: Run sequence regression**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=SequenceNumberGeneratorTest,PurchaseOrderControllerCreateDetailTest,PurchaseReceiptControllerCreateDetailTest,PurchaseReturnControllerCreateDetailTest" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java src/main/java/com/tuowei/erp/system/config/model/SequenceRuleEntity.java src/main/java/com/tuowei/erp/system/config/service/SequenceNumberGenerator.java src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderNumberService.java src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptNumberService.java src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnNumberService.java src/test/java/com/tuowei/erp/system/config/SequenceNumberGeneratorTest.java
git commit -m "feat: harden sequence generation with optimistic retry"
```

---

### Task 5: Verify Phase1 Scope And Leave Clean Follow-Up Boundaries

**Files:**
- Modify only files that fail verification

- [x] **Step 1: Search the targeted files for fake audit values**

Run:

```powershell
rg -n "setCompanyId\(1L\)|setAccountBookId\(1L\)|setCreatedBy\(0L\)|setUpdatedBy\(0L\)" src/main/java/com/tuowei/erp/system/user/service/UserService.java src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderNumberService.java src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptNumberService.java src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnNumberService.java
```

Expected:

```text
no matches
```

- [x] **Step 2: Run the focused regression pack**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=CurrentUserContextTest,UserAuditFieldsIntegrationTest,PurchaseOrderAuditFieldsTest,PurchaseReceiptAuditInventoryTest,PurchaseReturnAuditInventoryTest,SequenceNumberGeneratorTest,AuthAuthorizationIntegrationTest,SecurityConfigTest" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 3: Run the full suite**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 4: Capture the remaining hardcoded-audit backlog for the next plan**

Run:

```powershell
rg -n "setCompanyId\(1L\)|setAccountBookId\(1L\)|setCreatedBy\(0L\)|setUpdatedBy\(0L\)" src/main/java/com/tuowei/erp/masterdata src/main/java/com/tuowei/erp/system | rg -v "system/user/service/UserService.java"
```

Expected:

```text
输出剩余 masterdata 和 system 模块里的待清理位置，作为下一阶段计划输入
```

- [x] **Step 5: Commit cleanup if verification required code adjustments**

```powershell
git add src/main src/test
git commit -m "test: verify phase1 context audit sequence hardening"
```

Only commit if Step 1 through Step 4 forced additional changes. If verification passed with no new edits, skip this commit.

---

## Self-Review

- 规格覆盖：本计划只覆盖第一阶段最值钱的修复点，包含真实测试 principal、关键服务审计字段、库存回写审计字段和编号并发安全，没有把数据权限和日志审计混进来。
- 占位检查：全文没有 `TODO/TBD/以后再说/类似上一步` 这种偷懒写法。
- 类型一致性：测试 principal、`CurrentUser`、`AuditMetadata`、三个编号服务的命名在全文保持一致，没有前后乱改口。
- 范围边界：`masterdata` 其他模块、后台菜单配置 CRUD、日志体系、数据权限故意留给下一份计划，避免这份计划失控。

