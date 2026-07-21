# Web Barcode Scanning Design

**Date:** 2026-07-20
**Status:** Approved by the active completion mandate; implementation may proceed without waiting for another scope decision.

## Goal

Turn the existing frontend-only `barcode` field into a real tenant-scoped product contract and support quantity entry by camera or keyboard-wedge scanner in the core purchase receipt and sales delivery workflows.

## Scope

- Persist one optional normalized barcode on each product.
- Enforce barcode uniqueness within one company and account book while allowing the same barcode in different account books.
- Expose exact active-product lookup by barcode.
- Keep product create, update, detail, list, export, and frontend models consistent.
- Provide one reusable Vue scan field for manual entry, keyboard-wedge scanners, and camera scanning.
- In purchase receipts and sales deliveries, each accepted scan increments the matching document line by one without exceeding its remaining quantity.
- Reject unknown, disabled, or document-unrelated products with a visible message and no quantity mutation.

Inventory adjustments, transfers, and production issue pages may reuse the same field later, but they are not required to prove the first complete inbound/outbound scanning loop.

## Approaches Considered

### Native browser detector plus keyboard fallback (selected)

Use `BarcodeDetector` when available and `navigator.mediaDevices.getUserMedia` for the camera. A normal text input completed by Enter supports keyboard-wedge scanners and manual entry everywhere.

This adds no runtime dependency and degrades cleanly. Camera support is browser-dependent, so the UI must state the unsupported condition and keep the input usable.

### ZXing browser package

This provides wider camera support but adds a decoding dependency and bundle weight. It is unnecessary for the current Chromium-oriented frontend unless production browser evidence later shows native detection is insufficient.

### Keyboard scanner only

This is the smallest implementation but contradicts the documented camera/scanner requirement and is rejected.

## Backend Design

Migration `V119` adds nullable `barcode VARCHAR(128)` to `md_product` and a unique key on `(company_id, account_book_id, barcode)`. MySQL permits multiple `NULL` values, so products without barcodes remain valid.

The product entity, create/update requests, response, list keyword search, and CSV export all include `barcode`. Service normalization trims whitespace and stores blank input as `NULL`.

`GET /api/masterdata/products/by-barcode?barcode=...` returns the active product in the current company and account book. Missing, blank, disabled, deleted, cross-tenant, or cross-account-book values return the existing business validation response rather than leaking product existence.

The service performs a friendly uniqueness check before writes; the database unique key remains the race-condition backstop.

## Frontend Design

`BarcodeScanField` owns scanner input and camera lifecycle. Enter submits the trimmed input. The camera button opens an unframed video preview, requests the environment-facing camera, polls `BarcodeDetector`, emits the first stable value, then stops every media track. Closing the dialog, unmounting, permission denial, unsupported APIs, and decode errors all stop resources deterministically.

Purchase receipt and sales delivery dialogs expose a compact scan tool above the line table. A scan calls the exact lookup endpoint, finds the product in the selected order, and increments its current quantity by one. It never inserts an arbitrary product into an order-derived document. Duplicate rapid camera detections are suppressed by closing the camera after a successful decode.

To make scan counting usable with the existing forms, entering scan-count mode explicitly resets line quantities to zero after confirmation. Editing an existing draft does not reset automatically; scans increment the saved quantities up to each line's remaining maximum.

## Error Handling

- Empty input: local validation, no request.
- Unknown or inactive barcode: backend validation message.
- Product not present on selected order: warning, no mutation.
- Maximum already reached: warning, no mutation.
- Camera unavailable or permission denied: camera dialog reports the condition while manual/scanner input remains enabled.
- Concurrent duplicate barcode writes: database conflict is translated to a stable business error.

## Verification

- Migration smoke tests cover the column and unique key.
- Product service/controller tests cover normalization, tenant/account-book isolation, uniqueness, active lookup, and serialization.
- Frontend unit tests cover Enter submission, whitespace normalization, unsupported camera state, matching-line increment, unrelated product rejection, and max enforcement.
- Contract checks require the backend lookup API, frontend API binding, scan component, and both workflow integrations.
- Full Maven tests, frontend tests, lint, type check, contract check, production build, and browser rendering must remain green.

## Acceptance Criteria

- A barcode entered on the product page is persisted and returned after reload.
- Two products in the same account book cannot share a barcode; another account book may reuse it.
- A keyboard-wedge scanner can increment purchase receipt and sales delivery lines through Enter-delimited input.
- A supported camera can decode a barcode and invoke the same quantity path.
- Unsupported camera environments remain fully usable with manual input or a scanner.
- No scan can exceed the order-derived quantity or add an unrelated product.
