package com.tuowei.erp.masterdata.supplier.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** Write-side create/update/state-transition for suppliers. */
@Service
public class SupplierPostingService {

    private final SupplierMapper supplierMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SupplierQueryService supplierQueryService;

    public SupplierPostingService(
            SupplierMapper supplierMapper,
            AuditMetadataFactory auditMetadataFactory,
            SupplierQueryService supplierQueryService
    ) {
        this.supplierMapper = supplierMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.supplierQueryService = supplierQueryService;
    }

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        SupplierEntity entity = new SupplierEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setSupplierCode(request.supplierCode());
        entity.setSupplierName(request.supplierName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setEmail(request.email());
        entity.setSettlementMethod(request.settlementMethod());
        entity.setCreditPeriod(request.creditPeriod());
        entity.setAddress(request.address());
        entity.setStatus(StringUtils.hasText(request.status()) ? request.status() : "ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        supplierMapper.insert(entity);
        return supplierQueryService.toResponse(entity);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierUpdateRequest request) {
        SupplierEntity entity = supplierQueryService.requireSupplier(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setSupplierName(request.supplierName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setEmail(request.email());
        entity.setSettlementMethod(request.settlementMethod());
        entity.setCreditPeriod(request.creditPeriod());
        entity.setAddress(request.address());
        if (StringUtils.hasText(request.status())) {
            entity.setStatus(request.status());
        }
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(supplierMapper.updateById(entity), "供应商已被其他操作修改，请刷新后重试");
        return supplierQueryService.toResponse(entity);
    }

    @Transactional
    public SupplierResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public SupplierResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private SupplierResponse updateStatus(Long id, String status) {
        SupplierEntity entity = supplierQueryService.requireSupplier(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(supplierMapper.updateById(entity), "供应商已被其他操作修改，请刷新后重试");
        return supplierQueryService.toResponse(entity);
    }
}
