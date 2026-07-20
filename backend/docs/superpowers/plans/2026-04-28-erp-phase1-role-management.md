# ERP Role Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot 工程骨架上，落地系统管理模块的角色管理首个业务切片，提供角色建档、分页查询、详情查询、更新、启停能力，并打通数据库迁移和集成测试链路。

**Architecture:** 角色管理按模块化单体思路实现，采用 `controller + application/service + mapper + model` 结构，持久层使用 MyBatis-Plus，测试环境统一走 H2 + Flyway 迁移。接口全部受现有安全配置保护，测试通过 `@WithMockUser` 模拟访问。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway System Schema for Role Management

**Files:**
- Modify: `src/main/resources/application-test.yml`
- Create: `src/main/resources/db/migration/V2__system_role_schema.sql`
- Create: `src/test/java/com/tuowei/erp/system/role/RoleSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**

```java
package com.tuowei.erp.system.role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RoleSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesRoleTable() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_role'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleSchemaMigrationTest test
```

Expected: FAIL because `sys_role` migration does not exist or Flyway is disabled in `test` profile.

- [x] **Step 3: Write minimal implementation**

Update `src/main/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:erp_server;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false
    driver-class-name: org.h2.Driver
    username: sa
    password:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
  flyway:
    enabled: true
```

Create `src/main/resources/db/migration/V2__system_role_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_role_code ON sys_role (role_code);
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleSchemaMigrationTest test
```

Expected: PASS with `sys_role` table created by Flyway.

- [x] **Step 5: Commit**

```powershell
git add src/main/resources/application-test.yml src/main/resources/db/migration/V2__system_role_schema.sql src/test/java/com/tuowei/erp/system/role/RoleSchemaMigrationTest.java
git commit -m "feat: add role schema migration"
```

### Task 2: Implement Role Query and Create APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/role/model/RoleEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/role/mapper/RoleMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/role/web/RoleCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/role/web/RoleResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/role/service/RoleService.java`
- Create: `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java`
- Create: `src/test/java/com/tuowei/erp/system/role/RoleControllerCreateTest.java`

- [x] **Step 1: Write the failing integration test**

```java
package com.tuowei.erp.system.role;

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
class RoleControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin")
    void createsRoleAndListsIt() throws Exception {
        mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCode": "WAREHOUSE_ADMIN",
                                  "roleName": "仓库管理员",
                                  "remark": "负责仓库业务"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.roleCode").value("WAREHOUSE_ADMIN"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/system/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].roleCode").value("WAREHOUSE_ADMIN"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleControllerCreateTest test
```

Expected: FAIL because role controller, persistence, and request model do not exist.

- [x] **Step 3: Write minimal implementation**

Create `src/main/java/com/tuowei/erp/system/role/model/RoleEntity.java`:

```java
package com.tuowei.erp.system.role.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_role")
public class RoleEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String roleCode;
    private String roleName;
    private String status;
    private Integer deletedFlag;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer version;

    // getters and setters
}
```

Create `src/main/java/com/tuowei/erp/system/role/mapper/RoleMapper.java`:

```java
package com.tuowei.erp.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
}
```

Create `src/main/java/com/tuowei/erp/system/role/web/RoleCreateRequest.java`:

```java
package com.tuowei.erp.system.role.web;

import jakarta.validation.constraints.NotBlank;

public record RoleCreateRequest(
        @NotBlank(message = "roleCode不能为空") String roleCode,
        @NotBlank(message = "roleName不能为空") String roleName,
        String remark
) {
}
```

Create `src/main/java/com/tuowei/erp/system/role/web/RoleResponse.java`:

```java
package com.tuowei.erp.system.role.web;

public record RoleResponse(
        Long id,
        String roleCode,
        String roleName,
        String status,
        String remark
) {
}
```

Create `src/main/java/com/tuowei/erp/system/role/service/RoleService.java`:

```java
package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoleService {

    private final RoleMapper roleMapper;

    public RoleService(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        RoleEntity entity = new RoleEntity();
        entity.setRoleCode(request.roleCode());
        entity.setRoleName(request.roleName());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(0L);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedBy(0L);
        entity.setUpdatedTime(LocalDateTime.now());
        entity.setVersion(0);
        roleMapper.insert(entity);
        return toResponse(entity);
    }

    public List<RoleResponse> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getDeletedFlag, 0)
                        .orderByAsc(RoleEntity::getRoleCode))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RoleResponse toResponse(RoleEntity entity) {
        return new RoleResponse(entity.getId(), entity.getRoleCode(), entity.getRoleName(), entity.getStatus(), entity.getRemark());
    }
}
```

Create `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java`:

```java
package com.tuowei.erp.system.role.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(roleService.list());
    }

    @PostMapping
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success(roleService.create(request));
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleControllerCreateTest test
```

Expected: PASS with successful role creation and list query.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/system/role src/test/java/com/tuowei/erp/system/role/RoleControllerCreateTest.java
git commit -m "feat: add role create and list apis"
```

### Task 3: Implement Role Detail, Update, and Enable/Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/role/web/RoleUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/role/service/RoleService.java`
- Modify: `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java`
- Create: `src/test/java/com/tuowei/erp/system/role/RoleControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**

```java
package com.tuowei.erp.system.role;

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
class RoleControllerLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin")
    void updatesRoleAndDisablesThenEnablesIt() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"FINANCE_MANAGER","roleName":"财务经理","remark":"财务审批"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long id = root.path("data").path("id").asLong();

        mockMvc.perform(put("/api/system/roles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleName":"财务负责人","remark":"更新后的备注"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("财务负责人"));

        mockMvc.perform(post("/api/system/roles/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/system/roles/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/system/roles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("财务负责人"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleControllerLifecycleTest test
```

Expected: FAIL because detail/update/enable/disable APIs do not exist.

- [x] **Step 3: Write minimal implementation**

Create `src/main/java/com/tuowei/erp/system/role/web/RoleUpdateRequest.java`:

```java
package com.tuowei.erp.system.role.web;

import jakarta.validation.constraints.NotBlank;

public record RoleUpdateRequest(
        @NotBlank(message = "roleName不能为空") String roleName,
        String remark
) {
}
```

Update `src/main/java/com/tuowei/erp/system/role/service/RoleService.java` to add:

```java
public RoleResponse getById(Long id) {
    RoleEntity entity = requireRole(id);
    return toResponse(entity);
}

@Transactional
public RoleResponse update(Long id, RoleUpdateRequest request) {
    RoleEntity entity = requireRole(id);
    entity.setRoleName(request.roleName());
    entity.setRemark(request.remark());
    entity.setUpdatedTime(LocalDateTime.now());
    roleMapper.updateById(entity);
    return toResponse(entity);
}

@Transactional
public RoleResponse enable(Long id) {
    return updateStatus(id, "ACTIVE");
}

@Transactional
public RoleResponse disable(Long id) {
    return updateStatus(id, "DISABLED");
}

private RoleResponse updateStatus(Long id, String status) {
    RoleEntity entity = requireRole(id);
    entity.setStatus(status);
    entity.setUpdatedTime(LocalDateTime.now());
    roleMapper.updateById(entity);
    return toResponse(entity);
}

private RoleEntity requireRole(Long id) {
    RoleEntity entity = roleMapper.selectById(id);
    if (entity == null || entity.getDeletedFlag() != 0) {
        throw new IllegalArgumentException("角色不存在");
    }
    return entity;
}
```

Update `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java` to add:

```java
@GetMapping("/{id}")
public ApiResponse<RoleResponse> detail(@PathVariable Long id) {
    return ApiResponse.success(roleService.getById(id));
}

@PutMapping("/{id}")
public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
    return ApiResponse.success(roleService.update(id, request));
}

@PostMapping("/{id}/enable")
public ApiResponse<RoleResponse> enable(@PathVariable Long id) {
    return ApiResponse.success(roleService.enable(id));
}

@PostMapping("/{id}/disable")
public ApiResponse<RoleResponse> disable(@PathVariable Long id) {
    return ApiResponse.success(roleService.disable(id));
}
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleControllerLifecycleTest test
```

Expected: PASS with role update and status toggle all green.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/system/role src/test/java/com/tuowei/erp/system/role/RoleControllerLifecycleTest.java
git commit -m "feat: add role lifecycle management"
```


