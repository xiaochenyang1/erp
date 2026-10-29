package com.tuowei.erp.system.config.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 18, 0)
    );
    private static final Long CONFIG_ID = 8001L;

    @Mock private SystemConfigMapper systemConfigMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SystemConfigQueryService systemConfigQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsGlobalActiveConfigWithCompleteAuditMetadata() {
        when(systemConfigMapper.insert(any(SystemConfigEntity.class))).thenAnswer(invocation -> {
            SystemConfigEntity entity = invocation.getArgument(0);
            entity.setId(CONFIG_ID);
            return 1;
        });
        SystemConfigResponse expected = response("ACTIVE");
        when(systemConfigQueryService.toResponse(any(SystemConfigEntity.class))).thenReturn(expected);

        SystemConfigResponse actual = service().create(new SystemConfigCreateRequest(
                "ERP_IMPORT_MAX_ROWS", "导入最大行数", "5000", "global config"
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<SystemConfigEntity> captor = ArgumentCaptor.forClass(SystemConfigEntity.class);
        verify(systemConfigMapper).insert(captor.capture());
        SystemConfigEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(CONFIG_ID);
        assertThat(inserted.getConfigCode()).isEqualTo("ERP_IMPORT_MAX_ROWS");
        assertThat(inserted.getConfigName()).isEqualTo("导入最大行数");
        assertThat(inserted.getConfigValue()).isEqualTo("5000");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getRemark()).isEqualTo("global config");
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void updateAuditsMutableFieldsAndMapsPersistedConfig() {
        SystemConfigEntity entity = config("ACTIVE");
        when(systemConfigQueryService.requireConfig(CONFIG_ID)).thenReturn(entity);
        when(systemConfigMapper.updateById(entity)).thenReturn(1);
        SystemConfigResponse expected = response("ACTIVE");
        when(systemConfigQueryService.toResponse(entity)).thenReturn(expected);

        SystemConfigResponse actual = service().update(CONFIG_ID, new SystemConfigUpdateRequest(
                "导入上限", "8000", "updated"
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(entity.getConfigName()).isEqualTo("导入上限");
        assertThat(entity.getConfigValue()).isEqualTo("8000");
        assertThat(entity.getRemark()).isEqualTo("updated");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeResponseMapping() {
        SystemConfigEntity entity = config("ACTIVE");
        when(systemConfigQueryService.requireConfig(CONFIG_ID)).thenReturn(entity);
        when(systemConfigMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(CONFIG_ID, new SystemConfigUpdateRequest(
                "冲突参数", "1", null
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("系统参数已被其他操作修改，请刷新后重试");

        verify(systemConfigQueryService, never()).toResponse(entity);
    }

    @Test
    void statusCommandsSetExpectedStatusAndAuditFields() {
        SystemConfigEntity entity = config("ACTIVE");
        when(systemConfigQueryService.requireConfig(CONFIG_ID)).thenReturn(entity);
        when(systemConfigMapper.updateById(entity)).thenReturn(1);
        when(systemConfigQueryService.toResponse(entity))
                .thenAnswer(invocation -> response(entity.getStatus()));

        assertThat(service().disable(CONFIG_ID).status()).isEqualTo("DISABLED");
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());

        assertThat(service().enable(CONFIG_ID).status()).isEqualTo("ACTIVE");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        verify(systemConfigMapper, times(2)).updateById(entity);
    }

    @Test
    void statusConflictStopsBeforeResponseMapping() {
        SystemConfigEntity entity = config("ACTIVE");
        when(systemConfigQueryService.requireConfig(CONFIG_ID)).thenReturn(entity);
        when(systemConfigMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().disable(CONFIG_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("系统参数已被其他操作修改，请刷新后重试");

        verify(systemConfigQueryService, never()).toResponse(entity);
    }

    @Test
    void lookupFailureStopsCommandsBeforeMapperWrites() {
        when(systemConfigQueryService.requireConfig(CONFIG_ID))
                .thenThrow(new IllegalArgumentException("系统参数不存在"));

        assertThatThrownBy(() -> service().enable(CONFIG_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("系统参数不存在");

        verifyNoInteractions(systemConfigMapper);
    }

    private SystemConfigCommandService service() {
        return new SystemConfigCommandService(
                systemConfigMapper, auditMetadataFactory, systemConfigQueryService
        );
    }

    private SystemConfigEntity config(String status) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setId(CONFIG_ID);
        entity.setConfigCode("ERP_IMPORT_MAX_ROWS");
        entity.setConfigName("导入最大行数");
        entity.setConfigValue("5000");
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setRemark("global config");
        entity.setVersion(0);
        return entity;
    }

    private SystemConfigResponse response(String status) {
        return new SystemConfigResponse(
                CONFIG_ID, "ERP_IMPORT_MAX_ROWS", "导入最大行数", "5000", status, "global config"
        );
    }
}
