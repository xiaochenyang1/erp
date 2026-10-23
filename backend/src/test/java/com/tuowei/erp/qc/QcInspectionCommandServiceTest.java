package com.tuowei.erp.qc.inspection.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.web.QcInspectionResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateLineRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QcInspectionCommandServiceTest {

    private static final Long INSPECTION_ID = 5001L;
    private static final Long LINE_ONE_ID = 6001L;
    private static final Long LINE_TWO_ID = 6002L;
    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9701L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 30);

    @Mock
    private QcInspectionOrderMapper qcInspectionOrderMapper;

    @Mock
    private QcInspectionLineMapper qcInspectionLineMapper;

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private QcInspectionSourceAccess qcInspectionSourceAccess;

    @Mock
    private QcInspectionQueryService qcInspectionQueryService;

    @Mock
    private AttachmentService attachmentService;

    private AuditMetadata audit;

    @BeforeEach
    void setUp() {
        audit = new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW);
        when(auditMetadataFactory.current()).thenReturn(audit);
    }

    @Test
    void updateRecalculatesTotalAndUpdatesChangedLineAndHeader() {
        QcInspectionOrderEntity inspection = inspection("DRAFT");
        QcInspectionLineEntity firstLine = line(LINE_ONE_ID, "1.23456");
        QcInspectionLineEntity secondLine = line(LINE_TWO_ID, "2.5000");
        stubInspection(inspection, firstLine, secondLine);
        when(qcInspectionLineMapper.updateById(any(QcInspectionLineEntity.class))).thenReturn(1);
        when(qcInspectionOrderMapper.updateById(any(QcInspectionOrderEntity.class))).thenReturn(1);
        QcInspectionResponse response = response("DRAFT");
        when(qcInspectionQueryService.getById(INSPECTION_ID)).thenReturn(response);

        QcInspectionUpdateRequest request = new QcInspectionUpdateRequest(
                LocalDate.of(2026, 8, 21),
                "updated header",
                List.of(new QcInspectionUpdateLineRequest(
                        LINE_ONE_ID,
                        new BigDecimal("3.12567"),
                        "scratch",
                        "rechecked"
                ))
        );

        assertThat(service().update(INSPECTION_ID, request)).isSameAs(response);

        assertThat(firstLine.getInspectedQty()).isEqualByComparingTo("3.1257");
        assertThat(firstLine.getDefectReason()).isEqualTo("scratch");
        assertThat(firstLine.getRemark()).isEqualTo("rechecked");
        assertThat(firstLine.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(firstLine.getUpdatedTime()).isEqualTo(NOW);
        assertThat(secondLine.getInspectedQty()).isEqualByComparingTo("2.5000");
        assertThat(inspection.getInspectionDate()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(inspection.getRemark()).isEqualTo("updated header");
        assertThat(inspection.getTotalQty()).isEqualByComparingTo("5.6257");
        assertThat(inspection.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(inspection.getUpdatedTime()).isEqualTo(NOW);
        verify(qcInspectionLineMapper).updateById(firstLine);
        verify(qcInspectionOrderMapper).updateById(inspection);
    }

    @Test
    void submitMovesDraftToSubmittedAndChecksAttachmentsBeforePersisting() {
        QcInspectionOrderEntity inspection = inspection("DRAFT");
        stubInspection(inspection);
        when(qcInspectionOrderMapper.updateById(inspection)).thenReturn(1);
        QcInspectionResponse response = response("SUBMITTED");
        when(qcInspectionQueryService.getById(INSPECTION_ID)).thenReturn(response);

        assertThat(service().submit(INSPECTION_ID)).isSameAs(response);

        verify(attachmentService).requireIfConfigured(AttachmentBusinessType.QC_INSPECTION, INSPECTION_ID);
        assertThat(inspection.getStatus()).isEqualTo("SUBMITTED");
        assertThat(inspection.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(inspection.getUpdatedTime()).isEqualTo(NOW);
        verify(qcInspectionOrderMapper).updateById(inspection);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SUBMITTED"})
    void cancelAllowsDraftAndSubmitted(String status) {
        QcInspectionOrderEntity inspection = inspection(status);
        stubInspection(inspection);
        when(qcInspectionOrderMapper.updateById(inspection)).thenReturn(1);
        QcInspectionResponse response = response("CANCELLED");
        when(qcInspectionQueryService.getById(INSPECTION_ID)).thenReturn(response);

        assertThat(service().cancel(INSPECTION_ID)).isSameAs(response);

        assertThat(inspection.getStatus()).isEqualTo("CANCELLED");
        assertThat(inspection.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(inspection.getUpdatedTime()).isEqualTo(NOW);
        verify(qcInspectionOrderMapper).updateById(inspection);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JUDGED", "CANCELLED"})
    void cancelRejectsStatusesThatAreAlreadyFinal(String status) {
        QcInspectionOrderEntity inspection = inspection(status);
        stubInspection(inspection);

        assertThatThrownBy(() -> service().cancel(INSPECTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前检验单状态不允许作废");

        verify(qcInspectionOrderMapper, never()).updateById(any(QcInspectionOrderEntity.class));
        verify(qcInspectionQueryService, never()).getById(INSPECTION_ID);
    }

    @Test
    void updateDoesNotReadBackAfterLineOptimisticLockConflict() {
        QcInspectionOrderEntity inspection = inspection("DRAFT");
        QcInspectionLineEntity line = line(LINE_ONE_ID, "1.0000");
        stubInspection(inspection, line);
        when(qcInspectionLineMapper.updateById(line)).thenReturn(0);

        QcInspectionUpdateRequest request = new QcInspectionUpdateRequest(
                LocalDate.of(2026, 8, 21),
                "conflict",
                List.of(new QcInspectionUpdateLineRequest(
                        LINE_ONE_ID,
                        new BigDecimal("2.0000"),
                        null,
                        null
                ))
        );

        assertThatThrownBy(() -> service().update(INSPECTION_ID, request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("检验单明细已被其他操作修改，请刷新后重试");

        verify(qcInspectionOrderMapper, never()).updateById(any(QcInspectionOrderEntity.class));
        verify(qcInspectionQueryService, never()).getById(INSPECTION_ID);
    }

    private QcInspectionCommandService service() {
        return new QcInspectionCommandService(
                qcInspectionOrderMapper,
                qcInspectionLineMapper,
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                auditMetadataFactory,
                qcInspectionSourceAccess,
                qcInspectionQueryService,
                attachmentService
        );
    }

    private void stubInspection(QcInspectionOrderEntity inspection, QcInspectionLineEntity... lines) {
        when(qcInspectionQueryService.requireInspection(eq(INSPECTION_ID), same(audit)))
                .thenReturn(inspection);
        if (lines.length > 0) {
            when(qcInspectionQueryService.loadInspectionLines(same(inspection)))
                    .thenReturn(List.of(lines));
        }
    }

    private QcInspectionOrderEntity inspection(String status) {
        QcInspectionOrderEntity inspection = new QcInspectionOrderEntity();
        inspection.setId(INSPECTION_ID);
        inspection.setCompanyId(COMPANY_ID);
        inspection.setAccountBookId(ACCOUNT_BOOK_ID);
        inspection.setInspectionNo("QC-5001");
        inspection.setInspectionType(QcInspectionGate.TYPE_IQC);
        inspection.setInspectionDate(LocalDate.of(2026, 8, 20));
        inspection.setStatus(status);
        inspection.setDeletedFlag(0);
        inspection.setTotalQty(new BigDecimal("3.7346"));
        inspection.setVersion(2);
        return inspection;
    }

    private QcInspectionLineEntity line(Long id, String inspectedQty) {
        QcInspectionLineEntity line = new QcInspectionLineEntity();
        line.setId(id);
        line.setCompanyId(COMPANY_ID);
        line.setAccountBookId(ACCOUNT_BOOK_ID);
        line.setInspectionId(INSPECTION_ID);
        line.setLineNo(id.equals(LINE_ONE_ID) ? 1 : 2);
        line.setInspectedQty(new BigDecimal(inspectedQty));
        line.setDeletedFlag(0);
        line.setVersion(1);
        return line;
    }

    private QcInspectionResponse response(String status) {
        return new QcInspectionResponse(
                INSPECTION_ID,
                "QC-5001",
                QcInspectionGate.TYPE_IQC,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 20),
                status,
                new BigDecimal("5.6257"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of()
        );
    }
}
