# ERP Menu Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐系统管理模块的菜单管理与角色菜单授权能力，打通菜单建档、树查询、启停维护、角色授权及数据库迁移链路。

**Architecture:** 菜单模块沿用现有 `controller + service + mapper + model + web` 结构，持久层继续使用 MyBatis-Plus，测试环境统一走 H2 + Flyway。角色授权不额外新建独立服务，先在现有 `RoleService` 中收口角色菜单分配逻辑，避免第一阶段过度设计。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for Menu and Role Menu

**Files:**
- Create: `src/main/resources/db/migration/V3__system_menu_schema.sql`
- Create: `src/test/java/com/tuowei/erp/system/menu/MenuSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**

```java
package com.tuowei.erp.system.menu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MenuSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesMenuTables() {
        Integer menuCount = jdbcTemplate.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLES where lower(TABLE_NAME) = 'sys_menu'",
                Integer.class
        );
        Integer roleMenuCount = jdbcTemplate.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLES where lower(TABLE_NAME) = 'sys_role_menu'",
                Integer.class
        );

        assertThat(menuCount).isEqualTo(1);
        assertThat(roleMenuCount).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=MenuSchemaMigrationTest test
```

Expected: FAIL because `sys_menu` / `sys_role_menu` 还没进入 Flyway 迁移。

- [x] **Step 3: Write minimal implementation**

Create `src/main/resources/db/migration/V3__system_menu_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_type VARCHAR(32) NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(64) NOT NULL,
    path VARCHAR(255),
    component VARCHAR(255),
    permission VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    visible_flag TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_menu_menu_code ON sys_menu (menu_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_menu_role_id_menu_id ON sys_role_menu (role_id, menu_id);
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent_id ON sys_menu (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_role_id ON sys_role_menu (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu_id ON sys_role_menu (menu_id);
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=MenuSchemaMigrationTest test
```

Expected: PASS with menu tables created by Flyway.

### Task 2: Implement Menu Create and Tree APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/menu/model/MenuEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/menu/mapper/MenuMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/menu/web/MenuCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/menu/web/MenuResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/menu/service/MenuService.java`
- Create: `src/main/java/com/tuowei/erp/system/menu/controller/MenuController.java`
- Create: `src/test/java/com/tuowei/erp/system/menu/MenuControllerTreeTest.java`

- [x] **Step 1: Write the failing integration test**

```java
package com.tuowei.erp.system.menu;

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
class MenuControllerTreeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin")
    void createsMenusAndReturnsTree() throws Exception {
        mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 0,
                                  "menuType": "CATALOG",
                                  "menuCode": "PURCHASE",
                                  "menuName": "采购管理",
                                  "path": "/purchase",
                                  "component": "Layout",
                                  "sortNo": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuCode").value("PURCHASE"));

        mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 0,
                                  "menuType": "MENU",
                                  "menuCode": "PURCHASE_ORDER",
                                  "menuName": "采购订单",
                                  "path": "/purchase/orders",
                                  "component": "purchase/order/index",
                                  "permission": "purchase:order:view",
                                  "sortNo": 2
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/menus/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].menuCode").value("PURCHASE"))
                .andExpect(jsonPath("$.data[1].menuCode").value("PURCHASE_ORDER"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=MenuControllerTreeTest test
```

Expected: FAIL because menu module and controller do not exist.

- [x] **Step 3: Write minimal implementation**

Create `MenuEntity` with字段：

```java
@TableName("sys_menu")
public class MenuEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long parentId;
    private String menuType;
    private String menuCode;
    private String menuName;
    private String path;
    private String component;
    private String permission;
    private Integer sortNo;
    private Integer visibleFlag;
    private String status;
    private Integer deletedFlag;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer version;
    // getters/setters
}
```

Create `MenuCreateRequest`:

```java
public record MenuCreateRequest(
        Long parentId,
        @NotBlank(message = "menuType不能为空") String menuType,
        @NotBlank(message = "menuCode不能为空") String menuCode,
        @NotBlank(message = "menuName不能为空") String menuName,
        String path,
        String component,
        String permission,
        Integer sortNo
) {
}
```

Create `MenuResponse`:

```java
public record MenuResponse(
        Long id,
        Long parentId,
        String menuType,
        String menuCode,
        String menuName,
        String path,
        String component,
        String permission,
        Integer sortNo,
        Integer visibleFlag,
        String status,
        List<MenuResponse> children
) {
}
```

Create `MenuService` minimal methods:

```java
@Transactional
public MenuResponse create(MenuCreateRequest request) { ... }

public List<MenuResponse> tree() { ... }
```

Rules:
- `parentId` 为空时默认 `0`
- `visibleFlag = 1`
- `status = "ACTIVE"`
- `deletedFlag = 0`
- 先按 `sortNo ASC, id ASC` 查全量菜单，再在内存组装树

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=MenuControllerTreeTest test
```

Expected: PASS with menu create and tree query available.

### Task 3: Implement Menu Detail, Update, Enable/Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/menu/web/MenuUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/menu/service/MenuService.java`
- Modify: `src/main/java/com/tuowei/erp/system/menu/controller/MenuController.java`
- Create: `src/test/java/com/tuowei/erp/system/menu/MenuControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**

```java
package com.tuowei.erp.system.menu;

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
class MenuControllerLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin")
    void updatesMenuAndDisablesThenEnablesIt() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":0,"menuType":"MENU","menuCode":"STOCK","menuName":"库存台账","path":"/inventory/ledger","component":"inventory/ledger/index","permission":"inventory:ledger:view","sortNo":3}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long id = root.path("data").path("id").asLong();

        mockMvc.perform(put("/api/system/menus/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuName":"库存总账","path":"/inventory/general-ledger","component":"inventory/general-ledger/index","permission":"inventory:general-ledger:view","sortNo":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuName").value("库存总账"));

        mockMvc.perform(post("/api/system/menus/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/system/menus/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/system/menus/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuName").value("库存总账"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=MenuControllerLifecycleTest test
```

Expected: FAIL because menu detail/update/status APIs do not exist.

- [x] **Step 3: Write minimal implementation**

Create `MenuUpdateRequest`:

```java
public record MenuUpdateRequest(
        @NotBlank(message = "menuName不能为空") String menuName,
        String path,
        String component,
        String permission,
        Integer sortNo
) {
}
```

Extend `MenuService`:

```java
public MenuResponse getById(Long id) { ... }

@Transactional
public MenuResponse update(Long id, MenuUpdateRequest request) { ... }

@Transactional
public MenuResponse enable(Long id) { ... }

@Transactional
public MenuResponse disable(Long id) { ... }
```

Rules:
- 菜单不存在或已逻辑删除时抛 `IllegalArgumentException("菜单不存在")`
- 更新仅允许修改 `menuName/path/component/permission/sortNo`
- 启停只改 `status`

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=MenuControllerLifecycleTest test
```

Expected: PASS with menu lifecycle APIs all green.

### Task 4: Implement Role Menu Assignment APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/menu/model/RoleMenuEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/menu/mapper/RoleMenuMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/role/web/RoleMenuAssignRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/role/service/RoleService.java`
- Modify: `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java`
- Create: `src/test/java/com/tuowei/erp/system/role/RoleMenuAuthorizationTest.java`

- [x] **Step 1: Write the failing authorization test**

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
class RoleMenuAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin")
    void assignsMenusToRole() throws Exception {
        MvcResult roleResult = mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"PURCHASE_MANAGER","roleName":"采购经理","remark":"采购审批"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult menu1Result = mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":0,"menuType":"MENU","menuCode":"PUR_ORDER","menuName":"采购订单","path":"/purchase/orders","component":"purchase/order/index","permission":"purchase:order:view","sortNo":1}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult menu2Result = mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":0,"menuType":"MENU","menuCode":"PUR_RECEIPT","menuName":"采购入库","path":"/purchase/receipts","component":"purchase/receipt/index","permission":"purchase:receipt:view","sortNo":2}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long roleId = objectMapper.readTree(roleResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        long menuId1 = objectMapper.readTree(menu1Result.getResponse().getContentAsString()).path("data").path("id").asLong();
        long menuId2 = objectMapper.readTree(menu2Result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(put("/api/system/roles/{id}/menus", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuIds":[%d,%d]}
                                """.formatted(menuId1, menuId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(roleId))
                .andExpect(jsonPath("$.data.menuIds[0]").value(menuId1))
                .andExpect(jsonPath("$.data.menuIds[1]").value(menuId2));

        mockMvc.perform(get("/api/system/roles/{id}/menus", roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuIds[0]").value(menuId1))
                .andExpect(jsonPath("$.data.menuIds[1]").value(menuId2));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleMenuAuthorizationTest test
```

Expected: FAIL because role menu assignment APIs and relation mapper do not exist.

- [x] **Step 3: Write minimal implementation**

Create `RoleMenuEntity`:

```java
@TableName("sys_role_menu")
public class RoleMenuEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private Long menuId;
    private Long createdBy;
    private LocalDateTime createdTime;
    // getters/setters
}
```

Create `RoleMenuAssignRequest`:

```java
public record RoleMenuAssignRequest(
        @NotEmpty(message = "menuIds不能为空") List<Long> menuIds
) {
}
```

Extend `RoleService`:

```java
@Transactional
public RoleMenuAssignmentResponse assignMenus(Long roleId, RoleMenuAssignRequest request) { ... }

public RoleMenuAssignmentResponse getAssignedMenus(Long roleId) { ... }
```

Rules:
- 先校验角色存在
- 先校验全部菜单存在且未删除
- 分配时先删旧关系，再批量插入新关系
- 返回结构至少包含 `roleId`、`menuIds`

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=RoleMenuAuthorizationTest test
```

Expected: PASS with role menu assignment working.

