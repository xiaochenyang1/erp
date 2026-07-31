package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.service.SalesReturnNumberService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnPostingService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnQueryService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReturnServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9601L,
            101L,
            202L,
            11L,
            12L,
            "sales_return_scope_user",
            "销售退货用户"
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 30);

    @Mock
    private SalesReturnMapper salesReturnMapper;

    @Mock
    private SalesReturnLineMapper salesReturnLineMapper;

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private SalesReturnNumberService salesReturnNumberService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private SalesReturnQueryService salesReturnQueryService;

    @Mock
    private SalesReturnPostingService salesReturnPostingService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesReturnLineEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
    }

    @Test
    void postDelegatesToPostingService() {
        SalesReturnResponse expected = new SalesReturnResponse(
                9001L,
                "SR-9001",
                7001L,
                3001L,
                LocalDate.of(2026, 6, 8),
                "POSTED",
                new BigDecimal("2.0000"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                null,
                List.of()
        );
        when(salesReturnPostingService.post(9001L)).thenReturn(expected);

        SalesReturnResponse result = service().post(9001L);

        assertThat(result).isSameAs(expected);
        verify(salesReturnPostingService).post(9001L);
    }

    @Test
    void createRejectsDeliveryLineProductFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(salesDeliveryMapper.selectById(7001L)).thenReturn(delivery());
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(deliveryLine()));
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(new SalesReturnCreateRequest(
                7001L,
                LocalDate.of(2026, 6, 8),
                "tenant boundary",
                List.of(new SalesReturnLineRequest(
                        7101L,
                        new BigDecimal("2.0000"),
                        "line"
                ))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine() {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(7101L);
        entity.setDeliveryId(7001L);
        entity.setLineNo(1);
        entity.setOrderLineId(6101L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("50.00"));
        entity.setTaxAmount(new BigDecimal("0.00"));
        entity.setReturnedQty(new BigDecimal("0.0000"));
        return entity;
    }

    private SalesReturnService service() {
        return new SalesReturnService(
                salesReturnMapper,
                salesReturnLineMapper,
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                productValidator,
                salesReturnNumberService,
                auditMetadataFactory,
                salesReturnQueryService,
                salesReturnPostingService
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
