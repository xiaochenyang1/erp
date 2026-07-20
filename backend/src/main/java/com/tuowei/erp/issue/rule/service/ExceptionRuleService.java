package com.tuowei.erp.issue.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
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
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitPageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRulePageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleScanResultResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleUpdateRequest;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ExceptionRuleService {

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
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> THRESHOLD_UNITS = Set.of("QTY", "DAYS", "MINUTES", "COUNT");
    private static final List<String> ACTIVE_TICKET_STATUSES = List.of("OPEN", "PROCESSING", "RESOLVED");

    private final AuditMetadataFactory auditMetadataFactory;
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

    public ExceptionRuleService(
            AuditMetadataFactory auditMetadataFactory,
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
        this.auditMetadataFactory = auditMetadataFactory;
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

    @Transactional
    public PageResponse<ExceptionRuleResponse> list(ExceptionRulePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ensureBuiltInRules(audit);
        ExceptionRulePageQuery safeQuery = query == null ? new ExceptionRulePageQuery() : query;
        Page<ExceptionRuleEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ExceptionRuleEntity> result = ruleMapper.selectPage(page, buildRuleQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toRuleResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionRuleHitResponse> listHits(ExceptionRuleHitPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleHitPageQuery safeQuery = query == null ? new ExceptionRuleHitPageQuery() : query;
        Page<ExceptionRuleHitEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ExceptionRuleHitEntity> result = hitMapper.selectPage(page, buildHitQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toHitResponse).toList()
        );
    }

    @Transactional
    public ExceptionRuleResponse update(Long id, ExceptionRuleUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        ExceptionRuleUpdateRequest safeRequest = request == null ? new ExceptionRuleUpdateRequest() : request;
        if (safeRequest.getThresholdValue() != null) {
            if (safeRequest.getThresholdValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("阈值不能小于 0");
            }
            rule.setThresholdValue(safeRequest.getThresholdValue());
        }
        String thresholdUnit = normalizeCode(safeRequest.getThresholdUnit());
        if (thresholdUnit != null) {
            if (!THRESHOLD_UNITS.contains(thresholdUnit)) {
                throw new IllegalArgumentException("阈值单位不支持");
            }
            rule.setThresholdUnit(thresholdUnit);
        }
        String priority = normalizeCode(safeRequest.getPriority());
        if (priority != null) {
            if (!PRIORITIES.contains(priority)) {
                throw new IllegalArgumentException("优先级不支持");
            }
            rule.setPriority(priority);
        }
        if (safeRequest.getScheduleIntervalMinutes() != null) {
            int interval = safeRequest.getScheduleIntervalMinutes();
            if (interval < MIN_SCHEDULE_INTERVAL_MINUTES || interval > MAX_SCHEDULE_INTERVAL_MINUTES) {
                throw new IllegalArgumentException("扫描间隔必须在 5 到 10080 分钟之间");
            }
            rule.setScheduleIntervalMinutes(interval);
        }
        rule.setAssigneeUserId(safeRequest.getAssigneeUserId());
        rule.setRemark(truncate(trimToNull(safeRequest.getRemark()), 512));
        touch(rule, audit);
        ruleMapper.updateById(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public ExceptionRuleResponse enable(Long id) {
        return updateEnabled(id, 1);
    }

    @Transactional
    public ExceptionRuleResponse disable(Long id) {
        return updateEnabled(id, 0);
    }

    @Transactional
    public ExceptionRuleScanResultResponse scanRule(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        if (!Integer.valueOf(1).equals(rule.getEnabled())) {
            throw new IllegalArgumentException("异常规则已停用，不能执行扫描");
        }
        return scanRule(rule, audit);
    }

    @Transactional
    public List<ExceptionRuleScanResultResponse> scanAll() {
        AuditMetadata audit = auditMetadataFactory.current();
        ensureBuiltInRules(audit);
        List<ExceptionRuleEntity> rules = ruleMapper.selectList(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0)
                .eq(ExceptionRuleEntity::getEnabled, 1)
                .orderByAsc(ExceptionRuleEntity::getId));
        return rules.stream().map(rule -> scanRule(rule, audit)).toList();
    }

    @Transactional
    public List<ExceptionRuleScanResultResponse> scanDueRules() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ExceptionRuleEntity> rules = ruleMapper.selectDueRulesForScheduler(now);
        return rules.stream()
                .map(rule -> scanRule(rule, schedulerAudit(rule, now)))
                .toList();
    }

    private void ensureBuiltInRules(AuditMetadata audit) {
        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0));
        if (count != null && count > 0) {
            return;
        }
        builtInRule(audit, "LOW_STOCK_DEFAULT", "低库存自动工单", RULE_LOW_STOCK,
                "LOW_STOCK", "HIGH", BigDecimal.ZERO, "QTY", "扫描库存预警规则命中的低库存项目");
        builtInRule(audit, "RECEIVABLE_OVERDUE_DEFAULT", "应收逾期自动工单", RULE_RECEIVABLE_OVERDUE,
                "PAYMENT_OVERDUE", "HIGH", new BigDecimal("30"), "DAYS", "扫描超过阈值天数仍未结清的应收单");
        builtInRule(audit, "PAYABLE_OVERDUE_DEFAULT", "应付逾期自动工单", RULE_PAYABLE_OVERDUE,
                "PAYMENT_OVERDUE", "MEDIUM", new BigDecimal("30"), "DAYS", "扫描超过阈值天数仍未结清的应付单");
        builtInRule(audit, "OPERATION_FAILURE_DEFAULT", "失败操作日志自动工单", RULE_OPERATION_FAILURE,
                "SYSTEM_ERROR", "MEDIUM", new BigDecimal("1440"), "MINUTES", "扫描最近窗口内的失败操作日志");
    }

    private void builtInRule(
            AuditMetadata audit,
            String ruleCode,
            String ruleName,
            String ruleType,
            String category,
            String priority,
            BigDecimal thresholdValue,
            String thresholdUnit,
            String remark
    ) {
        ExceptionRuleEntity rule = new ExceptionRuleEntity();
        rule.setCompanyId(audit.companyId());
        rule.setAccountBookId(audit.accountBookId());
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setRuleType(ruleType);
        rule.setCategory(category);
        rule.setPriority(priority);
        rule.setThresholdValue(thresholdValue);
        rule.setThresholdUnit(thresholdUnit);
        rule.setEnabled(1);
        rule.setRemark(remark);
        rule.setScheduleIntervalMinutes(DEFAULT_SCHEDULE_INTERVAL_MINUTES);
        rule.setLastHitCount(0);
        rule.setLastTicketCreatedCount(0);
        rule.setDeletedFlag(0);
        rule.setCreatedBy(audit.userId());
        rule.setCreatedTime(audit.now());
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
        rule.setVersion(0);
        ruleMapper.insert(rule);
    }

    private ExceptionRuleScanResultResponse scanRule(ExceptionRuleEntity rule, AuditMetadata audit) {
        LocalDateTime scannedAt = audit.now();
        try {
            List<ExceptionRuleFinding> findings = scanFindings(rule, audit);
            int ticketCreatedCount = 0;
            int duplicateTicketCount = 0;
            for (ExceptionRuleFinding finding : findings) {
                ExceptionTicketEntity activeTicket = findActiveTicket(audit, finding);
                Long ticketId;
                if (activeTicket == null) {
                    ExceptionTicketResponse ticket = ticketService.create(toTicketRequest(rule, finding, audit, scannedAt), audit);
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
        String title = "库存低于安全线";
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
                title,
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
                .filter(item -> remaining(item.getOriginalAmount(), item.getSettledAmount()).compareTo(BigDecimal.ZERO) > 0)
                .map(item -> toReceivableFinding(item, thresholdDays, audit.now().toLocalDate()))
                .toList();
    }

    private ExceptionRuleFinding toReceivableFinding(ReceivableEntity item, int thresholdDays, LocalDate today) {
        long overdueDays = item.getBizDate() == null ? 0 : ChronoUnit.DAYS.between(item.getBizDate(), today);
        BigDecimal remainingAmount = remaining(item.getOriginalAmount(), item.getSettledAmount());
        String sourceNo = firstText(item.getReceivableNo(), item.getSourceNo(), String.valueOf(item.getId()));
        String route = "/finance/receivables?keyword=" + sourceNo;
        return new ExceptionRuleFinding(
                RULE_RECEIVABLE_OVERDUE,
                item.getId(),
                sourceNo,
                route,
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
                .filter(item -> remaining(item.getOriginalAmount(), item.getSettledAmount()).compareTo(BigDecimal.ZERO) > 0)
                .map(item -> toPayableFinding(item, thresholdDays, audit.now().toLocalDate()))
                .toList();
    }

    private ExceptionRuleFinding toPayableFinding(PayableEntity item, int thresholdDays, LocalDate today) {
        long overdueDays = item.getBizDate() == null ? 0 : ChronoUnit.DAYS.between(item.getBizDate(), today);
        BigDecimal remainingAmount = remaining(item.getOriginalAmount(), item.getSettledAmount());
        String sourceNo = firstText(item.getPayableNo(), item.getSourceNo(), String.valueOf(item.getId()));
        String route = "/finance/payables?keyword=" + sourceNo;
        return new ExceptionRuleFinding(
                RULE_PAYABLE_OVERDUE,
                item.getId(),
                sourceNo,
                route,
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
        String route = "/system/logs?result=FAILURE&bizNo=" + sourceNo;
        String description = firstText(item.getModule(), "-")
                + " / " + firstText(item.getOperation(), "-")
                + " 执行失败：" + firstText(item.getMessage(), "-");
        return new ExceptionRuleFinding(
                RULE_OPERATION_FAILURE,
                item.getId(),
                sourceNo,
                route,
                RULE_OPERATION_FAILURE + ":" + item.getId(),
                "业务操作失败",
                description,
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

    private ExceptionRuleResponse updateEnabled(Long id, Integer enabled) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        rule.setEnabled(enabled);
        touch(rule, audit);
        ruleMapper.updateById(rule);
        return toRuleResponse(rule);
    }

    private LambdaQueryWrapper<ExceptionRuleEntity> buildRuleQuery(AuditMetadata audit, ExceptionRulePageQuery query) {
        LambdaQueryWrapper<ExceptionRuleEntity> wrapper = new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested
                    .like(ExceptionRuleEntity::getRuleCode, keyword)
                    .or()
                    .like(ExceptionRuleEntity::getRuleName, keyword)
                    .or()
                    .like(ExceptionRuleEntity::getRemark, keyword));
        }
        String ruleType = normalizeCode(query.getRuleType());
        if (ruleType != null) {
            wrapper.eq(ExceptionRuleEntity::getRuleType, ruleType);
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ExceptionRuleEntity::getEnabled, Boolean.TRUE.equals(query.getEnabled()) ? 1 : 0);
        }
        return wrapper.orderByDesc(ExceptionRuleEntity::getUpdatedTime).orderByDesc(ExceptionRuleEntity::getId);
    }

    private LambdaQueryWrapper<ExceptionRuleHitEntity> buildHitQuery(AuditMetadata audit, ExceptionRuleHitPageQuery query) {
        LambdaQueryWrapper<ExceptionRuleHitEntity> wrapper = new LambdaQueryWrapper<ExceptionRuleHitEntity>()
                .eq(ExceptionRuleHitEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleHitEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleHitEntity::getDeletedFlag, 0);
        if (query.getRuleId() != null) {
            wrapper.eq(ExceptionRuleHitEntity::getRuleId, query.getRuleId());
        }
        String ruleType = normalizeCode(query.getRuleType());
        if (ruleType != null) {
            wrapper.eq(ExceptionRuleHitEntity::getRuleType, ruleType);
        }
        String sourceNo = trimToNull(query.getSourceNo());
        if (sourceNo != null) {
            wrapper.like(ExceptionRuleHitEntity::getSourceNo, sourceNo);
        }
        if (query.getTicketId() != null) {
            wrapper.eq(ExceptionRuleHitEntity::getTicketId, query.getTicketId());
        }
        return wrapper.orderByDesc(ExceptionRuleHitEntity::getLastHitTime).orderByDesc(ExceptionRuleHitEntity::getId);
    }

    private ExceptionRuleEntity requireRule(Long id, AuditMetadata audit) {
        ExceptionRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0)
                .eq(ExceptionRuleEntity::getId, id));
        if (rule == null) {
            throw new IllegalArgumentException("异常规则不存在");
        }
        return rule;
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

    private void touch(ExceptionRuleEntity rule, AuditMetadata audit) {
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
    }

    private ExceptionRuleResponse toRuleResponse(ExceptionRuleEntity rule) {
        return new ExceptionRuleResponse(
                rule.getId(),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getRuleType(),
                rule.getCategory(),
                rule.getPriority(),
                rule.getThresholdValue(),
                rule.getThresholdUnit(),
                Integer.valueOf(1).equals(rule.getEnabled()),
                rule.getAssigneeUserId(),
                scheduleIntervalMinutes(rule),
                rule.getNextScanTime(),
                rule.getRemark(),
                rule.getLastScanTime(),
                rule.getLastScanStatus(),
                rule.getLastHitCount(),
                rule.getLastTicketCreatedCount(),
                rule.getLastErrorMessage(),
                rule.getUpdatedTime()
        );
    }

    private ExceptionRuleHitResponse toHitResponse(ExceptionRuleHitEntity hit) {
        return new ExceptionRuleHitResponse(
                hit.getId(),
                hit.getRuleId(),
                hit.getRuleCode(),
                hit.getRuleType(),
                hit.getSourceType(),
                hit.getSourceId(),
                hit.getSourceNo(),
                hit.getSourceRoute(),
                hit.getHitKey(),
                hit.getTitle(),
                hit.getDescription(),
                hit.getTriggerValue(),
                hit.getThresholdValue(),
                hit.getTicketId(),
                hit.getHitCount(),
                hit.getFirstHitTime(),
                hit.getLastHitTime()
        );
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

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
