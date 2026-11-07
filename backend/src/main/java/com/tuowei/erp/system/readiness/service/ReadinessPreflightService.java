package com.tuowei.erp.system.readiness.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@NativeSqlTenantScoped("Preflight contains a small number of global readiness checks; tenant-owned checks derive company_id/account_book_id from AuditMetadata and pass them as JdbcTemplate parameters.")
public class ReadinessPreflightService {

    private static final String PASS = "PASS";
    private static final String WARN = "WARN";
    private static final String FAIL = "FAIL";

    private final AuditMetadataFactory auditMetadataFactory;
    private final JdbcTemplate jdbcTemplate;

    public ReadinessPreflightService(AuditMetadataFactory auditMetadataFactory, JdbcTemplate jdbcTemplate) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public ReadinessPreflightResponse preflight() {
        AuditMetadata audit = auditMetadataFactory.current();
        List<ReadinessPreflightItemResponse> items = List.of(
                flywayMigrationStatus(),
                logTenantColumns(),
                negativeInventory(audit),
                voucherWithoutEntry(audit),
                unbalancedVoucher(audit),
                payableSettlementRange(audit),
                receivableSettlementRange(audit),
                duplicateProductCode(audit),
                duplicateCustomerCode(audit),
                duplicateSupplierCode(audit),
                duplicateWarehouseCode(audit)
        );
        return new ReadinessPreflightResponse(resolveOverallStatus(items), audit.now(), items);
    }

    private ReadinessPreflightItemResponse flywayMigrationStatus() {
        return item(
                "FLYWAY_MIGRATION_STATUS",
                "P0",
                "Flyway 不存在失败迁移",
                count("""
                        select count(*)
                        from flyway_schema_history
                        where success = false
                        """),
                sample("""
                        select concat(version, ':', description)
                        from flyway_schema_history
                        where success = false
                        order by installed_rank desc
                        limit 5
                        """)
        );
    }

    private ReadinessPreflightItemResponse logTenantColumns() {
        long issueCount = count("""
                select sum(issue_count)
                from (
                    select count(*) as issue_count from sys_login_log where company_id is null or account_book_id is null
                    union all
                    select count(*) as issue_count from sys_operation_log where company_id is null or account_book_id is null
                    union all
                    select count(*) as issue_count from sys_audit_log where company_id is null or account_book_id is null
                ) t
                """);
        List<String> samples = new ArrayList<>();
        addLogSample(samples, "sys_login_log", "id", "login_time");
        addLogSample(samples, "sys_operation_log", "id", "operation_time");
        addLogSample(samples, "sys_audit_log", "id", "audit_time");
        return item("LOG_TENANT_COLUMNS", "P0", "系统日志租户字段已回填", issueCount, samples);
    }

    private ReadinessPreflightItemResponse negativeInventory(AuditMetadata audit) {
        return item(
                "NEGATIVE_INVENTORY",
                "P0",
                "库存余额不存在负数",
                count("""
                        select sum(issue_count)
                        from (
                            select count(*) as issue_count
                            from inv_balance
                            where company_id = ? and account_book_id = ? and qty_on_hand < 0
                            union all
                            select count(*) as issue_count
                            from inv_lot_balance
                            where company_id = ? and account_book_id = ? and qty_on_hand < 0
                        ) t
                        """, audit.companyId(), audit.accountBookId(), audit.companyId(), audit.accountBookId()),
                sample("""
                        select concat('inv_balance#', id, ' warehouse=', warehouse_id, ' product=', product_id, ' qty=', qty_on_hand)
                        from inv_balance
                        where company_id = ? and account_book_id = ? and qty_on_hand < 0
                        union all
                        select concat('inv_lot_balance#', id, ' warehouse=', warehouse_id, ' product=', product_id, ' lot=', lot_no, ' qty=', qty_on_hand)
                        from inv_lot_balance
                        where company_id = ? and account_book_id = ? and qty_on_hand < 0
                        limit 5
                        """, audit.companyId(), audit.accountBookId(), audit.companyId(), audit.accountBookId())
        );
    }

    private ReadinessPreflightItemResponse voucherWithoutEntry(AuditMetadata audit) {
        return item(
                "VOUCHER_WITHOUT_ENTRY",
                "P0",
                "已过账凭证均存在分录",
                count("""
                        select count(*)
                        from fin_voucher v
                        where v.company_id = ?
                          and v.account_book_id = ?
                          and v.deleted_flag = 0
                          and v.status = 'POSTED'
                          and not exists (
                              select 1 from fin_voucher_entry e
                              where e.voucher_id = v.id
                                and e.company_id = v.company_id
                                and e.account_book_id = v.account_book_id
                          )
                        """, audit.companyId(), audit.accountBookId()),
                sample("""
                        select concat('fin_voucher#', v.id, ' no=', v.voucher_no)
                        from fin_voucher v
                        where v.company_id = ?
                          and v.account_book_id = ?
                          and v.deleted_flag = 0
                          and v.status = 'POSTED'
                          and not exists (
                              select 1 from fin_voucher_entry e
                              where e.voucher_id = v.id
                                and e.company_id = v.company_id
                                and e.account_book_id = v.account_book_id
                          )
                        order by v.id
                        limit 5
                        """, audit.companyId(), audit.accountBookId())
        );
    }

    private ReadinessPreflightItemResponse unbalancedVoucher(AuditMetadata audit) {
        return item(
                "UNBALANCED_VOUCHER",
                "P0",
                "凭证分录借贷平衡",
                count("""
                        select count(*)
                        from (
                            select v.id
                            from fin_voucher v
                            join fin_voucher_entry e on e.voucher_id = v.id
                            where v.company_id = ?
                              and v.account_book_id = ?
                              and v.deleted_flag = 0
                              and v.status = 'POSTED'
                            group by v.id
                            having sum(e.debit_amount) <> sum(e.credit_amount)
                        ) t
                        """, audit.companyId(), audit.accountBookId()),
                sample("""
                        select concat('fin_voucher#', id, ' debit=', debit_total, ' credit=', credit_total)
                        from (
                            select v.id, sum(e.debit_amount) as debit_total, sum(e.credit_amount) as credit_total
                            from fin_voucher v
                            join fin_voucher_entry e on e.voucher_id = v.id
                            where v.company_id = ?
                              and v.account_book_id = ?
                              and v.deleted_flag = 0
                              and v.status = 'POSTED'
                            group by v.id
                            having sum(e.debit_amount) <> sum(e.credit_amount)
                        ) t
                        limit 5
                        """, audit.companyId(), audit.accountBookId())
        );
    }

    private ReadinessPreflightItemResponse payableSettlementRange(AuditMetadata audit) {
        return settlementRange(
                "PAYABLE_SETTLEMENT_RANGE",
                "应付核销金额未越界",
                "fin_payable",
                "payable_no",
                audit
        );
    }

    private ReadinessPreflightItemResponse receivableSettlementRange(AuditMetadata audit) {
        return settlementRange(
                "RECEIVABLE_SETTLEMENT_RANGE",
                "应收核销金额未越界",
                "fin_receivable",
                "receivable_no",
                audit
        );
    }

    private ReadinessPreflightItemResponse settlementRange(
            String code,
            String summary,
            String tableName,
            String noColumn,
            AuditMetadata audit
    ) {
        return item(
                code,
                "P0",
                summary,
                count("""
                        select count(*)
                        from %s
                        where company_id = ?
                          and account_book_id = ?
                          and deleted_flag = 0
                          and (settled_amount < 0 or settled_amount > original_amount)
                        """.formatted(tableName), audit.companyId(), audit.accountBookId()),
                sample("""
                        select concat('%s#', id, ' no=', %s, ' original=', original_amount, ' settled=', settled_amount)
                        from %s
                        where company_id = ?
                          and account_book_id = ?
                          and deleted_flag = 0
                          and (settled_amount < 0 or settled_amount > original_amount)
                        order by id
                        limit 5
                        """.formatted(tableName, noColumn, tableName), audit.companyId(), audit.accountBookId())
        );
    }

    private ReadinessPreflightItemResponse duplicateProductCode(AuditMetadata audit) {
        return duplicateMasterData("DUPLICATE_PRODUCT_CODE", "商品编码按租户不重复", "md_product", "product_code", audit);
    }

    private ReadinessPreflightItemResponse duplicateCustomerCode(AuditMetadata audit) {
        return duplicateMasterData("DUPLICATE_CUSTOMER_CODE", "客户编码按租户不重复", "md_customer", "customer_code", audit);
    }

    private ReadinessPreflightItemResponse duplicateSupplierCode(AuditMetadata audit) {
        return duplicateMasterData("DUPLICATE_SUPPLIER_CODE", "供应商编码按租户不重复", "md_supplier", "supplier_code", audit);
    }

    private ReadinessPreflightItemResponse duplicateWarehouseCode(AuditMetadata audit) {
        return duplicateMasterData("DUPLICATE_WAREHOUSE_CODE", "仓库编码按租户不重复", "md_warehouse", "warehouse_code", audit);
    }

    private ReadinessPreflightItemResponse duplicateMasterData(
            String code,
            String summary,
            String tableName,
            String codeColumn,
            AuditMetadata audit
    ) {
        return item(
                code,
                "P1",
                summary,
                count("""
                        select count(*)
                        from (
                            select %s
                            from %s
                            where company_id = ?
                              and account_book_id = ?
                              and deleted_flag = 0
                            group by %s
                            having count(*) > 1
                        ) t
                        """.formatted(codeColumn, tableName, codeColumn), audit.companyId(), audit.accountBookId()),
                sample("""
                        select concat('%s ', %s, ' count=', count(*))
                        from %s
                        where company_id = ?
                          and account_book_id = ?
                          and deleted_flag = 0
                        group by %s
                        having count(*) > 1
                        limit 5
                        """.formatted(tableName, codeColumn, tableName, codeColumn), audit.companyId(), audit.accountBookId())
        );
    }

    private void addLogSample(List<String> samples, String tableName, String idColumn, String timeColumn) {
        if (samples.size() >= 5) {
            return;
        }
        samples.addAll(sample("""
                select concat('%s#', %s, ' time=', %s)
                from %s
                where company_id is null or account_book_id is null
                order by %s desc
                limit ?
                """.formatted(tableName, idColumn, timeColumn, tableName, timeColumn), 5 - samples.size()));
    }

    private ReadinessPreflightItemResponse item(
            String code,
            String severity,
            String summary,
            long issueCount,
            List<String> sample
    ) {
        return new ReadinessPreflightItemResponse(
                code,
                issueCount == 0 ? PASS : FAIL,
                severity,
                summary,
                issueCount,
                sample == null ? List.of() : sample
        );
    }

    private long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }

    private List<String> sample(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), args);
    }

    private String resolveOverallStatus(List<ReadinessPreflightItemResponse> items) {
        boolean hasFailure = false;
        boolean hasWarning = false;
        for (ReadinessPreflightItemResponse item : items) {
            if (!PASS.equals(item.status())) {
                if ("P0".equals(item.severity())) {
                    hasFailure = true;
                } else {
                    hasWarning = true;
                }
            }
        }
        if (hasFailure) {
            return FAIL;
        }
        return hasWarning ? WARN : PASS;
    }
}
