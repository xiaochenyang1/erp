package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.inventory.stock.web.CounterpartyRef;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LotGenealogyCounterpartyResolver {

    public record CounterpartyIndex(
            Map<String, CounterpartyRef> bySupplierDocument,
            Map<String, CounterpartyRef> byCustomerDocument
    ) {
        public static CounterpartyIndex empty() {
            return new CounterpartyIndex(Map.of(), Map.of());
        }

        public CounterpartyRef supplierFor(String documentNo) {
            return documentNo == null ? null : bySupplierDocument.get(documentNo);
        }

        public CounterpartyRef customerFor(String documentNo) {
            return documentNo == null ? null : byCustomerDocument.get(documentNo);
        }
    }

    private final PurchaseReceiptMapper receiptMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final SalesDeliveryMapper deliveryMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final CustomerMapper customerMapper;
    private final SalesReturnMapper salesReturnMapper;

    public LotGenealogyCounterpartyResolver(
            PurchaseReceiptMapper receiptMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            SupplierMapper supplierMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            SalesDeliveryMapper deliveryMapper,
            SalesOrderMapper salesOrderMapper,
            CustomerMapper customerMapper,
            SalesReturnMapper salesReturnMapper
    ) {
        this.receiptMapper = receiptMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.supplierMapper = supplierMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.deliveryMapper = deliveryMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.customerMapper = customerMapper;
        this.salesReturnMapper = salesReturnMapper;
    }

    public CounterpartyIndex resolve(
            Collection<String> receiptNos,
            Collection<String> deliveryNos,
            Long companyId,
            Long accountBookId
    ) {
        return new CounterpartyIndex(
                resolveSuppliers(texts(receiptNos), companyId, accountBookId),
                resolveCustomers(texts(deliveryNos), companyId, accountBookId)
        );
    }

    private Map<String, CounterpartyRef> resolveSuppliers(
            Set<String> receiptNos,
            Long companyId,
            Long accountBookId
    ) {
        if (receiptNos.isEmpty()) {
            return Map.of();
        }
        var receipts = receiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getCompanyId, companyId)
                .eq(PurchaseReceiptEntity::getAccountBookId, accountBookId)
                .in(PurchaseReceiptEntity::getReceiptNo, receiptNos));
        var returns = purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturnEntity>()
                .eq(PurchaseReturnEntity::getCompanyId, companyId)
                .eq(PurchaseReturnEntity::getAccountBookId, accountBookId)
                .in(PurchaseReturnEntity::getReturnNo, receiptNos));
        Set<Long> returnReceiptIds = returns.stream()
                .map(PurchaseReturnEntity::getReceiptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!returnReceiptIds.isEmpty()) {
            Map<Long, PurchaseReceiptEntity> receiptsById = receiptMapper.selectList(
                            new LambdaQueryWrapper<PurchaseReceiptEntity>()
                                    .eq(PurchaseReceiptEntity::getCompanyId, companyId)
                                    .eq(PurchaseReceiptEntity::getAccountBookId, accountBookId)
                                    .in(PurchaseReceiptEntity::getId, returnReceiptIds))
                    .stream()
                    .collect(Collectors.toMap(PurchaseReceiptEntity::getId, Function.identity(), (left, right) -> left));
            receipts = java.util.stream.Stream.concat(receipts.stream(), receiptsById.values().stream())
                    .collect(Collectors.toMap(PurchaseReceiptEntity::getId, Function.identity(), (left, right) -> left))
                    .values().stream().toList();
        }
        Set<Long> orderIds = receipts.stream()
                .map(PurchaseReceiptEntity::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PurchaseOrderEntity> orders = purchaseOrderMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrderEntity>()
                                .eq(PurchaseOrderEntity::getCompanyId, companyId)
                                .eq(PurchaseOrderEntity::getAccountBookId, accountBookId)
                                .in(PurchaseOrderEntity::getId, orderIds))
                .stream()
                .collect(Collectors.toMap(PurchaseOrderEntity::getId, Function.identity(), (left, right) -> left));
        Set<Long> supplierIds = orders.values().stream()
                .map(PurchaseOrderEntity::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SupplierEntity> suppliers = supplierIds.isEmpty() ? Map.of()
                : supplierMapper.selectList(new LambdaQueryWrapper<SupplierEntity>()
                                .eq(SupplierEntity::getCompanyId, companyId)
                                .eq(SupplierEntity::getAccountBookId, accountBookId)
                                .in(SupplierEntity::getId, supplierIds))
                        .stream()
                        .collect(Collectors.toMap(SupplierEntity::getId, Function.identity(), (left, right) -> left));

        Map<String, CounterpartyRef> result = new HashMap<>();
        for (PurchaseReceiptEntity receipt : receipts) {
            PurchaseOrderEntity order = orders.get(receipt.getOrderId());
            SupplierEntity supplier = order == null ? null : suppliers.get(order.getSupplierId());
            if (order != null && supplier != null) {
                result.put(receipt.getReceiptNo(), new CounterpartyRef(
                        "SUPPLIER",
                        supplier.getId(),
                        supplier.getSupplierCode(),
                        supplier.getSupplierName(),
                        order.getOrderNo()
                ));
            }
        }
        for (PurchaseReturnEntity returnEntity : returns) {
            PurchaseReceiptEntity receipt = receipts.stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), returnEntity.getReceiptId()))
                    .findFirst().orElse(null);
            PurchaseOrderEntity order = receipt == null ? null : orders.get(receipt.getOrderId());
            SupplierEntity supplier = order == null ? null : suppliers.get(order.getSupplierId());
            if (order != null && supplier != null) {
                result.put(returnEntity.getReturnNo(), new CounterpartyRef(
                        "SUPPLIER", supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName(), order.getOrderNo()));
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, CounterpartyRef> resolveCustomers(
            Set<String> deliveryNos,
            Long companyId,
            Long accountBookId
    ) {
        if (deliveryNos.isEmpty()) {
            return Map.of();
        }
        var deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<SalesDeliveryEntity>()
                .eq(SalesDeliveryEntity::getCompanyId, companyId)
                .eq(SalesDeliveryEntity::getAccountBookId, accountBookId)
                .in(SalesDeliveryEntity::getDeliveryNo, deliveryNos));
        var returns = salesReturnMapper.selectList(new LambdaQueryWrapper<SalesReturnEntity>()
                .eq(SalesReturnEntity::getCompanyId, companyId)
                .eq(SalesReturnEntity::getAccountBookId, accountBookId)
                .in(SalesReturnEntity::getReturnNo, deliveryNos));
        Set<Long> returnDeliveryIds = returns.stream()
                .map(SalesReturnEntity::getDeliveryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!returnDeliveryIds.isEmpty()) {
            Map<Long, SalesDeliveryEntity> deliveriesById = deliveryMapper.selectList(
                            new LambdaQueryWrapper<SalesDeliveryEntity>()
                                    .eq(SalesDeliveryEntity::getCompanyId, companyId)
                                    .eq(SalesDeliveryEntity::getAccountBookId, accountBookId)
                                    .in(SalesDeliveryEntity::getId, returnDeliveryIds))
                    .stream()
                    .collect(Collectors.toMap(SalesDeliveryEntity::getId, Function.identity(), (left, right) -> left));
            deliveries = java.util.stream.Stream.concat(deliveries.stream(), deliveriesById.values().stream())
                    .collect(Collectors.toMap(SalesDeliveryEntity::getId, Function.identity(), (left, right) -> left))
                    .values().stream().toList();
        }
        Set<Long> orderIds = deliveries.stream()
                .map(SalesDeliveryEntity::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SalesOrderEntity> orders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrderEntity>()
                        .eq(SalesOrderEntity::getCompanyId, companyId)
                        .eq(SalesOrderEntity::getAccountBookId, accountBookId)
                        .in(SalesOrderEntity::getId, orderIds))
                .stream()
                .collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity(), (left, right) -> left));
        Set<Long> customerIds = orders.values().stream()
                .map(SalesOrderEntity::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CustomerEntity> customers = customerIds.isEmpty() ? Map.of()
                : customerMapper.selectList(new LambdaQueryWrapper<CustomerEntity>()
                                .eq(CustomerEntity::getCompanyId, companyId)
                                .eq(CustomerEntity::getAccountBookId, accountBookId)
                                .in(CustomerEntity::getId, customerIds))
                        .stream()
                        .collect(Collectors.toMap(CustomerEntity::getId, Function.identity(), (left, right) -> left));

        Map<String, CounterpartyRef> result = new HashMap<>();
        for (SalesDeliveryEntity delivery : deliveries) {
            SalesOrderEntity order = orders.get(delivery.getOrderId());
            CustomerEntity customer = order == null ? null : customers.get(order.getCustomerId());
            if (order != null && customer != null) {
                result.put(delivery.getDeliveryNo(), new CounterpartyRef(
                        "CUSTOMER",
                        customer.getId(),
                        customer.getCustomerCode(),
                        customer.getCustomerName(),
                        order.getOrderNo()
                ));
            }
        }
        for (SalesReturnEntity returnEntity : returns) {
            SalesDeliveryEntity delivery = deliveries.stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), returnEntity.getDeliveryId()))
                    .findFirst().orElse(null);
            SalesOrderEntity order = delivery == null ? null : orders.get(delivery.getOrderId());
            CustomerEntity customer = order == null ? null : customers.get(order.getCustomerId());
            if (order != null && customer != null) {
                result.put(returnEntity.getReturnNo(), new CounterpartyRef(
                        "CUSTOMER", customer.getId(), customer.getCustomerCode(), customer.getCustomerName(), order.getOrderNo()));
            }
        }
        return Map.copyOf(result);
    }

    private static Set<String> texts(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
