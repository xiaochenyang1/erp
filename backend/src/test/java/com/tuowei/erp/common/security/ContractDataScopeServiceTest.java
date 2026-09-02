package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );

    private final ContractDataScopeService service = new ContractDataScopeService();

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ContractEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), ContractEntity.class.getName());
        assistant.setCurrentNamespace(ContractEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ContractEntity.class);
    }

    @Test
    void allScopeLeavesQueryUnchanged() {
        LambdaQueryWrapper<ContractEntity> wrapper = new LambdaQueryWrapper<>(ContractEntity.class);

        LambdaQueryWrapper<ContractEntity> scoped = service.applyContractScope(
                wrapper, CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of());

        assertThat(scoped).isSameAs(wrapper);
        assertThat(wrapper.getSqlSegment()).isEmpty();
    }

    @Test
    void emptyScopeRejectsAllRows() {
        LambdaQueryWrapper<ContractEntity> wrapper = service.applyContractScope(
                new LambdaQueryWrapper<>(ContractEntity.class),
                CURRENT_USER,
                DataScopeSnapshot.none(),
                Set.of(),
                Set.of()
        );

        assertThat(wrapper.getSqlSegment()).contains("1 = 0");
    }

    @Test
    void selfDepartmentAndPostScopesFilterVisibleCreators() {
        assertCreatorScope(
                new DataScopeSnapshot(false, false, false, true, Set.of()),
                Set.of(), Set.of(), Set.of(CURRENT_USER.userId()));
        assertCreatorScope(
                new DataScopeSnapshot(false, true, false, false, Set.of()),
                Set.of(21L, 22L), Set.of(), Set.of(21L, 22L));
        assertCreatorScope(
                new DataScopeSnapshot(false, false, true, false, Set.of()),
                Set.of(), Set.of(31L, 32L), Set.of(31L, 32L));
    }

    @Test
    void viewAssertionAllowsSelfDepartmentAndPostScopes() {
        ContractEntity ownContract = contract(CURRENT_USER.userId());
        ContractEntity organizationContract = contract(9999L);

        assertThatCode(() -> service.assertCanViewContract(
                ownContract,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, true, Set.of()),
                null,
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewContract(
                organizationContract,
                CURRENT_USER,
                new DataScopeSnapshot(false, true, false, false, Set.of()),
                CURRENT_USER.deptId(),
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewContract(
                organizationContract,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, true, false, Set.of()),
                null,
                CURRENT_USER.postId()
        )).doesNotThrowAnyException();
    }

    @Test
    void viewAssertionRejectsUnrelatedCreator() {
        assertDenied(() -> service.assertCanViewContract(
                contract(9999L), CURRENT_USER, DataScopeSnapshot.none(), null, null));
    }

    @Test
    void tenantProtectionRunsBeforeAllScopeAcceptance() {
        ContractEntity wrongCompany = contract(CURRENT_USER.userId());
        wrongCompany.setCompanyId(9999L);
        ContractEntity wrongAccountBook = contract(CURRENT_USER.userId());
        wrongAccountBook.setAccountBookId(9999L);

        assertDenied(() -> service.assertCanViewContract(
                wrongCompany, CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewContract(
                wrongAccountBook, CURRENT_USER, DataScopeSnapshot.all(), null, null));
    }

    private void assertCreatorScope(
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        LambdaQueryWrapper<ContractEntity> wrapper = service.applyContractScope(
                new LambdaQueryWrapper<>(ContractEntity.class),
                CURRENT_USER,
                snapshot,
                deptUserIds,
                postUserIds
        );

        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT)).contains("created_by");
        assertThat(wrapper.getParamNameValuePairs().values()).containsAll(expectedCreatorIds);
    }

    private static ContractEntity contract(Long createdBy) {
        ContractEntity entity = new ContractEntity();
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static void assertDenied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AccessDeniedException.class);
    }
}
