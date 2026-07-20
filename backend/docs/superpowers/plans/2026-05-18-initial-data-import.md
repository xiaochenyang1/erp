# Initial Data Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend-only CSV import center for production go-live initialization data: master data, opening inventory, opening receivables, opening payables, and opening account balances.

**Architecture:** Add a focused `com.tuowei.erp.imports` module with a shared job lifecycle, CSV parser, template registry, row validators, and per-type commit handlers. Preview persists parsed row snapshots and validation errors without touching business tables; commit locks a validated job and writes the whole batch in one transaction, reusing existing inventory and finance persistence patterns where possible.

**Tech Stack:** Spring Boot 3.5.x, Spring MVC multipart upload, Spring Security, MyBatis-Plus, Flyway, MySQL, Java 17

---

## File Map

**Create:**

- `src/main/resources/db/migration/V35__initial_import_schema.sql`
- `src/main/java/com/tuowei/erp/imports/controller/ImportController.java`
- `src/main/java/com/tuowei/erp/imports/mapper/ImportJobMapper.java`
- `src/main/java/com/tuowei/erp/imports/mapper/ImportJobRowMapper.java`
- `src/main/java/com/tuowei/erp/imports/model/ImportJobEntity.java`
- `src/main/java/com/tuowei/erp/imports/model/ImportJobRowEntity.java`
- `src/main/java/com/tuowei/erp/imports/service/CsvImportParser.java`
- `src/main/java/com/tuowei/erp/imports/service/ImportConstants.java`
- `src/main/java/com/tuowei/erp/imports/service/ImportJobService.java`
- `src/main/java/com/tuowei/erp/imports/service/ImportTemplateRegistry.java`
- `src/main/java/com/tuowei/erp/imports/service/ImportTypeHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/ImportValidationSupport.java`
- `src/main/java/com/tuowei/erp/imports/service/ProductImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/CustomerImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/SupplierImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/WarehouseImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/OpeningInventoryImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/OpeningReceivableImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/OpeningPayableImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/service/OpeningAccountBalanceImportHandler.java`
- `src/main/java/com/tuowei/erp/imports/web/ImportJobResponse.java`
- `src/main/java/com/tuowei/erp/imports/web/ImportRowErrorResponse.java`
- `src/main/java/com/tuowei/erp/imports/web/ImportRowResponse.java`

**Modify:**

- `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`

**Reference:**

- `docs/superpowers/specs/2026-05-18-initial-data-import-design.md`
- `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java`
- `src/main/java/com/tuowei/erp/common/math/ScalePrecision.java`

---

### Task 1: Schema, Tenant Registration, And Permission

**Files:**

- Create: `src/main/resources/db/migration/V35__initial_import_schema.sql`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`

- [x] **Step 1: Add import job tables and menu seed**

Create `V35__initial_import_schema.sql` with these tables:

```sql
CREATE TABLE IF NOT EXISTS sys_import_job (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    import_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    committed_rows INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_import_job_row (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    job_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    raw_json TEXT NOT NULL,
    normalized_json TEXT NOT NULL,
    valid_flag TINYINT NOT NULL DEFAULT 0,
    error_json TEXT NOT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_import_job_company_type_status
    ON sys_import_job (company_id, import_type, status, created_time);
CREATE INDEX idx_sys_import_job_company_created
    ON sys_import_job (company_id, created_time);
CREATE UNIQUE INDEX uk_sys_import_job_row_job_row
    ON sys_import_job_row (job_id, row_no);
CREATE INDEX idx_sys_import_job_row_company_job
    ON sys_import_job_row (company_id, job_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5070, 0, 'CATALOG', 'IMPORT_CENTER', '导入中心', '/imports', 'Layout', NULL, 11, 1, 'ACTIVE', 0, 0, 0, 0),
    (5071, 5070, 'MENU', 'INITIAL_IMPORT', '期初数据导入', '/imports/initial',
     'imports/initial/index', 'import:init:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7090, 3002, 5070, 0),
    (7091, 3002, 5071, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

- [x] **Step 2: Register tenant tables**

Add `sys_import_job` and `sys_import_job_row` to the `TENANT_TABLES` set in `MybatisPlusConfig`.

- [x] **Step 3: Add permission constants**

Add to `PermissionCodes`:

```java
public static final String IMPORT_INIT_MANAGE = "import:init:manage";
public static final String HAS_IMPORT_INIT_MANAGE = "hasAuthority('" + IMPORT_INIT_MANAGE + "')";
```

Place the raw permission near report/workflow permissions and the `HAS_` constant near other `HAS_` declarations.

### Task 2: Import Models, Mappers, And Response DTOs

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/model/ImportJobEntity.java`
- Create: `src/main/java/com/tuowei/erp/imports/model/ImportJobRowEntity.java`
- Create: `src/main/java/com/tuowei/erp/imports/mapper/ImportJobMapper.java`
- Create: `src/main/java/com/tuowei/erp/imports/mapper/ImportJobRowMapper.java`
- Create: `src/main/java/com/tuowei/erp/imports/web/ImportJobResponse.java`
- Create: `src/main/java/com/tuowei/erp/imports/web/ImportRowResponse.java`
- Create: `src/main/java/com/tuowei/erp/imports/web/ImportRowErrorResponse.java`

- [x] **Step 1: Create entity classes**

`ImportJobEntity` maps `sys_import_job` and contains `id`, `companyId`, `accountBookId`, `importType`, `fileName`, `status`, row count fields, `errorMessage`, audit fields, and `@Version Integer version`.

`ImportJobRowEntity` maps `sys_import_job_row` and contains `id`, `companyId`, `accountBookId`, `jobId`, `rowNo`, `rawJson`, `normalizedJson`, `validFlag`, `errorJson`, and `createdTime`.

- [x] **Step 2: Create mapper interfaces**

Create standard MyBatis-Plus mappers:

```java
@Mapper
public interface ImportJobMapper extends BaseMapper<ImportJobEntity> {
}
```

```java
@Mapper
public interface ImportJobRowMapper extends BaseMapper<ImportJobRowEntity> {
}
```

- [x] **Step 3: Create response records**

Create:

```java
public record ImportRowErrorResponse(String column, String message) {
}
```

```java
public record ImportRowResponse(
        Integer rowNo,
        boolean valid,
        Map<String, String> raw,
        Map<String, Object> normalized,
        List<ImportRowErrorResponse> errors
) {
}
```

```java
public record ImportJobResponse(
        Long jobId,
        String importType,
        String fileName,
        String status,
        Integer totalRows,
        Integer validRows,
        Integer errorRows,
        Integer committedRows,
        String errorMessage,
        List<ImportRowResponse> rows
) {
}
```

### Task 3: Shared CSV Parser, Templates, And Validation Support

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/service/ImportConstants.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/CsvImportParser.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/ImportTemplateRegistry.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/ImportTypeHandler.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/ImportValidationSupport.java`

- [x] **Step 1: Create constants**

`ImportConstants` contains:

```java
public static final String PRODUCT = "PRODUCT";
public static final String CUSTOMER = "CUSTOMER";
public static final String SUPPLIER = "SUPPLIER";
public static final String WAREHOUSE = "WAREHOUSE";
public static final String OPENING_INVENTORY = "OPENING_INVENTORY";
public static final String OPENING_RECEIVABLE = "OPENING_RECEIVABLE";
public static final String OPENING_PAYABLE = "OPENING_PAYABLE";
public static final String OPENING_ACCOUNT_BALANCE = "OPENING_ACCOUNT_BALANCE";

public static final String VALIDATED = "VALIDATED";
public static final String INVALID = "INVALID";
public static final String COMMITTING = "COMMITTING";
public static final String COMMITTED = "COMMITTED";
public static final String FAILED = "FAILED";
```

- [x] **Step 2: Implement `CsvImportParser`**

Implement a UTF-8 parser with:

```java
public ParsedCsv parse(MultipartFile file, List<String> expectedHeaders)
```

Types:

```java
public record ParsedCsv(List<String> headers, List<ParsedCsvRow> rows) {
}

public record ParsedCsvRow(int rowNo, Map<String, String> values) {
}
```

Behavior:

- Reject blank file.
- Parse quoted cells, escaped quotes, commas inside quotes, CRLF and LF.
- Ignore empty trailing lines.
- Require the header list to exactly equal the expected headers.
- Throw `IllegalArgumentException` with a clear Chinese message on malformed CSV or header mismatch.

- [x] **Step 3: Implement template registry**

`ImportTemplateRegistry` exposes:

```java
public List<String> headers(String importType)
public String csvTemplate(String importType)
public Set<String> supportedTypes()
```

Use exact headers from the design spec for all eight import types. `csvTemplate` returns header row plus one sample row for each type.

- [x] **Step 4: Define handler interface**

`ImportTypeHandler`:

```java
String importType();
ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context);
int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit);
```

Records:

```java
public record ImportValidationContext(Long companyId, Long accountBookId, Long userId) {
}

public record ImportRowPlan(Map<String, Object> normalized, List<ImportRowErrorResponse> errors) {
    public boolean valid() {
        return errors == null || errors.isEmpty();
    }
}
```

- [x] **Step 5: Implement validation support**

`ImportValidationSupport` provides helpers:

- `String required(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors)`
- `BigDecimal amount(...)`
- `BigDecimal quantity(...)`
- `LocalDate date(...)`
- `Long optionalLong(...)`
- `void duplicateInFile(Set<String> seen, String key, String column, List<ImportRowErrorResponse> errors)`
- JSON helpers using `ObjectMapper`:
  - `String toJson(Object value)`
  - `Map<String, String> rawFromJson(String json)`
  - `Map<String, Object> normalizedFromJson(String json)`
  - `List<ImportRowErrorResponse> errorsFromJson(String json)`

Use existing `ScalePrecision.amount`, `ScalePrecision.quantity`, and `ScalePrecision.zeroDefault`.

### Task 4: Master Data Import Handlers

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/service/ProductImportHandler.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/CustomerImportHandler.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/SupplierImportHandler.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/WarehouseImportHandler.java`

- [x] **Step 1: Implement product handler**

Validate required `product_code`, `product_name`, `unit_name`; reject duplicate code in the file and existing `md_product` code in current company; default `product_type=STANDARD`, `status=ACTIVE`; reject negative price/tax fields.

Commit inserts `ProductEntity` with:

- `companyId/accountBookId` from audit
- `deletedFlag=0`
- audit fields from `AuditMetadata`
- `version=0`

- [x] **Step 2: Implement customer handler**

Validate required `customer_code`, `customer_name`; reject duplicate code in file and existing current-company code; default `credit_limit=0`, `status=ACTIVE`; reject negative credit limit.

Commit inserts `CustomerEntity` with audit fields and `version=0`.

- [x] **Step 3: Implement supplier handler**

Validate required `supplier_code`, `supplier_name`; reject duplicate code in file and existing current-company code; default `status=ACTIVE`.

Commit inserts `SupplierEntity` with audit fields and `version=0`.

- [x] **Step 4: Implement warehouse handler**

Validate required `warehouse_code`, `warehouse_name`; reject duplicate code in file and existing current-company code; validate optional `dept_id` and `manager_user_id` belong to current company; default `status=ACTIVE`.

Commit inserts `WarehouseEntity` with audit fields and `version=0`.

### Task 5: Opening Inventory Import Handler

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/service/OpeningInventoryImportHandler.java`

- [x] **Step 1: Validate references and row values**

Validate `warehouse_code`, `product_code`, `qty_on_hand`, `amount_on_hand`, `opening_date`.

Rules:

- Warehouse and product must exist in current company, `deletedFlag=0`, `status=ACTIVE`.
- `qty_on_hand > 0`.
- `amount_on_hand >= 0`.
- No duplicate `(warehouse_code, product_code)` in file.
- Existing `inv_balance` for the same company/warehouse/product with non-zero `qtyOnHand` or `amountOnHand` is rejected.

Normalize to `warehouseId`, `productId`, `qtyOnHand`, `amountOnHand`, `openingDate`, and `remark`.

- [x] **Step 2: Add opening commit guard**

Before commit, reject when current company/account book has any `inv_txn` row where `biz_type <> 'OPENING_BALANCE'`.

- [x] **Step 3: Commit through inventory service**

For each row, call:

```java
inventoryPostingService.postInbound(
    new InventoryPostingCommand(
        warehouseId,
        productId,
        "OPENING_BALANCE",
        "OPEN-INV-" + job.getId(),
        row.getId(),
        qtyOnHand,
        amountOnHand,
        remark
    ),
    audit
);
```

Do not update `inv_balance` directly.

### Task 6: Opening Receivable And Payable Import Handlers

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/service/OpeningReceivableImportHandler.java`
- Create: `src/main/java/com/tuowei/erp/imports/service/OpeningPayableImportHandler.java`

- [x] **Step 1: Implement opening receivable validation**

Validate `customer_code`, `biz_date`, `original_amount`, optional `receivable_no`, optional `settled_amount`, optional `remark`.

Rules:

- Customer exists in current company, `deletedFlag=0`, `status=ACTIVE`.
- `original_amount > 0`.
- `settled_amount >= 0`.
- `settled_amount <= original_amount`.
- Provided `receivable_no` is unique in current company and unique in file.
- Missing `receivable_no` normalizes to `AR-OPENING-{jobId}-{rowNo}` at commit time, with preview normalized value `null`.

- [x] **Step 2: Commit opening receivables**

Before commit, reject when current company/account book has any `fin_receivable` row where `source_type <> 'OPENING_RECEIVABLE'`.

Insert `ReceivableEntity`:

- `receivableNo`: provided value or `AR-OPENING-{jobId}-{rowNo}`
- `sourceType=OPENING_RECEIVABLE`
- `sourceId=row.id`
- `sourceNo=receivableNo`
- `direction=INCREASE`
- `customerId`
- `bizDate`
- `originalAmount`
- `settledAmount`
- `status=SETTLED` when settled equals original, otherwise `UNSETTLED`
- audit fields and `version=0`

- [x] **Step 3: Implement opening payable validation**

Validate `supplier_code`, `biz_date`, `original_amount`, optional `payable_no`, optional `settled_amount`, optional `remark`.

Rules mirror receivable validation against `md_supplier` and `fin_payable`.

- [x] **Step 4: Commit opening payables**

Before commit, reject when current company/account book has any `fin_payable` row where `source_type <> 'OPENING_PAYABLE'`.

Insert `PayableEntity`:

- `payableNo`: provided value or `AP-OPENING-{jobId}-{rowNo}`
- `sourceType=OPENING_PAYABLE`
- `sourceId=row.id`
- `sourceNo=payableNo`
- `direction=INCREASE`
- `supplierId`
- `bizDate`
- `originalAmount`
- `settledAmount`
- `status=SETTLED` when settled equals original, otherwise `UNSETTLED`
- audit fields and `version=0`

### Task 7: Opening Account Balance Import Handler

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/service/OpeningAccountBalanceImportHandler.java`

- [x] **Step 1: Validate account balance rows**

Validate `subject_code`, `biz_date`, optional `debit_amount`, optional `credit_amount`, optional `summary`.

Rules:

- Subject exists in current company, `deletedFlag=0`, `status=ACTIVE`.
- Subject must be leaf: no active child subject exists in same company.
- Debit and credit default to `0`.
- Exactly one of debit/credit is greater than `0`.
- Whole file debit total equals credit total.

Normalize to `subjectId`, `subjectCode`, `subjectName`, `bizDate`, `debitAmount`, `creditAmount`, `summary`.

- [x] **Step 2: Add opening voucher guard**

Before commit, reject when current company/account book has any `fin_voucher` row where `source_type <> 'OPENING_ACCOUNT_BALANCE'`.

- [x] **Step 3: Commit opening voucher and entries**

Create one `VoucherEntity`:

- `voucherNo=VO-OPENING-{jobId}`
- `sourceType=OPENING_ACCOUNT_BALANCE`
- `sourceId=job.id`
- `sourceNo=VO-OPENING-{jobId}`
- `bizDate`: the minimum row `bizDate`
- `amount`: debit total
- `status=POSTED`
- audit fields and `version=0`

Insert one `VoucherEntryEntity` per row:

- `voucherId` from created voucher
- `lineNo` starts at `1`
- `bizDate` from each row
- subject fields from normalized row
- debit/credit values from normalized row
- summary from row or `期初科目余额`
- audit fields and `version=0`

### Task 8: Job Service And Controller

**Files:**

- Create: `src/main/java/com/tuowei/erp/imports/service/ImportJobService.java`
- Create: `src/main/java/com/tuowei/erp/imports/controller/ImportController.java`

- [x] **Step 1: Wire handler registry**

In `ImportJobService`, inject `List<ImportTypeHandler>` and build a `Map<String, ImportTypeHandler>` by `importType()`. Reject unsupported import types with `IllegalArgumentException("不支持的导入类型: " + type)`.

- [x] **Step 2: Implement template download**

`ImportJobService.template(type)` returns CSV bytes and filename. Controller endpoint:

```java
@PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
@GetMapping(value = "/templates/{type}", produces = "text/csv")
public ResponseEntity<byte[]> template(@PathVariable String type)
```

Use `Content-Disposition: attachment; filename="{type}.csv"`.

- [x] **Step 3: Implement preview**

`preview(type, MultipartFile file)`:

- Load current user and audit metadata.
- Resolve handler and expected headers.
- Parse CSV.
- Create `ImportJobEntity` with `VALIDATED` or `INVALID` after row validation.
- Insert all `ImportJobRowEntity` rows with raw/normalized/error JSON.
- Return `ImportJobResponse`.

Controller:

```java
@PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
@PostMapping(value = "/jobs/{type}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<ImportJobResponse> preview(@PathVariable String type, @RequestPart("file") MultipartFile file)
```

- [x] **Step 4: Implement job detail**

`detail(jobId)` verifies current company/account book and returns job with rows ordered by `rowNo`.

Controller:

```java
@PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
@GetMapping("/jobs/{jobId}")
public ApiResponse<ImportJobResponse> detail(@PathVariable Long jobId)
```

- [x] **Step 5: Implement commit**

`commit(jobId)`:

- Load job by ID and current company.
- Reject if account book differs.
- Reject if `status != VALIDATED`.
- Set `COMMITTING`.
- Load valid rows ordered by `rowNo`.
- Dispatch handler commit.
- Set `COMMITTED` with committed row count.
- On runtime failure, update job to `FAILED` with failure message in a separate transaction and rethrow a clear `BusinessConflictException`.

Controller:

```java
@PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
@PostMapping("/jobs/{jobId}/commit")
public ApiResponse<ImportJobResponse> commit(@PathVariable Long jobId)
```

### Task 9: Build Verification And Manual API Checklist

**Files:**

- No production source changes beyond previous tasks.

- [x] **Step 1: Build package**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package
```

Expected:

- `BUILD SUCCESS`
- Restored minimal test suite runs and passes.
- `target/erp-server-1.0.0.jar` exists.

- [ ] **Step 2: Manual API validation checklist**

Use a dev or pre-production database with valid login token:

- Download each template:
  - `GET /api/import/templates/PRODUCT`
  - `GET /api/import/templates/CUSTOMER`
  - `GET /api/import/templates/SUPPLIER`
  - `GET /api/import/templates/WAREHOUSE`
  - `GET /api/import/templates/OPENING_INVENTORY`
  - `GET /api/import/templates/OPENING_RECEIVABLE`
  - `GET /api/import/templates/OPENING_PAYABLE`
  - `GET /api/import/templates/OPENING_ACCOUNT_BALANCE`
- Upload invalid CSV for each type and confirm `INVALID` with row/column messages.
- Upload valid master data CSVs and commit them.
- Upload valid opening CSVs and commit them.
- Query tables to confirm business writes:
  - `md_product`
  - `md_customer`
  - `md_supplier`
  - `md_warehouse`
  - `inv_balance`
  - `inv_txn`
  - `fin_receivable`
  - `fin_payable`
  - `fin_voucher`
  - `fin_voucher_entry`
- Commit the same job again and confirm rejection.
- Insert a normal non-opening business row and confirm the matching opening commit guard rejects late opening import.

---

## Self-Review

**Spec coverage:** Covers the eight import types, strict CSV templates, preview/commit split, job persistence, row errors, opening inventory transactions, opening receivable/payable rows, opening account balance voucher entries, tenant scoping, permission, and commit guards.

**Placeholder scan:** This plan contains no `TODO`, `TBD`, "fill in details", or open-ended implementation placeholders.

**Type consistency:** Import type strings, status strings, source type strings, endpoint paths, table names, and response field names match the design spec.
