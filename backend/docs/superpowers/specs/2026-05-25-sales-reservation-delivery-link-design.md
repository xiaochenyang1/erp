# Sales Reservation Delivery Link Design

## Goal

Strengthen the link between sales order inventory reservations and sales delivery posting.

The existing system already reserves inventory when a sales order is approved, releases reservation during sales delivery posting, and releases all remaining reservation when an approved undelivered order is cancelled. This design tightens the missing boundary rules so reservation quantities, draft delivery documents, manual releases, and outbound posting stay consistent.

## Scope

In scope:

- Validate sales delivery create and update against remaining sales order reservation.
- Treat other `DRAFT` sales delivery lines as consuming the same reservation capacity.
- Validate sales delivery posting against actual remaining reservation before releasing and posting outbound stock.
- Block manual reservation release when it would leave existing `DRAFT` delivery lines uncovered.
- Add focused tests for reservation and delivery edge cases.

Out of scope:

- Database schema changes.
- Persisting draft reservation occupation in a new table.
- Automatically creating additional reservations during delivery posting.
- Automatically cancelling or rewriting existing draft delivery documents.
- Frontend UI changes.
- Changing non-sales reservation sources.

## Existing Behavior

Sales order approval calls `InventoryPostingService.reserve(...)` with source type `SALES_ORDER`. Each order line gets one reservation row keyed by `sourceLineId`.

Sales delivery posting currently:

- Checks order remaining deliverable quantity.
- Checks warehouse stock on hand.
- Updates `deliveredQty`.
- Calls `releaseReservation("SALES_ORDER", orderLineId, qty, audit)`.
- Calls `postOutbound(...)`.

Manual release currently calls `InventoryPostingService.manualReleaseReservation(...)` directly after data-scope checks.

The weak spot is that draft delivery lines do not participate in reservation availability. Multiple draft deliveries can point at the same reservation and all appear valid until posting. Manual release can also reduce a reservation below the quantity already represented by draft delivery lines.

## Business Rules

### Strict Reservation Requirement

Sales delivery must be backed by remaining reservation from the corresponding sales order line.

If a user manually releases reservation, that released quantity is no longer available to sales delivery. A later sales delivery cannot bypass the reservation rule just because physical stock is still available.

### Create And Update Validation

When creating a sales delivery:

```text
availableReservedQty = active reservation remaining qty for orderLineId
                     - qty from other DRAFT sales delivery lines for orderLineId
```

The requested quantity for that order line must be less than or equal to `availableReservedQty`.

When updating a sales delivery, the same formula applies, but lines belonging to the current delivery are excluded from `other DRAFT` quantity. This allows users to edit the current draft without it double-counting itself.

The existing sales order deliverable quantity validation remains in place. Reservation validation is an additional guard.

### Posting Validation

Before posting a sales delivery, each line must still satisfy:

```text
line qty <= active reservation remaining qty for orderLineId
```

Posting does not subtract other draft delivery quantities, because the current document is the one being posted and the final authority is the actual remaining reservation. If another process has manually released reservation or posted another delivery first, posting fails.

Posting sequence remains:

1. Validate delivery status, account period, order status, order lines, order remaining quantity, stock, and reservation.
2. Mark delivery as `POSTED`.
3. Update sales order line `deliveredQty`.
4. Release reservation.
5. Post outbound stock.
6. Refresh sales order delivery status.
7. Record finance posting.

If reservation release fails, the transaction rolls back and no outbound stock should be posted.

### Manual Release Validation

Manual release for a `SALES_ORDER` reservation must keep enough remaining reservation to cover existing draft delivery lines for the same `sourceLineId`.

```text
remainingAfterRelease = reservation.remainingQty - request.qty
draftDeliveryQty = qty from DRAFT sales delivery lines for sourceLineId
remainingAfterRelease >= draftDeliveryQty
```

If not, fail with a Chinese business message such as:

```text
预占已被销售出库草稿占用，不能释放
```

This rule only applies to `SALES_ORDER` reservations. Other reservation source types keep the existing manual release behavior.

## Service Design

### SalesDeliveryService

Add internal helpers near existing delivery quantity validation:

- Load active reservation remaining quantity for a sales order line.
- Sum draft delivery line quantity by `orderLineId`, optionally excluding the current delivery id.
- Validate requested delivery lines against reservation availability.
- Validate posting lines against current remaining reservation.

The service should use existing mappers rather than adding a new repository abstraction unless the implementation becomes noisy. Queries should stay scoped by `companyId` through the parent delivery/order records and reservation rows.

Suggested validation message:

```text
销售订单预占数量不足，不能创建销售出库单
```

For posting:

```text
销售订单预占数量不足，不能执行销售出库
```

### InventoryReservationOpsService

Before calling `manualReleaseReservation(...)`, detect whether the reservation is a `SALES_ORDER` reservation. If yes, sum related `DRAFT` sales delivery lines and block release that would uncover those drafts.

The method already has `SalesDeliveryLineMapper`, `SalesOrderLineMapper`, and `SalesOrderMapper`, so this stays inside the existing service boundary.

### InventoryPostingService

No new public API is required for this design. Existing `releaseReservation(...)` and `manualReleaseReservation(...)` remain the authority for mutating reservation and inventory balance quantities.

## Data Design

No schema migration.

Continue using:

- `inv_reservation.remaining_qty`
- `sales_delivery.status`
- `sales_delivery_line.order_line_id`
- `sales_delivery_line.qty`

Draft reservation occupation is calculated on demand from `sales_delivery` and `sales_delivery_line`.

## Error Handling

- Missing reservation for a sales order line is treated as insufficient reservation.
- Released or fully released reservation is treated as zero available reservation.
- Multiple active reservations for one `SALES_ORDER` line are not expected, but validation should sum active remaining quantities if encountered. The existing reservation check endpoint can still flag inconsistent data separately.
- Validation failures use `IllegalArgumentException` like nearby sales and inventory business guards.
- Optimistic lock and transaction rollback behavior remain unchanged.

## Testing

Add tests around the existing sales and inventory test classes.

Sales delivery tests:

- Creating a delivery fails when sales order reservation was manually released below requested quantity.
- Creating a second draft delivery fails when another draft already consumes the remaining reservation.
- Updating an existing draft excludes its own quantity and succeeds when total requested quantity is still covered.
- Updating a draft fails when increasing quantity beyond reservation after other draft consumption.
- Posting fails when reservation was manually released after draft creation.

Reservation operation tests:

- Manual release fails when release would leave existing draft sales delivery lines uncovered.
- Manual release succeeds when enough reservation remains after release.
- Non-`SALES_ORDER` manual release keeps existing behavior.

Regression tests:

- Normal sales order approval, delivery create, delivery post, reservation release, stock outbound, and finance posting still pass.
- Partial delivery leaves remaining reservation active.
- Full delivery releases reservation to `RELEASED`.

## Acceptance Criteria

- A sales delivery cannot be created, updated, or posted without enough corresponding sales order reservation.
- Other draft sales deliveries reduce available reservation during create and update validation.
- Manual release cannot invalidate existing draft sales deliveries.
- No database migration is introduced.
- Existing sales delivery, inventory posting, and reservation operation tests continue passing.
