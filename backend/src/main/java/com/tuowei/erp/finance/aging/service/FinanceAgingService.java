package com.tuowei.erp.finance.aging.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.aging.web.FinanceAgingBucketResponse;
import com.tuowei.erp.finance.aging.web.FinanceAgingOpenItemResponse;
import com.tuowei.erp.finance.aging.web.FinanceAgingSummaryResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应收/应付账龄分析（只读）。
 *
 * <p>口径：
 * <ul>
 *   <li>未结单据：status 不在 SETTLED/CANCELLED/CLOSED，且剩余金额 &gt; 0；</li>
 *   <li>账龄天数：asOfDate - dueDate（无 dueDate 时回退 bizDate；负值按 0）；</li>
 *   <li>分段：0-30 / 31-60 / 61-90 / 90+。</li>
 * </ul>
 */
@Service
public class FinanceAgingService {

    private static final Set<String> CLOSED_STATUSES = Set.of("SETTLED", "CANCELLED", "CLOSED");
    private static final int OVERDUE_TOP_N = 20;

    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public FinanceAgingService(
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            CustomerMapper customerMapper,
            SupplierMapper supplierMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public FinanceAgingSummaryResponse summary(LocalDate asOfDate) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;

        List<ReceivableEntity> openReceivables = receivableMapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, audit.companyId())
                .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceivableEntity::getDeletedFlag, 0)
                .notIn(ReceivableEntity::getStatus, CLOSED_STATUSES));

        List<PayableEntity> openPayables = payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, audit.companyId())
                .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                .eq(PayableEntity::getDeletedFlag, 0)
                .notIn(PayableEntity::getStatus, CLOSED_STATUSES));

        Map<String, BucketAgg> arBuckets = emptyBuckets();
        Map<String, BucketAgg> apBuckets = emptyBuckets();
        List<FinanceAgingOpenItemResponse> arItems = new ArrayList<>();
        List<FinanceAgingOpenItemResponse> apItems = new ArrayList<>();

        Map<Long, String> customerNames = loadCustomerNames(openReceivables, audit);
        Map<Long, String> supplierNames = loadSupplierNames(openPayables, audit);

        for (ReceivableEntity entity : openReceivables) {
            BigDecimal remaining = remaining(entity.getOriginalAmount(), entity.getSettledAmount());
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate dueDate = effectiveDueDate(entity.getDueDate(), entity.getBizDate());
            long days = agingDays(dueDate, asOf);
            String bucket = bucketCode(days);
            arBuckets.get(bucket).add(remaining);
            arItems.add(new FinanceAgingOpenItemResponse(
                    "RECEIVABLE",
                    entity.getId(),
                    entity.getReceivableNo(),
                    entity.getCustomerId(),
                    customerNames.get(entity.getCustomerId()),
                    entity.getBizDate(),
                    dueDate,
                    days,
                    bucket,
                    remaining,
                    entity.getStatus()
            ));
        }

        for (PayableEntity entity : openPayables) {
            BigDecimal remaining = remaining(entity.getOriginalAmount(), entity.getSettledAmount());
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate dueDate = effectiveDueDate(entity.getDueDate(), entity.getBizDate());
            long days = agingDays(dueDate, asOf);
            String bucket = bucketCode(days);
            apBuckets.get(bucket).add(remaining);
            apItems.add(new FinanceAgingOpenItemResponse(
                    "PAYABLE",
                    entity.getId(),
                    entity.getPayableNo(),
                    entity.getSupplierId(),
                    supplierNames.get(entity.getSupplierId()),
                    entity.getBizDate(),
                    dueDate,
                    days,
                    bucket,
                    remaining,
                    entity.getStatus()
            ));
        }

        Comparator<FinanceAgingOpenItemResponse> byDaysDesc =
                Comparator.comparingLong(FinanceAgingOpenItemResponse::agingDays).reversed()
                        .thenComparing(FinanceAgingOpenItemResponse::remainingAmount, Comparator.reverseOrder());

        List<FinanceAgingOpenItemResponse> overdueAr = arItems.stream()
                .filter(item -> item.agingDays() > 0)
                .sorted(byDaysDesc)
                .limit(OVERDUE_TOP_N)
                .toList();
        List<FinanceAgingOpenItemResponse> overdueAp = apItems.stream()
                .filter(item -> item.agingDays() > 0)
                .sorted(byDaysDesc)
                .limit(OVERDUE_TOP_N)
                .toList();

        return new FinanceAgingSummaryResponse(
                asOf,
                sumBuckets(arBuckets),
                sumBuckets(apBuckets),
                toBucketList(arBuckets),
                toBucketList(apBuckets),
                overdueAr,
                overdueAp
        );
    }

    private Map<Long, String> loadCustomerNames(List<ReceivableEntity> receivables, AuditMetadata audit) {
        Set<Long> ids = receivables.stream()
                .map(ReceivableEntity::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectBatchIds(ids).stream()
                .filter(c -> Objects.equals(c.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(CustomerEntity::getId, CustomerEntity::getCustomerName, (a, b) -> a, HashMap::new));
    }

    private Map<Long, String> loadSupplierNames(List<PayableEntity> payables, AuditMetadata audit) {
        Set<Long> ids = payables.stream()
                .map(PayableEntity::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(ids).stream()
                .filter(s -> Objects.equals(s.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(SupplierEntity::getId, SupplierEntity::getSupplierName, (a, b) -> a, HashMap::new));
    }

    private LocalDate effectiveDueDate(LocalDate dueDate, LocalDate bizDate) {
        return dueDate != null ? dueDate : bizDate;
    }

    private long agingDays(LocalDate dueDate, LocalDate asOf) {
        if (dueDate == null) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(dueDate, asOf);
        return Math.max(days, 0L);
    }

    private String bucketCode(long days) {
        if (days <= 30) {
            return "D0_30";
        }
        if (days <= 60) {
            return "D31_60";
        }
        if (days <= 90) {
            return "D61_90";
        }
        return "D90_PLUS";
    }

    private Map<String, BucketAgg> emptyBuckets() {
        Map<String, BucketAgg> map = new HashMap<>();
        map.put("D0_30", new BucketAgg("D0_30", "0-30 天", 0, 30));
        map.put("D31_60", new BucketAgg("D31_60", "31-60 天", 31, 60));
        map.put("D61_90", new BucketAgg("D61_90", "61-90 天", 61, 90));
        map.put("D90_PLUS", new BucketAgg("D90_PLUS", "90 天以上", 91, null));
        return map;
    }

    private List<FinanceAgingBucketResponse> toBucketList(Map<String, BucketAgg> buckets) {
        return List.of("D0_30", "D31_60", "D61_90", "D90_PLUS").stream()
                .map(buckets::get)
                .map(BucketAgg::toResponse)
                .toList();
    }

    private BigDecimal sumBuckets(Map<String, BucketAgg> buckets) {
        return buckets.values().stream()
                .map(BucketAgg::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))
        );
    }

    private static final class BucketAgg {
        private final String code;
        private final String label;
        private final int minDays;
        private final Integer maxDays;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;

        private BucketAgg(String code, String label, int minDays, Integer maxDays) {
            this.code = code;
            this.label = label;
            this.minDays = minDays;
            this.maxDays = maxDays;
        }

        private void add(BigDecimal value) {
            count++;
            amount = ScalePrecision.amount(amount.add(value));
        }

        private BigDecimal amount() {
            return amount;
        }

        private FinanceAgingBucketResponse toResponse() {
            return new FinanceAgingBucketResponse(code, label, minDays, maxDays, count, amount);
        }
    }
}
