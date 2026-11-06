package com.tuowei.erp.inventory.adjust;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Real transactions: a failed posting request must roll back stock, vouchers and the linked count. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryAdjustmentApprovalIntegrationTest {
    private static final long COMPANY = 6979001L;
    private static final long BOOK = 1L;
    private static final long SUBMITTER = 6979101L;
    private static final long APPROVER = 6979102L;
    private static final long SECOND_APPROVER = 6979103L;
    private static final long WAREHOUSE = 6979201L;
    private static final long PRODUCT = 6979301L;
    private static final long CONFIG = 6979401L;
    private static final long NODE = 6979411L;
    private static final String DATE = "2035-11-06";
    private static final String BASE = "/api/inventory/adjustments";
    private static final String CHECKS = "/api/inventory/checks";
    private static final Set<String> PERMISSIONS = Set.of(
            "inventory:adjustment:view", "inventory:adjustment:create", "inventory:adjustment:update",
            "inventory:adjustment:submit", "inventory:adjustment:approve", "inventory:adjustment:post",
            "inventory:adjustment:cancel", "inventory:check:view", "inventory:check:create",
            "inventory:check:adjust", "inventory:check:cancel",
            "workflow:view", "workflow:approve", "workflow:reject", "workflow:withdraw");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        for (long id : List.of(SUBMITTER, APPROVER, SECOND_APPROVER)) {
            jdbc.update("""
                    insert into sys_user (id, company_id, account_book_id, username, password, real_name,
                                          employee_no, dept_id, post_id, status, deleted_flag)
                    values (?, ?, ?, ?, 'unused-test-password', 'Inventory approval test', ?, 1, 1, 'ACTIVE', 0)
                    """, id, COMPANY, BOOK, "inventory_approval_" + id, "IA-" + id);
        }
        jdbc.update("""
                insert into md_warehouse (id, company_id, account_book_id, warehouse_code, warehouse_name,
                                          dept_id, manager_user_id, status, deleted_flag)
                values (?, ?, ?, 'IA-APP-WH', 'Approval warehouse', 1, 1, 'ACTIVE', 0)
                """, WAREHOUSE, COMPANY, BOOK);
        jdbc.update("""
                insert into md_location (id, company_id, account_book_id, warehouse_id, location_code,
                                         location_name, is_default, status, deleted_flag)
                values (6979202, ?, ?, ?, 'MAIN', 'Default location', 1, 'ACTIVE', 0)
                """, COMPANY, BOOK, WAREHOUSE);
        jdbc.update("""
                insert into md_product (id, company_id, account_book_id, product_code, product_name,
                                        product_type, category_name, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag)
                values (?, ?, ?, 'IA-APP-PRODUCT', 'Approval product', 'STANDARD', 'TEST', 'pcs', 10, 20, 0, 'ACTIVE', 0)
                """, PRODUCT, COMPANY, BOOK);
        jdbc.update("""
                insert into fin_account_period (id, company_id, account_book_id, period_year, period_month,
                                                start_date, end_date, status)
                values (6979501, ?, ?, 2035, '2035-11', '2035-11-01', '2035-11-30', 'OPEN')
                """, COMPANY, BOOK);
        jdbc.update("""
                insert into sys_sequence_rule (id, company_id, account_book_id, biz_type, prefix,
                                               date_pattern, seq_length, current_value, status)
                values (6979601, ?, ?, 'INVENTORY_ADJUSTMENT', 'IA-APP-', 'yyyyMMdd', 6, 0, 'ACTIVE'),
                       (6979602, ?, ?, 'INVENTORY_STOCK_CHECK', 'IC-APP-', 'yyyyMMdd', 6, 0, 'ACTIVE')
                """, COMPANY, BOOK, COMPANY, BOOK);
        jdbc.update("""
                insert into wf_approval_config (id, company_id, account_book_id, business_type, config_name,
                                                status, deleted_flag, task_timeout_hours)
                values (?, ?, ?, 'INVENTORY_ADJUSTMENT', 'Inventory approval test', 'ACTIVE', 0, 24)
                """, CONFIG, COMPANY, BOOK);
        seedNode(NODE, 1, APPROVER);
    }

    @AfterEach
    void cleanup() {
        // All rows belong to this test company; never clear another test's or a default tenant's data.
        for (String table : List.of("sys_notification_recipient", "sys_notification", "wf_approval_record",
                "wf_approval_task", "wf_approval_instance", "wf_approval_node_approver", "wf_approval_node",
                "wf_approval_config", "fin_voucher_entry", "fin_voucher", "fin_account_subject",
                "inv_txn", "inv_lot_balance", "inv_balance", "inv_adjustment_line", "inv_adjustment",
                "inv_stock_check_line", "inv_stock_check", "md_location", "md_warehouse", "md_product",
                "fin_account_period", "sys_sequence_counter", "sys_sequence_rule", "sys_operation_log", "sys_user")) {
            jdbc.update("delete from " + table + " where company_id = ?", COMPANY);
        }
    }

    @Test
    void submissionAndApprovalDoNotPostAndDuplicatePostingDoesNotDuplicateFacts() throws Exception {
        long id = createAdjustment();
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "审批通过");
        submit(id);
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "审批通过");
        assertNoPosting();
        approve(id);
        assertThat(adjustmentStatus(id)).isEqualTo("APPROVED");
        assertNoPosting();
        ok(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null);
        assertThat(adjustmentStatus(id)).isEqualTo("POSTED");
        assertThat(stockQuantity()).isEqualByComparingTo("5");
        assertThat(count("fin_voucher")).isEqualTo(1);
        assertThat(count("fin_voucher_entry")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(debit_amount - credit_amount) from fin_voucher_entry where company_id = ?",
                BigDecimal.class, COMPANY)).isEqualByComparingTo("0");
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "审批通过");
        assertThat(count("inv_txn")).isEqualTo(1);
        assertThat(count("fin_voucher")).isEqualTo(1);
    }

    @Test
    void selfApprovalAndEditsOrCancellationDuringApprovalAreRejected() throws Exception {
        long id = createAdjustment();
        submit(id);
        fail(post(BASE + "/" + id + "/approve"), actor(SUBMITTER), null, 400, "自己的单据");
        fail(post(taskPath(id, "approve")), actor(SUBMITTER), null, 400, "自己的单据");
        fail(put(BASE + "/" + id), actor(SUBMITTER), adjustmentPayload(8), 400, "可以编辑");
        fail(post(BASE + "/" + id + "/cancel"), actor(SUBMITTER), null, 400, "撤回或驳回");
        fail(post(BASE + "/" + id + "/submit"), actor(SUBMITTER), null, 400, "可以提交审批");
        assertThat(count("wf_approval_instance")).isEqualTo(1);
        assertThat(adjustmentStatus(id)).isEqualTo("SUBMITTED");
        assertNoPosting();
    }

    @Test
    void genericWorkflowPermissionCannotBypassInventoryApprovalOrSubmissionPermission() throws Exception {
        long id = createAdjustment();
        ErpPrincipal withoutSubmit = withoutPermission(SUBMITTER, "inventory:adjustment:submit");
        fail(post(BASE + "/" + id + "/submit"), withoutSubmit, null, 403, "");
        submit(id);
        ErpPrincipal workflowOnly = withoutPermission(APPROVER, "inventory:adjustment:approve");
        for (String action : List.of("approve", "reject")) {
            fail(post(BASE + "/" + id + "/" + action), workflowOnly, Map.of("comment", "review"), 403, "");
            fail(post(taskPath(id, action)), workflowOnly, Map.of("comment", "review"), 403, "");
        }
        fail(post("/api/workflow/INVENTORY_ADJUSTMENT/" + id + "/withdraw"), withoutSubmit, null, 403, "");
        assertThat(adjustmentStatus(id)).isEqualTo("SUBMITTED");
        assertThat(count("wf_approval_record")).isEqualTo(1);
        assertNoPosting();
    }

    @Test
    void companyAccountBookAndWarehouseScopeApplyToBusinessAndGenericTaskActions() throws Exception {
        long id = createAdjustment();
        submit(id);
        for (ErpPrincipal outsider : List.of(
                principal(APPROVER, COMPANY + 1, BOOK, PERMISSIONS, DataScopeSnapshot.all()),
                principal(APPROVER, COMPANY, BOOK + 1, PERMISSIONS, DataScopeSnapshot.all()))) {
            fail(get(BASE + "/" + id + "/approval-info"), outsider, null, 400, "不存在");
            fail(post(BASE + "/" + id + "/approve"), outsider, null, 400, "不存在");
            fail(post(taskPath(id, "approve")), outsider, null, 400, "不存在");
        }
        ErpPrincipal otherWarehouse = principal(APPROVER, COMPANY, BOOK, PERMISSIONS,
                new DataScopeSnapshot(false, false, false, false, Set.of(WAREHOUSE + 10)));
        fail(get(BASE + "/" + id + "/approval-info"), otherWarehouse, null, 403, "");
        fail(post(BASE + "/" + id + "/approve"), otherWarehouse, null, 403, "");
        fail(post(taskPath(id, "approve")), otherWarehouse, null, 403, "");
        assertThat(adjustmentStatus(id)).isEqualTo("SUBMITTED");
        assertNoPosting();
    }

    @Test
    void rejectionRequiresReasonAndAllowsCorrectionResubmissionWithCompleteHistory() throws Exception {
        long id = createAdjustment();
        submit(id);
        fail(post(BASE + "/" + id + "/reject"), actor(APPROVER), Map.of("comment", " "), 400, "驳回原因");
        ok(post(taskPath(id, "reject")), actor(APPROVER), Map.of("comment", "盘点数量需复核"));
        assertThat(adjustmentStatus(id)).isEqualTo("REJECTED");
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "审批通过");
        JsonNode edited = ok(put(BASE + "/" + id), actor(SUBMITTER), adjustmentPayload(8));
        assertThat(edited.path("lines")).hasSize(1);
        assertThat(edited.path("lines").get(0).path("qty").decimalValue()).isEqualByComparingTo("8");
        submit(id);
        approve(id);
        JsonNode history = ok(get(BASE + "/" + id + "/approval-info"), actor(SUBMITTER), null);
        assertThat(actions(history)).containsExactly("SUBMIT", "REJECT", "SUBMIT", "APPROVE");
        assertThat(history.path("records").get(1).path("comment").asText()).isEqualTo("盘点数量需复核");
        assertNoPosting();
        ok(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null);
        assertThat(stockQuantity()).isEqualByComparingTo("8");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void withdrawalThroughEitherEndpointRestoresDraftAndClosesTasks(boolean generic) throws Exception {
        long id = createAdjustment();
        submit(id);
        String path = generic ? "/api/workflow/INVENTORY_ADJUSTMENT/" + id + "/withdraw" : BASE + "/" + id + "/withdraw";
        fail(post(path), actor(APPROVER), null, 400, "只有提交人");
        ok(post(path), actor(SUBMITTER), Map.of("comment", "更正后重提"));
        assertThat(adjustmentStatus(id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select status from wf_approval_instance where company_id = ?", String.class, COMPANY))
                .isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select status from wf_approval_task where company_id = ?", String.class, COMPANY))
                .isEqualTo("CANCELLED");
        ok(put(BASE + "/" + id), actor(SUBMITTER), adjustmentPayload(7));
        submit(id);
        assertThat(actions(ok(get(BASE + "/" + id + "/approval-info"), actor(SUBMITTER), null)))
                .containsExactly("SUBMIT", "WITHDRAW", "SUBMIT");
        assertNoPosting();
    }

    @Test
    void everyApprovalStageMustCompleteBeforePostingAndProcessedWorkflowCannotWithdraw() throws Exception {
        seedNode(NODE + 1, 2, SECOND_APPROVER);
        long id = createAdjustment();
        submit(id);
        approve(id);
        assertThat(adjustmentStatus(id)).isEqualTo("SUBMITTED");
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "审批通过");
        fail(post(BASE + "/" + id + "/withdraw"), actor(SUBMITTER), null, 400, "已被处理");
        ok(post(taskPath(id, "approve")), actor(SECOND_APPROVER), Map.of("comment", "终审"));
        assertThat(adjustmentStatus(id)).isEqualTo("APPROVED");
        assertNoPosting();
    }

    @Test
    void allApproversMustActBeforeAnAllModeNodeCompletes() throws Exception {
        jdbc.update("update wf_approval_node set approval_mode = 'ALL' where id = ?", NODE);
        jdbc.update("""
                insert into wf_approval_node_approver (id, company_id, node_id, approver_type, approver_id)
                values (6979499, ?, ?, 'USER', ?)
                """, COMPANY, NODE, SECOND_APPROVER);
        long id = createAdjustment();
        submit(id);
        approve(id);
        assertThat(adjustmentStatus(id)).isEqualTo("SUBMITTED");
        fail(post(BASE + "/" + id + "/approve"), actor(APPROVER), null, 400, "");
        ok(post(BASE + "/" + id + "/approve"), actor(SECOND_APPROVER), null);
        assertThat(adjustmentStatus(id)).isEqualTo("APPROVED");
        assertNoPosting();
    }

    @Test
    void stockCountRemainsPendingUntilItsApprovedAdjustmentPosts() throws Exception {
        JsonNode check = createCount();
        long checkId = check.path("id").asLong();
        JsonNode generated = ok(post(CHECKS + "/" + checkId + "/adjust"), actor(SUBMITTER), null);
        long id = generated.path("generatedAdjustmentId").asLong();
        assertThat(generated.path("status").asText()).isEqualTo("ADJUSTMENT_PENDING");
        JsonNode adjustment = ok(get(BASE + "/" + id), actor(SUBMITTER), null);
        assertThat(adjustment.path("sourceId").asLong()).isEqualTo(checkId);
        assertThat(adjustment.path("sourceNo").asText()).isEqualTo(check.path("checkNo").asText());
        assertThat(adjustment.path("sourceType").asText()).isEqualTo("INVENTORY_CHECK");
        assertThat(adjustmentStatus(id)).isEqualTo("DRAFT");
        fail(put(BASE + "/" + id), actor(SUBMITTER), adjustmentPayload(8), 400, "不能直接编辑");
        fail(post(CHECKS + "/" + checkId + "/cancel"), actor(SUBMITTER), null, 400, "不能取消");
        fail(post(CHECKS + "/" + checkId + "/adjust"), actor(SUBMITTER), null, 400, "");
        fail(put(CHECKS + "/" + checkId), actor(SUBMITTER), countUpdate(check, 9), 400, "可以编辑");
        assertNoPosting();
        submit(id);
        approve(id);
        assertNoPosting();
        assertThat(checkStatus(checkId)).isEqualTo("ADJUSTMENT_PENDING");
        ok(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null);
        assertThat(checkStatus(checkId)).isEqualTo("ADJUSTED");
        assertThat(stockQuantity()).isEqualByComparingTo("5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "REJECTED", "APPROVED"})
    void cancellingLinkedAdjustmentClearsReferenceAndAllowsCountCorrectionAndRegeneration(String state) throws Exception {
        JsonNode check = createCount();
        long checkId = check.path("id").asLong();
        long id = ok(post(CHECKS + "/" + checkId + "/adjust"), actor(SUBMITTER), null).path("generatedAdjustmentId").asLong();
        if (!state.equals("DRAFT")) {
            submit(id);
            if (state.equals("APPROVED")) approve(id);
            else ok(post(BASE + "/" + id + "/reject"), actor(APPROVER), Map.of("comment", "复盘"));
        }
        ok(post(BASE + "/" + id + "/cancel"), actor(SUBMITTER), null);
        JsonNode restored = ok(get(CHECKS + "/" + checkId), actor(SUBMITTER), null);
        assertThat(restored.path("status").asText()).isEqualTo("COUNTED");
        assertThat(restored.path("generatedAdjustmentId").isNull()).isTrue();
        assertThat(restored.path("generatedAdjustmentNo").isNull()).isTrue();
        ok(put(CHECKS + "/" + checkId), actor(SUBMITTER), countUpdate(check, 9));
        long newId = ok(post(CHECKS + "/" + checkId + "/adjust"), actor(SUBMITTER), null)
                .path("generatedAdjustmentId").asLong();
        assertThat(newId).isNotEqualTo(id);
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "审批通过");
        assertNoPosting();
        submit(newId);
        approve(newId);
        ok(post(BASE + "/" + newId + "/post"), actor(SUBMITTER), null);
        assertThat(stockQuantity()).isEqualByComparingTo("9");
        assertThat(checkStatus(checkId)).isEqualTo("ADJUSTED");
    }

    @Test
    void sourceMismatchRollsBackStockVoucherAndDocumentPosting() throws Exception {
        long checkId = createCount().path("id").asLong();
        long id = ok(post(CHECKS + "/" + checkId + "/adjust"), actor(SUBMITTER), null).path("generatedAdjustmentId").asLong();
        submit(id);
        approve(id);
        jdbc.update("update inv_stock_check set generated_adjustment_id = ? where id = ?", id + 1, checkId);
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 400, "关联盘点单");
        assertThat(adjustmentStatus(id)).isEqualTo("APPROVED");
        assertThat(checkStatus(checkId)).isEqualTo("ADJUSTMENT_PENDING");
        assertNoPosting();
    }

    @Test
    void approvalDoesNotBypassLockedAccountingPeriod() throws Exception {
        long id = createAdjustment();
        submit(id);
        approve(id);
        jdbc.update("update fin_account_period set status = 'LOCKED' where company_id = ?", COMPANY);
        fail(post(BASE + "/" + id + "/post"), actor(SUBMITTER), null, 409, "锁定");
        assertThat(adjustmentStatus(id)).isEqualTo("APPROVED");
        assertNoPosting();
    }

    private void seedNode(long nodeId, int order, long approverId) {
        jdbc.update("""
                insert into wf_approval_node (id, company_id, config_id, node_name, node_order, approval_mode, status)
                values (?, ?, ?, ?, ?, 'ANY', 'ACTIVE')
                """, nodeId, COMPANY, CONFIG, "Review " + order, order);
        jdbc.update("""
                insert into wf_approval_node_approver (id, company_id, node_id, approver_type, approver_id)
                values (?, ?, ?, 'USER', ?)
                """, nodeId + 50, COMPANY, nodeId, approverId);
    }

    private long createAdjustment() throws Exception {
        return ok(post(BASE), actor(SUBMITTER), adjustmentPayload(5)).path("id").asLong();
    }

    private Map<String, Object> adjustmentPayload(int qty) {
        return Map.of("warehouseId", WAREHOUSE, "adjustmentDate", DATE, "remark", "approval regression",
                "lines", List.of(Map.of("productId", PRODUCT, "direction", "IN", "qty", qty, "unitCost", 10, "reason", "盘盈")));
    }

    private JsonNode createCount() throws Exception {
        return ok(post(CHECKS), actor(SUBMITTER), Map.of("warehouseId", WAREHOUSE, "checkDate", DATE,
                "lines", List.of(Map.of("productId", PRODUCT, "actualQty", 5, "unitCost", 10))));
    }

    private Map<String, Object> countUpdate(JsonNode check, int qty) {
        return Map.of("items", List.of(Map.of("id", check.path("lines").get(0).path("id").asLong(),
                "productId", PRODUCT, "actualQty", qty, "unitCost", 10)));
    }

    private void submit(long id) throws Exception {
        ok(post(BASE + "/" + id + "/submit"), actor(SUBMITTER), Map.of("comment", "提交复核"));
    }

    private void approve(long id) throws Exception {
        ok(post(BASE + "/" + id + "/approve"), actor(APPROVER), Map.of("comment", "核对通过"));
    }

    private String taskPath(long id, String action) {
        Long taskId = jdbc.queryForObject("""
                select id from wf_approval_task where company_id = ? and business_type = 'INVENTORY_ADJUSTMENT'
                and business_id = ? and status = 'PENDING' order by created_time desc, id desc limit 1
                """, Long.class, COMPANY, id);
        return "/api/workflow/tasks/" + taskId + "/" + action;
    }

    private String adjustmentStatus(long id) {
        return jdbc.queryForObject("select status from inv_adjustment where id = ?", String.class, id);
    }

    private String checkStatus(long id) {
        return jdbc.queryForObject("select status from inv_stock_check where id = ?", String.class, id);
    }

    private BigDecimal stockQuantity() {
        return jdbc.queryForObject("select coalesce(sum(qty_on_hand), 0) from inv_balance where company_id = ?", BigDecimal.class, COMPANY);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where company_id = ?", Integer.class, COMPANY);
    }

    private void assertNoPosting() {
        assertThat(count("inv_txn")).isZero();
        assertThat(stockQuantity()).isEqualByComparingTo("0");
        assertThat(count("fin_voucher")).isZero();
        assertThat(count("fin_voucher_entry")).isZero();
    }

    private List<String> actions(JsonNode history) {
        return StreamSupport.stream(history.path("records").spliterator(), false).map(r -> r.path("action").asText()).toList();
    }

    private ErpPrincipal actor(long id) {
        return principal(id, COMPANY, BOOK, PERMISSIONS, DataScopeSnapshot.all());
    }

    private ErpPrincipal withoutPermission(long id, String permission) {
        Set<String> reduced = new HashSet<>(PERMISSIONS);
        reduced.remove(permission);
        return principal(id, COMPANY, BOOK, reduced, DataScopeSnapshot.all());
    }

    private ErpPrincipal principal(long id, long company, long book, Set<String> permissions, DataScopeSnapshot scope) {
        return new ErpPrincipal(id, company, book, 1L, 1L, "inventory_approval_" + id,
                "Inventory approval test", "unused", permissions, scope);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, ErpPrincipal principal, Object body) throws Exception {
        MvcResult result = perform(request, principal, body);
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(content).isEqualTo(200);
        JsonNode response = json.readTree(content);
        assertThat(response.path("code").asText()).as(content).isEqualTo("0");
        return response.path("data");
    }

    private void fail(MockHttpServletRequestBuilder request, ErpPrincipal principal, Object body,
                      int status, String message) throws Exception {
        MvcResult result = perform(request, principal, body);
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(content).isEqualTo(status);
        assertThat(json.readTree(content).path("message").asText()).contains(message);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, ErpPrincipal principal, Object body) throws Exception {
        request.with(user(principal));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(request).andReturn();
    }
}
