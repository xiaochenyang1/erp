# Inventory Lot Trace And Expiry Alert Design

## Goal

Add lightweight lot traceability, expiry alerts, and expired-lot outbound blocking on top of the existing lot-controlled inventory implementation.

This feature answers three business questions:

- Which inventory transactions moved a given product lot?
- Which available lots are expired or expiring soon?
- Can an expired lot still leave inventory?

The answer to the last question is no: expired lots must not be outbound posted.

## Scope

In scope:

- Query lot movement history from `inv_txn`.
- Query expired and expiring lot balances from `inv_lot_balance`.
- Block outbound posting for expired lots.
- Skip expired lots during automatic FEFO/FIFO picking.
- Reuse existing inventory stock view permission and warehouse data scope.

Out of scope:

- Full document-chain tracing across purchase order, sales order, transfer, production order, and finance vouchers.
- Configurable per-product warning days.
- Approval-based expired-lot release.
- Quality status, frozen stock, warehouse bin/location, and recall workflow.
- Database schema changes.

## Business Rules

### Expiry Status

Use the posting or query business date as the reference date.

- `EXPIRED`: `expiryDate < referenceDate`
- `EXPIRING`: `expiryDate >= referenceDate` and `expiryDate <= referenceDate + warningDays`
- Lots without `expiryDate` are not expiry-alert lots.
- Default `warningDays` is `30`.
- A lot with expiry date equal to the reference date is still usable and is classified as `EXPIRING`.

### Alert Query

The alert query only returns lots with available stock:

```text
qty_on_hand - qty_reserved > 0
```

The default alert window is 30 days from the current date. Callers may pass a different `warningDays` for query convenience, but no system-level default is stored.

### Outbound Blocking

For lot-controlled products:

- Explicit outbound from an expired lot fails with `批次已过期，不能出库`.
- Automatic FEFO/FIFO outbound ignores expired lots. Eligible lots must satisfy `expiryDate is null or expiryDate >= referenceDate`.
- If only expired lots have stock, automatic outbound fails with the caller-provided shortage message.
- Expiry blocking applies to all outbound domains that use `InventoryPostingService.postOutbound(...)` or `postOutboundWithAllocations(...)`, including sales delivery, purchase return, inventory adjustment out, transfer out, and production issue.
- Inbound posting is not blocked by expiry; inbound may create or restore expired stock so the business can later adjust, return, or dispose it through controlled processes.

## API Design

Reuse `PermissionCodes.HAS_INVENTORY_STOCK_VIEW`.

### Lot Trace

Endpoint:

```http
GET /api/inventory/lots/trace
```

Query fields:

- `pageNo`, default `1`
- `pageSize`, default `20`, max `200`
- `productId`, required
- `lotNo`, required
- `warehouseId`, optional
- `direction`, optional, normalized to uppercase
- `occurredTimeFrom`, optional
- `occurredTimeTo`, optional

Response record fields:

- `id`
- `warehouseId`
- `productId`
- `lotNo`
- `productionDate`
- `expiryDate`
- `bizType`
- `bizNo`
- `bizLineId`
- `direction`
- `qty`
- `amount`
- `unitCost`
- `occurredTime`
- `remark`

Data source:

- `inv_txn`
- Filter by current `companyId`, current `accountBookId`, `productId`, normalized `lotNo`, and optional filters.
- Apply inventory transaction data scope.
- Sort by `occurredTime desc`, then `id desc`.

This is intentionally transaction-centric. It does not join each business document table because `bizType`, `bizNo`, and `bizLineId` already provide a stable document reference without coupling the inventory query service to every domain module.

### Expiry Alerts

Endpoint:

```http
GET /api/inventory/lots/alerts
```

Query fields:

- `pageNo`, default `1`
- `pageSize`, default `20`, max `200`
- `warehouseId`, optional
- `productId`, optional
- `lotNo`, optional partial match
- `warningDays`, optional, default `30`, minimum `0`, maximum `365`
- `status`, optional: `EXPIRED` or `EXPIRING`

Response record fields:

- `id`
- `warehouseId`
- `productId`
- `lotNo`
- `productionDate`
- `expiryDate`
- `firstInboundTime`
- `qtyOnHand`
- `qtyReserved`
- `qtyAvailable`
- `amountOnHand`
- `expiryStatus`
- `daysToExpiry`
- `updatedTime`

Data source:

- `inv_lot_balance`
- Filter by current `companyId`, current `accountBookId`, positive available stock, non-null `expiryDate`, optional filters, and the selected expiry status.
- Apply inventory lot balance data scope.
- Sort by `expiryDate asc`, then `firstInboundTime asc`, then `id asc`.

## Service Design

### InventoryStockQueryService

Add methods:

- `PageResponse<InventoryLotTraceResponse> traceLot(InventoryLotTraceQuery query)`
- `PageResponse<InventoryLotExpiryAlertResponse> listLotExpiryAlerts(InventoryLotExpiryAlertQuery query)`

The service owns:

- Page normalization.
- Text normalization.
- Like escaping for lot number filters.
- Expiry status calculation.
- Data scope application.

### InventoryStockQueryController

Add routes:

- `GET /api/inventory/lots/trace`
- `GET /api/inventory/lots/alerts`

Both routes return `ApiResponse<PageResponse<...>>`.

### InventoryPostingService

Add expired-lot checks in the lot outbound path only.

Reference date:

```text
command.bizDate() != null ? command.bizDate() : audit.now().toLocalDate()
```

Changes:

- `allocateExplicitLot(...)` rejects expired lot.
- `candidateLotWrapper(...)` only returns lots where `expiryDate is null or expiryDate >= referenceDate`.
- Keep existing FEFO/FIFO sorting among eligible lots.

The check belongs in `InventoryPostingService` because all physical outbound stock mutations pass through it. Domain services should not duplicate expiry logic.

## Error Handling

- Missing `productId` or `lotNo` in trace query: `IllegalArgumentException`.
- Invalid `status` in alert query: `IllegalArgumentException("预警状态不正确")`.
- Explicit expired lot outbound: `IllegalArgumentException("批次已过期，不能出库")`.
- Automatic picking with no eligible non-expired stock: existing caller shortage message.

## Testing

Add or extend focused tests under inventory stock tests.

Required coverage:

- Trace query returns only transactions for the requested `productId + lotNo`.
- Trace query respects direction and warehouse filters.
- Alert query returns available expired lots as `EXPIRED`.
- Alert query returns available lots expiring within 30 days as `EXPIRING`.
- Alert query excludes lots with zero available stock.
- Explicit outbound from an expired lot fails with `批次已过期，不能出库`.
- Automatic outbound skips expired lots and consumes the next eligible lot.
- Automatic outbound fails with shortage message when stock exists only in expired lots.

Regression coverage:

- Existing `InventoryPostingLotServiceTest`.
- Existing purchase receipt and sales delivery lot tests.
- Full `mvnw test` before final completion.

## Acceptance Criteria

- Users can query movement history for a product lot from inventory transaction data.
- Users can query expired and expiring available lot balances with a default 30-day warning window.
- Expired lots cannot be explicitly posted outbound.
- Automatic FEFO/FIFO picking does not consume expired lots.
- No database migration is required.
- Existing batch/expiry posting behavior remains compatible for inbound, transfer, production, and opening import.
