package com.tuowei.erp.finance.subject;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.subject.web.AccountSubjectCreateRequest;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class AccountSubjectServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9941L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 22, 0)
    );
    private static final Long SUBJECT_ID = 5101L;
    private static final Long PARENT_ID = 5201L;

    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(AccountSubjectEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                AccountSubjectEntity.class.getName()
        );
        assistant.setCurrentNamespace(AccountSubjectEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, AccountSubjectEntity.class);
    }

    @Test
    void listScopesSubjectsByCompanyAndAccountBook() {
        stubAudit();
        Page<AccountSubjectEntity> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(accountSubjectMapper.selectPage(any(Page.class), any())).thenReturn(page);

        service().list(new AccountSubjectPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<AccountSubjectEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountSubjectMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void detailRejectsSubjectFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(accountSubjectMapper.selectById(SUBJECT_ID))
                .thenReturn(activeSubject(SUBJECT_ID, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().detail(SUBJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目不存在");
    }

    @Test
    void createRejectsParentFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(accountSubjectMapper.selectCount(any())).thenReturn(0L);
        when(accountSubjectMapper.selectById(PARENT_ID))
                .thenReturn(activeSubject(PARENT_ID, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上级科目不存在");
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private AccountSubjectService service() {
        return new AccountSubjectService(accountSubjectMapper, auditMetadataFactory);
    }

    private AccountSubjectCreateRequest createRequest() {
        return new AccountSubjectCreateRequest(
                "1002",
                "tenant child",
                PARENT_ID,
                "ASSET",
                "DEBIT",
                "tenant boundary"
        );
    }

    private AccountSubjectEntity activeSubject(Long id, Long companyId, Long accountBookId) {
        AccountSubjectEntity subject = new AccountSubjectEntity();
        subject.setId(id);
        subject.setCompanyId(companyId);
        subject.setAccountBookId(accountBookId);
        subject.setSubjectCode("1001");
        subject.setSubjectName("tenant subject");
        subject.setSubjectType("ASSET");
        subject.setBalanceDirection("DEBIT");
        subject.setStatus("ACTIVE");
        subject.setDeletedFlag(0);
        subject.setVersion(0);
        return subject;
    }

    private void assertTenantScoped(LambdaQueryWrapper<AccountSubjectEntity> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }
}
