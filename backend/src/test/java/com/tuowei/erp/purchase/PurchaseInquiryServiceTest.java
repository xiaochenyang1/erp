package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryNumberService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryService;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquirySelectQuoteRequest;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseInquiryServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 8801L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 11, 0);

    @Mock
    private PurchaseInquiryMapper purchaseInquiryMapper;
    @Mock
    private PurchaseInquiryLineMapper purchaseInquiryLineMapper;
    @Mock
    private PurchaseInquiryQuoteMapper purchaseInquiryQuoteMapper;
    @Mock
    private PurchaseInquiryNumberService purchaseInquiryNumberService;
    @Mock
    private ProductValidator productValidator;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseInquiryEntity.class);
        initTableInfo(PurchaseInquiryLineEntity.class);
        initTableInfo(PurchaseInquiryQuoteEntity.class);
    }

    @Test
    void createAddQuoteSelectHappyPath() {
        stubAudit();
        AtomicLong ids = new AtomicLong(5000L);
        when(purchaseInquiryNumberService.nextInquiryNo(any())).thenReturn("RFQ202607170001");
        when(productValidator.requireProduct(eq(9001L), eq(COMPANY_ID), eq(ACCOUNT_BOOK_ID)))
                .thenReturn(product(9001L));
        when(purchaseInquiryMapper.insert(any(PurchaseInquiryEntity.class))).thenAnswer(invocation -> {
            PurchaseInquiryEntity entity = invocation.getArgument(0);
            entity.setId(ids.incrementAndGet());
            return 1;
        });
        when(purchaseInquiryLineMapper.insert(any(PurchaseInquiryLineEntity.class))).thenAnswer(invocation -> {
            PurchaseInquiryLineEntity entity = invocation.getArgument(0);
            entity.setId(ids.incrementAndGet());
            return 1;
        });
        when(purchaseInquiryQuoteMapper.insert(any(PurchaseInquiryQuoteEntity.class))).thenAnswer(invocation -> {
            PurchaseInquiryQuoteEntity entity = invocation.getArgument(0);
            entity.setId(ids.incrementAndGet());
            return 1;
        });
        when(purchaseInquiryMapper.updateById(any(PurchaseInquiryEntity.class))).thenReturn(1);
        when(purchaseInquiryQuoteMapper.updateById(any(PurchaseInquiryQuoteEntity.class))).thenReturn(1);

        PurchaseInquiryCreateRequest createRequest = new PurchaseInquiryCreateRequest(
                LocalDate.of(2026, 7, 17),
                "紧固件询价",
                "紧急",
                List.of(new PurchaseInquiryLineRequest(9001L, new BigDecimal("10.0000"), "M8螺栓"))
        );
        PurchaseInquiryResponse created = service().create(createRequest);
        assertThat(created.inquiryNo()).isEqualTo("RFQ202607170001");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.lines()).hasSize(1);
        Long inquiryId = created.id();

        PurchaseInquiryEntity storedInquiry = inquiry(inquiryId, "DRAFT");
        when(purchaseInquiryMapper.selectById(inquiryId)).thenReturn(storedInquiry);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(inquiryId, 6001L)));
        when(purchaseInquiryQuoteMapper.selectList(any())).thenReturn(List.of());

        PurchaseInquiryResponse submitted = service().submit(inquiryId);
        assertThat(submitted.status()).isEqualTo("SUBMITTED");

        when(supplierMapper.selectById(7001L)).thenReturn(supplier(7001L));
        when(purchaseInquiryQuoteMapper.exists(any())).thenReturn(false);

        PurchaseInquiryResponse withQuote = service().addQuote(
                inquiryId,
                new PurchaseInquiryQuoteRequest(7001L, new BigDecimal("12.50"), new BigDecimal("13.0000"), "含运")
        );
        assertThat(withQuote.status()).isEqualTo("SUBMITTED");

        ArgumentCaptor<PurchaseInquiryQuoteEntity> quoteCaptor = ArgumentCaptor.forClass(PurchaseInquiryQuoteEntity.class);
        verify(purchaseInquiryQuoteMapper).insert(quoteCaptor.capture());
        PurchaseInquiryQuoteEntity insertedQuote = quoteCaptor.getValue();
        Long quoteId = insertedQuote.getId();

        PurchaseInquiryQuoteEntity storedQuote = quote(quoteId, inquiryId, 7001L, "PENDING");
        when(purchaseInquiryQuoteMapper.selectById(quoteId)).thenReturn(storedQuote);
        when(purchaseInquiryQuoteMapper.selectList(any())).thenAnswer(inv -> List.of(storedQuote));

        PurchaseInquiryResponse closed = service().selectQuote(inquiryId, new PurchaseInquirySelectQuoteRequest(quoteId));
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.selectedSupplierId()).isEqualTo(7001L);
        assertThat(closed.selectedQuoteId()).isEqualTo(quoteId);
        assertThat(closed.quotes()).anyMatch(q -> "SELECTED".equals(q.status()));

        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(inquiryId, 6001L)));
        when(purchaseInquiryQuoteMapper.selectById(quoteId)).thenReturn(storedQuote);

        PurchaseInquiryPoPrefillResponse prefill = service().poPrefill(inquiryId);
        assertThat(prefill.supplierId()).isEqualTo(7001L);
        assertThat(prefill.lines()).hasSize(1);
        assertThat(prefill.lines().get(0).productId()).isEqualTo(9001L);
        assertThat(prefill.lines().get(0).price()).isEqualByComparingTo("12.50");
        assertThat(prefill.lines().get(0).taxRate()).isEqualByComparingTo("13.0000");
        assertThat(prefill.remark()).contains("RFQ202607170001");
    }

    private PurchaseInquiryService service() {
        return new PurchaseInquiryService(
                purchaseInquiryMapper,
                purchaseInquiryLineMapper,
                purchaseInquiryQuoteMapper,
                purchaseInquiryNumberService,
                productValidator,
                supplierMapper,
                auditMetadataFactory
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW));
    }

    private PurchaseInquiryEntity inquiry(Long id, String status) {
        PurchaseInquiryEntity entity = new PurchaseInquiryEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInquiryNo("RFQ202607170001");
        entity.setInquiryDate(LocalDate.of(2026, 7, 17));
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setRemark("紧急");
        entity.setVersion(0);
        return entity;
    }

    private PurchaseInquiryLineEntity line(Long inquiryId, Long lineId) {
        PurchaseInquiryLineEntity line = new PurchaseInquiryLineEntity();
        line.setId(lineId);
        line.setCompanyId(COMPANY_ID);
        line.setAccountBookId(ACCOUNT_BOOK_ID);
        line.setInquiryId(inquiryId);
        line.setLineNo(1);
        line.setProductId(9001L);
        line.setQty(new BigDecimal("10.0000"));
        line.setDeletedFlag(0);
        line.setRemark("M8螺栓");
        line.setVersion(0);
        return line;
    }

    private PurchaseInquiryQuoteEntity quote(Long id, Long inquiryId, Long supplierId, String status) {
        PurchaseInquiryQuoteEntity quote = new PurchaseInquiryQuoteEntity();
        quote.setId(id);
        quote.setCompanyId(COMPANY_ID);
        quote.setAccountBookId(ACCOUNT_BOOK_ID);
        quote.setInquiryId(inquiryId);
        quote.setSupplierId(supplierId);
        quote.setUnitPrice(new BigDecimal("12.50"));
        quote.setTaxRate(new BigDecimal("13.0000"));
        quote.setStatus(status);
        quote.setDeletedFlag(0);
        quote.setVersion(0);
        return quote;
    }

    private SupplierEntity supplier(Long id) {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(id);
        supplier.setCompanyId(COMPANY_ID);
        supplier.setAccountBookId(ACCOUNT_BOOK_ID);
        supplier.setStatus("ACTIVE");
        supplier.setDeletedFlag(0);
        supplier.setSupplierName("供应商A");
        return supplier;
    }

    private ProductEntity product(Long id) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setCompanyId(COMPANY_ID);
        product.setAccountBookId(ACCOUNT_BOOK_ID);
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
    }

    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }
}
