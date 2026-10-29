package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class ContractExecutionService {
    private final ContractLineMapper contractLineMapper;

    public ContractExecutionService(ContractLineMapper contractLineMapper) {
        this.contractLineMapper = contractLineMapper;
    }

    public void increase(Long contractLineId, BigDecimal quantity, AuditMetadata audit) {
        adjust(contractLineId, quantity, audit, false);
    }

    public void decrease(Long contractLineId, BigDecimal quantity, AuditMetadata audit) {
        adjust(contractLineId, quantity, audit, true);
    }

    private void adjust(Long contractLineId, BigDecimal quantity, AuditMetadata audit, boolean decrease) {
        if (contractLineId == null || quantity == null || quantity.signum() == 0) return;
        ContractLineEntity line = contractLineMapper.selectById(contractLineId);
        if (line == null || !Objects.equals(line.getCompanyId(), audit.companyId())
                || !Objects.equals(line.getAccountBookId(), audit.accountBookId())
                || line.getDeletedFlag() == null || line.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("合同明细不存在");
        }
        BigDecimal current = ScalePrecision.quantity(line.getFulfilledQuantity());
        BigDecimal delta = ScalePrecision.quantity(quantity);
        BigDecimal next = decrease ? current.subtract(delta) : current.add(delta);
        if (next.signum() < 0) throw new IllegalArgumentException("合同履约数量不能为负");
        if (next.compareTo(ScalePrecision.quantity(line.getQuantity())) > 0) {
            throw new IllegalArgumentException("合同履约数量不能超过合同数量");
        }
        line.setFulfilledQuantity(ScalePrecision.quantity(next));
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(contractLineMapper.updateById(line), "合同明细已被其他操作修改，请刷新后重试");
    }
}
