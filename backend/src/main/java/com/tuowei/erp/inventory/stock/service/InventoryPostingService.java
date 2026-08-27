package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Compatibility facade for inventory posting, balance queries and reservations. */
@Service
public class InventoryPostingService {
    private final InventoryBalancePostingService balancePostingService;
    private final InventoryPostingQueryService queryService;
    private final InventoryReservationPostingService reservationPostingService;

    @Autowired
    public InventoryPostingService(
            InventoryBalancePostingService balancePostingService,
            InventoryPostingQueryService queryService,
            InventoryReservationPostingService reservationPostingService
    ) {
        this.balancePostingService = balancePostingService;
        this.queryService = queryService;
        this.reservationPostingService = reservationPostingService;
    }

    /** Keeps direct construction in existing focused tests and downstream modules compatible. */
    public InventoryPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionWriter inventoryTransactionWriter,
            InventoryReservationPostingService inventoryReservationPostingService,
            ProductMapper productMapper,
            InventoryLotBalanceMapper inventoryLotBalanceMapper
    ) {
        InventoryLotPostingService lotPostingService = new InventoryLotPostingService(
                inventoryBalanceMapper, inventoryLotBalanceMapper, inventoryTransactionWriter
        );
        this.balancePostingService = new InventoryBalancePostingService(
                inventoryBalanceMapper, inventoryTransactionWriter, productMapper, lotPostingService, null
        );
        this.queryService = new InventoryPostingQueryService(inventoryBalanceMapper);
        this.reservationPostingService = inventoryReservationPostingService;
    }

    @Transactional
    public void postInbound(InventoryPostingCommand command, AuditMetadata audit) {
        balancePostingService.postInbound(command, audit);
    }

    @Transactional
    public BigDecimal postOutbound(InventoryPostingCommand command, AuditMetadata audit, String shortageMessage) {
        return balancePostingService.postOutbound(command, audit, shortageMessage);
    }

    @Transactional
    public List<LotAllocation> postOutboundWithAllocations(
            InventoryPostingCommand command, AuditMetadata audit, String shortageMessage
    ) {
        return balancePostingService.postOutboundWithAllocations(command, audit, shortageMessage);
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyOnHand(Long warehouseId, Long productId, Long companyId, Long accountBookId) {
        return queryService.getQtyOnHand(warehouseId, productId, companyId, accountBookId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyOnHand(Long warehouseId, Long productId, Long locationId, Long companyId, Long accountBookId) {
        return queryService.getQtyOnHand(warehouseId, productId, locationId, companyId, accountBookId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyAvailable(Long warehouseId, Long productId, Long companyId, Long accountBookId) {
        return queryService.getQtyAvailable(warehouseId, productId, companyId, accountBookId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyAvailable(Long warehouseId, Long productId, Long locationId, Long companyId, Long accountBookId) {
        return queryService.getQtyAvailable(warehouseId, productId, locationId, companyId, accountBookId);
    }

    @Transactional
    public void reserve(InventoryReservationCommand command, AuditMetadata audit, String shortageMessage) {
        reservationPostingService.reserve(command, audit, shortageMessage);
    }

    @Transactional
    public void releaseReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit) {
        reservationPostingService.releaseReservation(sourceType, sourceLineId, qty, audit);
    }

    @Transactional
    public void manualReleaseReservation(Long reservationId, BigDecimal qty, AuditMetadata audit, String reason) {
        reservationPostingService.manualReleaseReservation(reservationId, qty, audit, reason);
    }

    @Transactional
    public void releaseAllReservations(String sourceType, Long sourceId, AuditMetadata audit) {
        reservationPostingService.releaseAllReservations(sourceType, sourceId, audit);
    }

    @Transactional
    public void restoreReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit, String reason) {
        reservationPostingService.restoreReservation(sourceType, sourceLineId, qty, audit, reason);
    }
}
