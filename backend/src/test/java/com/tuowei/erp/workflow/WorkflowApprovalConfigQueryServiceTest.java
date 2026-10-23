package com.tuowei.erp.workflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalConfigMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalNodeApproverMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalNodeMapper;
import com.tuowei.erp.workflow.model.WorkflowApprovalConfigEntity;
import com.tuowei.erp.workflow.model.WorkflowApprovalNodeApproverEntity;
import com.tuowei.erp.workflow.model.WorkflowApprovalNodeEntity;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigQueryService;
import com.tuowei.erp.workflow.web.WorkflowApprovalApproverResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class WorkflowApprovalConfigQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-08-14T10:30:00")
    );

    @Mock
    private WorkflowApprovalConfigMapper configMapper;

    @Mock
    private WorkflowApprovalNodeMapper nodeMapper;

    @Mock
    private WorkflowApprovalNodeApproverMapper approverMapper;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RoleMenuMapper roleMenuMapper;

    @Mock
    private MenuMapper menuMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(WorkflowApprovalConfigEntity.class);
        initTableInfo(WorkflowApprovalNodeEntity.class);
        initTableInfo(WorkflowApprovalNodeApproverEntity.class);
        initTableInfo(UserEntity.class);
        initTableInfo(UserRoleEntity.class);
        initTableInfo(RoleEntity.class);
        initTableInfo(RoleMenuEntity.class);
        initTableInfo(MenuEntity.class);
    }

    @Test
    void missingConfigReturnsDisabledDefaults() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);

        var response = service().getByBusinessType(" sales_order ");

        assertThat(response.id()).isNull();
        assertThat(response.businessType()).isEqualTo("SALES_ORDER");
        assertThat(response.configName()).isNull();
        assertThat(response.status()).isEqualTo("DISABLED");
        assertThat(response.taskTimeoutHours()).isEqualTo(24);
        assertThat(response.remark()).isNull();
        assertThat(response.nodes()).isEmpty();
        verifyNoInteractions(nodeMapper, approverMapper);
    }

    @Test
    void configResponseLoadsNodesAndApproversInBatches() {
        WorkflowApprovalConfigEntity config = config();
        WorkflowApprovalNodeEntity first = node(1101L, 1, "财务审批");
        WorkflowApprovalNodeEntity second = node(1102L, 2, "总经理审批");
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(configMapper.selectOne(any())).thenReturn(config);
        when(nodeMapper.selectList(any())).thenReturn(List.of(first, second));
        when(approverMapper.selectList(any())).thenReturn(List.of(
                approver(2203L, second.getId(), "ROLE", 3303L),
                approver(2202L, first.getId(), "USER", 3302L),
                approver(2201L, first.getId(), "ROLE", 3301L)
        ));

        var response = service().getByBusinessType("sales_order");

        assertThat(response.nodes()).extracting(node -> node.id())
                .containsExactly(first.getId(), second.getId());
        assertThat(response.nodes().get(0).approvers())
                .extracting(WorkflowApprovalApproverResponse::id)
                .containsExactly(2201L, 2202L);
        assertThat(response.nodes().get(1).approvers())
                .extracting(WorkflowApprovalApproverResponse::id)
                .containsExactly(2203L);

        ArgumentCaptor<LambdaQueryWrapper<WorkflowApprovalNodeEntity>> nodeQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<WorkflowApprovalNodeApproverEntity>> approverQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(nodeMapper).selectList(nodeQueryCaptor.capture());
        verify(approverMapper).selectList(approverQueryCaptor.capture());
        assertThat(nodeQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "config_id", "node_order", "order by");
        assertThat(approverQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "node_id", "in", "order by");
        assertThat(approverQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(first.getId(), second.getId());
    }

    @Test
    void resolvesFirstAndNextActiveNodesInConfiguredOrder() {
        WorkflowApprovalNodeEntity first = node(1101L, 1, "一级审批");
        WorkflowApprovalNodeEntity second = node(1102L, 2, "二级审批");
        WorkflowInstanceEntity instance = instance();
        when(configMapper.selectOne(any())).thenReturn(config());
        when(nodeMapper.selectList(any())).thenReturn(List.of(first, second));

        WorkflowApprovalConfigQueryService service = service();

        assertThat(service.resolveFirstActiveNode(instance, AUDIT)).isSameAs(first);
        assertThat(service.resolveNextActiveNode(instance, first.getId(), AUDIT)).isSameAs(second);
        assertThat(service.resolveNextActiveNode(instance, second.getId(), AUDIT)).isNull();
        assertThat(service.resolveNextActiveNode(instance, 9999L, AUDIT)).isNull();
    }

    @Test
    void expandsUserAndRoleApproversAndScopesEligibleUsers() {
        WorkflowApprovalNodeEntity node = node(1101L, 1, "一级审批");
        WorkflowInstanceEntity instance = instance();
        instance.setSubmitUserId(700L);
        when(configMapper.selectOne(any())).thenReturn(config());
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(approverMapper.selectList(any())).thenReturn(List.of(
                approver(2201L, node.getId(), "USER", 700L),
                approver(2202L, node.getId(), "USER", 701L),
                approver(2203L, node.getId(), "USER", 702L),
                approver(2204L, node.getId(), "USER", 703L),
                approver(2205L, node.getId(), "ROLE", 900L)
        ));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(900L)));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                userRole(900L, 704L),
                userRole(900L, 700L)
        ));
        when(userMapper.selectList(any())).thenReturn(List.of(user(701L), user(704L)));

        List<Long> approverUserIds = service().resolveConfiguredNodeApproverUserIds(
                instance, node.getId(), AUDIT);

        assertThat(approverUserIds).containsExactly(701L, 704L);

        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> roleQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> userQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectList(roleQueryCaptor.capture());
        verify(userMapper).selectList(userQueryCaptor.capture());
        assertThat(roleQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "status", "deleted_flag");
        assertThat(userQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "status", "deleted_flag");
        assertThat(userQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(701L, 702L, 703L, 704L)
                .doesNotContain(700L);
        verify(menuMapper, never()).selectList(any());
        verify(roleMenuMapper, never()).selectList(any());
    }

    private WorkflowApprovalConfigQueryService service() {
        return new WorkflowApprovalConfigQueryService(
                configMapper,
                nodeMapper,
                approverMapper,
                auditMetadataFactory,
                userMapper,
                userRoleMapper,
                roleMapper,
                roleMenuMapper,
                menuMapper
        );
    }

    private WorkflowApprovalConfigEntity config() {
        WorkflowApprovalConfigEntity entity = new WorkflowApprovalConfigEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBusinessType("SALES_ORDER");
        entity.setConfigName("销售订单审批");
        entity.setStatus("ACTIVE");
        entity.setTaskTimeoutHours(12);
        entity.setDeletedFlag(0);
        return entity;
    }

    private WorkflowApprovalNodeEntity node(Long id, int order, String name) {
        WorkflowApprovalNodeEntity entity = new WorkflowApprovalNodeEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setConfigId(1001L);
        entity.setNodeName(name);
        entity.setNodeOrder(order);
        entity.setApprovalMode("ANY");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private WorkflowApprovalNodeApproverEntity approver(
            Long id,
            Long nodeId,
            String type,
            Long approverId
    ) {
        WorkflowApprovalNodeApproverEntity entity = new WorkflowApprovalNodeApproverEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setNodeId(nodeId);
        entity.setApproverType(type);
        entity.setApproverId(approverId);
        return entity;
    }

    private WorkflowInstanceEntity instance() {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBusinessType("SALES_ORDER");
        entity.setSubmitUserId(700L);
        return entity;
    }

    private RoleEntity role(Long id) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserRoleEntity userRole(Long roleId, Long userId) {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setRoleId(roleId);
        entity.setUserId(userId);
        return entity;
    }

    private UserEntity user(Long id) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setStatus("ACTIVE");
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
