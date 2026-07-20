# ERP User Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐系统管理模块的用户管理能力，打通用户建档、查询、维护、启停、角色分配及数据库迁移链路。

**Architecture:** 用户模块沿用现有 `controller + service + mapper + model + web` 结构，持久层继续使用 MyBatis-Plus，测试环境统一走 H2 + Flyway。用户角色分配先收口在 `UserService` 中，保持第一阶段边界清晰，不提前引入认证中心或 JWT 登录流程。

**Tech Stack:** Spring Boot 3.3.x, Spring Security Crypto, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for User and User Role

**Files:**
- Create: `src/main/resources/db/migration/V4__system_user_schema.sql`
- Create: `src/test/java/com/tuowei/erp/system/user/UserSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**

```java
package com.tuowei.erp.system.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesUserTables() {
        Integer userCount = jdbcTemplate.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLES where lower(TABLE_NAME) = 'sys_user'",
                Integer.class
        );
        Integer userRoleCount = jdbcTemplate.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLES where lower(TABLE_NAME) = 'sys_user_role'",
                Integer.class
        );

        assertThat(userCount).isEqualTo(1);
        assertThat(userRoleCount).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserSchemaMigrationTest test
```

Expected: FAIL because `sys_user` / `sys_user_role` 还没进入 Flyway 迁移。

- [x] **Step 3: Write minimal implementation**

Create `src/main/resources/db/migration/V4__system_user_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    dept_id BIGINT,
    post_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username ON sys_user (username);
CREATE INDEX IF NOT EXISTS idx_sys_user_dept_id ON sys_user (dept_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_user_id ON sys_user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_role_id ON sys_user_role (role_id);
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserSchemaMigrationTest test
```

Expected: PASS with user tables created by Flyway.

### Task 2: Implement User Create and List APIs

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java`
- Create: `src/main/java/com/tuowei/erp/system/user/model/UserEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/user/mapper/UserMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/user/web/UserCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/user/web/UserResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/user/service/UserService.java`
- Create: `src/main/java/com/tuowei/erp/system/user/controller/UserController.java`
- Create: `src/test/java/com/tuowei/erp/system/user/UserControllerCreateTest.java`

- [x] **Step 1: Write the failing integration test**

```java
package com.tuowei.erp.system.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin")
    void createsUserAndListsIt() throws Exception {
        mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "buyer01",
                                  "password": "P@ssw0rd123",
                                  "realName": "采购员张三",
                                  "mobile": "13800000001",
                                  "remark": "采购部门测试账号"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.username").value("buyer01"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/system/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].username").value("buyer01"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserControllerCreateTest test
```

Expected: FAIL because user controller, persistence, request model do not exist.

- [x] **Step 3: Write minimal implementation**

Update `SecurityConfig` to expose:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Create `UserEntity`:

```java
@TableName("sys_user")
public class UserEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String username;
    private String password;
    private String realName;
    private String mobile;
    private Long deptId;
    private Long postId;
    private String status;
    private Integer deletedFlag;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer version;
    // getters/setters
}
```

Create `UserCreateRequest`:

```java
public record UserCreateRequest(
        @NotBlank(message = "username不能为空") String username,
        @NotBlank(message = "password不能为空") String password,
        @NotBlank(message = "realName不能为空") String realName,
        String mobile,
        String remark
) {
}
```

Create `UserResponse`:

```java
public record UserResponse(
        Long id,
        String username,
        String realName,
        String mobile,
        String status,
        String remark
) {
}
```

Create `UserService` minimal methods:

```java
@Transactional
public UserResponse create(UserCreateRequest request) { ... }

public List<UserResponse> list() { ... }
```

Rules:
- `companyId = 1`
- `accountBookId = 1`
- `status = "ACTIVE"`
- `deletedFlag = 0`
- 密码用 `PasswordEncoder` 编码后落库
- 查询列表只返回非删除用户，按 `username ASC` 排序

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserControllerCreateTest test
```

Expected: PASS with successful user creation and list query.

### Task 3: Implement User Detail, Update, Enable/Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/user/web/UserUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/service/UserService.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/controller/UserController.java`
- Create: `src/test/java/com/tuowei/erp/system/user/UserControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**

```java
package com.tuowei.erp.system.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin")
    void updatesUserAndDisablesThenEnablesIt() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"stocker01","password":"P@ssw0rd123","realName":"库管李四","mobile":"13800000002","remark":"仓库账号"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long id = root.path("data").path("id").asLong();

        mockMvc.perform(put("/api/system/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"realName":"仓储主管李四","mobile":"13900000002","remark":"更新后的备注"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("仓储主管李四"));

        mockMvc.perform(post("/api/system/users/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/system/users/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/system/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("仓储主管李四"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserControllerLifecycleTest test
```

Expected: FAIL because user detail/update/enable/disable APIs do not exist.

- [x] **Step 3: Write minimal implementation**

Create `UserUpdateRequest`:

```java
public record UserUpdateRequest(
        @NotBlank(message = "realName不能为空") String realName,
        String mobile,
        String remark
) {
}
```

Extend `UserService`:

```java
public UserResponse getById(Long id) { ... }

@Transactional
public UserResponse update(Long id, UserUpdateRequest request) { ... }

@Transactional
public UserResponse enable(Long id) { ... }

@Transactional
public UserResponse disable(Long id) { ... }
```

Rules:
- 用户不存在或已逻辑删除时抛 `IllegalArgumentException("用户不存在")`
- 更新仅允许修改 `realName/mobile/remark`
- 启停只改 `status`

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserControllerLifecycleTest test
```

Expected: PASS with user lifecycle APIs all green.

### Task 4: Implement User Role Assignment APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/user/model/UserRoleEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/user/mapper/UserRoleMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/user/web/UserRoleAssignRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/user/web/UserRoleAssignmentResponse.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/service/UserService.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/controller/UserController.java`
- Create: `src/test/java/com/tuowei/erp/system/user/UserRoleAssignmentTest.java`

- [x] **Step 1: Write the failing authorization test**

```java
package com.tuowei.erp.system.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRoleAssignmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin")
    void assignsRolesToUser() throws Exception {
        MvcResult userResult = mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"finance01","password":"P@ssw0rd123","realName":"财务王五","mobile":"13800000003","remark":"财务账号"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult role1Result = mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"FINANCE_MANAGER","roleName":"财务经理","remark":"财务审批"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult role2Result = mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"CASHIER","roleName":"出纳","remark":"资金岗位"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        long roleId1 = objectMapper.readTree(role1Result.getResponse().getContentAsString()).path("data").path("id").asLong();
        long roleId2 = objectMapper.readTree(role2Result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(put("/api/system/users/{id}/roles", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds":[%d,%d]}
                                """.formatted(roleId1, roleId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.roleIds[0]").value(roleId1))
                .andExpect(jsonPath("$.data.roleIds[1]").value(roleId2));

        mockMvc.perform(get("/api/system/users/{id}/roles", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.roleIds[0]").value(roleId1))
                .andExpect(jsonPath("$.data.roleIds[1]").value(roleId2));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserRoleAssignmentTest test
```

Expected: FAIL because user role assignment APIs and relation mapper do not exist.

- [x] **Step 3: Write minimal implementation**

Create `UserRoleEntity`:

```java
@TableName("sys_user_role")
public class UserRoleEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
    private Long createdBy;
    private LocalDateTime createdTime;
    // getters/setters
}
```

Create `UserRoleAssignRequest`:

```java
public record UserRoleAssignRequest(
        @NotEmpty(message = "roleIds不能为空") List<Long> roleIds
) {
}
```

Create `UserRoleAssignmentResponse`:

```java
public record UserRoleAssignmentResponse(
        Long userId,
        List<Long> roleIds
) {
}
```

Extend `UserService`:

```java
@Transactional
public UserRoleAssignmentResponse assignRoles(Long userId, UserRoleAssignRequest request) { ... }

public UserRoleAssignmentResponse getAssignedRoles(Long userId) { ... }
```

Rules:
- 先校验用户存在
- 先校验全部角色存在且未删除
- 分配时先删旧关系，再插入新关系
- 返回结构至少包含 `userId`、`roleIds`

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserRoleAssignmentTest test
```

Expected: PASS with user role assignment working.

