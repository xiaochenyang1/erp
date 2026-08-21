package com.tuowei.erp.system.dept;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.dept.service.DeptQueryService;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.dept.web.DeptResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class DeptQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 16, 0)
    );

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(DeptEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), DeptEntity.class.getName()
        );
        assistant.setCurrentNamespace(DeptEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, DeptEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesCompanyAndAccountBook() {
        DeptMapper deptMapper = mock(DeptMapper.class);
        Page<DeptEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(dept(7L, 0L, AUDIT.companyId(), AUDIT.accountBookId())));
        when(deptMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        DeptPageQuery query = new DeptPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  finance ");
        query.setStatus(" active ");
        query.setParentId(9L);

        var response = service(deptMapper).list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.deptCode()).isEqualTo("FINANCE");
            assertThat(item.children()).isEmpty();
        });

        ArgumentCaptor<Page<DeptEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<DeptEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deptMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "deleted_flag")
                .contains("dept_code", "dept_name", "status", "parent_id")
                .contains("order by parent_id asc", "sort_no asc", "id asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%finance%", "ACTIVE", 9L, 0);
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        DeptMapper deptMapper = mock(DeptMapper.class);
        when(deptMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service(deptMapper).list(null);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(20L);
    }

    @Test
    void treeScopesTenantBuildsChildrenAndPromotesOrphansToRoots() {
        DeptMapper deptMapper = mock(DeptMapper.class);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                dept(10L, 0L, AUDIT.companyId(), AUDIT.accountBookId()),
                dept(11L, 10L, AUDIT.companyId(), AUDIT.accountBookId()),
                dept(12L, 999L, AUDIT.companyId(), AUDIT.accountBookId()),
                dept(13L, null, AUDIT.companyId(), AUDIT.accountBookId())
        ));

        List<DeptResponse> tree = service(deptMapper).tree();

        assertThat(tree).extracting(DeptResponse::id).containsExactly(10L, 12L, 13L);
        assertThat(tree.get(0).children()).extracting(DeptResponse::id).containsExactly(11L);
        assertThat(tree.get(1).children()).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<DeptEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deptMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "deleted_flag")
                .contains("order by parent_id asc", "sort_no asc", "id asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0);
    }

    @Test
    void getByIdMapsCurrentTenantAndRejectsMissingDeletedOrCrossTenantDepartments() {
        DeptMapper deptMapper = mock(DeptMapper.class);
        DeptQueryService service = service(deptMapper);

        when(deptMapper.selectById(7L))
                .thenReturn(dept(7L, 0L, AUDIT.companyId(), AUDIT.accountBookId()));
        assertThat(service.getById(7L)).satisfies(response -> {
            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.deptCode()).isEqualTo("FINANCE");
            assertThat(response.children()).isEmpty();
        });

        when(deptMapper.selectById(8L)).thenReturn(null);
        assertDepartmentMissing(() -> service.getById(8L));

        DeptEntity deleted = dept(9L, 0L, AUDIT.companyId(), AUDIT.accountBookId());
        deleted.setDeletedFlag(1);
        when(deptMapper.selectById(9L)).thenReturn(deleted);
        assertDepartmentMissing(() -> service.getById(9L));

        when(deptMapper.selectById(10L))
                .thenReturn(dept(10L, 0L, 999L, AUDIT.accountBookId()));
        assertDepartmentMissing(() -> service.getById(10L));

        when(deptMapper.selectById(11L))
                .thenReturn(dept(11L, 0L, AUDIT.companyId(), 999L));
        assertDepartmentMissing(() -> service.getById(11L));
    }

    private void assertDepartmentMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
    }

    private DeptQueryService service(DeptMapper deptMapper) {
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        return new DeptQueryService(deptMapper, auditMetadataFactory);
    }

    private DeptEntity dept(Long id, Long parentId, Long companyId, Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setParentId(parentId);
        entity.setDeptCode("FINANCE");
        entity.setDeptName("财务部");
        entity.setLeaderUserId(7001L);
        entity.setSortNo(id.intValue());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        entity.setVersion(0);
        return entity;
    }
}
