package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.system.notification.mapper.NotificationMapper;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import com.tuowei.erp.system.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ContractAlertService {
    public static final String BUSINESS_TYPE = "COMMERCIAL_CONTRACT";
    public static final String TYPE_EXPIRING = "CONTRACT_EXPIRING";
    public static final String TYPE_EXECUTION_LOW = "CONTRACT_EXECUTION_LOW";

    private final ContractMapper contractMapper;
    private final ContractLineMapper contractLineMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    public ContractAlertService(ContractMapper contractMapper, ContractLineMapper contractLineMapper,
                                NotificationMapper notificationMapper, NotificationService notificationService) {
        this.contractMapper = contractMapper;
        this.contractLineMapper = contractLineMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public int scan(AuditMetadata audit, LocalDate today, int expirationWarningDays,
                    BigDecimal lowExecutionRate) {
        List<ContractEntity> contracts = contractMapper.selectList(new LambdaQueryWrapper<ContractEntity>()
                .eq(ContractEntity::getCompanyId, audit.companyId())
                .eq(ContractEntity::getAccountBookId, audit.accountBookId())
                .eq(ContractEntity::getStatus, "ACTIVE")
                .eq(ContractEntity::getDeletedFlag, 0));
        int created = 0;
        for (ContractEntity contract : contracts) {
            if (contract.getEffectiveTo() == null) continue;
            long days = ChronoUnit.DAYS.between(today, contract.getEffectiveTo());
            if (days >= 0 && days <= expirationWarningDays
                    && createIfAbsent(contract, TYPE_EXPIRING, "合同即将到期：" + contract.getContractNo(),
                    "合同 " + contract.getContractName() + " 将在 " + days + " 天后到期，请及时跟进。", audit)) {
                created++;
            }
            if (days >= 0 && days <= expirationWarningDays && belowExecutionRate(contract, lowExecutionRate)
                    && createIfAbsent(contract, TYPE_EXECUTION_LOW, "合同执行率偏低：" + contract.getContractNo(),
                    "合同 " + contract.getContractName() + " 临近到期，当前履约率低于预警阈值，请及时跟进。", audit)) {
                created++;
            }
        }
        return created;
    }

    private boolean belowExecutionRate(ContractEntity contract, BigDecimal threshold) {
        List<ContractLineEntity> lines = contractLineMapper.selectList(new LambdaQueryWrapper<ContractLineEntity>()
                .eq(ContractLineEntity::getCompanyId, contract.getCompanyId())
                .eq(ContractLineEntity::getAccountBookId, contract.getAccountBookId())
                .eq(ContractLineEntity::getContractId, contract.getId())
                .eq(ContractLineEntity::getDeletedFlag, 0));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fulfilled = BigDecimal.ZERO;
        for (ContractLineEntity line : lines) {
            total = total.add(line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity());
            fulfilled = fulfilled.add(line.getFulfilledQuantity() == null ? BigDecimal.ZERO : line.getFulfilledQuantity());
        }
        return total.signum() > 0 && fulfilled.divide(total, 6, java.math.RoundingMode.HALF_UP).compareTo(threshold) < 0;
    }

    private boolean createIfAbsent(ContractEntity contract, String type, String title, String content, AuditMetadata audit) {
        boolean exists = notificationMapper.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getCompanyId, contract.getCompanyId())
                .eq(NotificationEntity::getAccountBookId, contract.getAccountBookId())
                .eq(NotificationEntity::getBusinessType, BUSINESS_TYPE)
                .eq(NotificationEntity::getBusinessId, contract.getId())
                .eq(NotificationEntity::getNotificationType, type)
                .eq(NotificationEntity::getStatus, "ACTIVE")
                .eq(NotificationEntity::getDeletedFlag, 0)) > 0;
        if (exists || contract.getCreatedBy() == null) return false;
        AuditMetadata scopedAudit = new AuditMetadata(audit.userId(), contract.getCompanyId(), contract.getAccountBookId(), audit.now());
        notificationService.createBusinessNotification("NOTICE", type, title, content, BUSINESS_TYPE,
                contract.getId(), contract.getContractNo(), "/contracts", List.of(contract.getCreatedBy()), scopedAudit, audit.now());
        return true;
    }
}
