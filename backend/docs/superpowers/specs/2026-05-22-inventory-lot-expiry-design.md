# Inventory Lot And Expiry Design

## Goal

Add lot, expiry, and automatic FEFO/FIFO outbound control to the existing ERP backend without breaking the current aggregate inventory model.

The current inventory core keeps `inv_balance` by `company_id + warehouse_id + product_id` and writes aggregate stock transactions in `inv_txn`. This is enough for total stock, but it cannot answer operational questions such as which lot is on hand, which lot is expiring, or which lots were consumed by an outbound document. This design adds lot-level stock as a controlled extension below the existing inventory posting service.

## Scope

This phase implements:

- Product-level switches for lot control and shelf-life control.
- Lot-level inventory balance by company, warehouse, product, and lot number.
- Lot metadata: lot number, production date, expiry date, and first inbound time.
- Inbound posting with lot creation or lot quantity increase.
- Outbound posting with explicit lot consumption when a lot is provided.
- Outbound posting with automatic FEFO/FIFO lot picking when a lot is not provided.
- Automatic outbound splitting into one inventory transaction per consumed lot.
- Lot-aware purchase receipt, purchase return, sales delivery, sales return, inventory adjustment, inventory transfer, production issue, production completion, production material return, and opening inventory import support.
- Lot balance query APIs for warehouse operations and expiry review.
- Regression coverage for lot-controlled and non-lot-controlled products.

This phase does not implement:

- Serial number management.
- Warehouse bin/location inventory.
- Lot-level reservation during sales approval or production order approval.
- Quality inspection, quarantine, blocked stock, or qualified/unqualified stock states.
- Lot split, merge, relabel, or correction operations.
- Frontend pages.

## Existing Context

The existing stock posting boundary is `InventoryPostingService`:

- `postInbound(...)` updates `inv_balance` and inserts an `IN` row into `inv_txn`.
- `postOutbound(...)` checks aggregate available stock, updates `inv_balance`, calculates outbound amount, and inserts an `OUT` row into `inv_txn`.
- `reserve(...)` and release methods update `inv_balance.qty_reserved` and `inv_reservation`.

Multiple domains call this service:

- Purchase receipt and purchase return.
- Sales delivery and sales return.
- Inventory adjustment and inventory transfer.
- Opening inventory import.
- Production issue, completion, and material return.

Because this service is already the central inventory boundary, lot support belongs here. Business services should pass lot intent into the posting command, but should not update lot balances directly.

## Recommended Approach

Use a lot balance table plus lot metadata on inventory transactions.

`inv_balance` remains the aggregate source used by current reports, reconciliation, period close checks, and existing APIs. `inv_lot_balance` becomes the lot-level operational stock view. Every lot-aware posting updates both tables in the same transaction.

This avoids a risky rewrite of existing inventory while still giving warehouse users a real lot ledger. It also keeps non-lot-controlled products compatible with the current API shape.

Outbound lot selection works as follows:

- If the request provides `lotNo`, consume only that lot.
- If the request omits `lotNo` for a shelf-life-controlled product, automatically consume lots by FEFO: lots with an expiry date first, earliest `expiryDate`, earliest first inbound time, then lowest id.
- If the request omits `lotNo` for a lot-controlled product without shelf-life control, automatically consume lots by FIFO: earliest first inbound time first, then lowest id.
- If one business line consumes multiple lots, keep the business line unchanged and insert one `inv_txn` row per consumed lot.

Rejected alternatives:

- Only adding lot fields to document lines and `inv_txn` would support trace text but not reliable lot stock. That is too weak for production use.
- Requiring users to manually provide `lotNo` for every outbound line would reduce backend complexity but would push picking correctness to operators. For an ERP inventory module, that is half a feature.
- Making sales and production reservations lot-level in this phase would be cleaner for strict picking, but it would drag approval, reservation repair, manual release, and picking strategy into the same change. That is too much surface for one release.

## Data Model

### Product Control Flags

Add columns to `md_product`:

- `lot_controlled TINYINT NOT NULL DEFAULT 0`
- `shelf_life_controlled TINYINT NOT NULL DEFAULT 0`

Rules:

- `shelf_life_controlled = 1` requires `lot_controlled = 1`.
- Lot-controlled products require `lotNo` on inbound physical inventory postings.
- Shelf-life-controlled products require `expiryDate` on inbound physical inventory postings.
- Non-lot-controlled products must not create lot balances.
- Enabling lot control for a product that already has aggregate stock is rejected unless matching lot balances already exist. The system must not invent historical lots.

Java model and product create/update/response DTOs expose both flags as booleans.

### Lot Balance

Add table `inv_lot_balance`:

- `id`
- `company_id`
- `account_book_id`
- `warehouse_id`
- `product_id`
- `lot_no`
- `production_date`
- `expiry_date`
- `first_inbound_time`
- `qty_on_hand`
- `qty_reserved`
- `amount_on_hand`
- audit columns and optimistic `version`

Indexes:

- Unique: `company_id + warehouse_id + product_id + lot_no`
- Query: `company_id + product_id + expiry_date`
- Query: `company_id + warehouse_id + product_id`
- Query/picking: `company_id + warehouse_id + product_id + expiry_date + first_inbound_time`

`qty_reserved` is included for forward compatibility, but this phase leaves lot-level reservations at zero.

### Inventory Transaction

Add columns to `inv_txn`:

- `lot_no`
- `production_date`
- `expiry_date`

The transaction remains the authoritative consumption trace. For a lot-controlled outbound line that is auto-picked from multiple lots, multiple `OUT` rows are inserted with the same `biz_type`, `biz_no`, and `biz_line_id`, each carrying its consumed lot and quantity.

Existing idempotency checks must account for this split behavior. A repeated outbound call should return the total amount of existing `OUT` rows for the business line instead of inserting duplicates.

### Business Line Tables

Add lot metadata columns to physical document line tables that need to persist user intent and support response display:

- `lot_no`
- `production_date`
- `expiry_date`

For auto-picked outbound lines, the business line fields may remain null. The actual lot consumption is read from `inv_txn`. This keeps existing document line shape stable and avoids splitting business documents just to display stock movements.

## API And DTO Changes

### Product APIs

Add fields to product create/update requests and responses:

- `lotControlled`
- `shelfLifeControlled`

Validation:

- `shelfLifeControlled=true` requires `lotControlled=true`.
- Turning on lot control is rejected when the product has aggregate stock without corresponding lot balances.
- Turning off lot control is rejected when lot balances exist.

### Posting Command

Extend `InventoryPostingCommand` with:

- `String lotNo`
- `LocalDate productionDate`
- `LocalDate expiryDate`

Keep overloaded constructors so existing non-lot call sites can compile while each domain is migrated deliberately.

For automatic picking, `postOutbound(...)` returns the total outbound cost amount, while internally inserting one transaction per consumed lot.

### Business Line Requests

Add lot fields to physical posting request lines:

- `PurchaseReceiptLineRequest`
- `PurchaseReturnLineRequest`
- `SalesDeliveryLineRequest`
- `SalesReturnLineRequest`
- `InventoryAdjustmentLineRequest`
- `InventoryTransferLineRequest`
- `InventoryStockCheckLineRequest`
- Production issue/completion/return request lines
- Opening inventory import template and handler

Response line DTOs include the same lot fields where the corresponding table stores them. For auto-picked outbound lines, detailed lot consumption is exposed through inventory transaction queries, not by splitting the business line response.

### Lot Balance Query

Add query endpoints under the existing inventory stock controller:

- `GET /api/inventory/lot-balances`
- `GET /api/inventory/lot-balances/{id}`

Filters:

- `warehouseId`
- `productId`
- `lotNo`
- `expiryDateFrom`
- `expiryDateTo`
- `expiringWithinDays`

Response fields:

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
- `updatedTime`

The endpoint uses the same company and data-scope constraints as aggregate balance queries.

## Posting Rules

### Inbound

For lot-controlled products:

1. Validate `lotNo` is present.
2. If shelf-life-controlled, validate `expiryDate` is present.
3. Normalize lot number by trimming. Empty is rejected.
4. If the lot balance does not exist, insert it with inbound quantity, amount, metadata, and `first_inbound_time`.
5. If the lot balance exists, validate immutable metadata:
   - Existing `production_date` must match the supplied non-null production date.
   - Existing `expiry_date` must match the supplied non-null expiry date.
6. Increase `inv_lot_balance.qty_on_hand` and `amount_on_hand`.
7. Increase aggregate `inv_balance`.
8. Insert `inv_txn` with lot metadata.

For non-lot-controlled products:

- Reject supplied `lotNo`, `productionDate`, or `expiryDate`.
- Continue updating only aggregate stock.

### Outbound With Explicit Lot

For lot-controlled products when `lotNo` is supplied:

1. Normalize and validate `lotNo`.
2. Load the matching `inv_lot_balance` for company, warehouse, product, and lot.
3. Validate `qty_on_hand - qty_reserved >= outbound qty`.
4. Calculate the lot outbound amount from the lot balance weighted average.
5. Decrease lot quantity and amount.
6. Decrease aggregate stock by the same quantity and the same lot outbound amount.
7. Insert one `OUT` `inv_txn` row with lot metadata.

For non-lot-controlled products:

- Reject supplied lot metadata and keep the current aggregate outbound behavior.

### Outbound With Automatic FEFO/FIFO

For lot-controlled products when `lotNo` is omitted:

1. Load candidate lot balances for company, warehouse, and product where available quantity is greater than zero.
2. Sort candidates:
   - Shelf-life-controlled products: rows with `expiry_date IS NOT NULL` first, then `expiry_date ASC`, `first_inbound_time ASC`, `id ASC`.
   - Lot-controlled products without shelf-life control: `first_inbound_time ASC`, `id ASC`.
3. Walk candidates until the required outbound quantity is fully allocated.
4. If total available lot quantity is insufficient, fail before updating any balance.
5. For each allocation, decrease that lot's `qty_on_hand` and `amount_on_hand`.
6. Decrease aggregate stock once for the full outbound quantity and the sum of consumed lot costs.
7. Insert one `OUT` `inv_txn` row per consumed lot.

The whole posting is one transaction. Partial allocation must never be committed.

## Domain Behavior

Purchase receipt creates or increases lots. Purchase return can use an explicit lot or automatic FEFO/FIFO picking.

Sales delivery can use an explicit lot or automatic FEFO/FIFO picking. Sales order approval still reserves aggregate stock only; delivery is where lot consumption is finalized.

Sales return increases the returned lot. If the return references an original delivery line, the service should default the lot fields from the delivery line or inventory transaction when there is a single consumed lot. If the original delivery consumed multiple lots, the return request must specify the returned lot.

Inventory adjustment supports lot-aware gain and loss. For lot-controlled products, positive adjustments require lot metadata; negative adjustments can use an explicit lot or automatic FEFO/FIFO picking.

Inventory transfer posts an outbound transaction from the source warehouse lot and an inbound transaction to the target warehouse using the same consumed lot metadata. If the transfer source auto-picks multiple lots, the target inbound side also splits by those same lots.

Production issue consumes material lots with explicit or automatic FEFO/FIFO picking. Production completion creates or increases finished-goods lots. Production material return restores the lot from the original issue where possible; when the original issue consumed multiple lots, the return request must specify the returned lot.

Opening inventory import supports optional lot fields. For lot-controlled products, `lot_no` is required and `expiry_date` is required when shelf-life control is enabled.

## Error Handling

Use explicit business validation messages:

- `启用批次管理的商品必须填写批次号`
- `启用效期管理的商品必须填写有效期`
- `未启用批次管理的商品不能填写批次信息`
- `批次库存不足，不能执行出库`
- `批次生产日期与已有批次不一致`
- `批次有效期与已有批次不一致`
- `商品已有库存，不能直接启用批次管理`
- `商品存在批次库存，不能关闭批次管理`

Optimistic update conflicts continue to use `BusinessConflictException` with retry behavior inside `InventoryPostingService`.

## Data Flow

Inbound example:

`PurchaseReceiptService -> InventoryPostingCommand(lot fields) -> InventoryPostingService -> inv_lot_balance + inv_balance + inv_txn`

Explicit outbound example:

`SalesDeliveryService -> InventoryPostingCommand(lotNo) -> InventoryPostingService -> validate inv_lot_balance -> decrease inv_lot_balance + inv_balance -> inv_txn`

Automatic outbound example:

`SalesDeliveryService -> InventoryPostingCommand(no lotNo) -> InventoryPostingService -> pick lots by FEFO/FIFO -> decrease multiple inv_lot_balance rows + one inv_balance row -> multiple inv_txn rows`

Query example:

`InventoryStockQueryController -> InventoryStockQueryService -> InventoryLotBalanceMapper -> InventoryLotBalanceResponse`

## Migration Strategy

Create a new Flyway migration after `V42`:

- Add product control columns.
- Add lot columns to `inv_txn`.
- Create `inv_lot_balance`.
- Add lot columns to physical document line tables that need to persist lot intent.
- Add indexes.

Existing stock remains aggregate-only. Existing products default to non-lot-controlled, so current data does not need automatic lot backfill.

If an existing product is later turned into lot-controlled while it already has aggregate stock, a separate conversion tool is required. This phase does not allow silent conversion because it would invent lot history. Product update validation should reject enabling lot control when aggregate on-hand stock exists without corresponding lot balances.

## Testing

Targeted tests:

- Product create/update validates shelf-life requires lot control.
- Product update rejects enabling lot control when aggregate stock exists without lot balances.
- Product update rejects disabling lot control when lot balances exist.
- Lot-controlled inbound creates `inv_lot_balance` and `inv_txn` lot metadata.
- Second inbound to the same lot increases the lot balance.
- Inbound to existing lot with conflicting expiry date fails.
- Lot-controlled inbound without lot number fails.
- Shelf-life-controlled inbound without expiry date fails.
- Explicit outbound from one lot does not reduce another lot.
- Auto outbound for shelf-life-controlled product follows FEFO.
- Auto outbound for lot-controlled product without shelf-life control follows FIFO.
- Auto outbound across multiple lots inserts multiple `inv_txn` rows with the same business line id.
- Auto outbound with insufficient total lot stock fails without partial updates.
- Non-lot-controlled product rejects lot metadata.
- Sales delivery and purchase receipt controller smoke tests include lot fields.
- Opening inventory import validates and commits lot stock.
- Period close and reconciliation still see aggregate `inv_balance` unchanged.

Verification commands:

- `.\mvnw.cmd -q -Dtest=InventoryPostingServiceTest test`
- `.\mvnw.cmd -q -Dtest=PurchaseReceiptControllerTest,SalesDeliveryControllerTest,InventoryStockQueryControllerTest test`
- `.\mvnw.cmd -q -Dtest=InitialImportControllerTest test`
- `.\scripts\release-check.ps1`

Docker Compose pre-production validation remains the final release gate and should run only after local tests pass.

## Acceptance Criteria

- Lot-controlled products cannot be inbound-posted without a lot number.
- Shelf-life-controlled products cannot be inbound-posted without an expiry date.
- Outbound posting supports explicit lot consumption.
- Outbound posting supports automatic FEFO/FIFO lot consumption when no lot is supplied.
- Auto-picked outbound lines are traceable through one `inv_txn` row per consumed lot.
- Lot-level on-hand quantity is queryable by warehouse, product, lot, and expiry window.
- Aggregate inventory behavior stays compatible for non-lot-controlled products.
- All lot updates and aggregate inventory updates are committed atomically.
- Existing release checks pass.
