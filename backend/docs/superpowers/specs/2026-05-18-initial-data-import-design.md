# Initial Data Import Design

## Background

The backend already covers the V1 operating loop: system setup, master data, purchase, sales, inventory, light finance, workflow, reporting, and finance ledger queries. What it still lacks is a controlled way to load opening data before production use.

This feature provides a backend-only CSV import center for go-live initialization. It is not a historical migration engine. Trying to fake years of purchase orders, sales orders, and accounting documents through this path would be asking for a data swamp, not an ERP launch.

## Goals

- Provide CSV templates for go-live initialization data.
- Validate uploaded CSV files without writing business tables.
- Store an import job with row snapshots, validation errors, and commit status.
- Commit only validation-passed jobs, in one transaction per job.
- Import product, customer, supplier, warehouse, opening inventory, opening receivables, opening payables, and opening account balances.
- Preserve traceability by writing opening inventory transactions, opening receivable/payable records, and opening finance vouchers/entries instead of directly mutating balances.
- Scope all imported data to the current user's `companyId` and `accountBookId`.

## Non-Goals

- No frontend screens.
- No Excel parsing in the first version.
- No partial success commit.
- No historical purchase orders, sales orders, receipts, deliveries, payments, or receipts migration.
- No automatic account-period closing or opening-period locking.
- No asynchronous background workers in the first version.
- No local file persistence for uploaded CSV content after preview. The parsed row snapshot is stored in database tables.

## Import Types

The first version supports these import types:

- `PRODUCT`
- `CUSTOMER`
- `SUPPLIER`
- `WAREHOUSE`
- `OPENING_INVENTORY`
- `OPENING_RECEIVABLE`
- `OPENING_PAYABLE`
- `OPENING_ACCOUNT_BALANCE`

Every import type uses UTF-8 CSV with a header row. Empty lines are ignored. Header names are strict and case-sensitive to avoid "guess the column" nonsense.

## API

Add a new import center under `com.tuowei.erp.imports`.

- `GET /api/import/templates/{type}`
  - Returns a CSV template as `text/csv`.
  - Requires `import:init:manage`.

- `POST /api/import/jobs/{type}/preview`
  - Accepts `multipart/form-data` with one `file`.
  - Parses and validates the CSV.
  - Creates an `import_job` and `import_job_row` records.
  - Does not write business tables.
  - Requires `import:init:manage`.

- `GET /api/import/jobs/{jobId}`
  - Returns job metadata, row counts, error counts, and row-level validation messages.
  - Requires `import:init:manage`.

- `POST /api/import/jobs/{jobId}/commit`
  - Commits a `VALIDATED` job.
  - Rejects jobs with validation errors, committed jobs, failed jobs, and jobs from another company/account book.
  - Uses one transaction for the job.
  - Requires `import:init:manage`.

## Data Model

Add migration `V35__initial_import_schema.sql`.

### `sys_import_job`

- `id`
- `company_id`
- `account_book_id`
- `import_type`
- `file_name`
- `status`
- `total_rows`
- `valid_rows`
- `error_rows`
- `committed_rows`
- `error_message`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

Statuses:

- `VALIDATED`: preview completed with zero validation errors.
- `INVALID`: preview completed with at least one validation error.
- `COMMITTING`: commit started.
- `COMMITTED`: commit succeeded.
- `FAILED`: commit failed.

### `sys_import_job_row`

- `id`
- `company_id`
- `account_book_id`
- `job_id`
- `row_no`
- `raw_json`
- `normalized_json`
- `valid_flag`
- `error_json`
- `created_time`

`raw_json` stores original cell values keyed by CSV header. `normalized_json` stores parsed values and resolved IDs needed for commit. `error_json` stores a list of `{column, message}` objects.

## CSV Templates

### Product

Headers:

```csv
product_code,product_name,product_type,category_name,specification,unit_name,purchase_price,sale_price,tax_rate,status,remark
```

Rules:

- `product_code`, `product_name`, `unit_name` are required.
- `product_code` must be unique in the current company.
- `product_type` defaults to `STANDARD`.
- `status` defaults to `ACTIVE`.
- Prices and tax rate cannot be negative.

### Customer

Headers:

```csv
customer_code,customer_name,contact_name,contact_phone,settlement_method,credit_limit,address,status,remark
```

Rules:

- `customer_code`, `customer_name` are required.
- `customer_code` must be unique in the current company.
- `credit_limit` defaults to `0`.
- `status` defaults to `ACTIVE`.

### Supplier

Headers:

```csv
supplier_code,supplier_name,contact_name,contact_phone,settlement_method,address,status,remark
```

Rules:

- `supplier_code`, `supplier_name` are required.
- `supplier_code` must be unique in the current company.
- `status` defaults to `ACTIVE`.

### Warehouse

Headers:

```csv
warehouse_code,warehouse_name,dept_id,manager_user_id,address,status,remark
```

Rules:

- `warehouse_code`, `warehouse_name` are required.
- `warehouse_code` must be unique in the current company.
- `dept_id` and `manager_user_id` are optional but must belong to the current company when provided.
- `status` defaults to `ACTIVE`.

### Opening Inventory

Headers:

```csv
warehouse_code,product_code,qty_on_hand,amount_on_hand,opening_date,remark
```

Rules:

- `warehouse_code`, `product_code`, `qty_on_hand`, `amount_on_hand`, `opening_date` are required.
- Warehouse and product must already exist in the current company and be active.
- Quantity must be greater than `0`.
- Amount cannot be negative.
- Duplicate `(warehouse_code, product_code)` rows in the same file are rejected.
- Existing inventory balance for the same warehouse/product with non-zero quantity or amount is rejected.

Commit behavior:

- Calls inventory opening logic that reuses `InventoryPostingService.postInbound`.
- Writes `inv_balance` and `inv_txn`.
- Uses `biz_type=OPENING_BALANCE`.
- Uses job row ID as `biz_line_id`.
- Uses generated source number `OPEN-INV-{jobId}` as `biz_no`.

### Opening Receivable

Headers:

```csv
customer_code,receivable_no,biz_date,original_amount,settled_amount,remark
```

Rules:

- `customer_code`, `biz_date`, `original_amount` are required.
- Customer must already exist in the current company and be active.
- `receivable_no` is optional; when absent the system generates `AR-OPENING-{jobId}-{rowNo}`.
- Original amount must be greater than `0`.
- Settled amount defaults to `0` and cannot exceed original amount.
- `receivable_no` must be unique in the current company when provided.

Commit behavior:

- Inserts `fin_receivable`.
- Uses `source_type=OPENING_RECEIVABLE`.
- Uses job row ID as `source_id`.
- Uses direction `INCREASE`.
- Uses status `UNSETTLED` when remaining amount is greater than `0`, otherwise `SETTLED`.

### Opening Payable

Headers:

```csv
supplier_code,payable_no,biz_date,original_amount,settled_amount,remark
```

Rules:

- `supplier_code`, `biz_date`, `original_amount` are required.
- Supplier must already exist in the current company and be active.
- `payable_no` is optional; when absent the system generates `AP-OPENING-{jobId}-{rowNo}`.
- Original amount must be greater than `0`.
- Settled amount defaults to `0` and cannot exceed original amount.
- `payable_no` must be unique in the current company when provided.

Commit behavior:

- Inserts `fin_payable`.
- Uses `source_type=OPENING_PAYABLE`.
- Uses job row ID as `source_id`.
- Uses direction `INCREASE`.
- Uses status `UNSETTLED` when remaining amount is greater than `0`, otherwise `SETTLED`.

### Opening Account Balance

Headers:

```csv
subject_code,biz_date,debit_amount,credit_amount,summary
```

Rules:

- `subject_code`, `biz_date` are required.
- Subject must already exist in the current company, be active, and be a leaf subject.
- Debit and credit default to `0`.
- Exactly one of debit or credit must be greater than `0`.
- The whole file must be balanced: total debit equals total credit.

Commit behavior:

- Creates one `fin_voucher` for the job.
- Uses `source_type=OPENING_ACCOUNT_BALANCE`.
- Uses job ID as `source_id`.
- Uses generated voucher number `VO-OPENING-{jobId}`.
- Inserts one `fin_voucher_entry` per row.
- Sets voucher status to `POSTED`.

## Opening Commit Guards

Opening imports must run before normal business transactions for the current company/account book. The commit step rejects opening imports when conflicting normal business data already exists:

- `OPENING_INVENTORY` is rejected when `inv_txn` has any non-opening transaction for the current company/account book.
- `OPENING_RECEIVABLE` is rejected when `fin_receivable` has any non-opening source type for the current company/account book.
- `OPENING_PAYABLE` is rejected when `fin_payable` has any non-opening source type for the current company/account book.
- `OPENING_ACCOUNT_BALANCE` is rejected when `fin_voucher` has any non-opening source type for the current company/account book.

Master data imports may still run later, but duplicate-code checks continue to apply.

## Validation Rules

Preview validation performs all checks needed to decide whether commit may run:

- CSV header exactly matches the selected import type.
- Required fields are present and non-blank.
- Number and date fields parse successfully.
- Decimal values are scaled through existing money and quantity precision rules.
- Duplicate business keys in the CSV are rejected.
- Existing business key conflicts in the current company are rejected.
- Referenced master data exists in the current company and is active.
- Cross-company IDs are rejected.

Commit repeats critical conflict checks before writing because another user may have changed data after preview. If any conflict appears during commit, the transaction rolls back and the job becomes `FAILED`.

## Permissions

Add one first-version permission code:

- `import:init:manage`

This permission allows template download, preview, job query, and commit. Keeping it single-purpose is enough for the first version; splitting preview and commit permissions can come later if operations demand it.

## Service Design

Core classes:

- `ImportController`
  - Exposes template, preview, job detail, and commit endpoints.

- `ImportJobService`
  - Owns job lifecycle and transaction boundary.
  - Dispatches import type handling.

- `CsvImportParser`
  - Reads UTF-8 CSV using a small internal parser based on Java standard APIs.
  - Supports quoted cells and escaped quotes.
  - Rejects malformed rows.

- `ImportTemplateRegistry`
  - Defines headers and sample rows for each import type.

- `ImportTypeHandler`
  - Interface for `validate(rowContext)` and `commit(job, rows, audit)`.

- Type handlers:
  - `ProductImportHandler`
  - `CustomerImportHandler`
  - `SupplierImportHandler`
  - `WarehouseImportHandler`
  - `OpeningInventoryImportHandler`
  - `OpeningReceivableImportHandler`
  - `OpeningPayableImportHandler`
  - `OpeningAccountBalanceImportHandler`

The handlers should reuse existing mapper and service patterns. For opening inventory, reuse `InventoryPostingService` instead of directly updating `inv_balance`. For opening account balances, use `VoucherMapper` and `VoucherEntryMapper` directly because there is no general manual voucher service yet.

## Transaction And Idempotency

Preview is not a business write, but it persists job metadata and row snapshots.

Commit is atomic per job:

- Lock the job row.
- Reject if status is not `VALIDATED`.
- Mark `COMMITTING`.
- Run handler commit logic.
- Mark `COMMITTED`.

If commit fails:

- Roll back all business writes.
- Store failure summary on the job in a separate status update.
- Mark the job `FAILED`.

Committed jobs cannot be committed again. This avoids accidental duplicate opening balances, which is the kind of mistake that makes accountants look at developers like we fell out of a tree.

## Error Response Shape

Job detail returns:

- `jobId`
- `importType`
- `status`
- `totalRows`
- `validRows`
- `errorRows`
- `committedRows`
- `rows`

Each row returns:

- `rowNo`
- `valid`
- `raw`
- `normalized`
- `errors`

Each error contains:

- `column`
- `message`

## Verification Plan

Current verification uses the restored minimal automated regression set, build packaging, and manual API checks:

- Run `.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package`.
- Confirm Maven reports the minimal test suite passing and `BUILD SUCCESS`.
- Manually preview invalid CSV for each import type and confirm row-level errors.
- Manually preview valid CSV for each import type and confirm `VALIDATED`.
- Commit valid jobs and verify business tables:
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
- Retry committed job and confirm it is rejected.
- Try cross-company references and confirm they are rejected.

## Rollout Notes

- The import center should be enabled before production go-live and restricted to trusted initialization operators.
- CSV files should be archived outside this backend if the business needs original-file retention.
- Opening imports should be completed before normal business transactions begin. If normal transactions already exist, opening inventory and opening finance imports should be blocked or require a separate controlled data correction process.
