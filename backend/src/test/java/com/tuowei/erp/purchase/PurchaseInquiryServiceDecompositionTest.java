package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQueryService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryCommandService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryNumberService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryService;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryUpdateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquirySelectQuoteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInquiryServiceDecompositionTest {

    @Test
    void purchaseInquiryServiceKeepsReadAndQuoteResponsibilitiesBehindDedicatedServices() {
        assertThat(constructorDependencies(PurchaseInquiryService.class))
                .contains(PurchaseInquiryQueryService.class, PurchaseInquiryCommandService.class)
                .doesNotContain(
                        PurchaseInquiryQuoteMapper.class,
                        PurchaseInquiryQuoteLineMapper.class,
                        SupplierMapper.class
                );
        assertThat(constructorDependencies(PurchaseInquiryQueryService.class))
                .containsExactlyInAnyOrder(
                        PurchaseInquiryMapper.class,
                        PurchaseInquiryLineMapper.class,
                        AuditMetadataFactory.class,
                        PurchaseInquiryQuoteService.class
                )
                .doesNotContain(PurchaseInquiryService.class);
        assertThat(constructorDependencies(PurchaseInquiryCommandService.class))
                .containsExactlyInAnyOrder(
                        PurchaseInquiryMapper.class,
                        PurchaseInquiryLineMapper.class,
                        PurchaseInquiryNumberService.class,
                        ProductValidator.class,
                        AuditMetadataFactory.class,
                        PurchaseOrderService.class,
                        PurchaseInquiryQuoteService.class,
                        PurchaseInquiryQueryService.class
                )
                .doesNotContain(PurchaseInquiryService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        assertReadOnly(PurchaseInquiryService.class.getDeclaredMethod(
                "list", PurchaseInquiryPageQuery.class));
        assertReadOnly(PurchaseInquiryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseInquiryService.class.getDeclaredMethod("poPrefill", Long.class));

        assertReadOnly(PurchaseInquiryQueryService.class.getDeclaredMethod(
                "list", PurchaseInquiryPageQuery.class));
        assertReadOnly(PurchaseInquiryQueryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseInquiryQueryService.class.getDeclaredMethod("poPrefill", Long.class));
    }

    @Test
    void quoteCommandsKeepOneRequiredTransactionAtTheFacade() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{PurchaseInquiryService.class, PurchaseInquiryCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("create", PurchaseInquiryCreateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("update", Long.class, PurchaseInquiryUpdateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("submit", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("convertToPurchaseOrder", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("cancel", Long.class));
        }
        assertRequiredWriteTransaction(PurchaseInquiryService.class.getDeclaredMethod(
                "addQuote",
                Long.class,
                PurchaseInquiryQuoteRequest.class
        ));
        assertRequiredWriteTransaction(PurchaseInquiryCommandService.class.getDeclaredMethod(
                "addQuote",
                Long.class,
                PurchaseInquiryQuoteRequest.class
        ));
        assertRequiredWriteTransaction(PurchaseInquiryCommandService.class.getDeclaredMethod(
                "selectQuote",
                Long.class,
                PurchaseInquirySelectQuoteRequest.class
        ));
        assertRequiredWriteTransaction(PurchaseInquiryService.class.getDeclaredMethod(
                "selectQuote",
                Long.class,
                PurchaseInquirySelectQuoteRequest.class
        ));

        assertThat(PurchaseInquiryQuoteService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(PurchaseInquiryQuoteService.class.getDeclaredMethod(
                "addQuote",
                PurchaseInquiryEntity.class,
                PurchaseInquiryQuoteRequest.class,
                AuditMetadata.class
        ).getAnnotation(Transactional.class)).isNull();
        assertThat(PurchaseInquiryQuoteService.class.getDeclaredMethod(
                "selectWinningQuote",
                PurchaseInquiryEntity.class,
                Long.class,
                AuditMetadata.class
        ).getAnnotation(Transactional.class)).isNull();
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
