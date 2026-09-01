package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.web.ContractLineResponse;
import com.tuowei.erp.commercial.contract.web.ContractPageQuery;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ContractDataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
public class ContractQueryService {
    private final ContractMapper contractMapper;
    private final ContractLineMapper contractLineMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final CurrentUserContext currentUserContext;
    private final ScopedUserResolver scopedUserResolver;
    private final ContractDataScopeService contractDataScopeService;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;

    @Autowired
    public ContractQueryService(ContractMapper contractMapper, ContractLineMapper contractLineMapper,
                                CustomerMapper customerMapper, SupplierMapper supplierMapper, ProductMapper productMapper,
                                UserMapper userMapper, CurrentUserContext currentUserContext, ScopedUserResolver scopedUserResolver,
                                SalesOrderMapper salesOrderMapper, SalesOrderLineMapper salesOrderLineMapper,
                                PurchaseOrderMapper purchaseOrderMapper, PurchaseOrderLineMapper purchaseOrderLineMapper,
                                ContractDataScopeService contractDataScopeService) {
        this.contractMapper = contractMapper;
        this.contractLineMapper = contractLineMapper;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.currentUserContext = currentUserContext;
        this.scopedUserResolver = scopedUserResolver;
        this.contractDataScopeService = contractDataScopeService;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
    }

    /** Backward-compatible constructor for isolated query tests and integrations. */
    public ContractQueryService(ContractMapper contractMapper, ContractLineMapper contractLineMapper,
                                CustomerMapper customerMapper, SupplierMapper supplierMapper, ProductMapper productMapper,
                                UserMapper userMapper, CurrentUserContext currentUserContext, ScopedUserResolver scopedUserResolver,
                                SalesOrderMapper salesOrderMapper, SalesOrderLineMapper salesOrderLineMapper,
                                PurchaseOrderMapper purchaseOrderMapper, PurchaseOrderLineMapper purchaseOrderLineMapper) {
        this(contractMapper, contractLineMapper, customerMapper, supplierMapper, productMapper, userMapper,
                currentUserContext, scopedUserResolver, salesOrderMapper, salesOrderLineMapper,
                purchaseOrderMapper, purchaseOrderLineMapper, new ContractDataScopeService());
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> list(ContractPageQuery query) {
        ContractPageQuery safeQuery = query == null ? new ContractPageQuery() : query;
        CurrentUser user = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        long pageNo = PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo() == null ? null : safeQuery.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize() == null ? null : safeQuery.getPageSize().intValue());
        LambdaQueryWrapper<ContractEntity> wrapper = listWrapper(safeQuery, user);
        ScopedUserResolver.ScopedUserIds scopedUserIds = snapshot.hasAllScope()
                ? new ScopedUserResolver.ScopedUserIds(Set.of(), Set.of())
                : scopedUserResolver.resolve(user, snapshot);
        wrapper = contractDataScopeService.applyContractScope(
                wrapper, user, snapshot, scopedUserIds.deptUserIds(), scopedUserIds.postUserIds());
        Page<ContractEntity> result = contractMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, String> customers = customerNames(result.getRecords());
        Map<Long, String> suppliers = supplierNames(result.getRecords());
        List<ContractResponse> records = result.getRecords().stream()
                .map(entity -> toResponse(entity, List.of(), nameFor(customers, entity.getCustomerId()),
                        nameFor(suppliers, entity.getSupplierId()), Map.of()))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional(readOnly = true)
    public ContractResponse detail(Long id) {
        ContractEntity entity = requireContract(id);
        List<ContractLineEntity> lines = loadLines(entity);
        Map<Long, ProductEntity> products = products(lines);
        return toResponse(entity, lines, customerName(entity.getCustomerId()), supplierName(entity.getSupplierId()), products);
    }

    ContractEntity requireContract(Long id) {
        ContractEntity entity = contractMapper.selectById(id);
        CurrentUser user = currentUserContext.requireCurrentUser();
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), user.companyId())
                || !Objects.equals(entity.getAccountBookId(), user.accountBookId())) {
            throw new IllegalArgumentException("合同不存在");
        }
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        boolean visibleBySelf = snapshot.selfScoped() && Objects.equals(entity.getCreatedBy(), user.userId());
        UserEntity creator = snapshot.hasAllScope() || visibleBySelf || entity.getCreatedBy() == null
                ? null
                : userMapper.selectById(entity.getCreatedBy());
        contractDataScopeService.assertCanViewContract(
                entity,
                user,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
        return entity;
    }

    List<ContractLineEntity> loadLines(ContractEntity contract) {
        return contractLineMapper.selectList(new LambdaQueryWrapper<ContractLineEntity>()
                .eq(ContractLineEntity::getCompanyId, contract.getCompanyId())
                .eq(ContractLineEntity::getAccountBookId, contract.getAccountBookId())
                .eq(ContractLineEntity::getContractId, contract.getId())
                .eq(ContractLineEntity::getDeletedFlag, 0)
                .orderByAsc(ContractLineEntity::getLineNo));
    }

    private LambdaQueryWrapper<ContractEntity> listWrapper(ContractPageQuery query, CurrentUser user) {
        LambdaQueryWrapper<ContractEntity> wrapper = new LambdaQueryWrapper<ContractEntity>()
                .eq(ContractEntity::getCompanyId, user.companyId())
                .eq(ContractEntity::getAccountBookId, user.accountBookId())
                .eq(ContractEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ContractEntity::getContractNo, keyword).or().like(ContractEntity::getContractName, keyword));
        }
        if (StringUtils.hasText(query.getContractType())) wrapper.eq(ContractEntity::getContractType, normalize(query.getContractType()));
        if (StringUtils.hasText(query.getStatus())) wrapper.eq(ContractEntity::getStatus, normalize(query.getStatus()));
        if (query.getCustomerId() != null) wrapper.eq(ContractEntity::getCustomerId, query.getCustomerId());
        if (query.getSupplierId() != null) wrapper.eq(ContractEntity::getSupplierId, query.getSupplierId());
        if (query.getEffectiveFrom() != null) wrapper.and(w -> w.isNull(ContractEntity::getEffectiveTo).or().ge(ContractEntity::getEffectiveTo, query.getEffectiveFrom()));
        if (query.getEffectiveTo() != null) wrapper.le(ContractEntity::getEffectiveFrom, query.getEffectiveTo());
        return wrapper.orderByDesc(ContractEntity::getSignedDate).orderByDesc(ContractEntity::getId);
    }

    private ContractResponse toResponse(ContractEntity entity, List<ContractLineEntity> lines, String customerName,
                                        String supplierName, Map<Long, ProductEntity> products) {
        Map<Long, BigDecimal> committed = committedQuantities(entity, lines);
        return new ContractResponse(entity.getId(), entity.getContractNo(), entity.getContractType(), entity.getCustomerId(), customerName,
                entity.getSupplierId(), supplierName, entity.getContractName(), entity.getSignedDate(), entity.getEffectiveFrom(),
                entity.getEffectiveTo(), entity.getStatus(), entity.getTotalAmount(), entity.getRemark(), lines.stream().map(line -> {
                            ProductEntity product = products.get(line.getProductId());
                    return new ContractLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                            product == null ? null : product.getProductCode(), product == null ? null : product.getProductName(),
                            line.getQuantity(), committed.getOrDefault(line.getId(), BigDecimal.ZERO),
                            line.getFulfilledQuantity(), line.getUnitPrice(), line.getAmount(), line.getRemark());
                }).toList());
    }

    private Map<Long, BigDecimal> committedQuantities(ContractEntity contract, List<ContractLineEntity> lines) {
        List<Long> lineIds = lines.stream().map(ContractLineEntity::getId).toList();
        if (lineIds.isEmpty()) return Map.of();
        return "SALES".equals(contract.getContractType())
                ? committedSalesQuantities(contract, lineIds)
                : committedPurchaseQuantities(contract, lineIds);
    }

    private Map<Long, BigDecimal> committedSalesQuantities(ContractEntity contract, List<Long> lineIds) {
        List<SalesOrderLineEntity> orderLines = salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, contract.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, contract.getAccountBookId())
                .in(SalesOrderLineEntity::getContractLineId, lineIds));
        if (orderLines.isEmpty()) return Map.of();
        Map<Long, SalesOrderEntity> orders = salesOrderMapper.selectBatchIds(orderLines.stream()
                        .map(SalesOrderLineEntity::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity()));
        Map<Long, BigDecimal> result = new java.util.HashMap<>();
        for (SalesOrderLineEntity line : orderLines) {
            SalesOrderEntity order = orders.get(line.getOrderId());
            if (order != null && Objects.equals(order.getContractId(), contract.getId())
                    && !"CANCELLED".equals(order.getStatus())) {
                result.merge(line.getContractLineId(), line.getQty(), BigDecimal::add);
            }
        }
        return result;
    }

    private Map<Long, BigDecimal> committedPurchaseQuantities(ContractEntity contract, List<Long> lineIds) {
        List<PurchaseOrderLineEntity> orderLines = purchaseOrderLineMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                .eq(PurchaseOrderLineEntity::getCompanyId, contract.getCompanyId())
                .eq(PurchaseOrderLineEntity::getAccountBookId, contract.getAccountBookId())
                .in(PurchaseOrderLineEntity::getContractLineId, lineIds));
        if (orderLines.isEmpty()) return Map.of();
        Map<Long, PurchaseOrderEntity> orders = purchaseOrderMapper.selectBatchIds(orderLines.stream()
                        .map(PurchaseOrderLineEntity::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(PurchaseOrderEntity::getId, Function.identity()));
        Map<Long, BigDecimal> result = new java.util.HashMap<>();
        for (PurchaseOrderLineEntity line : orderLines) {
            PurchaseOrderEntity order = orders.get(line.getOrderId());
            if (order != null && Objects.equals(order.getContractId(), contract.getId())
                    && !"CANCELLED".equals(order.getStatus())) {
                result.merge(line.getContractLineId(), line.getQty(), BigDecimal::add);
            }
        }
        return result;
    }

    private Map<Long, ProductEntity> products(List<ContractLineEntity> lines) {
        List<Long> ids = lines.stream().map(ContractLineEntity::getProductId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return productMapper.selectBatchIds(ids).stream()
                .filter(entity -> inTenant(entity.getCompanyId(), entity.getAccountBookId(), entity.getDeletedFlag()))
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
    }

    private Map<Long, String> customerNames(List<ContractEntity> records) {
        List<Long> ids = records.stream().map(ContractEntity::getCustomerId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return customerMapper.selectBatchIds(ids).stream()
                .filter(entity -> inTenant(entity.getCompanyId(), entity.getAccountBookId(), entity.getDeletedFlag()))
                .collect(Collectors.toMap(CustomerEntity::getId, CustomerEntity::getCustomerName));
    }

    private Map<Long, String> supplierNames(List<ContractEntity> records) {
        List<Long> ids = records.stream().map(ContractEntity::getSupplierId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return supplierMapper.selectBatchIds(ids).stream()
                .filter(entity -> inTenant(entity.getCompanyId(), entity.getAccountBookId(), entity.getDeletedFlag()))
                .collect(Collectors.toMap(SupplierEntity::getId, SupplierEntity::getSupplierName));
    }

    private String customerName(Long id) { CustomerEntity entity = id == null ? null : customerMapper.selectById(id); return entity != null && inTenant(entity.getCompanyId(), entity.getAccountBookId(), entity.getDeletedFlag()) ? entity.getCustomerName() : null; }
    private String supplierName(Long id) { SupplierEntity entity = id == null ? null : supplierMapper.selectById(id); return entity != null && inTenant(entity.getCompanyId(), entity.getAccountBookId(), entity.getDeletedFlag()) ? entity.getSupplierName() : null; }
    private String nameFor(Map<Long, String> names, Long id) { return id == null ? null : names.get(id); }
    private boolean inTenant(Long companyId, Long accountBookId, Integer deletedFlag) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Objects.equals(companyId, user.companyId()) && Objects.equals(accountBookId, user.accountBookId())
                && deletedFlag != null && deletedFlag == 0;
    }
    private String normalize(String value) { return value.trim().toUpperCase(Locale.ROOT); }
}
