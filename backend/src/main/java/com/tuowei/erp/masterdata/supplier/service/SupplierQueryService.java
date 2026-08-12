package com.tuowei.erp.masterdata.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.supplier.web.SupplierResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side list/detail/export, tenant guard and response mapping for suppliers. */
@Service
public class SupplierQueryService {

    private static final List<String> SUPPLIER_EXPORT_HEADERS = List.of(
            "supplierCode",
            "supplierName",
            "contactName",
            "contactPhone",
            "email",
            "settlementMethod",
            "creditPeriod",
            "address",
            "status",
            "remark"
    );

    private final SupplierMapper supplierMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public SupplierQueryService(SupplierMapper supplierMapper, AuditMetadataFactory auditMetadataFactory) {
        this.supplierMapper = supplierMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public SupplierResponse getById(Long id) {
        return toResponse(requireSupplier(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(SupplierPageQuery query) {
        SupplierPageQuery safeQuery = query == null ? new SupplierPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        String settlementMethod = normalizeNullableText(safeQuery.getSettlementMethod());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<SupplierEntity> page = new Page<>(pageNo, pageSize);
        Page<SupplierEntity> result = supplierMapper.selectPage(
                page,
                buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status, settlementMethod)
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    public StreamingResponseBody exportSuppliers(SupplierPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SupplierPageQuery safeQuery = query == null ? new SupplierPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, SUPPLIER_EXPORT_HEADERS, rowWriter -> {
            String keyword = normalizeNullableText(safeQuery.getKeyword());
            String status = normalizeStatus(safeQuery.getStatus());
            String settlementMethod = normalizeNullableText(safeQuery.getSettlementMethod());
            AuditMetadata audit = auditMetadataFactory.current();
            List<SupplierEntity> suppliers = supplierMapper.selectList(
                    buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status, settlementMethod)
            );
            for (SupplierEntity entity : suppliers) {
                rowWriter.write(supplierExportRow(toResponse(entity)));
            }
        }));
    }

    public SupplierEntity requireSupplier(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        SupplierEntity entity = supplierMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("供应商不存在");
        }
        return entity;
    }

    public SupplierResponse toResponse(SupplierEntity entity) {
        return new SupplierResponse(
                entity.getId(),
                entity.getSupplierCode(),
                entity.getSupplierName(),
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getEmail(),
                entity.getSettlementMethod(),
                entity.getCreditPeriod(),
                entity.getAddress(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private LambdaQueryWrapper<SupplierEntity> buildListQuery(Long companyId, Long accountBookId, String keyword, String status, String settlementMethod) {
        LambdaQueryWrapper<SupplierEntity> wrapper = new LambdaQueryWrapper<SupplierEntity>()
                .eq(SupplierEntity::getCompanyId, companyId)
                .eq(SupplierEntity::getAccountBookId, accountBookId)
                .eq(SupplierEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SupplierEntity::getSupplierCode, keyword)
                    .or()
                    .like(SupplierEntity::getSupplierName, keyword)
                    .or()
                    .like(SupplierEntity::getContactName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SupplierEntity::getStatus, status);
        }
        if (StringUtils.hasText(settlementMethod)) {
            wrapper.eq(SupplierEntity::getSettlementMethod, settlementMethod);
        }
        return wrapper.orderByAsc(SupplierEntity::getSupplierCode);
    }

    private List<?> supplierExportRow(SupplierResponse record) {
        return Arrays.asList(
                record.supplierCode(),
                record.supplierName(),
                record.contactName(),
                record.contactPhone(),
                record.email(),
                record.settlementMethod(),
                record.creditPeriod(),
                record.address(),
                record.status(),
                record.remark()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}
