package com.tuowei.erp.production.order;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.service.ProductionOrderPostingService;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionOrderPostingServiceTest {

    private static final Long ORDER_ID = 6001L;
    private static final Long MATERIAL_WAREHOUSE_ID = 5001L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            9921L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 20, 0)
    );

    @Mock
    private ProductionOrderMapper orderMapper;

    @Mock
    private ProductionOrderQueryService queryService;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private ProductionOperationService productionOperationService;

    @Mock
    private AttachmentService attachmentService;

    @Test
    void releaseDraftChecksAttachmentReservesEveryMaterialUpdatesStatusAndGeneratesOperations() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_DRAFT);
        ProductionOrderMaterialEntity first = material(7001L, 4001L, "2.5000", "first material");
        ProductionOrderMaterialEntity second = material(7002L, 4002L, "3.7500", "second material");
        ProductionOrderResponse expected = response(ProductionOrderService.STATUS_RELEASED);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        when(queryService.selectMaterials(order)).thenReturn(List.of(first, second));
        when(orderMapper.updateById(order)).thenReturn(1);
        when(queryService.toResponse(order)).thenReturn(expected);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        ProductionOrderResponse result = service().release(ORDER_ID);

        assertThat(result).isSameAs(expected);
        assertThat(order.getStatus()).isEqualTo(ProductionOrderService.STATUS_RELEASED);
        assertThat(order.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(order.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(attachmentService).requireIfConfigured(AttachmentBusinessType.PRODUCTION_ORDER, ORDER_ID);

        ArgumentCaptor<InventoryReservationCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryReservationCommand.class);
        verify(inventoryPostingService, times(2)).reserve(
                commandCaptor.capture(),
                same(AUDIT),
                same("材料可用量不足，不能释放生产工单")
        );
        assertReservation(commandCaptor.getAllValues().get(0), first);
        assertReservation(commandCaptor.getAllValues().get(1), second);
        verify(orderMapper).updateById(order);
        verify(productionOperationService).generateForReleasedOrder(order, AUDIT);
        verify(queryService).toResponse(order);

        InOrder releaseOrder = inOrder(
                queryService,
                attachmentService,
                inventoryPostingService,
                orderMapper,
                productionOperationService
        );
        releaseOrder.verify(queryService).requireOrder(ORDER_ID);
        releaseOrder.verify(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.PRODUCTION_ORDER, ORDER_ID);
        releaseOrder.verify(queryService).selectMaterials(order);
        releaseOrder.verify(inventoryPostingService, times(2)).reserve(any(), same(AUDIT), any());
        releaseOrder.verify(orderMapper).updateById(order);
        releaseOrder.verify(productionOperationService).generateForReleasedOrder(order, AUDIT);
    }

    @Test
    void releaseStopsAtAttachmentGateBeforeMaterialReservationOrStatusUpdate() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_DRAFT);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        doThrow(new IllegalArgumentException("生产工单必须上传附件"))
                .when(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.PRODUCTION_ORDER, ORDER_ID);

        assertThatThrownBy(() -> service().release(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产工单必须上传附件");

        assertThat(order.getStatus()).isEqualTo(ProductionOrderService.STATUS_DRAFT);
        verify(queryService, never()).selectMaterials(any(ProductionOrderEntity.class));
        verify(inventoryPostingService, never()).reserve(any(), any(), any());
        verify(orderMapper, never()).updateById(any(ProductionOrderEntity.class));
        verify(productionOperationService, never()).generateForReleasedOrder(any(), any());
        verify(queryService, never()).toResponse(any());
    }

    @Test
    void releaseRejectsNonDraftOrderBeforeAttachmentGate() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_RELEASED);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().release(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有草稿状态的生产工单可以释放");

        verify(attachmentService, never()).requireIfConfigured(any(), any());
        verify(queryService, never()).selectMaterials(any(ProductionOrderEntity.class));
        verify(inventoryPostingService, never()).reserve(any(), any(), any());
        verify(orderMapper, never()).updateById(any(ProductionOrderEntity.class));
        verify(productionOperationService, never()).generateForReleasedOrder(any(), any());
    }

    @Test
    void releaseDoesNotGenerateOperationsWhenOrderUpdateConflicts() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_DRAFT);
        ProductionOrderMaterialEntity material = material(7001L, 4001L, "2.5000", "material");
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        when(queryService.selectMaterials(order)).thenReturn(List.of(material));
        when(orderMapper.updateById(order)).thenReturn(0);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        assertThatThrownBy(() -> service().release(ORDER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("生产工单已被其他操作修改，请重试");

        verify(inventoryPostingService).reserve(any(), same(AUDIT), any());
        verify(productionOperationService, never()).generateForReleasedOrder(any(), any());
        verify(queryService, never()).toResponse(any());
    }

    @Test
    void cancelReleasedOrderReleasesReservationsUpdatesStatusAndReturnsResponse() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_RELEASED);
        ProductionOrderResponse expected = response(ProductionOrderService.STATUS_CANCELLED);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        when(orderMapper.updateById(order)).thenReturn(1);
        when(queryService.toResponse(order)).thenReturn(expected);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        ProductionOrderResponse result = service().cancel(ORDER_ID);

        assertThat(result).isSameAs(expected);
        assertThat(order.getStatus()).isEqualTo(ProductionOrderService.STATUS_CANCELLED);
        assertThat(order.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(order.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(inventoryPostingService).releaseAllReservations(
                ProductionOrderService.SOURCE_TYPE,
                ORDER_ID,
                AUDIT
        );
        verify(orderMapper).updateById(order);
        verify(queryService).toResponse(order);
    }

    @Test
    void cancelDraftOrderDoesNotReleaseReservations() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_DRAFT);
        ProductionOrderResponse expected = response(ProductionOrderService.STATUS_CANCELLED);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        when(orderMapper.updateById(order)).thenReturn(1);
        when(queryService.toResponse(order)).thenReturn(expected);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        ProductionOrderResponse result = service().cancel(ORDER_ID);

        assertThat(result).isSameAs(expected);
        assertThat(order.getStatus()).isEqualTo(ProductionOrderService.STATUS_CANCELLED);
        verify(inventoryPostingService, never()).releaseAllReservations(any(), any(), any());
        verify(orderMapper).updateById(order);
    }

    @Test
    void cancelDoesNotReturnResponseWhenOrderUpdateConflicts() {
        ProductionOrderEntity order = order(ProductionOrderService.STATUS_RELEASED);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        when(orderMapper.updateById(order)).thenReturn(0);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        assertThatThrownBy(() -> service().cancel(ORDER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("生产工单已被其他操作修改，请重试");

        verify(inventoryPostingService).releaseAllReservations(
                ProductionOrderService.SOURCE_TYPE,
                ORDER_ID,
                AUDIT
        );
        verify(queryService, never()).toResponse(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ProductionOrderService.STATUS_MATERIAL_ISSUED,
            ProductionOrderService.STATUS_COMPLETED
    })
    void cancelRejectsIssuedOrCompletedOrder(String status) {
        ProductionOrderEntity order = order(status);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().cancel(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已领料或已完工的生产工单不能取消");

        assertThat(order.getStatus()).isEqualTo(status);
        verify(inventoryPostingService, never()).releaseAllReservations(any(), any(), any());
        verify(orderMapper, never()).updateById(any(ProductionOrderEntity.class));
        verify(queryService, never()).toResponse(any());
    }

    private ProductionOrderPostingService service() {
        return new ProductionOrderPostingService(
                orderMapper,
                inventoryPostingService,
                auditMetadataFactory,
                queryService,
                productionOperationService,
                attachmentService
        );
    }

    private ProductionOrderEntity order(String status) {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(ORDER_ID);
        order.setCompanyId(AUDIT.companyId());
        order.setAccountBookId(AUDIT.accountBookId());
        order.setOrderNo("MO-6001");
        order.setMaterialWarehouseId(MATERIAL_WAREHOUSE_ID);
        order.setStatus(status);
        order.setDeletedFlag(0);
        return order;
    }

    private ProductionOrderMaterialEntity material(
            Long id,
            Long productId,
            String requiredQty,
            String remark
    ) {
        ProductionOrderMaterialEntity material = new ProductionOrderMaterialEntity();
        material.setId(id);
        material.setCompanyId(AUDIT.companyId());
        material.setAccountBookId(AUDIT.accountBookId());
        material.setOrderId(ORDER_ID);
        material.setMaterialProductId(productId);
        material.setRequiredQty(new BigDecimal(requiredQty));
        material.setRemark(remark);
        return material;
    }

    private ProductionOrderResponse response(String status) {
        return new ProductionOrderResponse(
                ORDER_ID,
                "MO-6001",
                3001L,
                4000L,
                5002L,
                MATERIAL_WAREHOUSE_ID,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                null,
                null,
                status,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "posting test",
                List.of()
        );
    }

    private void assertReservation(
            InventoryReservationCommand command,
            ProductionOrderMaterialEntity material
    ) {
        assertThat(command.warehouseId()).isEqualTo(MATERIAL_WAREHOUSE_ID);
        assertThat(command.productId()).isEqualTo(material.getMaterialProductId());
        assertThat(command.sourceType()).isEqualTo(ProductionOrderService.SOURCE_TYPE);
        assertThat(command.sourceId()).isEqualTo(ORDER_ID);
        assertThat(command.sourceNo()).isEqualTo("MO-6001");
        assertThat(command.sourceLineId()).isEqualTo(material.getId());
        assertThat(command.qty()).isEqualByComparingTo(material.getRequiredQty());
        assertThat(command.remark()).isEqualTo(material.getRemark());
    }
}
