package com.tuowei.erp.system.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.service.UserDataScopeService;
import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataScopeServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserDataScopeMapper userDataScopeMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SecurityPrincipalCache principalCache;
    @Mock private DataScopeService dataScopeService;

    private UserDataScopeService service;

    @BeforeEach
    void setUp() {
        service = new UserDataScopeService(
                userMapper,
                userDataScopeMapper,
                warehouseMapper,
                auditMetadataFactory,
                principalCache,
                dataScopeService
        );
    }

    @Test
    void assignWarehouseScopePersistsRowsAndEvictsPrincipalCache() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9001L)).thenReturn(activeUser(9001L));
        when(warehouseMapper.selectById(4501L)).thenReturn(activeWarehouse(4501L));
        when(userDataScopeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(userDataScopeMapper.insert(any(UserDataScopeEntity.class))).thenReturn(1);
        when(dataScopeService.buildSnapshot(eq(9001L), eq(1L), eq(1L)))
                .thenReturn(new DataScopeSnapshot(false, true, false, true, Set.of(4501L, 4600L)));

        UserDataScopeResponse response = service.assign(9001L, new UserDataScopeAssignRequest(
                false,
                true,
                false,
                true,
                List.of(4501L, 4501L)
        ));

        ArgumentCaptor<UserDataScopeEntity> captor = ArgumentCaptor.forClass(UserDataScopeEntity.class);
        verify(userDataScopeMapper, atLeastOnce()).insert(captor.capture());
        List<UserDataScopeEntity> rows = captor.getAllValues();
        assertThat(rows).extracting(UserDataScopeEntity::getScopeType)
                .containsExactlyInAnyOrder("DEPT", "SELF", "WAREHOUSE");
        assertThat(rows.stream().filter(r -> "WAREHOUSE".equals(r.getScopeType())).findFirst().orElseThrow().getWarehouseId())
                .isEqualTo(4501L);

        assertThat(response.deptScoped()).isTrue();
        assertThat(response.selfScoped()).isTrue();
        assertThat(response.hasAllScope()).isFalse();
        assertThat(response.warehouseIds()).containsExactly(4501L);
        assertThat(response.effectiveDeptScoped()).isTrue();
        assertThat(response.effectiveSelfScoped()).isTrue();
        assertThat(response.effectiveWarehouseIds()).containsExactly(4501L, 4600L);
        verify(principalCache).evictUser(9001L);
    }

    @Test
    void assignAllScopeClearsWarehouseList() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9001L)).thenReturn(activeUser(9001L));
        when(userDataScopeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(userDataScopeMapper.insert(any(UserDataScopeEntity.class))).thenReturn(1);
        when(dataScopeService.buildSnapshot(eq(9001L), eq(1L), eq(1L)))
                .thenReturn(DataScopeSnapshot.all());

        UserDataScopeResponse response = service.assign(9001L, new UserDataScopeAssignRequest(
                true,
                true,
                true,
                true,
                List.of(4501L)
        ));

        ArgumentCaptor<UserDataScopeEntity> captor = ArgumentCaptor.forClass(UserDataScopeEntity.class);
        verify(userDataScopeMapper).insert(captor.capture());
        assertThat(captor.getValue().getScopeType()).isEqualTo("ALL");
        assertThat(captor.getValue().getWarehouseId()).isNull();
        assertThat(response.hasAllScope()).isTrue();
        assertThat(response.warehouseIds()).isEmpty();
        assertThat(response.effectiveHasAllScope()).isTrue();
        assertThat(response.effectiveWarehouseIds()).isEmpty();
    }

    @Test
    void assignRejectsInactiveWarehouse() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9001L)).thenReturn(activeUser(9001L));
        when(warehouseMapper.selectById(4501L)).thenReturn(null);

        assertThatThrownBy(() -> service.assign(9001L, new UserDataScopeAssignRequest(
                false, false, false, false, List.of(4501L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仓库不存在");
    }

    @Test
    void getAssignedAggregatesExistingRowsAndEffectiveUnion() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9001L)).thenReturn(activeUser(9001L));
        when(userDataScopeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                scopeRow(9001L, "DEPT", null),
                scopeRow(9001L, "WAREHOUSE", 4502L),
                scopeRow(9001L, "WAREHOUSE", 4501L),
                scopeRow(9001L, "SELF", null)
        ));
        when(dataScopeService.buildSnapshot(eq(9001L), eq(1L), eq(1L)))
                .thenReturn(new DataScopeSnapshot(false, true, true, true, Set.of(4501L, 4502L, 4503L)));

        UserDataScopeResponse response = service.getAssigned(9001L);

        assertThat(response.userId()).isEqualTo(9001L);
        assertThat(response.hasAllScope()).isFalse();
        assertThat(response.deptScoped()).isTrue();
        assertThat(response.postScoped()).isFalse();
        assertThat(response.selfScoped()).isTrue();
        assertThat(response.warehouseIds()).containsExactly(4501L, 4502L);
        // 生效范围含角色并集：post 与额外仓库来自角色
        assertThat(response.effectivePostScoped()).isTrue();
        assertThat(response.effectiveWarehouseIds()).containsExactly(4501L, 4502L, 4503L);
    }

    @Test
    void assignEmptyRequestClearsAllUserScopes() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9001L)).thenReturn(activeUser(9001L));
        when(userDataScopeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
        when(dataScopeService.buildSnapshot(eq(9001L), eq(1L), eq(1L)))
                .thenReturn(DataScopeSnapshot.none());

        UserDataScopeResponse response = service.assign(9001L, new UserDataScopeAssignRequest(
                false, false, false, false, List.of()
        ));

        assertThat(response.hasAllScope()).isFalse();
        assertThat(response.deptScoped()).isFalse();
        assertThat(response.postScoped()).isFalse();
        assertThat(response.selfScoped()).isFalse();
        assertThat(response.warehouseIds()).isEmpty();
        assertThat(response.effectiveHasAllScope()).isFalse();
        verify(userDataScopeMapper).delete(any(LambdaQueryWrapper.class));
        verify(principalCache).evictUser(9001L);
    }

    @Test
    void getAssignedRejectsMissingUser() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9001L)).thenReturn(null);

        assertThatThrownBy(() -> service.getAssigned(9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户不存在");
    }

    private static UserDataScopeEntity scopeRow(Long userId, String scopeType, Long warehouseId) {
        UserDataScopeEntity entity = new UserDataScopeEntity();
        entity.setUserId(userId);
        entity.setScopeType(scopeType);
        entity.setWarehouseId(warehouseId);
        return entity;
    }

    private static AuditMetadata audit() {
        return new AuditMetadata(4001L, 1L, 1L, LocalDateTime.of(2026, 7, 17, 12, 0));
    }

    private static UserEntity activeUser(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setCompanyId(1L);
        user.setAccountBookId(1L);
        user.setDeletedFlag(0);
        user.setStatus("ACTIVE");
        return user;
    }

    private static WarehouseEntity activeWarehouse(Long id) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setCompanyId(1L);
        warehouse.setAccountBookId(1L);
        warehouse.setDeletedFlag(0);
        warehouse.setStatus("ACTIVE");
        return warehouse;
    }
}
