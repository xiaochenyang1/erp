package com.tuowei.erp.imports;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.imports.service.ImportJobService;
import com.tuowei.erp.imports.web.ImportJobResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class InitialImportControllerTest {

    private static final long IMPORT_USER_ID = 880001L;
    private static final String IMPORT_PERMISSION = "import:init:manage";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from sys_import_job_row where job_id in (select id from sys_import_job where created_by = ?)", IMPORT_USER_ID);
        jdbcTemplate.update("delete from sys_import_job where created_by = ?", IMPORT_USER_ID);
        jdbcTemplate.update("delete from fin_receivable where receivable_no like 'IMP-AR-%'");
        jdbcTemplate.update("delete from md_customer where customer_code like 'IMP-CUST-%'");
        jdbcTemplate.update("delete from inv_txn where product_id in (select id from md_product where product_code like 'IMP-PROD-%')");
        jdbcTemplate.update("delete from inv_lot_balance where product_id in (select id from md_product where product_code like 'IMP-PROD-%')");
        jdbcTemplate.update("delete from inv_balance where product_id in (select id from md_product where product_code like 'IMP-PROD-%')");
        jdbcTemplate.update("delete from md_location where warehouse_id in (select id from md_warehouse where warehouse_code like 'IMP-WH-%')");
        jdbcTemplate.update("delete from md_warehouse where warehouse_code like 'IMP-WH-%'");
        jdbcTemplate.update("delete from md_product where product_code like 'IMP-PROD-%'");
        jdbcTemplate.update("delete from fin_account_period where id = 880001");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void downloadsProductTemplate() {
        ResponseEntity<ByteArrayResource> response = importJobService.template("PRODUCT");

        Assertions.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Assertions.assertThat(response.getHeaders().getContentType()).isNotNull();
        Assertions.assertThat(response.getHeaders().getContentType().isCompatibleWith(org.springframework.http.MediaType.valueOf("text/csv"))).isTrue();
        Assertions.assertThat(response.getBody()).isNotNull();
        String content = new String(response.getBody().getByteArray(), StandardCharsets.UTF_8);
        Assertions.assertThat(content)
                .contains("product_code,product_name,product_type")
                .contains("P001,标准商品");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void downloadsTemplateWithNormalizedImportType() {
        ResponseEntity<ByteArrayResource> response = importJobService.template(" product ");

        Assertions.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Assertions.assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION))
                .contains("filename*=UTF-8''product-template.csv");
        Assertions.assertThat(response.getBody()).isNotNull();
        Assertions.assertThat(new String(response.getBody().getByteArray(), StandardCharsets.UTF_8))
                .contains("product_code,product_name,product_type");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void rejectsCsvWithWrongHeader() {
        MockMultipartFile file = csvFile("bad-product.csv", "bad_header\nvalue\n");

        Assertions.assertThatThrownBy(() -> importJobService.preview("PRODUCT", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV表头不匹配，请使用系统提供的模板");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void previewsAndCommitsProductImportThenRejectsRepeatedCommit() {
        String productCode = "IMP-PROD-880001";
        String csv = "product_code,product_name,product_type,category_name,specification,unit_name,aux_unit_name,conversion_factor,barcode,purchase_price,sale_price,tax_rate,status,lot_controlled,shelf_life_controlled,inspection_required,serial_controlled,remark\n"
                + productCode + ",导入测试商品,STANDARD,导入分类,规格A,件,箱,12,6901234567890,10.00,18.00,13.00,ACTIVE,1,1,1,0,导入测试\n";

        ImportJobResponse preview = importJobService.preview("PRODUCT", csvFile("product.csv", csv));
        Assertions.assertThat(preview.status()).isEqualTo("VALIDATED");
        Assertions.assertThat(preview.validRows()).isEqualTo(1);
        Assertions.assertThat(preview.errorRows()).isZero();

        ImportJobResponse committed = importJobService.commit(preview.jobId());
        Assertions.assertThat(committed.status()).isEqualTo("COMMITTED");
        Assertions.assertThat(committed.committedRows()).isEqualTo(1);

        Long importedCount = jdbcTemplate.queryForObject(
                "select count(*) from md_product where product_code = ? and company_id = 1 and account_book_id = 1",
                Long.class,
                productCode
        );
        Assertions.assertThat(importedCount).isEqualTo(1L);

        Map<String, Object> product = jdbcTemplate.queryForMap(
                "select aux_unit_name, conversion_factor, barcode, lot_controlled, shelf_life_controlled, inspection_required, serial_controlled from md_product where product_code = ? and company_id = 1 and account_book_id = 1",
                productCode
        );
        Assertions.assertThat(String.valueOf(product.get("AUX_UNIT_NAME") != null ? product.get("AUX_UNIT_NAME") : product.get("aux_unit_name")))
                .isEqualTo("箱");
        Object factor = product.get("CONVERSION_FACTOR") != null ? product.get("CONVERSION_FACTOR") : product.get("conversion_factor");
        Assertions.assertThat(new BigDecimal(factor.toString()).compareTo(new BigDecimal("12"))).isZero();
        Object barcode = product.get("BARCODE") != null ? product.get("BARCODE") : product.get("barcode");
        Assertions.assertThat(String.valueOf(barcode)).isEqualTo("6901234567890");
        Assertions.assertThat(intColumn(product, "LOT_CONTROLLED", "lot_controlled")).isEqualTo(1);
        Assertions.assertThat(intColumn(product, "SHELF_LIFE_CONTROLLED", "shelf_life_controlled")).isEqualTo(1);
        Assertions.assertThat(intColumn(product, "INSPECTION_REQUIRED", "inspection_required")).isEqualTo(1);
        Assertions.assertThat(intColumn(product, "SERIAL_CONTROLLED", "serial_controlled")).isEqualTo(0);

        Assertions.assertThatThrownBy(() -> importJobService.commit(preview.jobId()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有校验通过的导入任务才能提交");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void previewStoresSafeOriginalFilename() {
        String csv = productCsv("IMP-PROD-FILENAME");

        ImportJobResponse preview = importJobService.preview(
                "PRODUCT",
                csvFile("..\\..\\unsafe:\r\nname?.csv", csv)
        );

        Assertions.assertThat(preview.fileName()).isEqualTo("unsafe___name_.csv");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void previewStoresNormalizedImportType() {
        String csv = productCsv("IMP-PROD-TYPE");

        ImportJobResponse preview = importJobService.preview(" product ", csvFile("product.csv", csv));

        Assertions.assertThat(preview.importType()).isEqualTo("PRODUCT");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void previewLimitsStoredFilenameToDatabaseColumnLength() {
        String csv = productCsv("IMP-PROD-LONGNAME");

        ImportJobResponse preview = importJobService.preview(
                "PRODUCT",
                csvFile("a".repeat(300) + ".csv", csv)
        );

        Assertions.assertThat(preview.fileName())
                .hasSize(255)
                .endsWith(".csv");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void marksJobFailedWhenOpeningReceivableCommitViolatesOpeningGuard() {
        seedOpenPeriod();
        seedCustomer(880101L, "IMP-CUST-FAIL");
        String csv = "customer_code,receivable_no,biz_date,original_amount,settled_amount,remark\n"
                + "IMP-CUST-FAIL,IMP-AR-FAIL,2026-05-19,100.00,0.00,期初应收失败测试\n";

        ImportJobResponse preview = importJobService.preview("OPENING_RECEIVABLE", csvFile("opening-receivable.csv", csv));
        Assertions.assertThat(preview.status()).isEqualTo("VALIDATED");
        seedNormalReceivable(880201L, 880101L);

        Assertions.assertThatThrownBy(() -> importJobService.commit(preview.jobId()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("已有正常应收数据，不能再导入期初应收");

        ImportJobResponse detail = importJobService.detail(preview.jobId());
        Assertions.assertThat(detail.status()).isEqualTo("FAILED");
        Assertions.assertThat(detail.errorMessage()).isEqualTo("已有正常应收数据，不能再导入期初应收");
    }

    @Test
    @WithErpUser(userId = IMPORT_USER_ID, authorities = {IMPORT_PERMISSION})
    void openingInventoryImportCommitsLotStock() {
        seedOpenPeriod();
        seedWarehouse(880301L, "IMP-WH-LOT");
        seedLotProduct(880401L, "IMP-PROD-LOT");
        String csv = "warehouse_code,product_code,location_code,qty_on_hand,amount_on_hand,opening_date,lot_no,production_date,expiry_date,serial_nos,remark\n"
                + "IMP-WH-LOT,IMP-PROD-LOT,MAIN,7.0000,70.00,2026-05-19,OPEN-LOT-A,2026-01-01,2026-12-31,,期初批次库存\n";

        ImportJobResponse preview = importJobService.preview("OPENING_INVENTORY", csvFile("opening-inventory-lot.csv", csv));
        Assertions.assertThat(preview.status()).isEqualTo("VALIDATED");
        Assertions.assertThat(preview.validRows()).isEqualTo(1);

        ImportJobResponse committed = importJobService.commit(preview.jobId());
        Assertions.assertThat(committed.status()).isEqualTo("COMMITTED");
        Assertions.assertThat(committed.committedRows()).isEqualTo(1);

        Map<String, Object> lot = jdbcTemplate.queryForMap("""
                select lot_no, production_date, expiry_date, qty_on_hand, amount_on_hand, location_id
                from inv_lot_balance
                where warehouse_id = ? and product_id = ? and lot_no = 'OPEN-LOT-A'
                """, 880301L, 880401L);
        Assertions.assertThat(String.valueOf(lot.get("LOT_NO") != null ? lot.get("LOT_NO") : lot.get("lot_no")))
                .isEqualTo("OPEN-LOT-A");
        Assertions.assertThat(dateValue(lot.get("PRODUCTION_DATE") != null ? lot.get("PRODUCTION_DATE") : lot.get("production_date")))
                .isEqualTo(LocalDate.of(2026, 1, 1));
        Assertions.assertThat(dateValue(lot.get("EXPIRY_DATE") != null ? lot.get("EXPIRY_DATE") : lot.get("expiry_date")))
                .isEqualTo(LocalDate.of(2026, 12, 31));
        Object qty = lot.get("QTY_ON_HAND") != null ? lot.get("QTY_ON_HAND") : lot.get("qty_on_hand");
        Object amount = lot.get("AMOUNT_ON_HAND") != null ? lot.get("AMOUNT_ON_HAND") : lot.get("amount_on_hand");
        Object locationId = lot.get("LOCATION_ID") != null ? lot.get("LOCATION_ID") : lot.get("location_id");
        Assertions.assertThat(new BigDecimal(qty.toString())).isEqualByComparingTo("7.0000");
        Assertions.assertThat(new BigDecimal(amount.toString())).isEqualByComparingTo("70.00");
        Assertions.assertThat(locationId).isNotNull();
        Assertions.assertThat(new BigDecimal(locationId.toString())).isEqualByComparingTo(String.valueOf(880301L + 500000000000000000L));
    }

    private void seedOpenPeriod() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 9, 0);
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (880001, 1, 1, 2026, '2026-05', '2026-05-01', '2026-05-31', 'OPEN',
                        ?, ?, ?, ?, 0)
                """, IMPORT_USER_ID, now, IMPORT_USER_ID, now);
    }

    private MockMultipartFile csvFile(String fileName, String content) {
        return new MockMultipartFile(
                "file",
                fileName,
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String productCsv(String productCode) {
        return "product_code,product_name,product_type,category_name,specification,unit_name,aux_unit_name,conversion_factor,barcode,purchase_price,sale_price,tax_rate,status,lot_controlled,shelf_life_controlled,inspection_required,serial_controlled,remark\n"
                + productCode + ",导入测试商品,STANDARD,导入分类,规格A,件,,,,10.00,18.00,13.00,ACTIVE,0,0,0,0,导入测试\n";
    }

    private int intColumn(Map<String, Object> row, String upperKey, String lowerKey) {
        Object value = row.get(upperKey) != null ? row.get(upperKey) : row.get(lowerKey);
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private void seedCustomer(long id, String customerCode) {
        jdbcTemplate.update("""
                insert into md_customer
                (id, company_id, account_book_id, customer_code, customer_name, contact_name, contact_phone,
                 settlement_method, credit_limit, address, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, '导入测试客户', '张三', '13800000000',
                        'MONTH_END', 0, '北京市', 'ACTIVE', 0, '导入测试', ?, ?, 0)
                """, id, customerCode, IMPORT_USER_ID, IMPORT_USER_ID);
    }

    private void seedWarehouse(long id, String warehouseCode) {
        jdbcTemplate.update("""
                insert into md_warehouse
                (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id,
                 address, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, '导入批次仓库', 1, 1,
                        '北京市', 'ACTIVE', 0, '导入批次测试', ?, ?, 0)
                """, id, warehouseCode, IMPORT_USER_ID, IMPORT_USER_ID);
        jdbcTemplate.update("""
                insert into md_location
                (id, company_id, account_book_id, warehouse_id, location_code, location_name,
                 is_default, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 'MAIN', '默认库位',
                        1, 'ACTIVE', 0, '导入测试默认库位', ?, ?, 0)
                """, id + 500000000000000000L, id, IMPORT_USER_ID, IMPORT_USER_ID);
    }

    private void seedLotProduct(long id, String productCode) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 lot_controlled, shelf_life_controlled, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, '导入批次商品', 'STANDARD', '导入分类',
                        '规格A', '件', 10.00, 18.00, 13.0000, 'ACTIVE', 0,
                        1, 1, '导入批次测试', ?, ?, 0)
                """, id, productCode, IMPORT_USER_ID, IMPORT_USER_ID);
    }

    private void seedNormalReceivable(long id, long customerId) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, 'IMP-AR-NORMAL', 'TEST_RECEIVABLE', ?, 'SRC-IMP-AR-NORMAL', 'INCREASE',
                        ?, '2026-05-19', ?, ?, 'UNSETTLED', 0, '正常业务应收测试',
                        ?, ?, 0)
                """, id, id, customerId, new BigDecimal("20.00"), BigDecimal.ZERO, IMPORT_USER_ID, IMPORT_USER_ID);
    }

    private LocalDate dateValue(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date value: " + value);
    }
}
