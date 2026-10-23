package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckIssueResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationDetailResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationManualReleaseRequest;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryResponse;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InventoryReservationOpsService {

    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final SystemLogService systemLogService;
    private final InventoryReservationQueryService reservationQueryService;
    private final InventoryReservationCheckService reservationCheckService;

    public InventoryReservationOpsService(
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            InventoryPostingService inventoryPostingService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            SystemLogService systemLogService,
            InventoryReservationQueryService reservationQueryService,
            InventoryReservationCheckService reservationCheckService
    ) {
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.systemLogService = systemLogService;
        this.reservationQueryService = reservationQueryService;
        this.reservationCheckService = reservationCheckService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryReservationResponse> listReservations(InventoryReservationPageQuery query) {
        return reservationQueryService.listReservations(query);
    }

    @Transactional(readOnly = true)
    public InventoryReservationDetailResponse getReservation(Long id) {
        return reservationQueryService.getReservation(id);
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationSummaryResponse> summary(InventoryReservationSummaryQuery query) {
        return reservationQueryService.summary(query);
    }

    @Transactional(readOnly = true)
    public InventoryReservationSourceResponse source(InventoryReservationSourceQuery query) {
        return reservationQueryService.source(query);
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationCheckIssueResponse> checks(InventoryReservationCheckQuery query) {
        return reservationCheckService.checks(query);
    }

    @Transactional
    public InventoryReservationDetailResponse manualRelease(
            Long id,
            InventoryReservationManualReleaseRequest request
    ) {
        InventoryReservationEntity reservation = reservationQueryService.requireVisibleReservation(id);
        validateDraftDeliveryCoverage(reservation, quantity(request.qty()));
        AuditMetadata audit = auditMetadataFactory.current();
        inventoryPostingService.manualReleaseReservation(id, request.qty(), audit, request.reason());
        CurrentUser operator = currentUserContext.requireCurrentUser();
        systemLogService.recordAudit(
                "INVENTORY_RESERVATION",
                "INVENTORY_RESERVATION",
                reservation.getId(),
                reservation.getSourceNo(),
                "MANUAL_RELEASE",
                operator.userId(),
                operator.username(),
                "{\"qty\":\"" + quantity(request.qty()).toPlainString() + "\"}",
                request.reason(),
                audit.now()
        );
        return reservationQueryService.getReservation(id);
    }

    private void validateDraftDeliveryCoverage(InventoryReservationEntity reservation, BigDecimal releaseQty) {
        if (!"SALES_ORDER".equalsIgnoreCase(reservation.getSourceType())) {
            return;
        }
        BigDecimal remainingAfterRelease = quantity(quantity(reservation.getRemainingQty()).subtract(releaseQty));
        BigDecimal occupiedDraftQty = draftDeliveryQty(
                reservation.getCompanyId(),
                reservation.getAccountBookId(),
                reservation.getSourceId(),
                reservation.getSourceLineId()
        );
        if (remainingAfterRelease.compareTo(occupiedDraftQty) < 0) {
            throw new IllegalArgumentException("预占已被销售出库草稿占用，不能释放");
        }
    }

    private BigDecimal draftDeliveryQty(Long companyId, Long accountBookId, Long orderId, Long orderLineId) {
        List<Long> draftDeliveryIds = salesDeliveryMapper.selectList(new LambdaQueryWrapper<SalesDeliveryEntity>()
                        .eq(SalesDeliveryEntity::getCompanyId, companyId)
                        .eq(SalesDeliveryEntity::getAccountBookId, accountBookId)
                        .eq(SalesDeliveryEntity::getOrderId, orderId)
                        .eq(SalesDeliveryEntity::getStatus, "DRAFT")
                        .eq(SalesDeliveryEntity::getDeletedFlag, 0))
                .stream()
                .map(SalesDeliveryEntity::getId)
                .toList();
        if (draftDeliveryIds.isEmpty()) {
            return quantity(BigDecimal.ZERO);
        }
        return quantity(salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, companyId)
                        .eq(SalesDeliveryLineEntity::getAccountBookId, accountBookId)
                        .in(SalesDeliveryLineEntity::getDeliveryId, draftDeliveryIds)
                        .eq(SalesDeliveryLineEntity::getOrderLineId, orderLineId))
                .stream()
                .map(SalesDeliveryLineEntity::getQty)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal quantity(BigDecimal value) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(value));
    }
}
