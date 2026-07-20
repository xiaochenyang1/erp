# Production Routing Work Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend support for production work centers and BOM-bound routings, including schema, permissions, menu seeds, CRUD APIs, and automated tests.

**Architecture:** Build two new production submodules: `workcenter` for independent shop-floor master data and `routing` for BOM-bound process definitions with ordered operations. Reuse current Spring Boot + MyBatis-Plus patterns, keep routing isolated from production-order execution in this phase, and enforce tenant/account-book boundaries everywhere.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway SQL migrations, JUnit 5, MockMvc, Mockito, AssertJ.

---

## File Map

- Create: `src/main/resources/db/migration/V92__production_routing_work_center_schema.sql`
  - Add `prd_work_center`, `prd_routing`, and `prd_routing_operation`.
- Create: `src/main/resources/db/migration/V93__production_routing_work_center_menu_seed.sql`
  - Seed production work-center/routing menus and `ERP_ADMIN` role bindings.
- Modify: `src/main/java/com/tuowei/erp/common/security/ProductionPermissionCodes.java`
  - Add work-center and routing permission strings plus `HAS_*` expressions.

- Create: `src/main/java/com/tuowei/erp/production/workcenter/model/ProductionWorkCenterEntity.java`
  - MyBatis-Plus entity for `prd_work_center`.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/mapper/ProductionWorkCenterMapper.java`
  - Mapper for work-center CRUD.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterCreateRequest.java`
  - Create DTO.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterUpdateRequest.java`
  - Update DTO without mutable code field.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterResponse.java`
  - API response DTO.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterPageQuery.java`
  - List query DTO.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/service/ProductionWorkCenterService.java`
  - Work-center business logic, validation, and status changes.
- Create: `src/main/java/com/tuowei/erp/production/workcenter/controller/ProductionWorkCenterController.java`
  - REST endpoints under `/api/production/work-centers`.
- Create: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterControllerTest.java`
  - MockMvc happy-path HTTP contract test.
- Create: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTest.java`
  - SpringBootTest duplicate-code and status-rule coverage.
- Create: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTenantBoundaryTest.java`
  - Mockito tenant/account-book guard coverage.

- Create: `src/main/java/com/tuowei/erp/production/routing/model/ProductionRoutingEntity.java`
  - MyBatis-Plus entity for `prd_routing`.
- Create: `src/main/java/com/tuowei/erp/production/routing/model/ProductionRoutingOperationEntity.java`
  - MyBatis-Plus entity for `prd_routing_operation`.
- Create: `src/main/java/com/tuowei/erp/production/routing/mapper/ProductionRoutingMapper.java`
  - Mapper for routing heads.
- Create: `src/main/java/com/tuowei/erp/production/routing/mapper/ProductionRoutingOperationMapper.java`
  - Mapper for ordered routing operations.
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingCreateRequest.java`
  - Create DTO.
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingUpdateRequest.java`
  - Update DTO without mutable code/BOM fields.
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingOperationRequest.java`
  - Operation line input DTO.
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingOperationResponse.java`
  - Operation line response DTO.
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingResponse.java`
  - Routing detail/list response DTO.
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingPageQuery.java`
  - List query DTO with `bomId`.
- Create: `src/main/java/com/tuowei/erp/production/routing/service/ProductionRoutingService.java`
  - Routing validation, persistence, enable/disable, and response assembly.
- Create: `src/main/java/com/tuowei/erp/production/routing/controller/ProductionRoutingController.java`
  - REST endpoints under `/api/production/routings`.
- Create: `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingControllerTest.java`
  - MockMvc routing HTTP contract test.
- Create: `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingServiceTest.java`
  - SpringBootTest routing validation/replacement coverage.
- Create: `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingServiceTenantBoundaryTest.java`
  - Mockito tenant/account-book guard coverage.

## Task 1: Work Center Schema, Permissions, And Happy Path

**Files:**
- Create: `src/main/resources/db/migration/V92__production_routing_work_center_schema.sql`
- Create: `src/main/resources/db/migration/V93__production_routing_work_center_menu_seed.sql`
- Modify: `src/main/java/com/tuowei/erp/common/security/ProductionPermissionCodes.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/model/ProductionWorkCenterEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/mapper/ProductionWorkCenterMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/web/ProductionWorkCenterPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/service/ProductionWorkCenterService.java`
- Create: `src/main/java/com/tuowei/erp/production/workcenter/controller/ProductionWorkCenterController.java`
- Create: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterControllerTest.java`:

```java
package com.tuowei.erp.production.workcenter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionWorkCenterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from prd_routing_operation");
        jdbcTemplate.update("delete from prd_routing");
        jdbcTemplate.update("delete from prd_work_center");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_product where id between 894000 and 894999");
    }

    @Test
    @WithErpUser(authorities = {
            "production:work-center:create",
            "production:work-center:view",
            "production:work-center:update",
            "production:work-center:enable",
            "production:work-center:disable"
    })
    void createsReadsDisablesAndEnablesWorkCenterThroughHttpApi() throws Exception {
        long id = idOf(mockMvc.perform(post("/api/production/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workCenterCode": "WC-1001",
                                  "workCenterName": "装配一线",
                                  "remark": "controller test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.workCenterCode").value("WC-1001"))
                .andReturn());

        mockMvc.perform(get("/api/production/work-centers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workCenterName").value("装配一线"));

        mockMvc.perform(post("/api/production/work-centers/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/production/work-centers/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("id").asLong();
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '工作中心测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '工作中心测试', 894001, 894001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo, long productId, long materialProductId) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1.0000, 'ACTIVE', 0, 'seed bom', 894001, 894001, 0)
                """, id, bomNo, productId);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate, remark, created_by, updated_by, version)
                values (894102, 1, 1, ?, 1, ?, 1.0000, 0.0000, 'seed line', 894001, 894001, 0)
                """, id, materialProductId);
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterControllerTest test
```

Expected: FAIL with `404` / `No mapping for POST /api/production/work-centers` because the controller does not exist yet.

- [ ] **Step 3: Add schema and permission scaffolding**

Create `src/main/resources/db/migration/V92__production_routing_work_center_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS prd_work_center (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    work_center_code VARCHAR(64) NOT NULL,
    work_center_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_routing (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    routing_code VARCHAR(64) NOT NULL,
    routing_name VARCHAR(128) NOT NULL,
    bom_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_routing_operation (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    routing_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    work_center_id BIGINT NOT NULL,
    standard_minutes DECIMAL(18, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_prd_work_center_company_book_code
    ON prd_work_center (company_id, account_book_id, work_center_code);
CREATE INDEX idx_prd_work_center_company_book_status
    ON prd_work_center (company_id, account_book_id, status);

CREATE UNIQUE INDEX uk_prd_routing_company_book_code
    ON prd_routing (company_id, account_book_id, routing_code);
CREATE UNIQUE INDEX uk_prd_routing_company_book_bom
    ON prd_routing (company_id, account_book_id, bom_id);
CREATE INDEX idx_prd_routing_company_book_status
    ON prd_routing (company_id, account_book_id, status);

CREATE UNIQUE INDEX uk_prd_routing_operation_company_book_line
    ON prd_routing_operation (company_id, account_book_id, routing_id, line_no);
CREATE UNIQUE INDEX uk_prd_routing_operation_company_book_code
    ON prd_routing_operation (company_id, account_book_id, routing_id, operation_code);
CREATE INDEX idx_prd_routing_operation_company_book_work_center
    ON prd_routing_operation (company_id, account_book_id, work_center_id);
```

Create `src/main/resources/db/migration/V93__production_routing_work_center_menu_seed.sql`:

```sql
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5090, 5080, 'MENU', 'PRODUCTION_WORK_CENTER', '工作中心', '/production/work-centers', 'production/work-center/index', 'production:work-center:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5091, 5090, 'BUTTON', 'PRODUCTION_WORK_CENTER_CREATE', '创建工作中心', NULL, NULL, 'production:work-center:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5092, 5090, 'BUTTON', 'PRODUCTION_WORK_CENTER_UPDATE', '修改工作中心', NULL, NULL, 'production:work-center:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5093, 5090, 'BUTTON', 'PRODUCTION_WORK_CENTER_ENABLE', '启用工作中心', NULL, NULL, 'production:work-center:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5094, 5090, 'BUTTON', 'PRODUCTION_WORK_CENTER_DISABLE', '停用工作中心', NULL, NULL, 'production:work-center:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5095, 5080, 'MENU', 'PRODUCTION_ROUTING', '工艺路线', '/production/routings', 'production/routing/index', 'production:routing:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5096, 5095, 'BUTTON', 'PRODUCTION_ROUTING_CREATE', '创建工艺路线', NULL, NULL, 'production:routing:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5097, 5095, 'BUTTON', 'PRODUCTION_ROUTING_UPDATE', '修改工艺路线', NULL, NULL, 'production:routing:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5098, 5095, 'BUTTON', 'PRODUCTION_ROUTING_ENABLE', '启用工艺路线', NULL, NULL, 'production:routing:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5099, 5095, 'BUTTON', 'PRODUCTION_ROUTING_DISABLE', '停用工艺路线', NULL, NULL, 'production:routing:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7110, 3002, 5090, 0),
    (7111, 3002, 5091, 0),
    (7112, 3002, 5092, 0),
    (7113, 3002, 5093, 0),
    (7114, 3002, 5094, 0),
    (7115, 3002, 5095, 0),
    (7116, 3002, 5096, 0),
    (7117, 3002, 5097, 0),
    (7118, 3002, 5098, 0),
    (7119, 3002, 5099, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

Modify `src/main/java/com/tuowei/erp/common/security/ProductionPermissionCodes.java`:

```java
    String PRODUCTION_WORK_CENTER_VIEW = "production:work-center:view";
    String PRODUCTION_WORK_CENTER_CREATE = "production:work-center:create";
    String PRODUCTION_WORK_CENTER_UPDATE = "production:work-center:update";
    String PRODUCTION_WORK_CENTER_ENABLE = "production:work-center:enable";
    String PRODUCTION_WORK_CENTER_DISABLE = "production:work-center:disable";
    String PRODUCTION_ROUTING_VIEW = "production:routing:view";
    String PRODUCTION_ROUTING_CREATE = "production:routing:create";
    String PRODUCTION_ROUTING_UPDATE = "production:routing:update";
    String PRODUCTION_ROUTING_ENABLE = "production:routing:enable";
    String PRODUCTION_ROUTING_DISABLE = "production:routing:disable";

    String HAS_PRODUCTION_WORK_CENTER_VIEW = "hasAuthority('" + PRODUCTION_WORK_CENTER_VIEW + "')";
    String HAS_PRODUCTION_WORK_CENTER_CREATE = "hasAuthority('" + PRODUCTION_WORK_CENTER_CREATE + "')";
    String HAS_PRODUCTION_WORK_CENTER_UPDATE = "hasAuthority('" + PRODUCTION_WORK_CENTER_UPDATE + "')";
    String HAS_PRODUCTION_WORK_CENTER_ENABLE = "hasAuthority('" + PRODUCTION_WORK_CENTER_ENABLE + "')";
    String HAS_PRODUCTION_WORK_CENTER_DISABLE = "hasAuthority('" + PRODUCTION_WORK_CENTER_DISABLE + "')";
    String HAS_PRODUCTION_ROUTING_VIEW = "hasAuthority('" + PRODUCTION_ROUTING_VIEW + "')";
    String HAS_PRODUCTION_ROUTING_CREATE = "hasAuthority('" + PRODUCTION_ROUTING_CREATE + "')";
    String HAS_PRODUCTION_ROUTING_UPDATE = "hasAuthority('" + PRODUCTION_ROUTING_UPDATE + "')";
    String HAS_PRODUCTION_ROUTING_ENABLE = "hasAuthority('" + PRODUCTION_ROUTING_ENABLE + "')";
    String HAS_PRODUCTION_ROUTING_DISABLE = "hasAuthority('" + PRODUCTION_ROUTING_DISABLE + "')";
```

- [ ] **Step 4: Add the minimal work-center implementation**

Create `src/main/java/com/tuowei/erp/production/workcenter/model/ProductionWorkCenterEntity.java`:

```java
package com.tuowei.erp.production.workcenter.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("prd_work_center")
public class ProductionWorkCenterEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String workCenterCode;
    private String workCenterName;
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
    public String getWorkCenterCode() { return workCenterCode; }
    public void setWorkCenterCode(String workCenterCode) { this.workCenterCode = workCenterCode; }
    public String getWorkCenterName() { return workCenterName; }
    public void setWorkCenterName(String workCenterName) { this.workCenterName = workCenterName; }
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

Create `src/main/java/com/tuowei/erp/production/workcenter/mapper/ProductionWorkCenterMapper.java`:

```java
package com.tuowei.erp.production.workcenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionWorkCenterMapper extends BaseMapper<ProductionWorkCenterEntity> {
}
```

Create the DTOs:

```java
package com.tuowei.erp.production.workcenter.web;

import jakarta.validation.constraints.NotBlank;

public record ProductionWorkCenterCreateRequest(
        @NotBlank(message = "工作中心编码不能为空") String workCenterCode,
        @NotBlank(message = "工作中心名称不能为空") String workCenterName,
        String remark
) {
}
```

```java
package com.tuowei.erp.production.workcenter.web;

import jakarta.validation.constraints.NotBlank;

public record ProductionWorkCenterUpdateRequest(
        @NotBlank(message = "工作中心名称不能为空") String workCenterName,
        String remark
) {
}
```

```java
package com.tuowei.erp.production.workcenter.web;

public record ProductionWorkCenterResponse(
        Long id,
        String workCenterCode,
        String workCenterName,
        String status,
        String remark
) {
}
```

```java
package com.tuowei.erp.production.workcenter.web;

public class ProductionWorkCenterPageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

Create `src/main/java/com/tuowei/erp/production/workcenter/service/ProductionWorkCenterService.java`:

```java
package com.tuowei.erp.production.workcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class ProductionWorkCenterService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionWorkCenterService(
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ProductionWorkCenterResponse create(ProductionWorkCenterCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setWorkCenterCode(requireText(request.workCenterCode(), "工作中心编码不能为空"));
        entity.setWorkCenterName(requireText(request.workCenterName(), "工作中心名称不能为空"));
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0);
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        workCenterMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public ProductionWorkCenterResponse getById(Long id) {
        return toResponse(requireWorkCenter(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionWorkCenterResponse> list(ProductionWorkCenterPageQuery query) {
        ProductionWorkCenterPageQuery safeQuery = query == null ? new ProductionWorkCenterPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionWorkCenterEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ProductionWorkCenterEntity> wrapper = new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionWorkCenterEntity::getDeletedFlag, 0);
        String keyword = normalizeNullable(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(ProductionWorkCenterEntity::getWorkCenterCode, keyword)
                    .or()
                    .like(ProductionWorkCenterEntity::getWorkCenterName, keyword));
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (status != null) {
            wrapper.eq(ProductionWorkCenterEntity::getStatus, status);
        }
        wrapper.orderByAsc(ProductionWorkCenterEntity::getWorkCenterCode);
        Page<ProductionWorkCenterEntity> result = workCenterMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional
    public ProductionWorkCenterResponse update(Long id, ProductionWorkCenterUpdateRequest request) {
        ProductionWorkCenterEntity entity = requireWorkCenter(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setWorkCenterName(requireText(request.workCenterName(), "工作中心名称不能为空"));
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(workCenterMapper.updateById(entity), "工作中心已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    @Transactional
    public ProductionWorkCenterResponse enable(Long id) {
        return updateStatus(id, STATUS_ACTIVE);
    }

    @Transactional
    public ProductionWorkCenterResponse disable(Long id) {
        return updateStatus(id, STATUS_DISABLED);
    }

    private ProductionWorkCenterResponse updateStatus(Long id, String status) {
        ProductionWorkCenterEntity entity = requireWorkCenter(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(workCenterMapper.updateById(entity), "工作中心已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private ProductionWorkCenterEntity requireWorkCenter(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = workCenterMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("工作中心不存在");
        }
        return entity;
    }

    private ProductionWorkCenterResponse toResponse(ProductionWorkCenterEntity entity) {
        return new ProductionWorkCenterResponse(
                entity.getId(),
                entity.getWorkCenterCode(),
                entity.getWorkCenterName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
```

Create `src/main/java/com/tuowei/erp/production/workcenter/controller/ProductionWorkCenterController.java`:

```java
package com.tuowei.erp.production.workcenter.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/work-centers")
public class ProductionWorkCenterController {

    private final ProductionWorkCenterService productionWorkCenterService;

    public ProductionWorkCenterController(ProductionWorkCenterService productionWorkCenterService) {
        this.productionWorkCenterService = productionWorkCenterService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_CREATE)
    @PostMapping
    public ApiResponse<ProductionWorkCenterResponse> create(@Valid @RequestBody ProductionWorkCenterCreateRequest request) {
        return ApiResponse.success(productionWorkCenterService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductionWorkCenterResponse>> list(ProductionWorkCenterPageQuery query) {
        return ApiResponse.success(productionWorkCenterService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductionWorkCenterResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productionWorkCenterService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<ProductionWorkCenterResponse> update(@PathVariable Long id, @Valid @RequestBody ProductionWorkCenterUpdateRequest request) {
        return ApiResponse.success(productionWorkCenterService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<ProductionWorkCenterResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(productionWorkCenterService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<ProductionWorkCenterResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(productionWorkCenterService.disable(id));
    }
}
```

- [ ] **Step 5: Run the controller test to verify it passes**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Add a failing duplicate-code service test**

Create `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTest.java`:

```java
package com.tuowei.erp.production.workcenter;

import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProductionWorkCenterServiceTest {

    @Autowired
    private ProductionWorkCenterService productionWorkCenterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from prd_routing_operation");
        jdbcTemplate.update("delete from prd_routing");
        jdbcTemplate.update("delete from prd_work_center");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_product where id between 895000 and 895999");
    }

    @Test
    @WithErpUser(authorities = {
            "production:work-center:create",
            "production:work-center:update",
            "production:work-center:view"
    })
    void rejectsDuplicateCodeAndOnlyAllowsNameRemarkUpdate() {
        var created = productionWorkCenterService.create(new ProductionWorkCenterCreateRequest("WC-2001", "切割", "first"));

        Assertions.assertThatThrownBy(() -> productionWorkCenterService.create(
                        new ProductionWorkCenterCreateRequest("WC-2001", "焊接", "dup")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心编码已存在");

        var updated = productionWorkCenterService.update(created.id(), new ProductionWorkCenterUpdateRequest("切割二线", "updated"));
        Assertions.assertThat(updated.workCenterCode()).isEqualTo("WC-2001");
        Assertions.assertThat(updated.workCenterName()).isEqualTo("切割二线");
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '工作中心测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '工作中心测试', 895001, 895001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo, long productId, long materialProductId) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1.0000, 'ACTIVE', 0, 'seed bom', 895001, 895001, 0)
                """, id, bomNo, productId);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate, remark, created_by, updated_by, version)
                values (895102, 1, 1, ?, 1, ?, 1.0000, 0.0000, 'seed line', 895001, 895001, 0)
                """, id, materialProductId);
    }
}
```

- [ ] **Step 7: Run the service test to verify it fails**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterServiceTest test
```

Expected: FAIL because duplicate-code handling still falls through to the database unique index instead of throwing the service-level `IllegalArgumentException("工作中心编码已存在")` expected by the test.

- [ ] **Step 8: Add duplicate-code validation and rerun focused tests**

Update `ProductionWorkCenterService` like this:

```java
        requireUniqueCode(request.workCenterCode(), audit, null);
```

```java
    private void requireUniqueCode(String code, AuditMetadata audit, Long excludedId) {
        LambdaQueryWrapper<ProductionWorkCenterEntity> wrapper = new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionWorkCenterEntity::getWorkCenterCode, requireText(code, "工作中心编码不能为空"))
                .eq(ProductionWorkCenterEntity::getDeletedFlag, 0);
        if (excludedId != null) {
            wrapper.ne(ProductionWorkCenterEntity::getId, excludedId);
        }
        if (workCenterMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("工作中心编码已存在");
        }
    }
```

The `ProductionWorkCenterUpdateRequest` from Step 4 stays unchanged, so the update path still cannot mutate `workCenterCode`.

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterControllerTest,ProductionWorkCenterServiceTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add src/main/resources/db/migration/V92__production_routing_work_center_schema.sql src/main/resources/db/migration/V93__production_routing_work_center_menu_seed.sql src/main/java/com/tuowei/erp/common/security/ProductionPermissionCodes.java src/main/java/com/tuowei/erp/production/workcenter src/test/java/com/tuowei/erp/production/workcenter
git commit -m "feat: add production work center backend"
```

## Task 2: Routing Schema Consumer And BOM-Bound CRUD

**Files:**
- Create: `src/main/java/com/tuowei/erp/production/routing/model/ProductionRoutingEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/model/ProductionRoutingOperationEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/mapper/ProductionRoutingMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/mapper/ProductionRoutingOperationMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingOperationRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingOperationResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/web/ProductionRoutingPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/service/ProductionRoutingService.java`
- Create: `src/main/java/com/tuowei/erp/production/routing/controller/ProductionRoutingController.java`
- Create: `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingServiceTest.java`

- [ ] **Step 1: Write the failing routing controller test**

Create `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingControllerTest.java`:

```java
package com.tuowei.erp.production.routing;

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

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionRoutingControllerTest {

    private static final long FINISHED_PRODUCT_ID = 894001L;
    private static final long MATERIAL_PRODUCT_ID = 894002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(FINISHED_PRODUCT_ID, "PRD-FG-894001", "工艺成品");
        seedProduct(MATERIAL_PRODUCT_ID, "PRD-MAT-894002", "工艺材料");
        seedBom(894101L, "BOM-894101");
        seedWorkCenter(894201L, "WC-894201", "装配一线");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from prd_routing_operation");
        jdbcTemplate.update("delete from prd_routing");
        jdbcTemplate.update("delete from prd_work_center");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_product where id between 894000 and 894999");
    }

    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:routing:view",
            "production:routing:update",
            "production:routing:enable",
            "production:routing:disable"
    })
    void createsReadsListsAndDisablesRoutingThroughHttpApi() throws Exception {
        long id = idOf(mockMvc.perform(post("/api/production/routings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routingCode": "RT-894001",
                                  "routingName": "装配路线",
                                  "bomId": 894101,
                                  "remark": "controller routing",
                                  "operations": [
                                    {
                                      "operationCode": "OP-10",
                                      "operationName": "切割",
                                      "workCenterId": 894201,
                                      "standardMinutes": 12.50,
                                      "remark": "first"
                                    },
                                    {
                                      "operationCode": "OP-20",
                                      "operationName": "装配",
                                      "workCenterId": 894201,
                                      "standardMinutes": 18.00,
                                      "remark": "second"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.operations[0].lineNo").value(1))
                .andExpect(jsonPath("$.data.operations[1].lineNo").value(2))
                .andReturn());

        mockMvc.perform(get("/api/production/routings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bomId").value(894101));

        mockMvc.perform(get("/api/production/routings").param("bomId", "894101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].routingCode").value("RT-894001"));

        mockMvc.perform(post("/api/production/routings/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("id").asLong();
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '工艺测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '工艺测试', 894001, 894001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1.0000, 'ACTIVE', 0, 'seed bom', 894001, 894001, 0)
                """, id, bomNo, FINISHED_PRODUCT_ID);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate, remark, created_by, updated_by, version)
                values (894102, 1, 1, ?, 1, ?, 1.0000, 0.0000, 'seed line', 894001, 894001, 0)
                """, id, MATERIAL_PRODUCT_ID);
    }

    private void seedWorkCenter(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into prd_work_center
                (id, company_id, account_book_id, work_center_code, work_center_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'ACTIVE', 0, 'seed wc', 894001, 894001, 0)
                """, id, code, name);
    }
}
```

- [ ] **Step 2: Run the routing controller test to verify it fails**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionRoutingControllerTest test
```

Expected: FAIL with `404` because `/api/production/routings` is not mapped yet.

- [ ] **Step 3: Add the routing entities, DTOs, service, and controller**

Create the entities:

```java
package com.tuowei.erp.production.routing.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("prd_routing")
public class ProductionRoutingEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String routingCode;
    private String routingName;
    private Long bomId;
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
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public String getRoutingName() { return routingName; }
    public void setRoutingName(String routingName) { this.routingName = routingName; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
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

```java
package com.tuowei.erp.production.routing.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("prd_routing_operation")
public class ProductionRoutingOperationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long routingId;
    private Integer lineNo;
    private String operationCode;
    private String operationName;
    private Long workCenterId;
    private BigDecimal standardMinutes;
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
    public Long getRoutingId() { return routingId; }
    public void setRoutingId(Long routingId) { this.routingId = routingId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public Long getWorkCenterId() { return workCenterId; }
    public void setWorkCenterId(Long workCenterId) { this.workCenterId = workCenterId; }
    public BigDecimal getStandardMinutes() { return standardMinutes; }
    public void setStandardMinutes(BigDecimal standardMinutes) { this.standardMinutes = standardMinutes; }
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

Create the mappers:

```java
package com.tuowei.erp.production.routing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionRoutingMapper extends BaseMapper<ProductionRoutingEntity> {
}
```

```java
package com.tuowei.erp.production.routing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionRoutingOperationMapper extends BaseMapper<ProductionRoutingOperationEntity> {
}
```

Create the request/response DTOs:

```java
package com.tuowei.erp.production.routing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProductionRoutingCreateRequest(
        @NotBlank(message = "工艺路线编码不能为空") String routingCode,
        @NotBlank(message = "工艺路线名称不能为空") String routingName,
        @NotNull(message = "BOM不能为空") Long bomId,
        String remark,
        @NotEmpty(message = "工艺路线至少需要一道工序") List<@Valid ProductionRoutingOperationRequest> operations
) {
}
```

```java
package com.tuowei.erp.production.routing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductionRoutingUpdateRequest(
        @NotBlank(message = "工艺路线名称不能为空") String routingName,
        String remark,
        @NotEmpty(message = "工艺路线至少需要一道工序") List<@Valid ProductionRoutingOperationRequest> operations
) {
}
```

```java
package com.tuowei.erp.production.routing.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductionRoutingOperationRequest(
        @NotBlank(message = "工序编码不能为空") String operationCode,
        @NotBlank(message = "工序名称不能为空") String operationName,
        @NotNull(message = "工作中心不能为空") Long workCenterId,
        @NotNull(message = "标准工时不能为空")
        @DecimalMin(value = "0.01", message = "标准工时必须大于0") BigDecimal standardMinutes,
        String remark
) {
}
```

```java
package com.tuowei.erp.production.routing.web;

import java.math.BigDecimal;

public record ProductionRoutingOperationResponse(
        Long id,
        Integer lineNo,
        String operationCode,
        String operationName,
        Long workCenterId,
        String workCenterCode,
        String workCenterName,
        BigDecimal standardMinutes,
        String remark
) {
}
```

```java
package com.tuowei.erp.production.routing.web;

import java.util.List;

public record ProductionRoutingResponse(
        Long id,
        String routingCode,
        String routingName,
        Long bomId,
        String bomNo,
        Long productId,
        String status,
        String remark,
        List<ProductionRoutingOperationResponse> operations
) {
}
```

```java
package com.tuowei.erp.production.routing.web;

public class ProductionRoutingPageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;
    private Long bomId;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
}
```

Create `src/main/java/com/tuowei/erp/production/routing/service/ProductionRoutingService.java` with these key methods:

```java
package com.tuowei.erp.production.routing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductionRoutingService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final ProductionBomMapper bomMapper;
    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionRoutingService(
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            ProductionBomMapper bomMapper,
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.bomMapper = bomMapper;
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ProductionRoutingResponse create(ProductionRoutingCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionBomEntity bom = requireActiveBom(request.bomId(), audit);
        requireUniqueRoutingCode(request.routingCode(), audit, null);
        List<ProductionRoutingOperationRequest> operations = validateOperations(request.operations(), audit);
        LocalDateTime now = audit.now();

        ProductionRoutingEntity entity = new ProductionRoutingEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setRoutingCode(requireText(request.routingCode(), "工艺路线编码不能为空"));
        entity.setRoutingName(requireText(request.routingName(), "工艺路线名称不能为空"));
        entity.setBomId(bom.getId());
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0);
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        routingMapper.insert(entity);

        replaceOperations(entity, operations, audit, now);
        return toResponse(entity, bom, audit);
    }

    @Transactional(readOnly = true)
    public ProductionRoutingResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionRoutingEntity entity = requireRouting(id, audit);
        return toResponse(entity, requireActiveBom(entity.getBomId(), audit), audit);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionRoutingResponse> list(ProductionRoutingPageQuery query) {
        ProductionRoutingPageQuery safeQuery = query == null ? new ProductionRoutingPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionRoutingEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        String keyword = normalizeNullable(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(ProductionRoutingEntity::getRoutingCode, keyword)
                    .or()
                    .like(ProductionRoutingEntity::getRoutingName, keyword));
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (status != null) {
            wrapper.eq(ProductionRoutingEntity::getStatus, status);
        }
        if (safeQuery.getBomId() != null) {
            wrapper.eq(ProductionRoutingEntity::getBomId, safeQuery.getBomId());
        }
        wrapper.orderByAsc(ProductionRoutingEntity::getRoutingCode);
        Page<ProductionRoutingEntity> result = routingMapper.selectPage(page, wrapper);
        List<ProductionRoutingResponse> records = result.getRecords().stream()
                .map(entity -> toResponse(entity, requireActiveBom(entity.getBomId(), audit), audit))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional
    public ProductionRoutingResponse update(Long id, ProductionRoutingUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionRoutingEntity entity = requireRouting(id, audit);
        ProductionBomEntity bom = requireActiveBom(entity.getBomId(), audit);
        List<ProductionRoutingOperationRequest> operations = validateOperations(request.operations(), audit);
        LocalDateTime now = audit.now();
        entity.setRoutingName(requireText(request.routingName(), "工艺路线名称不能为空"));
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(routingMapper.updateById(entity), "工艺路线已被其他操作修改，请刷新后重试");
        replaceOperations(entity, operations, audit, now);
        return toResponse(entity, bom, audit);
    }

    @Transactional
    public ProductionRoutingResponse enable(Long id) { return updateStatus(id, STATUS_ACTIVE); }

    @Transactional
    public ProductionRoutingResponse disable(Long id) { return updateStatus(id, STATUS_DISABLED); }

    private ProductionRoutingResponse updateStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionRoutingEntity entity = requireRouting(id, audit);
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(routingMapper.updateById(entity), "工艺路线已被其他操作修改，请刷新后重试");
        return toResponse(entity, requireActiveBom(entity.getBomId(), audit), audit);
    }

    private List<ProductionRoutingOperationRequest> validateOperations(List<ProductionRoutingOperationRequest> operations, AuditMetadata audit) {
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("工艺路线至少需要一道工序");
        }
        Set<String> operationCodes = new HashSet<>();
        for (ProductionRoutingOperationRequest operation : operations) {
            String code = requireText(operation.operationCode(), "工序编码不能为空");
            if (!operationCodes.add(code)) {
                throw new IllegalArgumentException("工序编码不能重复");
            }
            requireText(operation.operationName(), "工序名称不能为空");
            BigDecimal minutes = ScalePrecision.amount(operation.standardMinutes());
            if (minutes.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("标准工时必须大于0");
            }
            requireActiveWorkCenter(operation.workCenterId(), audit);
        }
        return operations;
    }

    private void replaceOperations(ProductionRoutingEntity routing, List<ProductionRoutingOperationRequest> operations, AuditMetadata audit, LocalDateTime now) {
        int lineNo = 1;
        for (ProductionRoutingOperationRequest request : operations) {
            ProductionRoutingOperationEntity line = new ProductionRoutingOperationEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setRoutingId(routing.getId());
            line.setLineNo(lineNo++);
            line.setOperationCode(requireText(request.operationCode(), "工序编码不能为空"));
            line.setOperationName(requireText(request.operationName(), "工序名称不能为空"));
            line.setWorkCenterId(request.workCenterId());
            line.setStandardMinutes(ScalePrecision.amount(request.standardMinutes()));
            line.setRemark(normalizeNullable(request.remark()));
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            routingOperationMapper.insert(line);
        }
    }

    private ProductionRoutingResponse toResponse(ProductionRoutingEntity entity, ProductionBomEntity bom, AuditMetadata audit) {
        List<ProductionRoutingOperationEntity> operations = routingOperationMapper.selectList(new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                .eq(ProductionRoutingOperationEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingOperationEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingOperationEntity::getRoutingId, entity.getId())
                .orderByAsc(ProductionRoutingOperationEntity::getLineNo));
        Map<Long, ProductionWorkCenterEntity> workCenterMap = loadWorkCenters(operations, audit);
        return new ProductionRoutingResponse(
                entity.getId(),
                entity.getRoutingCode(),
                entity.getRoutingName(),
                entity.getBomId(),
                bom.getBomNo(),
                bom.getProductId(),
                entity.getStatus(),
                entity.getRemark(),
                operations.stream().map(line -> {
                    ProductionWorkCenterEntity workCenter = workCenterMap.get(line.getWorkCenterId());
                    return new ProductionRoutingOperationResponse(
                            line.getId(),
                            line.getLineNo(),
                            line.getOperationCode(),
                            line.getOperationName(),
                            line.getWorkCenterId(),
                            workCenter == null ? null : workCenter.getWorkCenterCode(),
                            workCenter == null ? null : workCenter.getWorkCenterName(),
                            line.getStandardMinutes(),
                            line.getRemark()
                    );
                }).toList()
        );
    }

    private Map<Long, ProductionWorkCenterEntity> loadWorkCenters(List<ProductionRoutingOperationEntity> operations, AuditMetadata audit) {
        Map<Long, ProductionWorkCenterEntity> result = new HashMap<>();
        if (operations.isEmpty()) {
            return result;
        }
        List<Long> ids = operations.stream().map(ProductionRoutingOperationEntity::getWorkCenterId).distinct().toList();
        workCenterMapper.selectList(new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                        .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                        .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                        .eq(ProductionWorkCenterEntity::getDeletedFlag, 0)
                        .in(ProductionWorkCenterEntity::getId, ids))
                .forEach(entity -> result.put(entity.getId(), entity));
        return result;
    }

    private void requireUniqueRoutingCode(String code, AuditMetadata audit, Long excludedId) {
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingEntity::getRoutingCode, requireText(code, "工艺路线编码不能为空"))
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        if (excludedId != null) {
            wrapper.ne(ProductionRoutingEntity::getId, excludedId);
        }
        if (routingMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("工艺路线编码已存在");
        }
    }

    private void requireNoRoutingForBom(Long bomId, AuditMetadata audit, Long excludedId) {
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingEntity::getBomId, bomId)
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        if (excludedId != null) {
            wrapper.ne(ProductionRoutingEntity::getId, excludedId);
        }
        if (routingMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("当前BOM已存在工艺路线");
        }
    }

    private ProductionRoutingEntity requireRouting(Long id, AuditMetadata audit) {
        ProductionRoutingEntity entity = routingMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("工艺路线不存在");
        }
        return entity;
    }

    private ProductionBomEntity requireActiveBom(Long bomId, AuditMetadata audit) {
        ProductionBomEntity bom = bomMapper.selectById(bomId);
        if (bom == null || !Objects.equals(bom.getCompanyId(), audit.companyId())
                || !Objects.equals(bom.getAccountBookId(), audit.accountBookId())
                || bom.getDeletedFlag() == null || bom.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(bom.getStatus())) {
            throw new IllegalArgumentException("BOM不存在或已停用");
        }
        return bom;
    }

    private ProductionWorkCenterEntity requireActiveWorkCenter(Long workCenterId, AuditMetadata audit) {
        ProductionWorkCenterEntity workCenter = workCenterMapper.selectById(workCenterId);
        if (workCenter == null || !Objects.equals(workCenter.getCompanyId(), audit.companyId())
                || !Objects.equals(workCenter.getAccountBookId(), audit.accountBookId())
                || workCenter.getDeletedFlag() == null || workCenter.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(workCenter.getStatus())) {
            throw new IllegalArgumentException("工作中心不存在或已停用");
        }
        return workCenter;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
```

Create `src/main/java/com/tuowei/erp/production/routing/controller/ProductionRoutingController.java`:

```java
package com.tuowei.erp.production.routing.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/routings")
public class ProductionRoutingController {

    private final ProductionRoutingService productionRoutingService;

    public ProductionRoutingController(ProductionRoutingService productionRoutingService) {
        this.productionRoutingService = productionRoutingService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_CREATE)
    @PostMapping
    public ApiResponse<ProductionRoutingResponse> create(@Valid @RequestBody ProductionRoutingCreateRequest request) {
        return ApiResponse.success(productionRoutingService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductionRoutingResponse>> list(ProductionRoutingPageQuery query) {
        return ApiResponse.success(productionRoutingService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductionRoutingResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productionRoutingService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<ProductionRoutingResponse> update(@PathVariable Long id, @Valid @RequestBody ProductionRoutingUpdateRequest request) {
        return ApiResponse.success(productionRoutingService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<ProductionRoutingResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(productionRoutingService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<ProductionRoutingResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(productionRoutingService.disable(id));
    }
}
```

- [ ] **Step 4: Run the routing controller test to verify it passes**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionRoutingControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Add a failing routing service test for validation and replacement**

Create `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingServiceTest.java`:

```java
package com.tuowei.erp.production.routing;

import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
class ProductionRoutingServiceTest {

    @Autowired
    private ProductionRoutingService productionRoutingService;

    @Autowired
    private ProductionWorkCenterService productionWorkCenterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(895001L, "PRD-FG-895001", "路线成品");
        seedProduct(895002L, "PRD-MAT-895002", "路线材料");
        seedBom(895101L, "BOM-895101");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from prd_routing_operation");
        jdbcTemplate.update("delete from prd_routing");
        jdbcTemplate.update("delete from prd_work_center");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_product where id between 895000 and 895999");
    }

    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:routing:update",
            "production:routing:view",
            "production:work-center:create"
    })
    void rejectsDuplicateBomAndReplacesOperationsOnUpdate() {
        var wc = productionWorkCenterService.create(new ProductionWorkCenterCreateRequest("WC-895001", "焊接一线", "wc"));
        var created = productionRoutingService.create(new ProductionRoutingCreateRequest(
                "RT-895001",
                "标准路线",
                895101L,
                "first",
                List.of(
                        new ProductionRoutingOperationRequest("OP-10", "切割", wc.id(), new BigDecimal("12.50"), "first"),
                        new ProductionRoutingOperationRequest("OP-20", "装配", wc.id(), new BigDecimal("18.00"), "second")
                )
        ));

        Assertions.assertThat(created.operations()).hasSize(2);
        Assertions.assertThat(created.operations().get(0).lineNo()).isEqualTo(1);
        Assertions.assertThat(created.operations().get(1).lineNo()).isEqualTo(2);

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895002",
                        "重复BOM路线",
                        895101L,
                        "dup",
                        List.of(new ProductionRoutingOperationRequest("OP-30", "检验", wc.id(), new BigDecimal("6.00"), "dup"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前BOM已存在工艺路线");

        var updated = productionRoutingService.update(created.id(), new ProductionRoutingUpdateRequest(
                "标准路线-更新",
                "updated",
                List.of(new ProductionRoutingOperationRequest("OP-99", "总装", wc.id(), new BigDecimal("22.00"), "replace"))
        ));

        Assertions.assertThat(updated.operations()).hasSize(1);
        Assertions.assertThat(updated.operations().get(0).lineNo()).isEqualTo(1);
        Assertions.assertThat(updated.operations().get(0).operationCode()).isEqualTo("OP-99");
    }

    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:work-center:create",
            "production:work-center:disable"
    })
    void rejectsEmptyOperationsDuplicateOperationCodesAndDisabledWorkCenter() {
        var active = productionWorkCenterService.create(new ProductionWorkCenterCreateRequest("WC-895011", "装配一线", "active"));
        var disabled = productionWorkCenterService.create(new ProductionWorkCenterCreateRequest("WC-895012", "装配二线", "disabled"));
        productionWorkCenterService.disable(disabled.id());

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895011",
                        "空工序",
                        895101L,
                        "empty",
                        List.of()
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线至少需要一道工序");

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895012",
                        "重复工序编码",
                        895101L,
                        "dup op",
                        List.of(
                                new ProductionRoutingOperationRequest("OP-10", "切割", active.id(), new BigDecimal("10.00"), "first"),
                                new ProductionRoutingOperationRequest("OP-10", "装配", active.id(), new BigDecimal("12.00"), "second")
                        )
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工序编码不能重复");

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895013",
                        "停用工作中心",
                        895101L,
                        "disabled wc",
                        List.of(new ProductionRoutingOperationRequest("OP-20", "检验", disabled.id(), new BigDecimal("6.00"), "line"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在或已停用");
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '路线测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '路线测试', 895001, 895001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 895001, 1.0000, 'ACTIVE', 0, 'seed bom', 895001, 895001, 0)
                """, id, bomNo);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate, remark, created_by, updated_by, version)
                values (895102, 1, 1, ?, 1, 895002, 1.0000, 0.0000, 'seed line', 895001, 895001, 0)
                """, id);
    }
}
```

- [ ] **Step 6: Run the routing service test to verify it fails**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionRoutingServiceTest test
```

Expected: FAIL because duplicate-`bomId` handling still falls through to the database unique index instead of the expected `IllegalArgumentException("当前BOM已存在工艺路线")`, and `update(...)` does not yet clear old operation rows before reinserting.

- [ ] **Step 7: Add BOM uniqueness and full-replace update behavior**

Update `ProductionRoutingService.create(...)` like this:

```java
        requireNoRoutingForBom(bom.getId(), audit, null);
```

Update `ProductionRoutingService.update(...)` like this:

```java
        routingOperationMapper.delete(new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                .eq(ProductionRoutingOperationEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingOperationEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingOperationEntity::getRoutingId, id));
```

- [ ] **Step 8: Run focused routing tests to verify they pass**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionRoutingControllerTest,ProductionRoutingServiceTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/tuowei/erp/production/routing src/test/java/com/tuowei/erp/production/routing
git commit -m "feat: add production routing backend"
```

## Task 3: Tenant Boundaries, Disable Conflict Guard, And Regression

**Files:**
- Modify: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterControllerTest.java`
- Modify: `src/main/java/com/tuowei/erp/production/workcenter/service/ProductionWorkCenterService.java`
- Modify: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTest.java`
- Create: `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTenantBoundaryTest.java`
- Create: `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingServiceTenantBoundaryTest.java`

- [ ] **Step 1: Add the failing disable-conflict and tenant-boundary tests**

Extend `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterControllerTest.java` with:

```java
    @Test
    @WithErpUser(authorities = {
            "production:work-center:create",
            "production:work-center:disable"
    })
    void returnsClientErrorWhenDisablingReferencedWorkCenter() throws Exception {
        long id = idOf(mockMvc.perform(post("/api/production/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workCenterCode": "WC-894501",
                                  "workCenterName": "冲突工位",
                                  "remark": "controller conflict"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());

        seedProduct(894501L, "PRD-FG-894501", "冲突成品");
        seedProduct(894502L, "PRD-MAT-894502", "冲突材料");
        seedBom(894601L, "BOM-894601", 894501L, 894502L);
        jdbcTemplate.update("""
                insert into prd_routing
                (id, company_id, account_book_id, routing_code, routing_name, bom_id, status, deleted_flag, remark, created_by, updated_by, version)
                values (894701, 1, 1, 'RT-894701', '冲突路线', 894601, 'ACTIVE', 0, 'routing', 1, 1, 0)
                """);
        jdbcTemplate.update("""
                insert into prd_routing_operation
                (id, company_id, account_book_id, routing_id, line_no, operation_code, operation_name, work_center_id, standard_minutes, remark, created_by, updated_by, version)
                values (894702, 1, 1, 894701, 1, 'OP-10', '工序', ?, 10.00, 'line', 1, 1, 0)
                """, id);

        mockMvc.perform(post("/api/production/work-centers/{id}/disable", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工作中心已被启用工艺路线引用，不能停用"));
    }
```

Extend `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTest.java` with:

```java
    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:work-center:create",
            "production:work-center:disable"
    })
    void rejectsDisableWhenReferencedByActiveRouting() {
        seedProduct(895011L, "PRD-FG-895011", "冲突成品");
        seedProduct(895012L, "PRD-MAT-895012", "冲突材料");
        seedBom(895111L, "BOM-895111", 895011L, 895012L);

        var wc = productionWorkCenterService.create(new ProductionWorkCenterCreateRequest("WC-895011", "冲突工位", "wc"));
        jdbcTemplate.update("""
                insert into prd_routing
                (id, company_id, account_book_id, routing_code, routing_name, bom_id, status, deleted_flag, remark, created_by, updated_by, version)
                values (895211, 1, 1, 'RT-895211', '冲突路线', 895111, 'ACTIVE', 0, 'routing', 1, 1, 0)
                """);
        jdbcTemplate.update("""
                insert into prd_routing_operation
                (id, company_id, account_book_id, routing_id, line_no, operation_code, operation_name, work_center_id, standard_minutes, remark, created_by, updated_by, version)
                values (895212, 1, 1, 895211, 1, 'OP-1', '工序', ?, 10.00, 'line', 1, 1, 0)
                """, wc.id());

        Assertions.assertThatThrownBy(() -> productionWorkCenterService.disable(wc.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心已被启用工艺路线引用，不能停用");
    }
```

Create `src/test/java/com/tuowei/erp/production/workcenter/ProductionWorkCenterServiceTenantBoundaryTest.java`:

```java
package com.tuowei.erp.production.workcenter;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionWorkCenterServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(9912L, 101L, 202L, LocalDateTime.of(2026, 7, 8, 11, 0));

    private final ProductionWorkCenterMapper workCenterMapper = mock(ProductionWorkCenterMapper.class);
    private final ProductionRoutingMapper routingMapper = mock(ProductionRoutingMapper.class);
    private final ProductionRoutingOperationMapper routingOperationMapper = mock(ProductionRoutingOperationMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionWorkCenterEntity.class);
    }

    @Test
    void listScopesQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workCenterMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionWorkCenterEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ProductionWorkCenterPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionWorkCenterEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(workCenterMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void getByIdRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workCenterMapper.selectById(7001L)).thenReturn(activeWorkCenter(7001L, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().getById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在");
    }

    @Test
    void disableRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workCenterMapper.selectById(7002L)).thenReturn(activeWorkCenter(7002L, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().disable(7002L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在");
    }

    private ProductionWorkCenterService service() {
        return new ProductionWorkCenterService(workCenterMapper, routingMapper, routingOperationMapper, auditMetadataFactory);
    }

    private ProductionWorkCenterEntity activeWorkCenter(Long id, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWorkCenterCode("WC-" + id);
        entity.setWorkCenterName("WC");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
```

Create `src/test/java/com/tuowei/erp/production/routing/ProductionRoutingServiceTenantBoundaryTest.java`:

```java
package com.tuowei.erp.production.routing;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionRoutingServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(9913L, 101L, 202L, LocalDateTime.of(2026, 7, 8, 11, 30));

    private final ProductionRoutingMapper routingMapper = mock(ProductionRoutingMapper.class);
    private final ProductionRoutingOperationMapper routingOperationMapper = mock(ProductionRoutingOperationMapper.class);
    private final ProductionBomMapper bomMapper = mock(ProductionBomMapper.class);
    private final ProductionWorkCenterMapper workCenterMapper = mock(ProductionWorkCenterMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionRoutingEntity.class);
    }

    @Test
    void listScopesRoutingQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionRoutingEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ProductionRoutingPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionRoutingEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(routingMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void createRejectsBomFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(bomMapper.selectById(8101L)).thenReturn(activeBom(8101L, AUDIT.companyId(), 999L));
        when(workCenterMapper.selectById(8201L)).thenReturn(activeWorkCenter(8201L, AUDIT.companyId(), AUDIT.accountBookId()));

        assertThatThrownBy(() -> service().create(new ProductionRoutingCreateRequest(
                        "RT-8101",
                        "tenant",
                        8101L,
                        "routing",
                        List.of(new ProductionRoutingOperationRequest("OP-10", "工序", 8201L, new BigDecimal("10.00"), "line"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOM不存在或已停用");
    }

    @Test
    void createRejectsWorkCenterFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(bomMapper.selectById(8102L)).thenReturn(activeBom(8102L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(workCenterMapper.selectById(8202L)).thenReturn(activeWorkCenter(8202L, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().create(new ProductionRoutingCreateRequest(
                        "RT-8102",
                        "tenant wc",
                        8102L,
                        "routing",
                        List.of(new ProductionRoutingOperationRequest("OP-10", "工序", 8202L, new BigDecimal("10.00"), "line"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在或已停用");
    }

    @Test
    void disableRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectById(8301L)).thenReturn(activeRouting(8301L, AUDIT.companyId(), 999L, 8102L));

        assertThatThrownBy(() -> service().disable(8301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线不存在");
    }

    private ProductionRoutingService service() {
        return new ProductionRoutingService(routingMapper, routingOperationMapper, bomMapper, workCenterMapper, auditMetadataFactory);
    }

    private ProductionBomEntity activeBom(Long id, Long companyId, Long accountBookId) {
        ProductionBomEntity entity = new ProductionBomEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setBomNo("BOM-" + id);
        entity.setProductId(1L);
        entity.setBaseQty(BigDecimal.ONE);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionWorkCenterEntity activeWorkCenter(Long id, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWorkCenterCode("WC-" + id);
        entity.setWorkCenterName("WC");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionRoutingEntity activeRouting(Long id, Long companyId, Long accountBookId, Long bomId) {
        ProductionRoutingEntity entity = new ProductionRoutingEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setRoutingCode("RT-" + id);
        entity.setRoutingName("RT");
        entity.setBomId(bomId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterControllerTest,ProductionWorkCenterServiceTest,ProductionWorkCenterServiceTenantBoundaryTest,ProductionRoutingServiceTenantBoundaryTest test
```

Expected: FAIL because `ProductionWorkCenterService.disable(...)` does not yet block active-routing references, so both the HTTP conflict test and the service conflict test still observe the wrong behavior.

- [ ] **Step 3: Implement the conflict guard and constructor wiring**

Update `src/main/java/com/tuowei/erp/production/workcenter/service/ProductionWorkCenterService.java` constructor and disable flow:

```java
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
```

```java
    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;

    public ProductionWorkCenterService(
            ProductionWorkCenterMapper workCenterMapper,
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.workCenterMapper = workCenterMapper;
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }
```

```java
    @Transactional
    public ProductionWorkCenterResponse disable(Long id) {
        ProductionWorkCenterEntity entity = requireWorkCenter(id);
        AuditMetadata audit = auditMetadataFactory.current();
        List<ProductionRoutingOperationEntity> operations = routingOperationMapper.selectList(new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                .eq(ProductionRoutingOperationEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingOperationEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingOperationEntity::getWorkCenterId, entity.getId()));
        for (ProductionRoutingOperationEntity operation : operations) {
            ProductionRoutingEntity routing = routingMapper.selectById(operation.getRoutingId());
            if (routing != null
                    && Objects.equals(routing.getCompanyId(), audit.companyId())
                    && Objects.equals(routing.getAccountBookId(), audit.accountBookId())
                    && routing.getDeletedFlag() != null
                    && routing.getDeletedFlag() == 0
                    && "ACTIVE".equalsIgnoreCase(routing.getStatus())) {
                throw new IllegalArgumentException("工作中心已被启用工艺路线引用，不能停用");
            }
        }
        return updateStatus(id, STATUS_DISABLED);
    }
```

Adjust any direct service construction in tests to use the new constructor signature.

- [ ] **Step 4: Run focused guards and tenant-boundary tests to verify they pass**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterControllerTest,ProductionWorkCenterServiceTest,ProductionWorkCenterServiceTenantBoundaryTest,ProductionRoutingServiceTenantBoundaryTest test
```

Expected: PASS.

- [ ] **Step 5: Run the full feature regression**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ProductionWorkCenterControllerTest,ProductionWorkCenterServiceTest,ProductionWorkCenterServiceTenantBoundaryTest,ProductionRoutingControllerTest,ProductionRoutingServiceTest,ProductionRoutingServiceTenantBoundaryTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/production/workcenter/service/ProductionWorkCenterService.java src/test/java/com/tuowei/erp/production/workcenter src/test/java/com/tuowei/erp/production/routing
git commit -m "test: harden production routing work center boundaries"
```

## Task 4: Full Backend Verification

**Files:**
- No code changes expected unless regressions require targeted fixes in previously added files.

- [ ] **Step 1: Run the backend test suite**

Run:

```powershell
.\mvnw.cmd -B test
```

Expected: PASS.

- [ ] **Step 2: Verify the worktree only contains intended files**

Run:

```powershell
git status --short
```

Expected: only the new migration, production work-center/routing source files, and their tests are present from this feature; unrelated pre-existing changes remain untouched.

- [ ] **Step 3: Confirm verification did not create extra feature changes**

```powershell
git status --short
```

Expected: the verification task does not introduce a new ad-hoc commit; feature code changes should already be covered by the incremental commits from Tasks 1-3, while unrelated pre-existing changes remain untouched.
