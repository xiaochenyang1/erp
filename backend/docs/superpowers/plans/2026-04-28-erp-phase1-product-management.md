# ERP Product Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐主数据模块的商品档案能力，支持商品新增、详情、更新、启停、分页查询以及数据库初始化脚本同步。

**Architecture:** 商品模块沿用现有 `controller + service + mapper + model + web` 结构，落在 `masterdata/product` 包下。当前阶段先落 `md_product` 主档表，把分类、单位、税率作为商品字段直接持久化，先满足采购、销售、库存后续引用需求，后续再独立细化 `md_product_category`、`md_unit`、`md_tax_rate`。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for Product

**Files:**
- Create: `src/main/resources/db/migration/V10__masterdata_product_schema.sql`
- Create: `src/test/java/com/tuowei/erp/masterdata/product/ProductSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductSchemaMigrationTest test` and verify it fails because `md_product` does not exist**
- [x] **Step 3: Add `md_product` table plus unique/index definitions**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 商品编码唯一，唯一索引名固定为 `uk_md_product_product_code`
- 默认 `status = "ACTIVE"`、`deleted_flag = 0`
- 建立 `idx_md_product_category_name`

### Task 2: Implement Product Create and Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/product/model/ProductEntity.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/product/mapper/ProductMapper.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductResponse.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/product/service/ProductService.java`
- Create: `src/main/java/com/tuowei/erp/masterdata/product/controller/ProductController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/product/ProductControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for product creation, detail query and duplicate code rejection**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductControllerCreateDetailTest test` and verify it fails because `/api/masterdata/products` does not exist**
- [x] **Step 3: Implement the minimal create/detail flow and duplicate-key error mapping**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仅支持 `productType = "PHYSICAL"`
- 返回字段包含 `id`、`productCode`、`productName`、`productType`、`categoryName`、`specification`、`unitName`、`purchasePrice`、`salePrice`、`taxRate`、`status`、`remark`
- 重复商品编码统一返回 `商品编码已存在`

### Task 3: Implement Product Update, Enable and Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/service/ProductService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/controller/ProductController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/product/ProductControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductControllerLifecycleTest test` and verify it fails because update/status APIs do not exist**
- [x] **Step 3: Implement minimal `PUT /api/masterdata/products/{id}`、`POST /api/masterdata/products/{id}/enable`、`POST /api/masterdata/products/{id}/disable`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 商品不存在或已逻辑删除时抛 `IllegalArgumentException("商品不存在")`
- 更新仅允许修改 `productName`、`categoryName`、`specification`、`unitName`、`purchasePrice`、`salePrice`、`taxRate`、`remark`
- 启停只改 `status`

### Task 4: Implement Product Page Query API

**Files:**
- Create: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductPageQuery.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/service/ProductService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/controller/ProductController.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/product/ProductControllerPageQueryTest.java`

- [x] **Step 1: Write the failing page query test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductControllerPageQueryTest test` and verify it fails because list API does not exist or cannot filter**
- [x] **Step 3: Implement minimal `GET /api/masterdata/products` pagination and filter logic**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 仅返回未删除商品
- 支持按 `keyword` 模糊匹配 `product_code` / `product_name`
- 支持按 `status`、`categoryName` 过滤
- 默认 `pageNo = 1`、`pageSize = 20`，`pageSize` 最大 `200`
- 按 `product_code ASC` 排序

### Task 5: Align Initialization Scripts and Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` (if new mapper breaks `@WebMvcTest`)
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java` (if new mapper breaks `@WebMvcTest`)

- [x] **Step 1: Add failing script assertions for `md_product`, unique index and default demo product seed**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails before script alignment**
- [x] **Step 3: Update initialization scripts and WebMvc slice mocks**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and fix any regression**

Rules:
- 初始化脚本保持可重复执行
- 默认种子至少包含一个可用商品，如 `DEMO_SKU_001`
- 全量回归必须覆盖新增商品模块测试

