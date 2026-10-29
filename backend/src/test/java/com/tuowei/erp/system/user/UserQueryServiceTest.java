package com.tuowei.erp.system.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.system.user.service.UserQueryService;
import com.tuowei.erp.system.user.web.UserPageQuery;
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
class UserQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 21, 14, 0)
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(UserEntity.class);
        initTableInfo(UserRoleEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesTenant() {
        UserMapper userMapper = mock(UserMapper.class);
        Page<UserEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(user(9001L, AUDIT.companyId(), AUDIT.accountBookId())));
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        UserPageQuery query = new UserPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  ali ");
        query.setStatus(" active ");
        query.setDeptId(11L);
        query.setPostId(12L);

        var response = service(userMapper, mock(UserRoleMapper.class)).list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(9001L);
            assertThat(item.username()).isEqualTo("alice");
        });

        ArgumentCaptor<Page<UserEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        LambdaQueryWrapper<UserEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "deleted_flag")
                .contains("username", "real_name", "mobile", "employee_no")
                .contains("status", "dept_id", "post_id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%ali%", "ACTIVE", 11L, 12L, 0);
    }

    @Test
    void getByIdRejectsDeletedOrDifferentTenantUser() {
        UserMapper userMapper = mock(UserMapper.class);
        UserQueryService service = service(userMapper, mock(UserRoleMapper.class));

        UserEntity otherBook = user(9002L, AUDIT.companyId(), 999L);
        when(userMapper.selectById(9002L)).thenReturn(otherBook);
        assertThatThrownBy(() -> service.getById(9002L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");

        UserEntity deleted = user(9003L, AUDIT.companyId(), AUDIT.accountBookId());
        deleted.setDeletedFlag(1);
        when(userMapper.selectById(9003L)).thenReturn(deleted);
        assertThatThrownBy(() -> service.getById(9003L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");
    }

    @Test
    void getAssignedRolesGuardsUserThenReturnsStoredOrder() {
        UserMapper userMapper = mock(UserMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        when(userMapper.selectById(9001L)).thenReturn(user(9001L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                userRole(301L),
                userRole(302L)
        ));

        var response = service(userMapper, userRoleMapper).getAssignedRoles(9001L);

        assertThat(response.userId()).isEqualTo(9001L);
        assertThat(response.roleIds()).containsExactly(301L, 302L);
        ArgumentCaptor<LambdaQueryWrapper<UserRoleEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userRoleMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("user_id")
                .contains("order by id asc");
    }

    @Test
    void toResponseNeverLeaksPasswordOrTenantFields() {
        UserMapper userMapper = mock(UserMapper.class);
        UserEntity entity = user(9001L, AUDIT.companyId(), AUDIT.accountBookId());
        entity.setPassword("secret-hash");
        when(userMapper.selectById(9001L)).thenReturn(entity);

        var response = service(userMapper, mock(UserRoleMapper.class)).getById(9001L);

        assertThat(response)
                .extracting(
                        it -> it.id(),
                        it -> it.username(),
                        it -> it.employeeNo(),
                        it -> it.realName(),
                        it -> it.status()
                )
                .containsExactly(9001L, "alice", "EMP-9001", "Alice", "ACTIVE");
        assertThat(response.toString()).doesNotContain("secret-hash", "companyId", "accountBookId");
    }

    private UserQueryService service(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        return new UserQueryService(userMapper, userRoleMapper, auditMetadataFactory);
    }

    private UserEntity user(Long id, Long companyId, Long accountBookId) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setUsername("alice");
        entity.setEmployeeNo("EMP-" + id);
        entity.setRealName("Alice");
        entity.setEmail("alice@example.com");
        entity.setMobile("13800000000");
        entity.setAvatar("avatar.png");
        entity.setDeptId(11L);
        entity.setPostId(12L);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        entity.setVersion(0);
        return entity;
    }

    private UserRoleEntity userRole(Long roleId) {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(9001L);
        entity.setRoleId(roleId);
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
