package com.tuowei.erp.issue.rule.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExceptionRuleMapper extends BaseMapper<ExceptionRuleEntity> {

    /**
     * Finds every tenant that has exception automation work.  This query is
     * intentionally global because the scheduler has no request principal;
     * the returned scope is installed before any tenant-owned mapper call.
     */
    @NativeSqlTenantScoped("scheduler discovers exception-rule and open-ticket scopes globally; each scope is installed before tenant-owned work")
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            select company_id, account_book_id
            from biz_exception_rule
            where company_id is not null
              and account_book_id is not null
              and deleted_flag = 0
            union
            select company_id, account_book_id
            from biz_exception_ticket
            where company_id is not null
              and account_book_id is not null
              and deleted_flag = 0
              and status in ('OPEN', 'PROCESSING')
            order by company_id, account_book_id
            """)
    List<ExceptionRuleEntity> selectTenantScopesForScheduler();

    @NativeSqlTenantScoped("scheduler intentionally selects due rules across tenants; each rule carries company_id/account_book_id and scan work uses schedulerAudit(rule, now)")
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            select *
            from biz_exception_rule
            where deleted_flag = 0
              and enabled = 1
              and (next_scan_time is null or next_scan_time <= #{now})
            order by next_scan_time asc, id asc
            """)
    List<ExceptionRuleEntity> selectDueRulesForScheduler(@Param("now") LocalDateTime now);

    /**
     * Selects due rules for one already-installed tenant scope.  Keeping the
     * scope predicates in SQL protects the call even if a caller has an
     * incomplete TenantLine context (TenantLine only filters company_id).
     */
    @NativeSqlTenantScoped("scheduler scans due rules with explicit company_id and account_book_id predicates")
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            select *
            from biz_exception_rule
            where company_id = #{companyId}
              and account_book_id = #{accountBookId}
              and deleted_flag = 0
              and enabled = 1
              and (next_scan_time is null or next_scan_time <= #{now})
            order by next_scan_time asc, id asc
            """)
    List<ExceptionRuleEntity> selectDueRulesForSchedulerScope(
            @Param("now") LocalDateTime now,
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId
    );
}
