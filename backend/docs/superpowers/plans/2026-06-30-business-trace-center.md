# Business Trace Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only business document trace center that searches a business keyword and shows related documents, timeline events, and risk summary.

**Architecture:** Backend adds a dedicated report trace controller and service instead of bloating `ReportQueryService`. Frontend adds a focused API module and a new Element Plus trace page wired into the existing static router.

**Tech Stack:** Spring Boot, MyBatis-Plus, JUnit/Mockito/MockMvc, Vue 3, TypeScript, Element Plus.

---

### Task 1: Backend Contract Tests

**Files:**
- Create: `src/test/java/com/tuowei/erp/report/BusinessTraceControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/report/BusinessTraceServiceTest.java`

- [ ] Write controller test for `GET /api/reports/business-traces` requiring `report:view`.
- [ ] Write controller test binding `keyword` and returning `documents[0].bizNo`.
- [ ] Write service test that mocks mappers and verifies summary, documents, timeline, and tenant/account filters.
- [ ] Write service test for blank keyword returning empty lists.
- [ ] Run `.\mvnw.cmd -B "-Dtest=BusinessTraceControllerTest,BusinessTraceServiceTest" test` and confirm it fails because production classes do not exist.

### Task 2: Backend Trace Implementation

**Files:**
- Create: `src/main/java/com/tuowei/erp/report/controller/BusinessTraceController.java`
- Create: `src/main/java/com/tuowei/erp/report/service/BusinessTraceService.java`
- Create: `src/main/java/com/tuowei/erp/report/web/BusinessTraceQuery.java`
- Create: `src/main/java/com/tuowei/erp/report/web/BusinessTraceResponse.java`
- Create: `src/main/java/com/tuowei/erp/report/web/BusinessTraceDocumentResponse.java`
- Create: `src/main/java/com/tuowei/erp/report/web/BusinessTraceTimelineResponse.java`
- Create: `src/main/java/com/tuowei/erp/report/web/BusinessTraceSummaryResponse.java`

- [ ] Add query and response records.
- [ ] Implement controller with `@PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)`.
- [ ] Implement service using existing mappers for orders, receipts, deliveries, finance, inventory transactions, workflow tasks, and operation logs.
- [ ] Normalize blank keyword to an empty response.
- [ ] Apply `companyId` and `accountBookId` filters to every wrapper.
- [ ] Cap each source query with `limit 20`.
- [ ] Run focused backend tests and confirm they pass.

### Task 3: Frontend API And Route

**Files:**
- Create: `src/api/businessTrace.ts`
- Modify: `src/router/index.ts`

- [ ] Add TypeScript interfaces matching the backend response.
- [ ] Add `getBusinessTrace(params)` calling `/reports/business-traces`.
- [ ] Add route `/reports/traces` with title `单据追踪`, icon `Search`, permission `report:view`.
- [ ] Run `npm run type-check` and fix route/API typing issues.

### Task 4: Frontend Page

**Files:**
- Create: `src/views/reports/traces/index.vue`

- [ ] Build a search form with keyword, query, and reset buttons.
- [ ] Render summary metrics.
- [ ] Render matched documents in an `el-table`.
- [ ] Render trace events in an `el-timeline`.
- [ ] Add route jump actions for rows and timeline events.
- [ ] Add loading and empty states.
- [ ] Run `npm run type-check` and `npm run build`.

### Task 5: Final Verification

**Files:**
- Read: `docs/superpowers/specs/2026-06-30-business-trace-center-design.md`
- Read: `docs/superpowers/plans/2026-06-30-business-trace-center.md`

- [ ] Run backend focused tests.
- [ ] Run backend full tests against isolated `erp_codex_test`.
- [ ] Run frontend type-check and build.
- [ ] Confirm no obvious mock or placeholder leftovers in new trace files.
- [ ] Report changed files, verification results, and known warnings.
