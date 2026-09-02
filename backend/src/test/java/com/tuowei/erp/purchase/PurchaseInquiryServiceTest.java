package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteLineEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryNumberService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryCommandService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQueryService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryService;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquirySelectQuoteRequest;
import com.tuowei.erp.purchase.order.service.PurchaseOrderInquirySource;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private PurchaseInquiryQuoteLineMapper purchaseInquiryQuoteLineMapper;
    @Mock
    private PurchaseInquiryNumberService purchaseInquiryNumberService;
    @Mock
    private ProductValidator productValidator;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private PurchaseOrderService purchaseOrderService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseInquiryEntity.class);
        initTableInfo(PurchaseInquiryLineEntity.class);
        initTableInfo(PurchaseInquiryQuoteEntity.class);
        initTableInfo(PurchaseInquiryQuoteLineEntity.class);
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
        ArgumentCaptor<PurchaseInquiryQuoteLineEntity> legacyLineCaptor =
                ArgumentCaptor.forClass(PurchaseInquiryQuoteLineEntity.class);
        verify(purchaseInquiryQuoteLineMapper).insert(legacyLineCaptor.capture());
        assertThat(legacyLineCaptor.getValue().getInquiryLineId()).isEqualTo(6001L);
        assertThat(legacyLineCaptor.getValue().getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(legacyLineCaptor.getValue().getTaxRate()).isEqualByComparingTo("13.0000");

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

    @Test
    void selectingWinnerRejectsOtherPendingQuotes() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        PurchaseInquiryQuoteEntity winner = quote(7002L, 5001L, 7001L, "PENDING");
        PurchaseInquiryQuoteEntity loser = quote(7003L, 5001L, 7004L, "PENDING");
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(5001L, 6001L)));
        when(purchaseInquiryQuoteMapper.selectById(7002L)).thenReturn(winner);
        when(purchaseInquiryQuoteMapper.updateById(any(PurchaseInquiryQuoteEntity.class))).thenReturn(1);
        when(purchaseInquiryQuoteMapper.selectList(any()))
                .thenReturn(List.of(loser))
                .thenReturn(List.of(winner, loser));
        when(purchaseInquiryMapper.updateById(inquiry)).thenReturn(1);

        PurchaseInquiryResponse response = service().selectQuote(
                5001L,
                new PurchaseInquirySelectQuoteRequest(7002L)
        );

        assertThat(winner.getStatus()).isEqualTo("SELECTED");
        assertThat(loser.getStatus()).isEqualTo("REJECTED");
        assertThat(loser.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(loser.getUpdatedTime()).isEqualTo(NOW);
        assertThat(response.quotes())
                .extracting(quote -> quote.id() + ":" + quote.status())
                .containsExactly("7002:SELECTED", "7003:REJECTED");
        verify(purchaseInquiryQuoteMapper, times(2)).updateById(any(PurchaseInquiryQuoteEntity.class));
    }

    @Test
    void addQuoteRejectsSupplierOutsideCurrentTenant() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        SupplierEntity foreignSupplier = supplier(7001L);
        foreignSupplier.setCompanyId(999L);
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(supplierMapper.selectById(7001L)).thenReturn(foreignSupplier);

        assertThatThrownBy(() -> service().addQuote(
                5001L,
                new PurchaseInquiryQuoteRequest(7001L, new BigDecimal("12.50"), BigDecimal.ZERO, null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在或已停用");

        verify(purchaseInquiryQuoteMapper, never()).insert(any(PurchaseInquiryQuoteEntity.class));
        verify(purchaseInquiryMapper, never()).updateById(any(PurchaseInquiryEntity.class));
    }

    @Test
    void selectQuoteRejectsQuoteOutsideCurrentAccountBook() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        PurchaseInquiryQuoteEntity foreignQuote = quote(7002L, 5001L, 7001L, "PENDING");
        foreignQuote.setAccountBookId(999L);
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L)).thenReturn(foreignQuote);

        assertThatThrownBy(() -> service().selectQuote(
                5001L,
                new PurchaseInquirySelectQuoteRequest(7002L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("报价不存在");

        verify(purchaseInquiryQuoteMapper, never()).updateById(any(PurchaseInquiryQuoteEntity.class));
        verify(purchaseInquiryMapper, never()).updateById(any(PurchaseInquiryEntity.class));
    }

    @Test
    void quoteOptimisticLockFailureDoesNotCloseInquiry() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        PurchaseInquiryQuoteEntity winner = quote(7002L, 5001L, 7001L, "PENDING");
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L)).thenReturn(winner);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(5001L, 6001L)));
        when(purchaseInquiryQuoteMapper.updateById(winner)).thenReturn(0);

        assertThatThrownBy(() -> service().selectQuote(
                5001L,
                new PurchaseInquirySelectQuoteRequest(7002L)
        ))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("报价已被其他操作修改，请刷新后重试");

        assertThat(inquiry.getStatus()).isEqualTo("SUBMITTED");
        assertThat(inquiry.getSelectedSupplierId()).isNull();
        assertThat(inquiry.getSelectedQuoteId()).isNull();
        verify(purchaseInquiryMapper, never()).updateById(any(PurchaseInquiryEntity.class));
    }

    @Test
    void losingQuoteOptimisticLockFailureDoesNotCloseInquiry() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        PurchaseInquiryQuoteEntity winner = quote(7002L, 5001L, 7001L, "PENDING");
        PurchaseInquiryQuoteEntity loser = quote(7003L, 5001L, 7004L, "PENDING");
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L)).thenReturn(winner);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(5001L, 6001L)));
        when(purchaseInquiryQuoteMapper.updateById(winner)).thenReturn(1);
        when(purchaseInquiryQuoteMapper.updateById(loser)).thenReturn(0);
        when(purchaseInquiryQuoteMapper.selectList(any())).thenReturn(List.of(loser));

        assertThatThrownBy(() -> service().selectQuote(
                5001L,
                new PurchaseInquirySelectQuoteRequest(7002L)
        ))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("报价已被其他操作修改，请刷新后重试");

        assertThat(inquiry.getStatus()).isEqualTo("SUBMITTED");
        assertThat(inquiry.getSelectedSupplierId()).isNull();
        assertThat(inquiry.getSelectedQuoteId()).isNull();
        verify(purchaseInquiryQuoteMapper).updateById(winner);
        verify(purchaseInquiryQuoteMapper).updateById(loser);
        verify(purchaseInquiryMapper, never()).updateById(any(PurchaseInquiryEntity.class));
    }

    @Test
    void convertCreatesOneSourcedPurchaseOrderAndPersistsReverseLink() {
        stubAudit();
        Long inquiryId = 5001L;
        Long quoteId = 7002L;
        PurchaseInquiryEntity inquiry = inquiry(inquiryId, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        inquiry.setSelectedQuoteId(quoteId);
        PurchaseInquiryQuoteEntity selectedQuote = quote(quoteId, inquiryId, 7001L, "SELECTED");
        PurchaseInquiryLineEntity firstLine = line(inquiryId, 6001L);
        PurchaseInquiryLineEntity secondLine = line(inquiryId, 6002L);
        secondLine.setLineNo(2);
        secondLine.setProductId(9002L);
        secondLine.setQty(new BigDecimal("3.5000"));
        secondLine.setRemark("M10螺母");

        when(purchaseInquiryMapper.selectForUpdate(inquiryId, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(quoteId)).thenReturn(selectedQuote);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(firstLine, secondLine));
        when(purchaseInquiryQuoteLineMapper.selectList(any())).thenReturn(List.of(
                quoteLine(inquiryId, quoteId, 6001L, "12.50", "13.0000"),
                quoteLine(inquiryId, quoteId, 6002L, "21.75", "6.0000")
        ));
        when(purchaseOrderService.createFromInquiry(any(), any())).thenReturn(convertedOrder(inquiryId, quoteId));
        when(purchaseInquiryMapper.updateById(inquiry)).thenReturn(1);

        PurchaseOrderResponse result = service().convertToPurchaseOrder(inquiryId);

        assertThat(result.id()).isEqualTo(8101L);
        assertThat(result.sourceInquiryId()).isEqualTo(inquiryId);
        assertThat(result.sourceQuoteId()).isEqualTo(quoteId);

        ArgumentCaptor<PurchaseOrderCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(PurchaseOrderCreateRequest.class);
        ArgumentCaptor<PurchaseOrderInquirySource> sourceCaptor =
                ArgumentCaptor.forClass(PurchaseOrderInquirySource.class);
        verify(purchaseOrderService).createFromInquiry(requestCaptor.capture(), sourceCaptor.capture());

        PurchaseOrderCreateRequest request = requestCaptor.getValue();
        assertThat(request.supplierId()).isEqualTo(7001L);
        assertThat(request.orderDate()).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(request.deliveryDate()).isNull();
        assertThat(request.remark()).contains("RFQ202607170001").contains("紧急");
        assertThat(request.lines()).hasSize(2);
        assertThat(request.lines().get(0).productId()).isEqualTo(9001L);
        assertThat(request.lines().get(0).qty()).isEqualByComparingTo("10.0000");
        assertThat(request.lines().get(0).price()).isEqualByComparingTo("12.50");
        assertThat(request.lines().get(0).taxRate()).isEqualByComparingTo("13.0000");
        assertThat(request.lines().get(1).productId()).isEqualTo(9002L);
        assertThat(request.lines().get(1).qty()).isEqualByComparingTo("3.5000");
        assertThat(request.lines().get(1).price()).isEqualByComparingTo("21.75");
        assertThat(request.lines().get(1).taxRate()).isEqualByComparingTo("6.0000");

        assertThat(sourceCaptor.getValue()).isEqualTo(new PurchaseOrderInquirySource(
                inquiryId,
                "RFQ202607170001",
                quoteId,
                List.of(6001L, 6002L)
        ));
        assertThat(inquiry.getStatus()).isEqualTo("CONVERTED");
        assertThat(inquiry.getConvertedOrderId()).isEqualTo(8101L);
        assertThat(inquiry.getConvertedOrderNo()).isEqualTo("PO202607170001");
        assertThat(inquiry.getConvertedBy()).isEqualTo(USER_ID);
        assertThat(inquiry.getConvertedTime()).isEqualTo(NOW);
        verify(purchaseInquiryMapper).updateById(inquiry);
    }

    @Test
    void multiLineQuotePersistsOneTenantScopedPriceForEveryInquiryLine() {
        stubAudit();
        Long inquiryId = 5001L;
        PurchaseInquiryEntity inquiry = inquiry(inquiryId, "SUBMITTED");
        PurchaseInquiryLineEntity firstLine = line(inquiryId, 6001L);
        PurchaseInquiryLineEntity secondLine = line(inquiryId, 6002L);
        secondLine.setLineNo(2);
        secondLine.setProductId(9002L);

        when(purchaseInquiryMapper.selectById(inquiryId)).thenReturn(inquiry);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(firstLine, secondLine));
        when(supplierMapper.selectById(7001L)).thenReturn(supplier(7001L));
        when(purchaseInquiryQuoteMapper.exists(any())).thenReturn(false);
        when(purchaseInquiryQuoteMapper.insert(any(PurchaseInquiryQuoteEntity.class))).thenAnswer(invocation -> {
            PurchaseInquiryQuoteEntity quote = invocation.getArgument(0);
            quote.setId(7002L);
            return 1;
        });
        when(purchaseInquiryMapper.updateById(inquiry)).thenReturn(1);

        service().addQuote(inquiryId, new PurchaseInquiryQuoteRequest(
                7001L,
                null,
                null,
                List.of(
                        new PurchaseInquiryQuoteLineRequest(6001L, new BigDecimal("12.50"), new BigDecimal("13")),
                        new PurchaseInquiryQuoteLineRequest(6002L, new BigDecimal("21.75"), new BigDecimal("6"))
                ),
                "分行报价"
        ));

        ArgumentCaptor<PurchaseInquiryQuoteEntity> quoteCaptor =
                ArgumentCaptor.forClass(PurchaseInquiryQuoteEntity.class);
        verify(purchaseInquiryQuoteMapper).insert(quoteCaptor.capture());
        assertThat(quoteCaptor.getValue().getUnitPrice()).isNull();
        assertThat(quoteCaptor.getValue().getTaxRate()).isNull();

        ArgumentCaptor<PurchaseInquiryQuoteLineEntity> lineCaptor =
                ArgumentCaptor.forClass(PurchaseInquiryQuoteLineEntity.class);
        verify(purchaseInquiryQuoteLineMapper, times(2)).insert(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues())
                .extracting(PurchaseInquiryQuoteLineEntity::getInquiryLineId)
                .containsExactly(6001L, 6002L);
        assertThat(lineCaptor.getAllValues())
                .extracting(PurchaseInquiryQuoteLineEntity::getUnitPrice)
                .containsExactly(new BigDecimal("12.50"), new BigDecimal("21.75"));
        assertThat(lineCaptor.getAllValues())
                .allSatisfy(lineEntity -> {
                    assertThat(lineEntity.getCompanyId()).isEqualTo(COMPANY_ID);
                    assertThat(lineEntity.getAccountBookId()).isEqualTo(ACCOUNT_BOOK_ID);
                    assertThat(lineEntity.getInquiryId()).isEqualTo(inquiryId);
                    assertThat(lineEntity.getQuoteId()).isEqualTo(7002L);
                });
    }

    @Test
    void multiLineQuoteRejectsMissingInquiryLine() {
        PurchaseInquiryQuoteRequest request = new PurchaseInquiryQuoteRequest(
                7001L,
                null,
                null,
                List.of(new PurchaseInquiryQuoteLineRequest(
                        6001L,
                        new BigDecimal("12.50"),
                        new BigDecimal("13")
                )),
                null
        );

        stubMultiLineQuoteValidation(5001L);

        assertThatThrownBy(() -> service().addQuote(5001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("报价明细必须完整覆盖询价单明细");
        verify(purchaseInquiryQuoteMapper, never()).insert(any(PurchaseInquiryQuoteEntity.class));
    }

    @Test
    void multiLineQuoteRejectsDuplicateInquiryLine() {
        PurchaseInquiryQuoteRequest request = new PurchaseInquiryQuoteRequest(
                7001L,
                null,
                null,
                List.of(
                        new PurchaseInquiryQuoteLineRequest(6001L, new BigDecimal("12.50"), BigDecimal.ZERO),
                        new PurchaseInquiryQuoteLineRequest(6001L, new BigDecimal("13.50"), BigDecimal.ZERO)
                ),
                null
        );

        stubMultiLineQuoteValidation(5001L);

        assertThatThrownBy(() -> service().addQuote(5001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("报价明细不能重复提交询价行");
        verify(purchaseInquiryQuoteMapper, never()).insert(any(PurchaseInquiryQuoteEntity.class));
    }

    @Test
    void multiLineQuoteRejectsLineFromAnotherInquiry() {
        PurchaseInquiryQuoteRequest request = new PurchaseInquiryQuoteRequest(
                7001L,
                null,
                null,
                List.of(
                        new PurchaseInquiryQuoteLineRequest(6001L, new BigDecimal("12.50"), BigDecimal.ZERO),
                        new PurchaseInquiryQuoteLineRequest(9999L, new BigDecimal("13.50"), BigDecimal.ZERO)
                ),
                null
        );

        stubMultiLineQuoteValidation(5001L);

        assertThatThrownBy(() -> service().addQuote(5001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("报价明细不属于当前询价单");
        verify(purchaseInquiryQuoteMapper, never()).insert(any(PurchaseInquiryQuoteEntity.class));
    }

    @Test
    void quoteResponseExposesLinePrices() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        PurchaseInquiryQuoteEntity quote = quote(7002L, 5001L, 7001L, "PENDING");
        PurchaseInquiryQuoteLineEntity quoteLine = quoteLine(
                5001L,
                7002L,
                6001L,
                "12.50",
                "13.0000"
        );
        quoteLine.setId(7101L);
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(5001L, 6001L)));
        when(purchaseInquiryQuoteMapper.selectList(any())).thenReturn(List.of(quote));
        when(purchaseInquiryQuoteLineMapper.selectList(any())).thenReturn(List.of(quoteLine));

        PurchaseInquiryResponse response = service().getById(5001L);

        assertThat(response.quotes()).singleElement().satisfies(resultQuote -> {
            assertThat(resultQuote.lines()).singleElement().satisfies(resultLine -> {
                assertThat(resultLine.id()).isEqualTo(7101L);
                assertThat(resultLine.inquiryLineId()).isEqualTo(6001L);
                assertThat(resultLine.unitPrice()).isEqualByComparingTo("12.50");
                assertThat(resultLine.taxRate()).isEqualByComparingTo("13.0000");
            });
        });
    }

    @Test
    void poPrefillUsesPriceMappedByInquiryLineId() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        inquiry.setSelectedQuoteId(7002L);
        PurchaseInquiryLineEntity firstLine = line(5001L, 6001L);
        PurchaseInquiryLineEntity secondLine = line(5001L, 6002L);
        secondLine.setLineNo(2);
        secondLine.setProductId(9002L);
        when(purchaseInquiryMapper.selectById(5001L)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L))
                .thenReturn(quote(7002L, 5001L, 7001L, "SELECTED"));
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(firstLine, secondLine));
        // Return prices in reverse order to prove lookup is by inquiryLineId rather than list position.
        when(purchaseInquiryQuoteLineMapper.selectList(any())).thenReturn(List.of(
                quoteLine(5001L, 7002L, 6002L, "21.75", "6.0000"),
                quoteLine(5001L, 7002L, 6001L, "12.50", "13.0000")
        ));

        PurchaseInquiryPoPrefillResponse prefill = service().poPrefill(5001L);

        assertThat(prefill.lines()).hasSize(2);
        assertThat(prefill.lines().get(0).productId()).isEqualTo(9001L);
        assertThat(prefill.lines().get(0).price()).isEqualByComparingTo("12.50");
        assertThat(prefill.lines().get(0).taxRate()).isEqualByComparingTo("13.0000");
        assertThat(prefill.lines().get(1).productId()).isEqualTo(9002L);
        assertThat(prefill.lines().get(1).price()).isEqualByComparingTo("21.75");
        assertThat(prefill.lines().get(1).taxRate()).isEqualByComparingTo("6.0000");
    }

    @Test
    void repeatedConversionReturnsTheExistingPurchaseOrder() {
        stubAudit();
        Long inquiryId = 5001L;
        Long quoteId = 7002L;
        PurchaseInquiryEntity inquiry = inquiry(inquiryId, "CONVERTED");
        inquiry.setConvertedOrderId(8101L);
        inquiry.setConvertedOrderNo("PO202607170001");
        when(purchaseInquiryMapper.selectForUpdate(inquiryId, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);
        when(purchaseOrderService.getBySourceInquiry(8101L, inquiryId)).thenReturn(convertedOrder(inquiryId, quoteId));

        PurchaseOrderResponse result = service().convertToPurchaseOrder(inquiryId);

        assertThat(result.id()).isEqualTo(8101L);
        verify(purchaseOrderService).getBySourceInquiry(8101L, inquiryId);
        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
        verify(purchaseInquiryMapper, never()).updateById(any(PurchaseInquiryEntity.class));
    }

    @Test
    void conversionRejectsInquiryOutsideTheCurrentTenantAndBook() {
        stubAudit();
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(null);

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("询价单不存在");

        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
    }

    @Test
    void conversionRejectsNonClosedInquiryWithoutWinningQuote() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "SUBMITTED");
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅已选定中标报价的询价单可转换为采购订单");

        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
    }

    @Test
    void conversionRejectsClosedInquiryWithoutSelectedQuote() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅已选定中标报价的询价单可转换为采购订单");

        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
    }

    @Test
    void conversionRejectsQuoteThatIsNotTheSelectedWinner() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        inquiry.setSelectedQuoteId(7002L);
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L)).thenReturn(quote(7002L, 5001L, 7001L, "PENDING"));

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("询价单中标报价无效，无法转换为采购订单");

        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
    }

    @Test
    void conversionRejectsIncompleteMultiLineQuotePrices() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        inquiry.setSelectedQuoteId(7002L);
        PurchaseInquiryLineEntity firstLine = line(5001L, 6001L);
        PurchaseInquiryLineEntity secondLine = line(5001L, 6002L);
        secondLine.setLineNo(2);
        secondLine.setProductId(9002L);
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L))
                .thenReturn(quote(7002L, 5001L, 7001L, "SELECTED"));
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(firstLine, secondLine));
        when(purchaseInquiryQuoteLineMapper.selectList(any())).thenReturn(List.of(
                quoteLine(5001L, 7002L, 6001L, "12.50", "13.0000")
        ));

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("中标报价明细不完整，无法转换为采购订单");

        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
    }

    @Test
    void conversionRejectsQuoteLineOutsideCurrentTenant() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        inquiry.setSelectedQuoteId(7002L);
        PurchaseInquiryQuoteLineEntity foreignLine = quoteLine(
                5001L,
                7002L,
                6001L,
                "12.50",
                "13.0000"
        );
        foreignLine.setCompanyId(999L);
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L))
                .thenReturn(quote(7002L, 5001L, 7001L, "SELECTED"));
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(5001L, 6001L)));
        when(purchaseInquiryQuoteLineMapper.selectList(any())).thenReturn(List.of(foreignLine));

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("中标报价明细不完整，无法转换为采购订单");

        verify(purchaseOrderService, never()).createFromInquiry(any(), any());
    }

    @Test
    void purchaseOrderFailureLeavesInquiryUnchangedForTransactionRollback() {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(5001L, "CLOSED");
        inquiry.setSelectedSupplierId(7001L);
        inquiry.setSelectedQuoteId(7002L);
        when(purchaseInquiryMapper.selectForUpdate(5001L, COMPANY_ID, ACCOUNT_BOOK_ID)).thenReturn(inquiry);
        when(purchaseInquiryQuoteMapper.selectById(7002L)).thenReturn(quote(7002L, 5001L, 7001L, "SELECTED"));
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(line(5001L, 6001L)));
        when(purchaseOrderService.createFromInquiry(any(), any()))
                .thenThrow(new IllegalStateException("insert purchase order failed"));

        assertThatThrownBy(() -> service().convertToPurchaseOrder(5001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("insert purchase order failed");

        assertThat(inquiry.getStatus()).isEqualTo("CLOSED");
        assertThat(inquiry.getConvertedOrderId()).isNull();
        verify(purchaseInquiryMapper, never()).updateById(any(PurchaseInquiryEntity.class));
    }

    private PurchaseInquiryService service() {
        PurchaseInquiryQuoteService quoteService = new PurchaseInquiryQuoteService(
                purchaseInquiryLineMapper,
                purchaseInquiryQuoteMapper,
                purchaseInquiryQuoteLineMapper,
                supplierMapper
        );
        PurchaseInquiryQueryService queryService = new PurchaseInquiryQueryService(
                purchaseInquiryMapper,
                purchaseInquiryLineMapper,
                auditMetadataFactory,
                quoteService
        );
        PurchaseInquiryCommandService commandService = new PurchaseInquiryCommandService(
                purchaseInquiryMapper,
                purchaseInquiryLineMapper,
                purchaseInquiryNumberService,
                productValidator,
                auditMetadataFactory,
                purchaseOrderService,
                quoteService,
                queryService
        );
        return new PurchaseInquiryService(queryService, commandService);
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW));
    }

    private void stubMultiLineQuoteValidation(Long inquiryId) {
        stubAudit();
        PurchaseInquiryEntity inquiry = inquiry(inquiryId, "SUBMITTED");
        PurchaseInquiryLineEntity firstLine = line(inquiryId, 6001L);
        PurchaseInquiryLineEntity secondLine = line(inquiryId, 6002L);
        secondLine.setLineNo(2);
        secondLine.setProductId(9002L);
        when(purchaseInquiryMapper.selectById(inquiryId)).thenReturn(inquiry);
        when(purchaseInquiryLineMapper.selectList(any())).thenReturn(List.of(firstLine, secondLine));
        when(supplierMapper.selectById(7001L)).thenReturn(supplier(7001L));
        when(purchaseInquiryQuoteMapper.exists(any())).thenReturn(false);
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

    private PurchaseInquiryQuoteLineEntity quoteLine(
            Long inquiryId,
            Long quoteId,
            Long inquiryLineId,
            String unitPrice,
            String taxRate
    ) {
        PurchaseInquiryQuoteLineEntity line = new PurchaseInquiryQuoteLineEntity();
        line.setCompanyId(COMPANY_ID);
        line.setAccountBookId(ACCOUNT_BOOK_ID);
        line.setInquiryId(inquiryId);
        line.setQuoteId(quoteId);
        line.setInquiryLineId(inquiryLineId);
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setTaxRate(new BigDecimal(taxRate));
        line.setDeletedFlag(0);
        line.setVersion(0);
        return line;
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

    private PurchaseOrderResponse convertedOrder(Long inquiryId, Long quoteId) {
        return new PurchaseOrderResponse(
                8101L,
                "PO202607170001",
                7001L,
                "供应商A",
                LocalDate.of(2026, 7, 17),
                null,
                "DRAFT",
                "NOT_SUBMITTED",
                "NOT_RECEIVED",
                inquiryId,
                "RFQ202607170001",
                quoteId,
                new BigDecimal("10.0000"),
                new BigDecimal("125.00"),
                new BigDecimal("16.25"),
                "来源询价单 RFQ202607170001",
                List.of(new PurchaseOrderLineResponse(
                        8201L,
                        1,
                        9001L,
                        new BigDecimal("10.0000"),
                        new BigDecimal("12.50"),
                        new BigDecimal("13.0000"),
                        new BigDecimal("125.00"),
                        new BigDecimal("16.25"),
                        BigDecimal.ZERO,
                        inquiryId,
                        6001L,
                        "M8螺栓"
                ))
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }
}
