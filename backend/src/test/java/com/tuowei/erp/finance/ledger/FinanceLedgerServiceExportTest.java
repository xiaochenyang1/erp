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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceLedgerServiceExportTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9801L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 18, 10, 30)
    );

    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void exportLedgerWritesScopedCsvRows() throws Exception {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(voucherEntry()));

        LedgerQuery query = new LedgerQuery();
        query.setSubjectCode("1001");
        query.setDateFrom(LocalDate.of(2026, 6, 1));
        query.setDateTo(LocalDate.of(2026, 6, 30));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service().exportLedger(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFbizDate,voucherId,lineNo,subjectCode,subjectName,summary,debitAmount,creditAmount\r\n");
        assertThat(csv).contains("2026-06-18,9001,1,1001,库存现金,期初导入,100.00,0.00\r\n");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("subject_code")
                .contains("biz_date");
    }

    private FinanceLedgerService service() {
        return new FinanceLedgerService(voucherEntryMapper, auditMetadataFactory);
    }

    private static VoucherEntryEntity voucherEntry() {
        VoucherEntryEntity entity = new VoucherEntryEntity();
        entity.setId(7001L);
        entity.setCompanyId(101L);
        entity.setAccountBookId(202L);
        entity.setVoucherId(9001L);
        entity.setBizDate(LocalDate.of(2026, 6, 18));
        entity.setLineNo(1);
        entity.setSubjectId(3001L);
        entity.setSubjectCode("1001");
        entity.setSubjectName("库存现金");
        entity.setSummary("期初导入");
        entity.setDebitAmount(new BigDecimal("100.00"));
        entity.setCreditAmount(new BigDecimal("0.00"));
        return entity;
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
