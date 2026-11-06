package com.tuowei.erp.inventory.mrp;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.inventory.mrp.service.MrpPlanService;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Two committed transactions in separate threads compete for the same physical shortage. */
@SpringBootTest
@ActiveProfiles("test")
@WithErpUser(authorities = {"inventory:mrp:run", "inventory:mrp:view", "inventory:mrp:convert", "purchase:order:create"})
class MrpPlanConcurrencyTest {
    private static final long PRODUCT=98817001, WAREHOUSE=98817002, SUPPLIER=98817003, RULE=98817004;
    @Autowired MrpPlanService mrp;
    @Autowired JdbcTemplate jdbc;

    @Test void concurrentConversionsOfDifferentRunsCreateOnlyOnePurchaseOrder() throws Exception {
        List<Long> runIds = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            jdbc.update("insert into md_product(id,company_id,account_book_id,product_code,product_name,product_type,category_name,unit_name,purchase_price,sale_price,tax_rate,status) values(?,1,1,'MRP-CONCURRENT','MRP concurrent product','GOODS','test','pcs',1,1,0,'ACTIVE')",PRODUCT);
            jdbc.update("insert into md_warehouse(id,company_id,account_book_id,warehouse_code,warehouse_name,dept_id,manager_user_id,status) values(?,1,1,'MRP-CONCURRENT-WH','MRP concurrent warehouse',1,1,'ACTIVE')",WAREHOUSE);
            jdbc.update("insert into md_supplier(id,company_id,account_book_id,supplier_code,supplier_name,settlement_method,status) values(?,1,1,'MRP-CONCURRENT-SP','MRP concurrent supplier','MONTH_END','ACTIVE')",SUPPLIER);
            jdbc.update("insert into inv_alert_rule(id,company_id,account_book_id,warehouse_id,product_id,min_qty,enabled) values(?,1,1,?,?,10,1)",RULE,WAREHOUSE,PRODUCT);
            MrpRunResponse first=mrp.run(), second=mrp.run(); runIds.add(first.id()); runIds.add(second.id());
            List<java.util.concurrent.Future<Object>> results = new ArrayList<>();
            for (MrpRunResponse run : List.of(first,second)) {
                Long lineId=run.purchaseLines().stream().filter(l -> l.productId()==PRODUCT).findFirst().orElseThrow().id();
                results.add(executor.submit(() -> {
                    var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
                    try {
                        if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start timed out");
                        return mrp.convertLine(run.id(),lineId,new MrpConvertLineRequest(SUPPLIER,null,null));
                    } catch (BusinessConflictException conflict) { return conflict; }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            start.countDown();
            var outcomes=List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(o -> o instanceof BusinessConflictException).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from pur_order where company_id=1 and account_book_id=1 and supplier_id=?",Long.class,SUPPLIER)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select sum(qty) from pur_order_line where company_id=1 and account_book_id=1 and product_id=?",java.math.BigDecimal.class,PRODUCT)).isEqualByComparingTo("10");
        } finally {
            start.countDown(); executor.shutdownNow(); executor.awaitTermination(30,TimeUnit.SECONDS);
            jdbc.update("delete from pur_order_line where company_id=1 and account_book_id=1 and product_id=?",PRODUCT);
            jdbc.update("delete from pur_order where company_id=1 and account_book_id=1 and supplier_id=?",SUPPLIER);
            for (Long runId:runIds) {
                jdbc.update("delete from inv_mrp_run_line where company_id=1 and account_book_id=1 and run_id=?",runId);
                jdbc.update("delete from inv_mrp_run where company_id=1 and account_book_id=1 and id=?",runId);
            }
            jdbc.update("delete from inv_alert_rule where id=?",RULE);
            jdbc.update("delete from md_product where id=?",PRODUCT);
            jdbc.update("delete from md_warehouse where id=?",WAREHOUSE);
            jdbc.update("delete from md_supplier where id=?",SUPPLIER);
        }
    }
}
