package com.tuowei.erp.issue.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleHitMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.rule.model.ExceptionRuleHitEntity;
import com.tuowei.erp.issue.rule.web.ExceptionRuleScanResultResponse;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ExceptionRuleScanService {

    private static final String RULE_LOW_STOCK = "LOW_STOCK";
    private static final String RULE_RECEIVABLE_OVERDUE = "RECEIVABLE_OVERDUE";
    private static final String RULE_PAYABLE_OVERDUE = "PAYABLE_OVERDUE";
    private static final String RULE_OPERATION_FAILURE = "OPERATION_FAILURE";
    private static final String SCAN_SUCCESS = "SUCCESS";
    private static final String SCAN_FAILED = "FAILED";
    private static final long SYSTEM_USER_ID = 0L;
    private static final int DEFAULT_SCHEDULE_INTERVAL_MINUTES = 60;
    private static final int MIN_SCHEDULE_INTERVAL_MINUTES = 5;
    private static final int MAX_SCHEDULE_INTERVAL_MINUTES = 10080;
    private static final List<String> ACTIVE_TICKET_STATUSES = List.of("OPEN", "PROCESSING", "RESOLVED");

    private final ExceptionRuleMapper ruleMapper;
    private final ExceptionRuleHitMapper hitMapper;
    private final ExceptionTicketMapper ticketMapper;
    private final ExceptionTicketService ticketService;
    private final ExceptionSlaPolicyService slaPolicyService;
    private final InventoryAlertService inventoryAlertService;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final OperationLogMapper operationLogMapper;
    private final Clock clock;

    public ExceptionRuleScanService(
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleHitMapper hitMapper,
            ExceptionTicketMapper ticketMapper,
            ExceptionTicketService ticketService,
            ExceptionSlaPolicyService slaPolicyService,
            InventoryAlertService inventoryAlertService,
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            OperationLogMapper operationLogMapper,
            Clock clock
    ) {
        this.ruleMapper = ruleMapper;
        this.hitMapper = hitMapper;
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
        this.slaPolicyService = slaPolicyService;
        this.inventoryAlertService = inventoryAlertService;
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.operationLogMapper = operationLogMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ExceptionRuleScanResultResponse scanRule(ExceptionRuleEntity rule, AuditMetadata audit) {
        return executeScan(rule, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<ExceptionRuleScanResultResponse> scanRules(
            List<ExceptionRuleEntity> rules,
            AuditMetadata audit
    ) {
        return rules.stream().map(rule -> executeScan(rule, audit)).toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<ExceptionRuleScanResultResponse> scanDueRules() {
        LocalDateTime now = LocalDateTime.now(clock);
        return ruleMapper.selectDueRulesForScheduler(now).stream()
                .map(rule -> executeScan(rule, schedulerAudit(rule, now)))
                .toList();
    }

    private ExceptionRuleScanResultResponse executeScan(ExceptionRuleEntity rule, AuditMetadata audit) {
        LocalDateTime scannedAt = audit.now();
        try {
            List<ExceptionRuleFinding> findings = scanFindings(rule, audit);
            int ticketCreatedCount = 0;
            int duplicateTicketCount = 0;
            for (ExceptionRuleFinding finding : findings) {
                ExceptionTicketEntity activeTicket = findActiveTicket(audit, finding);
                Long ticketId;
                if (activeTicket == null) {
                    ExceptionTicketResponse ticket = ticketService.create(
                            toTicketRequest(rule, finding, audit, scannedAt),
                            audit
                    );
                    ticketId = ticket.id();
                    ticketCreatedCount++;
                } else {
                    ticketId = activeTicket.getId();
                    duplicateTicketCount++;
                }
                upsertHit(rule, finding, ticketId, audit, scannedAt);
            }
            markScan(rule, SCAN_SUCCESS, findings.size(), ticketCreatedCount, null, audit, scannedAt);
            return new ExceptionRuleScanResultResponse(
                    rule.getId(),
                    rule.getRuleCode(),
                    rule.getRuleType(),
                    SCAN_SUCCESS,
                    findings.size(),
                    ticketCreatedCount,
                    duplicateTicketCount,
                    "扫描完成",
                    scannedAt
            );
        } catch (RuntimeException ex) {
            markScan(rule, SCAN_FAILED, 0, 0, ex.getMessage(), audit, scannedAt);
            return new ExceptionRuleScanResultResponse(
                    rule.getId(),
                    rule.getRuleCode(),
                    rule.getRuleType(),
                    SCAN_FAILED,
                    0,
                    0,
                    0,
                    ex.getMessage(),
                    scannedAt
            );
        }
    }

    private List<ExceptionRuleFinding> scanFindings(ExceptionRuleEntity rule, AuditMetadata audit) {
        return switch (rule.getRuleType()) {
            case RULE_LOW_STOCK -> scanLowStock(audit);
            case RULE_RECEIVABLE_OVERDUE -> scanReceivableOverdue(rule, audit);
            case RULE_PAYABLE_OVERDUE -> scanPayableOverdue(rule, audit);
            case RULE_OPERATION_FAILURE -> scanOperationFailures(rule, audit);
            default -> throw new IllegalArgumentException("不支持的异常规则类型：" + rule.getRuleType());
        };
    }

    private List<ExceptionRuleFinding> scanLowStock(AuditMetadata audit) {
        return inventoryAlertService.listLowStock(null, null, audit).stream()
                .map(this::toLowStockFinding)
                .toList();
    }

    private ExceptionRuleFinding toLowStockFinding(InventoryLowStockResponse item) {
        String sourceNo = "W:" + item.warehouseId() + "/P:" + item.productId();
        String route = "/inventory/alerts?warehouseId=" + item.warehouseId() + "&productId=" + item.productId();
        String description = "仓库 " + item.warehouseId()
                + " 商品 " + item.productId()
                + " 当前库存 " + formatDecimal(item.qtyOnHand())
                + "，安全库存 " + formatDecimal(item.minQty())
                + "，缺口 " + formatDecimal(item.shortageQty());
        return new ExceptionRuleFinding(
                RULE_LOW_STOCK,
                item.ruleId(),
                sourceNo,
                route,
                RULE_LOW_STOCK + ":" + item.warehouseId() + ":" + item.productId(),
                "库存低于安全线",
                description,
                formatDecimal(item.qtyOnHand()),
                formatDecimal(item.minQty())
        );
    }

    private List<ExceptionRuleFinding> scanReceivableOverdue(ExceptionRuleEntity rule, AuditMetadata audit) {
        int thresholdDays = thresholdAsInt(rule, 30);
        LocalDate cutoff = audit.now().toLocalDate().minusDays(thresholdDays);
        return receivableMapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                        .eq(ReceivableEntity::getCompanyId, audit.companyId())
                        .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                        .eq(ReceivableEntity::getDeletedFlag, 0)
                        .in(ReceivableEntity::getStatus, List.of("UNSETTLED", "PARTIALLY_SETTLED"))
                        .le(ReceivableEntity::getBizDate, cutoff))
                .stream()
                .filter(item -> remaining(item.getOriginalAmount(), item.getSettledAmount())
                        .compareTo(BigDecimal.ZERO) > 0)
                .map(item -> toReceivableFinding(item, thresholdDays, audit.now().toLocalDate()))
                .toList();
    }

    private ExceptionRuleFinding toReceivableFinding(
            ReceivableEntity item,
            int thresholdDays,
            LocalDate today
    ) {
        long overdueDays = item.getBizDate() == null ? 0 : ChronoUnit.DAYS.between(item.getBizDate(), today);
        BigDecimal remainingAmount = remaining(item.getOriginalAmount(), item.getSettledAmount());
        String sourceNo = firstText(item.getReceivableNo(), item.getSourceNo(), String.valueOf(item.getId()));
        return new ExceptionRuleFinding(
                RULE_RECEIVABLE_OVERDUE,
                item.getId(),
                sourceNo,
                "/finance/receivables?keyword=" + sourceNo,
                RULE_RECEIVABLE_OVERDUE + ":" + item.getId(),
                "应收逾期未结清",
                "应收单 " + sourceNo + " 已超过 " + thresholdDays + " 天未结清，剩余金额 " + formatDecimal(remainingAmount),
                String.valueOf(overdueDays),
                String.valueOf(thresholdDays)
        );
    }

    private List<ExceptionRuleFinding> scanPayableOverdue(ExceptionRuleEntity rule, AuditMetadata audit) {
        int thresholdDays = thresholdAsInt(rule, 30);
        LocalDate cutoff = audit.now().toLocalDate().minusDays(thresholdDays);
        return payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                        .eq(PayableEntity::getCompanyId, audit.companyId())
                        .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                        .eq(PayableEntity::getDeletedFlag, 0)
                        .in(PayableEntity::getStatus, List.of("UNSETTLED", "PARTIALLY_SETTLED"))
                        .le(PayableEntity::getBizDate, cutoff))
                .stream()
                .filter(item -> remaining(item.getOriginalAmount(), item.getSettledAmount())
                        .compareTo(BigDecimal.ZERO) > 0)
                .map(item -> toPayableFinding(item, thresholdDays, audit.now().toLocalDate()))
                .toList();
    }

    private ExceptionRuleFinding toPayableFinding(PayableEntity item, int thresholdDays, LocalDate today) {
        long overdueDays = item.getBizDate() == null ? 0 : ChronoUnit.DAYS.between(item.getBizDate(), today);
        BigDecimal remainingAmount = remaining(item.getOriginalAmount(), item.getSettledAmount());
        String sourceNo = firstText(item.getPayableNo(), item.getSourceNo(), String.valueOf(item.getId()));
        return new ExceptionRuleFinding(
                RULE_PAYABLE_OVERDUE,
                item.getId(),
                sourceNo,
                "/finance/payables?keyword=" + sourceNo,
                RULE_PAYABLE_OVERDUE + ":" + item.getId(),
                "应付逾期未结清",
                "应付单 " + sourceNo + " 已超过 " + thresholdDays + " 天未结清，剩余金额 " + formatDecimal(remainingAmount),
                String.valueOf(overdueDays),
                String.valueOf(thresholdDays)
        );
    }

    private List<ExceptionRuleFinding> scanOperationFailures(ExceptionRuleEntity rule, AuditMetadata audit) {
        int thresholdMinutes = thresholdAsInt(rule, 1440);
        LocalDateTime since = audit.now().minusMinutes(thresholdMinutes);
        return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLogEntity>()
                        .eq(OperationLogEntity::getCompanyId, audit.companyId())
                        .eq(OperationLogEntity::getAccountBookId, audit.accountBookId())
                        .eq(OperationLogEntity::getResult, "FAILURE")
                        .ge(OperationLogEntity::getOperationTime, since))
                .stream()
                .map(item -> toOperationFailureFinding(item, thresholdMinutes))
                .toList();
    }

    private ExceptionRuleFinding toOperationFailureFinding(OperationLogEntity item, int thresholdMinutes) {
        String sourceNo = firstText(item.getBizNo(), item.getRequestUri(), String.valueOf(item.getId()));
        return new ExceptionRuleFinding(
                RULE_OPERATION_FAILURE,
                item.getId(),
                sourceNo,
                "/system/logs?result=FAILURE&bizNo=" + sourceNo,
                RULE_OPERATION_FAILURE + ":" + item.getId(),
                "业务操作失败",
                firstText(item.getModule(), "-")
                        + " / " + firstText(item.getOperation(), "-")
                        + " 执行失败：" + firstText(item.getMessage(), "-"),
                firstText(item.getRequestUri(), "-"),
                String.valueOf(thresholdMinutes)
        );
    }

    private void upsertHit(
            ExceptionRuleEntity rule,
            ExceptionRuleFinding finding,
            Long ticketId,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        ExceptionRuleHitEntity hit = hitMapper.selectOne(new LambdaQueryWrapper<ExceptionRuleHitEntity>()
                .eq(ExceptionRuleHitEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleHitEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleHitEntity::getRuleId, rule.getId())
                .eq(ExceptionRuleHitEntity::getHitKey, finding.hitKey())
                .eq(ExceptionRuleHitEntity::getDeletedFlag, 0));
        if (hit == null) {
            hit = new ExceptionRuleHitEntity();
            hit.setCompanyId(audit.companyId());
            hit.setAccountBookId(audit.accountBookId());
            hit.setRuleId(rule.getId());
            hit.setRuleCode(rule.getRuleCode());
            hit.setRuleType(rule.getRuleType());
            hit.setHitCount(1);
            hit.setFirstHitTime(now);
            hit.setDeletedFlag(0);
            hit.setCreatedBy(audit.userId());
            hit.setCreatedTime(now);
            hit.setVersion(0);
            fillHit(hit, finding, ticketId, audit, now);
            hitMapper.insert(hit);
            return;
        }
        hit.setHitCount((hit.getHitCount() == null ? 0 : hit.getHitCount()) + 1);
        fillHit(hit, finding, ticketId, audit, now);
        hitMapper.updateById(hit);
    }

    private void fillHit(
            ExceptionRuleHitEntity hit,
            ExceptionRuleFinding finding,
            Long ticketId,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        hit.setSourceType(truncate(finding.sourceType(), 64));
        hit.setSourceId(finding.sourceId());
        hit.setSourceNo(truncate(finding.sourceNo(), 128));
        hit.setSourceRoute(truncate(finding.sourceRoute(), 512));
        hit.setHitKey(truncate(finding.hitKey(), 256));
        hit.setTitle(truncate(finding.title(), 128));
        hit.setDescription(truncate(finding.description(), 1024));
        hit.setTriggerValue(truncate(finding.triggerValue(), 64));
        hit.setThresholdValue(truncate(finding.thresholdValue(), 64));
        hit.setTicketId(ticketId);
        hit.setLastHitTime(now);
        hit.setUpdatedBy(audit.userId());
        hit.setUpdatedTime(now);
    }

    private ExceptionTicketEntity findActiveTicket(AuditMetadata audit, ExceptionRuleFinding finding) {
        LambdaQueryWrapper<ExceptionTicketEntity> wrapper = new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, audit.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionTicketEntity::getDeletedFlag, 0)
                .eq(ExceptionTicketEntity::getSourceType, finding.sourceType())
                .in(ExceptionTicketEntity::getStatus, ACTIVE_TICKET_STATUSES);
        if (finding.sourceId() != null) {
            wrapper.eq(ExceptionTicketEntity::getSourceId, finding.sourceId());
        } else {
            wrapper.eq(ExceptionTicketEntity::getSourceNo, finding.sourceNo());
        }
        return ticketMapper.selectOne(wrapper);
    }

    private ExceptionTicketCreateRequest toTicketRequest(
            ExceptionRuleEntity rule,
            ExceptionRuleFinding finding,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        ExceptionTicketCreateRequest request = new ExceptionTicketCreateRequest();
        request.setCategory(rule.getCategory());
        request.setPriority(rule.getPriority());
        request.setTitle(finding.title());
        request.setDescription(finding.description());
        request.setSourceType(finding.sourceType());
        request.setSourceId(finding.sourceId());
        request.setSourceNo(finding.sourceNo());
        request.setSourceRoute(finding.sourceRoute());
        request.setAssigneeUserId(rule.getAssigneeUserId());
        request.setDueTime(slaPolicyService.resolveDueTime(rule.getCategory(), rule.getPriority(), now, audit));
        return request;
    }

    private void markScan(
            ExceptionRuleEntity rule,
            String status,
            int hitCount,
            int ticketCreatedCount,
            String errorMessage,
            AuditMetadata audit,
            LocalDateTime scannedAt
    ) {
        rule.setLastScanTime(scannedAt);
        rule.setLastScanStatus(status);
        rule.setLastHitCount(hitCount);
        rule.setLastTicketCreatedCount(ticketCreatedCount);
        rule.setLastErrorMessage(truncate(errorMessage, 512));
        rule.setNextScanTime(scannedAt.plusMinutes(scheduleIntervalMinutes(rule)));
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(scannedAt);
        ruleMapper.updateById(rule);
    }

    private AuditMetadata schedulerAudit(ExceptionRuleEntity rule, LocalDateTime now) {
        return new AuditMetadata(SYSTEM_USER_ID, rule.getCompanyId(), rule.getAccountBookId(), now);
    }

    private int scheduleIntervalMinutes(ExceptionRuleEntity rule) {
        Integer interval = rule.getScheduleIntervalMinutes();
        if (interval == null) {
            return DEFAULT_SCHEDULE_INTERVAL_MINUTES;
        }
        if (interval < MIN_SCHEDULE_INTERVAL_MINUTES) {
            return MIN_SCHEDULE_INTERVAL_MINUTES;
        }
        if (interval > MAX_SCHEDULE_INTERVAL_MINUTES) {
            return MAX_SCHEDULE_INTERVAL_MINUTES;
        }
        return interval;
    }

    private int thresholdAsInt(ExceptionRuleEntity rule, int defaultValue) {
        BigDecimal value = rule.getThresholdValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return defaultValue;
        }
        return Math.max(1, value.intValue());
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return zeroDefault(originalAmount).subtract(zeroDefault(settledAmount));
    }

    private BigDecimal zeroDefault(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatDecimal(BigDecimal value) {
        return zeroDefault(value).stripTrailingZeros().toPlainString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
