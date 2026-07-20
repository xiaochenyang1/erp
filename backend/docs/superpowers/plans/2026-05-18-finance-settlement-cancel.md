# Finance Settlement Cancel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Current status:** 收款单/付款单作废元数据、接口、服务回滚逻辑和自动化回归已纳入当前实现。下方清单保留原始执行配方；真实付款/收款样例仍需在预生产完成人工验收。

**Goal:** Add cancellable posted receipts and payments so finance users can roll back mistaken settlement documents without introducing reverse vouchers or new document models.

**Architecture:** Keep the existing receipt/payment aggregates and allocation tables as the source of truth. A cancel action updates the document header to `CANCELLED`, stores cancel audit fields on the same row, and replays existing allocation rows in reverse to reduce `settled_amount` on receivables and payables. Verification now includes the restored minimal `src/test` regression set plus the manual API/data checklist.

**Tech Stack:** Spring Boot 3.5.x, Spring MVC, Spring Security, MyBatis-Plus, Flyway, Java 17

---

## File Map

**Create:**

- `src/main/resources/db/migration/V36__finance_settlement_cancel_metadata.sql`
- `src/main/java/com/tuowei/erp/finance/payment/web/PaymentCancelRequest.java`
- `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptCancelRequest.java`

**Modify:**

- `src/main/java/com/tuowei/erp/finance/payment/model/PaymentEntity.java`
- `src/main/java/com/tuowei/erp/finance/receipt/model/ReceiptEntity.java`
- `src/main/java/com/tuowei/erp/finance/payment/web/PaymentResponse.java`
- `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptResponse.java`
- `src/main/java/com/tuowei/erp/finance/payment/controller/PaymentController.java`
- `src/main/java/com/tuowei/erp/finance/receipt/controller/ReceiptController.java`
- `src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java`
- `src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java`

**Reference:**

- `docs/superpowers/specs/2026-05-18-finance-settlement-cancel-design.md`
- `src/main/resources/db/migration/V18__finance_receivable_payable_schema.sql`
- `src/main/java/com/tuowei/erp/common/exception/BusinessConflictException.java`
- `src/main/java/com/tuowei/erp/common/exception/OptimisticLockGuard.java`

---

### Task 1: Add Cancel Metadata Schema And DTO Contracts

**Files:**

- Create: `src/main/resources/db/migration/V36__finance_settlement_cancel_metadata.sql`
- Create: `src/main/java/com/tuowei/erp/finance/payment/web/PaymentCancelRequest.java`
- Create: `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptCancelRequest.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/model/PaymentEntity.java`
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/model/ReceiptEntity.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/web/PaymentResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptResponse.java`

- [ ] **Step 1: Add the Flyway migration for cancel metadata**

Create `src/main/resources/db/migration/V36__finance_settlement_cancel_metadata.sql`:

```sql
ALTER TABLE fin_payment
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER remark,
    ADD COLUMN cancelled_by BIGINT NULL AFTER cancel_reason,
    ADD COLUMN cancelled_time TIMESTAMP NULL AFTER cancelled_by;

ALTER TABLE fin_receipt
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER remark,
    ADD COLUMN cancelled_by BIGINT NULL AFTER cancel_reason,
    ADD COLUMN cancelled_time TIMESTAMP NULL AFTER cancelled_by;
```

- [ ] **Step 2: Extend payment and receipt entities**

Add these fields plus standard getters/setters in `PaymentEntity` and `ReceiptEntity`:

```java
private String cancelReason;
private Long cancelledBy;
private LocalDateTime cancelledTime;
```

The new accessors must follow the existing style:

```java
public String getCancelReason() { return cancelReason; }
public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
public Long getCancelledBy() { return cancelledBy; }
public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
public LocalDateTime getCancelledTime() { return cancelledTime; }
public void setCancelledTime(LocalDateTime cancelledTime) { this.cancelledTime = cancelledTime; }
```

- [ ] **Step 3: Add cancel request DTOs**

Create `PaymentCancelRequest.java`:

```java
package com.tuowei.erp.finance.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
        @NotBlank(message = "作废原因不能为空")
        @Size(max = 200, message = "作废原因长度不能超过200")
        String reason
) {
}
```

Create `ReceiptCancelRequest.java`:

```java
package com.tuowei.erp.finance.receipt.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceiptCancelRequest(
        @NotBlank(message = "作废原因不能为空")
        @Size(max = 200, message = "作废原因长度不能超过200")
        String reason
) {
}
```

- [ ] **Step 4: Extend response DTOs with cancel metadata**

Update `PaymentResponse` to:

```java
public record PaymentResponse(
        Long id,
        String paymentNo,
        Long supplierId,
        LocalDate paymentDate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String remark,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime cancelledTime,
        List<PaymentAllocationResponse> allocations
) {
}
```

Update `ReceiptResponse` to:

```java
public record ReceiptResponse(
        Long id,
        String receiptNo,
        Long customerId,
        LocalDate receiptDate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String remark,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime cancelledTime,
        List<ReceiptAllocationResponse> allocations
) {
}
```

- [ ] **Step 5: Run compile verification for schema/DTO changes**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -DskipTests compile
```

Expected:

- `BUILD SUCCESS`
- Flyway migration file is picked up during resource processing
- No constructor mismatch errors for `PaymentResponse` / `ReceiptResponse`

- [ ] **Step 6: Commit the contract layer**

Run:

```powershell
git add src/main/resources/db/migration/V36__finance_settlement_cancel_metadata.sql src/main/java/com/tuowei/erp/finance/payment/model/PaymentEntity.java src/main/java/com/tuowei/erp/finance/receipt/model/ReceiptEntity.java src/main/java/com/tuowei/erp/finance/payment/web/PaymentCancelRequest.java src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptCancelRequest.java src/main/java/com/tuowei/erp/finance/payment/web/PaymentResponse.java src/main/java/com/tuowei/erp/finance/receipt/web/ReceiptResponse.java
git commit -m "feat: add settlement cancel metadata contracts"
```

### Task 2: Add Payment Cancel Action

**Files:**

- Modify: `src/main/java/com/tuowei/erp/finance/payment/controller/PaymentController.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java`

- [ ] **Step 1: Add the payment cancel endpoint**

Update `PaymentController` imports and add:

```java
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
```

Then add this endpoint below `detail`:

```java
@PreAuthorize(PermissionCodes.HAS_FINANCE_PAYMENT_CREATE)
@PostMapping("/{id}/cancel")
@OperationLog(module = "finance", operation = "cancel-payment", message = "作废付款单", bizNo = "#id")
public ApiResponse<PaymentResponse> cancel(@PathVariable Long id, @Valid @RequestBody PaymentCancelRequest request) {
    return ApiResponse.success(paymentService.cancel(id, request));
}
```

- [ ] **Step 2: Add the payment cancel service method**

Add this method to `PaymentService`:

```java
@Transactional
public PaymentResponse cancel(Long id, PaymentCancelRequest request) {
    PaymentEntity payment = requirePayment(id);
    if ("CANCELLED".equals(payment.getStatus())) {
        return detail(id);
    }
    if (!"POSTED".equals(payment.getStatus())) {
        throw new IllegalArgumentException("只有已过账付款单可以作废");
    }

    AuditMetadata audit = auditMetadataFactory.current();
    LocalDateTime now = audit.now();
    String reason = request.reason().trim();
    List<PaymentAllocationEntity> allocations = allocationEntities(id);
    for (PaymentAllocationEntity allocation : allocations) {
        revertPayableAllocation(allocation, payment, audit, now);
    }

    payment.setStatus("CANCELLED");
    payment.setCancelReason(reason);
    payment.setCancelledBy(audit.userId());
    payment.setCancelledTime(now);
    payment.setUpdatedBy(audit.userId());
    payment.setUpdatedTime(now);
    OptimisticLockGuard.requireUpdated(paymentMapper.updateById(payment), "付款单已被其他操作修改，请刷新后重试");
    return detail(id);
}
```

- [ ] **Step 3: Add allocation query and payable rollback helpers**

Add these helpers to `PaymentService`:

```java
private List<PaymentAllocationEntity> allocationEntities(Long paymentId) {
    return paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>()
            .eq(PaymentAllocationEntity::getPaymentId, paymentId)
            .orderByAsc(PaymentAllocationEntity::getId));
}

private void revertPayableAllocation(
        PaymentAllocationEntity allocation,
        PaymentEntity payment,
        AuditMetadata audit,
        LocalDateTime now
) {
    PayableEntity payable = payableMapper.selectById(allocation.getPayableId());
    if (payable == null || payable.getDeletedFlag() == null || payable.getDeletedFlag() != 0
            || !Objects.equals(payable.getCompanyId(), payment.getCompanyId())) {
        throw new IllegalArgumentException("应付记录不存在");
    }
    BigDecimal allocationAmount = ScalePrecision.amount(allocation.getAmount());
    BigDecimal settledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(payable.getSettledAmount()));
    if (settledAmount.compareTo(allocationAmount) < 0) {
        throw new BusinessConflictException("应付已核销金额不足，无法作废当前付款单");
    }

    payable.setSettledAmount(ScalePrecision.amount(settledAmount.subtract(allocationAmount)));
    payable.setStatus(settlementStatus(payable.getOriginalAmount(), payable.getSettledAmount()));
    payable.setUpdatedBy(audit.userId());
    payable.setUpdatedTime(now);
    OptimisticLockGuard.requireUpdated(payableMapper.updateById(payable), "应付记录已被其他操作修改，请刷新后重试");
}
```

- [ ] **Step 4: Update payment response mapping**

Replace `toResponse` in `PaymentService` with:

```java
private PaymentResponse toResponse(PaymentEntity payment, List<PaymentAllocationResponse> allocations) {
    return new PaymentResponse(
            payment.getId(),
            payment.getPaymentNo(),
            payment.getSupplierId(),
            payment.getPaymentDate(),
            payment.getAmount(),
            payment.getAllocatedAmount(),
            payment.getStatus(),
            payment.getRemark(),
            payment.getCancelReason(),
            payment.getCancelledBy(),
            payment.getCancelledTime(),
            allocations
    );
}
```

- [ ] **Step 5: Run compile verification for payment cancel**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -DskipTests compile
```

Expected:

- `BUILD SUCCESS`
- No missing import or constructor errors in payment module

- [ ] **Step 6: Commit the payment cancel flow**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/payment/controller/PaymentController.java src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java
git commit -m "feat: add payment cancel flow"
```

### Task 3: Add Receipt Cancel Action

**Files:**

- Modify: `src/main/java/com/tuowei/erp/finance/receipt/controller/ReceiptController.java`
- Modify: `src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java`

- [ ] **Step 1: Add the receipt cancel endpoint**

Update `ReceiptController` imports and add:

```java
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
```

Then add:

```java
@PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIPT_CREATE)
@PostMapping("/{id}/cancel")
@OperationLog(module = "finance", operation = "cancel-receipt", message = "作废收款单", bizNo = "#id")
public ApiResponse<ReceiptResponse> cancel(@PathVariable Long id, @Valid @RequestBody ReceiptCancelRequest request) {
    return ApiResponse.success(receiptService.cancel(id, request));
}
```

- [ ] **Step 2: Add the receipt cancel service method**

Add this method to `ReceiptService`:

```java
@Transactional
public ReceiptResponse cancel(Long id, ReceiptCancelRequest request) {
    ReceiptEntity receipt = requireReceipt(id);
    if ("CANCELLED".equals(receipt.getStatus())) {
        return detail(id);
    }
    if (!"POSTED".equals(receipt.getStatus())) {
        throw new IllegalArgumentException("只有已过账收款单可以作废");
    }

    AuditMetadata audit = auditMetadataFactory.current();
    LocalDateTime now = audit.now();
    String reason = request.reason().trim();
    List<ReceiptAllocationEntity> allocations = allocationEntities(id);
    for (ReceiptAllocationEntity allocation : allocations) {
        revertReceivableAllocation(allocation, receipt, audit, now);
    }

    receipt.setStatus("CANCELLED");
    receipt.setCancelReason(reason);
    receipt.setCancelledBy(audit.userId());
    receipt.setCancelledTime(now);
    receipt.setUpdatedBy(audit.userId());
    receipt.setUpdatedTime(now);
    OptimisticLockGuard.requireUpdated(receiptMapper.updateById(receipt), "收款单已被其他操作修改，请刷新后重试");
    return detail(id);
}
```

- [ ] **Step 3: Add allocation query and receivable rollback helpers**

Add these helpers to `ReceiptService`:

```java
private List<ReceiptAllocationEntity> allocationEntities(Long receiptId) {
    return receiptAllocationMapper.selectList(new LambdaQueryWrapper<ReceiptAllocationEntity>()
            .eq(ReceiptAllocationEntity::getReceiptId, receiptId)
            .orderByAsc(ReceiptAllocationEntity::getId));
}

private void revertReceivableAllocation(
        ReceiptAllocationEntity allocation,
        ReceiptEntity receipt,
        AuditMetadata audit,
        LocalDateTime now
) {
    ReceivableEntity receivable = receivableMapper.selectById(allocation.getReceivableId());
    if (receivable == null || receivable.getDeletedFlag() == null || receivable.getDeletedFlag() != 0
            || !Objects.equals(receivable.getCompanyId(), receipt.getCompanyId())) {
        throw new IllegalArgumentException("应收记录不存在");
    }
    BigDecimal allocationAmount = ScalePrecision.amount(allocation.getAmount());
    BigDecimal settledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(receivable.getSettledAmount()));
    if (settledAmount.compareTo(allocationAmount) < 0) {
        throw new BusinessConflictException("应收已核销金额不足，无法作废当前收款单");
    }

    receivable.setSettledAmount(ScalePrecision.amount(settledAmount.subtract(allocationAmount)));
    receivable.setStatus(settlementStatus(receivable.getOriginalAmount(), receivable.getSettledAmount()));
    receivable.setUpdatedBy(audit.userId());
    receivable.setUpdatedTime(now);
    OptimisticLockGuard.requireUpdated(receivableMapper.updateById(receivable), "应收记录已被其他操作修改，请刷新后重试");
}
```

- [ ] **Step 4: Update receipt response mapping**

Replace `toResponse` in `ReceiptService` with:

```java
private ReceiptResponse toResponse(ReceiptEntity receipt, List<ReceiptAllocationResponse> allocations) {
    return new ReceiptResponse(
            receipt.getId(),
            receipt.getReceiptNo(),
            receipt.getCustomerId(),
            receipt.getReceiptDate(),
            receipt.getAmount(),
            receipt.getAllocatedAmount(),
            receipt.getStatus(),
            receipt.getRemark(),
            receipt.getCancelReason(),
            receipt.getCancelledBy(),
            receipt.getCancelledTime(),
            allocations
    );
}
```

- [ ] **Step 5: Run compile verification for receipt cancel**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -DskipTests compile
```

Expected:

- `BUILD SUCCESS`
- No missing import or constructor errors in receipt module

- [ ] **Step 6: Commit the receipt cancel flow**

Run:

```powershell
git add src/main/java/com/tuowei/erp/finance/receipt/controller/ReceiptController.java src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java
git commit -m "feat: add receipt cancel flow"
```

### Task 4: Full Verification And Manual API Checklist

**Files:**

- No production source changes required

- [ ] **Step 1: Run full package verification**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package
```

Expected:

- `BUILD SUCCESS`
- Restored minimal test suite runs and passes.
- `target/erp-server-1.0.0.jar` exists

- [ ] **Step 2: Run payment cancel manual checks**

Use a valid login token and a dev/pre-production database:

- Create one payable with remaining amount greater than zero.
- Create one payment allocating against that payable.
- Call:

```http
POST /api/finance/payments/{id}/cancel
Content-Type: application/json

{"reason":"录入金额错误"}
```

Expected:

- Response `status = "CANCELLED"`
- Response contains `cancelReason`, `cancelledBy`, `cancelledTime`
- Linked payable `settled_amount` decreases by the allocation amount
- Linked payable status recomputes to `UNSETTLED` or `PARTIALLY_SETTLED`

- [ ] **Step 3: Run payment idempotency/conflict checks**

Repeat the same cancel request:

```http
POST /api/finance/payments/{id}/cancel
Content-Type: application/json

{"reason":"重复提交"}
```

Expected:

- Response still returns the same cancelled payment
- No second rollback happens

Then send invalid payloads:

```http
POST /api/finance/payments/{id}/cancel
Content-Type: application/json

{"reason":"   "}
```

Expected:

- Parameter validation failure with `作废原因不能为空`

- [ ] **Step 4: Run receipt cancel manual checks**

- Create one receivable with remaining amount greater than zero.
- Create one receipt allocating against that receivable.
- Call:

```http
POST /api/finance/receipts/{id}/cancel
Content-Type: application/json

{"reason":"收款录入错误"}
```

Expected:

- Response `status = "CANCELLED"`
- Response contains `cancelReason`, `cancelledBy`, `cancelledTime`
- Linked receivable `settled_amount` decreases by the allocation amount
- Linked receivable status recomputes to `UNSETTLED` or `PARTIALLY_SETTLED`

- [ ] **Step 5: Commit the verification pass**

Run:

```powershell
git add src/main/resources/db/migration/V36__finance_settlement_cancel_metadata.sql src/main/java/com/tuowei/erp/finance/payment src/main/java/com/tuowei/erp/finance/receipt
git commit -m "feat: support receipt and payment cancel"
```

---

## Self-Review

**Spec coverage:** Covers cancel endpoints, cancel reason validation, cancel metadata persistence, idempotent repeated cancel calls, rollback of receivable/payable settled amounts, status recomputation, and build/manual verification.

**Placeholder scan:** This plan contains no `TODO`, `TBD`, vague “handle later” steps, or undefined endpoint names.

**Type consistency:** `PaymentCancelRequest` / `ReceiptCancelRequest`, `cancelReason` / `cancelledBy` / `cancelledTime`, `cancel(id, request)` methods, and `CANCELLED` status are used consistently across schema, DTOs, controllers, services, and verification steps.
