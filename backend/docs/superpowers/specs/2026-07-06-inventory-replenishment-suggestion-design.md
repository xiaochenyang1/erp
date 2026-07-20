# Inventory Replenishment Suggestion Design

## Goal

Turn actionable low-stock alerts into replenishment suggestions that purchasing users can review before creating purchase orders.

The current inventory alert page can create alert rules, show derived low-stock hits, ignore hits, and mark hits as resolved. That is not enough for a real workflow: marking an alert as handled does not explain how stock will be replenished. This feature adds a controlled step between an alert and a purchase order.

## Scope

The first version supports a manual, user-confirmed replenishment workflow from active low-stock alerts.

Included:

- Create a replenishment suggestion from a low-stock alert.
- Default suggested quantity from the current shortage quantity.
- Optional supplier, expected arrival date, and remark.
- Query suggestions by status, warehouse, product, supplier, source alert, and date range.
- Cancel a draft suggestion.
- Convert a draft suggestion into a purchase order.
- Mark the source low-stock alert as resolved with a remark that references the suggestion.
- Show the suggestion on the inventory alert page and in a dedicated suggestion list page.

Excluded:

- Automatic purchase order creation without user review.
- MRP, sales forecast, safety-stock calculation, or lead-time planning.
- Multi-supplier price comparison.
- Purchase approval changes.
- Automatic supplier selection.
- Batch conversion of many suggestions at once.

## Domain Model

Add a replenishment suggestion table:

- `inv_replenishment_suggestion`

Fields:

- `id`
- `company_id`
- `account_book_id`
- `suggestion_no`
- `source_type`: first version uses `LOW_STOCK_ALERT`
- `source_rule_id`
- `warehouse_id`
- `product_id`
- `supplier_id`
- `suggested_qty`
- `shortage_qty_snapshot`
- `expected_arrival_date`
- `status`: `DRAFT`, `CONVERTED`, `CANCELLED`
- `purchase_order_id`
- `purchase_order_no`
- `remark`
- audit fields and optimistic-lock `version`

Uniqueness:

- Only one active `DRAFT` suggestion is allowed per tenant, warehouse, product, and source type.
- Converted and cancelled suggestions stay for traceability.

## State Model

Allowed states:

- `DRAFT`
- `CONVERTED`
- `CANCELLED`

Allowed transitions:

- `DRAFT -> CONVERTED`
- `DRAFT -> CANCELLED`

Converted and cancelled suggestions are immutable in the first version.

Creating a suggestion from an active alert also writes or updates the existing inventory alert disposition as `RESOLVED`. If the shortage later becomes worse than the disposition snapshot, the existing alert service may expose the alert as active again. That behavior remains unchanged.

## Backend Design

Add a new package under inventory:

- `com.tuowei.erp.inventory.replenishment`

Add endpoints:

- `GET /api/inventory/replenishment-suggestions`
- `POST /api/inventory/replenishment-suggestions`
- `POST /api/inventory/replenishment-suggestions/{id}/cancel`
- `POST /api/inventory/replenishment-suggestions/{id}/convert-to-purchase-order`

Create request:

- `warehouseId`
- `productId`
- `ruleId`
- `supplierId`
- `suggestedQty`
- `expectedArrivalDate`
- `remark`

Conversion behavior:

- Validate the suggestion is `DRAFT`.
- Validate supplier, warehouse, and product belong to the current company and account book.
- Create a purchase order in `DRAFT` status using existing purchase order service behavior where possible.
- Use one line with the suggestion product and quantity.
- Price and tax fields use existing purchase order defaults if available. If no safe default exists, conversion must reject with a clear validation error instead of creating a financially incomplete order.
- Store generated purchase order id and number on the suggestion.
- Change suggestion status to `CONVERTED`.

Permissions:

- `inventory:replenishment:view`
- `inventory:replenishment:create`
- `inventory:replenishment:cancel`
- `inventory:replenishment:convert`

All queries and mutations must scope by `companyId` and `accountBookId`.

## Frontend Design

Update the inventory alert page:

- Add `生成补货建议` action for `ACTIVE` low-stock alerts.
- Open a dialog prefilled with warehouse, product, shortage quantity, and suggested quantity.
- Let the user select supplier, expected arrival date, and remark.
- After successful creation, refresh alerts and show a link to the suggestion list.

Add route:

- `/inventory/replenishment-suggestions`
- Title: `补货建议`
- Permission: `inventory:replenishment:view`

The page should be a dense operational list:

- Filters for status, warehouse, product, supplier, suggestion number, and date range.
- Table columns for suggestion number, warehouse, product, supplier, shortage snapshot, suggested quantity, expected arrival date, status, purchase order number, and operations.
- Operations:
  - Cancel draft suggestion.
  - Convert draft suggestion to purchase order.
  - Navigate to generated purchase order when converted.

## Error Handling

- Creating a suggestion requires the related low-stock rule to exist and still be active.
- Creating a suggestion requires current stock to still be below minimum stock.
- Duplicate active draft suggestions return a business conflict with the existing suggestion number.
- Conversion rejects missing supplier, invalid product, invalid warehouse, invalid quantity, or missing purchase defaults.
- Cancel and convert operations must be idempotency-safe enough to return a clear conflict if the status has already changed.

## Testing

Backend tests:

- Tenant and account-book scoping for list and mutations.
- Creating from a valid low-stock alert writes a draft suggestion and resolves the alert disposition.
- Duplicate draft suggestion is rejected.
- Cancel only works from `DRAFT`.
- Convert creates a draft purchase order and marks the suggestion as `CONVERTED`.
- Converted or cancelled suggestions cannot be converted again.

Frontend checks:

- `npm run type-check`
- `npm run lint`
- `npm run build`

UI smoke:

- Create a low-stock alert rule with stock below minimum.
- Open inventory alerts.
- Generate a replenishment suggestion.
- Open suggestion list.
- Convert suggestion to purchase order.
- Verify the generated purchase order appears in the purchase order page.

## Rollout Notes

Before implementing this feature, fix the existing failing quality gates:

- Frontend `type-check` and `lint` currently fail.
- Backend tests currently include failures in `BusinessReadOnlyTransactionStructureTest` and `InventoryAlertServiceTenantBoundaryTest`.

Those failures are unrelated to the replenishment feature but must not be carried into the final verification, otherwise the new work will be impossible to trust.
