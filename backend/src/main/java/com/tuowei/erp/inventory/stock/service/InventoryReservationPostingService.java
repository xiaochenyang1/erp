package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Compatibility facade for inventory reservation creation and release operations. */
@Service
public class InventoryReservationPostingService {
    private final InventoryReservationCommandService commandService;
    private final InventoryReservationReleaseService releaseService;

    @Autowired
    public InventoryReservationPostingService(
            InventoryReservationCommandService commandService,
            InventoryReservationReleaseService releaseService
    ) {
        this.commandService = commandService;
        this.releaseService = releaseService;
    }

    /** Keeps direct construction in focused tests and downstream modules compatible. */
    public InventoryReservationPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryReservationMapper inventoryReservationMapper,
            InventoryReservationEventMapper inventoryReservationEventMapper
    ) {
        this.commandService = new InventoryReservationCommandService(
                inventoryBalanceMapper, inventoryReservationMapper, inventoryReservationEventMapper
        );
        this.releaseService = new InventoryReservationReleaseService(
                inventoryBalanceMapper, inventoryReservationMapper, inventoryReservationEventMapper
        );
    }

    @Transactional
    public void reserve(InventoryReservationCommand command, AuditMetadata audit, String shortageMessage) {
        commandService.reserve(command, audit, shortageMessage);
    }

    @Transactional
    public void releaseReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit) {
        releaseService.releaseReservation(sourceType, sourceLineId, qty, audit);
    }

    @Transactional
    public void manualReleaseReservation(Long reservationId, BigDecimal qty, AuditMetadata audit, String reason) {
        releaseService.manualReleaseReservation(reservationId, qty, audit, reason);
    }

    @Transactional
    public void releaseAllReservations(String sourceType, Long sourceId, AuditMetadata audit) {
        releaseService.releaseAllReservations(sourceType, sourceId, audit);
    }

    @Transactional
    public void restoreReservation(
            String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit, String reason
    ) {
        releaseService.restoreReservation(sourceType, sourceLineId, qty, audit, reason);
    }
}
