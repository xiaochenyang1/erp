# Exception Ticket Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an exception ticket center for assigning, processing, resolving, and closing business exceptions.

**Architecture:** Backend adds a focused `issue` domain with ticket and ticket-event tables, service-level state transitions, and permission-guarded REST endpoints. Frontend adds a compact Element Plus workbench page with filters, summary metrics, table actions, and create/process dialogs.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, JUnit/Mockito/MockMvc, Vue 3, TypeScript, Element Plus.

---

### Task 1: Backend Contract Tests

**Files:**
- Create: `src/test/java/com/tuowei/erp/issue/ExceptionTicketControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/issue/ExceptionTicketServiceTest.java`

- [ ] Write controller tests for permission checks, list binding, create binding, and action endpoints.
- [ ] Write service tests for create/list tenant filters, state transition events, and invalid transition rejection.
- [ ] Run `.\mvnw.cmd -B "-Dtest=ExceptionTicketControllerTest,ExceptionTicketServiceTest" test` and confirm it fails because production classes do not exist.

### Task 2: Backend Schema And Domain

**Files:**
- Create: `src/main/resources/db/migration/V80__exception_ticket_center.sql`
- Modify: `docs/migrations-history.md`
- Create: `src/main/java/com/tuowei/erp/common/security/ExceptionTicketPermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Create: `src/main/java/com/tuowei/erp/issue/model/ExceptionTicketEntity.java`
- Create: `src/main/java/com/tuowei/erp/issue/model/ExceptionTicketEventEntity.java`
- Create: `src/main/java/com/tuowei/erp/issue/mapper/ExceptionTicketMapper.java`
- Create: `src/main/java/com/tuowei/erp/issue/mapper/ExceptionTicketEventMapper.java`

- [ ] Add ticket and event tables with tenant/account-book columns and useful indexes.
- [ ] Document skipped `V79` in migration history.
- [ ] Add permission constants.
- [ ] Add MyBatis entities and mappers.

### Task 3: Backend Service And Controller

**Files:**
- Create: `src/main/java/com/tuowei/erp/issue/controller/ExceptionTicketController.java`
- Create: `src/main/java/com/tuowei/erp/issue/service/ExceptionTicketService.java`
- Create: `src/main/java/com/tuowei/erp/issue/web/ExceptionTicketPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/issue/web/ExceptionTicketCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/issue/web/ExceptionTicketAssignRequest.java`
- Create: `src/main/java/com/tuowei/erp/issue/web/ExceptionTicketActionRequest.java`
- Create: `src/main/java/com/tuowei/erp/issue/web/ExceptionTicketResponse.java`
- Create: `src/main/java/com/tuowei/erp/issue/web/ExceptionTicketEventResponse.java`

- [ ] Implement list, create, assign, start, resolve, and close.
- [ ] Enforce allowed transitions.
- [ ] Write event rows for create and every action.
- [ ] Run focused backend tests and confirm they pass.

### Task 4: Frontend API, Route, And Page

**Files:**
- Create: `src/api/exceptionTicket.ts`
- Modify: `src/router/index.ts`
- Create: `src/views/exception-tickets/index.vue`

- [ ] Add API interfaces and request functions.
- [ ] Add `/exception-tickets` route with `exception-ticket:view`.
- [ ] Build filter bar, summary cards, ticket table, create dialog, and action dialog.
- [ ] Wire action buttons to backend endpoints.
- [ ] Run `npm run type-check`.

### Task 5: Final Verification

**Files:**
- Read all files above.

- [ ] Run backend focused tests.
- [ ] Run backend full tests against isolated `erp_codex_test`.
- [ ] Run frontend `npm run type-check` and `npm run build`.
- [ ] Scan new files for obvious placeholder leftovers.
- [ ] HTTP-smoke `/exception-tickets` on the local Vite server.
