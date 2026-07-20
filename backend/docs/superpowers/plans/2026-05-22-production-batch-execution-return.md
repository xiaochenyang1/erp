# Production Batch Execution And Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add batch material issue, batch completion, and production material return while preserving inventory idempotency, finance posting, and account-period guards.

**Architecture:** Add production execution documents for issue, completion, and material return. Each execution line gets its own primary key, and inventory posting uses that line/document id as the idempotency key instead of reusing the production order material id. Returning material posts inbound stock, reverses production cost, and restores the related reservation so the material can be issued again later.

**Tech Stack:** Spring Boot 3.5.x, Java 17, MyBatis-Plus, Flyway, JUnit 5, MockMvc, H2 test profile.

---

## File Map

**Create:**
- `src/main/resources/db/migration/V42__production_batch_execution_schema.sql`
- `src/main/java/com/tuowei/erp/production/issue/model/ProductionIssueEntity.java`
- `src/main/java/com/tuowei/erp/production/issue/model/ProductionIssueLineEntity.java`
- `src/main/java/com/tuowei/erp/production/issue/mapper/ProductionIssueMapper.java`
- `src/main/java/com/tuowei/erp/production/issue/mapper/ProductionIssueLineMapper.java`
- `src/main/java/com/tuowei/erp/production/completion/model/ProductionCompletionEntity.java`
- `src/main/java/com/tuowei/erp/production/completion/mapper/ProductionCompletionMapper.java`
- `src/main/java/com/tuowei/erp/production/returnmaterial/model/ProductionReturnEntity.java`
- `src/main/java/com/tuowei/erp/production/returnmaterial/model/ProductionReturnLineEntity.java`
- `src/main/java/com/tuowei/erp/production/returnmaterial/mapper/ProductionReturnMapper.java`
- `src/main/java/com/tuowei/erp/production/returnmaterial/mapper/ProductionReturnLineMapper.java`
- `src/main/java/com/tuowei/erp/production/returnmaterial/service/ProductionReturnService.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionIssueLineRequest.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionReturnRequest.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionReturnLineRequest.java`

**Modify:**
- `src/main/java/com/tuowei/erp/production/order/controller/ProductionOrderController.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionIssueRequest.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionCompletionRequest.java`
- `src/main/java/com/tuowei/erp/production/order/service/ProductionOrderService.java`
- `src/main/java/com/tuowei/erp/production/issue/service/ProductionIssueService.java`
- `src/main/java/com/tuowei/erp/production/completion/service/ProductionCompletionService.java`
- `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java`
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- `src/test/java/com/tuowei/erp/production/order/ProductionOrderServiceTest.java`

## Tasks

- [x] Write failing service tests for batch issue/completion and return/reissue.
- [x] Add schema, entities, mappers, request DTOs, sequence rules, and return permission.
- [x] Implement batch issue with per-issue-line inventory and finance posting.
- [x] Implement batch completion with quantity validation and per-completion finance posting.
- [x] Implement material return with stock inbound, production cost reversal, and reservation restore.
- [x] Run focused production tests and release gate.

## Implementation Evidence

- `V42__production_batch_execution_schema.sql` adds `prd_issue`, `prd_issue_line`, `prd_completion`, `prd_return`, and `prd_return_line` execution document tables.
- Batch issue writes one issue document per action and uses `prd_issue_line.id` as the inventory idempotency line key, so repeated batches do not collide on the original order material id.
- Batch completion writes one completion document per action, validates remaining planned quantity and issued-material completable quantity, then posts finished-goods inbound stock and `1001/5001` finance entries.
- Production material return posts inbound material stock, restores the production reservation, reduces issued quantity/amount, and posts `1001/5001` reversal entries.
- HTTP coverage exists in `ProductionOrderControllerTest`; service coverage exists in `ProductionOrderServiceTest`, including batch issue/completion, return permission, return/reissue, and voucher counts.
- Focused verification on 2026-05-22 passed for `ProductionOrderControllerTest`, `ProductionOrderServiceTest`, and `FlywayMigrationSmokeTest`.
