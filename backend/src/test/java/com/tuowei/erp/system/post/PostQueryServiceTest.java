package com.tuowei.erp.system.post;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.post.service.PostQueryService;
import com.tuowei.erp.system.post.web.PostPageQuery;
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
class PostQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 17, 0)
    );

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(PostEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), PostEntity.class.getName()
        );
        assistant.setCurrentNamespace(PostEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, PostEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesCompanyAndAccountBook() {
        PostMapper postMapper = mock(PostMapper.class);
        Page<PostEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(post(7L, AUDIT.companyId(), AUDIT.accountBookId())));
        when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        PostPageQuery query = new PostPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  buyer ");
        query.setStatus(" active ");
        query.setDeptId(6001L);

        var response = service(postMapper).list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.postCode()).isEqualTo("BUYER");
            assertThat(item.deptId()).isEqualTo(6001L);
        });

        ArgumentCaptor<Page<PostEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<PostEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(postMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "deleted_flag")
                .contains("post_code", "post_name", "status", "dept_id")
                .contains("order by post_code asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%buyer%", "ACTIVE", 6001L, 0);
    }

    @Test
    void listNullQueryUsesDefaultPaginationWithoutOptionalFilters() {
        PostMapper postMapper = mock(PostMapper.class);
        when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service(postMapper).list(null);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(20L);
        ArgumentCaptor<LambdaQueryWrapper<PostEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(postMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "deleted_flag")
                .doesNotContain("post_name", "status", "dept_id");
    }

    @Test
    void getByIdMapsCurrentTenantPost() {
        PostMapper postMapper = mock(PostMapper.class);
        when(postMapper.selectById(7L))
                .thenReturn(post(7L, AUDIT.companyId(), AUDIT.accountBookId()));

        var response = service(postMapper).getById(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.deptId()).isEqualTo(6001L);
        assertThat(response.postCode()).isEqualTo("BUYER");
        assertThat(response.postName()).isEqualTo("采购员");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getByIdRejectsMissingDeletedOrCrossTenantPost() {
        PostMapper postMapper = mock(PostMapper.class);
        PostQueryService service = service(postMapper);

        when(postMapper.selectById(7L)).thenReturn(null);
        assertPostMissing(() -> service.getById(7L));

        PostEntity deleted = post(8L, AUDIT.companyId(), AUDIT.accountBookId());
        deleted.setDeletedFlag(1);
        when(postMapper.selectById(8L)).thenReturn(deleted);
        assertPostMissing(() -> service.getById(8L));

        when(postMapper.selectById(9L))
                .thenReturn(post(9L, 999L, AUDIT.accountBookId()));
        assertPostMissing(() -> service.getById(9L));

        when(postMapper.selectById(10L))
                .thenReturn(post(10L, AUDIT.companyId(), 999L));
        assertPostMissing(() -> service.getById(10L));
    }

    private void assertPostMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在");
    }

    private PostQueryService service(PostMapper postMapper) {
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        return new PostQueryService(postMapper, auditMetadataFactory);
    }

    private PostEntity post(Long id, Long companyId, Long accountBookId) {
        PostEntity entity = new PostEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setDeptId(6001L);
        entity.setPostCode("BUYER");
        entity.setPostName("采购员");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        entity.setVersion(0);
        return entity;
    }
}
