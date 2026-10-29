package com.tuowei.erp.inventory.serial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.serial.mapper.InventorySerialNumberMapper;
import com.tuowei.erp.inventory.serial.model.InventorySerialNumberEntity;
import com.tuowei.erp.inventory.serial.web.InventorySerialCreateRequest;
import com.tuowei.erp.inventory.serial.web.InventorySerialResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class InventorySerialNumberCommandService {

    private static final String STATUS_IN_STOCK = "IN_STOCK";
    private static final String STATUS_ISSUED = "ISSUED";
    private static final String STATUS_SCRAPPED = "SCRAPPED";

    private final InventorySerialNumberMapper serialNumberMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final InventorySerialNumberQueryService queryService;

    public InventorySerialNumberCommandService(
            InventorySerialNumberMapper serialNumberMapper,
            ProductMapper productMapper,
            AuditMetadataFactory auditMetadataFactory,
            InventorySerialNumberQueryService queryService
    ) {
        this.serialNumberMapper = serialNumberMapper;
        this.productMapper = productMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public InventorySerialResponse create(InventorySerialCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductEntity product = queryService.requireSerialProduct(request.productId(), audit);
        String serialNo = normalizeSerial(request.serialNo());
        ensureUnique(audit, product.getId(), serialNo);
        LocalDateTime now = audit.now();
        InventorySerialNumberEntity entity = new InventorySerialNumberEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setProductId(product.getId());
        entity.setWarehouseId(request.warehouseId());
        entity.setLocationId(request.locationId());
        entity.setSerialNo(serialNo);
        entity.setStatus(STATUS_IN_STOCK);
        entity.setInboundBizType(trimToNull(request.inboundBizType()));
        entity.setInboundBizNo(trimToNull(request.inboundBizNo()));
        entity.setRemark(trimToNull(request.remark()));
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        serialNumberMapper.insert(entity);
        return queryService.toResponse(entity, product);
    }

    @Transactional
    public InventorySerialResponse issue(Long id, String outboundBizType, String outboundBizNo) {
        return transition(id, STATUS_IN_STOCK, STATUS_ISSUED, outboundBizType, outboundBizNo);
    }

    @Transactional
    public InventorySerialResponse scrap(Long id) {
        return transition(id, STATUS_IN_STOCK, STATUS_SCRAPPED, null, null);
    }

    @Transactional
    public void registerInboundSerials(
            Long productId,
            Long warehouseId,
            Long locationId,
            String serialNos,
            String inboundBizType,
            String inboundBizNo,
            BigDecimal qty,
            AuditMetadata audit
    ) {
        ProductEntity product = requireProductForIntegration(productId, audit);
        if (!Integer.valueOf(1).equals(product.getSerialControlled())) {
            if (StringUtils.hasText(serialNos)) {
                throw new IllegalArgumentException("商品未启用序列号管理，不能填写序列号");
            }
            return;
        }
        List<String> serials = parseSerials(serialNos);
        if (serials.isEmpty()) {
            throw new IllegalArgumentException("启用序列号管理的商品入库必须填写序列号");
        }
        if (qty != null && serials.size() != qty.stripTrailingZeros().intValueExact()) {
            throw new IllegalArgumentException("序列号数量必须等于入库数量");
        }
        for (String serial : serials) {
            create(new InventorySerialCreateRequest(
                    productId, warehouseId, locationId, serial, inboundBizType, inboundBizNo, null
            ));
        }
    }

    @Transactional
    public void issueOutboundSerials(
            Long productId,
            String serialNos,
            String outboundBizType,
            String outboundBizNo,
            BigDecimal qty,
            AuditMetadata audit
    ) {
        ProductEntity product = requireProductForIntegration(productId, audit);
        if (!Integer.valueOf(1).equals(product.getSerialControlled())) {
            if (StringUtils.hasText(serialNos)) {
                throw new IllegalArgumentException("商品未启用序列号管理，不能填写序列号");
            }
            return;
        }
        List<String> serials = parseSerials(serialNos);
        if (serials.isEmpty()) {
            throw new IllegalArgumentException("启用序列号管理的商品出库必须填写序列号");
        }
        if (qty != null && serials.size() != qty.stripTrailingZeros().intValueExact()) {
            throw new IllegalArgumentException("序列号数量必须等于出库数量");
        }
        for (String serial : serials) {
            InventorySerialNumberEntity entity = findSerial(productId, serial, audit);
            if (entity == null) {
                throw new IllegalArgumentException("序列号不存在: " + serial);
            }
            if (!STATUS_IN_STOCK.equals(entity.getStatus())) {
                throw new IllegalArgumentException("序列号不在库: " + serial);
            }
            issue(entity.getId(), outboundBizType, outboundBizNo);
        }
    }

    @Transactional
    public void moveInStockSerials(
            Long productId,
            Long toWarehouseId,
            Long toLocationId,
            String serialNos,
            BigDecimal qty,
            AuditMetadata audit
    ) {
        ProductEntity product = requireProductForIntegration(productId, audit);
        if (!Integer.valueOf(1).equals(product.getSerialControlled())) {
            if (StringUtils.hasText(serialNos)) {
                throw new IllegalArgumentException("商品未启用序列号管理，不能填写序列号");
            }
            return;
        }
        List<String> serials = parseSerials(serialNos);
        if (serials.isEmpty()) {
            throw new IllegalArgumentException("启用序列号管理的商品调拨必须填写序列号");
        }
        if (qty != null && serials.size() != qty.stripTrailingZeros().intValueExact()) {
            throw new IllegalArgumentException("序列号数量必须等于调拨数量");
        }
        for (String serial : serials) {
            InventorySerialNumberEntity entity = findSerial(productId, serial, audit);
            if (entity == null) {
                throw new IllegalArgumentException("序列号不存在: " + serial);
            }
            if (!STATUS_IN_STOCK.equals(entity.getStatus())) {
                throw new IllegalArgumentException("序列号不在库: " + serial);
            }
            entity.setWarehouseId(toWarehouseId);
            entity.setLocationId(toLocationId);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(audit.now());
            OptimisticLockGuard.requireUpdated(
                    serialNumberMapper.updateById(entity),
                    "序列号已被其他操作修改，请刷新后重试"
            );
        }
    }

    private InventorySerialResponse transition(
            Long id,
            String from,
            String to,
            String outboundBizType,
            String outboundBizNo
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventorySerialNumberEntity entity = queryService.requireSerial(id, audit);
        if (!from.equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前序列号状态不允许该操作");
        }
        entity.setStatus(to);
        if (STATUS_ISSUED.equals(to)) {
            entity.setOutboundBizType(trimToNull(outboundBizType));
            entity.setOutboundBizNo(trimToNull(outboundBizNo));
        }
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                serialNumberMapper.updateById(entity),
                "序列号已被其他操作修改，请刷新后重试"
        );
        return queryService.toResponse(entity, queryService.requireProduct(entity.getProductId(), audit));
    }

    private InventorySerialNumberEntity findSerial(Long productId, String serial, AuditMetadata audit) {
        return serialNumberMapper.selectOne(new LambdaQueryWrapper<InventorySerialNumberEntity>()
                .eq(InventorySerialNumberEntity::getCompanyId, audit.companyId())
                .eq(InventorySerialNumberEntity::getAccountBookId, audit.accountBookId())
                .eq(InventorySerialNumberEntity::getProductId, productId)
                .eq(InventorySerialNumberEntity::getSerialNo, serial)
                .eq(InventorySerialNumberEntity::getDeletedFlag, 0)
                .last("limit 1"));
    }

    private ProductEntity requireProductForIntegration(Long productId, AuditMetadata audit) {
        return productMapper.selectById(productId);
    }

    private void ensureUnique(AuditMetadata audit, Long productId, String serialNo) {
        Long count = serialNumberMapper.selectCount(new LambdaQueryWrapper<InventorySerialNumberEntity>()
                .eq(InventorySerialNumberEntity::getCompanyId, audit.companyId())
                .eq(InventorySerialNumberEntity::getAccountBookId, audit.accountBookId())
                .eq(InventorySerialNumberEntity::getProductId, productId)
                .eq(InventorySerialNumberEntity::getSerialNo, serialNo)
                .eq(InventorySerialNumberEntity::getDeletedFlag, 0));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("序列号已存在");
        }
    }

    private List<String> parseSerials(String serialNos) {
        if (!StringUtils.hasText(serialNos)) {
            return List.of();
        }
        return Arrays.stream(serialNos.split("[,;\n]"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private String normalizeSerial(String serialNo) {
        if (!StringUtils.hasText(serialNo)) {
            throw new IllegalArgumentException("序列号不能为空");
        }
        return serialNo.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
