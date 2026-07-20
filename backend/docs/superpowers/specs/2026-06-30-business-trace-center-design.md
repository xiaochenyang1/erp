# Business Trace Center Design

## Goal

Build a read-only business document trace center that lets users search a business number and see the related order, fulfillment, finance, inventory, workflow, and operation-log context in one place.

## Scope

The first version supports these existing document sources:

- Purchase orders and receipts.
- Sales orders and deliveries.
- Payables and receivables linked by `sourceNo`.
- Inventory transactions linked by `bizNo`.
- Workflow tasks linked by `businessNo`.
- Operation logs linked by `bizNo`.

It does not add new database tables, write operations, exports, or configurable trace rules.

## Backend Design

Add a dedicated report endpoint:

- `GET /api/reports/business-traces?keyword=...`
- Required authority: `report:view`.

The controller delegates to a new `BusinessTraceService`. The service normalizes the keyword, applies current `companyId` and `accountBookId` to every query, and caps each source list to avoid turning one search box into a database bonfire.

Search flow:

1. Query purchase and sales orders whose order number contains the keyword.
2. Query receipts and deliveries whose own number contains the keyword or whose parent order matched.
3. Build a known business-number set from matched documents.
4. Query payables, receivables, inventory transactions, workflow tasks, and operation logs by direct keyword match or known business number.
5. Return matched documents, a chronological timeline, and summary metrics.

## Response Shape

The API returns:

- `keyword`: normalized search text.
- `documents`: matched business documents with type, number, status, date, amount, quantity, partner, and frontend route.
- `timeline`: chronological events from order, fulfillment, finance, inventory, workflow, and logs.
- `summary`: counts and key amounts such as open receivable/payable amount, inventory movement quantity, and failed operation count.
- `generatedAt`: server timestamp.

## Frontend Design

Add a new route:

- `/reports/traces`
- Title: `单据追踪`
- Permission: `report:view`.

The page is a dense operational tool:

- Search bar with keyword input and refresh/reset actions.
- Summary strip for document count, timeline count, open receivable/payable amount, failed operation count.
- Left table: matched documents with type, number, status, date, amount, and route jump.
- Right timeline: cross-module events ordered by time.
- Empty state and loading state.

The UI uses existing Element Plus patterns and avoids dashboard-style decoration.

## Error Handling

- Blank keyword returns an empty trace response instead of raising a server error.
- Backend queries always apply tenant/account-book filters.
- Frontend shows the standard request interceptor error message when the API fails.

## Tests

Backend tests:

- Controller security and query binding.
- Service aggregation across sales, fulfillment, finance, inventory, workflow, and operation logs.
- Tenant/account-book filter presence in representative wrappers.
- Blank keyword returns empty collections.

Frontend verification:

- `npm run type-check`.
- `npm run build`.
