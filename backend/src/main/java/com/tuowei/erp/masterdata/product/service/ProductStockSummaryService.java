package com.tuowei.erp.masterdata.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.web.ProductStockSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductStockSummaryService {

    private final ProductMapper productMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductStockSummaryService(ProductMapper productMapper, InventoryBalanceMapper balanceMapper, AuditMetadataFactory auditMetadataFactory) {
        this.productMapper = productMapper;
        this.balanceMapper = balanceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public ProductStockSummaryResponse summary(Long productId) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductEntity product = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getId, productId)
                .eq(ProductEntity::getCompanyId, audit.companyId())
                .eq(ProductEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        List<InventoryBalanceEntity> balances = balanceMapper.selectList(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, audit.companyId())
                .eq(InventoryBalanceEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryBalanceEntity::getProductId, productId));
        BigDecimal onHand = sum(balances, true);
        BigDecimal reserved = sum(balances, false);
        BigDecimal amount = balances.stream().map(InventoryBalanceEntity::getAmountOnHand)
                .map(ScalePrecision::zeroDefault).reduce(BigDecimal.ZERO, BigDecimal::add);
        int warehouses = (int) balances.stream().filter(item -> ScalePrecision.zeroDefault(item.getQtyOnHand()).compareTo(BigDecimal.ZERO) != 0).count();
        return new ProductStockSummaryResponse(productId, warehouses, ScalePrecision.quantity(onHand),
                ScalePrecision.quantity(reserved), ScalePrecision.quantity(onHand.subtract(reserved)), ScalePrecision.amount(amount));
    }

    private BigDecimal sum(List<InventoryBalanceEntity> balances, boolean onHand) {
        return balances.stream().map(item -> onHand ? item.getQtyOnHand() : item.getQtyReserved())
                .map(ScalePrecision::zeroDefault).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
