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
class ProductLotControlServiceTest {

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
                select 893001, id, 'product_type', '标准商品', 'STANDARD', 99, 'ACTIVE', 0,
                       'lot control test', 893001, 893001, 0
                  from sys_dict_type
                 where dict_type = 'product_type'
                   and not exists (select 1 from sys_dict_item where id = 893001)
                """);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from inv_lot_balance where product_id in (select id from md_product where product_code like 'LOT-PROD-%')");
        jdbcTemplate.update("delete from inv_balance where product_id in (select id from md_product where product_code like 'LOT-PROD-%')");
        jdbcTemplate.update("delete from md_product where id between 893000 and 893999 or product_code like 'LOT-PROD-%'");
        jdbcTemplate.update("delete from sys_dict_item where id = 893001");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create"})
    void rejectsShelfLifeControlWithoutLotControl() {
        Assertions.assertThatThrownBy(() -> productService.create(new ProductCreateRequest(
                "LOT-PROD-001", "效期商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, true, false, "bad flags"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("启用效期管理必须同时启用批次管理");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void rejectsEnablingLotControlWhenAggregateStockExists() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-002", "已有库存商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, "stock exists"
        ));
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, product_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (8931001, 1, 1, 893101, ?, 5.0000, 0.0000, 50.00, 893001, 893001, 0)
                """, created.id());

        Assertions.assertThatThrownBy(() -> productService.update(created.id(), new ProductUpdateRequest(
                "已有库存商品", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                true, false, false, "turn on lot"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("商品已有库存，不能直接启用批次管理");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create"})
    void defaultsNullLotFlagsToFalse() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-003", "默认标记商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                null, null, null, "null flags"
        ));

        Assertions.assertThat(created.lotControlled()).isFalse();
        Assertions.assertThat(created.shelfLifeControlled()).isFalse();
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void rejectsDisablingLotControlWhenLotStockExists() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-004", "批次库存商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                true, false, false, "lot stock exists"
        ));
        insertLotBalance(created.id(), new BigDecimal("1.0000"), BigDecimal.ZERO, BigDecimal.ZERO);

        Assertions.assertThatThrownBy(() -> productService.update(created.id(), new ProductUpdateRequest(
                "批次库存商品", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, "turn off lot"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("商品存在批次库存，不能关闭批次管理");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void rejectsEnablingLotControlWhenReservedStockExists() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-005", "预留库存商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, "reserved stock exists"
        ));
        insertBalance(created.id(), BigDecimal.ZERO, new BigDecimal("2.0000"), BigDecimal.ZERO);

        Assertions.assertThatThrownBy(() -> productService.update(created.id(), new ProductUpdateRequest(
                "预留库存商品", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                true, false, false, "turn on lot"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("商品已有库存，不能直接启用批次管理");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void rejectsEnablingLotControlWhenAmountStockExists() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-006", "金额库存商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, "amount stock exists"
        ));
        insertBalance(created.id(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"));

        Assertions.assertThatThrownBy(() -> productService.update(created.id(), new ProductUpdateRequest(
                "金额库存商品", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                true, false, false, "turn on lot"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("商品已有库存，不能直接启用批次管理");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void allowsEnablingLotControlWhenAggregateStockIsZero() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-007", "零库存商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, false, "zero stock"
        ));
        insertBalance(created.id(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        ProductResponse updated = productService.update(created.id(), new ProductUpdateRequest(
                "零库存商品", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                true, false, false, "turn on lot"
        ));

        Assertions.assertThat(updated.lotControlled()).isTrue();
        Assertions.assertThat(updated.shelfLifeControlled()).isFalse();
    }

    private void insertBalance(Long productId, BigDecimal qtyOnHand, BigDecimal qtyReserved, BigDecimal amountOnHand) {
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, product_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (?, 1, 1, 893101, ?, ?, ?, ?, 893001, 893001, 0)
                """, 8931000L + productId % 100000L, productId, qtyOnHand, qtyReserved, amountOnHand);
    }

    private void insertLotBalance(Long productId, BigDecimal qtyOnHand, BigDecimal qtyReserved, BigDecimal amountOnHand) {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no, production_date, expiry_date,
                 first_inbound_time, qty_on_hand, qty_reserved, amount_on_hand, created_by, updated_by, version)
                values (?, 1, 1, 893101, ?, 'LOT-001', current_date, date_add(current_date, interval 30 day),
                        current_timestamp, ?, ?, ?, 893001, 893001, 0)
                """, 8932000L + productId % 100000L, productId, qtyOnHand, qtyReserved, amountOnHand);
    }
}
