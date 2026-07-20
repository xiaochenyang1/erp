# Exception Rule Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an exception rule engine that scans ERP data, records rule hits, and automatically creates deduplicated exception tickets.

**Architecture:** Backend adds a focused `issue.rule` package with rule/hit tables, permission-guarded REST endpoints, built-in scanner logic, and ticket deduplication through the existing exception-ticket domain. Frontend adds a compact Element Plus rule center with rule filters, scan/configuration actions, and latest hit records.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, JUnit/Mockito/MockMvc, Vue 3, TypeScript, Element Plus.

---

### Task 1: Backend Contract Tests

**Files:**
- Create: `src/test/java/com/tuowei/erp/issue/rule/ExceptionRuleControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/issue/rule/ExceptionRuleServiceTest.java`

- [ ] Add controller tests for `GET /api/exception-rules`, `PUT /api/exception-rules/{id}`, enable/disable, scan-one, scan-all, and hit listing.
- [ ] Add service tests for tenant-scoped list queries, update validation, enable/disable, scan ticket creation, scan ticket deduplication, and disabled-rule rejection.
- [ ] Run `.\mvnw.cmd -B "-Dtest=ExceptionRuleControllerTest,ExceptionRuleServiceTest" test`.
- [ ] Confirm the test run fails because `ExceptionRuleController`, `ExceptionRuleService`, and related DTOs do not exist yet.

### Task 2: Schema, Permissions, Entities, And Mappers

**Files:**
- Create: `src/main/resources/db/migration/V81__exception_rule_engine.sql`
- Create: `src/main/java/com/tuowei/erp/common/security/ExceptionRulePermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/PermissionCodesStructureTest.java`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/model/ExceptionRuleEntity.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/model/ExceptionRuleHitEntity.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/mapper/ExceptionRuleMapper.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/mapper/ExceptionRuleHitMapper.java`

- [ ] Add `biz_exception_rule` with rule metadata, threshold fields, enabled state, assignee, and last scan metadata.
- [ ] Add `biz_exception_rule_hit` with normalized finding data, hit counters, ticket link, and timestamps.
- [ ] Seed four built-in rules and route/menu permissions.
- [ ] Register new tables in the tenant interceptor.
- [ ] Add permission constants and include them in `PermissionCodes`.
- [ ] Add MyBatis entities and mappers.
- [ ] Run the focused backend tests again and confirm they still fail only because service/controller behavior is missing.

### Task 3: Backend Service And Controller

**Files:**
- Create: `src/main/java/com/tuowei/erp/issue/rule/controller/ExceptionRuleController.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/service/ExceptionRuleService.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/service/ExceptionRuleFinding.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRulePageQuery.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleHitPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleResponse.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleHitResponse.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleScanResultResponse.java`

- [ ] Implement list/update/enable/disable.
- [ ] Implement scan-one and scan-all with scanners for low stock, receivable overdue, payable overdue, and operation failures.
- [ ] Upsert hits by `rule_id + hit_key`.
- [ ] Deduplicate tickets by `source_type + source_id` and active statuses `OPEN`, `PROCESSING`, `RESOLVED`.
- [ ] Create new tickets through `ExceptionTicketService.create` only when no active ticket exists.
- [ ] Update rule scan metadata after each scan.
- [ ] Run `.\mvnw.cmd -B "-Dtest=ExceptionRuleControllerTest,ExceptionRuleServiceTest,PermissionCodesStructureTest,TenantTableCoverageConfigurationTest" test` and confirm it passes.

### Task 4: Frontend API, Route, And Page

**Files:**
- Create: `E:/tuowei/python/erp-frontend/src/api/exceptionRule.ts`
- Modify: `E:/tuowei/python/erp-frontend/src/router/index.ts`
- Create: `E:/tuowei/python/erp-frontend/src/views/exception-rules/index.vue`

- [ ] Add TypeScript interfaces and request functions for rules, hits, update, enable/disable, scan-one, and scan-all.
- [ ] Add `/exception-rules` route with `exception-rule:view`.
- [ ] Build a dense Element Plus workbench with filters, rule table, edit dialog, and hit table.
- [ ] Wire scan and configuration actions to backend APIs.
- [ ] Run `npm run type-check` from `E:/tuowei/python/erp-frontend`.

### Task 5: Final Verification

**Files:**
- Read changed backend and frontend files.

- [ ] Run focused backend tests.
- [ ] Run backend full tests against isolated `erp_codex_test`.
- [ ] Run frontend `npm run type-check`.
- [ ] Run frontend `npm run build`.
- [ ] Scan changed source files for unfinished-work markers and random data generation.
- [ ] Start or reuse Vite on an available port and HTTP-smoke `/exception-rules`.
