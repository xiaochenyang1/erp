package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.web.ContractPageQuery;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.web.PageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class ContractExportService {
    private static final List<String> HEADERS = List.of(
            "contractNo", "contractType", "contractName", "partnerName", "signedDate",
            "effectiveFrom", "effectiveTo", "status", "totalAmount", "remark"
    );
    private final ContractQueryService queryService;
    private final ReportProperties reportProperties;

    public ContractExportService(ContractQueryService queryService, ReportProperties reportProperties) {
        this.queryService = queryService;
        this.reportProperties = reportProperties;
    }

    public StreamingResponseBody export(ContractPageQuery query) {
        ContractPageQuery base = copy(query == null ? new ContractPageQuery() : query);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, HEADERS, writer -> {
            long emitted = 0;
            long pageNo = 1;
            int batchSize = Math.min(reportProperties.exportBatchSize(), reportProperties.maxExportRows());
            while (true) {
                ContractPageQuery pageQuery = copy(base); pageQuery.setPageNo(pageNo); pageQuery.setPageSize((long) batchSize);
                PageResponse<ContractResponse> page = queryService.list(pageQuery);
                if (pageNo == 1 && page.total() > reportProperties.maxExportRows()) {
                    throw new IllegalArgumentException("导出结果超过" + reportProperties.maxExportRows() + "行，请缩小筛选范围后重试");
                }
                for (ContractResponse record : page.records()) {
                    if (emitted++ >= reportProperties.maxExportRows()) throw new IllegalArgumentException("导出结果超过行数限制");
                    writer.write(row(record));
                }
                if (page.records().size() < batchSize) break;
                pageNo++;
            }
        }));
    }

    private List<?> row(ContractResponse record) {
        String partner = "SALES".equals(record.contractType()) ? record.customerName() : record.supplierName();
        return Arrays.asList(record.contractNo(), record.contractType(), record.contractName(), partner, record.signedDate(),
                record.effectiveFrom(), record.effectiveTo(), record.status(), record.totalAmount(), record.remark());
    }

    private ContractPageQuery copy(ContractPageQuery source) {
        ContractPageQuery target = new ContractPageQuery(); target.setPageNo(source.getPageNo()); target.setPageSize(source.getPageSize());
        target.setKeyword(source.getKeyword()); target.setContractType(source.getContractType()); target.setStatus(source.getStatus());
        target.setCustomerId(source.getCustomerId()); target.setSupplierId(source.getSupplierId());
        target.setEffectiveFrom(source.getEffectiveFrom()); target.setEffectiveTo(source.getEffectiveTo()); return target;
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try { SecurityContextHolder.getContext().setAuthentication(authentication); action.run(); }
        finally { if (previous == null) SecurityContextHolder.clearContext(); else SecurityContextHolder.getContext().setAuthentication(previous); }
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws IOException; }
}
