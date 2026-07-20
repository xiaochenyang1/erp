package com.tuowei.erp.inventory.check.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class InventoryStockCheckNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public InventoryStockCheckNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextCheckNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("INVENTORY_STOCK_CHECK", "库存盘点单", bizDate);
    }
}
