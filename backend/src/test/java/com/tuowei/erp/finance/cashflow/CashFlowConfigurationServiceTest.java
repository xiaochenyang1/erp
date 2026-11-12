package com.tuowei.erp.finance.cashflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.cashflow.mapper.CashEquivalentSubjectMapper;
import com.tuowei.erp.finance.cashflow.mapper.CashFlowItemTemplateMapper;
import com.tuowei.erp.finance.cashflow.mapper.CashFlowSubjectMapMapper;
import com.tuowei.erp.finance.cashflow.model.CashEquivalentSubjectEntity;
import com.tuowei.erp.finance.cashflow.model.CashFlowDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowItemTemplateEntity;
import com.tuowei.erp.finance.cashflow.model.CashFlowSubjectMapEntity;
import com.tuowei.erp.finance.cashflow.service.CashFlowConfigurationService;
import com.tuowei.erp.finance.cashflow.web.CashFlowSubjectCodesRequest;
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

class CashFlowConfigurationServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L, 101L, 202L, LocalDateTime.of(2026, 11, 10, 15, 0)
    );

    private final CashFlowItemTemplateMapper itemTemplateMapper = mock(CashFlowItemTemplateMapper.class);
    private final CashEquivalentSubjectMapper cashSubjectMapper = mock(CashEquivalentSubjectMapper.class);
    private final CashFlowSubjectMapMapper subjectMapMapper = mock(CashFlowSubjectMapMapper.class);
    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(CashFlowItemTemplateEntity.class);
        initTableInfo(CashEquivalentSubjectEntity.class);
        initTableInfo(CashFlowSubjectMapEntity.class);
        initTableInfo(AccountSubjectEntity.class);
    }

    @Test
    void buildsDefinitionAndReportsCashCounterpartConflicts() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(template("CFO_OTHER", "OPERATING", 10)));
        when(cashSubjectMapper.selectList(any())).thenReturn(List.of(cashSubject(1L, "1002")));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(
                mapping(2L, "CFO_OTHER", "1002"),
                mapping(3L, "MISSING", "9999")
        ));
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(subject("1002", "ASSET")));

        CashFlowDefinition definition = service().definition();

        assertThat(definition.formulaVersion()).isEqualTo("CASH_FLOW_DIRECT_V1");
        assertThat(definition.validationErrors())
                .anyMatch(error -> error.contains("现金科目不能作为对方科目映射: 1002"))
                .anyMatch(error -> error.contains("不存在的现金流量项目: MISSING"));
    }

    @Test
    void rejectsNonAssetCashSubjects() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(subject("2202", "LIABILITY")));

        assertThatThrownBy(() -> service().replaceCashSubjects(
                new CashFlowSubjectCodesRequest(List.of("2202"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是资产类科目");
    }

    @Test
    void rejectsCashSubjectAlreadyUsedAsCounterpart() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(subject("1002", "ASSET")));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(mapping(2L, "CFO_OTHER", "1002")));

        assertThatThrownBy(() -> service().replaceCashSubjects(
                new CashFlowSubjectCodesRequest(List.of("1002"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能同时设为现金科目");
    }

    @Test
    void reportsDisabledConfiguredSubjectsAndRejectsNewDisabledMappings() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(template("CFO_OTHER", "OPERATING", 10)));
        when(cashSubjectMapper.selectList(any())).thenReturn(List.of(cashSubject(1L, "1002")));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(mapping(2L, "CFO_OTHER", "1122")));
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(
                subject("1002", "ASSET", "DISABLED"),
                subject("1122", "ASSET", "DISABLED")
        ));

        assertThat(service().definition().validationErrors())
                .containsExactlyInAnyOrder(
                        "现金范围引用已停用的会计科目: 1002",
                        "映射引用已停用的会计科目: 1122"
                );
        assertThatThrownBy(() -> service().replaceCashSubjects(
                new CashFlowSubjectCodesRequest(List.of("1002"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目已停用: 1002");
        assertThatThrownBy(() -> service().replaceItemSubjects(
                "CFO_OTHER",
                new CashFlowSubjectCodesRequest(List.of("1122"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目已停用: 1122");
    }

    @Test
    void reassignsCounterpartAndKeepsTenantAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(itemTemplateMapper.selectList(any())).thenReturn(List.of(
                template("CFO_OTHER", "OPERATING", 10),
                template("CFI_OTHER", "INVESTING", 20)
        ));
        when(cashSubjectMapper.selectList(any())).thenReturn(List.of(cashSubject(1L, "1002")));
        when(subjectMapMapper.selectList(any())).thenReturn(List.of(
                mapping(11L, "CFI_OTHER", "1122"),
                mapping(12L, "CFO_OTHER", "2202")
        ));
        when(accountSubjectMapper.selectList(any())).thenReturn(List.of(
                subject("1002", "ASSET"), subject("1122", "ASSET"), subject("2202", "LIABILITY")
        ));

        service().replaceItemSubjects("CFO_OTHER", new CashFlowSubjectCodesRequest(List.of("1122")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> deleteCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(subjectMapMapper).deleteByIds(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).containsExactlyInAnyOrder(11L, 12L);
        ArgumentCaptor<CashFlowSubjectMapEntity> insertCaptor =
                ArgumentCaptor.forClass(CashFlowSubjectMapEntity.class);
        verify(subjectMapMapper).insert(insertCaptor.capture());
        CashFlowSubjectMapEntity inserted = insertCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(101L);
        assertThat(inserted.getAccountBookId()).isEqualTo(202L);
        assertThat(inserted.getItemCode()).isEqualTo("CFO_OTHER");
        assertThat(inserted.getSubjectCode()).isEqualTo("1122");
        assertThat(inserted.getCreatedBy()).isEqualTo(9L);
    }

    private CashFlowConfigurationService service() {
        return new CashFlowConfigurationService(
                itemTemplateMapper, cashSubjectMapper, subjectMapMapper,
                accountSubjectMapper, auditMetadataFactory
        );
    }

    private CashFlowItemTemplateEntity template(String code, String activityType, int sortNo) {
        CashFlowItemTemplateEntity entity = new CashFlowItemTemplateEntity();
        entity.setFormulaVersion("CASH_FLOW_DIRECT_V1");
        entity.setItemCode(code);
        entity.setItemName(code);
        entity.setActivityType(activityType);
        entity.setSortNo(sortNo);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private CashEquivalentSubjectEntity cashSubject(Long id, String code) {
        CashEquivalentSubjectEntity entity = new CashEquivalentSubjectEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setFormulaVersion("CASH_FLOW_DIRECT_V1");
        entity.setSubjectCode(code);
        return entity;
    }

    private CashFlowSubjectMapEntity mapping(Long id, String itemCode, String subjectCode) {
        CashFlowSubjectMapEntity entity = new CashFlowSubjectMapEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setFormulaVersion("CASH_FLOW_DIRECT_V1");
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
