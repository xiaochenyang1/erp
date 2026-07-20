package com.tuowei.erp.finance.voucher;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
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
import org.springframework.dao.DuplicateKeyException;

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
import static org.mockito.Mockito.times;
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
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity posted = invocation.getArgument(0);
            posted.setId(2001L);
            return 1;
        });

        service().post(1001L);

        verify(accountPeriodGuard).requireOpen(LocalDate.of(2026, 7, 1), "手工凭证过账");
        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        VoucherEntity posted = voucherCaptor.getValue();
        assertThat(posted.getSourceType()).isEqualTo("MANUAL");
        assertThat(posted.getSourceId()).isEqualTo(1001L);
        assertThat(posted.getStatus()).isEqualTo("POSTED");

        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues())
                .extracting(VoucherEntryEntity::getVoucherId)
                .containsOnly(2001L);

        assertThat(manual.getStatus()).isEqualTo("POSTED");
        assertThat(manual.getPostedVoucherId()).isEqualTo(2001L);
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
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherEntryMapper.selectList(any())).thenReturn(originalEntries(2001L));
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity reversal = invocation.getArgument(0);
            reversal.setId(3001L);
            return 1;
        });
        when(manualVoucherMapper.updateById(any(ManualVoucherEntity.class))).thenReturn(1);

        service().cancel(1001L, "  录入错误  ");

        verify(accountPeriodGuard).requireOpen(LocalDate.of(2026, 7, 7), "手工凭证作废");
        verify(voucherEntryMapper, never()).delete(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<VoucherEntryEntity>>any());
        verify(voucherMapper, never()).updateById(any(VoucherEntity.class));

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        VoucherEntity reversal = voucherCaptor.getValue();
        assertThat(reversal.getVoucherNo()).isEqualTo("MV202607010001-REV");
        assertThat(reversal.getSourceType()).isEqualTo("MANUAL_REVERSAL");
        assertThat(reversal.getSourceId()).isEqualTo(1001L);
        assertThat(reversal.getSourceNo()).isEqualTo("MV202607010001");
        assertThat(reversal.getBizDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(reversal.getAmount()).isEqualByComparingTo("100.00");
        assertThat(reversal.getStatus()).isEqualTo("POSTED");
        assertThat(reversal.getDeletedFlag()).isZero();
        assertThat(reversal.getRemark()).contains("红冲", "录入错误", "MV202607010001");

        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entryCaptor.capture());
        List<VoucherEntryEntity> reversalEntries = entryCaptor.getAllValues();
        assertThat(reversalEntries)
                .extracting(VoucherEntryEntity::getVoucherId)
                .containsOnly(3001L);
        assertThat(reversalEntries.get(0).getLineNo()).isEqualTo(1);
        assertThat(reversalEntries.get(0).getSubjectCode()).isEqualTo("1001");
        assertThat(reversalEntries.get(0).getDebitAmount()).isEqualByComparingTo("0.00");
        assertThat(reversalEntries.get(0).getCreditAmount()).isEqualByComparingTo("100.00");
        assertThat(reversalEntries.get(0).getBizDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(reversalEntries.get(1).getLineNo()).isEqualTo(2);
        assertThat(reversalEntries.get(1).getSubjectCode()).isEqualTo("2001");
        assertThat(reversalEntries.get(1).getDebitAmount()).isEqualByComparingTo("100.00");
        assertThat(reversalEntries.get(1).getCreditAmount()).isEqualByComparingTo("0.00");
        assertThat(reversalEntries).extracting(VoucherEntryEntity::getSummary).allMatch(summary -> summary.startsWith("红冲:"));

        assertThat(manual.getStatus()).isEqualTo("CANCELLED");
        assertThat(manual.getReversalVoucherId()).isEqualTo(3001L);
        assertThat(manual.getCancelReason()).isEqualTo("录入错误");
        assertThat(manual.getCancelledBy()).isEqualTo(AUDIT.userId());
        assertThat(manual.getCancelledTime()).isEqualTo(AUDIT.now());
        assertThat(manual.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(manual.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(manualVoucherMapper).updateById(manual);
    }

    @Test
    void cancelRejectsConcurrentManualVoucherUpdate() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherEntryMapper.selectList(any())).thenReturn(originalEntries(2001L));
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity reversal = invocation.getArgument(0);
            reversal.setId(3001L);
            return 1;
        });
        when(manualVoucherMapper.updateById(any(ManualVoucherEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service().cancel(1001L, "并发更新"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证已被其他操作修改，请刷新后重试");

        verify(manualVoucherMapper).updateById(manual);
    }

    @Test
    void cancelRejectsExistingReversalVoucherWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherMapper.selectOne(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<VoucherEntity>>any()))
                .thenReturn(reversalVoucher(3001L));

        assertThatThrownBy(() -> service().cancel(1001L, "重复作废"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证已生成红冲凭证，不能重复作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelConvertsDuplicateReversalInsertToBusinessConflict() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherEntryMapper.selectList(any())).thenReturn(originalEntries(2001L));
        when(voucherMapper.insert(any(VoucherEntity.class))).thenThrow(new DuplicateKeyException("duplicate reversal"));

        assertThatThrownBy(() -> service().cancel(1001L, "并发重复作废"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证已生成红冲凭证，不能重复作废");

        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsOriginalVoucherWithDifferentAmountWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        VoucherEntity original = postedVoucher(2001L, "MV202607010001");
        original.setAmount(new BigDecimal("99.99"));
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(original);

        assertThatThrownBy(() -> service().cancel(1001L, "金额不一致"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证原始过账凭证不存在，无法作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsMissingPostedVoucherIdWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);

        assertThatThrownBy(() -> service().cancel(1001L, "原始凭证缺失"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证缺少原始过账凭证，无法作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsOriginalVoucherFromDifferentAccountBookWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        VoucherEntity original = postedVoucher(2001L, "MV202607010001");
        original.setAccountBookId(99L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(original);

        assertThatThrownBy(() -> service().cancel(1001L, "账套不匹配"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证原始过账凭证不存在，无法作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsEmptyOriginalEntriesWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().cancel(1001L, "原分录缺失"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证原始凭证缺少分录，无法作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsUnbalancedOriginalEntriesWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(
                entry(2001L, 1, 101L, "1001", "现金", "100.00", "0.00"),
                entry(2001L, 2, 201L, "2001", "应付", "0.00", "90.00")
        ));

        assertThatThrownBy(() -> service().cancel(1001L, "原分录污染"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证原始凭证分录不平衡，无法作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsOriginalEntriesAmountMismatchWithoutSideEffects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity manual = manualVoucher(1001L, "POSTED", AUDIT.companyId(), AUDIT.accountBookId());
        manual.setPostedVoucherId(2001L);
        when(manualVoucherMapper.selectById(1001L)).thenReturn(manual);
        when(voucherMapper.selectById(2001L)).thenReturn(postedVoucher(2001L, "MV202607010001"));
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(
                entry(2001L, 1, 101L, "1001", "现金", "90.00", "0.00"),
                entry(2001L, 2, 201L, "2001", "应付", "0.00", "90.00")
        ));

        assertThatThrownBy(() -> service().cancel(1001L, "原分录金额污染"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("手工凭证原始凭证分录金额不一致，无法作废");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void cancelRejectsNonPostedVoucher() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(manualVoucherMapper.selectById(1001L))
                .thenReturn(manualVoucher(1001L, "APPROVED", AUDIT.companyId(), AUDIT.accountBookId()));

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

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
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
        when(manualVoucherMapper.selectById(1001L))
                .thenReturn(manualVoucher(1001L, "POSTED", AUDIT.companyId(), 99L));

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

    private VoucherEntity reversalVoucher(Long id) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setId(id);
        voucher.setCompanyId(AUDIT.companyId());
        voucher.setAccountBookId(AUDIT.accountBookId());
        voucher.setVoucherNo("MV202607010001-REV");
        voucher.setSourceType("MANUAL_REVERSAL");
        voucher.setSourceId(1001L);
        voucher.setSourceNo("MV202607010001");
        voucher.setBizDate(LocalDate.of(2026, 7, 7));
        voucher.setAmount(new BigDecimal("100.00"));
        voucher.setStatus("POSTED");
        voucher.setDeletedFlag(0);
        return voucher;
    }

    private List<ManualVoucherLineEntity> manualLines(Long manualVoucherId) {
        return List.of(
                manualLine(manualVoucherId, 1, 101L, "1001", "现金", "100.00", "0.00"),
                manualLine(manualVoucherId, 2, 201L, "2001", "应付", "0.00", "100.00")
        );
    }

    private ManualVoucherLineEntity manualLine(
            Long manualVoucherId,
            int lineNo,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String debit,
            String credit
    ) {
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

    private VoucherEntryEntity entry(
            Long voucherId,
            int lineNo,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String debit,
            String credit
    ) {
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
