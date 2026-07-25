package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.service.PurchasePriceEvaluator;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.price.mapper.PurchasePriceMapper;
import com.tuowei.erp.purchase.price.model.PurchasePriceEntity;
import com.tuowei.erp.purchase.price.service.PurchasePriceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchasePriceEvaluatorTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long BOOK_ID = 1L;
    private static final Long SUPPLIER_ID = 11L;
    private static final Long PRODUCT_ID = 22L;

    @Mock
    private PurchasePriceMapper purchasePriceMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private ProductValidator productValidator;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(PurchasePriceEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                PurchasePriceEntity.class.getName()
        );
        assistant.setCurrentNamespace(PurchasePriceEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, PurchasePriceEntity.class);
    }

    @Test
    void allowsWhenNoPriceConfigured() {
        when(purchasePriceMapper.selectList(any())).thenReturn(List.of());
        PurchasePriceEvaluator evaluator = evaluator();

        assertThatCode(() -> evaluator.assertLinesWithinMaxPrice(
                COMPANY_ID,
                BOOK_ID,
                SUPPLIER_ID,
                LocalDate.of(2026, 7, 25),
                List.of(line(new BigDecimal("100.00")))
        )).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenPriceAboveSupplierMax() {
        PurchasePriceEntity supplierPrice = price(SUPPLIER_ID, new BigDecimal("80.00"), new BigDecimal("90.00"));
        when(purchasePriceMapper.selectList(any())).thenReturn(List.of(supplierPrice));
        PurchasePriceEvaluator evaluator = evaluator();

        assertThatThrownBy(() -> evaluator.assertLinesWithinMaxPrice(
                COMPANY_ID,
                BOOK_ID,
                SUPPLIER_ID,
                LocalDate.of(2026, 7, 25),
                List.of(line(new BigDecimal("95.00")))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("高于生效最高价");
    }

    @Test
    void prefersSupplierPriceOverProductPrice() {
        PurchasePriceEntity productPrice = price(null, new BigDecimal("50.00"), new BigDecimal("60.00"));
        PurchasePriceEntity supplierPrice = price(SUPPLIER_ID, new BigDecimal("80.00"), new BigDecimal("90.00"));
        when(purchasePriceMapper.selectList(any()))
                .thenReturn(List.of(supplierPrice))
                .thenReturn(List.of(productPrice));

        BigDecimal max = priceService().resolveMaxPrice(
                COMPANY_ID, BOOK_ID, SUPPLIER_ID, PRODUCT_ID, LocalDate.of(2026, 7, 25)
        );
        assertThat(max).isEqualByComparingTo("90.00");
    }

    @Test
    void allowsEqualToMaxPrice() {
        when(purchasePriceMapper.selectList(any()))
                .thenReturn(List.of(price(null, new BigDecimal("50.00"), new BigDecimal("60.00"))));
        PurchasePriceEvaluator evaluator = evaluator();

        assertThatCode(() -> evaluator.assertLinesWithinMaxPrice(
                COMPANY_ID,
                BOOK_ID,
                SUPPLIER_ID,
                LocalDate.of(2026, 7, 25),
                List.of(line(new BigDecimal("60.00")))
        )).doesNotThrowAnyException();
    }

    private PurchaseOrderLineRequest line(BigDecimal price) {
        return new PurchaseOrderLineRequest(PRODUCT_ID, new BigDecimal("1.0000"), price, new BigDecimal("0.13"), null);
    }

    private PurchasePriceEntity price(Long supplierId, BigDecimal list, BigDecimal max) {
        PurchasePriceEntity entity = new PurchasePriceEntity();
        entity.setId(9001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setSupplierId(supplierId);
        entity.setProductId(PRODUCT_ID);
        entity.setListPrice(list);
        entity.setMaxPrice(max);
        entity.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        entity.setEffectiveTo(null);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchasePriceService priceService() {
        return new PurchasePriceService(
                purchasePriceMapper,
                productMapper,
                supplierMapper,
                productValidator,
                auditMetadataFactory
        );
    }

    private PurchasePriceEvaluator evaluator() {
        return new PurchasePriceEvaluator(priceService());
    }
}
