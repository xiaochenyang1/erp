package com.tuowei.erp.production.order;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationService;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomLineRequest;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
import com.tuowei.erp.production.completion.service.ProductionCompletionReversalService;
import com.tuowei.erp.production.completion.service.ProductionCompletionService;
import com.tuowei.erp.production.issue.service.ProductionIssueService;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionCompletionRequest;
import com.tuowei.erp.production.order.web.ProductionCompletionReversalRequest;
import com.tuowei.erp.production.order.web.ProductionIssueLineRequest;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionIssueRequest;
import com.tuowei.erp.production.order.web.ProductionReturnLineRequest;
import com.tuowei.erp.production.order.web.ProductionReturnRequest;
import com.tuowei.erp.production.returnmaterial.service.ProductionReturnService;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpringBootTest
@ActiveProfiles("test")
class ProductionOrderServiceTest {

    private static final long FINISHED_PRODUCT_ID = 892001L;
    private static final long MATERIAL_ONE_ID = 892002L;
    private static final long MATERIAL_TWO_ID = 892003L;
    private static final long MATERIAL_WAREHOUSE_ID = 892101L;
    private static final long FINISHED_WAREHOUSE_ID = 892102L;

    @Autowired
    private ProductionBomService productionBomService;

    @Autowired
    private ProductionOrderService productionOrderService;

    @Autowired
    private ProductionIssueService productionIssueService;

    @Autowired
    private ProductionCompletionService productionCompletionService;

    @Autowired
    private ProductionCompletionReversalService productionCompletionReversalService;

    @Autowired
    private ProductionReturnService productionReturnService;

    @Autowired
    private InventoryFinanceReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(FINISHED_PRODUCT_ID, "PRD-FG-892001", "制造成品");
        seedProduct(MATERIAL_ONE_ID, "PRD-MAT-892002", "制造材料1");
        seedProduct(MATERIAL_TWO_ID, "PRD-MAT-892003", "制造材料2");
        seedWarehouse(MATERIAL_WAREHOUSE_ID, "PRD-MWH-892101", "材料仓");
        seedWarehouse(FINISHED_WAREHOUSE_ID, "PRD-FWH-892102", "成品仓");
        seedOpenPeriod(892501L, 2026, "2026-05");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_COMPLETION_REVERSAL', 'PRODUCTION_RETURN')
                )
                """);
        jdbcTemplate.update("delete from fin_voucher where source_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_COMPLETION_REVERSAL', 'PRODUCTION_RETURN')");
        jdbcTemplate.update("delete from inv_reservation_event where source_type = 'PRODUCTION_ORDER'");
        jdbcTemplate.update("delete from inv_reservation where source_type = 'PRODUCTION_ORDER'");
        jdbcTemplate.update("delete from inv_txn where biz_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_COMPLETION_REVERSAL')");
        jdbcTemplate.update("delete from inv_txn where biz_type = 'PRODUCTION_RETURN'");
        jdbcTemplate.update("delete from prd_completion_reversal");
        jdbcTemplate.update("delete from prd_return_line");
        jdbcTemplate.update("delete from prd_return");
        jdbcTemplate.update("delete from prd_completion");
        jdbcTemplate.update("delete from prd_issue_line");
        jdbcTemplate.update("delete from prd_issue");
        jdbcTemplate.update("delete from inv_lot_balance where warehouse_id between 892100 and 892199");
        jdbcTemplate.update("delete from inv_balance where warehouse_id between 892100 and 892199");
        jdbcTemplate.update("delete from prd_order_material");
        jdbcTemplate.update("delete from prd_order");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_location where warehouse_id between 892100 and 892199");
        jdbcTemplate.update("delete from md_warehouse where id between 892100 and 892199");
        jdbcTemplate.update("delete from md_product where id between 892000 and 892999");
        jdbcTemplate.update("delete from fin_account_period where id between 892500 and 892599");
    }

    @Test
    @WithErpUser(authorities = {"production:bom:manage", "production:order:create", "production:order:view"})
    void expandsMaterialsFromBomWhenCreatingOrder() {
        ProductionBomResponse bom = createBom();

        ProductionOrderResponse response = productionOrderService.create(new ProductionOrderCreateRequest(
                bom.id(),
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                new BigDecimal("10.0000"),
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 21),
                "test order"
        ));

        Assertions.assertThat(response.status()).isEqualTo("DRAFT");
        Assertions.assertThat(response.materials()).hasSize(2);
        Assertions.assertThat(response.materials().get(0).requiredQty()).isEqualByComparingTo("22.0000");
        Assertions.assertThat(response.materials().get(1).requiredQty()).isEqualByComparingTo("30.0000");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:complete"
    })
    void releasesIssuesAndCompletesProductionOrderThroughInventoryPosting() {
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));

        ProductionOrderResponse released = productionOrderService.release(created.id());
        Assertions.assertThat(released.status()).isEqualTo("RELEASED");
        Assertions.assertThat(readAmount("inv_balance", "qty_reserved", MATERIAL_WAREHOUSE_ID, MATERIAL_ONE_ID))
                .isEqualByComparingTo("22.0000");

        ProductionOrderResponse issued = productionIssueService.issue(created.id());
        Assertions.assertThat(issued.status()).isEqualTo("MATERIAL_ISSUED");
        Assertions.assertThat(issued.issuedAmount()).isGreaterThan(BigDecimal.ZERO);
        Assertions.assertThat(readAmount("inv_balance", "qty_reserved", MATERIAL_WAREHOUSE_ID, MATERIAL_ONE_ID))
                .isEqualByComparingTo("0.0000");

        ProductionOrderResponse completed = productionCompletionService.complete(created.id());
        Assertions.assertThat(completed.status()).isEqualTo("COMPLETED");
        Assertions.assertThat(completed.finishedAmount()).isEqualByComparingTo(completed.issuedAmount());
        Assertions.assertThat(jdbcTemplate.queryForObject(
                        "select completed_qty from prd_order where id = ?",
                        BigDecimal.class,
                        completed.id()
                ))
                .isEqualByComparingTo("10.0000");
        Assertions.assertThat(readAmount("inv_balance", "qty_on_hand", FINISHED_WAREHOUSE_ID, FINISHED_PRODUCT_ID))
                .isEqualByComparingTo("10.0000");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:complete"
    })
    void productionIssueAndCompletionPostFinanceEntriesForReconciliation() {
        seedOpenPeriod(892502L, 2037, "2037-05");
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(new ProductionOrderCreateRequest(
                createBom().id(),
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                new BigDecimal("10.0000"),
                LocalDate.of(2037, 5, 18),
                LocalDate.of(2037, 5, 19),
                "finance production order"
        ));

        productionOrderService.release(created.id());
        ProductionOrderResponse issued = productionIssueService.issue(
                created.id(),
                new ProductionIssueRequest(LocalDate.of(2037, 5, 18), "finance issue")
        );
        ProductionOrderResponse completed = productionCompletionService.complete(
                created.id(),
                new ProductionCompletionRequest(LocalDate.of(2037, 5, 19), "finance completion")
        );

        Assertions.assertThat(financeEntryCount("PRODUCTION_ISSUE", completed.orderNo(), "5001", "debit_amount", issued.issuedAmount()))
                .isEqualTo(1);
        Assertions.assertThat(financeEntryCount("PRODUCTION_ISSUE", completed.orderNo(), "1001", "credit_amount", issued.issuedAmount()))
                .isEqualTo(1);
        Assertions.assertThat(financeEntryCount("PRODUCTION_COMPLETION", completed.orderNo(), "1001", "debit_amount", completed.finishedAmount()))
                .isEqualTo(1);
        Assertions.assertThat(financeEntryCount("PRODUCTION_COMPLETION", completed.orderNo(), "5001", "credit_amount", completed.finishedAmount()))
                .isEqualTo(1);

        InventoryFinanceReconciliationResponse reconciliation = reconciliationService.summary(892502L);
        Assertions.assertThat(reconciliation.balanced()).isTrue();
        Assertions.assertThat(reconciliation.inventoryNetAmount()).isEqualByComparingTo("0.00");
        Assertions.assertThat(reconciliation.financeInventoryNetAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:complete",
            "production:order:reverse-completion"
    })
    void productionCompletionReversalUsesSameSourceNoAcrossInventoryAndFinance() {
        seedOpenPeriod(892503L, 2037, "2037-06");
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(new ProductionOrderCreateRequest(
                createBom().id(),
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                new BigDecimal("10.0000"),
                LocalDate.of(2037, 6, 18),
                LocalDate.of(2037, 6, 19),
                "reversal reconciliation order"
        ));

        productionOrderService.release(created.id());
        productionIssueService.issue(
                created.id(),
                new ProductionIssueRequest(LocalDate.of(2037, 6, 18), "reversal issue")
        );
        productionCompletionService.complete(
                created.id(),
                new ProductionCompletionRequest(LocalDate.of(2037, 6, 19), "reversal completion")
        );

        productionCompletionReversalService.reverseCompletion(
                created.id(),
                new ProductionCompletionReversalRequest(LocalDate.of(2037, 6, 20), "reversal test")
        );

        String reversalNo = jdbcTemplate.queryForObject(
                "select reversal_no from prd_completion_reversal where order_id = ?",
                String.class,
                created.id()
        );
        Assertions.assertThat(jdbcTemplate.queryForObject(
                        "select biz_no from inv_txn where biz_type = 'PRODUCTION_COMPLETION_REVERSAL' order by id limit 1",
                        String.class
                ))
                .isEqualTo(reversalNo);
        Assertions.assertThat(jdbcTemplate.queryForObject(
                        "select source_no from fin_voucher where source_type = 'PRODUCTION_COMPLETION_REVERSAL' order by id limit 1",
                        String.class
                ))
                .isEqualTo(reversalNo);
        Assertions.assertThat(reconciliationService.differences(892503L, null))
                .noneMatch(difference -> "PRODUCTION_COMPLETION_REVERSAL".equals(difference.sourceType()));
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:complete"
    })
    void issuesAndCompletesProductionOrderInBatches() {
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));
        productionOrderService.release(created.id());
        Long materialOneLineId = created.materials().get(0).id();
        Long materialTwoLineId = created.materials().get(1).id();

        ProductionOrderResponse firstIssue = productionIssueService.issue(created.id(), new ProductionIssueRequest(
                LocalDate.of(2026, 5, 20),
                "first issue",
                List.of(
                        new ProductionIssueLineRequest(materialOneLineId, new BigDecimal("11.0000"), "m1 half"),
                        new ProductionIssueLineRequest(materialTwoLineId, new BigDecimal("15.0000"), "m2 half")
                )
        ));
        Assertions.assertThat(firstIssue.status()).isEqualTo("MATERIAL_ISSUED");
        Assertions.assertThat(firstIssue.materials().get(0).issuedQty()).isEqualByComparingTo("11.0000");
        Assertions.assertThat(firstIssue.materials().get(1).issuedQty()).isEqualByComparingTo("15.0000");

        ProductionOrderResponse firstCompletion = productionCompletionService.complete(created.id(), new ProductionCompletionRequest(
                LocalDate.of(2026, 5, 21),
                new BigDecimal("5.0000"),
                "first completion"
        ));
        Assertions.assertThat(firstCompletion.status()).isEqualTo("MATERIAL_ISSUED");
        Assertions.assertThat(firstCompletion.completedQty()).isEqualByComparingTo("5.0000");

        ProductionOrderResponse secondIssue = productionIssueService.issue(created.id(), new ProductionIssueRequest(
                LocalDate.of(2026, 5, 22),
                "second issue",
                List.of(
                        new ProductionIssueLineRequest(materialOneLineId, new BigDecimal("11.0000"), "m1 rest"),
                        new ProductionIssueLineRequest(materialTwoLineId, new BigDecimal("15.0000"), "m2 rest")
                )
        ));
        Assertions.assertThat(secondIssue.materials().get(0).issuedQty()).isEqualByComparingTo("22.0000");
        Assertions.assertThat(secondIssue.materials().get(1).issuedQty()).isEqualByComparingTo("30.0000");

        ProductionOrderResponse secondCompletion = productionCompletionService.complete(created.id(), new ProductionCompletionRequest(
                LocalDate.of(2026, 5, 23),
                new BigDecimal("5.0000"),
                "second completion"
        ));
        Assertions.assertThat(secondCompletion.status()).isEqualTo("COMPLETED");
        Assertions.assertThat(secondCompletion.completedQty()).isEqualByComparingTo("10.0000");
        Assertions.assertThat(rowCount("prd_issue")).isEqualTo(2);
        Assertions.assertThat(rowCount("prd_issue_line")).isEqualTo(4);
        Assertions.assertThat(rowCount("prd_completion")).isEqualTo(2);
        Assertions.assertThat(voucherCount("PRODUCTION_ISSUE")).isEqualTo(2);
        Assertions.assertThat(voucherCount("PRODUCTION_COMPLETION")).isEqualTo(2);
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue"
    })
    void productionIssueAutoPicksMaterialLots() {
        seedLotProduct(MATERIAL_ONE_ID);
        seedMaterialBalance(MATERIAL_ONE_ID, "5.0000", "65.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        seedMaterialLotBalance(892701L, MATERIAL_ONE_ID, "PRD-MAT-SOON", "2.0000", "20.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), LocalDateTime.of(2026, 5, 19, 8, 0));
        seedMaterialLotBalance(892702L, MATERIAL_ONE_ID, "PRD-MAT-LATER", "3.0000", "45.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDateTime.of(2026, 5, 19, 9, 0));
        ProductionOrderResponse created = productionOrderService.create(new ProductionOrderCreateRequest(
                createBom().id(),
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                new BigDecimal("2.0000"),
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 21),
                "lot issue order"
        ));
        productionOrderService.release(created.id());
        Long materialOneLineId = created.materials().get(0).id();

        productionIssueService.issue(created.id(), new ProductionIssueRequest(
                LocalDate.of(2026, 5, 20),
                "lot issue",
                List.of(new ProductionIssueLineRequest(
                        materialOneLineId,
                        new BigDecimal("4.4000"),
                        null,
                        null,
                        null,
                        "auto pick material lot"
                ))
        ));

        List<Map<String, Object>> txns = jdbcTemplate.queryForList("""
                select lot_no, qty, amount
                from inv_txn
                where biz_type = 'PRODUCTION_ISSUE'
                  and direction = 'OUT'
                  and product_id = ?
                order by id
                """, MATERIAL_ONE_ID);
        Assertions.assertThat(txns).hasSize(2);
        Assertions.assertThat(txns.get(0).get("LOT_NO")).isEqualTo("PRD-MAT-SOON");
        Assertions.assertThat((BigDecimal) txns.get(0).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(0).get("AMOUNT")).isEqualByComparingTo("20.00");
        Assertions.assertThat(txns.get(1).get("LOT_NO")).isEqualTo("PRD-MAT-LATER");
        Assertions.assertThat((BigDecimal) txns.get(1).get("QTY")).isEqualByComparingTo("2.4000");
        Assertions.assertThat((BigDecimal) txns.get(1).get("AMOUNT")).isEqualByComparingTo("36.00");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue"
    })
    void rejectsNullProductionIssueLine() {
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));
        productionOrderService.release(created.id());

        Assertions.assertThatThrownBy(() -> productionIssueService.issue(created.id(), new ProductionIssueRequest(
                        LocalDate.of(2026, 5, 20),
                        "null issue line",
                        Collections.singletonList(null)
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产领料明细不能为空");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:complete"
    })
    void productionCompletionCreatesFinishedGoodLot() {
        seedLotProduct(FINISHED_PRODUCT_ID);
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));
        productionOrderService.release(created.id());
        productionIssueService.issue(created.id());

        productionCompletionService.complete(created.id(), new ProductionCompletionRequest(
                LocalDate.of(2026, 5, 21),
                new BigDecimal("10.0000"),
                "FG-LOT-001",
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 12, 31),
                "lot completion"
        ));

        Map<String, Object> lot = jdbcTemplate.queryForMap("""
                select lot_no, production_date, expiry_date, qty_on_hand, amount_on_hand
                from inv_lot_balance
                where warehouse_id = ? and product_id = ? and lot_no = 'FG-LOT-001'
                """, FINISHED_WAREHOUSE_ID, FINISHED_PRODUCT_ID);
        Assertions.assertThat(lot.get("LOT_NO")).isEqualTo("FG-LOT-001");
        Assertions.assertThat(lot.get("PRODUCTION_DATE").toString()).isEqualTo("2026-05-20");
        Assertions.assertThat(lot.get("EXPIRY_DATE").toString()).isEqualTo("2026-12-31");
        Assertions.assertThat((BigDecimal) lot.get("QTY_ON_HAND")).isEqualByComparingTo("10.0000");
        Assertions.assertThat((BigDecimal) lot.get("AMOUNT_ON_HAND")).isEqualByComparingTo("380.00");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:return"
    })
    void returnsIssuedMaterialRestoresReservationAndAllowsReissue() {
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));
        productionOrderService.release(created.id());
        Long materialOneLineId = created.materials().get(0).id();

        productionIssueService.issue(created.id(), new ProductionIssueRequest(
                LocalDate.of(2026, 5, 20),
                "issue before return",
                List.of(new ProductionIssueLineRequest(materialOneLineId, new BigDecimal("11.0000"), "m1 issue"))
        ));
        BigDecimal reservedAfterIssue = readAmount("inv_balance", "qty_reserved", MATERIAL_WAREHOUSE_ID, MATERIAL_ONE_ID);

        ProductionOrderResponse returned = productionReturnService.returnMaterials(created.id(), new ProductionReturnRequest(
                LocalDate.of(2026, 5, 21),
                "return unused material",
                List.of(new ProductionReturnLineRequest(materialOneLineId, new BigDecimal("4.0000"), "m1 return"))
        ));

        Assertions.assertThat(returned.materials().get(0).issuedQty()).isEqualByComparingTo("7.0000");
        Assertions.assertThat(readAmount("inv_balance", "qty_reserved", MATERIAL_WAREHOUSE_ID, MATERIAL_ONE_ID))
                .isEqualByComparingTo(reservedAfterIssue.add(new BigDecimal("4.0000")));
        Assertions.assertThat(voucherCount("PRODUCTION_RETURN")).isEqualTo(1);

        ProductionOrderResponse reissued = productionIssueService.issue(created.id(), new ProductionIssueRequest(
                LocalDate.of(2026, 5, 22),
                "reissue returned material",
                List.of(new ProductionIssueLineRequest(materialOneLineId, new BigDecimal("4.0000"), "m1 reissue"))
        ));
        Assertions.assertThat(reissued.materials().get(0).issuedQty()).isEqualByComparingTo("11.0000");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:order:create", "production:order:view",
            "production:order:release", "production:order:issue", "production:order:return"
    })
    void rejectsNullProductionReturnLine() {
        seedMaterialBalance(MATERIAL_ONE_ID, "100.0000", "500.00");
        seedMaterialBalance(MATERIAL_TWO_ID, "100.0000", "900.00");
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));
        productionOrderService.release(created.id());
        Long materialOneLineId = created.materials().get(0).id();
        productionIssueService.issue(created.id(), new ProductionIssueRequest(
                LocalDate.of(2026, 5, 20),
                "issue before invalid return",
                List.of(new ProductionIssueLineRequest(materialOneLineId, new BigDecimal("11.0000"), "m1 issue"))
        ));

        Assertions.assertThatThrownBy(() -> productionReturnService.returnMaterials(created.id(), new ProductionReturnRequest(
                        LocalDate.of(2026, 5, 21),
                        "null return line",
                        Collections.singletonList(null)
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产退料明细不能为空");
    }

    @Test
    @WithErpUser(authorities = {"production:bom:manage", "production:order:create", "production:order:view"})
    void rejectsOrderVisibilityOutsideWarehouseScope() {
        ProductionOrderResponse created = productionOrderService.create(orderRequest(createBom().id()));

        TestSecurityContexts.useUser(
                892901L,
                1L,
                1L,
                1L,
                1L,
                "limited-production-user",
                "limited-production-user",
                Set.of(PermissionCodes.PRODUCTION_ORDER_VIEW),
                new DataScopeSnapshot(false, false, false, false, Set.of(MATERIAL_WAREHOUSE_ID))
        );

        Assertions.assertThatThrownBy(() -> productionOrderService.getById(created.id()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问该生产工单");
    }

    private ProductionBomResponse createBom() {
        return productionBomService.create(new ProductionBomCreateRequest(
                FINISHED_PRODUCT_ID,
                BigDecimal.ONE,
                "order bom",
                List.of(
                        new ProductionBomLineRequest(MATERIAL_ONE_ID, new BigDecimal("2.0000"), new BigDecimal("0.1000"), "material1"),
                        new ProductionBomLineRequest(MATERIAL_TWO_ID, new BigDecimal("3.0000"), BigDecimal.ZERO, "material2")
                )
        ));
    }

    private ProductionOrderCreateRequest orderRequest(Long bomId) {
        return new ProductionOrderCreateRequest(
                bomId,
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                new BigDecimal("10.0000"),
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 21),
                "test order"
        );
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '生产测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '生产测试', 892001, 892001, 0)
                """, id, code, name);
    }

    private void seedLotProduct(long id) {
        jdbcTemplate.update("""
                update md_product
                set lot_controlled = 1,
                    shelf_life_controlled = 1
                where id = ?
                """, id);
    }

    private void seedWarehouse(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_warehouse
                (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id,
                 address, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1, 1, '生产测试地址', 'ACTIVE', 0, '生产测试', 892001, 892001, 0)
                """, id, code, name);
        jdbcTemplate.update("""
                insert into md_location
                (id, company_id, account_book_id, warehouse_id, location_code, location_name,
                 is_default, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 'MAIN', '默认库位',
                        1, 'ACTIVE', 0, '生产测试默认库位', 892001, 892001, 0)
                """, id + 500000000000000000L, id);
    }

    private void seedMaterialBalance(long productId, String qty, String amount) {
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, product_id, location_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 0.0000, ?, 892001, 892001, 0)
                """, 8920000L + productId, MATERIAL_WAREHOUSE_ID, productId,
                MATERIAL_WAREHOUSE_ID + 500000000000000000L, new BigDecimal(qty), new BigDecimal(amount));
    }

    private void seedMaterialLotBalance(
            long id,
            long productId,
            String lotNo,
            String qty,
            String amount,
            LocalDate productionDate,
            LocalDate expiryDate,
            LocalDateTime firstInboundTime
    ) {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, location_id, lot_no, production_date, expiry_date,
                 first_inbound_time, qty_on_hand, qty_reserved, amount_on_hand,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, 0.0000, ?, 892001, 892001, 0)
                """, id, MATERIAL_WAREHOUSE_ID, productId, MATERIAL_WAREHOUSE_ID + 500000000000000000L,
                lotNo, productionDate, expiryDate, firstInboundTime, new BigDecimal(qty), new BigDecimal(amount));
    }

    private BigDecimal readAmount(String table, String column, long warehouseId, long productId) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from " + table + " where warehouse_id = ? and product_id = ?",
                BigDecimal.class,
                warehouseId,
                productId
        );
    }

    private Integer financeEntryCount(String sourceType, String sourceNo, String subjectCode, String amountColumn, BigDecimal amount) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from fin_voucher_entry e
                join fin_voucher v on v.id = e.voucher_id
                where v.source_type = ?
                  and v.source_no = ?
                  and e.subject_code = ?
                  and %s = ?
                """.formatted(amountColumn), Integer.class, sourceType, sourceNo, subjectCode, amount);
    }

    private Integer rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Integer voucherCount(String sourceType) {
        return jdbcTemplate.queryForObject("select count(*) from fin_voucher where source_type = ?", Integer.class, sourceType);
    }

    private void seedOpenPeriod(long id, int year, String periodMonth) {
        LocalDate start = LocalDate.parse(periodMonth + "-01");
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 'OPEN', 892001, 892001, 0)
                """, id, year, periodMonth, start, start.withDayOfMonth(start.lengthOfMonth()));
    }
}
