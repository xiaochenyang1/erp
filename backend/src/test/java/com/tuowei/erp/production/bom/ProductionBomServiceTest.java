package com.tuowei.erp.production.bom;

import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomLineRequest;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
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
class ProductionBomServiceTest {

    private static final long FINISHED_PRODUCT_ID = 891001L;
    private static final long FINISHED_PRODUCT_TWO_ID = 891004L;
    private static final long MATERIAL_ONE_ID = 891002L;
    private static final long MATERIAL_TWO_ID = 891003L;

    @Autowired
    private ProductionBomService productionBomService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(FINISHED_PRODUCT_ID, "PRD-FG-891001", "制造成品");
        seedProduct(FINISHED_PRODUCT_TWO_ID, "PRD-FG-891004", "制造成品2");
        seedProduct(MATERIAL_ONE_ID, "PRD-MAT-891002", "制造材料1");
        seedProduct(MATERIAL_TWO_ID, "PRD-MAT-891003", "制造材料2");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_product where id between 891000 and 891999");
    }

    @Test
    @WithErpUser(authorities = {"production:bom:manage"})
    void createsBomWithOrderedLinesAndRejectsDuplicateMaterials() {
        ProductionBomResponse response = productionBomService.create(new ProductionBomCreateRequest(
                FINISHED_PRODUCT_ID,
                new BigDecimal("1.0000"),
                "Active BOM",
                List.of(
                        new ProductionBomLineRequest(MATERIAL_ONE_ID, new BigDecimal("2.0000"), new BigDecimal("0.0500"), "line1"),
                        new ProductionBomLineRequest(MATERIAL_TWO_ID, new BigDecimal("3.0000"), BigDecimal.ZERO, "line2")
                )
        ));

        Assertions.assertThat(response.lines()).hasSize(2);
        Assertions.assertThat(response.lines().get(0).lineNo()).isEqualTo(1);
        Assertions.assertThat(response.lines().get(1).lineNo()).isEqualTo(2);

        ProductionBomCreateRequest duplicateRequest = new ProductionBomCreateRequest(
                FINISHED_PRODUCT_TWO_ID,
                new BigDecimal("1.0000"),
                "Duplicate BOM",
                List.of(
                        new ProductionBomLineRequest(MATERIAL_ONE_ID, BigDecimal.ONE, BigDecimal.ZERO, "dup1"),
                        new ProductionBomLineRequest(MATERIAL_ONE_ID, BigDecimal.ONE, BigDecimal.ZERO, "dup2")
                )
        );
        Assertions.assertThatThrownBy(() -> productionBomService.create(duplicateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOM材料不能重复");
    }

    @Test
    @WithErpUser(authorities = {"production:bom:manage"})
    void rejectsFinishedProductAsMaterialAndDuplicateActiveBom() {
        ProductionBomCreateRequest selfMaterialRequest = new ProductionBomCreateRequest(
                FINISHED_PRODUCT_ID,
                new BigDecimal("1.0000"),
                "Self material BOM",
                List.of(new ProductionBomLineRequest(FINISHED_PRODUCT_ID, BigDecimal.ONE, BigDecimal.ZERO, "self"))
        );
        Assertions.assertThatThrownBy(() -> productionBomService.create(selfMaterialRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOM材料不能和成品相同");

        productionBomService.create(new ProductionBomCreateRequest(
                FINISHED_PRODUCT_ID,
                new BigDecimal("1.0000"),
                "Active BOM",
                List.of(new ProductionBomLineRequest(MATERIAL_ONE_ID, BigDecimal.ONE, BigDecimal.ZERO, "line"))
        ));

        ProductionBomCreateRequest duplicateActiveBom = new ProductionBomCreateRequest(
                FINISHED_PRODUCT_ID,
                new BigDecimal("1.0000"),
                "Another Active BOM",
                List.of(new ProductionBomLineRequest(MATERIAL_TWO_ID, BigDecimal.ONE, BigDecimal.ZERO, "line"))
        );
        Assertions.assertThatThrownBy(() -> productionBomService.create(duplicateActiveBom))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("同一成品已存在启用BOM");
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '生产测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '生产测试', 891001, 891001, 0)
                """, id, code, name);
    }
}
