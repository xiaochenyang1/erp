package com.tuowei.erp.production.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionMapper;
import com.tuowei.erp.production.completion.model.ProductionCompletionEntity;
import com.tuowei.erp.production.cost.mapper.ProductionCostCloseMapper;
import com.tuowei.erp.production.cost.mapper.ProductionCostEntryMapper;
import com.tuowei.erp.production.cost.model.ProductionCostCloseEntity;
import com.tuowei.erp.production.cost.model.ProductionCostEntryEntity;
import com.tuowei.erp.production.cost.web.ProductionCostCloseRequest;
import com.tuowei.erp.production.cost.web.ProductionCostCloseResponse;
import com.tuowei.erp.production.cost.web.ProductionCostReopenRequest;
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
import java.util.Map;
import java.util.Objects;

/** Closes completed production costs into an auditable variance account and supports immutable reopening. */
@Service
public class ProductionCostCloseService {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String ENTRY_POSTED = "POSTED";
    public static final String ENTRY_REVERSED = "REVERSED";
    private static final String BIZ_TYPE = "PRODUCTION_COST_CLOSE";

    private final ProductionCostCloseMapper closeMapper;
    private final ProductionCostEntryMapper costEntryMapper;
    private final ProductionCompletionMapper completionMapper;
    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderService productionOrderService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionCostCloseService(
            ProductionCostCloseMapper closeMapper,
            ProductionCostEntryMapper costEntryMapper,
            ProductionCompletionMapper completionMapper,
            ProductionOrderMapper orderMapper,
            ProductionOrderService productionOrderService,
            AccountPeriodGuard accountPeriodGuard,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            SequenceNumberGenerator sequenceNumberGenerator
    ) {
        this.closeMapper = closeMapper;
        this.costEntryMapper = costEntryMapper;
        this.completionMapper = completionMapper;
        this.orderMapper = orderMapper;
        this.productionOrderService = productionOrderService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<ProductionCostCloseResponse> list(Long orderId) {
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        return toResponses(selectEntries(order));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProductionCostCloseResponse close(Long orderId, ProductionCostCloseRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireLockedOrder(orderId);
        if (!ProductionOrderService.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已完工的生产工单可以成本关单");
        }
        productionOrderService.requireCostOpen(order, "成本关单");
        validateBusinessDate(order, request.businessDate());
        accountPeriodGuard.requireOpen(request.businessDate(), "生产成本关单");

        BigDecimal variance = unsettledVariance(order);
        ProductionCostCloseEntity entry = insertEntry(
                order, request.businessDate(), variance, null, null, normalize(request.remark()), audit
        );
        order.setSettledVarianceAmount(ScalePrecision.amount(
                ScalePrecision.zeroDefault(order.getSettledVarianceAmount()).add(variance)
        ));
        order.setCostCloseStatus(STATUS_CLOSED);
        order.setCostCloseEntryId(entry.getId());
        order.setCostClosedDate(request.businessDate());
        order.setCostClosedBy(audit.userId());
        order.setCostClosedTime(audit.now());
        persistOrder(order, audit);
        financePostingService.recordProductionCostClose(
                order, entry.getId(), entry.getCloseNo(), variance, entry.getBusinessDate(), audit
        );
        return findResponse(List.of(entry), entry.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProductionCostCloseResponse reopen(Long orderId, ProductionCostReopenRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireLockedOrder(orderId);
        if (!STATUS_CLOSED.equals(order.getCostCloseStatus()) || order.getCostCloseEntryId() == null) {
            throw new IllegalArgumentException("生产工单尚未成本关单");
        }
        ProductionCostCloseEntity original = requireActiveClose(order, order.getCostCloseEntryId());
        if (request.businessDate().isBefore(original.getBusinessDate())) {
            throw new IllegalArgumentException("反关单日期不能早于原关单日期");
        }
        accountPeriodGuard.requireOpen(request.businessDate(), "生产成本反关单");

        original.setStatus(ENTRY_REVERSED);
        original.setUpdatedBy(audit.userId());
        original.setUpdatedTime(audit.now());
        if (closeMapper.updateById(original) != 1) {
            throw new BusinessConflictException("生产成本关单记录已被其他操作修改，请重试");
        }
        String reason = request.reason().trim();
        ProductionCostCloseEntity reversal = insertEntry(
                order,
                request.businessDate(),
                ScalePrecision.amount(original.getAmount()).negate(),
                original.getId(),
                reason,
                "反关单 " + original.getCloseNo() + "：" + reason,
                audit
        );
        order.setSettledVarianceAmount(ScalePrecision.amount(
                ScalePrecision.zeroDefault(order.getSettledVarianceAmount()).add(reversal.getAmount())
        ));
        order.setCostCloseStatus(STATUS_OPEN);
        order.setCostCloseEntryId(null);
        order.setCostClosedDate(null);
        order.setCostClosedBy(null);
        order.setCostClosedTime(null);
        persistOrder(order, audit);
        financePostingService.recordProductionCostCloseReversal(
                order, reversal.getId(), reversal.getCloseNo(), reversal.getAmount(),
                reversal.getBusinessDate(), audit
        );
        return findResponse(List.of(original, reversal), reversal.getId());
    }

    private ProductionOrderEntity requireLockedOrder(Long orderId) {
        ProductionOrderEntity visible = productionOrderService.requireOrder(orderId);
        ProductionOrderEntity locked = orderMapper.selectOne(new LambdaQueryWrapper<ProductionOrderEntity>()
                .eq(ProductionOrderEntity::getCompanyId, visible.getCompanyId())
                .eq(ProductionOrderEntity::getAccountBookId, visible.getAccountBookId())
                .eq(ProductionOrderEntity::getId, visible.getId())
                .eq(ProductionOrderEntity::getDeletedFlag, 0)
                .last("FOR UPDATE"));
        if (locked == null) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        return locked;
    }

    private ProductionCostCloseEntity requireActiveClose(ProductionOrderEntity order, Long closeId) {
        ProductionCostCloseEntity entry = closeMapper.selectOne(scope(order)
                .eq(ProductionCostCloseEntity::getId, closeId)
                .last("FOR UPDATE"));
        if (entry == null
                || !ENTRY_POSTED.equals(entry.getStatus())
                || entry.getReversalOfCloseId() != null) {
            throw new BusinessConflictException("当前生产成本关单记录不存在或已反关单");
        }
        if (closeMapper.selectCount(scope(order)
                .eq(ProductionCostCloseEntity::getReversalOfCloseId, entry.getId())) > 0) {
            throw new BusinessConflictException("生产成本关单记录已反关单，请刷新后重试");
        }
        return entry;
    }

    private void validateBusinessDate(ProductionOrderEntity order, LocalDate businessDate) {
        LocalDate latest = latestActivityDate(order);
        if (latest != null && businessDate.isBefore(latest)) {
            throw new IllegalArgumentException("成本关单日期不能早于最近生产成本业务日期 " + latest);
        }
    }

    private LocalDate latestActivityDate(ProductionOrderEntity order) {
        LocalDate latest = costEntryMapper.selectList(new LambdaQueryWrapper<ProductionCostEntryEntity>()
                        .eq(ProductionCostEntryEntity::getCompanyId, order.getCompanyId())
                        .eq(ProductionCostEntryEntity::getAccountBookId, order.getAccountBookId())
                        .eq(ProductionCostEntryEntity::getOrderId, order.getId())
                        .eq(ProductionCostEntryEntity::getDeletedFlag, 0)
                        .orderByDesc(ProductionCostEntryEntity::getBusinessDate)
                        .last("limit 1"))
                .stream().findFirst().map(ProductionCostEntryEntity::getBusinessDate).orElse(null);
        LocalDate completionDate = completionMapper.selectList(new LambdaQueryWrapper<ProductionCompletionEntity>()
                        .eq(ProductionCompletionEntity::getCompanyId, order.getCompanyId())
                        .eq(ProductionCompletionEntity::getAccountBookId, order.getAccountBookId())
                        .eq(ProductionCompletionEntity::getOrderId, order.getId())
                        .orderByDesc(ProductionCompletionEntity::getCompletionDate)
                        .last("limit 1"))
                .stream().findFirst().map(ProductionCompletionEntity::getCompletionDate).orElse(null);
        if (latest == null) return completionDate;
        if (completionDate == null) return latest;
        return latest.isAfter(completionDate) ? latest : completionDate;
    }

    private BigDecimal unsettledVariance(ProductionOrderEntity order) {
        BigDecimal input = ScalePrecision.zeroDefault(order.getIssuedAmount())
                .add(ScalePrecision.zeroDefault(order.getLaborAmount()))
                .add(ScalePrecision.zeroDefault(order.getOverheadAmount()));
        BigDecimal finished = ScalePrecision.zeroDefault(order.getFinishedMaterialAmount())
                .add(ScalePrecision.zeroDefault(order.getFinishedLaborAmount()))
                .add(ScalePrecision.zeroDefault(order.getFinishedOverheadAmount()));
        return ScalePrecision.amount(input
                .subtract(finished)
                .subtract(ScalePrecision.zeroDefault(order.getSettledVarianceAmount())));
    }

    private ProductionCostCloseEntity insertEntry(
            ProductionOrderEntity order,
            LocalDate businessDate,
            BigDecimal amount,
            Long reversalOfCloseId,
            String reason,
            String remark,
            AuditMetadata audit
    ) {
        LocalDateTime now = audit.now();
        ProductionCostCloseEntity entry = new ProductionCostCloseEntity();
        entry.setCompanyId(audit.companyId());
        entry.setAccountBookId(audit.accountBookId());
        entry.setCloseNo(sequenceNumberGenerator.nextNumber(BIZ_TYPE, "生产成本关单", businessDate));
        entry.setOrderId(order.getId());
        entry.setBusinessDate(businessDate);
        entry.setAmount(ScalePrecision.amount(amount));
        entry.setStatus(ENTRY_POSTED);
        entry.setReversalOfCloseId(reversalOfCloseId);
        entry.setDeletedFlag(0);
        entry.setReason(reason);
        entry.setRemark(remark);
        entry.setCreatedBy(audit.userId());
        entry.setCreatedTime(now);
        entry.setUpdatedBy(audit.userId());
        entry.setUpdatedTime(now);
        entry.setVersion(0);
        closeMapper.insert(entry);
        return entry;
    }

    private void persistOrder(ProductionOrderEntity order, AuditMetadata audit) {
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(audit.now());
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LambdaQueryWrapper<ProductionCostCloseEntity> scope(ProductionOrderEntity order) {
        return new LambdaQueryWrapper<ProductionCostCloseEntity>()
                .eq(ProductionCostCloseEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionCostCloseEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionCostCloseEntity::getOrderId, order.getId())
                .eq(ProductionCostCloseEntity::getDeletedFlag, 0);
    }

    private List<ProductionCostCloseEntity> selectEntries(ProductionOrderEntity order) {
        return closeMapper.selectList(scope(order)
                .orderByDesc(ProductionCostCloseEntity::getBusinessDate)
                .orderByDesc(ProductionCostCloseEntity::getCreatedTime)
                .orderByDesc(ProductionCostCloseEntity::getId));
    }

    private ProductionCostCloseResponse findResponse(List<ProductionCostCloseEntity> entries, Long id) {
        return toResponses(entries).stream()
                .filter(response -> Objects.equals(response.id(), id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("生产成本关单结果不存在"));
    }

    private List<ProductionCostCloseResponse> toResponses(List<ProductionCostCloseEntity> entries) {
        Map<Long, ProductionCostCloseEntity> byId = new LinkedHashMap<>();
        Map<Long, ProductionCostCloseEntity> reversalByOriginal = new LinkedHashMap<>();
        for (ProductionCostCloseEntity entry : entries) {
            byId.put(entry.getId(), entry);
            if (entry.getReversalOfCloseId() != null) {
                reversalByOriginal.put(entry.getReversalOfCloseId(), entry);
            }
        }
        return entries.stream().map(entry -> {
            ProductionCostCloseEntity reversalOf = byId.get(entry.getReversalOfCloseId());
            ProductionCostCloseEntity reversal = reversalByOriginal.get(entry.getId());
            return new ProductionCostCloseResponse(
                    entry.getId(), entry.getCloseNo(), entry.getOrderId(), entry.getBusinessDate(),
                    ScalePrecision.amount(entry.getAmount()), entry.getStatus(),
                    entry.getReversalOfCloseId() == null ? "CLOSE" : "REVERSAL",
                    entry.getReversalOfCloseId(), reversalOf == null ? null : reversalOf.getCloseNo(),
                    reversal == null ? null : reversal.getId(), reversal == null ? null : reversal.getCloseNo(),
                    entry.getReason(), entry.getRemark(), entry.getCreatedTime()
            );
        }).toList();
    }
}
