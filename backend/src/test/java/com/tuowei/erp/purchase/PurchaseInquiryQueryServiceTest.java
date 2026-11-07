package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteLineEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQueryService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class PurchaseInquiryQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            8801L,
            101L,
            202L,
            LocalDateTime.parse("2026-08-14T11:30:00")
    );

    @Mock
    private PurchaseInquiryMapper purchaseInquiryMapper;

    @Mock
    private PurchaseInquiryLineMapper purchaseInquiryLineMapper;

    @Mock
    private PurchaseInquiryQuoteMapper purchaseInquiryQuoteMapper;

    @Mock
    private PurchaseInquiryQuoteLineMapper purchaseInquiryQuoteLineMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseInquiryEntity.class);
        initTableInfo(PurchaseInquiryLineEntity.class);
        initTableInfo(PurchaseInquiryQuoteEntity.class);
        initTableInfo(PurchaseInquiryQuoteLineEntity.class);
    }

    @Test
    void listTreatsNullQueryAsTenantScopedDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(purchaseInquiryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseInquiryEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(inquiry(AUDIT.companyId(), AUDIT.accountBookId())));
            return page;
        });

        PageResponse<PurchaseInquiryResponse> response = service().list(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(PurchaseInquiryResponse::inquiryNo)
                .containsExactly("RFQ202608140001");
        assertThat(response.records().get(0).lines()).isEmpty();
        assertThat(response.records().get(0).quotes()).isEmpty();

        ArgumentCaptor<Page<PurchaseInquiryEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<PurchaseInquiryEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseInquiryMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "inquiry_date", "order by");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void getByIdHydratesInquiryLinesAndQuoteLines() {
        PurchaseInquiryEntity inquiry = inquiry(AUDIT.companyId(), AUDIT.accountBookId());
        PurchaseInquiryLineEntity inquiryLine = inquiryLine(inquiry.getId());
        PurchaseInquiryQuoteEntity quote = quote(inquiry.getId());
        PurchaseInquiryQuoteLineEntity quoteLine = quoteLine(inquiry.getId(), quote.getId(), inquiryLine.getId());
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(purchaseInquiryMapper.selectById(inquiry.getId())).thenReturn(inquiry);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(inquiryLine));
        when(purchaseInquiryQuoteMapper.selectList(any())).thenReturn(List.of(quote));
        when(purchaseInquiryQuoteLineMapper.selectList(any())).thenReturn(List.of(quoteLine));

        PurchaseInquiryResponse response = service().getById(inquiry.getId());

        assertThat(response.lines()).singleElement().satisfies(line -> {
            assertThat(line.id()).isEqualTo(inquiryLine.getId());
            assertThat(line.productId()).isEqualTo(inquiryLine.getProductId());
            assertThat(line.qty()).isEqualByComparingTo("12.5000");
        });
        assertThat(response.quotes()).singleElement().satisfies(quoteResponse -> {
            assertThat(quoteResponse.id()).isEqualTo(quote.getId());
            assertThat(quoteResponse.lines()).singleElement().satisfies(line -> {
                assertThat(line.inquiryLineId()).isEqualTo(inquiryLine.getId());
                assertThat(line.unitPrice()).isEqualByComparingTo("8.25");
            });
        });
    }

    @Test
    void getByIdRejectsInquiryOutsideCurrentTenantBeforeHydration() {
        PurchaseInquiryEntity foreignInquiry = inquiry(999L, AUDIT.accountBookId());
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(purchaseInquiryMapper.selectById(foreignInquiry.getId())).thenReturn(foreignInquiry);

        assertThatThrownBy(() -> service().getById(foreignInquiry.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("询价单不存在");

        verifyNoInteractions(
                purchaseInquiryLineMapper,
                purchaseInquiryQuoteMapper,
                purchaseInquiryQuoteLineMapper,
                supplierMapper
        );
    }

    private PurchaseInquiryQueryService service() {
        PurchaseInquiryQuoteService quoteService = new PurchaseInquiryQuoteService(
                purchaseInquiryLineMapper,
                purchaseInquiryQuoteMapper,
                purchaseInquiryQuoteLineMapper,
                supplierMapper
        );
        return new PurchaseInquiryQueryService(
                purchaseInquiryMapper,
                purchaseInquiryLineMapper,
                auditMetadataFactory,
                quoteService
        );
    }

    private PurchaseInquiryEntity inquiry(Long companyId, Long accountBookId) {
        PurchaseInquiryEntity entity = new PurchaseInquiryEntity();
        entity.setId(5001L);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setInquiryNo("RFQ202608140001");
        entity.setInquiryDate(LocalDate.of(2026, 8, 14));
        entity.setStatus("SUBMITTED");
        entity.setTitle("紧固件询价");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private PurchaseInquiryLineEntity inquiryLine(Long inquiryId) {
        PurchaseInquiryLineEntity entity = new PurchaseInquiryLineEntity();
        entity.setId(6001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setInquiryId(inquiryId);
        entity.setLineNo(1);
        entity.setProductId(9001L);
        entity.setQty(new BigDecimal("12.5000"));
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseInquiryQuoteEntity quote(Long inquiryId) {
        PurchaseInquiryQuoteEntity entity = new PurchaseInquiryQuoteEntity();
        entity.setId(7001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setInquiryId(inquiryId);
        entity.setSupplierId(8001L);
        entity.setUnitPrice(new BigDecimal("8.25"));
        entity.setTaxRate(new BigDecimal("13.0000"));
        entity.setStatus("PENDING");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseInquiryQuoteLineEntity quoteLine(Long inquiryId, Long quoteId, Long inquiryLineId) {
        PurchaseInquiryQuoteLineEntity entity = new PurchaseInquiryQuoteLineEntity();
        entity.setId(7101L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setInquiryId(inquiryId);
        entity.setQuoteId(quoteId);
        entity.setInquiryLineId(inquiryLineId);
        entity.setUnitPrice(new BigDecimal("8.25"));
        entity.setTaxRate(new BigDecimal("13.0000"));
        entity.setDeletedFlag(0);
        return entity;
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
