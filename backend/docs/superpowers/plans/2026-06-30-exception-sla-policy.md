# Exception SLA Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configurable tenant-scoped SLA policies for exception ticket due times and overdue escalation.

**Architecture:** Introduce a focused SLA policy module that owns persistence, default bootstrap, due-time resolution, and escalation resolution. Existing rule and ticket services depend on this module instead of keeping priority timing and escalation logic hard-coded.

**Tech Stack:** Spring Boot, MyBatis Plus, Flyway, JUnit 5, Mockito, Vue 3, Element Plus, Vite.

---

### Task 1: SLA Policy Persistence

**Files:**
- Create: `src/main/resources/db/migration/V83__exception_sla_policy.sql`
- Create: `src/main/java/com/tuowei/erp/issue/sla/model/ExceptionSlaPolicyEntity.java`
- Create: `src/main/java/com/tuowei/erp/issue/sla/mapper/ExceptionSlaPolicyMapper.java`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Test: `src/test/java/com/tuowei/erp/common/config/TenantTableCoverageConfigurationTest.java`

- [ ] Add `biz_exception_sla_policy` with tenant/account-book columns, unique category/priority key, checks, indexes, and default seed rows for tenant `1/1`.
- [ ] Add the MyBatis entity and mapper.
- [ ] Add `biz_exception_sla_policy` to `TENANT_TABLES`.
- [ ] Run tenant coverage after implementation.

### Task 2: SLA Policy Service and API

**Files:**
- Create: `src/main/java/com/tuowei/erp/issue/sla/service/ExceptionSlaPolicyService.java`
- Create: `src/main/java/com/tuowei/erp/issue/sla/service/ExceptionSlaEscalationPolicy.java`
- Create: `src/main/java/com/tuowei/erp/issue/sla/web/ExceptionSlaPolicyPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/issue/sla/web/ExceptionSlaPolicyResponse.java`
- Create: `src/main/java/com/tuowei/erp/issue/sla/web/ExceptionSlaPolicyUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/issue/sla/controller/ExceptionSlaPolicyController.java`
- Create: `src/main/java/com/tuowei/erp/common/security/ExceptionSlaPolicyPermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Test: `src/test/java/com/tuowei/erp/issue/sla/ExceptionSlaPolicyServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/issue/sla/ExceptionSlaPolicyControllerTest.java`
- Test: `src/test/java/com/tuowei/erp/common/security/PermissionCodesStructureTest.java`

- [ ] Write failing service tests for bootstrap, update validation, due-time resolution, and escalation resolution.
- [ ] Write failing controller tests for view/manage permissions and request binding.
- [ ] Implement service validation and mapping.
- [ ] Implement controller endpoints.
- [ ] Register permission facade constants.

### Task 3: Integrate Rules and Tickets

**Files:**
- Modify: `src/main/java/com/tuowei/erp/issue/rule/service/ExceptionRuleService.java`
- Modify: `src/main/java/com/tuowei/erp/issue/service/ExceptionTicketService.java`
- Test: `src/test/java/com/tuowei/erp/issue/rule/ExceptionRuleServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/issue/ExceptionTicketServiceTest.java`

- [ ] Write failing rule-service test proving created tickets use SLA due hours.
- [ ] Write failing ticket-service tests proving configured escalation target is used and disabled escalation is skipped.
- [ ] Inject `ExceptionSlaPolicyService` into both services.
- [ ] Remove hard-coded `dueHours()` and `nextPriority()` usage.
- [ ] Keep existing one-time `ESCALATE` event guard unchanged.

### Task 4: Frontend SLA Policy Page

**Files:**
- Create: `E:/tuowei/python/erp-frontend/src/api/exceptionSlaPolicy.ts`
- Create: `E:/tuowei/python/erp-frontend/src/views/exception-sla-policies/index.vue`
- Modify: `E:/tuowei/python/erp-frontend/src/router/index.ts`

- [ ] Add typed API functions for listing and updating policies.
- [ ] Add route `/exception-sla-policies` with permission `exception-sla-policy:view`.
- [ ] Build the Element Plus table, filters, summary cards, and edit dialog.
- [ ] Reuse compact operational styling from exception rules and tickets.

### Task 5: Verification

**Commands:**
- Backend focused: `.\mvnw.cmd -B "-Dtest=ExceptionSlaPolicyServiceTest,ExceptionSlaPolicyControllerTest,ExceptionRuleServiceTest,ExceptionTicketServiceTest,ExceptionRuleSchedulerTest,PermissionCodesStructureTest,TenantTableCoverageConfigurationTest,FlywayMigrationSmokeTest" test`
- Backend full isolated MySQL:
  `mysql --protocol=tcp -uroot -p12345678 -e "DROP DATABASE IF EXISTS erp_codex_test; CREATE DATABASE erp_codex_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"`
  then set `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/erp_codex_test?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true`
  and run `.\mvnw.cmd -B test`
- Frontend: `npm run type-check`
- Frontend: `npm run build`
- HTTP smoke: request `/exception-sla-policies` from the running Vite server if available.
