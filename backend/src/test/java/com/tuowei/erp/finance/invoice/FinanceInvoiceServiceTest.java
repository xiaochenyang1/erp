package com.tuowei.erp.finance.invoice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.invoice.mapper.InvoiceRegisterMapper;
import com.tuowei.erp.finance.invoice.model.InvoiceRegisterEntity;
import com.tuowei.erp.finance.invoice.service.FinanceInvoiceService;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.finance.invoice.service.InvoiceNumberService;
import com.tuowei.erp.finance.invoice.web.InvoiceCreateRequest;
import com.tuowei.erp.finance.invoice.web.InvoiceResponse;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceInvoiceServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            8801L,
            1L,
            10L,
            LocalDateTime.of(2026, 7, 17, 10, 0)
    );

    private final InvoiceRegisterMapper invoiceRegisterMapper = mock(InvoiceRegisterMapper.class);
    private final InvoiceNumberService invoiceNumberService = mock(InvoiceNumberService.class);
    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final SalesOrderMapper salesOrderMapper = mock(SalesOrderMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(InvoiceRegisterEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                InvoiceRegisterEntity.class.getName()
        );
        assistant.setCurrentNamespace(InvoiceRegisterEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, InvoiceRegisterEntity.class);
    }

    @Test
    void createDraftThenPostThenCancelHappyPath() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(invoiceNumberService.nextInvoiceNo(LocalDate.of(2026, 7, 17))).thenReturn("FI202607170001");
        when(invoiceRegisterMapper.insert(any(InvoiceRegisterEntity.class))).thenAnswer(invocation -> {
            InvoiceRegisterEntity entity = invocation.getArgument(0);
            entity.setId(5001L);
            return 1;
        });
        when(invoiceRegisterMapper.updateById(any(InvoiceRegisterEntity.class))).thenReturn(1);
        PurchaseOrderEntity po = new PurchaseOrderEntity();
        po.setId(9001L);
        po.setCompanyId(1L);
        when(purchaseOrderMapper.selectById(9001L)).thenReturn(po);

        FinanceInvoiceService service = service();

        InvoiceResponse created = service.create(new InvoiceCreateRequest(
                "INPUT",
                " 华东供应商 ",
                new BigDecimal("1000.00"),
                new BigDecimal("130.00"),
                LocalDate.of(2026, 7, 17),
                "PURCHASE_ORDER",
                9001L,
                "进项登记"
        ));

        assertThat(created.id()).isEqualTo(5001L);
        assertThat(created.invoiceNo()).isEqualTo("FI202607170001");
        assertThat(created.invoiceType()).isEqualTo("INPUT");
        assertThat(created.partnerName()).isEqualTo("华东供应商");
        assertThat(created.amount()).isEqualByComparingTo("1000.00");
        assertThat(created.taxAmount()).isEqualByComparingTo("130.00");
        assertThat(created.status()).isEqualTo("DRAFT");

        ArgumentCaptor<InvoiceRegisterEntity> createCaptor = ArgumentCaptor.forClass(InvoiceRegisterEntity.class);
        verify(invoiceRegisterMapper).insert(createCaptor.capture());
        InvoiceRegisterEntity stored = createCaptor.getValue();
        assertThat(stored.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(stored.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(stored.getStatus()).isEqualTo("DRAFT");
        assertThat(stored.getDeletedFlag()).isZero();

        // post
        InvoiceRegisterEntity draft = copy(stored);
        draft.setId(5001L);
        when(invoiceRegisterMapper.selectById(5001L)).thenReturn(draft);
        InvoiceResponse posted = service.post(5001L);
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(draft.getStatus()).isEqualTo("POSTED");
        assertThat(draft.getUpdatedBy()).isEqualTo(AUDIT.userId());

        // cancel
        when(invoiceRegisterMapper.selectById(5001L)).thenReturn(draft);
        InvoiceResponse cancelled = service.cancel(5001L);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(draft.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void postStopsAtAttachmentGateWithoutMarkingInvoicePosted() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InvoiceRegisterEntity draft = new InvoiceRegisterEntity();
        draft.setId(5001L);
        draft.setCompanyId(AUDIT.companyId());
        draft.setAccountBookId(AUDIT.accountBookId());
        draft.setInvoiceNo("FI202607170001");
        draft.setStatus("DRAFT");
        draft.setDeletedFlag(0);
        when(invoiceRegisterMapper.selectById(5001L)).thenReturn(draft);
        doThrow(new IllegalArgumentException("业务类型 FIN_INVOICE 要求至少上传 1 个附件，当前 0 个"))
                .when(attachmentService)
                .requireIfConfigured("FIN_INVOICE", 5001L);

        assertThatThrownBy(() -> service().post(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FIN_INVOICE");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        verify(invoiceRegisterMapper, never()).updateById(any(InvoiceRegisterEntity.class));
    }

    private FinanceInvoiceService service() {
        return new FinanceInvoiceService(invoiceRegisterMapper, invoiceNumberService, purchaseOrderMapper, salesOrderMapper, auditMetadataFactory, attachmentService);
    }

    private InvoiceRegisterEntity copy(InvoiceRegisterEntity source) {
        InvoiceRegisterEntity entity = new InvoiceRegisterEntity();
        entity.setId(source.getId());
        entity.setCompanyId(source.getCompanyId());
        entity.setAccountBookId(source.getAccountBookId());
        entity.setInvoiceNo(source.getInvoiceNo());
        entity.setInvoiceType(source.getInvoiceType());
        entity.setPartnerName(source.getPartnerName());
        entity.setAmount(source.getAmount());
        entity.setTaxAmount(source.getTaxAmount());
        entity.setInvoiceDate(source.getInvoiceDate());
        entity.setRelatedBizType(source.getRelatedBizType());
        entity.setRelatedBizId(source.getRelatedBizId());
        entity.setStatus(source.getStatus());
        entity.setDeletedFlag(source.getDeletedFlag());
        entity.setRemark(source.getRemark());
        entity.setCreatedBy(source.getCreatedBy());
        entity.setCreatedTime(source.getCreatedTime());
        entity.setUpdatedBy(source.getUpdatedBy());
        entity.setUpdatedTime(source.getUpdatedTime());
        entity.setVersion(source.getVersion());
        return entity;
    }
}
