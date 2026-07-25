package com.tuowei.erp.masterdata.product;

import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
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

@SpringBootTest
@ActiveProfiles("test")
class ProductAuxUnitServiceTest {

    @Autowired
    ProductService productService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("""
                insert into sys_dict_item
                (id, type_id, dict_type, item_label, item_value, sort_no, status, deleted_flag,
                 remark, created_by, updated_by, version)
                select 894001, id, 'product_type', '标准商品', 'STANDARD', 99, 'ACTIVE', 0,
                       'aux unit test', 894001, 894001, 0
                  from sys_dict_type
                 where dict_type = 'product_type'
                   and not exists (select 1 from sys_dict_item where id = 894001)
                """);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from md_product where product_code like 'AUX-PROD-%' or id between 894000 and 894999");
        jdbcTemplate.update("delete from sys_dict_item where id = 894001");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create"})
    void createsProductWithAuxUnitAndConversionFactor() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "AUX-PROD-001", "辅单位商品", "STANDARD", "单位测试", "规格", "件",
                "箱", new BigDecimal("12"),
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, false, "aux unit", null
        ));

        Assertions.assertThat(created.auxUnitName()).isEqualTo("箱");
        Assertions.assertThat(created.conversionFactor().compareTo(new BigDecimal("12"))).isZero();
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create"})
    void rejectsAuxUnitWithoutPositiveConversionFactor() {
        Assertions.assertThatThrownBy(() -> productService.create(new ProductCreateRequest(
                "AUX-PROD-002", "缺换算率商品", "STANDARD", "单位测试", "规格", "件",
                "箱", null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, false, "missing factor", null
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("启用辅单位时换算率必须大于0（1 辅单位 = N 库存单位）");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create"})
    void rejectsAuxUnitSameAsStockUnit() {
        Assertions.assertThatThrownBy(() -> productService.create(new ProductCreateRequest(
                "AUX-PROD-003", "同单位商品", "STANDARD", "单位测试", "规格", "件",
                "件", new BigDecimal("1"),
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, false, "same unit", null
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("辅单位不能与库存单位相同");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void clearsAuxUnitWhenBlankOnUpdate() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "AUX-PROD-004", "可清除辅单位", "STANDARD", "单位测试", "规格", "件",
                "箱", new BigDecimal("10"),
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, false, "clearable", null
        ));

        ProductResponse updated = productService.update(created.id(), new ProductUpdateRequest(
                "可清除辅单位", "单位测试", "规格", "件",
                null, null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, false, "cleared", null
        ));

        Assertions.assertThat(updated.auxUnitName()).isNull();
        Assertions.assertThat(updated.conversionFactor()).isNull();
    }
}
