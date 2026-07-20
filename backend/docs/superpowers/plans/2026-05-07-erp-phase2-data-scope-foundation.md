# ERP Data Scope Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为采购订单、采购入库、采购退货查询补齐基于角色和用户聚合的数据权限底座，并把数据范围快照接入认证上下文与登录返回。

**Architecture:** 采用服务层中心化数据权限方案：通过 Flyway 新增角色/用户数据范围表，登录装载阶段生成不可变 `DataScopeSnapshot` 并塞入 `ErpPrincipal`，采购相关 service 的列表与详情查询复用 `DataScopeService` 统一拼装过滤条件和越权断言。列表权限尽量下推到 MyBatis-Plus wrapper，详情统一抛 `AccessDeniedException` 并由全局异常处理收口为 `403`。

**Tech Stack:** Spring Boot 3.3.x, Spring Security + JWT, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

**New database migration**
- Create: `src/main/resources/db/migration/V16__system_data_scope_schema.sql`

**New data scope module**
- Create: `src/main/java/com/tuowei/erp/system/datascope/model/RoleDataScopeEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/model/UserDataScopeEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/mapper/RoleDataScopeMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/mapper/UserDataScopeMapper.java`

**Security changes**
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeRule.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeSnapshot.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/CurrentUser.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`

**Auth response changes**
- Modify: `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java`
- Modify: `src/main/java/com/tuowei/erp/system/auth/web/LoginUserResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/auth/web/LoginUserDataScopeResponse.java`

**Purchase service changes**
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`

**Test support changes**
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`

**New tests**
- Create: `src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java`
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderDataScopeTest.java`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptDataScopeTest.java`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnDataScopeTest.java`

**Existing tests to update**
- Modify: `src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java`
- Modify: `src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java`

## Task 1: Add schema and data scope persistence mapping

**Files:**
- Create: `src/main/resources/db/migration/V16__system_data_scope_schema.sql`
- Create: `src/main/java/com/tuowei/erp/system/datascope/model/RoleDataScopeEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/model/UserDataScopeEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/mapper/RoleDataScopeMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/mapper/UserDataScopeMapper.java`
- Create: `src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java`

- [x] **Step 1: Write the failing aggregation smoke test**

```java
package com.tuowei.erp.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DataScopeServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataScopeService dataScopeService;

    @Test
    void loadsAdminAllScopeFromSeedData() {
        Long adminRoleId = jdbcTemplate.queryForObject(
                "select id from sys_role where role_code = 'SUPER_ADMIN'",
                Long.class
        );

        assertThat(adminRoleId).isNotNull();

        DataScopeSnapshot snapshot = dataScopeService.buildSnapshot(1L);

        assertThat(snapshot.hasAllScope()).isTrue();
        assertThat(snapshot.deptScoped()).isFalse();
        assertThat(snapshot.postScoped()).isFalse();
        assertThat(snapshot.selfScoped()).isFalse();
        assertThat(snapshot.warehouseIds()).isEmpty();
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest test`

Expected: FAIL because `DataScopeService` and V16 schema do not exist.

- [x] **Step 3: Add migration with constraints and seed data**

```sql
CREATE TABLE sys_role_data_scope (
    id BIGINT NOT NULL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    warehouse_id BIGINT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sys_role_data_scope_type
        CHECK (scope_type IN ('ALL', 'DEPT', 'POST', 'WAREHOUSE', 'SELF')),
    CONSTRAINT chk_sys_role_data_scope_warehouse
        CHECK (
            (scope_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL)
            OR (scope_type <> 'WAREHOUSE' AND warehouse_id IS NULL)
        )
);

CREATE TABLE sys_user_data_scope (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    warehouse_id BIGINT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sys_user_data_scope_type
        CHECK (scope_type IN ('ALL', 'DEPT', 'POST', 'WAREHOUSE', 'SELF')),
    CONSTRAINT chk_sys_user_data_scope_warehouse
        CHECK (
            (scope_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL)
            OR (scope_type <> 'WAREHOUSE' AND warehouse_id IS NULL)
        )
);

CREATE UNIQUE INDEX uk_sys_role_data_scope_unique
    ON sys_role_data_scope (role_id, scope_type, warehouse_id);

CREATE UNIQUE INDEX uk_sys_user_data_scope_unique
    ON sys_user_data_scope (user_id, scope_type, warehouse_id);

INSERT INTO sys_role_data_scope (id, role_id, scope_type, warehouse_id, created_by)
SELECT 16001, r.id, 'ALL', NULL, 0
FROM sys_role r
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_data_scope s
      WHERE s.role_id = r.id
        AND s.scope_type = 'ALL'
  );
```

```java
@TableName("sys_role_data_scope")
public class RoleDataScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private String scopeType;
    private Long warehouseId;
    private Long createdBy;
    private LocalDateTime createdTime;
}
```

```java
@Mapper
public interface RoleDataScopeMapper extends BaseMapper<RoleDataScopeEntity> {
}
```

- [x] **Step 4: Mirror the mapping for user scopes**

```java
@TableName("sys_user_data_scope")
public class UserDataScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String scopeType;
    private Long warehouseId;
    private Long createdBy;
    private LocalDateTime createdTime;
}
```

```java
@Mapper
public interface UserDataScopeMapper extends BaseMapper<UserDataScopeEntity> {
}
```

- [x] **Step 5: Run test to verify compile still fails for missing service**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest test`

Expected: FAIL because `DataScopeService` / `DataScopeSnapshot` are still missing.

- [x] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V16__system_data_scope_schema.sql src/main/java/com/tuowei/erp/system/datascope src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java
git commit -m "feat: add data scope schema and mappers"
```

## Task 2: Implement data scope aggregation and default deny semantics

**Files:**
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeRule.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeSnapshot.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java`

- [x] **Step 1: Extend the failing test for union and deny-by-default**

```java
@AfterEach
void cleanup() {
    jdbcTemplate.update("delete from sys_user_data_scope where user_id in (99101, 99111)");
    jdbcTemplate.update("delete from sys_role_data_scope where role_id in (99102)");
    jdbcTemplate.update("delete from sys_user_role where user_id in (99101, 99111)");
    jdbcTemplate.update("delete from sys_role where id in (99102)");
    jdbcTemplate.update("delete from sys_user where id in (99101, 99111)");
}

@Test
void mergesRoleAndUserScopesByUnion() {
    jdbcTemplate.update("""
            insert into sys_user
            (id, company_id, account_book_id, username, password, real_name, dept_id, post_id, status, deleted_flag, created_by, updated_by, version)
            values (99101, 1, 1, 'scope_union_user', 'N/A', '并集用户', 11, 21, 'ACTIVE', 0, 0, 0, 0)
            """);
    jdbcTemplate.update("""
            insert into sys_role
            (id, role_code, role_name, status, deleted_flag, created_by, updated_by, version)
            values (99102, 'ROLE_SCOPE_UNION', '并集角色', 'ACTIVE', 0, 0, 0, 0)
            """);
    jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (99103, 99101, 99102, 0)");
    jdbcTemplate.update("insert into sys_role_data_scope (id, role_id, scope_type, warehouse_id, created_by) values (99104, 99102, 'DEPT', null, 0)");
    jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (99105, 99101, 'SELF', null, 0)");
    jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (99106, 99101, 'WAREHOUSE', 3001, 0)");

    DataScopeSnapshot snapshot = dataScopeService.buildSnapshot(99101L);

    assertThat(snapshot.hasAllScope()).isFalse();
    assertThat(snapshot.deptScoped()).isTrue();
    assertThat(snapshot.postScoped()).isFalse();
    assertThat(snapshot.selfScoped()).isTrue();
    assertThat(snapshot.warehouseIds()).containsExactly(3001L);
}

@Test
void returnsNoneWhenNoScopeConfigured() {
    jdbcTemplate.update("""
            insert into sys_user
            (id, company_id, account_book_id, username, password, real_name, dept_id, post_id, status, deleted_flag, created_by, updated_by, version)
            values (99111, 1, 1, 'scope_empty_user', 'N/A', '空权限用户', 12, 22, 'ACTIVE', 0, 0, 0, 0)
            """);

    DataScopeSnapshot snapshot = dataScopeService.buildSnapshot(99111L);

    assertThat(snapshot.hasAllScope()).isFalse();
    assertThat(snapshot.deptScoped()).isFalse();
    assertThat(snapshot.postScoped()).isFalse();
    assertThat(snapshot.selfScoped()).isFalse();
    assertThat(snapshot.warehouseIds()).isEmpty();
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest test`

Expected: FAIL because aggregation logic is missing.

- [x] **Step 3: Implement the enum and immutable snapshot**

```java
public enum DataScopeRule {
    ALL,
    DEPT,
    POST,
    WAREHOUSE,
    SELF;

    public static DataScopeRule from(String value) {
        return DataScopeRule.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
```

```java
public record DataScopeSnapshot(
        boolean hasAllScope,
        boolean deptScoped,
        boolean postScoped,
        boolean selfScoped,
        Set<Long> warehouseIds
) {
    public DataScopeSnapshot {
        warehouseIds = Set.copyOf(warehouseIds);
    }

    public static DataScopeSnapshot all() {
        return new DataScopeSnapshot(true, false, false, false, Set.of());
    }

    public static DataScopeSnapshot none() {
        return new DataScopeSnapshot(false, false, false, false, Set.of());
    }
}
```

- [x] **Step 4: Implement aggregation service**

```java
@Service
public class DataScopeService {

    private final UserRoleMapper userRoleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final UserDataScopeMapper userDataScopeMapper;

    public DataScopeSnapshot buildSnapshot(Long userId) {
        Set<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RoleDataScopeEntity> roleScopes = roleIds.isEmpty()
                ? List.of()
                : roleDataScopeMapper.selectList(new LambdaQueryWrapper<RoleDataScopeEntity>()
                        .in(RoleDataScopeEntity::getRoleId, roleIds));
        List<UserDataScopeEntity> userScopes = userDataScopeMapper.selectList(
                new LambdaQueryWrapper<UserDataScopeEntity>()
                        .eq(UserDataScopeEntity::getUserId, userId)
        );

        boolean hasAll = false;
        boolean dept = false;
        boolean post = false;
        boolean self = false;
        Set<Long> warehouseIds = new LinkedHashSet<>();

        for (RoleDataScopeEntity entity : roleScopes) {
            DataScopeRule rule = DataScopeRule.from(entity.getScopeType());
            hasAll |= rule == DataScopeRule.ALL;
            dept |= rule == DataScopeRule.DEPT;
            post |= rule == DataScopeRule.POST;
            self |= rule == DataScopeRule.SELF;
            if (rule == DataScopeRule.WAREHOUSE && entity.getWarehouseId() != null) {
                warehouseIds.add(entity.getWarehouseId());
            }
        }
        for (UserDataScopeEntity entity : userScopes) {
            DataScopeRule rule = DataScopeRule.from(entity.getScopeType());
            hasAll |= rule == DataScopeRule.ALL;
            dept |= rule == DataScopeRule.DEPT;
            post |= rule == DataScopeRule.POST;
            self |= rule == DataScopeRule.SELF;
            if (rule == DataScopeRule.WAREHOUSE && entity.getWarehouseId() != null) {
                warehouseIds.add(entity.getWarehouseId());
            }
        }

        if (hasAll) {
            return DataScopeSnapshot.all();
        }
        if (!dept && !post && !self && warehouseIds.isEmpty()) {
            return DataScopeSnapshot.none();
        }
        return new DataScopeSnapshot(false, dept, post, self, warehouseIds);
    }
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest test`

Expected: PASS

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/tuowei/erp/common/security/DataScopeRule.java src/main/java/com/tuowei/erp/common/security/DataScopeSnapshot.java src/main/java/com/tuowei/erp/common/security/DataScopeService.java src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java
git commit -m "feat: add data scope snapshot aggregation"
```

## Task 3: Attach department, post, and snapshot to authenticated principal

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/CurrentUser.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java`
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithErpUser.java`
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java`

- [x] **Step 1: Write the failing principal and current user tests**

```java
@Autowired
private DatabaseUserDetailsService userDetailsService;

@Test
void loadsPrincipalWithDeptPostAndDataScopeSnapshot() {
    ErpPrincipal principal = userDetailsService.loadPrincipalByUserId(1L);

    assertThat(principal.dataScopeSnapshot()).isNotNull();
    assertThat(principal.dataScopeSnapshot().hasAllScope()).isTrue();
    assertThat(principal.deptId()).isNotNull();
    assertThat(principal.postId()).isNotNull();
}
```

```java
@Test
@WithErpUser(
        userId = 9001L,
        companyId = 88L,
        accountBookId = 66L,
        deptId = 18L,
        postId = 28L,
        username = "planner",
        realName = "计划员王五"
)
void returnsErpPrincipalSnapshot() {
    CurrentUser currentUser = currentUserContext.requireCurrentUser();

    assertThat(currentUser.deptId()).isEqualTo(18L);
    assertThat(currentUser.postId()).isEqualTo(28L);
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest,CurrentUserContextTest test`

Expected: FAIL because principal, current user, and test support do not carry dept/post/snapshot.

- [x] **Step 3: Extend principal, current user, and context**

```java
public record ErpPrincipal(
        Long userId,
        Long companyId,
        Long accountBookId,
        Long deptId,
        Long postId,
        String username,
        String realName,
        String password,
        Set<String> permissions,
        DataScopeSnapshot dataScopeSnapshot
) implements UserDetails {
```

```java
public record CurrentUser(
        Long userId,
        Long companyId,
        Long accountBookId,
        Long deptId,
        Long postId,
        String username,
        String realName
) {
}
```

```java
public ErpPrincipal requirePrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof ErpPrincipal principal)) {
        throw new IllegalStateException("当前用户未登录");
    }
    return principal;
}
```

```java
return new CurrentUser(
        principal.userId(),
        principal.companyId(),
        principal.accountBookId(),
        principal.deptId(),
        principal.postId(),
        principal.username(),
        principal.realName()
);
```

- [x] **Step 4: Wire snapshot creation into user loading**

```java
private final DataScopeService dataScopeService;

private ErpPrincipal toPrincipal(UserEntity user) {
    return new ErpPrincipal(
            user.getId(),
            user.getCompanyId(),
            user.getAccountBookId(),
            user.getDeptId(),
            user.getPostId(),
            user.getUsername(),
            user.getRealName(),
            user.getPassword(),
            userPermissionService.loadPermissions(user.getId()),
            dataScopeService.buildSnapshot(user.getId())
    );
}
```

- [x] **Step 5: Update test support annotations**

```java
public @interface WithErpUser {

    long userId() default 1L;
    long companyId() default 1L;
    long accountBookId() default 1L;
    long deptId() default 1L;
    long postId() default 1L;
    String username() default "admin";
    String realName() default "系统管理员";
    String[] authorities() default {};
}
```

```java
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
        DataScopeSnapshot.none()
);
```

- [x] **Step 6: Run tests to verify they pass**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest,CurrentUserContextTest test`

Expected: PASS

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java src/main/java/com/tuowei/erp/common/security/CurrentUser.java src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java src/test/java/com/tuowei/erp/common/security/CurrentUserContextTest.java src/test/java/com/tuowei/erp/testsupport/WithErpUser.java src/test/java/com/tuowei/erp/testsupport/WithErpUserSecurityContextFactory.java
git commit -m "feat: carry data scope context in principal"
```

## Task 4: Return data scope summary from login and map detail denials to 403

**Files:**
- Modify: `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java`
- Modify: `src/main/java/com/tuowei/erp/system/auth/web/LoginUserResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/auth/web/LoginUserDataScopeResponse.java`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java`

- [x] **Step 1: Extend login test to assert data scope summary**

```java
@Test
void logsInActiveUserAndReturnsBearerTokenWithPermissions() throws Exception {
    String username = "login_admin_" + seed;
    seedUser(seed, username, "ACTIVE");
    seedRoleAndPermission(seed, "system:user:list");

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"password\":\"password\"}\n".formatted(username)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.dataScope.hasAllScope").value(true))
            .andExpect(jsonPath("$.data.user.dataScope.deptScoped").value(false))
            .andExpect(jsonPath("$.data.user.dataScope.postScoped").value(false))
            .andExpect(jsonPath("$.data.user.dataScope.selfScoped").value(false))
            .andExpect(jsonPath("$.data.user.dataScope.warehouseIds.length()").value(0));
}
```

- [x] **Step 2: Run auth test to verify it fails**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=AuthControllerLoginTest test`

Expected: FAIL because login response has no data scope summary.

- [x] **Step 3: Add response records and map scope summary**

```java
public record LoginUserResponse(
        Long id,
        String username,
        String realName,
        LoginUserDataScopeResponse dataScope
) {
}
```

```java
public record LoginUserDataScopeResponse(
        boolean hasAllScope,
        boolean deptScoped,
        boolean postScoped,
        boolean selfScoped,
        List<Long> warehouseIds
) {
}
```

```java
new LoginUserResponse(
        principal.userId(),
        principal.username(),
        principal.realName(),
        new LoginUserDataScopeResponse(
                principal.dataScopeSnapshot().hasAllScope(),
                principal.dataScopeSnapshot().deptScoped(),
                principal.dataScopeSnapshot().postScoped(),
                principal.dataScopeSnapshot().selfScoped(),
                principal.dataScopeSnapshot().warehouseIds().stream().sorted().toList()
        )
)
```

- [x] **Step 4: Add explicit AccessDenied handler in global exception flow**

```java
@ExceptionHandler(AccessDeniedException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public ApiResponse<String> handleAccessDenied(AccessDeniedException ex) {
    return new ApiResponse<>("403", ex.getMessage(), null);
}
```

- [x] **Step 5: Run auth test to verify it passes**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=AuthControllerLoginTest test`

Expected: PASS

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/tuowei/erp/system/auth/service/AuthService.java src/main/java/com/tuowei/erp/system/auth/web/LoginResponse.java src/main/java/com/tuowei/erp/system/auth/web/LoginUserResponse.java src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java
git commit -m "feat: expose login data scope summary"
```

## Task 5: Enforce purchase order list and detail data scope

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderDataScopeTest.java`

- [x] **Step 1: Write failing purchase order scope tests**

```java
package com.tuowei.erp.purchase.order;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseOrderDataScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from pur_order_line where order_id in (92001, 92002)");
        jdbcTemplate.update("delete from pur_order where id in (92001, 92002)");
        jdbcTemplate.update("delete from sys_user_data_scope where user_id in (91001, 91002)");
        jdbcTemplate.update("delete from sys_user where id in (91001, 91002)");
    }

    @Test
    @WithErpUser(
            userId = 91001L,
            deptId = 11L,
            postId = 21L,
            username = "self_scope_user",
            realName = "自建用户",
            authorities = {"purchase:order:view"}
    )
    void selfScopeOnlyReturnsOwnOrders() throws Exception {
        seedUser(91001L, 11L, 21L, "self_scope_user");
        seedUser(91002L, 11L, 21L, "other_scope_user");
        seedOrder(92001L, 91001L, "PO_SELF_VISIBLE");
        seedOrder(92002L, 91002L, "PO_SELF_HIDDEN");
        jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (93001, 91001, 'SELF', null, 0)");

        mockMvc.perform(get("/api/purchase/orders").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("PO_SELF_VISIBLE"));
    }

    @Test
    @WithErpUser(
            userId = 91001L,
            deptId = 11L,
            postId = 21L,
            username = "warehouse_only_user",
            realName = "仓库范围用户",
            authorities = {"purchase:order:view"}
    )
    void warehouseOnlyScopeBlocksOrderDetail() throws Exception {
        seedUser(91001L, 11L, 21L, "warehouse_only_user");
        seedUser(91002L, 12L, 22L, "other_scope_user");
        seedOrder(92002L, 91002L, "PO_SCOPE_BLOCKED");
        jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (93002, 91001, 'WAREHOUSE', 2001, 0)");

        mockMvc.perform(get("/api/purchase/orders/{id}", 92002L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权访问该采购订单"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseOrderDataScopeTest test`

Expected: FAIL because purchase order queries are not scope-aware.

- [x] **Step 3: Add order-specific scope helpers**

```java
public LambdaQueryWrapper<PurchaseOrderEntity> applyPurchaseOrderScope(
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper,
        CurrentUser currentUser,
        DataScopeSnapshot snapshot,
        Set<Long> deptUserIds,
        Set<Long> postUserIds
) {
    if (snapshot.hasAllScope()) {
        return wrapper;
    }

    List<Long> selfIds = snapshot.selfScoped() ? List.of(currentUser.userId()) : List.of();
    Set<Long> visibleCreatorIds = new LinkedHashSet<>();
    visibleCreatorIds.addAll(selfIds);
    if (snapshot.deptScoped()) {
        visibleCreatorIds.addAll(deptUserIds);
    }
    if (snapshot.postScoped()) {
        visibleCreatorIds.addAll(postUserIds);
    }
    if (visibleCreatorIds.isEmpty()) {
        return wrapper.apply("1 = 0");
    }
    return wrapper.and(query -> query.in(PurchaseOrderEntity::getCreatedBy, visibleCreatorIds));
}

public void assertCanViewPurchaseOrder(
        PurchaseOrderEntity entity,
        CurrentUser currentUser,
        DataScopeSnapshot snapshot,
        Long creatorDeptId,
        Long creatorPostId
) {
    if (snapshot.hasAllScope()) {
        return;
    }
    if (snapshot.selfScoped() && Objects.equals(entity.getCreatedBy(), currentUser.userId())) {
        return;
    }
    if (snapshot.deptScoped() && Objects.equals(creatorDeptId, currentUser.deptId())) {
        return;
    }
    if (snapshot.postScoped() && Objects.equals(creatorPostId, currentUser.postId())) {
        return;
    }
    throw new AccessDeniedException("无权访问该采购订单");
}
```

- [x] **Step 4: Apply wrapper filtering and detail assertion**

```java
public PageResponse<PurchaseOrderResponse> list(PurchaseOrderPageQuery query) {
    CurrentUser currentUser = currentUserContext.requireCurrentUser();
    DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
    Set<Long> deptUserIds = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getDeletedFlag, 0)
                    .eq(UserEntity::getStatus, "ACTIVE")
                    .eq(snapshot.deptScoped(), UserEntity::getDeptId, currentUser.deptId()))
            .stream()
            .map(UserEntity::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<Long> postUserIds = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getDeletedFlag, 0)
                    .eq(UserEntity::getStatus, "ACTIVE")
                    .eq(snapshot.postScoped(), UserEntity::getPostId, currentUser.postId()))
            .stream()
            .map(UserEntity::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    LambdaQueryWrapper<PurchaseOrderEntity> wrapper = buildListQuery(keyword, status, approvalStatus, query.getSupplierId());
    wrapper = dataScopeService.applyPurchaseOrderScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);

    Page<PurchaseOrderEntity> result = purchaseOrderMapper.selectPage(page, wrapper);
    ...
}
```

```java
public PurchaseOrderResponse getById(Long id) {
    PurchaseOrderEntity entity = requireOrder(id);
    CurrentUser currentUser = currentUserContext.requireCurrentUser();
    ErpPrincipal principal = currentUserContext.requirePrincipal();
    UserEntity creator = userMapper.selectById(entity.getCreatedBy());
    dataScopeService.assertCanViewPurchaseOrder(
            entity,
            currentUser,
            principal.dataScopeSnapshot(),
            creator == null ? null : creator.getDeptId(),
            creator == null ? null : creator.getPostId()
    );
    ...
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseOrderDataScopeTest test`

Expected: PASS

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/tuowei/erp/common/security/DataScopeService.java src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderDataScopeTest.java
git commit -m "feat: enforce purchase order data scope"
```

## Task 6: Enforce purchase receipt list and detail data scope

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptDataScopeTest.java`

- [x] **Step 1: Write failing receipt scope tests**

```java
@Test
@WithErpUser(
        userId = 91101L,
        deptId = 11L,
        postId = 21L,
        username = "warehouse_receipt_user",
        realName = "仓库入库用户",
        authorities = {"purchase:receipt:view"}
)
void warehouseScopeOnlyReturnsAuthorizedReceipts() throws Exception {
    seedReceiptUser(91101L, 11L, 21L, "warehouse_receipt_user");
    seedReceiptUser(91102L, 12L, 22L, "other_receipt_user");
    seedReceipt(92101L, 91101L, 2001L, "PR_SCOPE_VISIBLE");
    seedReceipt(92102L, 91102L, 2002L, "PR_SCOPE_HIDDEN");
    jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (93101, 91101, 'WAREHOUSE', 2001, 0)");

    mockMvc.perform(get("/api/purchase/receipts").param("pageNo", "1").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].receiptNo").value("PR_SCOPE_VISIBLE"));
}

@Test
@WithErpUser(
        userId = 91101L,
        deptId = 11L,
        postId = 21L,
        username = "warehouse_receipt_user",
        realName = "仓库入库用户",
        authorities = {"purchase:receipt:view"}
)
void warehouseScopeBlocksForeignReceiptDetail() throws Exception {
    seedReceiptUser(91101L, 11L, 21L, "warehouse_receipt_user");
    seedReceiptUser(91102L, 12L, 22L, "other_receipt_user");
    seedReceipt(92102L, 91102L, 2002L, "PR_SCOPE_HIDDEN");
    jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (93101, 91101, 'WAREHOUSE', 2001, 0)");

    mockMvc.perform(get("/api/purchase/receipts/{id}", 92102L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("无权访问该采购入库单"));
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseReceiptDataScopeTest test`

Expected: FAIL because receipts are not scope-aware.

- [x] **Step 3: Add receipt-specific wrapper and assertion**

```java
public LambdaQueryWrapper<PurchaseReceiptEntity> applyPurchaseReceiptScope(
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper,
        CurrentUser currentUser,
        DataScopeSnapshot snapshot,
        Set<Long> deptUserIds,
        Set<Long> postUserIds
) {
    if (snapshot.hasAllScope()) {
        return wrapper;
    }

    return wrapper.and(query -> {
        boolean hasAny = false;
        if (snapshot.selfScoped()) {
            query.eq(PurchaseReceiptEntity::getCreatedBy, currentUser.userId());
            hasAny = true;
        }
        if (snapshot.deptScoped() && !deptUserIds.isEmpty()) {
            if (hasAny) {
                query.or();
            }
            query.in(PurchaseReceiptEntity::getCreatedBy, deptUserIds);
            hasAny = true;
        }
        if (snapshot.postScoped() && !postUserIds.isEmpty()) {
            if (hasAny) {
                query.or();
            }
            query.in(PurchaseReceiptEntity::getCreatedBy, postUserIds);
            hasAny = true;
        }
        if (!snapshot.warehouseIds().isEmpty()) {
            if (hasAny) {
                query.or();
            }
            query.in(PurchaseReceiptEntity::getWarehouseId, snapshot.warehouseIds());
            hasAny = true;
        }
        if (!hasAny) {
            query.apply("1 = 0");
        }
    });
}
```

```java
public void assertCanViewPurchaseReceipt(
        PurchaseReceiptEntity entity,
        CurrentUser currentUser,
        DataScopeSnapshot snapshot,
        Long creatorDeptId,
        Long creatorPostId
) {
    if (snapshot.hasAllScope()) {
        return;
    }
    if (snapshot.selfScoped() && Objects.equals(entity.getCreatedBy(), currentUser.userId())) {
        return;
    }
    if (snapshot.deptScoped() && Objects.equals(creatorDeptId, currentUser.deptId())) {
        return;
    }
    if (snapshot.postScoped() && Objects.equals(creatorPostId, currentUser.postId())) {
        return;
    }
    if (snapshot.warehouseIds().contains(entity.getWarehouseId())) {
        return;
    }
    throw new AccessDeniedException("无权访问该采购入库单");
}
```

- [x] **Step 4: Apply the scope helpers in receipt service**

```java
LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = buildListQuery(keyword, query.getOrderId(), query.getWarehouseId(), status, query.getReceiptDateFrom(), query.getReceiptDateTo());
wrapper = dataScopeService.applyPurchaseReceiptScope(wrapper, currentUser, principal.dataScopeSnapshot(), deptUserIds, postUserIds);
Page<PurchaseReceiptEntity> result = purchaseReceiptMapper.selectPage(page, wrapper);
```

```java
PurchaseReceiptEntity receipt = requireReceipt(id);
UserEntity creator = userMapper.selectById(receipt.getCreatedBy());
dataScopeService.assertCanViewPurchaseReceipt(
        receipt,
        currentUser,
        principal.dataScopeSnapshot(),
        creator == null ? null : creator.getDeptId(),
        creator == null ? null : creator.getPostId()
);
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseReceiptDataScopeTest test`

Expected: PASS

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/tuowei/erp/common/security/DataScopeService.java src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptDataScopeTest.java
git commit -m "feat: enforce purchase receipt data scope"
```

## Task 7: Enforce purchase return list and detail data scope

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnDataScopeTest.java`

- [x] **Step 1: Write failing return scope tests**

```java
@Test
@WithErpUser(
        userId = 91201L,
        deptId = 11L,
        postId = 21L,
        username = "warehouse_return_user",
        realName = "仓库退货用户",
        authorities = {"purchase:return:view"}
)
void warehouseScopeOnlyReturnsAuthorizedReturns() throws Exception {
    seedReturnUser(91201L, 11L, 21L, "warehouse_return_user");
    seedReturnUser(91202L, 12L, 22L, "other_return_user");
    seedReturn(92201L, 91201L, 2001L, "PRT_SCOPE_VISIBLE");
    seedReturn(92202L, 91202L, 2002L, "PRT_SCOPE_HIDDEN");
    jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (93201, 91201, 'WAREHOUSE', 2001, 0)");

    mockMvc.perform(get("/api/purchase/returns").param("pageNo", "1").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].returnNo").value("PRT_SCOPE_VISIBLE"));
}

@Test
@WithErpUser(
        userId = 91201L,
        deptId = 11L,
        postId = 21L,
        username = "warehouse_return_user",
        realName = "仓库退货用户",
        authorities = {"purchase:return:view"}
)
void warehouseScopeBlocksForeignReturnDetail() throws Exception {
    seedReturnUser(91201L, 11L, 21L, "warehouse_return_user");
    seedReturnUser(91202L, 12L, 22L, "other_return_user");
    seedReturn(92202L, 91202L, 2002L, "PRT_SCOPE_HIDDEN");
    jdbcTemplate.update("insert into sys_user_data_scope (id, user_id, scope_type, warehouse_id, created_by) values (93201, 91201, 'WAREHOUSE', 2001, 0)");

    mockMvc.perform(get("/api/purchase/returns/{id}", 92202L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("无权访问该采购退货单"));
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseReturnDataScopeTest test`

Expected: FAIL because returns are not scope-aware.

- [x] **Step 3: Add return-specific wrapper and assertion**

```java
public LambdaQueryWrapper<PurchaseReturnEntity> applyPurchaseReturnScope(
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper,
        CurrentUser currentUser,
        DataScopeSnapshot snapshot,
        Set<Long> deptUserIds,
        Set<Long> postUserIds
) {
    if (snapshot.hasAllScope()) {
        return wrapper;
    }

    return wrapper.and(query -> {
        boolean hasAny = false;
        if (snapshot.selfScoped()) {
            query.eq(PurchaseReturnEntity::getCreatedBy, currentUser.userId());
            hasAny = true;
        }
        if (snapshot.deptScoped() && !deptUserIds.isEmpty()) {
            if (hasAny) {
                query.or();
            }
            query.in(PurchaseReturnEntity::getCreatedBy, deptUserIds);
            hasAny = true;
        }
        if (snapshot.postScoped() && !postUserIds.isEmpty()) {
            if (hasAny) {
                query.or();
            }
            query.in(PurchaseReturnEntity::getCreatedBy, postUserIds);
            hasAny = true;
        }
        if (!snapshot.warehouseIds().isEmpty()) {
            if (hasAny) {
                query.or();
            }
            query.in(PurchaseReturnEntity::getWarehouseId, snapshot.warehouseIds());
            hasAny = true;
        }
        if (!hasAny) {
            query.apply("1 = 0");
        }
    });
}

public void assertCanViewPurchaseReturn(
        PurchaseReturnEntity entity,
        CurrentUser currentUser,
        DataScopeSnapshot snapshot,
        Long creatorDeptId,
        Long creatorPostId
) {
    if (snapshot.hasAllScope()) {
        return;
    }
    if (snapshot.selfScoped() && Objects.equals(entity.getCreatedBy(), currentUser.userId())) {
        return;
    }
    if (snapshot.deptScoped() && Objects.equals(creatorDeptId, currentUser.deptId())) {
        return;
    }
    if (snapshot.postScoped() && Objects.equals(creatorPostId, currentUser.postId())) {
        return;
    }
    if (snapshot.warehouseIds().contains(entity.getWarehouseId())) {
        return;
    }
    throw new AccessDeniedException("无权访问该采购退货单");
}
```

- [x] **Step 4: Apply the scope helpers in return service**

```java
LambdaQueryWrapper<PurchaseReturnEntity> wrapper = buildListQuery(keyword, query.getReceiptId(), query.getWarehouseId(), status, query.getReturnDateFrom(), query.getReturnDateTo());
wrapper = dataScopeService.applyPurchaseReturnScope(wrapper, currentUser, principal.dataScopeSnapshot(), deptUserIds, postUserIds);
Page<PurchaseReturnEntity> result = purchaseReturnMapper.selectPage(page, wrapper);
```

```java
PurchaseReturnEntity entity = requireReturn(id);
UserEntity creator = userMapper.selectById(entity.getCreatedBy());
dataScopeService.assertCanViewPurchaseReturn(
        entity,
        currentUser,
        principal.dataScopeSnapshot(),
        creator == null ? null : creator.getDeptId(),
        creator == null ? null : creator.getPostId()
);
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseReturnDataScopeTest test`

Expected: PASS

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/tuowei/erp/common/security/DataScopeService.java src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnDataScopeTest.java
git commit -m "feat: enforce purchase return data scope"
```

## Task 8: Run focused regression and full suite

**Files:**
- Modify: `src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java`
- Modify: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderDataScopeTest.java`
- Modify: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptDataScopeTest.java`
- Modify: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnDataScopeTest.java`

- [x] **Step 1: Run the new focused tests together**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=DataScopeServiceTest,CurrentUserContextTest,AuthControllerLoginTest,PurchaseOrderDataScopeTest,PurchaseReceiptDataScopeTest,PurchaseReturnDataScopeTest test`

Expected: PASS

- [x] **Step 2: Run the existing purchase regression tests**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" -Dtest=PurchaseOrderControllerWorkflowTest,PurchaseReceiptControllerPostTest,PurchaseReturnControllerPostTest test`

Expected: PASS

- [x] **Step 3: Run the full suite**

Run: `mvn "-Dmaven.repo.local=E:/tuowei/python/erpServer/.m2/repository" test`

Expected: `BUILD SUCCESS`

- [x] **Step 4: Commit**

```bash
git add src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderDataScopeTest.java src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptDataScopeTest.java src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnDataScopeTest.java
git commit -m "test: verify purchase data scope regression"
```

## Self-Review

**Spec coverage:** 已覆盖 schema、快照聚合、认证接入、登录摘要、采购订单/入库/退货列表与详情控制、默认拒绝、`403` 语义、测试与回归验证。未扩展到主数据、销售、财务、报表，和 spec 保持一致。

**Placeholder scan:** 计划内没有 `TODO`、`TBD`、"类似前一个任务" 之类偷懒描述；每个任务都包含明确文件路径、测试命令和预期结果。

**Type consistency:** `ErpPrincipal`、`CurrentUser`、`WithErpUser`、`DataScopeSnapshot`、`DataScopeService` 的新增字段与方法在后续任务中保持一致；测试注解统一使用 `authorities` 字段，不再混入不存在的 `permissions` 字段。

