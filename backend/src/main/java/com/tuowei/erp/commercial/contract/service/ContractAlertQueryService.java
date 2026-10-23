package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.web.ContractAlertResponse;
import com.tuowei.erp.commercial.contract.web.ContractPageQuery;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.common.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContractAlertQueryService {
    private final ContractQueryService contractQueryService;
    private final Clock clock;

    public ContractAlertQueryService(ContractQueryService contractQueryService, Clock clock) {
        this.contractQueryService = contractQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContractAlertResponse> list(int expirationWarningDays, BigDecimal lowExecutionRate) {
        LocalDate today = LocalDate.now(clock);
        List<ContractAlertResponse> result = new ArrayList<>();
        long pageNo = 1;
        while (true) {
            PageResponse<ContractResponse> page = contractQueryService.list(activeQuery(pageNo));
            for (ContractResponse summary : page.records()) {
                ContractResponse detail = contractQueryService.detail(summary.id());
                if (detail.effectiveTo() == null) continue;
                long days = ChronoUnit.DAYS.between(today, detail.effectiveTo());
                BigDecimal total = detail.lines().stream().map(line -> line.quantity() == null ? BigDecimal.ZERO : line.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal fulfilled = detail.lines().stream().map(line -> line.fulfilledQuantity() == null ? BigDecimal.ZERO : line.fulfilledQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal rate = total.signum() == 0 ? BigDecimal.ZERO : fulfilled.divide(total, 6, RoundingMode.HALF_UP);
                List<String> types = new ArrayList<>();
                if (days >= 0 && days <= expirationWarningDays) types.add(ContractAlertService.TYPE_EXPIRING);
                if (days >= 0 && days <= expirationWarningDays && rate.compareTo(lowExecutionRate) < 0) types.add(ContractAlertService.TYPE_EXECUTION_LOW);
                if (!types.isEmpty()) result.add(new ContractAlertResponse(detail.id(), detail.contractNo(), detail.contractName(), detail.contractType(), detail.effectiveTo(), days, rate, types));
            }
            if (page.records().size() < page.pageSize()) break;
            pageNo++;
        }
        return result;
    }

    private ContractPageQuery activeQuery(long pageNo) {
        ContractPageQuery query = new ContractPageQuery();
        query.setPageNo(pageNo); query.setPageSize(200L); query.setStatus("ACTIVE");
        return query;
    }
}
