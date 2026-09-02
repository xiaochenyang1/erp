package com.tuowei.erp.system.dict.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.model.DictItemEntity;
import com.tuowei.erp.system.dict.model.DictTypeEntity;
import com.tuowei.erp.system.dict.web.DictItemCreateRequest;
import com.tuowei.erp.system.dict.web.DictItemResponse;
import com.tuowei.erp.system.dict.web.DictItemUpdateRequest;
import com.tuowei.erp.system.dict.web.DictTypeCreateRequest;
import com.tuowei.erp.system.dict.web.DictTypeResponse;
import com.tuowei.erp.system.dict.web.DictTypeUpdateRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemDictCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            2L, 102L, 203L, LocalDateTime.of(2026, 8, 21, 11, 30)
    );
    private static final Long TYPE_ID = 701L;
    private static final Long ITEM_ID = 702L;

    @Mock
    private DictTypeMapper dictTypeMapper;
    @Mock
    private DictItemMapper dictItemMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private SystemDictQueryService queryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createTypeBuildsActiveAuditedEntityAndMapsResponse() {
        when(dictTypeMapper.insert(any(DictTypeEntity.class))).thenAnswer(invocation -> {
            DictTypeEntity entity = invocation.getArgument(0);
            entity.setId(TYPE_ID);
            return 1;
        });
        DictTypeResponse expected = new DictTypeResponse(TYPE_ID, "status", "状态", "ACTIVE", "remark");
        when(queryService.toTypeResponse(any(DictTypeEntity.class))).thenReturn(expected);

        DictTypeResponse actual = service().createType(new DictTypeCreateRequest(
                "  status ", "状态", "remark"
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<DictTypeEntity> captor = ArgumentCaptor.forClass(DictTypeEntity.class);
        verify(dictTypeMapper).insert(captor.capture());
        DictTypeEntity inserted = captor.getValue();
        assertThat(inserted.getDictType()).isEqualTo("status");
        assertThat(inserted.getDictName()).isEqualTo("状态");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void createItemNormalizesFieldsLinksTypeAndEvictsGlobalCache() {
        DictTypeEntity type = type("product_type", "商品类型", "DISABLED");
        when(queryService.requireTypeByCode("product_type")).thenReturn(type);
        when(dictItemMapper.insert(any(DictItemEntity.class))).thenAnswer(invocation -> {
            DictItemEntity entity = invocation.getArgument(0);
            entity.setId(ITEM_ID);
            return 1;
        });
        DictItemResponse expected = new DictItemResponse(
                ITEM_ID, TYPE_ID, "product_type", "标准", "STANDARD", 3, "ACTIVE", "remark"
        );
        when(queryService.toItemResponse(any(DictItemEntity.class))).thenReturn(expected);

        DictItemResponse actual = service().createItem(new DictItemCreateRequest(
                " product_type ", " 标准 ", " STANDARD ", 3, "remark"
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<DictItemEntity> captor = ArgumentCaptor.forClass(DictItemEntity.class);
        verify(dictItemMapper).insert(captor.capture());
        DictItemEntity inserted = captor.getValue();
        assertThat(inserted.getTypeId()).isEqualTo(TYPE_ID);
        assertThat(inserted.getDictType()).isEqualTo("product_type");
        assertThat(inserted.getItemLabel()).isEqualTo("标准");
        assertThat(inserted.getItemValue()).isEqualTo("STANDARD");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        verify(queryService).evictDictItemsCache("product_type");
    }

    @Test
    void updateTypeSurfacesOptimisticConflict() {
        DictTypeEntity entity = type("status", "旧状态", "ACTIVE");
        when(queryService.requireType(TYPE_ID)).thenReturn(entity);
        when(dictTypeMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().updateType(TYPE_ID, new DictTypeUpdateRequest("新状态", "remark")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("字典类型已被其他操作修改，请刷新后重试");

        verify(queryService, never()).toTypeResponse(entity);
    }

    @Test
    void updateItemAndStatusCommandsKeepAuditFieldsAndEvictItemCache() {
        DictItemEntity entity = item("status", "ACTIVE");
        when(queryService.requireItem(ITEM_ID)).thenReturn(entity);
        when(dictItemMapper.updateById(entity)).thenReturn(1);
        DictItemResponse expected = new DictItemResponse(
                ITEM_ID, TYPE_ID, "status", "已启用", "ACTIVE", 4, "ACTIVE", "updated"
        );
        when(queryService.toItemResponse(entity)).thenReturn(expected);

        DictItemResponse actual = service().updateItem(ITEM_ID, new DictItemUpdateRequest(
                " 已启用 ", 4, "updated"
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(entity.getItemLabel()).isEqualTo("已启用");
        assertThat(entity.getSortNo()).isEqualTo(4);
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(queryService).evictDictItemsCache("status");

        when(queryService.requireItem(ITEM_ID)).thenReturn(entity);
        when(queryService.toItemResponse(entity)).thenReturn(expected);
        service().disableItem(ITEM_ID);
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        verify(queryService, org.mockito.Mockito.times(2)).evictDictItemsCache("status");
    }

    @Test
    void typeStatusChangesDoNotEvictItemCache() {
        DictTypeEntity entity = type("status", "状态", "ACTIVE");
        when(queryService.requireType(TYPE_ID)).thenReturn(entity);
        when(dictTypeMapper.updateById(entity)).thenReturn(1);
        when(queryService.toTypeResponse(entity)).thenReturn(
                new DictTypeResponse(TYPE_ID, "status", "状态", "DISABLED", null)
        );

        service().disableType(TYPE_ID);

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        verify(queryService, never()).evictDictItemsCache(any());
    }

    @Test
    void createTypeRejectsUnicodeWhitespaceDictionaryCode() {
        assertThatThrownBy(() -> service().createType(new DictTypeCreateRequest("\u2003", "状态", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("字典编码不能为空");

        verify(dictTypeMapper, never()).insert(any(DictTypeEntity.class));
    }

    private SystemDictCommandService service() {
        return new SystemDictCommandService(dictTypeMapper, dictItemMapper, auditMetadataFactory, queryService);
    }

    private DictTypeEntity type(String code, String name, String status) {
        DictTypeEntity entity = new DictTypeEntity();
        entity.setId(TYPE_ID);
        entity.setDictType(code);
        entity.setDictName(name);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private DictItemEntity item(String dictType, String status) {
        DictItemEntity entity = new DictItemEntity();
        entity.setId(ITEM_ID);
        entity.setTypeId(TYPE_ID);
        entity.setDictType(dictType);
        entity.setItemLabel("已启用");
        entity.setItemValue("ACTIVE");
        entity.setSortNo(1);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }
}
