# ERP Customer Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐主数据模块的客户档案能力，支持客户新增、详情、更新、启停、分页查询以及数据库初始化脚本同步。

**Architecture:** 客户模块沿用现有 `controller + service + mapper + model + web` 结构，落在 `masterdata/customer` 包下。当前阶段先落 `md_customer` 主档表，把联系人、电话、结算方式、信用额度、地址作为客户主表字段直接持久化，先满足销售、应收、报表后续引用需求，后续再细化客户分类、区域、价格体系等扩展能力。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for Customer

**Files:**
- Create: `src/main/resources/db/migration/V11__masterdata_customer_schema.sql`
- Create: `src/test/java/com/tuowei/erp/masterdata/customer/CustomerSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=CustomerSchemaMigrationTest test` and verify it fails because `md_customer` does not exist**
- [x] **Step 3: Add `md_customer` table plus unique/index definitions**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 客户编码唯一，唯一索引名固定为 `uk_md_customer_customer_code`
- 默认 `status = "ACTIVE"`、`deleted_flag = 0`
- 建立 `idx_md_customer_customer_name`
- 建立 `idx_md_customer_contact_phone`

### Task 2: Implement Customer Create and Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/model/CustomerEntity.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/mapper/CustomerMapper.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/web/CustomerCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/web/CustomerResponse.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/service/CustomerService.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/controller/CustomerController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/customer/CustomerControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for customer creation, detail query and duplicate code rejection**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=CustomerControllerCreateDetailTest test` and verify it fails because `/api/masterdata/customers` does not exist**
- [x] **Step 3: Implement the minimal create/detail flow and duplicate-key error mapping**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 返回字段包含 `id`、`customerCode`、`customerName`、`contactName`、`contactPhone`、`settlementMethod`、`creditLimit`、`address`、`status`、`remark`
- 重复客户编码统一返回 `客户编码已存在`

### Task 3: Implement Customer Update, Enable and Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/web/CustomerUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/customer/service/CustomerService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/customer/controller/CustomerController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/customer/CustomerControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=CustomerControllerLifecycleTest test` and verify it fails because update/status APIs do not exist**
- [x] **Step 3: Implement minimal `PUT /api/masterdata/customers/{id}`、`POST /api/masterdata/customers/{id}/enable`、`POST /api/masterdata/customers/{id}/disable`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 客户不存在或已逻辑删除时抛 `IllegalArgumentException("客户不存在")`
- 更新仅允许修改 `customerName`、`contactName`、`contactPhone`、`settlementMethod`、`creditLimit`、`address`、`remark`
- 启停只改 `status`

### Task 4: Implement Customer Page Query API

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/customer/web/CustomerPageQuery.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/customer/service/CustomerService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/customer/controller/CustomerController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/customer/CustomerControllerPageQueryTest.java`

- [x] **Step 1: Write the failing page query test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=CustomerControllerPageQueryTest test` and verify it fails because list API does not exist or cannot filter**
- [x] **Step 3: Implement minimal `GET /api/masterdata/customers` pagination and filter logic**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仅返回未删除客户
- 支持按 `keyword` 模糊匹配 `customer_code` / `customer_name` / `contact_name`
- 支持按 `status`、`settlementMethod` 过滤
- 默认 `pageNo = 1`、`pageSize = 20`，`pageSize` 最大 `200`
- 按 `customer_code ASC` 排序

### Task 5: Align Initialization Scripts and Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` (if new mapper breaks `@WebMvcTest`)
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java` (if new mapper breaks `@WebMvcTest`)

- [x] **Step 1: Add failing script assertions for `md_customer`, unique indexes and default demo customer seed**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails before script alignment**
- [x] **Step 3: Update initialization scripts and WebMvc slice mocks**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and fix any regression**

Rules:
- 初始化脚本保持可重复执行
- 默认种子至少包含一个可用客户，如 `DEMO_CUST_001`
- 全量回归必须覆盖新增客户模块测试

