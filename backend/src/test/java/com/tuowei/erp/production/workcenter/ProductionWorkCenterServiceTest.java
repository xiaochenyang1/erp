package com.tuowei.erp.production.workcenter;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ProductionWorkCenterServiceTest {

    @Autowired
    private ProductionWorkCenterService productionWorkCenterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductionWorkCenterMapper productionWorkCenterMapper;

    @Autowired
    private AuditMetadataFactory auditMetadataFactory;

    @Autowired
    private ProductionRoutingMapper productionRoutingMapper;

    @Autowired
    private ProductionRoutingOperationMapper productionRoutingOperationMapper;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from prd_routing_operation");
        jdbcTemplate.update("delete from prd_routing");
        jdbcTemplate.update("delete from prd_work_center");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_product where id between 895000 and 895999");
    }

    @Test
    @WithErpUser(authorities = {"production:work-center:create"})
    void rejectsDuplicateWorkCenterCodeAtServiceLayer() {
        productionWorkCenterService.create(new ProductionWorkCenterCreateRequest("WC-895001", "切割", "first"));

        Assertions.assertThatThrownBy(() -> productionWorkCenterService.create(
                        new ProductionWorkCenterCreateRequest("WC-895001", "切割二线", "duplicate")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心编码已存在");
    }

    @Test
    @WithErpUser(authorities = {"production:work-center:create", "production:work-center:update"})
    void updatesOnlyNameAndRemarkWhileKeepingCodeUnchanged() {
        ProductionWorkCenterResponse created = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895002", "装配", "before"));

        ProductionWorkCenterResponse updated = productionWorkCenterService.update(
                created.id(),
                new ProductionWorkCenterUpdateRequest("总装", "after"));

        Assertions.assertThat(updated.workCenterCode()).isEqualTo("WC-895002");
        Assertions.assertThat(updated.workCenterName()).isEqualTo("总装");
        Assertions.assertThat(updated.remark()).isEqualTo("after");

        ProductionWorkCenterResponse detail = productionWorkCenterService.getById(created.id());
        Assertions.assertThat(detail.workCenterCode()).isEqualTo("WC-895002");
        Assertions.assertThat(detail.workCenterName()).isEqualTo("总装");
        Assertions.assertThat(detail.remark()).isEqualTo("after");
    }

    @Test
    @WithErpUser(authorities = {"production:work-center:create"})
    void rejectsNullWorkCenterCodeWithBusinessMessage() {
        Assertions.assertThatThrownBy(() -> productionWorkCenterService.create(
                        new ProductionWorkCenterCreateRequest(null, "切割", "first")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心编码不能为空");
    }

    @Test
    @WithErpUser(authorities = {"production:work-center:create", "production:work-center:update"})
    void rejectsBlankWorkCenterNameWithBusinessMessage() {
        ProductionWorkCenterResponse created = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895003", "冲压", "before"));

        Assertions.assertThatThrownBy(() -> productionWorkCenterService.update(
                        created.id(),
                        new ProductionWorkCenterUpdateRequest("   ", "after")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心名称不能为空");
    }

    @Test
    @WithErpUser(authorities = {"production:work-center:create", "production:work-center:update"})
    void updateThrowsBusinessConflictWhenPersistedVersionIsStale() {
        ProductionWorkCenterResponse created = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895004", "机加", "before"));
        ProductionWorkCenterEntity staleEntity = snapshotOf(productionWorkCenterMapper.selectById(created.id()));
        jdbcTemplate.update("""
                update prd_work_center
                set work_center_name = '并发改名',
                    updated_by = 895004,
                    updated_time = current_timestamp,
                    version = version + 1
                where id = ?
                """, created.id());

        ProductionWorkCenterMapper staleAwareMapper = mock(ProductionWorkCenterMapper.class);
        when(staleAwareMapper.selectById(created.id())).thenReturn(staleEntity);
        when(staleAwareMapper.updateById(any(ProductionWorkCenterEntity.class)))
                .thenAnswer(invocation -> {
                    ProductionWorkCenterEntity entity = invocation.getArgument(0, ProductionWorkCenterEntity.class);
                    return productionWorkCenterMapper.updateById(entity);
                });

        ProductionWorkCenterService service = new ProductionWorkCenterService(
                staleAwareMapper,
                productionRoutingMapper,
                productionRoutingOperationMapper,
                auditMetadataFactory
        );

        Assertions.assertThatThrownBy(() -> service.update(
                        created.id(),
                        new ProductionWorkCenterUpdateRequest("冲压二线", "stale")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("工作中心已被其他操作修改，请刷新后重试");
    }

    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:work-center:create",
            "production:work-center:disable"
    })
    void rejectsDisableWhenReferencedByActiveRouting() {
        seedProduct(895011L, "PRD-FG-895011", "冲突成品");
        seedProduct(895012L, "PRD-MAT-895012", "冲突材料");
        seedBom(895111L, "BOM-895111", 895011L, 895012L);

        ProductionWorkCenterResponse wc = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895011", "冲突工位", "wc"));
        jdbcTemplate.update("""
                insert into prd_routing
                (id, company_id, account_book_id, routing_code, routing_name, bom_id, status, deleted_flag, remark, created_by, updated_by, version)
                values (895211, 1, 1, 'RT-895211', '冲突路线', 895111, 'ACTIVE', 0, 'routing', 1, 1, 0)
                """);
        jdbcTemplate.update("""
                insert into prd_routing_operation
                (id, company_id, account_book_id, routing_id, line_no, operation_code, operation_name, work_center_id, standard_minutes, remark, created_by, updated_by, version)
                values (895212, 1, 1, 895211, 1, 'OP-1', '工序', ?, 10.00, 'line', 1, 1, 0)
                """, wc.id());

        Assertions.assertThatThrownBy(() -> productionWorkCenterService.disable(wc.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心已被启用工艺路线引用，不能停用");
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '工作中心测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '工作中心测试', 895001, 895001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo, long productId, long materialProductId) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1.0000, 'ACTIVE', 0, 'seed bom', 895001, 895001, 0)
                """, id, bomNo, productId);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate, remark, created_by, updated_by, version)
                values (895112, 1, 1, ?, 1, ?, 1.0000, 0.0000, 'seed line', 895001, 895001, 0)
                """, id, materialProductId);
    }

    private ProductionWorkCenterEntity snapshotOf(ProductionWorkCenterEntity source) {
        ProductionWorkCenterEntity copy = new ProductionWorkCenterEntity();
        copy.setId(source.getId());
        copy.setCompanyId(source.getCompanyId());
        copy.setAccountBookId(source.getAccountBookId());
        copy.setWorkCenterCode(source.getWorkCenterCode());
        copy.setWorkCenterName(source.getWorkCenterName());
        copy.setStatus(source.getStatus());
        copy.setDeletedFlag(source.getDeletedFlag());
        copy.setRemark(source.getRemark());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedTime(source.getCreatedTime());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedTime(source.getUpdatedTime());
        copy.setVersion(source.getVersion());
        return copy;
    }
}
