package com.tuowei.erp.finance.ledger;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.ledger.service.FinanceLedgerService;
import com.tuowei.erp.finance.ledger.web.LedgerQuery;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceLedgerServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9801L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 17, 0)
    );

    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void generalLedgerScopesVoucherEntriesByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of());

        service().general(new LedgerQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private FinanceLedgerService service() {
        return new FinanceLedgerService(voucherEntryMapper, auditMetadataFactory);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
