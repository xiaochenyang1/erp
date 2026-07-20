package com.tuowei.erp.inventory.adjust.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class InventoryAdjustmentNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public InventoryAdjustmentNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextAdjustmentNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("INVENTORY_ADJUSTMENT", "库存调整单", bizDate);
    }
}
