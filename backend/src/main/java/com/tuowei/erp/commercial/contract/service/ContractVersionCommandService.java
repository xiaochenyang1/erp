package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractVersionMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.model.ContractVersionEntity;
import com.tuowei.erp.commercial.contract.service.ContractVersionQueryService.ContractVersionSnapshot;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionHeaderResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionLineResponse;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Write-side contract-version snapshot recording and version-to-draft restore, reusing the query side's snapshot reader. */
@Service
public class ContractVersionCommandService {

    private final ContractVersionMapper versionMapper;
    private final ContractMapper contractMapper;
    private final ContractLineMapper lineMapper;
    private final ContractQueryService queryService;
    private final ContractNumberService numberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ObjectMapper objectMapper;
    private final ContractVersionQueryService versionQueryService;

    public ContractVersionCommandService(ContractVersionMapper versionMapper, ContractMapper contractMapper,
                                         ContractLineMapper lineMapper, ContractQueryService queryService,
                                         ContractNumberService numberService, AuditMetadataFactory auditMetadataFactory,
                                         ObjectMapper objectMapper, ContractVersionQueryService versionQueryService) {
        this.versionMapper = versionMapper;
        this.contractMapper = contractMapper;
        this.lineMapper = lineMapper;
        this.queryService = queryService;
        this.numberService = numberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.objectMapper = objectMapper;
        this.versionQueryService = versionQueryService;
    }

    @Transactional
    public void record(ContractEntity contract, List<ContractLineEntity> lines, String eventType) {
        if (contract == null) return;
        AuditMetadata audit = auditMetadataFactory.current();
        List<ContractLineEntity> safeLines = lines == null ? queryService.loadLines(contract) : lines;
        ContractVersionEntity previous = latest(contract);
        LocalDateTime now = audit.now();

        ContractVersionEntity version = new ContractVersionEntity();
        version.setCompanyId(contract.getCompanyId());
        version.setAccountBookId(contract.getAccountBookId());
        version.setContractId(contract.getId());
        version.setVersionNo(previous == null ? 1 : previous.getVersionNo() + 1);
        version.setEventType(eventType);
        version.setStatus(contract.getStatus());
        version.setContractSnapshotJson(writeJson(toHeader(contract)));
        version.setLineSnapshotJson(writeJson(safeLines.stream().map(this::toLine).toList()));
        version.setCreatedBy(audit.userId());
        version.setCreatedTime(now);
        version.setUpdatedBy(audit.userId());
        version.setUpdatedTime(now);
        version.setVersion(0);
        versionMapper.insert(version);
    }

    @Transactional
    public ContractResponse restoreAsDraft(Long contractId, Long versionId) {
        ContractEntity sourceContract = queryService.requireContract(contractId);
        ContractVersionEntity sourceVersion = requireVersion(sourceContract, versionId);
        ContractVersionSnapshot snapshot = versionQueryService.readSnapshot(sourceVersion);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        ContractEntity restored = new ContractEntity();
        restored.setCompanyId(audit.companyId());
        restored.setAccountBookId(audit.accountBookId());
        restored.setContractNo(numberService.nextContractNo(snapshot.header().signedDate()));
        restored.setContractType(snapshot.header().contractType());
        restored.setCustomerId(snapshot.header().customerId());
        restored.setSupplierId(snapshot.header().supplierId());
        restored.setContractName(snapshot.header().contractName());
        restored.setSignedDate(snapshot.header().signedDate());
        restored.setEffectiveFrom(snapshot.header().effectiveFrom());
        restored.setEffectiveTo(snapshot.header().effectiveTo());
        restored.setStatus("DRAFT");
        restored.setTotalAmount(snapshot.header().totalAmount());
        restored.setCurrencyCode(snapshot.header().currencyCode());
        restored.setExchangeRate(snapshot.header().exchangeRate());
        restored.setBaseTotalAmount(snapshot.header().baseTotalAmount());
        restored.setDeletedFlag(0);
        restored.setRemark(snapshot.header().remark());
        restored.setCreatedBy(audit.userId());
        restored.setCreatedTime(now);
        restored.setUpdatedBy(audit.userId());
        restored.setUpdatedTime(now);
        restored.setVersion(0);
        contractMapper.insert(restored);

        List<ContractLineEntity> restoredLines = new ArrayList<>();
        int lineNo = 1;
        for (ContractVersionLineResponse snapshotLine : snapshot.lines()) {
            ContractLineEntity line = new ContractLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setContractId(restored.getId());
            line.setLineNo(lineNo++);
            line.setProductId(snapshotLine.productId());
            line.setQuantity(ScalePrecision.quantity(snapshotLine.quantity()));
            line.setFulfilledQuantity(ScalePrecision.quantity(BigDecimal.ZERO));
            line.setUnitPrice(ScalePrecision.amount(snapshotLine.unitPrice()));
            line.setAmount(ScalePrecision.amount(snapshotLine.amount()));
            line.setRemark(snapshotLine.remark());
            line.setDeletedFlag(0);
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            lineMapper.insert(line);
            restoredLines.add(line);
        }
        record(restored, restoredLines, "RESTORED");
        return queryService.detail(restored.getId());
    }

    private ContractVersionEntity requireVersion(ContractEntity contract, Long versionId) {
        ContractVersionEntity version = versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getCompanyId(), contract.getCompanyId())
                || !Objects.equals(version.getAccountBookId(), contract.getAccountBookId())
                || !Objects.equals(version.getContractId(), contract.getId())) {
            throw new IllegalArgumentException("合同历史版本不存在");
        }
        return version;
    }

    private ContractVersionEntity latest(ContractEntity contract) {
        return versionMapper.selectOne(new LambdaQueryWrapper<ContractVersionEntity>()
                .eq(ContractVersionEntity::getCompanyId, contract.getCompanyId())
                .eq(ContractVersionEntity::getAccountBookId, contract.getAccountBookId())
                .eq(ContractVersionEntity::getContractId, contract.getId())
                .orderByDesc(ContractVersionEntity::getVersionNo)
                .last("LIMIT 1"));
    }

    private ContractVersionHeaderResponse toHeader(ContractEntity entity) {
        return new ContractVersionHeaderResponse(entity.getContractNo(), entity.getContractType(), entity.getCustomerId(),
                entity.getSupplierId(), entity.getContractName(), entity.getSignedDate(), entity.getEffectiveFrom(),
                entity.getEffectiveTo(), entity.getTotalAmount(), entity.getCurrencyCode(), entity.getExchangeRate(),
                entity.getBaseTotalAmount(), entity.getRemark());
    }

    private ContractVersionLineResponse toLine(ContractLineEntity line) {
        return new ContractVersionLineResponse(line.getLineNo(), line.getProductId(), line.getQuantity(),
                line.getFulfilledQuantity(), line.getUnitPrice(), line.getAmount(), line.getRemark());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("合同历史版本序列化失败", ex);
        }
    }
}
