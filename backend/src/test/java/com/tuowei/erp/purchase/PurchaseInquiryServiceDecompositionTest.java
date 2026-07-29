package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryService;
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
    void purchaseInquiryServiceKeepsQuotePersistenceBehindDedicatedService() {
        assertThat(constructorDependencies(PurchaseInquiryService.class))
                .contains(PurchaseInquiryQuoteService.class)
                .doesNotContain(
                        PurchaseInquiryQuoteMapper.class,
                        PurchaseInquiryQuoteLineMapper.class,
                        SupplierMapper.class
                );
    }

    @Test
    void quoteCommandsKeepOneRequiredTransactionAtTheFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(PurchaseInquiryService.class.getDeclaredMethod(
                "addQuote",
                Long.class,
                PurchaseInquiryQuoteRequest.class
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
}
