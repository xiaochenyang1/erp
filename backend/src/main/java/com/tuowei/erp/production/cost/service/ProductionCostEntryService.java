package com.tuowei.erp.production.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.production.cost.mapper.ProductionCostEntryMapper;
import com.tuowei.erp.production.cost.model.ProductionCostEntryEntity;
import com.tuowei.erp.production.cost.web.ProductionCostEntryCorrectionRequest;
import com.tuowei.erp.production.cost.web.ProductionCostEntryRequest;
import com.tuowei.erp.production.cost.web.ProductionCostEntryResponse;
import com.tuowei.erp.production.cost.web.ProductionCostEntryReversalRequest;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Posts, reverses and corrects labor and overhead for a production order. */
@Service
public class ProductionCostEntryService {
    public static final String TYPE_LABOR = "LABOR";
    public static final String TYPE_OVERHEAD = "OVERHEAD";
    public static final String STATUS_POSTED = "POSTED";
    public static final String STATUS_REVERSED = "REVERSED";
    private static final String BIZ_TYPE = "PRODUCTION_COST_ENTRY";

    private final ProductionCostEntryMapper costEntryMapper;
    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderOperationMapper operationMapper;
    private final ProductionOrderService productionOrderService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionCostEntryService(
            ProductionCostEntryMapper costEntryMapper,
            ProductionOrderMapper orderMapper,
            ProductionOrderOperationMapper operationMapper,
            ProductionOrderService productionOrderService,
            AccountPeriodGuard accountPeriodGuard,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            SequenceNumberGenerator sequenceNumberGenerator
    ) {
        this.costEntryMapper = costEntryMapper;
        this.orderMapper = orderMapper;
        this.operationMapper = operationMapper;
        this.productionOrderService = productionOrderService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<ProductionCostEntryResponse> list(Long orderId) {
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        return toResponses(selectEntries(order));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProductionCostEntryResponse post(Long orderId, ProductionCostEntryRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        requireCostableOrder(order);
        String costType = normalizeCostType(request.costType());
        BigDecimal amount = requirePositiveAmount(request.amount());
        accountPeriodGuard.requireOpen(request.businessDate(), "生产成本归集");
        validateOperation(order, request.operationId());

        ProductionCostEntryEntity entry = insertEntry(
                order, request.operationId(), costType, request.businessDate(), amount,
                STATUS_POSTED, null, null, normalizeRemark(request.remark()), audit
        );
        applyCostDelta(order, costType, amount);
        persistOrder(order, audit);
        financePostingService.recordProductionCostEntry(
                order, entry.getId(), entry.getCostNo(), costType, amount, entry.getBusinessDate(), audit
        );
        return findResponse(List.of(entry), entry.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProductionCostEntryResponse reverse(
            Long orderId,
            Long costEntryId,
            ProductionCostEntryReversalRequest request
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        requireCostableOrder(order);
        ProductionCostEntryEntity original = requireReversibleEntry(order, costEntryId);
        validateReversalDate(original, request.businessDate());
        accountPeriodGuard.requireOpen(request.businessDate(), "生产成本归集冲销");

        markReversed(original, audit);
        ProductionCostEntryEntity reversal = insertReversal(order, original, request.businessDate(), request.reason(), audit);
        applyCostDelta(order, original.getCostType(), original.getAmount().negate());
        persistOrder(order, audit);
        financePostingService.recordProductionCostEntryReversal(
                order, reversal.getId(), reversal.getCostNo(), original.getCostType(),
                original.getAmount(), reversal.getBusinessDate(), audit
        );
        return findResponse(List.of(original, reversal), reversal.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProductionCostEntryResponse correct(
            Long orderId,
            Long costEntryId,
            ProductionCostEntryCorrectionRequest request
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        requireCostableOrder(order);
        ProductionCostEntryEntity original = requireReversibleEntry(order, costEntryId);
        validateReversalDate(original, request.businessDate());
        String correctedType = normalizeCostType(request.costType());
        BigDecimal correctedAmount = requirePositiveAmount(request.amount());
        validateOperation(order, request.operationId());
        accountPeriodGuard.requireOpen(request.businessDate(), "生产成本归集更正");

        markReversed(original, audit);
        ProductionCostEntryEntity reversal = insertReversal(order, original, request.businessDate(), request.reason(), audit);
        ProductionCostEntryEntity correction = insertEntry(
                order, request.operationId(), correctedType, request.businessDate(), correctedAmount,
                STATUS_POSTED, null, original.getId(), normalizeRemark(request.remark()), audit
        );
        applyCostDelta(order, original.getCostType(), original.getAmount().negate());
        applyCostDelta(order, correctedType, correctedAmount);
        persistOrder(order, audit);
        financePostingService.recordProductionCostEntryReversal(
                order, reversal.getId(), reversal.getCostNo(), original.getCostType(),
                original.getAmount(), reversal.getBusinessDate(), audit
        );
        financePostingService.recordProductionCostEntry(
                order, correction.getId(), correction.getCostNo(), correctedType,
                correctedAmount, correction.getBusinessDate(), audit
        );
        return findResponse(List.of(original, reversal, correction), correction.getId());
    }

    private ProductionCostEntryEntity requireReversibleEntry(ProductionOrderEntity order, Long costEntryId) {
        ProductionCostEntryEntity entry = costEntryMapper.selectOne(entryScope(order)
                .eq(ProductionCostEntryEntity::getId, costEntryId)
                .last("FOR UPDATE"));
        if (entry == null) {
            throw new IllegalArgumentException("生产成本归集单不存在");
        }
        if (!STATUS_POSTED.equals(entry.getStatus())
                || entry.getAmount() == null
                || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || entry.getReversalOfEntryId() != null) {
            throw new IllegalArgumentException("仅未冲销的正向生产成本归集单可操作");
        }
        if (costEntryMapper.selectCount(entryScope(order)
                .eq(ProductionCostEntryEntity::getReversalOfEntryId, entry.getId())) > 0) {
            throw new BusinessConflictException("生产成本归集单已冲销，请刷新后重试");
        }
        return entry;
    }

    private void markReversed(ProductionCostEntryEntity original, AuditMetadata audit) {
        original.setStatus(STATUS_REVERSED);
        original.setUpdatedBy(audit.userId());
        original.setUpdatedTime(audit.now());
        if (costEntryMapper.updateById(original) != 1) {
            throw new BusinessConflictException("生产成本归集单已被其他操作修改，请重试");
        }
    }

    private ProductionCostEntryEntity insertReversal(
            ProductionOrderEntity order,
            ProductionCostEntryEntity original,
            LocalDate businessDate,
            String reason,
            AuditMetadata audit
    ) {
        return insertEntry(
                order, original.getOperationId(), original.getCostType(), businessDate,
                ScalePrecision.amount(original.getAmount()).negate(), STATUS_POSTED,
                original.getId(), null,
                "冲销 " + original.getCostNo() + "：" + reason.trim(), audit
        );
    }

    private ProductionCostEntryEntity insertEntry(
            ProductionOrderEntity order,
            Long operationId,
            String costType,
            LocalDate businessDate,
            BigDecimal amount,
            String status,
            Long reversalOfEntryId,
            Long correctionOfEntryId,
            String remark,
            AuditMetadata audit
    ) {
        LocalDateTime now = audit.now();
        ProductionCostEntryEntity entry = new ProductionCostEntryEntity();
        entry.setCompanyId(audit.companyId());
        entry.setAccountBookId(audit.accountBookId());
        entry.setCostNo(sequenceNumberGenerator.nextNumber(BIZ_TYPE, "生产成本归集单", businessDate));
        entry.setOrderId(order.getId());
        entry.setOperationId(operationId);
        entry.setCostType(costType);
        entry.setBusinessDate(businessDate);
        entry.setAmount(ScalePrecision.amount(amount));
        entry.setStatus(status);
        entry.setReversalOfEntryId(reversalOfEntryId);
        entry.setCorrectionOfEntryId(correctionOfEntryId);
        entry.setDeletedFlag(0);
        entry.setRemark(remark);
        entry.setCreatedBy(audit.userId());
        entry.setCreatedTime(now);
        entry.setUpdatedBy(audit.userId());
        entry.setUpdatedTime(now);
        entry.setVersion(0);
        costEntryMapper.insert(entry);
        return entry;
    }

    private void applyCostDelta(ProductionOrderEntity order, String costType, BigDecimal delta) {
        BigDecimal current = TYPE_LABOR.equals(costType)
                ? ScalePrecision.zeroDefault(order.getLaborAmount())
                : ScalePrecision.zeroDefault(order.getOverheadAmount());
        BigDecimal updated = ScalePrecision.amount(current.add(delta));
        if (updated.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessConflictException("生产工单成本余额异常，不能冲销");
        }
        if (TYPE_LABOR.equals(costType)) {
            order.setLaborAmount(updated);
        } else {
            order.setOverheadAmount(updated);
        }
    }

    private void persistOrder(ProductionOrderEntity order, AuditMetadata audit) {
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(audit.now());
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
    }

    private void requireCostableOrder(ProductionOrderEntity order) {
        if (ProductionOrderService.STATUS_DRAFT.equals(order.getStatus())
                || ProductionOrderService.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new IllegalArgumentException("草稿或已取消的生产工单不能归集成本或冲销成本");
        }
    }

    private void validateReversalDate(ProductionCostEntryEntity original, LocalDate businessDate) {
        if (businessDate.isBefore(original.getBusinessDate())) {
            throw new IllegalArgumentException("冲销或更正日期不能早于原归集日期");
        }
    }

    private void validateOperation(ProductionOrderEntity order, Long operationId) {
        if (operationId == null) return;
        ProductionOrderOperationEntity operation = operationMapper.selectById(operationId);
        if (operation == null
                || !Objects.equals(operation.getCompanyId(), order.getCompanyId())
                || !Objects.equals(operation.getAccountBookId(), order.getAccountBookId())
                || !Objects.equals(operation.getOrderId(), order.getId())
                || Integer.valueOf(1).equals(operation.getDeletedFlag())) {
            throw new IllegalArgumentException("归集工序不存在或不属于当前工单");
        }
    }

    private String normalizeCostType(String costType) {
        String normalized = costType == null ? "" : costType.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_LABOR.equals(normalized) && !TYPE_OVERHEAD.equals(normalized)) {
            throw new IllegalArgumentException("生产成本类型只能为LABOR或OVERHEAD");
        }
        return normalized;
    }

    private BigDecimal requirePositiveAmount(BigDecimal value) {
        BigDecimal amount = ScalePrecision.amount(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("生产成本归集金额必须大于0");
        }
        return amount;
    }

    private String normalizeRemark(String remark) {
        return StringUtils.hasText(remark) ? remark.trim() : null;
    }

    private LambdaQueryWrapper<ProductionCostEntryEntity> entryScope(ProductionOrderEntity order) {
        return new LambdaQueryWrapper<ProductionCostEntryEntity>()
                .eq(ProductionCostEntryEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionCostEntryEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionCostEntryEntity::getOrderId, order.getId())
                .eq(ProductionCostEntryEntity::getDeletedFlag, 0);
    }

    private List<ProductionCostEntryEntity> selectEntries(ProductionOrderEntity order) {
        return costEntryMapper.selectList(entryScope(order)
                .orderByDesc(ProductionCostEntryEntity::getBusinessDate)
                .orderByDesc(ProductionCostEntryEntity::getCreatedTime)
                .orderByDesc(ProductionCostEntryEntity::getId));
    }

    private ProductionCostEntryResponse findResponse(List<ProductionCostEntryEntity> entries, Long entryId) {
        return toResponses(entries).stream()
                .filter(response -> Objects.equals(response.id(), entryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("生产成本归集结果不存在"));
    }

    private List<ProductionCostEntryResponse> toResponses(List<ProductionCostEntryEntity> entries) {
        Map<Long, ProductionCostEntryEntity> byId = new LinkedHashMap<>();
        Map<Long, ProductionCostEntryEntity> reversalByOriginal = new LinkedHashMap<>();
        Map<Long, ProductionCostEntryEntity> correctionByOriginal = new LinkedHashMap<>();
        for (ProductionCostEntryEntity entry : entries) {
            byId.put(entry.getId(), entry);
            if (entry.getReversalOfEntryId() != null) {
                reversalByOriginal.put(entry.getReversalOfEntryId(), entry);
            }
            if (entry.getCorrectionOfEntryId() != null) {
                correctionByOriginal.put(entry.getCorrectionOfEntryId(), entry);
            }
        }
        return entries.stream().map(entry -> {
            ProductionCostEntryEntity reversalOf = byId.get(entry.getReversalOfEntryId());
            ProductionCostEntryEntity correctionOf = byId.get(entry.getCorrectionOfEntryId());
            ProductionCostEntryEntity reversal = reversalByOriginal.get(entry.getId());
            ProductionCostEntryEntity correction = correctionByOriginal.get(entry.getId());
            String entryKind = entry.getReversalOfEntryId() != null
                    ? "REVERSAL"
                    : entry.getCorrectionOfEntryId() != null ? "CORRECTION" : "ORIGINAL";
            return new ProductionCostEntryResponse(
                    entry.getId(), entry.getCostNo(), entry.getOrderId(), entry.getOperationId(),
                    entry.getCostType(), entry.getBusinessDate(), ScalePrecision.amount(entry.getAmount()),
                    entry.getStatus(), entryKind,
                    entry.getReversalOfEntryId(), reversalOf == null ? null : reversalOf.getCostNo(),
                    entry.getCorrectionOfEntryId(), correctionOf == null ? null : correctionOf.getCostNo(),
                    reversal == null ? null : reversal.getId(), reversal == null ? null : reversal.getCostNo(),
                    correction == null ? null : correction.getId(), correction == null ? null : correction.getCostNo(),
                    entry.getRemark(), entry.getCreatedTime()
            );
        }).toList();
    }
}
