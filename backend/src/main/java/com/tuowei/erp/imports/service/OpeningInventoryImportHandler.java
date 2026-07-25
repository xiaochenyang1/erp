package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpeningInventoryImportHandler extends AbstractImportHandler {

    private static final String OPENING_BIZ_TYPE = "OPENING_BALANCE";

    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;

    public OpeningInventoryImportHandler(
            ImportValidationSupport support,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            LocationMapper locationMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService
    ) {
        super(support);
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.locationMapper = locationMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
    }

    @Override
    public String importType() {
        return ImportConstants.OPENING_INVENTORY;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String warehouseCode = support.required(raw, "warehouse_code", errors);
        String productCode = support.required(raw, "product_code", errors);
        String locationCode = support.optionalText(raw, "location_code");
        BigDecimal qtyOnHand = support.quantity(raw, "qty_on_hand", errors);
        BigDecimal amountOnHand = support.amount(raw, "amount_on_hand", errors);
        LocalDate openingDate = support.date(raw, "opening_date", errors);
        String lotNo = support.optionalText(raw, "lot_no");
        LocalDate productionDate = optionalDate(raw, "production_date", errors);
        LocalDate expiryDate = optionalDate(raw, "expiry_date", errors);
        String serialNos = support.optionalText(raw, "serial_nos");
        if (qtyOnHand.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ImportRowErrorResponse("qty_on_hand", "期初库存数量必须大于0"));
        }
        if (amountOnHand.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse("amount_on_hand", "期初库存金额不能小于0"));
        }
        WarehouseEntity warehouse = null;
        ProductEntity product = null;
        LocationEntity location = null;
        if (warehouseCode != null) {
            warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<WarehouseEntity>()
                    .eq(WarehouseEntity::getCompanyId, context.companyId())
                    .eq(WarehouseEntity::getAccountBookId, context.accountBookId())
                    .eq(WarehouseEntity::getWarehouseCode, warehouseCode)
                    .eq(WarehouseEntity::getStatus, "ACTIVE")
                    .eq(WarehouseEntity::getDeletedFlag, 0));
            if (warehouse == null) {
                errors.add(new ImportRowErrorResponse("warehouse_code", "仓库不存在或已停用"));
            }
        }
        if (productCode != null) {
            product = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                    .eq(ProductEntity::getCompanyId, context.companyId())
                    .eq(ProductEntity::getAccountBookId, context.accountBookId())
                    .eq(ProductEntity::getProductCode, productCode)
                    .eq(ProductEntity::getStatus, "ACTIVE")
                    .eq(ProductEntity::getDeletedFlag, 0));
            if (product == null) {
                errors.add(new ImportRowErrorResponse("product_code", "商品不存在或已停用"));
            }
        }
        if (warehouse != null) {
            if (StringUtils.hasText(locationCode)) {
                location = locationMapper.selectOne(new LambdaQueryWrapper<LocationEntity>()
                        .eq(LocationEntity::getCompanyId, context.companyId())
                        .eq(LocationEntity::getAccountBookId, context.accountBookId())
                        .eq(LocationEntity::getWarehouseId, warehouse.getId())
                        .eq(LocationEntity::getLocationCode, locationCode.trim())
                        .eq(LocationEntity::getStatus, "ACTIVE")
                        .eq(LocationEntity::getDeletedFlag, 0)
                        .last("limit 1"));
                if (location == null) {
                    errors.add(new ImportRowErrorResponse("location_code", "库位不存在或已停用"));
                }
            } else {
                location = locationMapper.selectOne(new LambdaQueryWrapper<LocationEntity>()
                        .eq(LocationEntity::getCompanyId, context.companyId())
                        .eq(LocationEntity::getAccountBookId, context.accountBookId())
                        .eq(LocationEntity::getWarehouseId, warehouse.getId())
                        .eq(LocationEntity::getIsDefault, 1)
                        .eq(LocationEntity::getStatus, "ACTIVE")
                        .eq(LocationEntity::getDeletedFlag, 0)
                        .last("limit 1"));
                if (location == null) {
                    errors.add(new ImportRowErrorResponse("location_code", "仓库缺少默认库位，请先维护库位主数据或填写 location_code"));
                }
            }
        }
        List<String> serials = parseSerials(serialNos);
        if (product != null) {
            if (lotControlled(product) && lotNo == null) {
                errors.add(new ImportRowErrorResponse("lot_no", "启用批次管理的商品必须填写批次号"));
            }
            if (shelfLifeControlled(product) && expiryDate == null) {
                errors.add(new ImportRowErrorResponse("expiry_date", "启用效期管理的商品必须填写有效期"));
            }
            if (!lotControlled(product) && (lotNo != null || productionDate != null || expiryDate != null)) {
                errors.add(new ImportRowErrorResponse("lot_no", "未启用批次管理的商品不能填写批次信息"));
            }
            if (serialControlled(product)) {
                if (serials.isEmpty()) {
                    errors.add(new ImportRowErrorResponse("serial_nos", "启用序列号管理的商品必须填写序列号"));
                } else {
                    try {
                        int expected = qtyOnHand.stripTrailingZeros().intValueExact();
                        if (serials.size() != expected) {
                            errors.add(new ImportRowErrorResponse("serial_nos", "序列号数量必须等于期初数量"));
                        }
                    } catch (ArithmeticException ex) {
                        errors.add(new ImportRowErrorResponse("qty_on_hand", "启用序列号管理的商品期初数量必须是整数"));
                    }
                    for (String serial : serials) {
                        support.duplicateInFile(seen(context, "openingSerial"), serial, "serial_nos", errors);
                    }
                }
            } else if (!serials.isEmpty()) {
                errors.add(new ImportRowErrorResponse("serial_nos", "未启用序列号管理的商品不能填写序列号"));
            }
        }
        if (warehouseCode != null && productCode != null) {
            String locationKey = location == null
                    ? (locationCode == null ? "" : locationCode.trim())
                    : location.getLocationCode();
            String duplicateKey = warehouseCode + "|" + productCode + "|" + locationKey
                    + (product != null && lotControlled(product) ? "|" + lotNo : "");
            support.duplicateInFile(seen(context, "openingInventory"), duplicateKey, "product_code", errors);
        }
        if (warehouse != null && product != null && location != null) {
            InventoryBalanceEntity balance = inventoryBalanceMapper.selectOne(new LambdaQueryWrapper<InventoryBalanceEntity>()
                    .eq(InventoryBalanceEntity::getCompanyId, context.companyId())
                    .eq(InventoryBalanceEntity::getAccountBookId, context.accountBookId())
                    .eq(InventoryBalanceEntity::getWarehouseId, warehouse.getId())
                    .eq(InventoryBalanceEntity::getProductId, product.getId())
                    .eq(InventoryBalanceEntity::getLocationId, location.getId())
                    .last("limit 1"));
            if (balance != null && (support.scaleQuantity(balance.getQtyOnHand()).compareTo(BigDecimal.ZERO) != 0
                    || support.scaleAmount(balance.getAmountOnHand()).compareTo(BigDecimal.ZERO) != 0)) {
                errors.add(new ImportRowErrorResponse("product_code", "该仓库库位商品已有库存余额，不能导入期初库存"));
            }
        }
        normalized.put("warehouseId", warehouse == null ? null : warehouse.getId());
        normalized.put("productId", product == null ? null : product.getId());
        normalized.put("locationId", location == null ? null : location.getId());
        normalized.put("qtyOnHand", qtyOnHand);
        normalized.put("amountOnHand", amountOnHand);
        normalized.put("openingDate", openingDate == null ? null : openingDate.toString());
        normalized.put("lotNo", lotNo);
        normalized.put("productionDate", productionDate == null ? null : productionDate.toString());
        normalized.put("expiryDate", expiryDate == null ? null : expiryDate.toString());
        normalized.put("serialNos", serials.isEmpty() ? null : String.join(",", serials));
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        Long normalTxnCount = inventoryTransactionMapper.selectCount(new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, audit.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, audit.accountBookId())
                .ne(InventoryTransactionEntity::getBizType, ImportConstants.OPENING_INVENTORY)
                .ne(InventoryTransactionEntity::getBizType, OPENING_BIZ_TYPE));
        if (exists(normalTxnCount)) {
            throw new IllegalArgumentException("已有正常库存流水，不能再导入期初库存");
        }
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            Long warehouseId = longValue(normalized, "warehouseId");
            Long productId = longValue(normalized, "productId");
            Long locationId = longValue(normalized, "locationId");
            BigDecimal qtyOnHand = decimalValue(normalized, "qtyOnHand");
            String bizNo = "OPEN-INV-" + job.getId();
            inventoryPostingService.postInbound(new InventoryPostingCommand(
                    warehouseId,
                    productId,
                    OPENING_BIZ_TYPE,
                    bizNo,
                    row.getId(),
                    qtyOnHand,
                    decimalValue(normalized, "amountOnHand"),
                    text(normalized, "remark"),
                    dateValue(normalized, "openingDate"),
                    text(normalized, "lotNo"),
                    dateValue(normalized, "productionDate"),
                    dateValue(normalized, "expiryDate"),
                    locationId
            ), audit);
            inventorySerialNumberService.registerInboundSerials(
                    productId,
                    warehouseId,
                    locationId,
                    text(normalized, "serialNos"),
                    OPENING_BIZ_TYPE,
                    bizNo,
                    qtyOnHand,
                    audit
            );
        }
        return rows.size();
    }

    private LocalDate optionalDate(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        String value = support.optionalText(raw, column);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            errors.add(new ImportRowErrorResponse(column, column + "格式不正确，必须是yyyy-MM-dd"));
            return null;
        }
    }

    private boolean lotControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getLotControlled());
    }

    private boolean shelfLifeControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getShelfLifeControlled());
    }

    private boolean serialControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getSerialControlled());
    }

    private List<String> parseSerials(String serialNos) {
        if (!StringUtils.hasText(serialNos)) {
            return List.of();
        }
        Set<String> serials = new LinkedHashSet<>();
        Arrays.stream(serialNos.split("[,;，；\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(serials::add);
        return List.copyOf(serials);
    }
}
