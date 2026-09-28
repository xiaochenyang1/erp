package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractVersionMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractVersionEntity;
import com.tuowei.erp.commercial.contract.web.ContractVersionHeaderResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionLineResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Read-side contract-version listing, detail and diffing, plus the shared snapshot reader reused by the command side. */
@Service
public class ContractVersionQueryService {
    private static final TypeReference<List<ContractVersionLineResponse>> LINE_LIST_TYPE = new TypeReference<>() {};

    private final ContractVersionMapper versionMapper;
    private final ContractQueryService queryService;
    private final ObjectMapper objectMapper;

    public ContractVersionQueryService(ContractVersionMapper versionMapper, ContractQueryService queryService,
                                       ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ContractVersionResponse> list(Long contractId) {
        ContractEntity contract = queryService.requireContract(contractId);
        List<ContractVersionEntity> versions = versionMapper.selectList(new LambdaQueryWrapper<ContractVersionEntity>()
                .eq(ContractVersionEntity::getCompanyId, contract.getCompanyId())
                .eq(ContractVersionEntity::getAccountBookId, contract.getAccountBookId())
                .eq(ContractVersionEntity::getContractId, contract.getId())
                .orderByAsc(ContractVersionEntity::getVersionNo));
        List<ContractVersionResponse> responses = new ArrayList<>();
        ContractVersionSnapshot previous = null;
        for (ContractVersionEntity entity : versions) {
            ContractVersionSnapshot current = readSnapshot(entity);
            responses.add(toResponse(entity, current, changedFields(previous, current)));
            previous = current;
        }
        Collections.reverse(responses);
        return responses;
    }

    @Transactional(readOnly = true)
    public ContractVersionResponse detail(Long contractId, Long versionId) {
        ContractEntity contract = queryService.requireContract(contractId);
        List<ContractVersionEntity> versions = versionMapper.selectList(new LambdaQueryWrapper<ContractVersionEntity>()
                .eq(ContractVersionEntity::getCompanyId, contract.getCompanyId())
                .eq(ContractVersionEntity::getAccountBookId, contract.getAccountBookId())
                .eq(ContractVersionEntity::getContractId, contract.getId())
                .orderByAsc(ContractVersionEntity::getVersionNo));
        ContractVersionEntity currentEntity = versions.stream().filter(item -> Objects.equals(item.getId(), versionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("合同历史版本不存在"));
        ContractVersionSnapshot current = readSnapshot(currentEntity);
        ContractVersionEntity previousEntity = versions.stream()
                .filter(item -> item.getVersionNo() < currentEntity.getVersionNo())
                .reduce((left, right) -> right).orElse(null);
        ContractVersionSnapshot previous = previousEntity == null ? null : readSnapshot(previousEntity);
        return toResponse(currentEntity, current, changedFields(previous, current));
    }

    ContractVersionSnapshot readSnapshot(ContractVersionEntity entity) {
        try {
            return new ContractVersionSnapshot(
                    objectMapper.readValue(entity.getContractSnapshotJson(), ContractVersionHeaderResponse.class),
                    objectMapper.readValue(entity.getLineSnapshotJson(), LINE_LIST_TYPE),
                    entity.getStatus());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("合同历史版本数据损坏", ex);
        }
    }

    private ContractVersionResponse toResponse(ContractVersionEntity entity, ContractVersionSnapshot snapshot,
                                               List<String> changedFields) {
        return new ContractVersionResponse(entity.getId(), entity.getContractId(), entity.getVersionNo(),
                entity.getEventType(), entity.getStatus(), snapshot.header(), snapshot.lines(), changedFields,
                entity.getCreatedBy(), entity.getCreatedTime());
    }

    private List<String> changedFields(ContractVersionSnapshot previous, ContractVersionSnapshot current) {
        if (previous == null) return List.of("CREATED");
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(previous.status(), current.status())) fields.add("STATUS");
        if (!Objects.equals(previous.header().contractType(), current.header().contractType())) fields.add("CONTRACT_TYPE");
        if (!Objects.equals(previous.header().customerId(), current.header().customerId())
                || !Objects.equals(previous.header().supplierId(), current.header().supplierId())) fields.add("PARTNER");
        if (!Objects.equals(previous.header().contractName(), current.header().contractName())) fields.add("CONTRACT_NAME");
        if (!Objects.equals(previous.header().signedDate(), current.header().signedDate())) fields.add("SIGNED_DATE");
        if (!Objects.equals(previous.header().effectiveFrom(), current.header().effectiveFrom())
                || !Objects.equals(previous.header().effectiveTo(), current.header().effectiveTo())) fields.add("EFFECTIVE_PERIOD");
        if (!sameAmount(previous.header().totalAmount(), current.header().totalAmount())) fields.add("TOTAL_AMOUNT");
        if (!Objects.equals(previous.header().remark(), current.header().remark())) fields.add("REMARK");
        if (!Objects.equals(previous.lines(), current.lines())) fields.add("LINES");
        return fields;
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }

    record ContractVersionSnapshot(ContractVersionHeaderResponse header,
                                   List<ContractVersionLineResponse> lines,
                                   String status) {}
}
