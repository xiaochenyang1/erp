package com.tuowei.erp.finance.voucher.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.web.ManualVoucherLineRequest;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ManualVoucherCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 20, 10, 30)
    );
    private static final LocalDate ORIGINAL_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalDate UPDATED_DATE = LocalDate.of(2026, 8, 21);
    private static final Long VOUCHER_ID = 1001L;

    @Mock
    private ManualVoucherMapper manualVoucherMapper;

    @Mock
    private ManualVoucherLineMapper manualVoucherLineMapper;

    @Mock
    private AccountSubjectMapper accountSubjectMapper;

    @Mock
    private SequenceNumberGenerator sequenceNumberGenerator;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private ManualVoucherQueryService manualVoucherQueryService;

    @Mock
    private AttachmentService attachmentService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ManualVoucherEntity.class);
        initTableInfo(ManualVoucherLineEntity.class);
    }

    @Test
    void createBuildsDraftAndSnapshotsSubjectMetadata() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        LocalDate bizDate = LocalDate.of(2026, 8, 20);
        when(sequenceNumberGenerator.nextNumber("FIN_MANUAL_VOUCHER", "手工凭证", bizDate))
                .thenReturn("MV202608200001");
        when(accountSubjectMapper.selectById(101L)).thenReturn(
                subject(101L, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0, "1001", "现金"));
        when(accountSubjectMapper.selectById(201L)).thenReturn(
                subject(201L, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0, "2001", "应付账款"));
        doAnswer(invocation -> {
            ManualVoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(VOUCHER_ID);
            return 1;
        }).when(manualVoucherMapper).insert(any(ManualVoucherEntity.class));
        ManualVoucherResponse expected = mock(ManualVoucherResponse.class);
        when(manualVoucherQueryService.toResponse(any(ManualVoucherEntity.class), same(AUDIT)))
                .thenReturn(expected);

        ManualVoucherResponse result = service().create(request(
                bizDate,
                "期初调整",
                List.of(
                        new ManualVoucherLineRequest(101L, amount("100.00"), BigDecimal.ZERO, "借方摘要"),
                        new ManualVoucherLineRequest(201L, BigDecimal.ZERO, amount("100.00"), "贷方摘要")
                )
        ));

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ManualVoucherEntity> voucherCaptor = ArgumentCaptor.forClass(ManualVoucherEntity.class);
        verify(manualVoucherMapper).insert(voucherCaptor.capture());
        ManualVoucherEntity insertedVoucher = voucherCaptor.getValue();
        assertThat(insertedVoucher.getId()).isEqualTo(VOUCHER_ID);
        assertThat(insertedVoucher.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(insertedVoucher.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(insertedVoucher.getVoucherNo()).isEqualTo("MV202608200001");
        assertThat(insertedVoucher.getBizDate()).isEqualTo(bizDate);
        assertThat(insertedVoucher.getAmount()).isEqualByComparingTo("100.00");
        assertThat(insertedVoucher.getStatus()).isEqualTo("DRAFT");
        assertThat(insertedVoucher.getRemark()).isEqualTo("期初调整");
        assertThat(insertedVoucher.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(insertedVoucher.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(insertedVoucher.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(insertedVoucher.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(insertedVoucher.getDeletedFlag()).isZero();
        assertThat(insertedVoucher.getVersion()).isZero();

        ArgumentCaptor<ManualVoucherLineEntity> lineCaptor = ArgumentCaptor.forClass(ManualVoucherLineEntity.class);
        verify(manualVoucherLineMapper, times(2)).insert(lineCaptor.capture());
        List<ManualVoucherLineEntity> insertedLines = lineCaptor.getAllValues();
        assertThat(insertedLines).hasSize(2);
        assertThat(insertedLines.get(0).getVoucherId()).isEqualTo(VOUCHER_ID);
        assertThat(insertedLines.get(0).getLineNo()).isEqualTo(1);
        assertThat(insertedLines.get(0).getSubjectId()).isEqualTo(101L);
        assertThat(insertedLines.get(0).getSubjectCode()).isEqualTo("1001");
        assertThat(insertedLines.get(0).getSubjectName()).isEqualTo("现金");
        assertThat(insertedLines.get(0).getDebitAmount()).isEqualByComparingTo("100.00");
        assertThat(insertedLines.get(0).getCreditAmount()).isEqualByComparingTo("0.00");
        assertThat(insertedLines.get(0).getSummary()).isEqualTo("借方摘要");
        assertThat(insertedLines.get(0).getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(insertedLines.get(0).getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(insertedLines.get(0).getDeletedFlag()).isZero();
        assertThat(insertedLines.get(0).getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(insertedLines.get(0).getVersion()).isZero();
        assertThat(insertedLines.get(1).getLineNo()).isEqualTo(2);
        assertThat(insertedLines.get(1).getSubjectId()).isEqualTo(201L);
        assertThat(insertedLines.get(1).getSubjectCode()).isEqualTo("2001");
        assertThat(insertedLines.get(1).getSubjectName()).isEqualTo("应付账款");
        assertThat(insertedLines.get(1).getDebitAmount()).isEqualByComparingTo("0.00");
        assertThat(insertedLines.get(1).getCreditAmount()).isEqualByComparingTo("100.00");
        verify(manualVoucherQueryService).toResponse(same(insertedVoucher), same(AUDIT));
    }

    @Test
    void updateReplacesDraftLinesAndKeepsExistingVoucherNumber() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity draft = voucher("DRAFT");
        draft.setVoucherNo("MV202608200007");
        draft.setBizDate(ORIGINAL_DATE);
        draft.setAmount(amount("100.00"));
        draft.setVersion(3);
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(draft);
        when(manualVoucherMapper.updateById(draft)).thenReturn(1);
        when(manualVoucherLineMapper.delete(
                org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ManualVoucherLineEntity>>any()))
                .thenReturn(2);
        when(accountSubjectMapper.selectById(101L)).thenReturn(
                subject(101L, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0, "1001", "现金"));
        when(accountSubjectMapper.selectById(301L)).thenReturn(
                subject(301L, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0, "3001", "银行存款"));
        ManualVoucherResponse expected = mock(ManualVoucherResponse.class);
        when(manualVoucherQueryService.toResponse(any(ManualVoucherEntity.class), same(AUDIT)))
                .thenReturn(expected);

        ManualVoucherResponse result = service().update(VOUCHER_ID, request(
                UPDATED_DATE,
                "更正后",
                List.of(
                        new ManualVoucherLineRequest(101L, amount("150.00"), BigDecimal.ZERO, "新借方"),
                        new ManualVoucherLineRequest(301L, BigDecimal.ZERO, amount("150.00"), "新贷方")
                )
        ));

        assertThat(result).isSameAs(expected);
        assertThat(draft.getVoucherNo()).isEqualTo("MV202608200007");
        assertThat(draft.getBizDate()).isEqualTo(UPDATED_DATE);
        assertThat(draft.getAmount()).isEqualByComparingTo("150.00");
        assertThat(draft.getRemark()).isEqualTo("更正后");
        verify(sequenceNumberGenerator, never()).nextNumber(any(), any(), any());

        ArgumentCaptor<ManualVoucherLineEntity> lineCaptor = ArgumentCaptor.forClass(ManualVoucherLineEntity.class);
        verify(manualVoucherLineMapper, times(2)).insert(lineCaptor.capture());
        List<ManualVoucherLineEntity> replacementLines = lineCaptor.getAllValues();
        assertThat(replacementLines.get(0).getSubjectCode()).isEqualTo("1001");
        assertThat(replacementLines.get(0).getSubjectName()).isEqualTo("现金");
        assertThat(replacementLines.get(0).getDebitAmount()).isEqualByComparingTo("150.00");
        assertThat(replacementLines.get(1).getSubjectCode()).isEqualTo("3001");
        assertThat(replacementLines.get(1).getSubjectName()).isEqualTo("银行存款");
        assertThat(replacementLines.get(1).getCreditAmount()).isEqualByComparingTo("150.00");

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ManualVoucherLineEntity>> deleteCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        InOrder writes = inOrder(manualVoucherMapper, manualVoucherLineMapper);
        writes.verify(manualVoucherMapper).updateById(same(draft));
        writes.verify(manualVoucherLineMapper).delete(deleteCaptor.capture());
        writes.verify(manualVoucherLineMapper, times(2)).insert(any(ManualVoucherLineEntity.class));
        verify(manualVoucherQueryService).toResponse(same(draft), same(AUDIT));
        assertScopedLineDelete(deleteCaptor.getValue());
    }

    @Test
    void updateRejectsNonDraftBeforeAnyWrite() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(voucher("PENDING"));

        assertThatThrownBy(() -> service().update(VOUCHER_ID, request(
                UPDATED_DATE,
                "not editable",
                List.of(
                        new ManualVoucherLineRequest(101L, amount("100.00"), BigDecimal.ZERO, "借"),
                        new ManualVoucherLineRequest(201L, BigDecimal.ZERO, amount("100.00"), "贷")
                )
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有草稿状态的手工凭证可以编辑");

        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
        verify(manualVoucherLineMapper, never()).delete(any());
        verify(manualVoucherLineMapper, never()).insert(any(ManualVoucherLineEntity.class));
    }

    @Test
    void submitRejectsNonDraftBeforeAttachmentAndBalanceChecks() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity pending = voucher("PENDING");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(pending);

        assertThatThrownBy(() -> service().submit(VOUCHER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有草稿状态的手工凭证可以提交");

        verifyNoInteractions(attachmentService);
        verify(manualVoucherQueryService, never()).loadLines(any(ManualVoucherEntity.class), same(AUDIT));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void submitRunsAttachmentGateBeforeLoadingPersistedLines() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity draft = voucher("DRAFT");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(draft);
        doThrow(new IllegalArgumentException("附件缺失"))
                .when(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.MANUAL_VOUCHER, VOUCHER_ID);

        assertThatThrownBy(() -> service().submit(VOUCHER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("附件缺失");

        InOrder order = inOrder(manualVoucherQueryService, attachmentService);
        order.verify(manualVoucherQueryService).requireVoucher(VOUCHER_ID, AUDIT);
        order.verify(attachmentService).requireIfConfigured(AttachmentBusinessType.MANUAL_VOUCHER, VOUCHER_ID);
        verify(manualVoucherQueryService, never()).loadLines(any(ManualVoucherEntity.class), same(AUDIT));
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void submitChecksPersistedBalanceAfterAttachmentBeforeWritingStatus() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity draft = voucher("DRAFT");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(draft);
        when(manualVoucherQueryService.loadLines(draft, AUDIT)).thenReturn(List.of(
                line(1, 101L, "100.00", "0.00"),
                line(2, 201L, "0.00", "90.00")
        ));

        assertThatThrownBy(() -> service().submit(VOUCHER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("借贷金额不平衡，不能继续");

        InOrder order = inOrder(manualVoucherQueryService, attachmentService);
        order.verify(manualVoucherQueryService).requireVoucher(VOUCHER_ID, AUDIT);
        order.verify(attachmentService).requireIfConfigured(AttachmentBusinessType.MANUAL_VOUCHER, VOUCHER_ID);
        order.verify(manualVoucherQueryService).loadLines(draft, AUDIT);
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void submitPersistsPendingAfterAttachmentAndBalanceChecks() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity draft = voucher("DRAFT");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(draft);
        when(manualVoucherQueryService.loadLines(draft, AUDIT)).thenReturn(List.of(
                line(1, 101L, "100.00", "0.00"),
                line(2, 201L, "0.00", "100.00")
        ));
        when(manualVoucherMapper.updateById(draft)).thenReturn(1);

        service().submit(VOUCHER_ID);

        assertThat(draft.getStatus()).isEqualTo("PENDING");
        assertThat(draft.getSubmittedBy()).isEqualTo(AUDIT.userId());
        assertThat(draft.getSubmittedTime()).isEqualTo(AUDIT.now());
        assertThat(draft.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(draft.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(manualVoucherQueryService, attachmentService, manualVoucherMapper);
        order.verify(manualVoucherQueryService).requireVoucher(VOUCHER_ID, AUDIT);
        order.verify(attachmentService).requireIfConfigured(AttachmentBusinessType.MANUAL_VOUCHER, VOUCHER_ID);
        order.verify(manualVoucherQueryService).loadLines(draft, AUDIT);
        order.verify(manualVoucherMapper).updateById(draft);
    }

    @Test
    void approveMovesPendingVoucherToApprovedAndSetsApprovalMetadata() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity pending = voucher("PENDING");
        pending.setSubmittedBy(8001L);
        pending.setSubmittedTime(AUDIT.now().minusHours(1));
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(pending);
        when(manualVoucherMapper.updateById(pending)).thenReturn(1);

        service().approve(VOUCHER_ID);

        assertThat(pending.getStatus()).isEqualTo("APPROVED");
        assertThat(pending.getApprovedBy()).isEqualTo(AUDIT.userId());
        assertThat(pending.getApprovedTime()).isEqualTo(AUDIT.now());
        assertThat(pending.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(pending.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(manualVoucherMapper).updateById(pending);
    }

    @Test
    void rejectReturnsPendingVoucherToDraftAndClearsSubmissionMetadata() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity pending = voucher("PENDING");
        pending.setSubmittedBy(8001L);
        pending.setSubmittedTime(AUDIT.now().minusHours(1));
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(pending);
        when(manualVoucherMapper.updateById(pending)).thenReturn(1);

        service().reject(VOUCHER_ID, "  科目选择错误  ");

        assertThat(pending.getStatus()).isEqualTo("DRAFT");
        assertThat(pending.getRejectReason()).isEqualTo("科目选择错误");
        assertThat(pending.getSubmittedBy()).isNull();
        assertThat(pending.getSubmittedTime()).isNull();
        assertThat(pending.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(pending.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(manualVoucherMapper).updateById(pending);
    }

    @Test
    void approveRejectsNonPendingBeforeWriting() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(voucher("DRAFT"));

        assertThatThrownBy(() -> service().approve(VOUCHER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有待审批状态的手工凭证可以审批");

        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void rejectRejectsNonPendingAndBlankReasonBeforeWriting() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity draft = voucher("DRAFT");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(draft);

        assertThatThrownBy(() -> service().reject(VOUCHER_ID, "  "))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有待审批状态的手工凭证可以驳回");
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));

        ManualVoucherEntity pending = voucher("PENDING");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(pending);
        assertThatThrownBy(() -> service().reject(VOUCHER_ID, " \t "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("驳回原因不能为空");
        verify(manualVoucherMapper, never()).updateById(any(ManualVoucherEntity.class));
    }

    @Test
    void deleteRemovesDraftLinesBeforeHeader() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ManualVoucherEntity draft = voucher("DRAFT");
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(draft);
        when(manualVoucherLineMapper.delete(
                org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ManualVoucherLineEntity>>any()))
                .thenReturn(2);
        when(manualVoucherMapper.deleteById(VOUCHER_ID)).thenReturn(1);

        service().delete(VOUCHER_ID);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ManualVoucherLineEntity>> deleteCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        InOrder order = inOrder(manualVoucherLineMapper, manualVoucherMapper);
        order.verify(manualVoucherLineMapper).delete(deleteCaptor.capture());
        order.verify(manualVoucherMapper).deleteById(VOUCHER_ID);
        assertScopedLineDelete(deleteCaptor.getValue());
    }

    @Test
    void deleteRejectsNonDraftBeforeRemovingLines() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(manualVoucherQueryService.requireVoucher(VOUCHER_ID, AUDIT)).thenReturn(voucher("APPROVED"));

        assertThatThrownBy(() -> service().delete(VOUCHER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有草稿状态的手工凭证可以删除");

        verify(manualVoucherLineMapper, never()).delete(any());
        verify(manualVoucherMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void createRejectsSubjectOutsideCurrentTenant() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        LocalDate bizDate = ORIGINAL_DATE;
        when(sequenceNumberGenerator.nextNumber("FIN_MANUAL_VOUCHER", "手工凭证", bizDate))
                .thenReturn("MV202608200002");
        doAnswer(invocation -> {
            ManualVoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(VOUCHER_ID);
            return 1;
        }).when(manualVoucherMapper).insert(any(ManualVoucherEntity.class));
        when(accountSubjectMapper.selectById(101L)).thenReturn(
                subject(101L, 999L, AUDIT.accountBookId(), "ACTIVE", 0, "1001", "越权科目"));

        assertThatThrownBy(() -> service().create(request(
                bizDate,
                "跨租户",
                List.of(
                        new ManualVoucherLineRequest(101L, amount("100.00"), BigDecimal.ZERO, "借"),
                        new ManualVoucherLineRequest(101L, BigDecimal.ZERO, amount("100.00"), "贷")
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目不存在");

        verify(manualVoucherLineMapper, never()).insert(any(ManualVoucherLineEntity.class));
        verify(manualVoucherQueryService, never()).toResponse(any(ManualVoucherEntity.class), any(AuditMetadata.class));
    }

    @Test
    void createRejectsInactiveSubjectBeforeInsertingItsLine() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        LocalDate bizDate = ORIGINAL_DATE;
        when(sequenceNumberGenerator.nextNumber("FIN_MANUAL_VOUCHER", "手工凭证", bizDate))
                .thenReturn("MV202608200003");
        doAnswer(invocation -> {
            ManualVoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(VOUCHER_ID);
            return 1;
        }).when(manualVoucherMapper).insert(any(ManualVoucherEntity.class));
        when(accountSubjectMapper.selectById(101L)).thenReturn(
                subject(101L, AUDIT.companyId(), AUDIT.accountBookId(), "INACTIVE", 0, "1001", "停用科目"));

        assertThatThrownBy(() -> service().create(request(
                bizDate,
                "停用科目",
                List.of(
                        new ManualVoucherLineRequest(101L, amount("100.00"), BigDecimal.ZERO, "借"),
                        new ManualVoucherLineRequest(101L, BigDecimal.ZERO, amount("100.00"), "贷")
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目已停用：1001");

        verify(manualVoucherLineMapper, never()).insert(any(ManualVoucherLineEntity.class));
        verify(manualVoucherQueryService, never()).toResponse(any(ManualVoucherEntity.class), any(AuditMetadata.class));
    }

    private ManualVoucherCommandService service() {
        return new ManualVoucherCommandService(
                manualVoucherMapper,
                manualVoucherLineMapper,
                accountSubjectMapper,
                sequenceNumberGenerator,
                auditMetadataFactory,
                manualVoucherQueryService,
                attachmentService
        );
    }

    private ManualVoucherSaveRequest request(
            LocalDate bizDate,
            String remark,
            List<ManualVoucherLineRequest> lines
    ) {
        return new ManualVoucherSaveRequest(bizDate, remark, lines);
    }

    private ManualVoucherEntity voucher(String status) {
        ManualVoucherEntity voucher = new ManualVoucherEntity();
        voucher.setId(VOUCHER_ID);
        voucher.setCompanyId(AUDIT.companyId());
        voucher.setAccountBookId(AUDIT.accountBookId());
        voucher.setVoucherNo("MV202608200001");
        voucher.setBizDate(ORIGINAL_DATE);
        voucher.setAmount(amount("100.00"));
        voucher.setStatus(status);
        voucher.setRemark("原始凭证");
        voucher.setDeletedFlag(0);
        voucher.setVersion(0);
        return voucher;
    }

    private ManualVoucherLineEntity line(int lineNo, Long subjectId, String debit, String credit) {
        ManualVoucherLineEntity line = new ManualVoucherLineEntity();
        line.setId((long) lineNo);
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setVoucherId(VOUCHER_ID);
        line.setLineNo(lineNo);
        line.setSubjectId(subjectId);
        line.setDebitAmount(amount(debit));
        line.setCreditAmount(amount(credit));
        line.setDeletedFlag(0);
        return line;
    }

    private AccountSubjectEntity subject(
            Long id,
            Long companyId,
            Long accountBookId,
            String status,
            Integer deletedFlag,
            String code,
            String name
    ) {
        AccountSubjectEntity subject = new AccountSubjectEntity();
        subject.setId(id);
        subject.setCompanyId(companyId);
        subject.setAccountBookId(accountBookId);
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setStatus(status);
        subject.setDeletedFlag(deletedFlag);
        return subject;
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertScopedLineDelete(LambdaQueryWrapper<ManualVoucherLineEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "voucher_id");
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters).contains(AUDIT.companyId(), AUDIT.accountBookId(), VOUCHER_ID);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                entityClass.getName()
        );
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
