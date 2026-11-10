package com.tuowei.erp.production.routing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRoutingCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            8001L,
            9001L,
            LocalDateTime.of(2026, 8, 20, 11, 30)
    );
    private static final Long ROUTING_ID = 701L;
    private static final Long BOM_ID = 901L;
    private static final Long WORK_CENTER_ID = 1001L;

    @Mock
    private ProductionRoutingMapper routingMapper;
    @Mock
    private ProductionRoutingOperationMapper routingOperationMapper;
    @Mock
    private ProductionBomMapper bomMapper;
    @Mock
    private ProductionWorkCenterMapper workCenterMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private ProductionRoutingQueryService routingQueryService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionRoutingEntity.class);
        initTableInfo(ProductionRoutingOperationEntity.class);
    }

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createRunsAllPrechecksBeforeWritingAndRoundsOperations() {
        ProductionBomEntity bom = activeBom(BOM_ID);
        ProductionWorkCenterEntity workCenter = activeWorkCenter(WORK_CENTER_ID);
        when(bomMapper.selectById(BOM_ID)).thenReturn(bom);
        when(routingMapper.selectCount(any())).thenReturn(0L);
        when(workCenterMapper.selectById(WORK_CENTER_ID)).thenReturn(workCenter);
        when(routingMapper.insert(any(ProductionRoutingEntity.class))).thenAnswer(invocation -> {
            ProductionRoutingEntity entity = invocation.getArgument(0);
            entity.setId(ROUTING_ID);
            return 1;
        });
        when(routingOperationMapper.insert(any(ProductionRoutingOperationEntity.class))).thenReturn(1);
        ProductionRoutingResponse expected = response();
        when(routingQueryService.toResponse(any(ProductionRoutingEntity.class), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(expected);

        ProductionRoutingResponse actual = service().create(new ProductionRoutingCreateRequest(
                " RT-701 ",
                " 标准路线 ",
                BOM_ID,
                " 备注 ",
                List.of(
                        new ProductionRoutingOperationRequest(
                                " OP-10 ", " 切割 ", WORK_CENTER_ID,
                                new BigDecimal("12.345"), " 首工序 "
                        ),
                        new ProductionRoutingOperationRequest(
                                "OP-20", "装配", WORK_CENTER_ID,
                                new BigDecimal("18.005"), "二工序"
                        )
                )
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<ProductionRoutingEntity> routingCaptor = ArgumentCaptor.forClass(ProductionRoutingEntity.class);
        verify(routingMapper).insert(routingCaptor.capture());
        ProductionRoutingEntity insertedRouting = routingCaptor.getValue();
        assertThat(insertedRouting.getRoutingCode()).isEqualTo("RT-701");
        assertThat(insertedRouting.getRoutingName()).isEqualTo("标准路线");
        assertThat(insertedRouting.getRemark()).isEqualTo("备注");
        assertThat(insertedRouting.getStatus()).isEqualTo("ACTIVE");
        assertThat(insertedRouting.getDeletedFlag()).isZero();
        assertThat(insertedRouting.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(insertedRouting.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(insertedRouting.getVersion()).isZero();

        ArgumentCaptor<ProductionRoutingOperationEntity> operationCaptor =
                ArgumentCaptor.forClass(ProductionRoutingOperationEntity.class);
        verify(routingOperationMapper, times(2)).insert(operationCaptor.capture());
        List<ProductionRoutingOperationEntity> operations = operationCaptor.getAllValues();
        assertThat(operations).extracting(ProductionRoutingOperationEntity::getLineNo).containsExactly(1, 2);
        assertThat(operations).extracting(ProductionRoutingOperationEntity::getOperationCode)
                .containsExactly("OP-10", "OP-20");
        assertThat(operations).extracting(ProductionRoutingOperationEntity::getStandardMinutes)
                .containsExactly(new BigDecimal("12.35"), new BigDecimal("18.01"));
        assertThat(operations).allSatisfy(operation -> {
            assertThat(operation.getRoutingId()).isEqualTo(ROUTING_ID);
            assertThat(operation.getCompanyId()).isEqualTo(AUDIT.companyId());
            assertThat(operation.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
            assertThat(operation.getCreatedTime()).isEqualTo(AUDIT.now());
            assertThat(operation.getVersion()).isZero();
        });

        InOrder order = inOrder(bomMapper, routingMapper, workCenterMapper, routingOperationMapper, routingQueryService);
        order.verify(bomMapper).selectById(BOM_ID);
        order.verify(routingMapper, times(2)).selectCount(any());
        order.verify(workCenterMapper, times(2)).selectById(WORK_CENTER_ID);
        order.verify(routingMapper).insert(any(ProductionRoutingEntity.class));
        order.verify(routingOperationMapper, times(2)).insert(any(ProductionRoutingOperationEntity.class));
        order.verify(routingQueryService).toResponse(any(ProductionRoutingEntity.class), eq(AUDIT.companyId()), eq(AUDIT.accountBookId()));
    }

    @Test
    void createRejectsDuplicateBomBeforeAnyWrite() {
        when(bomMapper.selectById(BOM_ID)).thenReturn(activeBom(BOM_ID));
        when(routingMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(operationRequest("OP-10", new BigDecimal("10")))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前BOM已存在工艺路线");

        verify(routingMapper, never()).insert(any(ProductionRoutingEntity.class));
        verify(routingOperationMapper, never()).insert(any(ProductionRoutingOperationEntity.class));
        verify(workCenterMapper, never()).selectById(any());
    }

    @Test
    void createRejectsDuplicateOperationAndNonPositiveMinutesBeforeAnyWrite() {
        when(bomMapper.selectById(BOM_ID)).thenReturn(activeBom(BOM_ID));
        when(routingMapper.selectCount(any())).thenReturn(0L, 0L);
        when(workCenterMapper.selectById(WORK_CENTER_ID)).thenReturn(activeWorkCenter(WORK_CENTER_ID));

        assertThatThrownBy(() -> service().create(createRequest(List.of(
                operationRequest("OP-10", new BigDecimal("10")),
                operationRequest("OP-10", new BigDecimal("11"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工序编码不能重复");
        verify(routingMapper, never()).insert(any(ProductionRoutingEntity.class));

        assertThatThrownBy(() -> service().create(createRequest(List.of(
                operationRequest("OP-20", new BigDecimal("0.004"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("标准工时必须大于0");
        verify(routingMapper, never()).insert(any(ProductionRoutingEntity.class));
    }

    @Test
    void updateDoesNotDeleteOperationsWhenOptimisticLockFails() {
        ProductionRoutingEntity routing = activeRouting();
        when(routingQueryService.requireRouting(ROUTING_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(routing);
        when(bomMapper.selectById(BOM_ID)).thenReturn(activeBom(BOM_ID));
        when(workCenterMapper.selectById(WORK_CENTER_ID)).thenReturn(activeWorkCenter(WORK_CENTER_ID));
        when(routingMapper.updateById(routing)).thenReturn(0);

        assertThatThrownBy(() -> service().update(ROUTING_ID, new ProductionRoutingUpdateRequest(
                "更新路线", "remark", List.of(operationRequest("OP-30", new BigDecimal("5")))
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("工艺路线已被其他操作修改，请刷新后重试");

        verify(routingOperationMapper, never()).delete(any());
        verify(routingOperationMapper, never()).insert(any(ProductionRoutingOperationEntity.class));
    }

    @Test
    void updateUsesTenantScopedDeleteAndRebuildsLinesAfterHeadUpdate() {
        ProductionRoutingEntity routing = activeRouting();
        when(routingQueryService.requireRouting(ROUTING_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(routing);
        when(bomMapper.selectById(BOM_ID)).thenReturn(activeBom(BOM_ID));
        when(workCenterMapper.selectById(WORK_CENTER_ID)).thenReturn(activeWorkCenter(WORK_CENTER_ID));
        when(routingMapper.updateById(routing)).thenReturn(1);
        when(routingOperationMapper.delete(any())).thenReturn(2);
        when(routingOperationMapper.insert(any(ProductionRoutingOperationEntity.class))).thenReturn(1);
        when(routingQueryService.toResponse(any(ProductionRoutingEntity.class), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(response());

        service().update(ROUTING_ID, new ProductionRoutingUpdateRequest(
                " 更新路线 ", " 更新备注 ", List.of(operationRequest("OP-30", new BigDecimal("5")))
        ));

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionRoutingOperationEntity>> deleteCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(routingOperationMapper).delete(deleteCaptor.capture());
        String sql = deleteCaptor.getValue().getSqlSegment().toLowerCase();
        assertThat(sql).contains("company_id", "account_book_id", "routing_id");
        ArgumentCaptor<ProductionRoutingOperationEntity> operationCaptor =
                ArgumentCaptor.forClass(ProductionRoutingOperationEntity.class);
        verify(routingOperationMapper).insert(operationCaptor.capture());
        assertThat(operationCaptor.getValue().getLineNo()).isEqualTo(1);
        assertThat(operationCaptor.getValue().getOperationCode()).isEqualTo("OP-30");
        assertThat(routing.getRoutingName()).isEqualTo("更新路线");
        assertThat(routing.getRemark()).isEqualTo("更新备注");
    }

    @Test
    void enableAndDisableUpdateStatusWithOptimisticLockAndReturnHydratedResponse() {
        ProductionRoutingEntity routing = activeRouting();
        when(routingQueryService.requireRouting(ROUTING_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(routing);
        when(routingMapper.updateById(routing)).thenReturn(1);
        when(routingQueryService.toResponse(any(ProductionRoutingEntity.class), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(response());

        service().disable(ROUTING_ID);
        assertThat(routing.getStatus()).isEqualTo("DISABLED");
        service().enable(ROUTING_ID);
        assertThat(routing.getStatus()).isEqualTo("ACTIVE");
        verify(routingMapper, times(2)).updateById(routing);
        verify(routingQueryService, times(2)).toResponse(any(ProductionRoutingEntity.class), eq(AUDIT.companyId()), eq(AUDIT.accountBookId()));
    }

    private ProductionRoutingCommandService service() {
        return new ProductionRoutingCommandService(
                routingMapper,
                routingOperationMapper,
                bomMapper,
                workCenterMapper,
                auditMetadataFactory,
                routingQueryService
        );
    }

    private ProductionRoutingCreateRequest createRequest(List<ProductionRoutingOperationRequest> operations) {
        return new ProductionRoutingCreateRequest("RT-701", "路线", BOM_ID, null, operations);
    }

    private ProductionRoutingOperationRequest operationRequest(String code, BigDecimal minutes) {
        return new ProductionRoutingOperationRequest(code, "工序", WORK_CENTER_ID, minutes, null);
    }

    private ProductionBomEntity activeBom(Long id) {
        ProductionBomEntity entity = new ProductionBomEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBomNo("BOM-" + id);
        entity.setProductId(3001L);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionWorkCenterEntity activeWorkCenter(Long id) {
        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWorkCenterCode("WC-" + id);
        entity.setWorkCenterName("装配中心");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionRoutingEntity activeRouting() {
        ProductionRoutingEntity entity = new ProductionRoutingEntity();
        entity.setId(ROUTING_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRoutingCode("RT-701");
        entity.setRoutingName("原路线");
        entity.setBomId(BOM_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setVersion(2);
        return entity;
    }

    private ProductionRoutingResponse response() {
        return new ProductionRoutingResponse(
                ROUTING_ID, "RT-701", "路线", BOM_ID, "BOM-901", 3001L,
                "ACTIVE", null, List.of()
        );
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                entityType.getName()
        );
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
