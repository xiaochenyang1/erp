package com.tuowei.erp.system.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.system.observability.web.BusinessHealthCheckResponse;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.model.ReadinessItemEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObservabilityBusinessHealthService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_WARN = "WARN";

    private final ReadinessItemMapper readinessItemMapper;
    private final ImportJobMapper importJobMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ObservabilityBusinessHealthService(
            ReadinessItemMapper readinessItemMapper,
            ImportJobMapper importJobMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.readinessItemMapper = readinessItemMapper;
        this.importJobMapper = importJobMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    public BusinessHealthResponse current() {
        AuditMetadata audit = auditMetadataFactory.current();
        List<BusinessHealthCheckResponse> checks = new ArrayList<>();
        checks.add(readinessCheck(audit));
        checks.add(importCheck(audit));
        checks.add(negativeInventoryCheck(audit));
        checks.add(openPeriodCheck(audit));
        String overallStatus = checks.stream().anyMatch(check -> STATUS_WARN.equals(check.status()))
                ? STATUS_WARN
                : STATUS_UP;
        return new BusinessHealthResponse(overallStatus, audit.now(), List.copyOf(checks));
    }

    private BusinessHealthCheckResponse readinessCheck(AuditMetadata audit) {
        long count = readinessItemMapper.selectCount(new LambdaQueryWrapper<ReadinessItemEntity>()
                .eq(ReadinessItemEntity::getCompanyId, audit.companyId())
                .eq(ReadinessItemEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessItemEntity::getDeletedFlag, 0)
                .in(ReadinessItemEntity::getPriority, List.of("P0", "P1"))
                .ne(ReadinessItemEntity::getStatus, "PASSED"));
        return thresholdZero("READINESS_UNPASSED_P0_P1", "未通过 P0/P1 验收项", count,
                "存在未通过或未执行的 P0/P1 readiness 项");
    }

    private BusinessHealthCheckResponse importCheck(AuditMetadata audit) {
        long count = importJobMapper.selectCount(new LambdaQueryWrapper<ImportJobEntity>()
                .eq(ImportJobEntity::getCompanyId, audit.companyId())
                .eq(ImportJobEntity::getAccountBookId, audit.accountBookId())
                .eq(ImportJobEntity::getStatus, "FAILED")
                .ge(ImportJobEntity::getCreatedTime, audit.now().minusHours(24)));
        return thresholdZero("IMPORT_FAILED_RECENT", "最近 24 小时失败导入任务", count,
                "最近 24 小时存在失败导入任务");
    }

    private BusinessHealthCheckResponse negativeInventoryCheck(AuditMetadata audit) {
        long count = inventoryBalanceMapper.selectCount(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, audit.companyId())
                .eq(InventoryBalanceEntity::getAccountBookId, audit.accountBookId())
                .lt(InventoryBalanceEntity::getQtyOnHand, BigDecimal.ZERO));
        return thresholdZero("NEGATIVE_INVENTORY_BALANCE", "负库存余额", count,
                "存在负库存余额");
    }

    private BusinessHealthCheckResponse openPeriodCheck(AuditMetadata audit) {
        long count = accountPeriodMapper.selectCount(new LambdaQueryWrapper<AccountPeriodEntity>()
                .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountPeriodEntity::getStatus, "OPEN"));
        String status = count == 0 ? STATUS_WARN : STATUS_UP;
        String summary = count == 0 ? "当前账套没有开放会计期间" : "存在开放会计期间";
        return new BusinessHealthCheckResponse("OPEN_PERIOD_COUNT", "开放会计期间数量", status, count, 1, summary);
    }

    private BusinessHealthCheckResponse thresholdZero(String code, String name, long count, String warnSummary) {
        String status = count > 0 ? STATUS_WARN : STATUS_UP;
        String summary = count > 0 ? warnSummary : "未发现异常";
        return new BusinessHealthCheckResponse(code, name, status, count, 0, summary);
    }
}
