# Workflow Center Minimum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal workflow center that records approval instances, todo tasks and approval records while keeping existing purchase and sales order APIs compatible.

**Architecture:** Workflow lives in `com.tuowei.erp.workflow` and owns approval persistence only. Business services keep their status transitions, but delegate submit/approve/reject audit trail creation to `WorkflowService` in the same transaction. This avoids a big-bang workflow engine and still removes the current "status only, no approval center" gap.

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

- Create: `src/main/resources/db/migration/V20__workflow_schema.sql`
- Create: `src/main/java/com/tuowei/erp/workflow/model/WorkflowInstanceEntity.java`
- Create: `src/main/java/com/tuowei/erp/workflow/model/WorkflowTaskEntity.java`
- Create: `src/main/java/com/tuowei/erp/workflow/model/WorkflowRecordEntity.java`
- Create: `src/main/java/com/tuowei/erp/workflow/mapper/WorkflowInstanceMapper.java`
- Create: `src/main/java/com/tuowei/erp/workflow/mapper/WorkflowTaskMapper.java`
- Create: `src/main/java/com/tuowei/erp/workflow/mapper/WorkflowRecordMapper.java`
- Create: `src/main/java/com/tuowei/erp/workflow/service/WorkflowService.java`
- Test: `src/test/java/com/tuowei/erp/workflow/WorkflowSchemaMigrationTest.java`
- Test: `src/test/java/com/tuowei/erp/workflow/WorkflowServiceTest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/sales/order/service/SalesOrderService.java`
- Modify: existing purchase and sales order workflow tests if needed

## Task 1: Workflow Schema

- [x] Write `WorkflowSchemaMigrationTest` asserting `wf_approval_instance`, `wf_approval_task`, and `wf_approval_record` exist with source and status columns.
- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=WorkflowSchemaMigrationTest" test`; expected failure is missing workflow tables.
- [x] Add `V20__workflow_schema.sql` with unique active source index, task source index and record source index.
- [x] Re-run the same test; expected `BUILD SUCCESS`.

## Task 2: Workflow Service

- [x] Write `WorkflowServiceTest` proving `submit` creates one active instance, one pending task, and one submit record.
- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=WorkflowServiceTest" test`; expected failure is missing service/classes.
- [x] Implement workflow entities, mappers and `WorkflowService.submit`.
- [x] Add tests for `approve` and `reject`: both close pending tasks, complete the active instance, and write an approval record.
- [x] Implement `approve` and `reject`.
- [x] Add cancel handling so order cancellation closes the active workflow instance, closes the pending task, and writes a `CANCEL` record.

## Task 3: Purchase And Sales Integration

- [x] Add assertions to existing purchase/sales order workflow tests that submit/approve/reject write workflow records.
- [x] Run targeted tests; expected failure is missing workflow writes from business services.
- [x] Inject `WorkflowService` into purchase and sales order services.
- [x] Call `workflowService.submit` after status changes to `IN_APPROVAL`.
- [x] Call `workflowService.approve` or `workflowService.reject` after approval result changes.
- [x] Call `workflowService.cancel` after order cancellation changes approval status to `CANCELLED`.
- [x] Re-run targeted tests.

## Task 4: Regression

- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=Workflow*Test,PurchaseOrderControllerWorkflowTest,SalesOrderControllerWorkflowTest" test`.
- [x] Run full `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test`.

## Follow-up: Workflow Query API

- [x] Add read-only `GET /api/workflow/tasks` and `GET /api/workflow/records` endpoints for the workflow center minimum usable surface.
- [x] Add `workflow:view` permission guard.
- [x] Add controller regression coverage for task and record listing filters.
- [x] Run workflow targeted regression and full `mvn test`.

