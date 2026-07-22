package com.tuowei.erp.masterdata.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customer.web.CustomerCreditExposureResponse;
import com.tuowei.erp.sales.order.service.SalesCreditEvaluator;
import com.tuowei.erp.sales.order.service.SalesCreditExposure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CustomerCreditService {

    private final CustomerMapper customerMapper;
    private final SalesCreditEvaluator creditEvaluator;
    private final AuditMetadataFactory auditMetadataFactory;

    public CustomerCreditService(CustomerMapper customerMapper, SalesCreditEvaluator creditEvaluator, AuditMetadataFactory auditMetadataFactory) {
        this.customerMapper = customerMapper;
        this.creditEvaluator = creditEvaluator;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public CustomerCreditExposureResponse exposure(Long customerId) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = customerMapper.selectOne(new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getId, customerId)
                .eq(CustomerEntity::getCompanyId, audit.companyId())
                .eq(CustomerEntity::getAccountBookId, audit.accountBookId())
                .eq(CustomerEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (customer == null) {
            throw new IllegalArgumentException("客户不存在");
        }
        SalesCreditExposure exposure = creditEvaluator.evaluate(customer);
        BigDecimal limit = ScalePrecision.amount(ScalePrecision.zeroDefault(customer.getCreditLimit()));
        boolean unlimited = limit.compareTo(BigDecimal.ZERO) <= 0;
        BigDecimal available = unlimited ? null : ScalePrecision.amount(limit.subtract(exposure.totalExposure()));
        return new CustomerCreditExposureResponse(customer.getId(), limit, exposure.outstandingReceivable(),
                exposure.openOrderExposure(), exposure.totalExposure(), available, unlimited,
                !unlimited && exposure.totalExposure().compareTo(limit) > 0);
    }
}
