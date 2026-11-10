package com.tuowei.erp.inventory.check;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckLineMapper;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckLineEntity;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckNumberService;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckService;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateLineRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryStockCheckUpdateServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 18, 14, 0)
    );

    @Mock
    private InventoryStockCheckMapper checkMapper;

    @Mock
    private InventoryStockCheckLineMapper lineMapper;

    @Mock
    private InventoryStockCheckNumberService numberService;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventoryAdjustmentService adjustmentService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private AttachmentService attachmentService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryStockCheckLineEntity.class);
    }

    @Test
    void updateRecalculatesDifferencesForCountedCheck() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(8001L)).thenReturn(check("COUNTED"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));
        when(lineMapper.updateById(any(InventoryStockCheckLineEntity.class))).thenReturn(1);
        when(checkMapper.updateById(any(InventoryStockCheckEntity.class))).thenReturn(1);

        service().update(8001L, new InventoryStockCheckUpdateRequest(List.of(
                new InventoryStockCheckUpdateLineRequest(
                        8101L,
                        7001L,
                        new BigDecimal("7.0000"),
                        new BigDecimal("10.0000"),
                        null,
                        null,
                        null,
                        "counted"
                )
        )));

        ArgumentCaptor<InventoryStockCheckLineEntity> lineCaptor = ArgumentCaptor.forClass(InventoryStockCheckLineEntity.class);
        verify(lineMapper).updateById(lineCaptor.capture());
        InventoryStockCheckLineEntity updatedLine = lineCaptor.getValue();
        assertThat(updatedLine.getActualQty()).isEqualByComparingTo("7.0000");
        assertThat(updatedLine.getDifferenceQty()).isEqualByComparingTo("2.0000");
        assertThat(updatedLine.getDifferenceAmount()).isEqualByComparingTo("20.00");
        assertThat(updatedLine.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(updatedLine.getUpdatedTime()).isEqualTo(AUDIT.now());

        ArgumentCaptor<InventoryStockCheckEntity> checkCaptor = ArgumentCaptor.forClass(InventoryStockCheckEntity.class);
        verify(checkMapper).updateById(checkCaptor.capture());
        assertThat(checkCaptor.getValue().getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(checkCaptor.getValue().getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void updateRejectsAdjustedCheck() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(8001L)).thenReturn(check("ADJUSTED"));

        assertThatThrownBy(() -> service().update(8001L, updateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有已录入盘点结果的盘点单可以编辑");
    }

    @Test
    void updateRejectsMissingLine() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(8001L)).thenReturn(check("COUNTED"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));

        assertThatThrownBy(() -> service().update(8001L, new InventoryStockCheckUpdateRequest(List.of(
                new InventoryStockCheckUpdateLineRequest(
                        9999L,
                        7001L,
                        new BigDecimal("7.0000"),
                        new BigDecimal("10.0000"),
                        null,
                        null,
                        null,
                        "missing"
                )
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点明细不存在");
    }

    @Test
    void updateReportsLineConflict() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(8001L)).thenReturn(check("COUNTED"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));
        when(lineMapper.updateById(any(InventoryStockCheckLineEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service().update(8001L, updateRequest()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("库存盘点明细已被其他操作修改，请重试");
    }

    private InventoryStockCheckUpdateRequest updateRequest() {
        return new InventoryStockCheckUpdateRequest(List.of(
                new InventoryStockCheckUpdateLineRequest(
                        8101L,
                        7001L,
                        new BigDecimal("7.0000"),
                        new BigDecimal("10.0000"),
                        null,
                        null,
                        null,
                        "counted"
                )
        ));
    }

    private InventoryStockCheckService service() {
        return new InventoryStockCheckService(
                checkMapper,
                lineMapper,
                numberService,
                inventoryPostingService,
                adjustmentService,
                auditMetadataFactory,
                warehouseMapper,
                productValidator,
                accountPeriodGuard,
                attachmentService
        );
    }

    private InventoryStockCheckEntity check(String status) {
        InventoryStockCheckEntity entity = new InventoryStockCheckEntity();
        entity.setId(8001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCheckNo("CHK-001");
        entity.setWarehouseId(6001L);
        entity.setCheckDate(LocalDate.of(2026, 6, 18));
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        return entity;
    }

    private InventoryStockCheckLineEntity line() {
        InventoryStockCheckLineEntity entity = new InventoryStockCheckLineEntity();
        entity.setId(8101L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCheckId(8001L);
        entity.setLineNo(1);
        entity.setProductId(7001L);
        entity.setBookQty(new BigDecimal("5.0000"));
        entity.setActualQty(new BigDecimal("5.0000"));
        entity.setDifferenceQty(new BigDecimal("0.0000"));
        entity.setUnitCost(new BigDecimal("10.0000"));
        entity.setDifferenceAmount(new BigDecimal("0.00"));
        return entity;
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
