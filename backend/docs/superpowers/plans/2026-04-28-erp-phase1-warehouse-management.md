# ERP Warehouse Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐主数据模块的仓库档案能力，支持仓库新增、分页查询、详情、更新、启停以及数据库初始化脚本同步。

**Architecture:** 仓库模块沿用现有 `controller + service + mapper + model + web` 结构，落在 `masterdata/warehouse` 包下。仓库作为主数据根对象，持久化到 `md_warehouse`，通过 `dept_id` 和 `manager_user_id` 关联现有组织与用户体系，继续使用 `H2 + Flyway + MockMvc` 做集成测试回归。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for Warehouse

**Files:**
- Create: `src/main/resources/db/migration/V9__masterdata_warehouse_schema.sql`
- Create: `src/test/java/com/tuowei/erp/masterdata/warehouse/WarehouseSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=WarehouseSchemaMigrationTest test` and verify it fails because `md_warehouse` does not exist**
- [x] **Step 3: Add `md_warehouse` table and unique/index definitions for warehouse code, department and manager**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仓库编码唯一，唯一索引名固定为 `uk_md_warehouse_warehouse_code`
- 仓库默认 `status = "ACTIVE"`、`deleted_flag = 0`
- 建立 `idx_md_warehouse_dept_id`、`idx_md_warehouse_manager_user_id`

### Task 2: Implement Warehouse Create and Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/model/WarehouseEntity.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/mapper/WarehouseMapper.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/web/WarehouseCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/web/WarehouseResponse.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/service/WarehouseService.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/controller/WarehouseController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/warehouse/WarehouseControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for warehouse creation and detail query**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=WarehouseControllerCreateDetailTest test` and verify it fails because `/api/masterdata/warehouses` does not exist**
- [x] **Step 3: Implement the minimal create/detail flow with department and manager validation**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- `deptId` 必须对应未删除部门，否则抛 `IllegalArgumentException("部门不存在")`
- `managerUserId` 必须对应未删除用户，否则抛 `IllegalArgumentException("负责人不存在")`
- 返回字段包含 `id`、`warehouseCode`、`warehouseName`、`deptId`、`managerUserId`、`address`、`status`、`remark`

### Task 3: Implement Warehouse Update, Enable and Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/web/WarehouseUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/warehouse/service/WarehouseService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/warehouse/controller/WarehouseController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/warehouse/WarehouseControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=WarehouseControllerLifecycleTest test` and verify it fails because update/status APIs do not exist**
- [x] **Step 3: Implement minimal `PUT /api/masterdata/warehouses/{id}`、`POST /api/masterdata/warehouses/{id}/enable`、`POST /api/masterdata/warehouses/{id}/disable`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仓库不存在或已逻辑删除时抛 `IllegalArgumentException("仓库不存在")`
- 更新仅允许修改 `warehouseName`、`deptId`、`managerUserId`、`address`、`remark`
- 启停只改 `status`

### Task 4: Implement Warehouse Page Query API

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/warehouse/web/WarehousePageQuery.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/warehouse/service/WarehouseService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/warehouse/controller/WarehouseController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/warehouse/WarehouseControllerPageQueryTest.java`

- [x] **Step 1: Write the failing page query test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=WarehouseControllerPageQueryTest test` and verify it fails because list API does not exist or cannot filter**
- [x] **Step 3: Implement minimal `GET /api/masterdata/warehouses` pagination and filter logic**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仅返回未删除仓库
- 支持按 `keyword` 模糊匹配 `warehouse_code` / `warehouse_name`
- 支持按 `status`、`deptId`、`managerUserId` 过滤
- 默认 `pageNo = 1`、`pageSize = 20`，`pageSize` 最大 `200`
- 按 `warehouse_code ASC` 排序

### Task 5: Align Initialization Scripts, Duplicate Mapping and Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/04_init_dict_and_config.sql`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` (if new mapper breaks `@WebMvcTest`)
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java` (if new mapper breaks `@WebMvcTest`)

- [x] **Step 1: Add failing script assertions for `md_warehouse`, unique index and default warehouse seed**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails before script alignment**
- [x] **Step 3: Update initialization scripts and duplicate-key error mapping**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and fix any new `WebMvcTest` mapper mock issues**

Rules:
- 初始化脚本保持可重复执行
- 默认种子至少包含一个可用仓库，如 `MAIN_WH`
- 重复仓库编码统一返回 `仓库编码已存在`
- 全量回归必须覆盖新增仓库模块测试

