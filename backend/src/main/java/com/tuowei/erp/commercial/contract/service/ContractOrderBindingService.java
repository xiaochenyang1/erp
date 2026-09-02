package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ContractOrderBindingService {
    private final ContractMapper contractMapper;
    private final ContractQueryService contractQueryService;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;

    public ContractOrderBindingService(ContractMapper contractMapper, ContractQueryService contractQueryService,
                                       SalesOrderMapper salesOrderMapper, SalesOrderLineMapper salesOrderLineMapper,
                                       PurchaseOrderMapper purchaseOrderMapper, PurchaseOrderLineMapper purchaseOrderLineMapper) {
        this.contractMapper = contractMapper;
        this.contractQueryService = contractQueryService;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
    }

    public void validateSales(Long contractId, Long customerId, LocalDate orderDate,
                              List<SalesOrderLineRequest> requestedLines, Long currentOrderId, AuditMetadata audit) {
        if (contractId == null) {
            requireNoOrphanLineBindings(requestedLines.stream().map(SalesOrderLineRequest::contractLineId).toList());
            return;
        }
        ContractContext context = lockAndRequire(contractId, "SALES", customerId, orderDate, audit);
        validateLines(context, requestedLines.stream().map(line -> new RequestedLine(
                line.contractLineId(), line.productId(), line.qty(), line.price())).toList(),
                committedSalesQuantities(context.lineIds(), currentOrderId, audit), "销售");
    }

    public void validatePurchase(Long contractId, Long supplierId, LocalDate orderDate,
                                 List<PurchaseOrderLineRequest> requestedLines, Long currentOrderId, AuditMetadata audit) {
        if (contractId == null) {
            requireNoOrphanLineBindings(requestedLines.stream().map(PurchaseOrderLineRequest::contractLineId).toList());
            return;
        }
        ContractContext context = lockAndRequire(contractId, "PURCHASE", supplierId, orderDate, audit);
        validateLines(context, requestedLines.stream().map(line -> new RequestedLine(
                line.contractLineId(), line.productId(), line.qty(), line.price())).toList(),
                committedPurchaseQuantities(context.lineIds(), currentOrderId, audit), "采购");
    }

    private ContractContext lockAndRequire(Long contractId, String type, Long partyId,
                                           LocalDate orderDate, AuditMetadata audit) {
        ContractEntity contract = contractQueryService.requireContract(contractId);
        if (!"ACTIVE".equals(contract.getStatus())) throw new IllegalArgumentException("仅生效中的合同可生成订单");
        if (!type.equals(contract.getContractType())) throw new IllegalArgumentException("合同类型与订单类型不匹配");
        Long contractPartyId = "SALES".equals(type) ? contract.getCustomerId() : contract.getSupplierId();
        if (!Objects.equals(contractPartyId, partyId)) throw new IllegalArgumentException("订单往来单位与合同不一致");
        if (orderDate == null || orderDate.isBefore(contract.getEffectiveFrom())
                || contract.getEffectiveTo() != null && orderDate.isAfter(contract.getEffectiveTo())) {
            throw new IllegalArgumentException("订单日期不在合同有效期内");
        }
        contract.setUpdatedBy(audit.userId());
        contract.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(contractMapper.updateById(contract), "合同正在生成其他订单，请刷新后重试");
        List<ContractLineEntity> lines = contractQueryService.loadLines(contract);
        return new ContractContext(contract, lines, lines.stream().map(ContractLineEntity::getId).toList());
    }

    private void validateLines(ContractContext context, List<RequestedLine> requestedLines,
                               Map<Long, BigDecimal> committed, String orderType) {
        if (requestedLines.isEmpty()) throw new IllegalArgumentException("订单明细不能为空");
        Map<Long, ContractLineEntity> contractLines = new HashMap<>();
        context.lines().forEach(line -> contractLines.put(line.getId(), line));
        Set<Long> seen = new HashSet<>();
        for (RequestedLine requested : requestedLines) {
            if (requested.contractLineId() == null || !seen.add(requested.contractLineId())) {
                throw new IllegalArgumentException(orderType + "订单必须逐行唯一关联合同明细");
            }
            ContractLineEntity contractLine = contractLines.get(requested.contractLineId());
            if (contractLine == null || !Objects.equals(contractLine.getProductId(), requested.productId())) {
                throw new IllegalArgumentException("订单商品与合同明细不一致");
            }
            if (ScalePrecision.amount(contractLine.getUnitPrice()).compareTo(ScalePrecision.amount(requested.price())) != 0) {
                throw new IllegalArgumentException("订单价格必须与合同约定价格一致");
            }
            BigDecimal totalCommitted = ScalePrecision.quantity(committed.getOrDefault(
                    contractLine.getId(), BigDecimal.ZERO).add(requested.quantity()));
            if (totalCommitted.compareTo(ScalePrecision.quantity(contractLine.getQuantity())) > 0) {
                throw new IllegalArgumentException("订单数量超过合同明细剩余可下单数量");
            }
        }
    }

    private Map<Long, BigDecimal> committedSalesQuantities(List<Long> contractLineIds, Long currentOrderId, AuditMetadata audit) {
        if (contractLineIds.isEmpty()) return Map.of();
        LambdaQueryWrapper<SalesOrderLineEntity> wrapper = new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, audit.companyId())
                .eq(SalesOrderLineEntity::getAccountBookId, audit.accountBookId())
                .in(SalesOrderLineEntity::getContractLineId, contractLineIds);
        if (currentOrderId != null) wrapper.ne(SalesOrderLineEntity::getOrderId, currentOrderId);
        List<SalesOrderLineEntity> lines = salesOrderLineMapper.selectList(wrapper);
        if (lines.isEmpty()) return Map.of();
        Map<Long, SalesOrderEntity> orders = salesOrderMapper.selectBatchIds(
                lines.stream().map(SalesOrderLineEntity::getOrderId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderEntity::getId, order -> order));
        Map<Long, BigDecimal> result = new HashMap<>();
        for (SalesOrderLineEntity line : lines) {
            SalesOrderEntity order = orders.get(line.getOrderId());
            if (isActiveOrder(order, audit, line.getContractLineId())) {
                result.merge(line.getContractLineId(), ScalePrecision.quantity(line.getQty()), BigDecimal::add);
            }
        }
        return result;
    }

    private Map<Long, BigDecimal> committedPurchaseQuantities(List<Long> contractLineIds, Long currentOrderId, AuditMetadata audit) {
        if (contractLineIds.isEmpty()) return Map.of();
        LambdaQueryWrapper<PurchaseOrderLineEntity> wrapper = new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                .eq(PurchaseOrderLineEntity::getCompanyId, audit.companyId())
                .eq(PurchaseOrderLineEntity::getAccountBookId, audit.accountBookId())
                .in(PurchaseOrderLineEntity::getContractLineId, contractLineIds);
        if (currentOrderId != null) wrapper.ne(PurchaseOrderLineEntity::getOrderId, currentOrderId);
        List<PurchaseOrderLineEntity> lines = purchaseOrderLineMapper.selectList(wrapper);
        if (lines.isEmpty()) return Map.of();
        Map<Long, PurchaseOrderEntity> orders = purchaseOrderMapper.selectBatchIds(
                lines.stream().map(PurchaseOrderLineEntity::getOrderId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseOrderEntity::getId, order -> order));
        Map<Long, BigDecimal> result = new HashMap<>();
        for (PurchaseOrderLineEntity line : lines) {
            PurchaseOrderEntity order = orders.get(line.getOrderId());
            if (isActiveOrder(order, audit, line.getContractLineId())) {
                result.merge(line.getContractLineId(), ScalePrecision.quantity(line.getQty()), BigDecimal::add);
            }
        }
        return result;
    }

    private boolean isActiveOrder(SalesOrderEntity order, AuditMetadata audit, Long ignoredLineId) {
        return order != null && Objects.equals(order.getCompanyId(), audit.companyId())
                && Objects.equals(order.getAccountBookId(), audit.accountBookId())
                && order.getDeletedFlag() != null && order.getDeletedFlag() == 0
                && !"CANCELLED".equals(order.getStatus());
    }

    private boolean isActiveOrder(PurchaseOrderEntity order, AuditMetadata audit, Long ignoredLineId) {
        return order != null && Objects.equals(order.getCompanyId(), audit.companyId())
                && Objects.equals(order.getAccountBookId(), audit.accountBookId())
                && order.getDeletedFlag() != null && order.getDeletedFlag() == 0
                && !"CANCELLED".equals(order.getStatus());
    }

    private void requireNoOrphanLineBindings(List<Long> lineIds) {
        if (lineIds.stream().anyMatch(Objects::nonNull)) {
            throw new IllegalArgumentException("合同明细关联必须同时填写合同ID");
        }
    }

    private record ContractContext(ContractEntity contract, List<ContractLineEntity> lines, List<Long> lineIds) {}
    private record RequestedLine(Long contractLineId, Long productId, BigDecimal quantity, BigDecimal price) {}
}
