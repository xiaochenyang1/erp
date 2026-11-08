package com.tuowei.erp.production.workcenter;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterCommandService;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterQueryService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionWorkCenterCapacityTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(
            7L, 1L, 1L, LocalDateTime.of(2026, 11, 8, 15, 0));

    private final ProductionWorkCenterMapper mapper = mock(ProductionWorkCenterMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final ProductionWorkCenterQueryService queryService = new ProductionWorkCenterQueryService(
            mapper, auditMetadataFactory);
    private final ProductionWorkCenterCommandService commandService = new ProductionWorkCenterCommandService(
            mapper,
            mock(ProductionRoutingMapper.class),
            mock(ProductionRoutingOperationMapper.class),
            auditMetadataFactory,
            queryService
    );

    @BeforeEach
    void setup() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.insert(any(ProductionWorkCenterEntity.class))).thenAnswer(invocation -> {
            ProductionWorkCenterEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });
        when(mapper.updateById(any(ProductionWorkCenterEntity.class))).thenReturn(1);
    }

    @Test
    void persistsCustomCapacityAndNormalizesStartTimeToMinutes() {
        var created = commandService.create(new ProductionWorkCenterCreateRequest(
                " WC-001 ", " Assembly ", 600, LocalTime.of(7, 30, 45), " first "));

        assertThat(created.workCenterCode()).isEqualTo("WC-001");
        assertThat(created.workCenterName()).isEqualTo("Assembly");
        assertThat(created.dailyCapacityMinutes()).isEqualTo(600);
        assertThat(created.workdayStartTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(created.remark()).isEqualTo("first");

        ArgumentCaptor<ProductionWorkCenterEntity> entity = ArgumentCaptor.forClass(ProductionWorkCenterEntity.class);
        org.mockito.Mockito.verify(mapper).insert(entity.capture());
        assertThat(entity.getValue().getDailyCapacityMinutes()).isEqualTo(600);
        assertThat(entity.getValue().getWorkdayStartTime()).isEqualTo(LocalTime.of(7, 30));
    }

    @Test
    void defaultsCapacityOnCreateAndPreservesItWhenLegacyUpdateOmitsFields() {
        var created = commandService.create(new ProductionWorkCenterCreateRequest("WC-002", "Cutting", "legacy"));
        assertThat(created.dailyCapacityMinutes()).isEqualTo(480);
        assertThat(created.workdayStartTime()).isEqualTo(LocalTime.of(8, 0));

        ProductionWorkCenterEntity stored = new ProductionWorkCenterEntity();
        stored.setId(102L);
        stored.setCompanyId(1L);
        stored.setAccountBookId(1L);
        stored.setWorkCenterCode("WC-002");
        stored.setWorkCenterName("Cutting");
        stored.setDailyCapacityMinutes(720);
        stored.setWorkdayStartTime(LocalTime.of(6, 0));
        stored.setStatus("ACTIVE");
        stored.setDeletedFlag(0);
        stored.setVersion(0);
        when(mapper.selectById(102L)).thenReturn(stored);

        var updated = commandService.update(102L, new ProductionWorkCenterUpdateRequest("Cutting 2", "updated"));

        assertThat(updated.dailyCapacityMinutes()).isEqualTo(720);
        assertThat(updated.workdayStartTime()).isEqualTo(LocalTime.of(6, 0));
    }
}
