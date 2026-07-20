# Manual Voucher Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace destructive manual-voucher cancellation with auditable reversal vouchers, including cancel reason capture and UI visibility.

**Architecture:** Keep the existing manual-voucher lifecycle and shared ledger tables. Add reversal metadata to `fin_manual_voucher`, create `MANUAL_REVERSAL` rows in `fin_voucher`, create debit/credit-swapped rows in `fin_voucher_entry`, and expose those links through the existing manual-voucher API/page.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway SQL migrations, JUnit 5, Mockito, AssertJ, Vue 3, Element Plus, TypeScript.

---

## File Map

- Modify: `src/main/resources/db/migration/V86__finance_manual_voucher.sql`
  - Fix misleading comments for fresh databases.
- Create: `src/main/resources/db/migration/V89__manual_voucher_reversal_metadata.sql`
  - Add `reversal_voucher_id` and `cancel_reason` for upgraded databases.
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/model/ManualVoucherEntity.java`
  - Add Java fields and getters/setters for new columns.
- Create: `src/main/java/com/tuowei/erp/finance/voucher/web/ManualVoucherCancelRequest.java`
  - Request body for required cancel reason.
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/web/ManualVoucherResponse.java`
  - Return reversal voucher ID and cancel reason.
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/controller/ManualVoucherController.java`
  - Accept cancel request body and pass the reason to the service.
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/service/ManualVoucherService.java`
  - Replace deletion-based cancellation with reversal-voucher creation.
- Create: `src/test/java/com/tuowei/erp/finance/voucher/ManualVoucherServiceTest.java`
  - Unit tests for posting, cancellation, validation, and tenant scoping.
- Modify: `../erp-frontend/src/api/finance.ts`
  - Add `reversalVoucherId`, `cancelReason`, and `cancelManualVoucher(id, reason)`.
- Modify: `../erp-frontend/src/views/finance/vouchers/manual/index.vue`
  - Add required cancel reason dialog and detail fields.

## Task 1: Backend Contract And Schema

**Files:**
- Modify: `src/main/resources/db/migration/V86__finance_manual_voucher.sql`
- Create: `src/main/resources/db/migration/V89__manual_voucher_reversal_metadata.sql`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/model/ManualVoucherEntity.java`
- Create: `src/main/java/com/tuowei/erp/finance/voucher/web/ManualVoucherCancelRequest.java`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/web/ManualVoucherResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/controller/ManualVoucherController.java`

- [ ] **Step 1: Add Flyway migration for upgraded databases**

Create `src/main/resources/db/migration/V89__manual_voucher_reversal_metadata.sql`:

```sql
-- 手工凭证作废改为红冲后，需要保留红冲凭证与作废原因。
ALTER TABLE fin_manual_voucher
    ADD COLUMN reversal_voucher_id BIGINT NULL AFTER posted_voucher_id,
    ADD COLUMN cancel_reason VARCHAR(512) NULL AFTER reject_reason;
```

- [ ] **Step 2: Update fresh-schema migration comments and columns**

In `src/main/resources/db/migration/V86__finance_manual_voucher.sql`, change the status-machine comment to:

```sql
-- 状态机：DRAFT →(提交)→ PENDING →(审批)→ APPROVED →(过账)→ POSTED；
--          PENDING →(驳回)→ DRAFT；POSTED →(作废红冲)→ CANCELLED。
```

In the `fin_manual_voucher` table definition, keep `posted_voucher_id` and add `reversal_voucher_id` immediately after it:

```sql
    -- 过账后回填共享凭证 id；作废时不会删除该凭证和分录
    posted_voucher_id BIGINT,
    -- 作废后回填红冲凭证 id，红冲凭证写入 fin_voucher / fin_voucher_entry
    reversal_voucher_id BIGINT,
```

Add `cancel_reason` after `reject_reason`:

```sql
    reject_reason VARCHAR(512),
    cancel_reason VARCHAR(512),
```

- [ ] **Step 3: Extend `ManualVoucherEntity`**

In `src/main/java/com/tuowei/erp/finance/voucher/model/ManualVoucherEntity.java`, add fields:

```java
    private Long reversalVoucherId;
    private String cancelReason;
```

Add getters/setters near the existing posted/cancel fields:

```java
    public Long getReversalVoucherId() { return reversalVoucherId; }
    public void setReversalVoucherId(Long reversalVoucherId) { this.reversalVoucherId = reversalVoucherId; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
```

- [ ] **Step 4: Add cancel request DTO**

Create `src/main/java/com/tuowei/erp/finance/voucher/web/ManualVoucherCancelRequest.java`:

```java
package com.tuowei.erp.finance.voucher.web;

import jakarta.validation.constraints.NotBlank;

public record ManualVoucherCancelRequest(
        @NotBlank(message = "作废原因不能为空") String reason
) {
}
```

- [ ] **Step 5: Extend response DTO**

Change `src/main/java/com/tuowei/erp/finance/voucher/web/ManualVoucherResponse.java` so the record includes `reversalVoucherId` and `cancelReason` after `postedVoucherId`:

```java
public record ManualVoucherResponse(
        Long id,
        String voucherNo,
        LocalDate bizDate,
        BigDecimal amount,
        String status,
        String remark,
        Long postedVoucherId,
        Long reversalVoucherId,
        String rejectReason,
        String cancelReason,
        LocalDateTime submittedTime,
        LocalDateTime approvedTime,
        LocalDateTime postedTime,
        LocalDateTime cancelledTime,
        LocalDateTime createdTime,
        List<ManualVoucherLineResponse> lines
) {
}
```

- [ ] **Step 6: Update controller cancel endpoint**

In `src/main/java/com/tuowei/erp/finance/voucher/controller/ManualVoucherController.java`, add import:

```java
import com.tuowei.erp.finance.voucher.web.ManualVoucherCancelRequest;
```

Change the endpoint:

```java
    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_POST)
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @Valid @RequestBody ManualVoucherCancelRequest request) {
        manualVoucherService.cancel(id, request.reason());
        return ApiResponse.success(null);
    }
```

- [ ] **Step 7: Run focused backend compilation test**

Run:

```powershell
.\mvnw.cmd -B -DskipTests compile
```

Expected: compilation fails until `ManualVoucherService.cancel(Long, String)` and `ManualVoucherResponse` construction are updated in Task 2. The relevant failures should mention the old `cancel(Long)` signature or `ManualVoucherResponse` constructor argument mismatch.

## Task 2: Backend Reversal Behavior

**Files:**
- Create: `src/test/java/com/tuowei/erp/finance/voucher/ManualVoucherServiceTest.java`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/service/ManualVoucherService.java`

- [ ] **Step 1: Write failing service tests**

Create `src/test/java/com/tuowei/erp/finance/voucher/ManualVoucherServiceTest.java`:

```java
package com.tuowei.erp.finance.voucher;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.service.ManualVoucherService;
import com.tuowei.erp.finance.voucher.web.ManualVoucherLineRequest;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualVoucherServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            1L,
            10L,
            LocalDateTime.of(2026, 7, 7, 9, 30)
    );

    private final ManualVoucherMapper manualVoucherMapper = mock(ManualVoucherMapper.class);
    private final ManualVoucherLineMapper manualVoucherLineMapper = mock(ManualVoucherLineMapper.class);
    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final AccountPeriodGuard accountPeriodGuard = mock(AccountPeriodGuard.class);
    private final SequenceNumberGenerator sequenceNumberGenerator = mock(SequenceNumberGenerator.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ManualVoucherEntity.class);
        initTableInfo(ManualVoucherLineEntity.class);
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void createRejectsUnbalancedLines() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        ManualVoucherSaveRequest request = new ManualVoucherSaveRequest(
                LocalDate.of(2026, 7, 1),
                "bad",
                List.of(
                        new ManualVoucherLineRequest(101L, new BigDecimal("100.00"), BigDecimal.ZERO, "借"),
                        new ManualVoucherLineRequest(102L, BigDecimal.ZERO, new BigDecimal("90.00"), "贷")
                )
        );

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("借贷金额不平衡");
    }

    @Test
    void postCreatesPostedVoucherAndEntries() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "APPROVED", AUDIT.companyId(), AUDIT.accountBookId());
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(manualVoucherLineMapper.selectList(any())).thenReturn(manualLines(1001L));

        service().post(1001L);

        verify(accountPeriodGuard).requireOpen(LocalDate.of(2026, 7, 1), "手工凭证过账");

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        VoucherEntity posted = voucherCaptor.getValue();
        assertThat(posted.getSourceType()).isEqualTo("MANUAL");
        assertThat(posted.getSourceId()).isEqualTo(1001L);
        assertThat(posted.getStatus()).isEqualTo("POSTED");

        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, org.mockito.Mockito.times(2)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues())
                .extracting(VoucherEntryEntity::getVoucherId)
                .containsOnly(posted.getId());

        assertThat(manual.getStatus()).isEqualTo("POSTED");
        assertThat(manual.getPostedVoucherId()).isEqualTo(posted.getId());
        verify(manualVoucherMapper).updateById(manual);
    }

    @Test
    void cancelRequiresReason() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        assertThatThrownBy(() -> service().cancel(1001L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("作废原因不能为空");
    }

    @Test
    void cancelCreatesReversalVoucherAndKeepsOriginalEntries() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);

        VoucherEntity original = postedVoucher(2001L, "MV202607010001");
        when(voucherMapper.selectById(2001L)).thenReturn(original);
        when(voucherEntryMapper.selectList(any())).thenReturn(originalEntries(2001L));

        service().cancel(1001L, "录入错误");

        verify(accountPeriodGuard).requireOpen(LocalDate.of(2026, 7, 7), "手工凭证作废");
        verify(voucherEntryMapper, never()).delete(any());

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        VoucherEntity reversal = voucherCaptor.getValue();
        assertThat(reversal.getSourceType()).isEqualTo("MANUAL_REVERSAL");
        assertThat(reversal.getSourceId()).isEqualTo(1001L);
        assertThat(reversal.getSourceNo()).isEqualTo("MV202607010001");
        assertThat(reversal.getBizDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(reversal.getStatus()).isEqualTo("POSTED");
        assertThat(reversal.getRemark()).contains("红冲").contains("录入错误").contains("MV202607010001");

        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, org.mockito.Mockito.times(2)).insert(entryCaptor.capture());
        List<VoucherEntryEntity> reversalEntries = entryCaptor.getAllValues();
        assertThat(reversalEntries.get(0).getDebitAmount()).isEqualByComparingTo("0.00");
        assertThat(reversalEntries.get(0).getCreditAmount()).isEqualByComparingTo("100.00");
        assertThat(reversalEntries.get(1).getDebitAmount()).isEqualByComparingTo("100.00");
        assertThat(reversalEntries.get(1).getCreditAmount()).isEqualByComparingTo("0.00");
        assertThat(reversalEntries).extracting(VoucherEntryEntity::getSummary).allMatch(summary -> summary.startsWith("红冲:"));

        assertThat(manual.getStatus()).isEqualTo("CANCELLED");
        assertThat(manual.getReversalVoucherId()).isEqualTo(reversal.getId());
        assertThat(manual.getCancelReason()).isEqualTo("录入错误");
        assertThat(manual.getCancelledBy()).isEqualTo(AUDIT.userId());
        assertThat(manual.getCancelledTime()).isEqualTo(AUDIT.now());
        verify(manualVoucherMapper).updateById(manual);
    }

    @Test
    void cancelRejectsNonPostedVoucher() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manualVoucher(1001L, "APPROVED", AUDIT.companyId(), AUDIT.accountBookId()));

        assertThatThrownBy(() -> service().cancel(1001L, "不该作废"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有已过账的手工凭证可以作废");
    }

    @Test
    void cancelUsesPeriodGuardForCancelDate() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        doThrow(new BusinessConflictException("期间已结账"))
                .when(accountPeriodGuard).requireOpen(LocalDate.of(2026, 7, 7), "手工凭证作废");

        assertThatThrownBy(() -> service().cancel(1001L, "期间关闭"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("期间已结账");

        verify(voucherMapper, never()).insert(any());
        verify(voucherEntryMapper, never()).insert(any());
    }

    @Test
    void detailReturnsReversalAndCancelReason() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "CANCELLED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        manual.setReversalVoucherId(3001L);
        manual.setCancelReason("录入错误");
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(manualVoucherLineMapper.selectList(any())).thenReturn(manualLines(1001L));

        var response = service().detail(1001L);

        assertThat(response.postedVoucherId()).isEqualTo(2001L);
        assertThat(response.reversalVoucherId()).isEqualTo(3001L);
        assertThat(response.cancelReason()).isEqualTo("录入错误");
    }

    @Test
    void requireVoucherRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manualVoucher(1001L, "POSTED", AUDIT.companyId(), 99L));

        assertThatThrownBy(() -> service().detail(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手工凭证不存在");
    }

    private ManualVoucherService service() {
        return new ManualVoucherService(
                manualVoucherMapper,
                manualVoucherLineMapper,
                voucherMapper,
                voucherEntryMapper,
                accountSubjectMapper,
                accountPeriodGuard,
                sequenceNumberGenerator,
                auditMetadataFactory
        );
    }

    private ManualVoucherEntity manualVoucher(Long id, String status, Long companyId, Long accountBookId) {
        ManualVoucherEntity voucher = new ManualVoucherEntity();
        voucher.setId(id);
        voucher.setCompanyId(companyId);
        voucher.setAccountBookId(accountBookId);
        voucher.setVoucherNo("MV202607010001");
        voucher.setBizDate(LocalDate.of(2026, 7, 1));
        voucher.setAmount(new BigDecimal("100.00"));
        voucher.setStatus(status);
        voucher.setRemark("手工凭证");
        voucher.setDeletedFlag(0);
        return voucher;
    }

    private VoucherEntity postedVoucher(Long id, String voucherNo) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setId(id);
        voucher.setCompanyId(AUDIT.companyId());
        voucher.setAccountBookId(AUDIT.accountBookId());
        voucher.setVoucherNo(voucherNo);
        voucher.setSourceType("MANUAL");
        voucher.setSourceId(1001L);
        voucher.setSourceNo(voucherNo);
        voucher.setBizDate(LocalDate.of(2026, 7, 1));
        voucher.setAmount(new BigDecimal("100.00"));
        voucher.setStatus("POSTED");
        voucher.setDeletedFlag(0);
        return voucher;
    }

    private List<ManualVoucherLineEntity> manualLines(Long manualVoucherId) {
        ManualVoucherLineEntity debit = manualLine(manualVoucherId, 1, 101L, "1001", "现金", "100.00", "0.00");
        ManualVoucherLineEntity credit = manualLine(manualVoucherId, 2, 201L, "2001", "应付", "0.00", "100.00");
        return List.of(debit, credit);
    }

    private ManualVoucherLineEntity manualLine(Long manualVoucherId, int lineNo, Long subjectId, String subjectCode, String subjectName, String debit, String credit) {
        ManualVoucherLineEntity line = new ManualVoucherLineEntity();
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setVoucherId(manualVoucherId);
        line.setLineNo(lineNo);
        line.setSubjectId(subjectId);
        line.setSubjectCode(subjectCode);
        line.setSubjectName(subjectName);
        line.setDebitAmount(new BigDecimal(debit));
        line.setCreditAmount(new BigDecimal(credit));
        line.setSummary("line-" + lineNo);
        line.setDeletedFlag(0);
        return line;
    }

    private List<VoucherEntryEntity> originalEntries(Long voucherId) {
        return List.of(
                entry(voucherId, 1, 101L, "1001", "现金", "100.00", "0.00"),
                entry(voucherId, 2, 201L, "2001", "应付", "0.00", "100.00")
        );
    }

    private VoucherEntryEntity entry(Long voucherId, int lineNo, Long subjectId, String subjectCode, String subjectName, String debit, String credit) {
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setCompanyId(AUDIT.companyId());
        entry.setAccountBookId(AUDIT.accountBookId());
        entry.setVoucherId(voucherId);
        entry.setBizDate(LocalDate.of(2026, 7, 1));
        entry.setLineNo(lineNo);
        entry.setSubjectId(subjectId);
        entry.setSubjectCode(subjectCode);
        entry.setSubjectName(subjectName);
        entry.setDebitAmount(new BigDecimal(debit));
        entry.setCreditAmount(new BigDecimal(credit));
        entry.setSummary("line-" + lineNo);
        return entry;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
```

- [ ] **Step 2: Run failing tests**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ManualVoucherServiceTest test
```

Expected: FAIL. Compilation/test failures should point to missing `cancel(Long, String)`, missing `reversalVoucherId` / `cancelReason`, or current deletion-based cancellation.

- [ ] **Step 3: Add constants and response fields in service**

In `src/main/java/com/tuowei/erp/finance/voucher/service/ManualVoucherService.java`, add constants:

```java
    private static final String REVERSAL_SOURCE_TYPE = "MANUAL_REVERSAL";
```

In `toResponse(...)`, construct `ManualVoucherResponse` with the new fields:

```java
                voucher.getPostedVoucherId(),
                voucher.getReversalVoucherId(),
                voucher.getRejectReason(),
                voucher.getCancelReason(),
```

- [ ] **Step 4: Replace destructive cancel implementation**

Replace `ManualVoucherService.cancel(Long id)` with:

```java
    @Transactional
    public void cancel(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("作废原因不能为空");
        }
        String cancelReason = reason.trim();
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_POSTED.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有已过账的手工凭证可以作废");
        }
        accountPeriodGuard.requireOpen(now.toLocalDate(), "手工凭证作废");

        VoucherEntity originalVoucher = requirePostedVoucher(voucher, audit);
        List<VoucherEntryEntity> originalEntries = loadVoucherEntries(originalVoucher.getId(), audit);
        if (originalEntries.isEmpty()) {
            throw new BusinessConflictException("手工凭证原始凭证缺少分录，无法作废");
        }

        VoucherEntity reversalVoucher = insertReversalVoucher(voucher, originalVoucher, cancelReason, audit, now);
        insertReversalEntries(reversalVoucher, originalEntries, audit, now);

        voucher.setStatus(STATUS_CANCELLED);
        voucher.setReversalVoucherId(reversalVoucher.getId());
        voucher.setCancelReason(cancelReason);
        voucher.setCancelledBy(audit.userId());
        voucher.setCancelledTime(now);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        manualVoucherMapper.updateById(voucher);
    }
```

Add helper methods below `post(...)`:

```java
    private VoucherEntity requirePostedVoucher(ManualVoucherEntity manualVoucher, AuditMetadata audit) {
        Long postedVoucherId = manualVoucher.getPostedVoucherId();
        if (postedVoucherId == null) {
            throw new BusinessConflictException("手工凭证缺少原始过账凭证，无法作废");
        }
        VoucherEntity posted = voucherMapper.selectById(postedVoucherId);
        if (posted == null
                || !Objects.equals(posted.getCompanyId(), audit.companyId())
                || !Objects.equals(posted.getAccountBookId(), audit.accountBookId())
                || posted.getDeletedFlag() == null || posted.getDeletedFlag() != 0
                || !POSTED_SOURCE_TYPE.equals(posted.getSourceType())
                || !Objects.equals(posted.getSourceId(), manualVoucher.getId())) {
            throw new BusinessConflictException("手工凭证原始过账凭证不存在，无法作废");
        }
        return posted;
    }

    private List<VoucherEntryEntity> loadVoucherEntries(Long voucherId, AuditMetadata audit) {
        return voucherEntryMapper.selectList(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucherId)
                .orderByAsc(VoucherEntryEntity::getLineNo));
    }

    private VoucherEntity insertReversalVoucher(
            ManualVoucherEntity manualVoucher,
            VoucherEntity originalVoucher,
            String cancelReason,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        VoucherEntity reversal = new VoucherEntity();
        reversal.setCompanyId(audit.companyId());
        reversal.setAccountBookId(audit.accountBookId());
        reversal.setVoucherNo(manualVoucher.getVoucherNo() + "-REV");
        reversal.setSourceType(REVERSAL_SOURCE_TYPE);
        reversal.setSourceId(manualVoucher.getId());
        reversal.setSourceNo(manualVoucher.getVoucherNo());
        reversal.setBizDate(now.toLocalDate());
        reversal.setAmount(originalVoucher.getAmount());
        reversal.setStatus("POSTED");
        reversal.setDeletedFlag(0);
        reversal.setRemark("手工凭证红冲: " + originalVoucher.getVoucherNo() + "，原因: " + cancelReason);
        reversal.setCreatedBy(audit.userId());
        reversal.setCreatedTime(now);
        reversal.setUpdatedBy(audit.userId());
        reversal.setUpdatedTime(now);
        reversal.setVersion(0);
        voucherMapper.insert(reversal);
        return reversal;
    }

    private void insertReversalEntries(
            VoucherEntity reversalVoucher,
            List<VoucherEntryEntity> originalEntries,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        for (VoucherEntryEntity originalEntry : originalEntries) {
            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setCompanyId(audit.companyId());
            entry.setAccountBookId(audit.accountBookId());
            entry.setVoucherId(reversalVoucher.getId());
            entry.setBizDate(reversalVoucher.getBizDate());
            entry.setLineNo(originalEntry.getLineNo());
            entry.setSubjectId(originalEntry.getSubjectId());
            entry.setSubjectCode(originalEntry.getSubjectCode());
            entry.setSubjectName(originalEntry.getSubjectName());
            entry.setDebitAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(originalEntry.getCreditAmount())));
            entry.setCreditAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(originalEntry.getDebitAmount())));
            entry.setSummary("红冲: " + originalEntry.getSummary());
            entry.setCreatedBy(audit.userId());
            entry.setCreatedTime(now);
            entry.setUpdatedBy(audit.userId());
            entry.setUpdatedTime(now);
            entry.setVersion(0);
            voucherEntryMapper.insert(entry);
        }
    }
```

- [ ] **Step 5: Run focused tests**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ManualVoucherServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Run voucher package tests**

Run:

```powershell
.\mvnw.cmd -B -Dtest=ManualVoucherServiceTest,VoucherQueryServiceTest test
```

Expected: PASS.

## Task 3: Frontend Cancel Reason And Detail Visibility

**Files:**
- Modify: `../erp-frontend/src/api/finance.ts`
- Modify: `../erp-frontend/src/views/finance/vouchers/manual/index.vue`

- [ ] **Step 1: Update frontend API types**

In `../erp-frontend/src/api/finance.ts`, extend `ManualVoucher`:

```ts
  postedVoucherId?: string
  reversalVoucherId?: string
  rejectReason?: string
  cancelReason?: string
```

Update `normalizeManualVoucher`:

```ts
const normalizeManualVoucher = (voucher: ManualVoucher): ManualVoucher => ({
  ...voucher,
  id: String(voucher.id),
  postedVoucherId: voucher.postedVoucherId != null ? String(voucher.postedVoucherId) : undefined,
  reversalVoucherId: voucher.reversalVoucherId != null ? String(voucher.reversalVoucherId) : undefined,
  amount: Number(voucher.amount ?? 0),
  lines: (voucher.lines || []).map(normalizeManualVoucherLine)
})
```

Change `cancelManualVoucher`:

```ts
export const cancelManualVoucher = (id: string | number, reason: string) => {
  return request.post<void>(`/finance/vouchers/manual/${id}/cancel`, { reason })
}
```

- [ ] **Step 2: Add cancel dialog state**

In `../erp-frontend/src/views/finance/vouchers/manual/index.vue`, add refs near the reject dialog state:

```ts
const cancelVisible = ref(false)
const cancelling = ref(false)
const cancelReason = ref('')
const cancellingRow = ref<ManualVoucher | null>(null)
```

Replace the current `handleCancel(row)` function with:

```ts
const openCancel = (row: ManualVoucher) => {
  cancellingRow.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

const handleCancel = async () => {
  if (!cancellingRow.value || !cancelReason.value.trim()) return
  cancelling.value = true
  try {
    await cancelManualVoucher(cancellingRow.value.id, cancelReason.value.trim())
    ElMessage.success('已作废并生成红冲凭证')
    cancelVisible.value = false
    loadData()
  } catch {
    ElMessage.error('作废失败')
  } finally {
    cancelling.value = false
  }
}
```

Update the table action button:

```vue
            <el-button
              v-if="row.status === 'POSTED'"
              v-permission="'finance:voucher:post'"
              link
              type="danger"
              @click="openCancel(row)"
            >作废</el-button>
```

- [ ] **Step 3: Add cancel reason dialog to template**

Place this block after the reject dialog:

```vue
    <!-- 作废原因弹窗 -->
    <el-dialog v-model="cancelVisible" title="作废凭证" width="520px">
      <el-alert
        v-if="cancellingRow"
        :title="`作废将为凭证 ${cancellingRow.voucherNo} 生成红冲凭证，原始分录会保留。`"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="90px">
        <el-form-item label="作废原因" required>
          <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="请填写作废原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="cancelling"
          :disabled="!cancelReason.trim()"
          @click="handleCancel"
        >
          确定作废
        </el-button>
      </template>
    </el-dialog>
```

- [ ] **Step 4: Show reversal metadata in detail dialog**

Inside the existing `<el-descriptions>` in the detail dialog, after amount/status fields, add:

```vue
          <el-descriptions-item v-if="currentVoucher.postedVoucherId" label="过账凭证ID">
            {{ currentVoucher.postedVoucherId }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.reversalVoucherId" label="红冲凭证ID">
            {{ currentVoucher.reversalVoucherId }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.cancelReason" label="作废原因" :span="2">
            {{ currentVoucher.cancelReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.submittedTime" label="提交时间">
            {{ currentVoucher.submittedTime }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.approvedTime" label="审批时间">
            {{ currentVoucher.approvedTime }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.postedTime" label="过账时间">
            {{ currentVoucher.postedTime }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.cancelledTime" label="作废时间">
            {{ currentVoucher.cancelledTime }}
          </el-descriptions-item>
```

- [ ] **Step 5: Run frontend focused checks**

Run in `E:\tuowei\python\erp-frontend`:

```powershell
npm run type-check
npm run lint
```

Expected: both PASS.

## Task 4: Full Verification And Commit

**Files:**
- Verify all files changed in Tasks 1-3.
- Commit only manual-voucher related changes.

- [ ] **Step 1: Run backend full test suite**

Run in `E:\tuowei\python\erpServer`:

```powershell
.\mvnw.cmd -B test
```

Expected: PASS.

- [ ] **Step 2: Run frontend full checks**

Run in `E:\tuowei\python\erp-frontend`:

```powershell
npm run type-check
npm run lint
npm run build
```

Expected: all PASS.

- [ ] **Step 3: Inspect scoped git diff**

Run in `E:\tuowei\python\erpServer`:

```powershell
git status --short
git diff -- src/main/resources/db/migration/V86__finance_manual_voucher.sql src/main/resources/db/migration/V89__manual_voucher_reversal_metadata.sql src/main/java/com/tuowei/erp/finance/voucher src/test/java/com/tuowei/erp/finance/voucher
```

Run in `E:\tuowei\python\erp-frontend`:

```powershell
git status --short
git diff -- src/api/finance.ts src/views/finance/vouchers/manual/index.vue
```

Expected: diffs only cover manual-voucher closure work. Existing unrelated dirty files remain untouched.

- [ ] **Step 4: Commit backend manual-voucher changes**

Run in `E:\tuowei\python\erpServer`:

```powershell
git add -- src/main/resources/db/migration/V86__finance_manual_voucher.sql src/main/resources/db/migration/V89__manual_voucher_reversal_metadata.sql src/main/java/com/tuowei/erp/finance/voucher src/test/java/com/tuowei/erp/finance/voucher
git commit -m "feat: add manual voucher reversal cancellation"
```

Expected: commit succeeds. If unrelated pre-existing files are already staged, stop and unstage only those unrelated paths before committing.

- [ ] **Step 5: Commit frontend manual-voucher changes**

Run in `E:\tuowei\python\erp-frontend`:

```powershell
git add -- src/api/finance.ts src/views/finance/vouchers/manual/index.vue
git commit -m "feat: require manual voucher cancel reason"
```

Expected: commit succeeds. If frontend repository has unrelated staged files, stop and unstage only those unrelated paths before committing.

## Self-Review

- Spec coverage:
  - Red reversal cancellation is implemented in Task 2.
  - Schema metadata is implemented in Task 1.
  - API request/response contract is implemented in Tasks 1 and 3.
  - Frontend cancel reason and detail visibility are implemented in Task 3.
  - Verification commands are listed in Task 4.
- Placeholder scan:
  - No placeholder markers or open-ended “handle later” requirements remain.
- Type consistency:
  - Backend uses `reversalVoucherId` / `cancelReason` consistently in entity, response, service, and frontend type normalization.
  - Cancel method signature is consistently `cancel(Long id, String reason)` in controller, service, and tests.
