# ERP Supplier Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐主数据模块的供应商档案能力，支持供应商新增、详情、更新、启停、分页查询以及数据库初始化脚本同步。

**Architecture:** 供应商模块沿用现有 `controller + service + mapper + model + web` 结构，落在 `masterdata/supplier` 包下。当前阶段先落 `md_supplier` 主档表，把联系人、电话、结算方式、地址作为供应商主表字段直接持久化，先满足采购、应付、报表后续引用需求，后续再细化供应商分类、开户信息、税号等扩展字段。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for Supplier

**Files:**
- Create: `src/main/resources/db/migration/V12__masterdata_supplier_schema.sql`
- Create: `src/test/java/com/tuowei/erp/masterdata/supplier/SupplierSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=SupplierSchemaMigrationTest test` and verify it fails because `md_supplier` does not exist**
- [x] **Step 3: Add `md_supplier` table plus unique/index definitions**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 供应商编码唯一，唯一索引名固定为 `uk_md_supplier_supplier_code`
- 默认 `status = "ACTIVE"`、`deleted_flag = 0`
- 建立 `idx_md_supplier_supplier_name`
- 建立 `idx_md_supplier_contact_phone`

### Task 2: Implement Supplier Create and Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/model/SupplierEntity.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/mapper/SupplierMapper.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/web/SupplierCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/web/SupplierResponse.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/service/SupplierService.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/controller/SupplierController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/supplier/SupplierControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for supplier creation, detail query and duplicate code rejection**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=SupplierControllerCreateDetailTest test` and verify it fails because `/api/masterdata/suppliers` does not exist**
- [x] **Step 3: Implement the minimal create/detail flow and duplicate-key error mapping**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 返回字段包含 `id`、`supplierCode`、`supplierName`、`contactName`、`contactPhone`、`settlementMethod`、`address`、`status`、`remark`
- 重复供应商编码统一返回 `供应商编码已存在`

### Task 3: Implement Supplier Update, Enable and Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/web/SupplierUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/supplier/service/SupplierService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/supplier/controller/SupplierController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/supplier/SupplierControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=SupplierControllerLifecycleTest test` and verify it fails because update/status APIs do not exist**
- [x] **Step 3: Implement minimal `PUT /api/masterdata/suppliers/{id}`、`POST /api/masterdata/suppliers/{id}/enable`、`POST /api/masterdata/suppliers/{id}/disable`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 供应商不存在或已逻辑删除时抛 `IllegalArgumentException("供应商不存在")`
- 更新仅允许修改 `supplierName`、`contactName`、`contactPhone`、`settlementMethod`、`address`、`remark`
- 启停只改 `status`

### Task 4: Implement Supplier Page Query API

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/supplier/web/SupplierPageQuery.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/supplier/service/SupplierService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/supplier/controller/SupplierController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/supplier/SupplierControllerPageQueryTest.java`

- [x] **Step 1: Write the failing page query test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=SupplierControllerPageQueryTest test` and verify it fails because list API does not exist or cannot filter**
- [x] **Step 3: Implement minimal `GET /api/masterdata/suppliers` pagination and filter logic**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仅返回未删除供应商
- 支持按 `keyword` 模糊匹配 `supplier_code` / `supplier_name` / `contact_name`
- 支持按 `status`、`settlementMethod` 过滤
- 默认 `pageNo = 1`、`pageSize = 20`，`pageSize` 最大 `200`
- 按 `supplier_code ASC` 排序

### Task 5: Align Initialization Scripts and Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` (if new mapper breaks `@WebMvcTest`)
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java` (if new mapper breaks `@WebMvcTest`)

- [x] **Step 1: Add failing script assertions for `md_supplier`, unique indexes and default demo supplier seed**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails before script alignment**
- [x] **Step 3: Update initialization scripts and WebMvc slice mocks**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and fix any regression**

Rules:
- 初始化脚本保持可重复执行
- 默认种子至少包含一个可用供应商，如 `DEMO_SUPP_001`
- 全量回归必须覆盖新增供应商模块测试

