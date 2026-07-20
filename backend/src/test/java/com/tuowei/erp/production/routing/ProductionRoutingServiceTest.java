package com.tuowei.erp.production.routing;

import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
class ProductionRoutingServiceTest {

    @Autowired
    private ProductionRoutingService productionRoutingService;

    @Autowired
    private ProductionWorkCenterService productionWorkCenterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(895001L, "PRD-FG-895001", "路线成品");
        seedProduct(895002L, "PRD-MAT-895002", "路线材料");
        seedBom(895101L, "BOM-895101");
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
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:routing:update",
            "production:routing:view",
            "production:work-center:create"
    })
    void rejectsDuplicateBomAndReplacesOperationsOnUpdate() {
        ProductionWorkCenterResponse workCenter = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895001", "焊接一线", "wc"));
        ProductionRoutingResponse created = productionRoutingService.create(new ProductionRoutingCreateRequest(
                "RT-895001",
                "标准路线",
                895101L,
                "first",
                List.of(
                        new ProductionRoutingOperationRequest("OP-10", "切割", workCenter.id(), new BigDecimal("12.50"), "first"),
                        new ProductionRoutingOperationRequest("OP-20", "装配", workCenter.id(), new BigDecimal("18.00"), "second")
                )
        ));

        Assertions.assertThat(created.operations()).hasSize(2);
        Assertions.assertThat(created.operations().get(0).lineNo()).isEqualTo(1);
        Assertions.assertThat(created.operations().get(1).lineNo()).isEqualTo(2);

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895002",
                        "重复BOM路线",
                        895101L,
                        "dup",
                        List.of(new ProductionRoutingOperationRequest("OP-30", "检验", workCenter.id(), new BigDecimal("6.00"), "dup"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前BOM已存在工艺路线");

        ProductionRoutingResponse updated = productionRoutingService.update(created.id(), new ProductionRoutingUpdateRequest(
                "标准路线-更新",
                "updated",
                List.of(new ProductionRoutingOperationRequest("OP-99", "总装", workCenter.id(), new BigDecimal("22.00"), "replace"))
        ));

        Assertions.assertThat(updated.operations()).hasSize(1);
        Assertions.assertThat(updated.operations().get(0).lineNo()).isEqualTo(1);
        Assertions.assertThat(updated.operations().get(0).operationCode()).isEqualTo("OP-99");
        Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from prd_routing_operation where routing_id = ?",
                Integer.class,
                created.id()
        )).isEqualTo(1);
    }

    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:work-center:create",
            "production:work-center:disable"
    })
    void rejectsEmptyOperationsDuplicateOperationCodesAndDisabledWorkCenter() {
        ProductionWorkCenterResponse active = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895011", "装配一线", "active"));
        ProductionWorkCenterResponse disabled = productionWorkCenterService.create(
                new ProductionWorkCenterCreateRequest("WC-895012", "装配二线", "disabled"));
        productionWorkCenterService.disable(disabled.id());

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895011",
                        "空工序",
                        895101L,
                        "empty",
                        List.of()
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线至少需要一道工序");

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895012",
                        "重复工序编码",
                        895101L,
                        "dup op",
                        List.of(
                                new ProductionRoutingOperationRequest("OP-10", "切割", active.id(), new BigDecimal("10.00"), "first"),
                                new ProductionRoutingOperationRequest("OP-10", "装配", active.id(), new BigDecimal("12.00"), "second")
                        )
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工序编码不能重复");

        Assertions.assertThatThrownBy(() -> productionRoutingService.create(new ProductionRoutingCreateRequest(
                        "RT-895013",
                        "停用工作中心",
                        895101L,
                        "disabled wc",
                        List.of(new ProductionRoutingOperationRequest("OP-20", "检验", disabled.id(), new BigDecimal("6.00"), "line"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在或已停用");
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '路线测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '路线测试', 895001, 895001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 895001, 1.0000, 'ACTIVE', 0, 'seed bom', 895001, 895001, 0)
                """, id, bomNo);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate,
                 remark, created_by, updated_by, version)
                values (895102, 1, 1, ?, 1, 895002, 1.0000, 0.0000, 'seed line', 895001, 895001, 0)
                """, id);
    }
}
