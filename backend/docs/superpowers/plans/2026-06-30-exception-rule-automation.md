# Exception Rule Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add scheduled exception rule scanning, exception ticket notifications, and one-time overdue ticket escalation.

**Architecture:** Extend existing exception rule rows with schedule metadata, keep scan summaries in the rule table, and reuse existing ticket and notification services. The scheduler runs due rules without relying on an authenticated web request by passing synthetic audit metadata.

**Tech Stack:** Spring Boot, MyBatis Plus, Flyway, JUnit 5, Mockito, Vue 3, Element Plus, Vite.

---

### Task 1: Persist Rule Schedule Fields

**Files:**
- Create: `src/main/resources/db/migration/V82__exception_rule_automation.sql`
- Modify: `docs/migrations-history.md`
- Modify: `src/main/java/com/tuowei/erp/issue/rule/model/ExceptionRuleEntity.java`
- Modify: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleResponse.java`
- Modify: `src/main/java/com/tuowei/erp/issue/rule/web/ExceptionRuleUpdateRequest.java`

- [ ] Add `schedule_interval_minutes` and `next_scan_time`.
- [ ] Add entity getters/setters and response/request fields.
- [ ] Validate schedule interval in service tests before production code.

### Task 2: Scheduled Rule Scanning

**Files:**
- Modify: `src/main/java/com/tuowei/erp/issue/rule/service/ExceptionRuleService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/alert/service/InventoryAlertService.java`
- Create: `src/main/java/com/tuowei/erp/issue/rule/service/ExceptionRuleScheduler.java`
- Modify: `src/main/java/com/tuowei/erp/ErpServerApplication.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`
- Test: `src/test/java/com/tuowei/erp/issue/rule/ExceptionRuleServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/issue/rule/ExceptionRuleSchedulerTest.java`

- [ ] Write failing tests for due-rule selection and next scan time updates.
- [ ] Add audit-aware low-stock scanning so scheduled scans do not need `SecurityContext`.
- [ ] Add scheduler bean guarded by `erp.exception-rule.scheduler.enabled`.
- [ ] Disable scheduler in test profile.

### Task 3: Ticket Notifications and Overdue Escalation

**Files:**
- Modify: `src/main/java/com/tuowei/erp/system/notification/service/NotificationService.java`
- Modify: `src/main/java/com/tuowei/erp/issue/service/ExceptionTicketService.java`
- Test: `src/test/java/com/tuowei/erp/issue/ExceptionTicketServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/workflow/WorkflowNotificationIntegrationTest.java`

- [ ] Write failing tests that ticket creation and assignment produce notification rows.
- [ ] Add a generic business notification method.
- [ ] Send exception-ticket notifications from create, assign, start, resolve, close.
- [ ] Add one-time overdue escalation with priority bump and `ESCALATE` event.

### Task 4: Frontend Schedule Controls

**Files:**
- Modify: `E:/tuowei/python/erp-frontend/src/api/exceptionRule.ts`
- Modify: `E:/tuowei/python/erp-frontend/src/views/exception-rules/index.vue`

- [ ] Add `scheduleIntervalMinutes` and `nextScanTime` types.
- [ ] Show scan interval and next scan time in the table.
- [ ] Add scan interval input to the configuration dialog.

### Task 5: Verification

**Commands:**
- Backend focused: `.\mvnw.cmd -B "-Dtest=ExceptionRuleServiceTest,ExceptionRuleSchedulerTest,ExceptionTicketServiceTest,PermissionCodesStructureTest,TenantTableCoverageConfigurationTest" test`
- Backend full isolated MySQL: recreate `erp_codex_test`, set `SPRING_DATASOURCE_URL`, run `.\mvnw.cmd -B test`
- Frontend: `npm run type-check`
- Frontend: `npm run build`
