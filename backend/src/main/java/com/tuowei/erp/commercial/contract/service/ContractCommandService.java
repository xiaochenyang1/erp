package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.web.ContractLineRequest;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractSaveRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ContractCommandService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED");
    private final ContractMapper contractMapper;
    private final ContractLineMapper contractLineMapper;
    private final ContractNumberService contractNumberService;
    private final ContractQueryService queryService;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ContractVersionService versionService;
    private final AttachmentService attachmentService;

    @Autowired
    public ContractCommandService(ContractMapper contractMapper, ContractLineMapper contractLineMapper,
                                  ContractNumberService contractNumberService, ContractQueryService queryService,
                                  CustomerMapper customerMapper, SupplierMapper supplierMapper,
                                  ProductValidator productValidator, AuditMetadataFactory auditMetadataFactory,
                                  ContractVersionService versionService, AttachmentService attachmentService) {
        this.contractMapper = contractMapper;
        this.contractLineMapper = contractLineMapper;
        this.contractNumberService = contractNumberService;
        this.queryService = queryService;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.versionService = versionService;
        this.attachmentService = attachmentService;
    }

    /** Backward-compatible constructor for isolated command tests and integrations. */
    public ContractCommandService(ContractMapper contractMapper, ContractLineMapper contractLineMapper,
                                  ContractNumberService contractNumberService, ContractQueryService queryService,
                                  CustomerMapper customerMapper, SupplierMapper supplierMapper,
                                  ProductValidator productValidator, AuditMetadataFactory auditMetadataFactory,
                                  ContractVersionService versionService) {
        this(contractMapper, contractLineMapper, contractNumberService, queryService, customerMapper, supplierMapper,
                productValidator, auditMetadataFactory, versionService, null);
    }

    /** Backward-compatible constructor for isolated command tests and integrations. */
    public ContractCommandService(ContractMapper contractMapper, ContractLineMapper contractLineMapper,
                                  ContractNumberService contractNumberService, ContractQueryService queryService,
                                  CustomerMapper customerMapper, SupplierMapper supplierMapper,
                                  ProductValidator productValidator, AuditMetadataFactory auditMetadataFactory) {
        this(contractMapper, contractLineMapper, contractNumberService, queryService, customerMapper, supplierMapper,
                productValidator, auditMetadataFactory, null, null);
    }

    @Transactional
    public ContractResponse create(ContractSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validate(request, audit);
        LocalDateTime now = audit.now();
        ContractEntity entity = new ContractEntity();
        entity.setCompanyId(audit.companyId()); entity.setAccountBookId(audit.accountBookId());
        entity.setContractNo(contractNumberService.nextContractNo(request.signedDate()));
        applyRequest(entity, request); entity.setStatus("DRAFT"); entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
        contractMapper.insert(entity);
        insertLines(entity, request.lines(), audit, now);
        if (versionService != null) versionService.record(entity, null, "CREATED");
        return queryService.detail(entity.getId());
    }

    @Transactional
    public ContractResponse update(Long id, ContractSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ContractEntity entity = queryService.requireContract(id);
        if (!EDITABLE.contains(entity.getStatus())) throw new IllegalArgumentException("仅草稿或已驳回合同可编辑");
        validate(request, audit); applyRequest(entity, request); touch(entity, audit);
        OptimisticLockGuard.requireUpdated(contractMapper.updateById(entity), "合同已被修改，请刷新后重试");
        deleteLines(entity); insertLines(entity, request.lines(), audit, audit.now());
        if (versionService != null) versionService.record(entity, null, "EDITED");
        return queryService.detail(id);
    }

    @Transactional
    public ContractResponse submit(Long id) {
        ContractEntity entity = queryService.requireContract(id);
        if (!Set.of("DRAFT", "REJECTED").contains(entity.getStatus())) {
            throw new IllegalArgumentException("仅草稿或已驳回合同可提交");
        }
        if (attachmentService != null) {
            attachmentService.requireIfConfigured(AttachmentBusinessType.COMMERCIAL_CONTRACT, entity.getId());
        }
        return transition(entity, "SUBMITTED");
    }

    @Transactional
    public ContractResponse approve(Long id) { return transition(id, Set.of("SUBMITTED"), "ACTIVE", "仅已提交合同可审批生效"); }

    @Transactional
    public ContractResponse reject(Long id) { return transition(id, Set.of("SUBMITTED"), "REJECTED", "仅已提交合同可驳回"); }

    @Transactional
    public ContractResponse close(Long id) { return transition(id, Set.of("ACTIVE"), "CLOSED", "仅生效中的合同可关闭"); }

    @Transactional
    public ContractResponse cancel(Long id) {
        ContractEntity entity = queryService.requireContract(id);
        if (Set.of("CLOSED", "CANCELLED").contains(entity.getStatus())) throw new IllegalArgumentException("当前合同状态不可作废");
        return transition(entity, "CANCELLED");
    }

    private ContractResponse transition(Long id, Set<String> expected, String target, String message) {
        ContractEntity entity = queryService.requireContract(id);
        if (!expected.contains(entity.getStatus())) throw new IllegalArgumentException(message);
        return transition(entity, target);
    }

    private ContractResponse transition(ContractEntity entity, String target) {
        AuditMetadata audit = auditMetadataFactory.current(); entity.setStatus(target); touch(entity, audit);
        OptimisticLockGuard.requireUpdated(contractMapper.updateById(entity), "合同已被修改，请刷新后重试");
        if (versionService != null) versionService.record(entity, null, target);
        return queryService.detail(entity.getId());
    }

    private void validate(ContractSaveRequest request, AuditMetadata audit) {
        String type = normalizeType(request.contractType());
        if ("SALES".equals(type)) requireCustomer(request.customerId(), audit);
        else requireSupplier(request.supplierId(), audit);
        if (request.lines() == null || request.lines().isEmpty()) throw new IllegalArgumentException("合同明细不能为空");
        for (ContractLineRequest line : request.lines()) productValidator.requireProduct(line.productId(), audit.companyId(), audit.accountBookId());
    }

    private void applyRequest(ContractEntity entity, ContractSaveRequest request) {
        String type = normalizeType(request.contractType()); entity.setContractType(type);
        entity.setCustomerId("SALES".equals(type) ? request.customerId() : null); entity.setSupplierId("PURCHASE".equals(type) ? request.supplierId() : null);
        entity.setContractName(requiredText(request.contractName(), "合同名称不能为空")); entity.setSignedDate(request.signedDate());
        entity.setEffectiveFrom(request.effectiveFrom()); entity.setEffectiveTo(request.effectiveTo()); entity.setRemark(trim(request.remark()));
        BigDecimal total = request.lines().stream().map(line -> ScalePrecision.amount(line.quantity().multiply(line.unitPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add); entity.setTotalAmount(ScalePrecision.amount(total));
    }

    private void insertLines(ContractEntity contract, List<ContractLineRequest> lines, AuditMetadata audit, LocalDateTime now) {
        int lineNo = 1;
        for (ContractLineRequest request : lines) {
            ContractLineEntity line = new ContractLineEntity(); line.setCompanyId(audit.companyId()); line.setAccountBookId(audit.accountBookId());
            line.setContractId(contract.getId()); line.setLineNo(lineNo++); line.setProductId(request.productId());
            line.setQuantity(ScalePrecision.quantity(request.quantity())); line.setFulfilledQuantity(ScalePrecision.quantity(BigDecimal.ZERO));
            line.setUnitPrice(ScalePrecision.amount(request.unitPrice())); line.setAmount(ScalePrecision.amount(request.quantity().multiply(request.unitPrice())));
            line.setRemark(trim(request.remark())); line.setDeletedFlag(0); line.setCreatedBy(audit.userId()); line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now); line.setVersion(0); contractLineMapper.insert(line);
        }
    }

    private void deleteLines(ContractEntity contract) {
        contractLineMapper.delete(new LambdaQueryWrapper<ContractLineEntity>()
                .eq(ContractLineEntity::getCompanyId, contract.getCompanyId())
                .eq(ContractLineEntity::getAccountBookId, contract.getAccountBookId())
                .eq(ContractLineEntity::getContractId, contract.getId()));
    }

    private CustomerEntity requireCustomer(Long id, AuditMetadata audit) {
        CustomerEntity entity = id == null ? null : customerMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getCompanyId(), audit.companyId()) || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0 || !"ACTIVE".equalsIgnoreCase(entity.getStatus()))
            throw new IllegalArgumentException("客户不存在或已停用");
        return entity;
    }

    private SupplierEntity requireSupplier(Long id, AuditMetadata audit) {
        SupplierEntity entity = id == null ? null : supplierMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getCompanyId(), audit.companyId()) || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0 || !"ACTIVE".equalsIgnoreCase(entity.getStatus()))
            throw new IllegalArgumentException("供应商不存在或已停用");
        return entity;
    }

    private String normalizeType(String value) {
        String type = requiredText(value, "合同类型不能为空").toUpperCase(Locale.ROOT);
        if (!Set.of("SALES", "PURCHASE").contains(type)) throw new IllegalArgumentException("合同类型仅支持 SALES 或 PURCHASE");
        return type;
    }
    private String requiredText(String value, String message) { if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message); return value.trim(); }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private void touch(ContractEntity entity, AuditMetadata audit) { entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now()); }
}
