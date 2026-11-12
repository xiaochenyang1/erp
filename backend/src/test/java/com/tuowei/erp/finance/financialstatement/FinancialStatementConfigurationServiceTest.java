package com.tuowei.erp.finance.financialstatement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.financialstatement.mapper.FinancialStatementItemTemplateMapper;
import com.tuowei.erp.finance.financialstatement.mapper.FinancialStatementSubjectMapMapper;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementDefinition;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementItemTemplateEntity;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementSubjectMapEntity;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementConfigurationService;
import com.tuowei.erp.finance.financialstatement.web.StatementMappingUpdateRequest;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialStatementConfigurationServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L, 101L, 202L, LocalDateTime.of(2026, 11, 10, 15, 0)
    );

    private final FinancialStatementItemTemplateMapper itemTemplateMapper = mock(FinancialStatementItemTemplateMapper.class);
    private final FinancialStatementSubjectMapMapper subjectMapMapper = mock(FinancialStatementSubjectMapMapper.class);
    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(FinancialStatementItemTemplateEntity.class);
        initTableInfo(FinancialStatementSubjectMapEntity.class);
        initTableInfo(AccountSubjectEntity.class);
    }

    @Test
    void buildsVersionedDefinitionAndReportsInvalidMappings() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(
                template("BS_CASH_BANK", "货币资金", "ASSET", "DEBIT", null, 10),
                template("BS_CURRENT_PROFIT", "累计未结转损益", "EQUITY", "CREDIT", "CUMULATIVE_PROFIT", 20)
        ));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(
                mapping(1L, "BS_CASH_BANK", "1002"),
                mapping(2L, "BS_CASH_BANK", "2202"),
                mapping(3L, "BS_CURRENT_PROFIT", "9999")
        ));
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(
                subject("1002", "ASSET"),
                subject("2202", "LIABILITY")
        ));

        FinancialStatementDefinition definition = service().definition("balance_sheet");

        assertThat(definition.formulaVersion()).isEqualTo("TYPE_MAPPING_V1");
        assertThat(definition.items().get(0).subjectCodes()).containsExactly("1002", "2202");
        assertThat(definition.validationErrors())
                .anyMatch(error -> error.contains("科目类别与报表项目不匹配: 2202"))
                .anyMatch(error -> error.contains("公式项目不允许配置科目"));
    }

    @Test
    void rejectsDuplicateSubjectCodesBeforeWriting() {
        stubSimpleDefinition();

        assertThatThrownBy(() -> service().replaceSubjects(
                "BALANCE_SHEET",
                "BS_CASH_BANK",
                new StatementMappingUpdateRequest(List.of("1002", "1002"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("科目映射不能重复");
    }

    @Test
    void reportsAndRejectsDisabledMappedSubjects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(
                template("BS_CASH_BANK", "货币资金", "ASSET", "DEBIT", null, 10)
        ));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(
                mapping(1L, "BS_CASH_BANK", "1002")
        ));
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(
                subject("1002", "ASSET", "DISABLED")
        ));

        assertThat(service().definition("BALANCE_SHEET").validationErrors())
                .containsExactly("映射引用已停用的会计科目: 1002");
        assertThatThrownBy(() -> service().replaceSubjects(
                "BALANCE_SHEET",
                "BS_CASH_BANK",
                new StatementMappingUpdateRequest(List.of("1002"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目已停用: 1002");
    }

    @Test
    void reassignsSubjectWithinTheSameStatementAndKeepsTenantAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(
                template("BS_CASH_BANK", "货币资金", "ASSET", "DEBIT", null, 10),
                template("BS_OTHER_ASSETS", "其他资产", "ASSET", "DEBIT", null, 20)
        ));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(
                mapping(11L, "BS_OTHER_ASSETS", "1002"),
                mapping(12L, "BS_CASH_BANK", "1001")
        ));
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(
                subject("1001", "ASSET"),
                subject("1002", "ASSET")
        ));

        service().replaceSubjects(
                "BALANCE_SHEET",
                "BS_CASH_BANK",
                new StatementMappingUpdateRequest(List.of("1002"))
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> deleteCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(subjectMapMapper).deleteByIds(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).containsExactlyInAnyOrder(11L, 12L);
        ArgumentCaptor<FinancialStatementSubjectMapEntity> insertCaptor =
                ArgumentCaptor.forClass(FinancialStatementSubjectMapEntity.class);
        verify(subjectMapMapper).insert(insertCaptor.capture());
        FinancialStatementSubjectMapEntity inserted = insertCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(101L);
        assertThat(inserted.getAccountBookId()).isEqualTo(202L);
        assertThat(inserted.getItemCode()).isEqualTo("BS_CASH_BANK");
        assertThat(inserted.getSubjectCode()).isEqualTo("1002");
        assertThat(inserted.getCreatedBy()).isEqualTo(9L);
    }

    private void stubSimpleDefinition() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(
                template("BS_CASH_BANK", "货币资金", "ASSET", "DEBIT", null, 10)
        ));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of());
    }

    private FinancialStatementConfigurationService service() {
        return new FinancialStatementConfigurationService(
                itemTemplateMapper, subjectMapMapper, accountSubjectMapper, auditMetadataFactory
        );
    }

    private FinancialStatementItemTemplateEntity template(
            String code,
            String name,
            String section,
            String direction,
            String formula,
            int sortNo
    ) {
        FinancialStatementItemTemplateEntity entity = new FinancialStatementItemTemplateEntity();
        entity.setFormulaVersion("TYPE_MAPPING_V1");
        entity.setStatementType("BALANCE_SHEET");
        entity.setItemCode(code);
        entity.setItemName(name);
        entity.setSectionCode(section);
        entity.setAmountDirection(direction);
        entity.setFormulaCode(formula);
        entity.setSortNo(sortNo);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private FinancialStatementSubjectMapEntity mapping(Long id, String itemCode, String subjectCode) {
        FinancialStatementSubjectMapEntity entity = new FinancialStatementSubjectMapEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setFormulaVersion("TYPE_MAPPING_V1");
        entity.setStatementType("BALANCE_SHEET");
        entity.setItemCode(itemCode);
        entity.setSubjectCode(subjectCode);
        return entity;
    }

    private AccountSubjectEntity subject(String code, String type) {
        return subject(code, type, "ACTIVE");
    }

    private AccountSubjectEntity subject(String code, String type, String status) {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setSubjectCode(code);
        entity.setSubjectType(type);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
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
