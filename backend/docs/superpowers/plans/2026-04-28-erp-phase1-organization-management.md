# ERP Organization Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐系统管理模块的部门与岗位管理能力，打通组织基础数据建档、查询、维护、启停和数据库初始化链路。

**Architecture:** 组织模块沿用现有 `controller + service + mapper + model + web` 结构，部门按树形结构建模，岗位按平铺列表建模并归属到部门。测试继续统一走 `H2 + Flyway + MockMvc`，初始化脚本同步对齐当前阶段表结构和种子数据。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add Flyway Schema for Department and Post

**Files:**
- Create: `src/main/resources/db/migration/V5__system_org_schema.sql`
- Create: `src/test/java/com/tuowei/erp/system/org/OrganizationSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=OrganizationSchemaMigrationTest test` and verify it fails because `sys_dept` / `sys_post` do not exist**
- [x] **Step 3: Add `sys_dept` and `sys_post` tables plus unique/index definitions**
- [x] **Step 4: Re-run the same test and verify it passes**

### Task 2: Implement Department Create and Tree APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/dept/model/DeptEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/dept/mapper/DeptMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/dept/web/DeptCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/dept/web/DeptResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/dept/service/DeptService.java`
- Create: `src/main/java/com/tuowei/erp/system/dept/controller/DeptController.java`
- Create: `src/test/java/com/tuowei/erp/system/dept/DeptControllerTreeTest.java`

- [x] **Step 1: Write the failing integration test for root department creation, child department creation and tree query**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DeptControllerTreeTest test` and verify it fails because `/api/system/depts` does not exist**
- [x] **Step 3: Implement the minimal department create and tree flow**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- `parentId` 为空时按 `0`
- `sortNo` 为空时按 `0`
- `status = "ACTIVE"`
- `deletedFlag = 0`
- 树查询仅返回未删除部门，按 `parentId + sortNo + id` 排序

### Task 3: Implement Department Detail, Update, Enable and Disable APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/dept/web/DeptUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/dept/service/DeptService.java`
- Modify: `src/main/java/com/tuowei/erp/system/dept/controller/DeptController.java`
- Create: `src/test/java/com/tuowei/erp/system/dept/DeptControllerLifecycleTest.java`

- [x] **Step 1: Write the failing lifecycle test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DeptControllerLifecycleTest test` and verify it fails because detail/update/status APIs do not exist**
- [x] **Step 3: Implement minimal `GET /api/system/depts/{id}`、`PUT /api/system/depts/{id}`、`POST /api/system/depts/{id}/enable`、`POST /api/system/depts/{id}/disable`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 部门不存在或已逻辑删除时抛 `IllegalArgumentException("部门不存在")`
- 更新仅允许修改 `deptName`、`leaderUserId`、`sortNo`、`remark`
- 启停只改 `status`

### Task 4: Implement Post Create, List and Lifecycle APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/post/model/PostEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/post/mapper/PostMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/post/web/PostCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/post/web/PostUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/post/web/PostResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/post/service/PostService.java`
- Create: `src/main/java/com/tuowei/erp/system/post/controller/PostController.java`
- Create: `src/test/java/com/tuowei/erp/system/post/PostControllerCreateTest.java`
- Create: `src/test/java/com/tuowei/erp/system/post/PostControllerLifecycleTest.java`

- [x] **Step 1: Write the failing create/list test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=PostControllerCreateTest test` and verify it fails because `/api/system/posts` does not exist**
- [x] **Step 3: Implement minimal post create and list flow**
- [x] **Step 4: Re-run the create/list test and verify it passes**
- [x] **Step 5: Write the failing lifecycle test**
- [x] **Step 6: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=PostControllerLifecycleTest test` and verify it fails because detail/update/status APIs do not exist**
- [x] **Step 7: Implement minimal post detail/update/enable/disable flow**
- [x] **Step 8: Re-run the lifecycle test and verify it passes**

Rules:
- 岗位创建必须校验 `deptId` 对应部门存在且未删除，否则抛 `IllegalArgumentException("部门不存在")`
- 岗位列表仅返回未删除岗位，按 `postCode ASC` 排序
- 岗位不存在或已逻辑删除时抛 `IllegalArgumentException("岗位不存在")`

### Task 5: Align Initialization Scripts and Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` (if new mappers break `@WebMvcTest`)
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java` (if new mappers break `@WebMvcTest`)

- [x] **Step 1: Add failing script assertions for `sys_dept` / `sys_post` and default organization seed data**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails before script alignment**
- [x] **Step 3: Update initialization scripts to include organization tables, indexes and default department/post**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and fix any new `WebMvcTest` mapper mock issues**

Rules:
- 初始化脚本保持可重复执行
- 默认安全数据至少包含一个根部门和一个管理员岗位
- 全量回归必须覆盖新增组织模块测试

