# Operations Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only operations dashboard that replaces static frontend mock data with real pending work and exception data.

**Architecture:** Add a Spring Boot `dashboard` package with a single read-only aggregation endpoint. The frontend adds one dashboard API module and updates the existing dashboard page to render the aggregate response.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, JUnit/Mockito/MockMvc, Vue 3, TypeScript, Element Plus, ECharts.

---

### Task 1: Backend Contract Tests

**Files:**
- Create: `src/test/java/com/tuowei/erp/dashboard/OperationsDashboardControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/dashboard/OperationsDashboardServiceTest.java`

- [ ] **Step 1: Write controller red test**

Create a MockMvc test for `GET /api/dashboard/operations` that uses `@WithErpUser`, stubs `OperationsDashboardService.getOperationsDashboard()`, and asserts `summary.pendingApprovals`, `todos[0].route`, and `generatedAt`.

- [ ] **Step 2: Write service red test**

Create a Mockito service test that stubs workflow, finance, order, log, and low-stock mappers/services, then asserts:

- tenant filters use current `companyId` and `accountBookId`
- pending workflow count is user-specific
- overdue receivable/payable todos are `HIGH`
- failed operation todos route to `/system/logs`
- todo list is capped at 12

- [ ] **Step 3: Run focused red tests**

Run:

```powershell
.\mvnw.cmd -B -Dtest=OperationsDashboardControllerTest,OperationsDashboardServiceTest test
```

Expected: compile failure because dashboard classes do not exist.

### Task 2: Backend Implementation

**Files:**
- Create: `src/main/java/com/tuowei/erp/dashboard/controller/OperationsDashboardController.java`
- Create: `src/main/java/com/tuowei/erp/dashboard/service/OperationsDashboardService.java`
- Create: `src/main/java/com/tuowei/erp/dashboard/web/OperationsDashboardResponse.java`
- Create: `src/main/java/com/tuowei/erp/dashboard/web/OperationsDashboardSummaryResponse.java`
- Create: `src/main/java/com/tuowei/erp/dashboard/web/OperationsDashboardTodoResponse.java`
- Create: `src/main/java/com/tuowei/erp/dashboard/web/OperationsDashboardLowStockResponse.java`
- Create: `src/main/java/com/tuowei/erp/dashboard/web/OperationsDashboardFailedOperationResponse.java`

- [ ] **Step 1: Implement response records**

Define records matching the design: summary, todos, lowStock, failedOperations, generatedAt.

- [ ] **Step 2: Implement controller**

Expose `GET /api/dashboard/operations` and return `ApiResponse.success(service.getOperationsDashboard())`. Require authentication with `@PreAuthorize("isAuthenticated()")`.

- [ ] **Step 3: Implement service aggregation**

Use existing mappers/services with current audit metadata:

- `WorkflowTaskMapper` for pending tasks assigned to the current user.
- `InventoryAlertService.listLowStock(null, null)` for low-stock previews.
- `ReceivableMapper` and `PayableMapper` for open and overdue settlement records.
- `PurchaseOrderMapper` for today's non-deleted purchase orders.
- `SalesOrderMapper` for today's non-deleted sales amount.
- `OperationLogMapper` for recent failed operations.

- [ ] **Step 4: Run focused backend tests**

Run:

```powershell
.\mvnw.cmd -B -Dtest=OperationsDashboardControllerTest,OperationsDashboardServiceTest test
```

Expected: tests pass.

### Task 3: Frontend API And Dashboard Page

**Files:**
- Create: `E:\tuowei\python\erp-frontend\src\api\dashboard.ts`
- Modify: `E:\tuowei\python\erp-frontend\src\views\dashboard\index.vue`

- [ ] **Step 1: Add TypeScript API module**

Define `OperationsDashboardResponse`, summary/todo/low-stock/failed-operation interfaces, and `getOperationsDashboard()`.

- [ ] **Step 2: Replace dashboard mock data**

Load dashboard data on mount, replace hard-coded stats, todos, and alerts, and make todo clicks route to `todo.route`.

- [ ] **Step 3: Remove randomized chart data**

Keep the existing chart layout, but derive chart values from today's purchase order count and sales amount so the build is deterministic.

- [ ] **Step 4: Run frontend checks**

Run:

```powershell
npm run type-check
npm run build
```

Expected: both commands exit 0.

### Task 4: Full Verification

**Files:**
- No source changes.

- [ ] **Step 1: Run backend full test suite against isolated database**

Run:

```powershell
mysql --protocol=tcp -uroot -p12345678 -e "DROP DATABASE IF EXISTS erp_codex_test; CREATE DATABASE erp_codex_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
$env:SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/erp_codex_test?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true'; .\mvnw.cmd -B test
```

Expected: Maven build succeeds with 0 failures and 0 errors.

- [ ] **Step 2: Smoke frontend dev server**

If port `5173` is already listening, request `http://127.0.0.1:5173`. Otherwise start Vite hidden on `5173` and request the same URL.

Expected: HTTP 200.

- [ ] **Step 3: Scan for dashboard mock leftovers**

Run:

```powershell
Select-String -Path 'src\views\dashboard\index.vue' -Pattern 'Math.random|purchaseOrders: 28|salesAmount: 128500|原材料A|PO2024061201'
```

Expected: no results.
