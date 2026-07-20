# Unified Export Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing export buttons for inventory balances, purchase orders, receivables, and payables into real CSV downloads.

**Architecture:** Backend export endpoints return `ResponseEntity<StreamingResponseBody>` with UTF-8 CSV, safe filenames, existing permissions, and existing scoped queries. Frontend API methods request blobs through the existing axios wrapper, and page download code uses one helper instead of reading `response.data` in views.

**Tech Stack:** Spring Boot, MockMvc, MyBatis-Plus, Vue 3, TypeScript, Axios.

---

### Task 1: Backend Export Contracts

**Files:**
- Create/modify tests in `src/test/java/com/tuowei/erp/purchase/PurchaseOrderControllerExportTest.java`
- Create/modify tests in `src/test/java/com/tuowei/erp/finance/receivable/ReceivableControllerExportTest.java`
- Create/modify tests in `src/test/java/com/tuowei/erp/finance/payable/PayableControllerExportTest.java`

- [ ] **Step 1: Write failing controller tests**

Cover:
- unauthorized user gets `403` and service is not called
- authorized user gets `text/csv;charset=UTF-8`
- `Content-Disposition` contains UTF-8 filename
- query params bind into the export query object

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
.\mvnw.cmd -B '-Dtest=PurchaseOrderControllerExportTest,ReceivableControllerExportTest,PayableControllerExportTest' test
```

Expected: tests fail because receivable/payable export endpoints do not exist and purchase export does not use the unified streaming contract.

### Task 2: Backend Streaming Exports

**Files:**
- Modify `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`
- Modify `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify `src/main/java/com/tuowei/erp/finance/receivable/controller/ReceivableController.java`
- Modify `src/main/java/com/tuowei/erp/finance/receivable/service/ReceivableQueryService.java`
- Modify `src/main/java/com/tuowei/erp/finance/payable/controller/PayableController.java`
- Modify `src/main/java/com/tuowei/erp/finance/payable/service/PayableQueryService.java`

- [ ] **Step 1: Implement minimal green path**

Use `StreamingResponseBody` and `CsvExport.write(...)`. Preserve existing query filters and permission codes:
- `purchase:order:view`
- `finance:receivable:view`
- `finance:payable:view`

- [ ] **Step 2: Run focused tests and confirm GREEN**

Run:

```powershell
.\mvnw.cmd -B '-Dtest=PurchaseOrderControllerExportTest,ReceivableControllerExportTest,PayableControllerExportTest' test
```

Expected: tests pass with 0 failures.

### Task 3: Frontend Blob API And Download Helper

**Files:**
- Modify `E:/tuowei/python/erp-frontend/src/api/inventory.ts`
- Modify `E:/tuowei/python/erp-frontend/src/api/purchase.ts`
- Modify `E:/tuowei/python/erp-frontend/src/api/finance.ts`
- Create `E:/tuowei/python/erp-frontend/src/utils/download.ts`
- Modify export handlers in the four affected pages.

- [ ] **Step 1: Replace stub exports**

Each export method returns `Promise<Blob>` by passing `{ responseType: 'blob' }`:

```ts
return request.get<Blob>('/path/export', { params, responseType: 'blob' })
```

- [ ] **Step 2: Add one download helper**

The helper accepts a `Blob` and filename, creates an object URL, clicks an anchor, and revokes the URL.

- [ ] **Step 3: Remove page-level `.data` blob handling**

Affected views should call:

```ts
downloadBlob(blob, 'xxx.csv')
```

### Task 4: Verification

- [ ] **Step 1: Run backend full tests**

```powershell
.\mvnw.cmd -B test
```

- [ ] **Step 2: Run frontend type check and build**

```powershell
npm run type-check
npm run build
```

- [ ] **Step 3: Scan for remaining page-level blob `.data`**

```powershell
rg -n "\.data\b" src/views
```

Expected: no ordinary page-level API unwrap remains; any remaining occurrence must be explicitly justified.
