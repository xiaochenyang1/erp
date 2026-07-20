package com.tuowei.erp.qc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QcInspectionGateTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 10, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(1L, COMPANY_ID, ACCOUNT_BOOK_ID, NOW);

    @Mock
    private QcInspectionOrderMapper qcInspectionOrderMapper;

    @Mock
    private QcInspectionLineMapper qcInspectionLineMapper;

    @Mock
    private ProductMapper productMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(QcInspectionOrderEntity.class);
        initTableInfo(QcInspectionLineEntity.class);
    }

    @Test
    void assertDeliveryInspectedSkipsWhenNoProductRequiresInspection() {
        ProductEntity product = product(0);
        when(productMapper.selectById(4001L)).thenReturn(product);

        assertThatCode(() -> gate().assertDeliveryInspected(delivery(), List.of(deliveryLine()), AUDIT))
                .doesNotThrowAnyException();
    }

    @Test
    void assertDeliveryInspectedBlocksWithoutJudgedOqc() {
        when(productMapper.selectById(4001L)).thenReturn(product(1));
        when(qcInspectionOrderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> gate().assertDeliveryInspected(delivery(), List.of(deliveryLine()), AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("存在需检验商品尚未完成出库质检，不能过账");
    }

    @Test
    void assertDeliveryInspectedAllowsWhenJudgedAndQtyMatches() {
        when(productMapper.selectById(4001L)).thenReturn(product(1));
        when(qcInspectionOrderMapper.selectOne(any())).thenReturn(judgedOqc());
        when(qcInspectionLineMapper.selectList(any())).thenReturn(List.of(judgedOqcLine("3.0000")));

        assertThatCode(() -> gate().assertDeliveryInspected(delivery(), List.of(deliveryLine()), AUDIT))
                .doesNotThrowAnyException();
    }

    @Test
    void assertDeliveryInspectedBlocksWhenQualifiedQtyMismatch() {
        when(productMapper.selectById(4001L)).thenReturn(product(1));
        when(qcInspectionOrderMapper.selectOne(any())).thenReturn(judgedOqc());
        when(qcInspectionLineMapper.selectList(any())).thenReturn(List.of(judgedOqcLine("2.0000")));

        assertThatThrownBy(() -> gate().assertDeliveryInspected(delivery(), List.of(deliveryLine()), AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("出库质检合格数量与出库数量不一致，不能过账");
    }

    private QcInspectionGate gate() {
        return new QcInspectionGate(qcInspectionOrderMapper, qcInspectionLineMapper, productMapper);
    }

    private ProductEntity product(int inspectionRequired) {
        ProductEntity product = new ProductEntity();
        product.setId(4001L);
        product.setInspectionRequired(inspectionRequired);
        return product;
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(9101L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine() {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(9201L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("3.0000"));
        return entity;
    }

    private QcInspectionOrderEntity judgedOqc() {
        QcInspectionOrderEntity entity = new QcInspectionOrderEntity();
        entity.setId(5101L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionType(QcInspectionGate.TYPE_OQC);
        entity.setDeliveryId(9101L);
        entity.setStatus("JUDGED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionLineEntity judgedOqcLine(String qualifiedQty) {
        QcInspectionLineEntity entity = new QcInspectionLineEntity();
        entity.setId(6101L);
        entity.setInspectionId(5101L);
        entity.setDeliveryLineId(9201L);
        entity.setProductId(4001L);
        entity.setQualifiedQty(new BigDecimal(qualifiedQty));
        entity.setDeletedFlag(0);
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
