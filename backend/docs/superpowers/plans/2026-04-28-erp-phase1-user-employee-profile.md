# ERP User Employee Profile Enhancement Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有用户管理模块上补齐员工档案基础字段和组织绑定能力，让 `sys_user` 能承载员工编号、部门、岗位信息。

**Architecture:** 继续沿用现有用户模块的 `controller + service + mapper + model + web` 结构，在 `sys_user` 上增量扩展 `employee_no` 字段，并通过部门、岗位模块做引用校验。保持当前接口兼容，新增字段都走可选入参，初始化脚本与 Flyway 迁移同步更新。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

### Task 1: Add User Employee Profile Schema Migration

**Files:**
- Modify: `src/test/java/com/tuowei/erp/system/user/UserSchemaMigrationTest.java`
- Create: `src/main/resources/db/migration/V6__system_user_employee_profile.sql`

- [x] **Step 1: Write the failing schema assertion for `employee_no` column**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserSchemaMigrationTest test` and verify it fails**
- [x] **Step 3: Add the minimal migration to append `employee_no` and its unique index**
- [x] **Step 4: Re-run the same test and verify it passes**

### Task 2: Implement User Create and Query with Employee Profile Fields

**Files:**
- Create: `src/test/java/com/tuowei/erp/system/user/UserControllerEmployeeProfileTest.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/model/UserEntity.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/web/UserCreateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/web/UserResponse.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/service/UserService.java`

- [x] **Step 1: Write the failing integration test for employee number + department/post binding**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserControllerEmployeeProfileTest test` and verify it fails**
- [x] **Step 3: Implement minimal create/detail/list support for `employeeNo`、`deptId`、`postId`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- `employeeNo` 可空，但传了就写库并返回
- `deptId` 传了必须校验部门存在
- `postId` 传了必须校验岗位存在
- `postId` 与 `deptId` 同时传入时必须同属一个部门

### Task 3: Implement User Update with Employee Profile Rebinding

**Files:**
- Create: `src/test/java/com/tuowei/erp/system/user/UserControllerEmployeeProfileUpdateTest.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/web/UserUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/service/UserService.java`

- [x] **Step 1: Write the failing update integration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=UserControllerEmployeeProfileUpdateTest test` and verify it fails**
- [x] **Step 3: Implement minimal update support for `employeeNo`、`deptId`、`postId`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 更新时允许重绑部门岗位
- 新岗位如果不属于当前部门，抛 `IllegalArgumentException("岗位不属于当前部门")`
- 保持已有生命周期测试继续通过

### Task 4: Align Initialization Scripts and Full Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`

- [x] **Step 1: Add failing script assertions for `employee_no` and default admin employee number**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails**
- [x] **Step 3: Update initialization scripts to include the new field and seed value**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and verify full regression stays green**

