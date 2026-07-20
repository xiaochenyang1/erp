# Workflow Withdraw And Resubmit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add workflow submitter withdraw support and allow rejected/withdrawn workflows to be submitted again without losing history.

**Architecture:** Keep workflow lifecycle logic inside `WorkflowService`, expose a thin `WorkflowController` endpoint, and update Flyway constraints so only active `IN_APPROVAL` instances are unique per tenant/business source. Preserve historical instances and records for audit.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway SQL migrations, JUnit 5, AssertJ, MockMvc.

---

### Task 1: Lifecycle Service Tests

**Files:**
- Modify: `src/test/java/com/tuowei/erp/workflow/WorkflowNotificationIntegrationTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that call `workflowService.withdraw("SALES_ORDER", BUSINESS_ID, "withdraw")`, assert `WITHDRAWN` instance status, closed pending task, closed pending notification, and `WITHDRAW` record. Add a second test asserting withdraw is rejected after an approver has already approved. Add a third test asserting rejected workflows can be submitted again and produce two instances plus a fresh active todo.

- [ ] **Step 2: Verify RED**

Run: `.\mvnw.cmd "-Dtest=WorkflowNotificationIntegrationTest" test`
Expected: compile/test failure because `WorkflowService.withdraw(...)` and lifecycle behavior do not exist yet.

### Task 2: Workflow Lifecycle Implementation

**Files:**
- Modify: `src/main/java/com/tuowei/erp/workflow/service/WorkflowService.java`
- Create: `src/main/resources/db/migration/V51__workflow_withdraw_resubmit.sql`

- [ ] **Step 1: Add migration**

Create migration that allows `WITHDRAWN` in `wf_approval_instance.status`, allows `WITHDRAW` in `wf_approval_record.action`, drops the old `(business_type, business_id, status)` unique index, and creates a tenant-aware unique index on `(company_id, account_book_id, business_type, business_id, status)` so only one active `IN_APPROVAL` instance can exist while completed historical statuses can repeat per tenant.

- [ ] **Step 2: Implement service**

Add `withdraw(String businessType, Long businessId, String comment)`. It must require an active instance, require current user is submitter, reject if the instance already has `APPROVE` or `REJECT` records, update status to `WITHDRAWN`, close pending task and todo, and insert `WITHDRAW` record.

- [ ] **Step 3: Verify GREEN**

Run: `.\mvnw.cmd "-Dtest=WorkflowNotificationIntegrationTest,FlywayMigrationSmokeTest" test`
Expected: tests pass.

### Task 3: HTTP Endpoint

**Files:**
- Modify: `src/main/java/com/tuowei/erp/workflow/controller/WorkflowController.java`
- Create: `src/main/java/com/tuowei/erp/workflow/web/WorkflowWithdrawRequest.java`
- Modify: `src/test/java/com/tuowei/erp/workflow/WorkflowApprovalConfigControllerTest.java` or add a focused workflow controller test if needed.

- [ ] **Step 1: Add endpoint test**

Add a MockMvc test for `POST /api/workflow/{businessType}/{businessId}/withdraw` with body `{"comment":"withdraw"}` and assert HTTP 200 plus persisted `WITHDRAWN` status.

- [ ] **Step 2: Implement endpoint**

Add request record with `comment`, and controller method guarded by `PermissionCodes.HAS_WORKFLOW_VIEW` that delegates to `workflowService.withdraw(...)`.

- [ ] **Step 3: Final verification**

Run: `.\mvnw.cmd "-Dtest=WorkflowNotificationIntegrationTest,WorkflowApprovalConfigControllerTest,FlywayMigrationSmokeTest" test`, then `.\mvnw.cmd test`.
Expected: all tests pass with zero failures and zero errors.
