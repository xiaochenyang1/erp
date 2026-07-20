# Operations Dashboard Design

## Goal

Replace the static dashboard mock data with a real operations dashboard that shows the current user's actionable work and business exceptions.

## Scope

The first version is read-only. It helps users see issues and navigate to the owning module, but it does not approve, post, cancel, settle, or mutate business documents from the dashboard.

Included:

- Summary metrics for pending approvals, low-stock alerts, open receivables, open payables, today's purchase orders, and today's sales amount.
- A unified todo list with workflow tasks, low-stock items, overdue receivables, overdue payables, and recent failed operations.
- A low-stock preview list.
- A recent failed operation list.
- Dashboard frontend wiring that replaces hard-coded mock values.

Excluded:

- Direct approval or posting actions from the dashboard.
- User-configurable widgets.
- Cross-company or cross-account-book aggregation.
- New persistence tables.

## Backend Design

Add a new `dashboard` package with a controller, service, and response records.

Endpoint:

- `GET /api/dashboard/operations`
- Permission: any authenticated ERP user can load it. The service scopes all data by the current user's company and account book.

The service reads existing tables through existing mappers:

- Workflow pending tasks from `wf_approval_task`.
- Low stock through `InventoryAlertService.listLowStock`.
- Open receivables from `fin_receivable`.
- Open payables from `fin_payable`.
- Today's purchase orders from `pur_order`.
- Today's sales amount from `sal_order`.
- Recent failed operation logs from `sys_operation_log`.

Open settlement records are statuses other than `SETTLED`, `CANCELLED`, or `CLOSED`. Overdue records use `bizDate < today`.

## Response Shape

`OperationsDashboardResponse` contains:

- `summary`: count and amount metrics.
- `todos`: up to 12 ordered action items.
- `lowStock`: up to 5 low-stock previews.
- `failedOperations`: up to 5 failed operation previews.
- `generatedAt`: server timestamp.

Todo items contain:

- stable `id`
- `type`
- `title`
- `description`
- `priority`: `HIGH`, `MEDIUM`, or `LOW`
- `route`
- `occurredAt`

## Frontend Design

Add `src/api/dashboard.ts` with the response types and `getOperationsDashboard()`.

Update `src/views/dashboard/index.vue`:

- Load dashboard data on mount.
- Replace static stats, todos, low-stock alerts, and chart counters with API data.
- Keep quick actions and charts, but remove randomized chart data.
- Route todo clicks to the module route supplied by the backend.
- Show empty and error states without breaking the page.

## Error Handling

Backend returns an empty dashboard instead of failing the entire response when one optional source is empty.

Frontend shows an error message if the dashboard request fails and keeps empty defaults visible.

## Testing

Backend:

- Controller test verifies authenticated access and response binding.
- Service test verifies tenant scoping, metric calculations, todo ordering, overdue detection, and failed operation filtering.

Frontend:

- `vue-tsc` verifies API and component type safety.
- `vite build` verifies production compilation.

## Assumptions

- The current authenticated user always has a company id and account book id.
- Workflow todo items are user-specific via `approverUserId`.
- Finance overdue items use `bizDate` because no dedicated due-date column exists yet.
- The dashboard is an operational entry point, not a reporting ledger.
