# Web Barcode Scanning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist tenant-scoped product barcodes and support camera or keyboard-scanner quantity entry in purchase receipts and sales deliveries.

**Architecture:** A Flyway migration and the existing product service own barcode identity and exact lookup. A focused Vue component owns browser camera/scanner mechanics, while a pure utility owns order-line quantity mutation so both workflows share deterministic rules.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Flyway, Vue 3, TypeScript, Element Plus, Vitest, native `BarcodeDetector`.

---

**Status:** DONE（2026-07-21）。当前完成证据：

- `.\mvnw.cmd -B "-Dmaven.repo.local=.m2" "-Dtest=FlywayMigrationSmokeTest,ProductBarcodeServiceTest,ProductBarcodeControllerTest,MasterdataServiceExportTest,BusinessSmokeScriptConfigurationTest" test` -> 39 tests, 0 failures/errors.
- `.\mvnw.cmd -B "-Dmaven.repo.local=.m2" test` -> 1000 tests, 0 failures/errors.
- `npm run check:contracts` -> pass；`npm test` -> 10 files / 37 tests passed；`npm run type-check`、`npm run lint`、`npm run build` 均 exit 0。
- `.\mvnw.cmd -B "-Dmaven.repo.local=.m2" "-DskipTests" package` -> `BUILD SUCCESS`，刷新 `target/erp-server-1.0.0.jar` 后运行 UI smoke。
- `UI_SMOKE_ROUTES=0 UI_SMOKE_WORKFLOW=purchase-receipt-draft-edit,sales-delivery-draft-edit node scripts\ui-smoke.mjs` -> 采购收货草稿编辑、销售发货草稿编辑均 `passed=true`，扫码后 `lineQty=1`。

Execution note: 下面的 checkbox 是原始 TDD 执行计划，保留作设计与实现路线记录；当前完成状态以本节证据和执行板为准。按用户当前要求，本功能以一个 P2-7 功能 commit/push 收口。

### Task 1: Barcode Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V119__product_barcode.sql`
- Modify: `backend/src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

- [ ] Add a migration smoke test that asserts the `md_product.barcode` column exists, verifies duplicate `(company_id, account_book_id, barcode)` values fail, and verifies the same barcode in a different account book succeeds.
- [ ] Run `./mvnw.cmd -B -Dtest=FlywayMigrationSmokeTest test` and confirm the new assertion fails because `barcode` does not exist.
- [ ] Add the migration:

```sql
ALTER TABLE md_product
    ADD COLUMN barcode VARCHAR(128) NULL AFTER product_name;

ALTER TABLE md_product
    ADD UNIQUE KEY uk_md_product_company_book_barcode
        (company_id, account_book_id, barcode);
```

- [ ] Re-run the focused migration test and confirm it passes.

### Task 2: Product Barcode Domain Contract

**Files:**
- Create: `backend/src/test/java/com/tuowei/erp/masterdata/product/ProductBarcodeServiceTest.java`
- Modify: `backend/src/main/java/com/tuowei/erp/masterdata/product/model/ProductEntity.java`
- Modify: `backend/src/main/java/com/tuowei/erp/masterdata/product/web/ProductCreateRequest.java`
- Modify: `backend/src/main/java/com/tuowei/erp/masterdata/product/web/ProductUpdateRequest.java`
- Modify: `backend/src/main/java/com/tuowei/erp/masterdata/product/web/ProductResponse.java`
- Modify: `backend/src/main/java/com/tuowei/erp/masterdata/product/service/ProductService.java`

- [ ] Add focused service tests proving create trims a barcode, blank becomes `null`, lookup is scoped by company/account book/deleted/status, duplicate create/update is rejected, and response serialization returns the normalized value.
- [ ] Run `./mvnw.cmd -B -Dtest=ProductBarcodeServiceTest test` and confirm compilation or assertions fail because the contract is absent.
- [ ] Add `barcode` to the entity and records, then normalize with:

```java
private String normalizeBarcode(String barcode) {
    String normalized = normalizeNullableText(barcode);
    if (normalized != null && normalized.length() > 128) {
        throw new IllegalArgumentException("商品条码长度不能超过128个字符");
    }
    return normalized;
}
```

- [ ] Add `getByBarcode(String barcode)` using a `LambdaQueryWrapper` constrained by `companyId`, `accountBookId`, `deletedFlag = 0`, `status = ACTIVE`, and exact barcode.
- [ ] Add friendly duplicate validation before create/update while retaining the unique key as the race backstop.
- [ ] Include barcode in list keyword search, CSV headers/rows, entity writes, and `ProductResponse`.
- [ ] Re-run `ProductBarcodeServiceTest` and the existing masterdata tenant/export tests.

### Task 3: Exact Lookup HTTP API

**Files:**
- Create: `backend/src/test/java/com/tuowei/erp/masterdata/product/ProductBarcodeControllerTest.java`
- Modify: `backend/src/main/java/com/tuowei/erp/masterdata/product/controller/ProductController.java`

- [ ] Add MockMvc tests proving `GET /api/masterdata/products/by-barcode?barcode=6901234567890` requires `masterdata:product:view`, passes the exact value to the service, and returns a string Long id plus barcode.
- [ ] Run `./mvnw.cmd -B -Dtest=ProductBarcodeControllerTest test` and confirm the endpoint returns 404.
- [ ] Add the literal mapping before the id mapping:

```java
@PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_VIEW)
@GetMapping("/by-barcode")
public ApiResponse<ProductResponse> byBarcode(@RequestParam String barcode) {
    return ApiResponse.success(productService.getByBarcode(barcode));
}
```

- [ ] Re-run controller and masterdata tests.

### Task 4: Shared Quantity Rules and Frontend API

**Files:**
- Create: `frontend/src/utils/barcode.test.ts`
- Create: `frontend/src/utils/barcode.ts`
- Modify: `frontend/src/api/masterdata.ts`
- Modify: `frontend/src/api/p2-extensions.test.ts`

- [ ] Add tests for `incrementScannedLine`: it increments the matching product by one, rejects an unrelated product, rejects a line at its maximum, and compares string/number product ids without precision-damaging numeric conversion.
- [ ] Add an API test proving `getProductByBarcode(' 6901 ')` sends trimmed `barcode=6901` to `/masterdata/products/by-barcode` and normalizes the returned id to string.
- [ ] Run the two test files and confirm they fail because the utility and API function do not exist.
- [ ] Implement this result contract:

```ts
export type ScanIncrementResult =
  | { status: 'incremented'; index: number; quantity: number }
  | { status: 'not-found' }
  | { status: 'at-maximum'; index: number; quantity: number }
```

- [ ] Implement `getProductByBarcode` with trimmed, non-empty input and the existing `normalizeProduct` path.
- [ ] Re-run the focused frontend tests and type check.

### Task 5: Reusable Camera and Scanner Field

**Files:**
- Create: `frontend/src/components/common/BarcodeScanField.vue`
- Create: `frontend/src/components/common/BarcodeScanField.test.ts`
- Modify: `frontend/src/components/common/index.ts`

- [ ] Add component tests proving Enter emits one trimmed `scan`, blank input does not emit, unsupported camera state is visible without disabling the input, and unmount stops all active media tracks.
- [ ] Run `npm test -- BarcodeScanField.test.ts` and confirm the component import fails.
- [ ] Read and apply the `frontend-design` and `make-interfaces-feel-better` skills before creating the component.
- [ ] Implement a compact Element Plus input with a camera icon button, tooltip, fixed control dimensions, camera dialog, `<video playsinline>`, native detector loop, deterministic track cleanup, and `scan`, `camera-error`, and `camera-state` events.
- [ ] Re-run the component test, lint, and type check.

### Task 6: Purchase Receipt and Sales Delivery Integration

**Files:**
- Modify: `frontend/src/views/purchase/receipts/index.vue`
- Modify: `frontend/src/views/sales/deliveries/index.vue`
- Modify: `frontend/scripts/check-production-order-warehouse-contract.mjs`

- [ ] Extend contract checks to require `BarcodeScanField`, `getProductByBarcode`, `incrementScannedLine`, scan-count reset confirmation, and scan handlers in both pages; run `npm run check:contracts` and confirm it fails on the missing fragments.
- [ ] Add a scan toolbar above each order-derived line table. The reset command confirms before setting all current quantities to zero; a successful scan uses exact product lookup and the shared increment utility.
- [ ] Map `not-found`, `at-maximum`, backend lookup errors, and success to concise Element Plus messages. Keep quantity limits aligned with ordered/received or ordered/delivered values.
- [ ] Re-run contract checks, unit tests, lint, type check, and build.

### Task 7: End-to-End Verification and Documentation

**Files:**
- Modify: `backend/docs/未完成.md`
- Modify: `backend/docs/WHAT_IS_MISSING.md`

- [ ] Start the existing local backend and frontend servers on free ports and use browser debugging to verify product barcode persistence, scanner-input Enter behavior, quantity increment, unrelated-product warning, max enforcement, and camera unsupported/permission-denied fallback.
- [ ] Run backend focused tests, then `./mvnw.cmd -B test` and require zero failures/errors.
- [ ] Run `npm run check:contracts`, `npm run lint`, `npm run type-check`, `npm test`, and `npm run build` and require all commands to exit zero.
- [ ] Update the execution board to mark `P2-7` done only after the runtime and full-gate evidence exists.
