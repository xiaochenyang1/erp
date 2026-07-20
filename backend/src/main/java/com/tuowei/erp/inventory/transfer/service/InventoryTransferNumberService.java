package com.tuowei.erp.inventory.transfer.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class InventoryTransferNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public InventoryTransferNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextTransferNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("INVENTORY_TRANSFER", "仓库调拨单", bizDate);
    }
}