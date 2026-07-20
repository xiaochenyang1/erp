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
}
