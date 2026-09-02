package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.order.service.SalesPriceEvaluator;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.price.mapper.SalesPriceMapper;
import com.tuowei.erp.sales.price.model.SalesPriceEntity;
import com.tuowei.erp.sales.price.service.SalesPriceService;
import com.tuowei.erp.sales.price.service.SalesPriceCommandService;
import com.tuowei.erp.sales.price.service.SalesPriceQueryService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesPriceEvaluatorTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long BOOK_ID = 1L;
    private static final Long CUSTOMER_ID = 11L;
    private static final Long PRODUCT_ID = 22L;

    @Mock
    private SalesPriceMapper salesPriceMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ProductValidator productValidator;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SalesPriceEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                SalesPriceEntity.class.getName()
        );
        assistant.setCurrentNamespace(SalesPriceEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SalesPriceEntity.class);
    }

    @Test
    void allowsWhenNoPriceConfigured() {
        when(salesPriceMapper.selectList(any())).thenReturn(List.of());
        SalesPriceEvaluator evaluator = evaluator();

        assertThatCode(() -> evaluator.assertLinesWithinMinPrice(
                COMPANY_ID,
                BOOK_ID,
                CUSTOMER_ID,
                LocalDate.of(2026, 7, 17),
                List.of(line(new BigDecimal("1.00")))
        )).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenPriceBelowCustomerMin() {
        SalesPriceEntity customerPrice = price(CUSTOMER_ID, new BigDecimal("100.00"), new BigDecimal("90.00"));
        when(salesPriceMapper.selectList(any())).thenReturn(List.of(customerPrice));
        SalesPriceEvaluator evaluator = evaluator();

        assertThatThrownBy(() -> evaluator.assertLinesWithinMinPrice(
                COMPANY_ID,
                BOOK_ID,
                CUSTOMER_ID,
                LocalDate.of(2026, 7, 17),
                List.of(line(new BigDecimal("80.00")))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("低于生效最低价");
    }

    @Test
    void prefersCustomerPriceOverProductPrice() {
        SalesPriceEntity productPrice = price(null, new BigDecimal("50.00"), new BigDecimal("40.00"));
        SalesPriceEntity customerPrice = price(CUSTOMER_ID, new BigDecimal("100.00"), new BigDecimal("90.00"));
        // first query customer-specific, second would be product if empty — we only return customer once
        when(salesPriceMapper.selectList(any()))
                .thenReturn(List.of(customerPrice))
                .thenReturn(List.of(productPrice));

        BigDecimal min = priceService().resolveMinPrice(
                COMPANY_ID, BOOK_ID, CUSTOMER_ID, PRODUCT_ID, LocalDate.of(2026, 7, 17)
        );
        assertThat(min).isEqualByComparingTo("90.00");
    }

    @Test
    void allowsEqualToMinPrice() {
        when(salesPriceMapper.selectList(any()))
                .thenReturn(List.of(price(null, new BigDecimal("50.00"), new BigDecimal("40.00"))));
        SalesPriceEvaluator evaluator = evaluator();

        assertThatCode(() -> evaluator.assertLinesWithinMinPrice(
                COMPANY_ID,
                BOOK_ID,
                CUSTOMER_ID,
                LocalDate.of(2026, 7, 17),
                List.of(line(new BigDecimal("40.00")))
        )).doesNotThrowAnyException();
    }

    private SalesOrderLineRequest line(BigDecimal price) {
        return new SalesOrderLineRequest(PRODUCT_ID, new BigDecimal("1.0000"), price, new BigDecimal("0.13"), null);
    }

    private SalesPriceEntity price(Long customerId, BigDecimal list, BigDecimal min) {
        SalesPriceEntity entity = new SalesPriceEntity();
        entity.setId(9001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setCustomerId(customerId);
        entity.setProductId(PRODUCT_ID);
        entity.setListPrice(list);
        entity.setMinPrice(min);
        entity.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        entity.setEffectiveTo(null);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesPriceService priceService() {
        SalesPriceQueryService queryService = new SalesPriceQueryService(
                salesPriceMapper,
                productMapper,
                customerMapper,
                productValidator,
                auditMetadataFactory
        );
        return new SalesPriceService(
                queryService,
                new SalesPriceCommandService(
                        salesPriceMapper,
                        customerMapper,
                        productValidator,
                        auditMetadataFactory,
                        queryService
                )
        );
    }

    private SalesPriceEvaluator evaluator() {
        return new SalesPriceEvaluator(priceService());
    }
}
