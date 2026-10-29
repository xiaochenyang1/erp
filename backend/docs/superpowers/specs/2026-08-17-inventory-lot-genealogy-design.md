# Inventory Lot Genealogy Design

**Date:** 2026-08-17
**Status:** Approach and security decision approved in conversation. Spec re-verified against the codebase on 2026-08-18 (see "Verification Log"); three corrections applied inline. Ready for an implementation plan.

## Goal

Answer the two recall questions that lot tracing cannot answer today:

- Where did this lot come from — which supplier and purchase document, and for a manufactured lot, which material lots were consumed?
- Where did this lot go — which customers received it, through which sales documents?

`2026-05-25-inventory-lot-trace-alert-design.md` deliberately placed "full document-chain tracing across purchase order, sales order, transfer, production order" and "recall workflow" out of scope. `docs/未完成.md` item 2.8 records the same gap as "端到端追溯查询未扩". This design closes it.

The existing `GET /api/inventory/lots/trace` returns a flat, paginated list of `inv_txn` rows for one `(productId, lotNo)`. It shows that a lot moved through a document number, but it resolves no counterparty and it stops at the manufacturing boundary: for a finished-good lot it reports "produced by order MO-xxxx" and cannot say which raw-material lots went into that order. Genealogy is the missing traversal, not a missing column.

## Scope

In scope:

- Upstream traversal from a lot to its supplier and purchase receipt, recursing through production orders into consumed material lots.
- Downstream traversal from a lot to its customers and sales deliveries, recursing through production orders into produced finished-good lots.
- One read-only endpoint returning both directions as a genealogy tree.
- A dedicated frontend page with lot search, tree display, and client-side CSV export of the downstream recall list.
- A new menu node and permission code seeded by migration.
- Explicit, reported limits: max depth, node cap, and data-scope restriction.

Out of scope:

- Database schema changes. The traversal reads `inv_txn` only.
- A recall workflow with its own document, approval, or status machine. This design delivers the query that a recall depends on, not the recall process.
- Serial-number genealogy. Serial numbers are registered per document, but this design traces lots.
- Finance voucher chaining. `BusinessTraceService` already covers document-to-voucher tracing.
- Changing the existing `/api/inventory/lots/trace` endpoint or its frontend dialog. Both keep working unchanged.
- Cost roll-up along the genealogy.

## Data Foundation

The traversal is derivable from `inv_txn` alone because posting already records the linking key. Verified in the current code:

| `biz_type` | `biz_no` written | Source |
|---|---|---|
| `PURCHASE_RECEIPT` | `receipt.getReceiptNo()` | `PurchaseReceiptPostingService` |
| `SALES_DELIVERY` | `delivery.getDeliveryNo()` | `SalesDeliveryPostingService` |
| `PRODUCTION_ISSUE` | `order.getOrderNo()` | `ProductionIssueService` |
| `PRODUCTION_COMPLETION` | `order.getOrderNo()` | `ProductionCompletionService` |

Material issue and finished-good completion both write the **production order number** as `biz_no`, and both carry `lot_no`. That shared key is the genealogy edge across manufacturing:

```
finished lot --(PRODUCTION_COMPLETION, biz_no = MO-1)--> MO-1
MO-1 --(PRODUCTION_ISSUE, biz_no = MO-1)--> material lot A, material lot B
```

No new data capture is required.

Counterparty resolution paths, each two hops from the document number:

- `PURCHASE_RECEIPT` → `PurchaseReceiptEntity` by `receipt_no` → `orderId` → `PurchaseOrderEntity` → `supplierId` → `SupplierEntity`
- `SALES_DELIVERY` → `SalesDeliveryEntity` by `delivery_no` → `orderId` → `SalesOrderEntity` → `customerId` → `CustomerEntity`

## Approaches Considered

### Level-batched Java traversal in a read-only service (selected)

A new `InventoryLotGenealogyService` walks `inv_txn` one level at a time. Each level collects the set of `(productId, lotNo)` reached so far and issues one `IN` query, so the walk costs a bounded number of round trips — depth multiplied by document-type resolution — rather than one query per node.

This matches how every other read service in the repository is built: `LambdaQueryWrapper`, `DataScopeService` scope injection, mocked-mapper unit tests. It needs no schema change and no native SQL.

The cost is several round trips instead of one. For a single-lot recall query that is acceptable, and the node caps bound it.

### MySQL recursive CTE

One `WITH RECURSIVE` statement would return the whole graph in a single round trip.

Rejected. Hand-written native SQL must inject company, account book, and warehouse data scope itself, which is exactly what `NativeSqlTenantScopeConfigurationTest` guards against. `2026-05-25` and later notes also record that H2 does not accept parts of the project's MySQL syntax, so recursive SQL would weaken the test fallback path. The performance gain is not needed for single-lot queries.

### Materialized `lot_genealogy` edge table

Write genealogy edges at posting time and read them directly.

Rejected as YAGNI. It requires a new table, writes in the posting paths, and a backfill for existing history, to duplicate information `inv_txn` already holds. No performance requirement justifies that today. If single-lot traversal ever becomes too slow, this remains the escape hatch.

## Traversal Design

This is the only genuinely new logic. The repository has no recursion precedent — MRP's BOM expansion is single-level — so the guards are established here.

### Upstream

Starting from `(productId, lotNo)`, load `inv_txn` rows with `direction = IN`, then per `biz_type`:

- `PURCHASE_RECEIPT` — resolve supplier and purchase order, terminal reason `PURCHASED`.
- `PRODUCTION_COMPLETION` — take `biz_no` as the production order number, load `PRODUCTION_ISSUE` rows with the same `biz_no`, and emit one child lot node per distinct material `(productId, lotNo)`. Recurse.
- `SALES_RETURN` — resolve the returning customer, terminal reason `RETURNED_BY_CUSTOMER`. Knowing a suspect lot re-entered stock through a customer return is a real recall answer.
- `PRODUCTION_RETURN` — material returned from the shop floor, terminal reason `MOVED_INTERNALLY`.
- `INVENTORY_TRANSFER` — the inbound leg, terminal reason `MOVED_INTERNALLY`.
- `INVENTORY_ADJUSTMENT`, `INVENTORY_CHECK` — stock gain, terminal reason `ADJUSTED`.
- `OPENING_BALANCE`, `OPENING_INVENTORY` — terminal reason `OPENING_BALANCE`.
- Any other `biz_type` — terminal reason `UNKNOWN_SOURCE`, carrying the raw `biz_type` so the page stays honest rather than dropping the branch.

### Downstream

Starting from the same root, load rows with `direction = OUT`, then per `biz_type`:

- `SALES_DELIVERY` — resolve customer and sales order, terminal reason `SOLD`. These links are the recall list.
- `PRODUCTION_ISSUE` — take `biz_no` as the production order number, load `PRODUCTION_COMPLETION` rows with the same `biz_no`, emit one child lot node per distinct finished-good `(productId, lotNo)`. Recurse.
- `PURCHASE_RETURN` — resolve supplier, terminal reason `RETURNED_TO_SUPPLIER`. Part of a suspect lot may already be back with the supplier.
- `PRODUCTION_COMPLETION_REVERSAL` — terminal reason `REVERSED`.
- `INVENTORY_TRANSFER` — the outbound leg, terminal reason `MOVED_INTERNALLY`.
- `INVENTORY_ADJUSTMENT`, `INVENTORY_CHECK` — stock loss, terminal reason `ADJUSTED`.
- Any other `biz_type` — terminal reason `UNKNOWN_DESTINATION`.

`InventoryTransferService` posts both legs of a transfer, so one transfer document number legitimately appears on both sides of a genealogy. That is correct, not a duplicate.

A production order that has consumed material but not yet reported completion yields a link with reason `IN_PRODUCTION` and no child node. That is a real recall answer: the lot is still on the shop floor.

### Lots that do not exist: non-lot-controlled materials and outputs

**Added 2026-08-18, found during spec verification.** The spec above assumed every expanded child has a lot number. It does not. `lot_no` is only forced when `product.lotControlled = 1`, so a production order can legitimately consume a non-lot-controlled material, or report a non-lot-controlled output, and write `inv_txn` rows whose `lot_no` is `NULL`. Genealogy is keyed on `(productId, lotNo)`, so such a child cannot be recursed into.

Dropping those branches would silently understate a recall, which this design forbids. Instead: emit the child node with `lotNo = null` so the consumed material or produced output is still named, and mark the link terminal with reason `MATERIAL_NOT_LOT_CONTROLLED` upstream and `OUTPUT_NOT_LOT_CONTROLLED` downstream. The operator learns that the chain genuinely ends there because the product is not lot-tracked, which is a different fact from "the chain ended because we hit a cap".

The root lot is unaffected: `lotNo` remains required and blank input is still rejected.

### Guards

- **Depth.** `maxDepth` defaults to `5`, hard cap `10`. Reaching it sets `truncated` and adds reason `MAX_DEPTH`.
- **Cycles.** A `visited` set keyed by `(productId, lotNo)` per direction. A lot already expanded in that direction is linked but not re-expanded, with reason `ALREADY_VISITED`. Return-then-reship history makes repeats legitimate, so this must not be treated as an error.
- **Fan-out.** At most `200` child nodes per level, matching the project's page-size ceiling, and at most `500` nodes per direction overall. The two caps are reported separately as `NODE_LIMIT_PER_LEVEL` and `NODE_LIMIT_TOTAL` so an operator can tell a wide genealogy from a deep one.
- **No silent truncation.** Every limit that fires is named in `limits.truncationReasons`. A tree that looks complete must be complete.
- **Data scope.** `DataScopeService.applyInventoryTransactionScope` is applied at every level, not only the first. A user restricted to some warehouses therefore sees a partial genealogy, so `limits.scopeLimited` is set whenever the caller's snapshot is not ALL. Presenting a warehouse-filtered recall list as complete would be the worst failure mode of this feature.

## API Design

```http
GET /api/inventory/lots/genealogy
```

Query fields:

- `productId`, required
- `lotNo`, required, trimmed, rejected when blank
- `direction`, optional, `UPSTREAM` | `DOWNSTREAM` | `BOTH`, default `BOTH`, normalized to uppercase
- `maxDepth`, optional, default `5`, values below `1` or above `10` are clamped

Response:

```
InventoryLotGenealogyResponse
  root         LotGenealogyNode        the queried lot, always present
  upstream     LotGenealogyNode|null   null when direction is DOWNSTREAM
  downstream   LotGenealogyNode|null   null when direction is UPSTREAM
  limits       GenealogyLimits

LotGenealogyNode
  productId, productCode, productName
  lotNo, productionDate, expiryDate
  depth
  links        List<LotGenealogyLink>

LotGenealogyLink
  bizType, bizNo, bizLabel, documentRoute
  occurredTime, qty
  warehouseId, warehouseName
  counterparty CounterpartyRef|null
  terminalReason String|null          non-null when the chain stops here
  node         LotGenealogyNode|null  next level, null for terminal links

CounterpartyRef
  type         SUPPLIER | CUSTOMER
  id, code, name
  documentNo                          purchase or sales order number

GenealogyLimits
  maxDepth
  perLevelNodeLimit                   200
  totalNodeLimit                      500, counted per direction
  truncated
  truncationReasons  List<String>     MAX_DEPTH | NODE_LIMIT_PER_LEVEL | NODE_LIMIT_TOTAL
  scopeLimited
```

Terminal reasons, the full closed set: `PURCHASED`, `SOLD`, `RETURNED_BY_CUSTOMER`, `RETURNED_TO_SUPPLIER`, `MOVED_INTERNALLY`, `ADJUSTED`, `OPENING_BALANCE`, `REVERSED`, `IN_PRODUCTION`, `NO_MATERIAL_ISSUED`, `MATERIAL_NOT_LOT_CONTROLLED`, `OUTPUT_NOT_LOT_CONTROLLED`, `ALREADY_VISITED`, `MAX_DEPTH`, `UNKNOWN_SOURCE`, `UNKNOWN_DESTINATION`.

`NO_MATERIAL_ISSUED` is the upstream mirror of `IN_PRODUCTION`, added while planning: a production order can report completion with no `PRODUCTION_ISSUE` rows recorded against it, and that must read as "nothing was consumed on record" rather than falling through to `UNKNOWN_SOURCE`, which would imply the traversal met a `biz_type` it did not understand.


`bizLabel` and `documentRoute` reuse the existing `resolveDocumentLabel` and `resolveDocumentRoute` mappings so deep links behave exactly as they do in the current lot trace dialog.

**Correction 1 (2026-08-18).** Both methods are `private` in `InventoryLotQueryService`, so "reuse" is not a call — it requires extracting them into a shared, injectable `InventoryDocumentLinkResolver` (package `inventory.stock.service`) that both `InventoryLotQueryService` and the new genealogy service depend on. `traceLot`'s output must stay byte-identical after the extraction; the existing `InventoryLotQueryServiceTest` is the guard. `resolveDocumentRoute` already covers all twelve `biz_type` values this design traverses, so no new route mapping is needed.

**Correction 2 (2026-08-18).** `resolveDocumentLabel` returns hardcoded Chinese, and the current dialog renders `row.documentLabel || row.bizType`, so an English-locale user already sees Chinese document types there. The new page must therefore map `bizType` to a label through i18n on the client and treat `bizLabel` as a fallback only, rather than displaying the backend string directly. Fixing the pre-existing hole in the `/inventory/stocks` dialog is out of scope for this design.

Errors follow the existing conventions: missing `productId` or blank `lotNo` raise `IllegalArgumentException` with the same wording style as `traceLot`. A lot with no transactions at all returns a root node with empty `links`, not a 404 — "this lot has no history in your scope" is a valid answer.

## Permission And Migration

Migration `V145` seeds one `MENU` node and its role binding:

- Parent: `5009`, the inventory directory.
- Menu id `5480`, code `INVENTORY_LOT_GENEALOGY`, path `/inventory/lot-genealogy`, component `inventory/lot-genealogy/index`, permission `inventory:lot:genealogy`. Current menu ids reach `5471`.
- `sys_role_menu` id `7490`, bound to `ERP_ADMIN` (`3002`). `SUPER_ADMIN` traverses the full tree and needs no binding.
- `ON DUPLICATE KEY UPDATE` for idempotency, following `V98`.

The controller method carries `@PreAuthorize` on the new `inventory:lot:genealogy` code, and `PermissionCodes` gains the matching constant. V126's runtime menu alignment filters nodes whose route or component does not exist, so the frontend route must land in the same change.

**Correction 3 (2026-08-18).** The constant belongs in `InventoryPermissionCodes`, not in `PermissionCodes` itself — `PermissionCodes` is a `final` aggregator implementing the fourteen module interfaces, and it reflectively collects every non-`HAS_` `String` field into `allPermissions()`. Adding `INVENTORY_LOT_GENEALOGY = "inventory:lot:genealogy"` plus `HAS_INVENTORY_LOT_GENEALOGY` there registers the code automatically and keeps it reachable as `PermissionCodes.HAS_INVENTORY_LOT_GENEALOGY`. Note the code omits the `:view` suffix that the rest of `InventoryPermissionCodes` uses; this is the approved design's wording and the seeded value must match it exactly.

**Security decision, approved in conversation.** The genealogy exposes supplier and customer identities under the page permission alone, without additionally requiring customer or supplier masterdata read permission. The roles that run recalls — quality and production managers — commonly lack masterdata rights, and degrading names to document numbers for them would make the page useless to its primary audience. This is a deliberate widening relative to the AR/AP pages, which do gate counterparty options on masterdata permissions. Revisit if a tenant needs supplier identities hidden from quality staff.

## Frontend Design

New page `frontend/src/views/inventory/lot-genealogy/index.vue`, split per the project's established shape:

- `useInventoryLotGenealogyPresentation` — direction and terminal-reason labels, quantity and date formatting through the user's preferences, counterparty display, truncation and scope-limited banners.
- `useInventoryLotGenealogyQuery` — product and lot input, direction and depth selection, request sequencing so a slow response cannot repaint a newer query, and failure feedback.
- `useInventoryLotGenealogyTree` — maps the nested response into `el-tree` data for both directions, expansion state, and derivation of the flat downstream recall list.

Recall export is client-side CSV from the already-loaded downstream tree, matching the existing `exportSelectedRowsToCsv` pattern. A single-lot recall list is small, and a server-side export would require repeating the traversal.

Full Chinese and English i18n keys, and the page is reachable from the lot trace dialog in `/inventory/stocks` so operators can escalate from movement history to genealogy without retyping the lot.

## Testing

Backend, mocked mappers unless stated:

- Upstream: purchase receipt resolves supplier and terminates; production completion expands into two material lots; multi-level chain across two production orders; opening balance terminates; unknown `biz_type` terminates without dropping the branch.
- Downstream: sales delivery resolves customer and terminates; production issue expands into the produced lot; a production order with issue but no completion yields `IN_PRODUCTION`.
- Guards: `maxDepth` clamped at both ends and reported; a cyclic lot graph terminates with `ALREADY_VISITED`; the per-level and total node caps each set `truncated` with their own reason; `scopeLimited` set for non-ALL snapshots.
- Tenant boundary: every level filters company and account book; a material lot belonging to another account book is not reached; `applyInventoryTransactionScope` is invoked per level, not only once.
- Batching: expanding N lots at one level issues one `IN` query, asserted on the captured wrapper, so the level-batched claim cannot silently regress into N+1.
- Controller: permission code enforced, query binding and clamping.
- Migration: `V145` menu and role binding assertions, following the existing migration contract tests.

Frontend: one test file per composable, covering label mapping, request sequencing, tree mapping for both directions, recall CSV rows, and truncation and scope banners. Plus the i18n key completeness and contract checks the project already enforces.

## Acceptance Criteria

- Given a finished-good lot produced from two purchased material lots, the upstream tree reaches both suppliers across the production order.
- Given a material lot consumed by a production order whose output was sold to two customers, the downstream tree lists both customers.
- Given a lot with no transactions in the caller's scope, the response is an empty-link root node rather than an error.
- Given a genealogy deeper than `maxDepth`, the response is truncated and says so.
- Given a caller restricted to one warehouse, `scopeLimited` is set.
- The existing `/api/inventory/lots/trace` endpoint, its dialog, and all current tests behave unchanged.
- Full backend suite green against real MySQL 8.4; frontend lint, types, tests, contract checks, and build green.

## Verification Log (2026-08-18)

Every load-bearing claim in this spec was checked against the working tree before an implementation plan was written. The design survives; three wording corrections are applied inline above.

Confirmed:

- **The manufacturing edge exists.** `ProductionIssueService` calls `postOutbound` with `BIZ_TYPE = "PRODUCTION_ISSUE"`, `order.getOrderNo()` as the document number, and `line.getLotNo()`. `ProductionCompletionService` calls `postInbound` with `BIZ_TYPE = "PRODUCTION_COMPLETION"`, `order.getOrderNo()`, and `completion.getLotNo()`. The shared production order number plus lot number is therefore already persisted on both sides, and `InventoryTransactionWriter` writes `lot_no` and `lot_key` onto `inv_txn`. **The "no schema change" premise holds.**
- **`inv_txn` carries every field the traversal reads**: `companyId`, `accountBookId`, `warehouseId`, `productId`, `bizType`, `bizNo`, `bizLineId`, `direction`, `qty`, `occurredTime`, `lotNo`, `productionDate`, `expiryDate`.
- **All thirteen `biz_type` values named in the traversal exist in `src/main/java`**, so no branch of the switch is dead: `PURCHASE_RECEIPT`, `SALES_DELIVERY`, `PURCHASE_RETURN`, `SALES_RETURN`, `INVENTORY_ADJUSTMENT`, `INVENTORY_TRANSFER`, `PRODUCTION_RETURN`, `PRODUCTION_ISSUE`, `PRODUCTION_COMPLETION`, `PRODUCTION_COMPLETION_REVERSAL`, `OPENING_INVENTORY`, `OPENING_BALANCE`, `INVENTORY_CHECK`.
- **`DataScopeService.applyInventoryTransactionScope`** exists and is the same helper the three existing inventory read services use.
- **Migration and id headroom.** Latest migration is `V144`, so `V145` is next. Highest seeded `sys_menu` id is `5471` and highest `sys_role_menu` id is `7481`, so `5480` and `7490` are both free. `5009` is confirmed as the inventory catalog parent (`V117` hangs the MRP menu off it) and `3002` as `ERP_ADMIN`.
- **Nothing is implemented yet.** `grep -i genealogy` over `backend/src` and `frontend/src` returns no matches, so this is a greenfield addition with no partial work to reconcile.
