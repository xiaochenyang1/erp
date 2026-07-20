package com.tuowei.erp.system.readiness.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessEvidenceMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessRunMapper;
import com.tuowei.erp.system.readiness.model.ReadinessEvidenceEntity;
import com.tuowei.erp.system.readiness.model.ReadinessItemEntity;
import com.tuowei.erp.system.readiness.model.ReadinessRunEntity;
import com.tuowei.erp.system.readiness.web.ReadinessDecisionRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemResultRequest;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessRunDetailResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import com.tuowei.erp.system.readiness.web.ReadinessRunResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReadinessService {

    private static final String RUN_DRAFT = "DRAFT";
    private static final String RUN_IN_PROGRESS = "IN_PROGRESS";
    private static final String RUN_PASSED = "PASSED";
    private static final String RUN_FAILED = "FAILED";
    private static final String RUN_BLOCKED = "BLOCKED";
    private static final String RUN_NO_GO = "NO_GO";
    private static final String DECISION_PENDING = "PENDING";
    private static final String DECISION_GO = "GO";
    private static final String DECISION_NO_GO = "NO_GO";
    private static final String ITEM_PENDING = "PENDING";
    private static final String ITEM_PASSED = "PASSED";
    private static final String ITEM_FAILED = "FAILED";
    private static final String ITEM_BLOCKED = "BLOCKED";
    private static final String ITEM_SKIPPED = "SKIPPED";
    private static final String PREFLIGHT_ITEM_CODE = "MIGRATION_PREFLIGHT";
    private static final Set<String> ITEM_RESULT_STATUSES = Set.of(ITEM_PASSED, ITEM_FAILED, ITEM_BLOCKED, ITEM_SKIPPED);
    private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2");
    private static final Set<String> EVIDENCE_TYPES = Set.of("API", "BUSINESS_NO", "LOG", "SCREENSHOT", "NOTE", "ATTACHMENT");
    private static final Set<String> CLOSED_RUN_STATUSES = Set.of(RUN_PASSED, RUN_FAILED, RUN_BLOCKED, RUN_NO_GO);
    private static final DateTimeFormatter RUN_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final List<DefaultReadinessItem> DEFAULT_PREPRODUCTION_ITEMS = List.of(
            new DefaultReadinessItem("RELEASE_GATE", "发布门禁", "RELEASE", "P0", "release-check、jar 和 SBOM 全部通过"),
            new DefaultReadinessItem("DOCKER_COMPOSE_HEALTH", "Docker Compose 启动健康检查", "DEPLOYMENT", "P0", "MySQL、Redis、后端服务健康检查全部通过"),
            new DefaultReadinessItem("AUTH_SMOKE", "登录与受保护接口冒烟", "AUTH", "P0", "登录、401、403 和 /api/system/profile 验证通过"),
            new DefaultReadinessItem("PURCHASE_TO_PAYMENT", "采购到付款", "PURCHASE", "P0", "采购入库、应付、付款核销闭环通过"),
            new DefaultReadinessItem("SALES_TO_RECEIPT", "销售到收款", "SALES", "P0", "销售出库、应收、收款核销闭环通过"),
            new DefaultReadinessItem("FINANCE_LEDGER", "财务账簿", "FINANCE", "P0", "凭证分录借贷平衡，总账和明细账可查"),
            new DefaultReadinessItem("PERIOD_LOCK", "期间锁账", "FINANCE", "P0", "锁账、结账、反开和锁定期间写入拦截通过"),
            new DefaultReadinessItem("INVENTORY_FINANCE_RECONCILIATION", "库存财务对账", "FINANCE", "P0", "库存流水与库存科目凭证对账通过"),
            new DefaultReadinessItem("PRODUCTION_MANUFACTURING", "生产制造", "PRODUCTION", "P1", "BOM、工单、领料、完工和退料链路通过"),
            new DefaultReadinessItem("INITIAL_IMPORT", "期初导入", "IMPORT", "P1", "模板、预览、提交、重复提交拒绝和晚期期初拒绝通过"),
            new DefaultReadinessItem("BACKUP_ROLLBACK", "备份与回滚确认", "OPERATIONS", "P1", "数据库备份、上一版镜像和回滚负责人已确认"),
            new DefaultReadinessItem("PREPROD_APPROVAL_GATE", "审批前总门禁", "DEPLOYMENT", "P0", "证据索引、离线补传校验和系统 readiness 证据对账全部通过，总门禁报告登记为 READY_FOR_APPROVAL")
    );

    private final ReadinessRunMapper runMapper;
    private final ReadinessItemMapper itemMapper;
    private final ReadinessEvidenceMapper evidenceMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ReadinessService(
            ReadinessRunMapper runMapper,
            ReadinessItemMapper itemMapper,
            ReadinessEvidenceMapper evidenceMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.runMapper = runMapper;
        this.itemMapper = itemMapper;
        this.evidenceMapper = evidenceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ReadinessRunResponse createRun(ReadinessRunCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReadinessRunEntity run = new ReadinessRunEntity();
        run.setCompanyId(audit.companyId());
        run.setAccountBookId(audit.accountBookId());
        run.setRunNo("RDY" + RUN_NO_FORMATTER.format(now));
        run.setReleaseCommit(normalizeRequired(request.releaseCommit(), "候选 commit 不能为空"));
        run.setReleaseVersion(normalizeNullable(request.releaseVersion()));
        run.setEnvironment(normalizeCode(request.environment(), "验收环境不能为空"));
        run.setDatabaseInstance(normalizeNullable(request.databaseInstance()));
        run.setRedisInstance(normalizeNullable(request.redisInstance()));
        run.setDockerProfile(normalizeNullable(request.dockerProfile()));
        run.setStatus(RUN_DRAFT);
        run.setDecision(DECISION_PENDING);
        run.setRemark(normalizeNullable(request.remark()));
        run.setStartedBy(audit.userId());
        run.setStartedTime(now);
        fillCreateAudit(run, audit, now);
        runMapper.insert(run);
        if (Boolean.TRUE.equals(request.generateDefaultItems())) {
            createDefaultItems(run, audit, now);
            run.setStatus(RUN_IN_PROGRESS);
            fillUpdateAudit(run, audit, now);
            runMapper.updateById(run);
        }
        return toRunResponse(run);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadinessRunResponse> listRuns(ReadinessRunPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReadinessRunPageQuery safeQuery = query == null ? new ReadinessRunPageQuery() : query;
        Page<ReadinessRunEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ReadinessRunEntity> result = runMapper.selectPage(page, buildRunQuery(safeQuery, audit));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toRunResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public ReadinessRunDetailResponse detail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReadinessRunEntity run = requireRun(id, audit);
        List<ReadinessItemEntity> items = itemMapper.selectList(baseItemQuery(audit)
                .eq(ReadinessItemEntity::getRunId, run.getId())
                .orderByAsc(ReadinessItemEntity::getCreatedTime)
                .orderByAsc(ReadinessItemEntity::getId));
        Map<Long, List<ReadinessEvidenceResponse>> evidence = loadEvidenceByItemId(run, items, audit);
        return new ReadinessRunDetailResponse(
                toRunResponse(run),
                items.stream()
                        .map(item -> toItemResponse(item, evidence.getOrDefault(item.getId(), List.of())))
                        .toList()
        );
    }

    @Transactional
    public ReadinessItemResponse addItem(Long runId, ReadinessItemCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReadinessRunEntity run = requireRun(runId, audit);
        assertRunOpen(run);
        ReadinessItemEntity item = new ReadinessItemEntity();
        item.setCompanyId(audit.companyId());
        item.setAccountBookId(audit.accountBookId());
        item.setRunId(run.getId());
        item.setItemCode(normalizeCode(request.itemCode(), "验收项编码不能为空"));
        item.setItemName(normalizeRequired(request.itemName(), "验收项名称不能为空"));
        item.setCategory(normalizeCode(request.category(), "验收项分类不能为空"));
        item.setPriority(normalizePriority(request.priority()));
        item.setStatus(ITEM_PENDING);
        item.setExpectedResult(normalizeNullable(request.expectedResult()));
        fillCreateAudit(item, audit, now);
        itemMapper.insert(item);
        if (RUN_DRAFT.equals(run.getStatus())) {
            run.setStatus(RUN_IN_PROGRESS);
            fillUpdateAudit(run, audit, now);
            runMapper.updateById(run);
        }
        return toItemResponse(item, List.of());
    }

    @Transactional
    public ReadinessEvidenceResponse addEvidence(Long itemId, ReadinessEvidenceCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReadinessItemEntity item = requireItem(itemId, audit);
        ReadinessRunEntity run = requireRun(item.getRunId(), audit);
        assertRunOpen(run);
        ReadinessEvidenceEntity evidence = new ReadinessEvidenceEntity();
        evidence.setCompanyId(audit.companyId());
        evidence.setAccountBookId(audit.accountBookId());
        evidence.setRunId(run.getId());
        evidence.setItemId(item.getId());
        evidence.setEvidenceType(normalizeEvidenceType(request.evidenceType()));
        evidence.setRequestMethod(normalizeCodeNullable(request.requestMethod()));
        evidence.setRequestUri(normalizeNullable(request.requestUri()));
        evidence.setHttpStatus(request.httpStatus());
        evidence.setBusinessType(normalizeCodeNullable(request.businessType()));
        evidence.setBusinessId(request.businessId());
        evidence.setBusinessNo(normalizeNullable(request.businessNo()));
        evidence.setSummary(normalizeRequired(request.summary(), "证据摘要不能为空"));
        evidence.setDetail(normalizeNullable(request.detail()));
        evidence.setAttachmentBusinessType(normalizeCodeNullable(request.attachmentBusinessType()));
        evidence.setAttachmentBusinessId(request.attachmentBusinessId());
        evidence.setRecordedBy(audit.userId());
        evidence.setRecordedTime(now);
        fillCreateAudit(evidence, audit, now);
        evidenceMapper.insert(evidence);
        return toEvidenceResponse(evidence);
    }

    @Transactional
    public ReadinessItemResponse markItemResult(Long itemId, ReadinessItemResultRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReadinessItemEntity item = requireItem(itemId, audit);
        ReadinessRunEntity run = requireRun(item.getRunId(), audit);
        assertRunOpen(run);
        String status = normalizeCode(request.status(), "验收项状态不能为空");
        if (!ITEM_RESULT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("验收项状态不合法");
        }
        String failureReason = normalizeNullable(request.failureReason());
        if ("P0".equals(item.getPriority()) && ITEM_SKIPPED.equals(status)) {
            throw new IllegalArgumentException("P0 验收项不能跳过");
        }
        if ("P1".equals(item.getPriority()) && ITEM_SKIPPED.equals(status) && !StringUtils.hasText(failureReason)) {
            throw new IllegalArgumentException("P1 验收项跳过原因不能为空");
        }
        item.setStatus(status);
        item.setActualResult(normalizeNullable(request.actualResult()));
        item.setFailureReason(failureReason);
        item.setExecutedBy(audit.userId());
        item.setExecutedTime(now);
        fillUpdateAudit(item, audit, now);
        itemMapper.updateById(item);
        return toItemResponse(item, loadEvidenceForItem(item, audit));
    }

    @Transactional
    public ReadinessRunResponse decide(Long runId, ReadinessDecisionRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReadinessRunEntity run = requireRun(runId, audit);
        assertRunOpen(run);
        String decision = normalizeCode(request.decision(), "发布决策不能为空");
        String status = normalizeCode(request.status(), "验收运行单状态不能为空");
        String decisionComment = normalizeNullable(request.decisionComment());
        if (!Set.of(DECISION_GO, DECISION_NO_GO).contains(decision)) {
            throw new IllegalArgumentException("发布决策不合法");
        }
        if (DECISION_GO.equals(decision)) {
            if (!RUN_PASSED.equals(status)) {
                throw new IllegalArgumentException("Go 决策只能标记为通过");
            }
            if (hasUnpassedP0P1Items(run, audit)) {
                throw new IllegalArgumentException("存在未通过或未执行的 P0/P1 验收项，不能标记发布通过");
            }
        } else {
            if (!StringUtils.hasText(decisionComment)) {
                throw new IllegalArgumentException("No-Go 决策说明不能为空");
            }
            if (!Set.of(RUN_FAILED, RUN_BLOCKED, RUN_NO_GO).contains(status)) {
                throw new IllegalArgumentException("No-Go 决策状态不合法");
            }
        }
        run.setDecision(decision);
        run.setStatus(status);
        run.setDecisionComment(decisionComment);
        run.setDecidedBy(audit.userId());
        run.setDecidedTime(now);
        fillUpdateAudit(run, audit, now);
        runMapper.updateById(run);
        return toRunResponse(run);
    }

    @Transactional
    public ReadinessPreflightResponse recordPreflightEvidence(Long runId, ReadinessPreflightResponse preflight) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReadinessRunEntity run = requireRun(runId, audit);
        assertRunOpen(run);
        ReadinessItemEntity item = findOrCreatePreflightItem(run, audit, now);
        item.setStatus(toItemStatus(preflight.overallStatus()));
        item.setActualResult("迁移前健康检查：" + preflight.overallStatus());
        item.setFailureReason(preflightFailureReason(preflight));
        item.setExecutedBy(audit.userId());
        item.setExecutedTime(now);
        fillUpdateAudit(item, audit, now);
        itemMapper.updateById(item);

        ReadinessEvidenceEntity evidence = new ReadinessEvidenceEntity();
        evidence.setCompanyId(audit.companyId());
        evidence.setAccountBookId(audit.accountBookId());
        evidence.setRunId(run.getId());
        evidence.setItemId(item.getId());
        evidence.setEvidenceType("API");
        evidence.setRequestMethod("POST");
        evidence.setRequestUri("/api/system/readiness/runs/" + run.getId() + "/preflight-evidence");
        evidence.setHttpStatus(200);
        evidence.setBusinessType("READINESS_PREFLIGHT");
        evidence.setBusinessId(run.getId());
        evidence.setBusinessNo(run.getRunNo());
        evidence.setSummary("迁移前健康检查：" + preflight.overallStatus());
        evidence.setDetail(formatPreflightEvidence(preflight));
        evidence.setRecordedBy(audit.userId());
        evidence.setRecordedTime(now);
        fillCreateAudit(evidence, audit, now);
        evidenceMapper.insert(evidence);

        if (RUN_DRAFT.equals(run.getStatus())) {
            run.setStatus(RUN_IN_PROGRESS);
            fillUpdateAudit(run, audit, now);
            runMapper.updateById(run);
        }
        return preflight;
    }

    private LambdaQueryWrapper<ReadinessRunEntity> buildRunQuery(ReadinessRunPageQuery query, AuditMetadata audit) {
        LambdaQueryWrapper<ReadinessRunEntity> wrapper = baseRunQuery(audit);
        String releaseCommit = normalizeNullable(query.getReleaseCommit());
        if (StringUtils.hasText(releaseCommit)) {
            wrapper.eq(ReadinessRunEntity::getReleaseCommit, releaseCommit);
        }
        String environment = normalizeCodeNullable(query.getEnvironment());
        if (StringUtils.hasText(environment)) {
            wrapper.eq(ReadinessRunEntity::getEnvironment, environment);
        }
        String status = normalizeCodeNullable(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ReadinessRunEntity::getStatus, status);
        }
        String decision = normalizeCodeNullable(query.getDecision());
        if (StringUtils.hasText(decision)) {
            wrapper.eq(ReadinessRunEntity::getDecision, decision);
        }
        if (query.getCreatedTimeFrom() != null) {
            wrapper.ge(ReadinessRunEntity::getCreatedTime, query.getCreatedTimeFrom());
        }
        if (query.getCreatedTimeTo() != null) {
            wrapper.le(ReadinessRunEntity::getCreatedTime, query.getCreatedTimeTo());
        }
        return wrapper.orderByDesc(ReadinessRunEntity::getCreatedTime).orderByDesc(ReadinessRunEntity::getId);
    }

    private ReadinessRunEntity requireRun(Long id, AuditMetadata audit) {
        ReadinessRunEntity run = runMapper.selectOne(baseRunQuery(audit)
                .eq(ReadinessRunEntity::getId, id)
                .last("limit 1"));
        if (run == null) {
            throw new IllegalArgumentException("验收运行单不存在");
        }
        return run;
    }

    private ReadinessItemEntity requireItem(Long id, AuditMetadata audit) {
        ReadinessItemEntity item = itemMapper.selectOne(baseItemQuery(audit)
                .eq(ReadinessItemEntity::getId, id)
                .last("limit 1"));
        if (item == null) {
            throw new IllegalArgumentException("验收项不存在");
        }
        return item;
    }

    private ReadinessItemEntity findOrCreatePreflightItem(ReadinessRunEntity run, AuditMetadata audit, LocalDateTime now) {
        ReadinessItemEntity item = itemMapper.selectOne(baseItemQuery(audit)
                .eq(ReadinessItemEntity::getRunId, run.getId())
                .eq(ReadinessItemEntity::getItemCode, PREFLIGHT_ITEM_CODE)
                .last("limit 1"));
        if (item != null) {
            return item;
        }
        item = new ReadinessItemEntity();
        item.setCompanyId(audit.companyId());
        item.setAccountBookId(audit.accountBookId());
        item.setRunId(run.getId());
        item.setItemCode(PREFLIGHT_ITEM_CODE);
        item.setItemName("迁移前健康检查");
        item.setCategory("MIGRATION");
        item.setPriority("P0");
        item.setStatus(ITEM_PENDING);
        item.setExpectedResult("迁移前健康检查整体通过，P0 检查项无失败");
        fillCreateAudit(item, audit, now);
        itemMapper.insert(item);
        return item;
    }

    private LambdaQueryWrapper<ReadinessRunEntity> baseRunQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReadinessRunEntity>()
                .eq(ReadinessRunEntity::getCompanyId, audit.companyId())
                .eq(ReadinessRunEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessRunEntity::getDeletedFlag, 0);
    }

    private LambdaQueryWrapper<ReadinessItemEntity> baseItemQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReadinessItemEntity>()
                .eq(ReadinessItemEntity::getCompanyId, audit.companyId())
                .eq(ReadinessItemEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessItemEntity::getDeletedFlag, 0);
    }

    private LambdaQueryWrapper<ReadinessEvidenceEntity> baseEvidenceQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReadinessEvidenceEntity>()
                .eq(ReadinessEvidenceEntity::getCompanyId, audit.companyId())
                .eq(ReadinessEvidenceEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessEvidenceEntity::getDeletedFlag, 0);
    }

    private void assertRunOpen(ReadinessRunEntity run) {
        if (CLOSED_RUN_STATUSES.contains(run.getStatus())) {
            throw new IllegalArgumentException("已关闭的验收运行单不能修改");
        }
    }

    private boolean hasUnpassedP0P1Items(ReadinessRunEntity run, AuditMetadata audit) {
        return itemMapper.selectCount(baseItemQuery(audit)
                .eq(ReadinessItemEntity::getRunId, run.getId())
                .in(ReadinessItemEntity::getPriority, List.of("P0", "P1"))
                .ne(ReadinessItemEntity::getStatus, ITEM_PASSED)) > 0;
    }

    private void createDefaultItems(ReadinessRunEntity run, AuditMetadata audit, LocalDateTime now) {
        for (DefaultReadinessItem defaultItem : DEFAULT_PREPRODUCTION_ITEMS) {
            ReadinessItemEntity item = new ReadinessItemEntity();
            item.setCompanyId(audit.companyId());
            item.setAccountBookId(audit.accountBookId());
            item.setRunId(run.getId());
            item.setItemCode(defaultItem.itemCode());
            item.setItemName(defaultItem.itemName());
            item.setCategory(defaultItem.category());
            item.setPriority(defaultItem.priority());
            item.setStatus(ITEM_PENDING);
            item.setExpectedResult(defaultItem.expectedResult());
            fillCreateAudit(item, audit, now);
            itemMapper.insert(item);
        }
    }

    private Map<Long, List<ReadinessEvidenceResponse>> loadEvidenceByItemId(
            ReadinessRunEntity run,
            List<ReadinessItemEntity> items,
            AuditMetadata audit
    ) {
        List<Long> itemIds = items.stream().map(ReadinessItemEntity::getId).toList();
        if (itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return evidenceMapper.selectList(baseEvidenceQuery(audit)
                        .eq(ReadinessEvidenceEntity::getRunId, run.getId())
                        .in(ReadinessEvidenceEntity::getItemId, itemIds)
                        .orderByAsc(ReadinessEvidenceEntity::getRecordedTime)
                        .orderByAsc(ReadinessEvidenceEntity::getId))
                .stream()
                .map(this::toEvidenceResponse)
                .collect(Collectors.groupingBy(ReadinessEvidenceResponse::itemId));
    }

    private List<ReadinessEvidenceResponse> loadEvidenceForItem(ReadinessItemEntity item, AuditMetadata audit) {
        return evidenceMapper.selectList(baseEvidenceQuery(audit)
                        .eq(ReadinessEvidenceEntity::getRunId, item.getRunId())
                        .eq(ReadinessEvidenceEntity::getItemId, item.getId())
                        .orderByAsc(ReadinessEvidenceEntity::getRecordedTime)
                        .orderByAsc(ReadinessEvidenceEntity::getId))
                .stream()
                .map(this::toEvidenceResponse)
                .toList();
    }

    private void fillCreateAudit(ReadinessRunEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillCreateAudit(ReadinessItemEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillCreateAudit(ReadinessEvidenceEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillUpdateAudit(ReadinessRunEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
    }

    private void fillUpdateAudit(ReadinessItemEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
    }

    private String toItemStatus(String overallStatus) {
        if ("PASS".equals(overallStatus)) {
            return ITEM_PASSED;
        }
        if ("WARN".equals(overallStatus)) {
            return ITEM_BLOCKED;
        }
        return ITEM_FAILED;
    }

    private String preflightFailureReason(ReadinessPreflightResponse preflight) {
        if ("PASS".equals(preflight.overallStatus())) {
            return null;
        }
        return preflight.items().stream()
                .filter(item -> !"PASS".equals(item.status()))
                .map(item -> item.code() + "=" + item.status() + "(" + item.count() + ")")
                .collect(Collectors.joining("; "));
    }

    private String formatPreflightEvidence(ReadinessPreflightResponse preflight) {
        return preflight.items().stream()
                .map(this::formatPreflightItem)
                .collect(Collectors.joining("\n"));
    }

    private String formatPreflightItem(ReadinessPreflightItemResponse item) {
        String sample = item.sample().isEmpty() ? "" : " sample=" + String.join(" | ", item.sample());
        return item.code() + " [" + item.severity() + "] " + item.status()
                + " count=" + item.count() + " " + item.summary() + sample;
    }

    private String normalizePriority(String value) {
        String priority = normalizeCode(value, "验收项优先级不能为空");
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("验收项优先级不合法");
        }
        return priority;
    }

    private String normalizeEvidenceType(String value) {
        String evidenceType = normalizeCode(value, "证据类型不能为空");
        if (!EVIDENCE_TYPES.contains(evidenceType)) {
            throw new IllegalArgumentException("证据类型不合法");
        }
        return evidenceType;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeCode(String value, String message) {
        return normalizeRequired(value, message).toUpperCase(Locale.ROOT);
    }

    private String normalizeCodeNullable(String value) {
        String normalized = normalizeNullable(value);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private ReadinessRunResponse toRunResponse(ReadinessRunEntity entity) {
        return new ReadinessRunResponse(
                entity.getId(),
                entity.getRunNo(),
                entity.getReleaseCommit(),
                entity.getReleaseVersion(),
                entity.getEnvironment(),
                entity.getDatabaseInstance(),
                entity.getRedisInstance(),
                entity.getDockerProfile(),
                entity.getStatus(),
                entity.getDecision(),
                entity.getDecisionComment(),
                entity.getRemark(),
                entity.getStartedBy(),
                entity.getStartedTime(),
                entity.getDecidedBy(),
                entity.getDecidedTime(),
                entity.getCreatedTime()
        );
    }

    private ReadinessItemResponse toItemResponse(ReadinessItemEntity entity, List<ReadinessEvidenceResponse> evidence) {
        return new ReadinessItemResponse(
                entity.getId(),
                entity.getRunId(),
                entity.getItemCode(),
                entity.getItemName(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getExpectedResult(),
                entity.getActualResult(),
                entity.getFailureReason(),
                entity.getExecutedBy(),
                entity.getExecutedTime(),
                entity.getCreatedTime(),
                evidence
        );
    }

    private ReadinessEvidenceResponse toEvidenceResponse(ReadinessEvidenceEntity entity) {
        return new ReadinessEvidenceResponse(
                entity.getId(),
                entity.getRunId(),
                entity.getItemId(),
                entity.getEvidenceType(),
                entity.getRequestMethod(),
                entity.getRequestUri(),
                entity.getHttpStatus(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getSummary(),
                entity.getDetail(),
                entity.getAttachmentBusinessType(),
                entity.getAttachmentBusinessId(),
                entity.getRecordedBy(),
                entity.getRecordedTime()
        );
    }

    private record DefaultReadinessItem(
            String itemCode,
            String itemName,
            String category,
            String priority,
            String expectedResult
    ) {
    }
}
