package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

class FlywayMigrationSmokeTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:flyway_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                FlywayMigrationSmokeTest.class,
                "flyway-smoke-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void createsProductionTablesAndColumns() {
        Long tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where lower(table_schema) = 'public'
                  and lower(table_name) in ('prd_bom', 'prd_bom_line', 'prd_order', 'prd_order_material')
                """, Long.class);
        Assertions.assertThat(tableCount).isEqualTo(4L);

        Long orderColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'prd_order'
                  and lower(column_name) in ('order_no', 'bom_id', 'material_warehouse_id', 'finished_warehouse_id', 'planned_qty', 'completed_qty', 'status')
                """, Long.class);
        Assertions.assertThat(orderColumnCount).isEqualTo(7L);
    }

    @Test
    void createsAccountPeriodTablesAndCostSubject() {
        Long tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'fin_account_period'
                """, Long.class);
        Assertions.assertThat(tableCount).isEqualTo(1L);

        Long periodColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'fin_account_period'
                  and lower(column_name) in ('period_month', 'start_date', 'end_date', 'status', 'locked_by', 'closed_by', 'reopened_by')
                """, Long.class);
        Assertions.assertThat(periodColumnCount).isEqualTo(7L);

        Long costSubjectCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_account_subject
                where company_id = 1
                  and account_book_id = 1
                  and subject_code = '6402'
                  and subject_name = '主营业务成本'
                  and status = 'ACTIVE'
                """, Long.class);
        Assertions.assertThat(costSubjectCount).isEqualTo(1L);

        Long productionCostSubjectCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_account_subject
                where company_id = 1
                  and account_book_id = 1
                  and subject_code = '5001'
                  and subject_name = '生产成本'
                  and status = 'ACTIVE'
        """, Long.class);
        Assertions.assertThat(productionCostSubjectCount).isEqualTo(1L);
    }

    @Test
    void createsProductionBatchExecutionTablesSequencesAndPermission() {
        Long tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where lower(table_schema) = 'public'
                  and lower(table_name) in ('prd_issue', 'prd_issue_line', 'prd_completion', 'prd_return', 'prd_return_line')
                """, Long.class);
        Assertions.assertThat(tableCount).isEqualTo(5L);

        Long issueLineColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'prd_issue_line'
                  and lower(column_name) in ('issue_id', 'order_id', 'order_material_id', 'material_product_id', 'issue_qty', 'issue_amount')
                """, Long.class);
        Assertions.assertThat(issueLineColumnCount).isEqualTo(6L);

        Long returnLineColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'prd_return_line'
                  and lower(column_name) in ('return_id', 'order_id', 'order_material_id', 'material_product_id', 'return_qty', 'return_amount')
                """, Long.class);
        Assertions.assertThat(returnLineColumnCount).isEqualTo(6L);

        Long sequenceCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_sequence_rule
                where biz_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_RETURN')
                  and status = 'ACTIVE'
                """, Long.class);
        Assertions.assertThat(sequenceCount).isEqualTo(3L);

        Long returnPermissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where permission = 'production:order:return'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(returnPermissionCount).isEqualTo(1L);
    }

    @Test
    void scopesProductionManufacturingIndexesByCompanyAndAccountBook() {
        assertIndexColumns("prd_bom", "uk_prd_bom_company_book_bom_no",
                "company_id", "account_book_id", "bom_no");
        assertIndexColumns("prd_bom", "idx_prd_bom_company_book_product",
                "company_id", "account_book_id", "product_id", "status");
        assertIndexColumns("prd_bom_line", "uk_prd_bom_line_company_book_bom_line",
                "company_id", "account_book_id", "bom_id", "line_no");
        assertIndexColumns("prd_bom_line", "uk_prd_bom_line_company_book_bom_material",
                "company_id", "account_book_id", "bom_id", "material_product_id");
        assertIndexColumns("prd_order", "uk_prd_order_company_book_order_no",
                "company_id", "account_book_id", "order_no");
        assertIndexColumns("prd_order", "idx_prd_order_company_book_status",
                "company_id", "account_book_id", "status", "planned_start_date");
        assertIndexColumns("prd_order", "idx_prd_order_company_book_warehouses",
                "company_id", "account_book_id", "material_warehouse_id", "finished_warehouse_id");
        assertIndexColumns("prd_order_material", "uk_prd_order_material_company_book_order_line",
                "company_id", "account_book_id", "order_id", "line_no");
        assertIndexColumns("prd_order_material", "idx_prd_order_material_company_book_order",
                "company_id", "account_book_id", "order_id");
        assertIndexColumns("prd_issue", "uk_prd_issue_company_book_no",
                "company_id", "account_book_id", "issue_no");
        assertIndexColumns("prd_issue", "idx_prd_issue_company_book_order",
                "company_id", "account_book_id", "order_id", "issue_date");
        assertIndexColumns("prd_issue_line", "idx_prd_issue_line_company_book_issue",
                "company_id", "account_book_id", "issue_id");
        assertIndexColumns("prd_issue_line", "idx_prd_issue_line_company_book_material",
                "company_id", "account_book_id", "order_material_id");
        assertIndexColumns("prd_completion", "uk_prd_completion_company_book_no",
                "company_id", "account_book_id", "completion_no");
        assertIndexColumns("prd_completion", "idx_prd_completion_company_book_order",
                "company_id", "account_book_id", "order_id", "completion_date");
        assertIndexColumns("prd_return", "uk_prd_return_company_book_no",
                "company_id", "account_book_id", "return_no");
        assertIndexColumns("prd_return", "idx_prd_return_company_book_order",
                "company_id", "account_book_id", "order_id", "return_date");
        assertIndexColumns("prd_return_line", "idx_prd_return_line_company_book_return",
                "company_id", "account_book_id", "return_id");
        assertIndexColumns("prd_return_line", "idx_prd_return_line_company_book_material",
                "company_id", "account_book_id", "order_material_id");
        assertIndexColumns("prd_completion_reversal", "uk_prd_completion_reversal_company_book_no",
                "company_id", "account_book_id", "reversal_no");
        assertIndexColumns("prd_completion_reversal", "idx_prd_completion_reversal_company_book_order",
                "company_id", "account_book_id", "order_id", "reversal_date");

        jdbcTemplate.update("""
                insert into prd_bom
                    (id, company_id, account_book_id, bom_no, product_id, base_qty, status)
                values
                    (992001, 992001, 1, 'BOM-SAME-NO', 1, 1.0000, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                insert into prd_bom
                    (id, company_id, account_book_id, bom_no, product_id, base_qty, status)
                values
                    (992002, 992001, 2, 'BOM-SAME-NO', 1, 1.0000, 'ACTIVE')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into prd_bom
                    (id, company_id, account_book_id, bom_no, product_id, base_qty, status)
                values
                    (992003, 992001, 1, 'BOM-SAME-NO', 1, 1.0000, 'ACTIVE')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into prd_order
                    (id, company_id, account_book_id, order_no, bom_id, product_id,
                     material_warehouse_id, finished_warehouse_id, planned_qty,
                     planned_start_date, planned_finish_date, status)
                values
                    (992101, 992101, 1, 'MO-SAME-NO', 1, 1, 1, 2, 1.0000,
                     date '2026-06-01', date '2026-06-02', 'DRAFT')
                """);
        jdbcTemplate.update("""
                insert into prd_order
                    (id, company_id, account_book_id, order_no, bom_id, product_id,
                     material_warehouse_id, finished_warehouse_id, planned_qty,
                     planned_start_date, planned_finish_date, status)
                values
                    (992102, 992101, 2, 'MO-SAME-NO', 1, 1, 1, 2, 1.0000,
                     date '2026-06-01', date '2026-06-02', 'DRAFT')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into prd_order
                    (id, company_id, account_book_id, order_no, bom_id, product_id,
                     material_warehouse_id, finished_warehouse_id, planned_qty,
                     planned_start_date, planned_finish_date, status)
                values
                    (992103, 992101, 1, 'MO-SAME-NO', 1, 1, 1, 2, 1.0000,
                     date '2026-06-01', date '2026-06-02', 'DRAFT')
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void createsInventoryLotExpiryColumnsAndTables() {
        Long lotBalanceTableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'inv_lot_balance'
                """, Long.class);
        Assertions.assertThat(lotBalanceTableCount).isEqualTo(1L);

        Long productColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = 'md_product'
                  and lower(column_name) in ('lot_controlled', 'shelf_life_controlled')
                """, Long.class);
        Assertions.assertThat(productColumnCount).isEqualTo(2L);

        assertColumnsExist("inv_lot_balance",
                "company_id", "account_book_id", "warehouse_id", "product_id", "lot_no",
                "production_date", "expiry_date", "first_inbound_time", "qty_on_hand",
                "qty_reserved", "amount_on_hand", "version");
        assertColumnsExist("inv_txn", "lot_no", "production_date", "expiry_date", "lot_key");

        for (String tableName : new String[]{
                "pur_receipt_line",
                "pur_return_line",
                "sal_delivery_line",
                "sal_return_line",
                "inv_adjustment_line",
                "inv_stock_check_line",
                "inv_transfer_line",
                "prd_issue_line",
                "prd_completion",
                "prd_return_line"
        }) {
            assertColumnsExist(tableName, "lot_no", "production_date", "expiry_date");
        }

        jdbcTemplate.update("""
                insert into inv_lot_balance
                    (id, company_id, account_book_id, warehouse_id, product_id, lot_no)
                values
                    (900001, 910001, 920001, 930001, 940001, 'LOT-A')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into inv_lot_balance
                    (id, company_id, account_book_id, warehouse_id, product_id, lot_no)
                values
                    (900002, 910001, 920001, 930001, 940001, 'LOT-A')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into inv_lot_balance
                    (id, company_id, account_book_id, warehouse_id, product_id, lot_no)
                values
                    (900003, 910001, 920002, 930001, 940001, 'LOT-A')
                """);

        jdbcTemplate.update("""
                insert into inv_txn
                    (id, company_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id, direction, qty, lot_key)
                values
                    (901001, 910001, 930001, 940001, 'SMOKE', 'SMOKE-1', 950001, 'IN', 1, 'LOT-A')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into inv_txn
                    (id, company_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id, direction, qty, lot_key)
                values
                    (901002, 910001, 930001, 940001, 'SMOKE', 'SMOKE-2', 950001, 'IN', 1, 'LOT-A')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into inv_txn
                    (id, company_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id, direction, qty, lot_key)
                values
                    (901003, 910001, 930001, 940001, 'SMOKE', 'SMOKE-3', 950001, 'IN', 1, 'LOT-B')
                """);
    }

    @Test
    void scopesInventoryStockAndReservationIndexesByCompanyAndAccountBook() {
        assertIndexColumns("inv_balance", "uk_inv_balance_company_book_warehouse_product",
                "company_id", "account_book_id", "warehouse_id", "product_id");
        assertIndexColumns("inv_balance", "idx_inv_balance_company_book_product",
                "company_id", "account_book_id", "product_id");
        assertIndexColumns("inv_txn", "idx_inv_txn_company_book_biz_no",
                "company_id", "account_book_id", "biz_no");
        assertIndexColumns("inv_txn", "idx_inv_txn_company_book_warehouse_product",
                "company_id", "account_book_id", "warehouse_id", "product_id");
        assertIndexColumns("inv_txn", "idx_inv_txn_company_book_occurred_time",
                "company_id", "account_book_id", "occurred_time");
        assertIndexColumns("inv_txn", "uk_inv_txn_company_book_biz_line_direction_lot_key",
                "company_id", "account_book_id", "biz_type", "biz_line_id", "direction", "lot_key");
        assertIndexColumns("inv_txn", "idx_inv_txn_company_book_biz_line_direction",
                "company_id", "account_book_id", "biz_type", "biz_line_id", "direction");
        assertIndexColumns("inv_txn", "idx_inv_txn_company_book_lot",
                "company_id", "account_book_id", "warehouse_id", "product_id", "lot_no");
        assertIndexColumns("sal_order", "idx_sal_order_company_book_warehouse_id",
                "company_id", "account_book_id", "warehouse_id");

        assertIndexColumns("inv_reservation", "uk_inv_reservation_company_book_source_line",
                "company_id", "account_book_id", "source_type", "source_line_id");
        assertIndexColumns("inv_reservation", "idx_inv_reservation_company_book_source",
                "company_id", "account_book_id", "source_type", "source_id");
        assertIndexColumns("inv_reservation", "idx_inv_reservation_company_book_balance",
                "company_id", "account_book_id", "warehouse_id", "product_id", "status");
        assertIndexColumns("inv_reservation_event", "idx_inv_reservation_event_company_book_reservation",
                "company_id", "account_book_id", "reservation_id", "created_time");
        assertIndexColumns("inv_reservation_event", "idx_inv_reservation_event_company_book_source",
                "company_id", "account_book_id", "source_type", "source_id");
        assertIndexColumns("inv_reservation_event", "idx_inv_reservation_event_company_book_balance",
                "company_id", "account_book_id", "warehouse_id", "product_id", "created_time");

        jdbcTemplate.update("""
                insert into inv_balance
                    (id, company_id, account_book_id, warehouse_id, product_id)
                values
                    (993001, 993001, 1, 993101, 993201)
                """);
        jdbcTemplate.update("""
                insert into inv_balance
                    (id, company_id, account_book_id, warehouse_id, product_id)
                values
                    (993002, 993001, 2, 993101, 993201)
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into inv_balance
                    (id, company_id, account_book_id, warehouse_id, product_id)
                values
                    (993003, 993001, 1, 993101, 993201)
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into inv_txn
                    (id, company_id, account_book_id, warehouse_id, product_id, biz_type,
                     biz_no, biz_line_id, direction, qty, lot_key)
                values
                    (993101, 993101, 1, 993301, 993401, 'SMOKE_STOCK',
                     'TXN-SAME-1', 993501, 'IN', 1, 'LOT-A')
                """);
        jdbcTemplate.update("""
                insert into inv_txn
                    (id, company_id, account_book_id, warehouse_id, product_id, biz_type,
                     biz_no, biz_line_id, direction, qty, lot_key)
                values
                    (993102, 993101, 2, 993301, 993401, 'SMOKE_STOCK',
                     'TXN-SAME-2', 993501, 'IN', 1, 'LOT-A')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into inv_txn
                    (id, company_id, account_book_id, warehouse_id, product_id, biz_type,
                     biz_no, biz_line_id, direction, qty, lot_key)
                values
                    (993103, 993101, 1, 993301, 993401, 'SMOKE_STOCK',
                     'TXN-SAME-3', 993501, 'IN', 1, 'LOT-A')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into inv_reservation
                    (id, company_id, account_book_id, warehouse_id, product_id,
                     source_type, source_id, source_no, source_line_id,
                     reserved_qty, remaining_qty)
                values
                    (993201, 993201, 1, 993601, 993701,
                     'SMOKE', 993801, 'SRC-SAME', 993901,
                     1, 1)
                """);
        jdbcTemplate.update("""
                insert into inv_reservation
                    (id, company_id, account_book_id, warehouse_id, product_id,
                     source_type, source_id, source_no, source_line_id,
                     reserved_qty, remaining_qty)
                values
                    (993202, 993201, 2, 993601, 993701,
                     'SMOKE', 993801, 'SRC-SAME', 993901,
                     1, 1)
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into inv_reservation
                    (id, company_id, account_book_id, warehouse_id, product_id,
                     source_type, source_id, source_no, source_line_id,
                     reserved_qty, remaining_qty)
                values
                    (993203, 993201, 1, 993601, 993701,
                     'SMOKE', 993801, 'SRC-SAME', 993901,
                     1, 1)
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void createsTenantScopedProductBarcodeColumnAndUniqueIndex() {
        assertColumnsExist("md_product", "barcode");
        assertIndexColumns("md_product", "uk_md_product_company_book_barcode",
                "company_id", "account_book_id", "barcode");

        jdbcTemplate.update("""
                insert into md_product
                    (id, company_id, account_book_id, product_code, product_name,
                     product_type, category_name, unit_name, purchase_price,
                     sale_price, tax_rate, barcode)
                values
                    (995901, 995001, 1, 'BARCODE-A-1', '条码测试商品1',
                     'STANDARD', 'TEST', '件', 1.00, 1.20, 0.1300, '6901234567890')
                """);
        jdbcTemplate.update("""
                insert into md_product
                    (id, company_id, account_book_id, product_code, product_name,
                     product_type, category_name, unit_name, purchase_price,
                     sale_price, tax_rate, barcode)
                values
                    (995902, 995001, 2, 'BARCODE-A-2', '条码测试商品2',
                     'STANDARD', 'TEST', '件', 1.00, 1.20, 0.1300, '6901234567890')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into md_product
                    (id, company_id, account_book_id, product_code, product_name,
                     product_type, category_name, unit_name, purchase_price,
                     sale_price, tax_rate, barcode)
                values
                    (995903, 995001, 1, 'BARCODE-A-3', '条码测试商品3',
                     'STANDARD', 'TEST', '件', 1.00, 1.20, 0.1300, '6901234567890')
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void createsFinanceSettlementReportIndexes() {
        assertIndexColumns("fin_payable", "idx_fin_payable_report_company_book_date",
                "company_id", "account_book_id", "deleted_flag", "biz_date", "id");
        assertIndexColumns("fin_payable", "idx_fin_payable_report_company_book_supplier_date",
                "company_id", "account_book_id", "deleted_flag", "supplier_id", "biz_date", "id");
        assertIndexColumns("fin_payable", "idx_fin_payable_report_company_book_status_date",
                "company_id", "account_book_id", "deleted_flag", "status", "biz_date", "id");
        assertIndexColumns("fin_receivable", "idx_fin_receivable_report_company_book_date",
                "company_id", "account_book_id", "deleted_flag", "biz_date", "id");
        assertIndexColumns("fin_receivable", "idx_fin_receivable_report_company_book_customer_date",
                "company_id", "account_book_id", "deleted_flag", "customer_id", "biz_date", "id");
        assertIndexColumns("fin_receivable", "idx_fin_receivable_report_company_book_status_date",
                "company_id", "account_book_id", "deleted_flag", "status", "biz_date", "id");
    }

    @Test
    void seedsFinanceSettlementActionPermissions() {
        Long menuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where permission in (
                    'finance:payment:create',
                    'finance:payment:cancel',
                    'finance:receipt:create',
                    'finance:receipt:cancel'
                )
                  and menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(menuCount).isEqualTo(4L);

        Long businessRoleBindingCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = 3002
                  and m.permission in (
                      'finance:payment:create',
                      'finance:payment:cancel',
                      'finance:receipt:create',
                      'finance:receipt:cancel'
                  )
                """, Long.class);
        Assertions.assertThat(businessRoleBindingCount).isEqualTo(4L);
    }

    @Test
    void seedsWorkflowWithdrawActionPermission() {
        Long menuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where permission = 'workflow:withdraw'
                  and menu_type = 'BUTTON'
                  and parent_id = 5011
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(menuCount).isEqualTo(1L);

        Long businessRoleBindingCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = 3002
                  and m.permission = 'workflow:withdraw'
                """, Long.class);
        Assertions.assertThat(businessRoleBindingCount).isEqualTo(1L);
    }

    @Test
    void seedsPurchaseOrderUnapproveActionPermission() {
        Long menuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where permission = 'purchase:order:unapprove'
                  and menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(menuCount).isEqualTo(1L);

        Long businessRoleBindingCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = 3002
                  and m.permission = 'purchase:order:unapprove'
                """, Long.class);
        Assertions.assertThat(businessRoleBindingCount).isEqualTo(1L);
    }

    @Test
    void seedsSalesOrderUnapproveActionPermission() {
        Long menuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where permission = 'sales:order:unapprove'
                  and menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(menuCount).isEqualTo(1L);

        Long businessRoleBindingCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = 3002
                  and m.permission = 'sales:order:unapprove'
                """, Long.class);
        Assertions.assertThat(businessRoleBindingCount).isEqualTo(1L);
    }

    @Test
    void seedsFrontendMenuCompletionCatalogsAndMenus() {
        // V94 补齐前端已有但后端缺失的顶级目录
        Long catalogCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'CATALOG'
                  and menu_code in ('MASTERDATA', 'PURCHASE', 'SALES')
                  and path in ('/masterdata', '/purchase', '/sales')
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(catalogCount).isEqualTo(3L);

        // 关键 MENU 节点存在，path/component 对齐前端真实路由
        Long menuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'MENU'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                  and (
                    (path = '/masterdata/products' and component = 'masterdata/products/index') or
                    (path = '/sales/orders' and component = 'sales/orders/index') or
                    (path = '/purchase/receipts' and component = 'purchase/receipts/index') or
                    (path = '/inventory/stocks' and component = 'inventory/stocks/index') or
                    (path = '/finance/payments' and component = 'finance/payments/index') or
                    (path = '/system/configs' and component = 'system/configs/index') or
                    (path = '/reports/traces' and component = 'reports/traces/index')
                  )
                """, Long.class);
        Assertions.assertThat(menuCount).isEqualTo(7L);
    }

    @Test
    void correctsFinanceSubjectMenuPathToFrontendRoute() {
        // V94 将会计科目 path 从 /finance/account-subjects 校正为前端路由 /finance/subjects
        Long correctedCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_code = 'FINANCE_SUBJECT'
                  and path = '/finance/subjects'
                  and component = 'finance/subjects/index'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(correctedCount).isEqualTo(1L);

        Long staleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where path = '/finance/account-subjects'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(staleCount).isEqualTo(0L);
    }

    @Test
    void seedsFirstWaveButtonPermissionsAndBindsBusinessRole() {
        // V94 首批高风险写操作页 BUTTON 节点
        Long buttonCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                  and permission in (
                    'sales:order:create', 'sales:order:approve', 'sales:order:unapprove',
                    'sales:delivery:post', 'sales:return:post',
                    'purchase:receipt:post', 'purchase:return:post',
                    'inventory:adjustment:create', 'inventory:transfer:cancel',
                    'production:order:return',
                    'system:user:create', 'system:role:assign-menu'
                  )
                """, Long.class);
        Assertions.assertThat(buttonCount).isEqualTo(12L);

        // 新节点绑定给 ERP_ADMIN(3002)
        Long businessRoleBindingCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = 3002
                  and m.permission in (
                    'sales:order:approve', 'purchase:receipt:post',
                    'inventory:adjustment:create', 'system:user:create'
                  )
                """, Long.class);
        Assertions.assertThat(businessRoleBindingCount).isEqualTo(4L);
    }

    @Test
    void seedsSecondWaveButtonPermissionsAndBindsBusinessRole() {
        // V95 第二批按钮：masterdata/purchase-order/inventory-check/system 各写操作
        Long buttonCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                  and permission in (
                    'masterdata:product:create', 'masterdata:customer:update',
                    'masterdata:supplier:enable', 'masterdata:warehouse:disable',
                    'purchase:order:approve', 'purchase:order:close',
                    'inventory:check:adjust', 'inventory:check:cancel',
                    'system:menu:create', 'system:dept:update',
                    'system:post:enable', 'system:dict:disable',
                    'system:config:create', 'system:sequence-rule:update'
                  )
                """, Long.class);
        Assertions.assertThat(buttonCount).isEqualTo(14L);

        // 新节点绑定给 ERP_ADMIN(3002)
        Long businessRoleBindingCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = 3002
                  and m.permission in (
                    'masterdata:customer:create', 'purchase:order:submit',
                    'inventory:check:create', 'system:dict:create',
                    'system:sequence-rule:disable'
                  )
                """, Long.class);
        Assertions.assertThat(businessRoleBindingCount).isEqualTo(5L);
    }

    @Test
    void scopesFinanceExpenseAndVoucherEntryIndexesByCompanyAndAccountBook() {
        assertIndexColumns("fin_account_subject", "idx_fin_account_subject_company_book_parent",
                "company_id", "account_book_id", "parent_id");
        assertIndexColumns("fin_expense", "uk_fin_expense_company_book_no",
                "company_id", "account_book_id", "expense_no");
        assertIndexColumns("fin_expense", "idx_fin_expense_company_book_date",
                "company_id", "account_book_id", "expense_date");
        assertIndexColumns("fin_voucher_entry", "idx_fin_voucher_entry_company_book_subject",
                "company_id", "account_book_id", "subject_code", "biz_date");
        assertIndexColumns("fin_voucher_entry", "idx_fin_voucher_entry_company_book_voucher",
                "company_id", "account_book_id", "voucher_id");

        jdbcTemplate.update("""
                insert into fin_expense
                    (id, company_id, account_book_id, expense_no, expense_date,
                     subject_id, payment_subject_id, amount)
                values
                    (994001, 994001, 1, 'FE-SAME-NO', date '2026-06-01',
                     1, 2, 10.00)
                """);
        jdbcTemplate.update("""
                insert into fin_expense
                    (id, company_id, account_book_id, expense_no, expense_date,
                     subject_id, payment_subject_id, amount)
                values
                    (994002, 994001, 2, 'FE-SAME-NO', date '2026-06-01',
                     1, 2, 10.00)
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into fin_expense
                    (id, company_id, account_book_id, expense_no, expense_date,
                     subject_id, payment_subject_id, amount)
                values
                    (994003, 994001, 1, 'FE-SAME-NO', date '2026-06-01',
                     1, 2, 10.00)
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void scopesLegacyDocumentAndSequenceUniqueKeysByCompanyAndAccountBook() {
        assertColumnsExist("sys_sequence_rule", "company_id", "account_book_id");
        assertIndexColumns("sys_sequence_rule", "uk_sys_sequence_rule_company_book_biz_type",
                "company_id", "account_book_id", "biz_type");
        assertColumnsExist("sys_sequence_counter",
                "company_id", "account_book_id", "biz_type", "period_key", "current_value", "version");
        assertIndexColumns("sys_sequence_counter", "uk_sys_sequence_counter_company_book_biz_period",
                "company_id", "account_book_id", "biz_type", "period_key");
        assertIndexColumns("sys_sequence_counter", "idx_sys_sequence_counter_company_book_biz",
                "company_id", "account_book_id", "biz_type");

        assertIndexColumns("pur_order", "uk_pur_order_company_book_order_no",
                "company_id", "account_book_id", "order_no");
        assertIndexColumns("pur_receipt", "uk_pur_receipt_company_book_receipt_no",
                "company_id", "account_book_id", "receipt_no");
        assertIndexColumns("pur_return", "uk_pur_return_company_book_return_no",
                "company_id", "account_book_id", "return_no");
        assertIndexColumns("sal_order", "uk_sal_order_company_book_order_no",
                "company_id", "account_book_id", "order_no");
        assertIndexColumns("sal_delivery", "uk_sal_delivery_company_book_delivery_no",
                "company_id", "account_book_id", "delivery_no");
        assertIndexColumns("sal_return", "uk_sal_return_company_book_return_no",
                "company_id", "account_book_id", "return_no");
        assertIndexColumns("inv_adjustment", "uk_inv_adjustment_company_book_adjustment_no",
                "company_id", "account_book_id", "adjustment_no");
        assertIndexColumns("inv_stock_check", "uk_inv_stock_check_company_book_check_no",
                "company_id", "account_book_id", "check_no");
        assertIndexColumns("inv_transfer", "uk_inv_transfer_company_book_transfer_no",
                "company_id", "account_book_id", "transfer_no");
        assertIndexColumns("fin_payable", "uk_fin_payable_company_book_payable_no",
                "company_id", "account_book_id", "payable_no");
        assertIndexColumns("fin_payable", "uk_fin_payable_company_book_source",
                "company_id", "account_book_id", "source_type", "source_id");
        assertIndexColumns("fin_payment", "uk_fin_payment_company_book_payment_no",
                "company_id", "account_book_id", "payment_no");
        assertIndexColumns("fin_receivable", "uk_fin_receivable_company_book_receivable_no",
                "company_id", "account_book_id", "receivable_no");
        assertIndexColumns("fin_receivable", "uk_fin_receivable_company_book_source",
                "company_id", "account_book_id", "source_type", "source_id");
        assertIndexColumns("fin_receipt", "uk_fin_receipt_company_book_receipt_no",
                "company_id", "account_book_id", "receipt_no");
        assertIndexColumns("fin_voucher", "uk_fin_voucher_company_book_voucher_no",
                "company_id", "account_book_id", "voucher_no");
        assertIndexColumns("fin_voucher", "uk_fin_voucher_company_book_source",
                "company_id", "account_book_id", "source_type", "source_id");
        assertIndexColumns("inv_alert_rule", "uk_inv_alert_rule_company_book_product_warehouse",
                "company_id", "account_book_id", "product_id", "warehouse_id");

        jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value, status,
                     created_by, updated_by, version)
                values
                    (990001, 990001, 1, 'SMOKE_TENANT_RULE', 'A', 'yyyyMMdd', 3, 0, 'ACTIVE', 0, 0, 0)
                """);
        jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value, status,
                     created_by, updated_by, version)
                values
                    (990002, 990001, 2, 'SMOKE_TENANT_RULE', 'B', 'yyyyMMdd', 3, 0, 'ACTIVE', 0, 0, 0)
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value, status,
                     created_by, updated_by, version)
                values
                    (990003, 990001, 1, 'SMOKE_TENANT_RULE', 'C', 'yyyyMMdd', 3, 0, 'ACTIVE', 0, 0, 0)
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into pur_order
                    (id, company_id, account_book_id, order_no, supplier_id, order_date)
                values
                    (990101, 990101, 1, 'PO-SAME-NO', 1, date '2026-06-01')
                """);
        jdbcTemplate.update("""
                insert into pur_order
                    (id, company_id, account_book_id, order_no, supplier_id, order_date)
                values
                    (990102, 990101, 2, 'PO-SAME-NO', 1, date '2026-06-01')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into pur_order
                    (id, company_id, account_book_id, order_no, supplier_id, order_date)
                values
                    (990103, 990101, 1, 'PO-SAME-NO', 1, date '2026-06-01')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into fin_payable
                    (id, company_id, account_book_id, payable_no, source_type, source_id, source_no,
                     direction, supplier_id, biz_date)
                values
                    (990201, 990201, 1, 'AP-SAME-NO', 'SMOKE', 990001, 'SRC-SAME',
                     'INCREASE', 1, date '2026-06-01')
                """);
        jdbcTemplate.update("""
                insert into fin_payable
                    (id, company_id, account_book_id, payable_no, source_type, source_id, source_no,
                     direction, supplier_id, biz_date)
                values
                    (990202, 990201, 2, 'AP-SAME-NO', 'SMOKE', 990001, 'SRC-SAME',
                     'INCREASE', 1, date '2026-06-01')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into fin_payable
                    (id, company_id, account_book_id, payable_no, source_type, source_id, source_no,
                     direction, supplier_id, biz_date)
                values
                    (990203, 990201, 1, 'AP-OTHER-NO', 'SMOKE', 990001, 'SRC-SAME',
                     'INCREASE', 1, date '2026-06-01')
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void scopesAccountBookMasterDataAndSubjectUniqueKeysByCompanyAndAccountBook() {
        assertIndexColumns("sys_role", "uk_sys_role_company_book_role_code",
                "company_id", "account_book_id", "role_code");
        assertIndexColumns("sys_dept", "uk_sys_dept_company_book_dept_code",
                "company_id", "account_book_id", "dept_code");
        assertIndexColumns("sys_post", "uk_sys_post_company_book_post_code",
                "company_id", "account_book_id", "post_code");
        assertIndexColumns("md_product", "uk_md_product_company_book_product_code",
                "company_id", "account_book_id", "product_code");
        assertIndexColumns("md_customer", "uk_md_customer_company_book_customer_code",
                "company_id", "account_book_id", "customer_code");
        assertIndexColumns("md_supplier", "uk_md_supplier_company_book_supplier_code",
                "company_id", "account_book_id", "supplier_code");
        assertIndexColumns("md_warehouse", "uk_md_warehouse_company_book_warehouse_code",
                "company_id", "account_book_id", "warehouse_code");
        assertIndexColumns("fin_account_subject", "uk_fin_account_subject_company_book_code",
                "company_id", "account_book_id", "subject_code");

        jdbcTemplate.update("""
                insert into md_product
                    (id, company_id, account_book_id, product_code, product_name, product_type,
                     category_name, unit_name, purchase_price, sale_price, tax_rate)
                values
                    (991001, 991001, 1, 'P-SAME-CODE', '账套一商品', 'STANDARD',
                     '默认', '件', 1.00, 1.00, 0.0000)
                """);
        jdbcTemplate.update("""
                insert into md_product
                    (id, company_id, account_book_id, product_code, product_name, product_type,
                     category_name, unit_name, purchase_price, sale_price, tax_rate)
                values
                    (991002, 991001, 2, 'P-SAME-CODE', '账套二商品', 'STANDARD',
                     '默认', '件', 1.00, 1.00, 0.0000)
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into md_product
                    (id, company_id, account_book_id, product_code, product_name, product_type,
                     category_name, unit_name, purchase_price, sale_price, tax_rate)
                values
                    (991003, 991001, 1, 'P-SAME-CODE', '账套一重复商品', 'STANDARD',
                     '默认', '件', 1.00, 1.00, 0.0000)
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into fin_account_subject
                    (id, company_id, account_book_id, subject_code, subject_name, subject_type, balance_direction)
                values
                    (991101, 991001, 1, '1001', '账套一库存商品', 'ASSET', 'DEBIT')
                """);
        jdbcTemplate.update("""
                insert into fin_account_subject
                    (id, company_id, account_book_id, subject_code, subject_name, subject_type, balance_direction)
                values
                    (991102, 991001, 2, '1001', '账套二库存商品', 'ASSET', 'DEBIT')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into fin_account_subject
                    (id, company_id, account_book_id, subject_code, subject_name, subject_type, balance_direction)
                values
                    (991103, 991001, 1, '1001', '账套一重复库存商品', 'ASSET', 'DEBIT')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into sys_role
                    (id, company_id, account_book_id, role_code, role_name, status)
                values
                    (991201, 991001, 1, 'SAME_ROLE', '账套一角色', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                insert into sys_role
                    (id, company_id, account_book_id, role_code, role_name, status)
                values
                    (991202, 991001, 2, 'SAME_ROLE', '账套二角色', 'ACTIVE')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sys_role
                    (id, company_id, account_book_id, role_code, role_name, status)
                values
                    (991203, 991001, 1, 'SAME_ROLE', '账套一重复角色', 'ACTIVE')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into sys_dept
                    (id, company_id, account_book_id, parent_id, dept_code, dept_name)
                values
                    (991301, 991001, 1, 0, 'SAME_DEPT', '账套一部门')
                """);
        jdbcTemplate.update("""
                insert into sys_dept
                    (id, company_id, account_book_id, parent_id, dept_code, dept_name)
                values
                    (991302, 991001, 2, 0, 'SAME_DEPT', '账套二部门')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sys_dept
                    (id, company_id, account_book_id, parent_id, dept_code, dept_name)
                values
                    (991303, 991001, 1, 0, 'SAME_DEPT', '账套一重复部门')
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("""
                insert into sys_post
                    (id, company_id, account_book_id, dept_id, post_code, post_name)
                values
                    (991401, 991001, 1, 991301, 'SAME_POST', '账套一岗位')
                """);
        jdbcTemplate.update("""
                insert into sys_post
                    (id, company_id, account_book_id, dept_id, post_code, post_name)
                values
                    (991402, 991001, 2, 991302, 'SAME_POST', '账套二岗位')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sys_post
                    (id, company_id, account_book_id, dept_id, post_code, post_name)
                values
                    (991403, 991001, 1, 991301, 'SAME_POST', '账套一重复岗位')
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void scopesMasterDataImportAndAttachmentQueryIndexesByCompanyAndAccountBook() {
        assertIndexColumns("md_product", "idx_md_product_company_book_deleted_status_code",
                "company_id", "account_book_id", "deleted_flag", "status", "product_code");
        assertIndexColumns("md_customer", "idx_md_customer_company_book_deleted_status_code",
                "company_id", "account_book_id", "deleted_flag", "status", "customer_code");
        assertIndexColumns("md_supplier", "idx_md_supplier_company_book_deleted_status_code",
                "company_id", "account_book_id", "deleted_flag", "status", "supplier_code");
        assertIndexColumns("md_warehouse", "idx_md_warehouse_company_book_deleted_status_code",
                "company_id", "account_book_id", "deleted_flag", "status", "warehouse_code");
        assertIndexColumns("md_warehouse", "idx_md_warehouse_company_book_dept_id",
                "company_id", "account_book_id", "dept_id");
        assertIndexColumns("md_warehouse", "idx_md_warehouse_company_book_manager_user_id",
                "company_id", "account_book_id", "manager_user_id");
        assertIndexColumns("sys_import_job", "idx_sys_import_job_company_book_type_status",
                "company_id", "account_book_id", "import_type", "status", "created_time");
        assertIndexColumns("sys_import_job", "idx_sys_import_job_company_book_created",
                "company_id", "account_book_id", "created_time");
        assertIndexColumns("sys_import_job_row", "idx_sys_import_job_row_company_book_job",
                "company_id", "account_book_id", "job_id");
        assertIndexColumns("sys_attachment", "idx_sys_attachment_company_book_created_time",
                "company_id", "account_book_id", "created_time");
    }

    @Test
    void scopesSystemSessionIndexesByCompanyAndAccountBook() {
        assertIndexColumns("sys_refresh_token", "idx_sys_refresh_token_company_book_status",
                "company_id", "account_book_id", "status", "issued_at", "id");
        assertIndexColumns("sys_refresh_token", "idx_sys_refresh_token_company_book_user_status",
                "company_id", "account_book_id", "user_id", "status");
    }

    @Test
    void scopesIdempotencyRequestsByCompanyAccountBookAndUser() {
        assertColumnsExist("sys_idempotency_request",
                "company_id", "account_book_id", "user_id", "request_method", "request_path", "idempotency_key");
        assertIndexColumns("sys_idempotency_request", "uk_sys_idempotency_book_user_scope_key",
                "company_id", "account_book_id", "user_id", "request_method", "request_path", "idempotency_key");
        assertIndexColumns("sys_idempotency_request", "idx_sys_idempotency_company_book_expires_at",
                "company_id", "account_book_id", "expires_at");

        jdbcTemplate.update("""
                insert into sys_idempotency_request
                    (id, company_id, account_book_id, user_id, idempotency_key,
                     request_method, request_path, request_body_hash, status, expires_at)
                values
                    (995001, 995001, 1, 995101, 'IDEM-SAME',
                     'POST', '/api/smoke/idempotency', 'HASH-A', 'PROCESSING',
                     timestamp '2026-06-01 10:00:00')
                """);
        jdbcTemplate.update("""
                insert into sys_idempotency_request
                    (id, company_id, account_book_id, user_id, idempotency_key,
                     request_method, request_path, request_body_hash, status, expires_at)
                values
                    (995002, 995001, 2, 995101, 'IDEM-SAME',
                     'POST', '/api/smoke/idempotency', 'HASH-A', 'PROCESSING',
                     timestamp '2026-06-01 10:00:00')
                """);
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sys_idempotency_request
                    (id, company_id, account_book_id, user_id, idempotency_key,
                     request_method, request_path, request_body_hash, status, expires_at)
                values
                    (995003, 995001, 1, 995101, 'IDEM-SAME',
                     'POST', '/api/smoke/idempotency', 'HASH-A', 'PROCESSING',
                     timestamp '2026-06-01 10:00:00')
                """)).isInstanceOf(Exception.class);
    }

    private void assertColumnsExist(String tableName, String... columnNames) {
        Long columnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = ?
                  and lower(column_name) in (%s)
                """.formatted("?,".repeat(columnNames.length - 1) + "?"), Long.class,
                columnQueryArgs(tableName, columnNames));
        Assertions.assertThat(columnCount).isEqualTo(columnNames.length);
    }

    private void assertIndexColumns(String tableName, String indexName, String... columnNames) {
        var actualColumns = jdbcTemplate.queryForList("""
                select lower(column_name)
                from information_schema.index_columns
                where lower(table_schema) = 'public'
                  and lower(table_name) = ?
                  and lower(index_name) = ?
                order by ordinal_position
                """, String.class, tableName, indexName);
        Assertions.assertThat(actualColumns).containsExactly(columnNames);
    }

    private Object[] columnQueryArgs(String tableName, String[] columnNames) {
        Object[] args = new Object[columnNames.length + 1];
        args[0] = tableName;
        System.arraycopy(columnNames, 0, args, 1, columnNames.length);
        return args;
    }
}
