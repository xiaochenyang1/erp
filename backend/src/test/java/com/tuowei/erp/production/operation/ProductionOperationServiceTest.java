package com.tuowei.erp.production.operation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionOperationServiceTest {

    @Mock private ProductionOrderOperationMapper operationMapper;
    @Mock private ProductionOrderMapper orderMapper;
    @Mock private ProductionRoutingMapper routingMapper;
    @Mock private ProductionRoutingOperationMapper routingOperationMapper;
    @Mock private ProductionWorkCenterMapper workCenterMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void init() {
        if (TableInfoHelper.getTableInfo(ProductionOrderOperationEntity.class) == null) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), ProductionOrderOperationEntity.class.getName());
            a.setCurrentNamespace(ProductionOrderOperationEntity.class.getName());
            TableInfoHelper.initTableInfo(a, ProductionOrderOperationEntity.class);
        }
    }

    @Test
    void completeBlockedWhenOperationNotDone() {
        ProductionOrderEntity order = order();
        ProductionOrderOperationEntity op = op(order, "PENDING", "5.0000");
        when(operationMapper.selectList(any())).thenReturn(List.of(op));

        assertThatThrownBy(() -> service().assertReadyForCompletion(order, new BigDecimal("5.0000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工序未完成报工");
    }

    @Test
    void completeBlockedWhenQualifiedInsufficient() {
        ProductionOrderEntity order = order();
        ProductionOrderOperationEntity op = op(order, "DONE", "3.0000");
        when(operationMapper.selectList(any())).thenReturn(List.of(op));

        assertThatThrownBy(() -> service().assertReadyForCompletion(order, new BigDecimal("5.0000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合格量不足");
    }

    @Test
    void completeAllowedWhenAllDoneAndQualifiedEnough() {
        ProductionOrderEntity order = order();
        ProductionOrderOperationEntity op = op(order, "DONE", "5.0000");
        when(operationMapper.selectList(any())).thenReturn(List.of(op));

        assertThatCode(() -> service().assertReadyForCompletion(order, new BigDecimal("5.0000")))
                .doesNotThrowAnyException();
    }

    @Test
    void completeAllowedWhenNoOperations() {
        ProductionOrderEntity order = order();
        when(operationMapper.selectList(any())).thenReturn(List.of());
        assertThatCode(() -> service().assertReadyForCompletion(order, new BigDecimal("5.0000")))
                .doesNotThrowAnyException();
    }

    private ProductionOrderEntity order() {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(1L);
        order.setCompanyId(1L);
        order.setAccountBookId(1L);
        order.setStatus(ProductionOrderService.STATUS_MATERIAL_ISSUED);
        order.setPlannedQty(new BigDecimal("5.0000"));
        order.setDeletedFlag(0);
        return order;
    }

    private ProductionOrderOperationEntity op(ProductionOrderEntity order, String status, String qualified) {
        ProductionOrderOperationEntity entity = new ProductionOrderOperationEntity();
        entity.setId(9L);
        entity.setCompanyId(order.getCompanyId());
        entity.setAccountBookId(order.getAccountBookId());
        entity.setOrderId(order.getId());
        entity.setLineNo(1);
        entity.setOperationCode("OP10");
        entity.setOperationName("下料");
        entity.setPlannedQty(new BigDecimal("5.0000"));
        entity.setReportedQty(new BigDecimal(qualified));
        entity.setQualifiedQty(new BigDecimal(qualified));
        entity.setScrapQty(BigDecimal.ZERO);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionOperationService service() {
        return new ProductionOperationService(
                operationMapper,
                orderMapper,
                routingMapper,
                routingOperationMapper,
                workCenterMapper,
                auditMetadataFactory
        );
    }
}
