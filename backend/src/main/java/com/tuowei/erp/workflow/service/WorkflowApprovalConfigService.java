package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.PermissionCodes;
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
import com.tuowei.erp.workflow.web.WorkflowApprovalApproverRequest;
import com.tuowei.erp.workflow.web.WorkflowApprovalApproverResponse;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigRequest;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigResponse;
import com.tuowei.erp.workflow.web.WorkflowApprovalNodeRequest;
import com.tuowei.erp.workflow.web.WorkflowApprovalNodeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkflowApprovalConfigService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String APPROVER_USER = "USER";
    private static final String APPROVER_ROLE = "ROLE";

    private final WorkflowApprovalConfigMapper configMapper;
    private final WorkflowApprovalNodeMapper nodeMapper;
    private final WorkflowApprovalNodeApproverMapper approverMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    public WorkflowApprovalConfigService(
            WorkflowApprovalConfigMapper configMapper,
            WorkflowApprovalNodeMapper nodeMapper,
            WorkflowApprovalNodeApproverMapper approverMapper,
            AuditMetadataFactory auditMetadataFactory,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            MenuMapper menuMapper
    ) {
        this.configMapper = configMapper;
        this.nodeMapper = nodeMapper;
        this.approverMapper = approverMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    @Transactional(readOnly = true)
    public WorkflowApprovalConfigResponse getByBusinessType(String businessType) {
        AuditMetadata audit = auditMetadataFactory.current();
        String normalizedBusinessType = normalizeCode(businessType);
        WorkflowApprovalConfigEntity config = findConfig(audit, normalizedBusinessType);
        if (config == null) {
            return new WorkflowApprovalConfigResponse(null, normalizedBusinessType, null, STATUS_DISABLED, null, List.of());
        }
        return toResponse(config);
    }

    @Transactional
    public WorkflowApprovalConfigResponse save(String businessType, WorkflowApprovalConfigRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        String normalizedBusinessType = normalizeCode(businessType);
        WorkflowApprovalConfigEntity config = findConfig(audit, normalizedBusinessType);
        if (config == null) {
            config = new WorkflowApprovalConfigEntity();
            config.setCompanyId(audit.companyId());
            config.setAccountBookId(audit.accountBookId());
            config.setBusinessType(normalizedBusinessType);
            config.setDeletedFlag(0);
            config.setCreatedBy(audit.userId());
            config.setCreatedTime(now);
            config.setVersion(0);
        }
        config.setConfigName(request.configName().trim());
        config.setStatus(normalizeStatus(request.status()));
        config.setRemark(normalizeNullableText(request.remark()));
        config.setUpdatedBy(audit.userId());
        config.setUpdatedTime(now);

        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            OptimisticLockGuard.requireUpdated(configMapper.updateById(config), "审批配置已被其他操作修改，请刷新后重试");
        }
        replaceNodes(config.getId(), audit, now, request.nodes());
        return toResponse(config);
    }

    public WorkflowApprovalNodeEntity resolveFirstActiveNode(WorkflowInstanceEntity instance, AuditMetadata audit) {
        List<WorkflowApprovalNodeEntity> activeNodes = activeNodes(instance, audit);
        return activeNodes.isEmpty() ? null : activeNodes.get(0);
    }

    public WorkflowApprovalNodeEntity resolveNextActiveNode(
            WorkflowInstanceEntity instance,
            Long currentNodeId,
            AuditMetadata audit
    ) {
        if (currentNodeId == null) {
            return null;
        }
        List<WorkflowApprovalNodeEntity> activeNodes = activeNodes(instance, audit);
        for (int i = 0; i < activeNodes.size(); i++) {
            if (Objects.equals(activeNodes.get(i).getId(), currentNodeId)) {
                return i + 1 < activeNodes.size() ? activeNodes.get(i + 1) : null;
            }
        }
        return null;
    }

    public List<Long> resolvePendingApproverUserIds(WorkflowInstanceEntity instance, AuditMetadata audit) {
        WorkflowApprovalNodeEntity firstNode = resolveFirstActiveNode(instance, audit);
        return resolvePendingApproverUserIds(instance, firstNode == null ? null : firstNode.getId(), audit);
    }

    public List<Long> resolvePendingApproverUserIds(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        List<Long> configuredUserIds = resolveConfiguredApproverUserIds(instance, approvalNodeId, audit);
        if (!configuredUserIds.isEmpty()) {
            return configuredUserIds;
        }
        return workflowViewerUserIds(audit.companyId(), audit.accountBookId(), instance.getSubmitUserId());
    }

    public List<Long> resolveConfiguredNodeApproverUserIds(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        return resolveConfiguredApproverUserIds(instance, approvalNodeId, audit);
    }

    public boolean isAllApprovalMode(WorkflowInstanceEntity instance, Long approvalNodeId, AuditMetadata audit) {
        WorkflowApprovalNodeEntity node = resolveActiveNode(instance, approvalNodeId, audit);
        return node != null && "ALL".equals(node.getApprovalMode());
    }

    public void assertCurrentUserCanApprove(WorkflowInstanceEntity instance, AuditMetadata audit) {
        assertCurrentUserCanApprove(instance, null, audit);
    }

    public void assertCurrentUserCanApprove(WorkflowInstanceEntity instance, Long approvalNodeId, AuditMetadata audit) {
        List<Long> configuredUserIds = resolveConfiguredApproverUserIds(instance, approvalNodeId, audit);
        if (!configuredUserIds.isEmpty() && !configuredUserIds.contains(audit.userId())) {
            throw new IllegalArgumentException("当前用户不是该单据审批人");
        }
    }

    private List<Long> resolveConfiguredApproverUserIds(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        WorkflowApprovalNodeEntity node = resolveActiveNode(instance, approvalNodeId, audit);
        if (node == null) {
            return List.of();
        }
        List<WorkflowApprovalNodeApproverEntity> approvers = approverMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalNodeApproverEntity>()
                .eq(WorkflowApprovalNodeApproverEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalNodeApproverEntity::getNodeId, node.getId())
                .orderByAsc(WorkflowApprovalNodeApproverEntity::getId));
        return resolveApprovers(approvers, audit.companyId(), audit.accountBookId(), instance.getSubmitUserId());
    }

    private WorkflowApprovalNodeEntity resolveActiveNode(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        WorkflowApprovalConfigEntity config = findConfig(audit, normalizeCode(instance.getBusinessType()));
        if (config == null || !STATUS_ACTIVE.equals(config.getStatus())) {
            return null;
        }
        return approvalNodeId == null
                ? firstActiveNode(config, audit)
                : activeNodeById(config, approvalNodeId, audit);
    }

    private List<WorkflowApprovalNodeEntity> activeNodes(WorkflowInstanceEntity instance, AuditMetadata audit) {
        WorkflowApprovalConfigEntity config = findConfig(audit, normalizeCode(instance.getBusinessType()));
        if (config == null || !STATUS_ACTIVE.equals(config.getStatus())) {
            return List.of();
        }
        return nodeMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalNodeEntity::getConfigId, config.getId())
                .eq(WorkflowApprovalNodeEntity::getStatus, STATUS_ACTIVE)
                .orderByAsc(WorkflowApprovalNodeEntity::getNodeOrder)
                .orderByAsc(WorkflowApprovalNodeEntity::getId));
    }

    private WorkflowApprovalNodeEntity firstActiveNode(WorkflowApprovalConfigEntity config, AuditMetadata audit) {
        return nodeMapper.selectOne(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalNodeEntity::getConfigId, config.getId())
                .eq(WorkflowApprovalNodeEntity::getStatus, STATUS_ACTIVE)
                .orderByAsc(WorkflowApprovalNodeEntity::getNodeOrder)
                .orderByAsc(WorkflowApprovalNodeEntity::getId)
                .last("limit 1"));
    }

    private WorkflowApprovalNodeEntity activeNodeById(
            WorkflowApprovalConfigEntity config,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        return nodeMapper.selectOne(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalNodeEntity::getConfigId, config.getId())
                .eq(WorkflowApprovalNodeEntity::getStatus, STATUS_ACTIVE)
                .eq(WorkflowApprovalNodeEntity::getId, approvalNodeId)
                .last("limit 1"));
    }

    private List<Long> resolveApprovers(
            List<WorkflowApprovalNodeApproverEntity> approvers,
            Long companyId,
            Long accountBookId,
            Long submitUserId
    ) {
        if (approvers.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> candidateUserIds = approvers.stream()
                .filter(approver -> APPROVER_USER.equals(approver.getApproverType()))
                .map(WorkflowApprovalNodeApproverEntity::getApproverId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Long> roleIds = approvers.stream()
                .filter(approver -> APPROVER_ROLE.equals(approver.getApproverType()))
                .map(WorkflowApprovalNodeApproverEntity::getApproverId)
                .distinct()
                .toList();
        if (!roleIds.isEmpty()) {
            List<Long> activeRoleIds = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                            .eq(RoleEntity::getCompanyId, companyId)
                            .eq(RoleEntity::getAccountBookId, accountBookId)
                            .eq(RoleEntity::getStatus, STATUS_ACTIVE)
                            .eq(RoleEntity::getDeletedFlag, 0)
                            .in(RoleEntity::getId, roleIds))
                    .stream()
                    .map(RoleEntity::getId)
                    .toList();
            if (!activeRoleIds.isEmpty()) {
                userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                                .in(UserRoleEntity::getRoleId, activeRoleIds))
                        .stream()
                        .map(UserRoleEntity::getUserId)
                        .forEach(candidateUserIds::add);
            }
        }
        return filterActiveUsers(candidateUserIds, companyId, accountBookId, submitUserId);
    }

    private List<Long> workflowViewerUserIds(Long companyId, Long accountBookId, Long submitUserId) {
        List<RoleEntity> activeRoles = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCompanyId, companyId)
                .eq(RoleEntity::getAccountBookId, accountBookId)
                .eq(RoleEntity::getStatus, STATUS_ACTIVE)
                .eq(RoleEntity::getDeletedFlag, 0));
        if (activeRoles.isEmpty()) {
            return List.of();
        }
        Map<Long, RoleEntity> roleById = activeRoles.stream().collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        Set<Long> eligibleRoleIds = activeRoles.stream()
                .filter(role -> "SUPER_ADMIN".equals(role.getRoleCode()))
                .map(RoleEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Long> workflowMenuIds = menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                        .eq(MenuEntity::getPermission, PermissionCodes.WORKFLOW_VIEW)
                        .eq(MenuEntity::getStatus, STATUS_ACTIVE)
                        .eq(MenuEntity::getDeletedFlag, 0))
                .stream()
                .map(MenuEntity::getId)
                .toList();
        if (!workflowMenuIds.isEmpty()) {
            roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                            .in(RoleMenuEntity::getRoleId, roleById.keySet())
                            .in(RoleMenuEntity::getMenuId, workflowMenuIds))
                    .stream()
                    .map(RoleMenuEntity::getRoleId)
                    .forEach(eligibleRoleIds::add);
        }
        if (eligibleRoleIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> candidateUserIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .in(UserRoleEntity::getRoleId, eligibleRoleIds))
                .stream()
                .map(UserRoleEntity::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return filterActiveUsers(candidateUserIds, companyId, accountBookId, submitUserId);
    }

    private List<Long> filterActiveUsers(
            LinkedHashSet<Long> candidateUserIds,
            Long companyId,
            Long accountBookId,
            Long submitUserId
    ) {
        List<Long> userIds = candidateUserIds.stream()
                .filter(userId -> userId != null && !Objects.equals(userId, submitUserId))
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getId, userIds)
                        .eq(UserEntity::getCompanyId, companyId)
                        .eq(UserEntity::getAccountBookId, accountBookId)
                        .eq(UserEntity::getStatus, STATUS_ACTIVE)
                        .eq(UserEntity::getDeletedFlag, 0))
                .stream()
                .map(UserEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private void replaceNodes(
            Long configId,
            AuditMetadata audit,
            LocalDateTime now,
            List<WorkflowApprovalNodeRequest> nodeRequests
    ) {
        List<Long> existingNodeIds = nodeMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                        .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                        .eq(WorkflowApprovalNodeEntity::getConfigId, configId))
                .stream()
                .map(WorkflowApprovalNodeEntity::getId)
                .toList();
        if (!existingNodeIds.isEmpty()) {
            approverMapper.delete(new LambdaQueryWrapper<WorkflowApprovalNodeApproverEntity>()
                    .eq(WorkflowApprovalNodeApproverEntity::getCompanyId, audit.companyId())
                    .in(WorkflowApprovalNodeApproverEntity::getNodeId, existingNodeIds));
        }
        nodeMapper.delete(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalNodeEntity::getConfigId, configId));

        int index = 1;
        for (WorkflowApprovalNodeRequest nodeRequest : nodeRequests) {
            WorkflowApprovalNodeEntity node = new WorkflowApprovalNodeEntity();
            node.setCompanyId(audit.companyId());
            node.setConfigId(configId);
            node.setNodeName(nodeRequest.nodeName().trim());
            node.setNodeOrder(nodeRequest.nodeOrder() == null ? index : nodeRequest.nodeOrder());
            node.setApprovalMode(normalizeApprovalMode(nodeRequest.approvalMode()));
            node.setStatus(STATUS_ACTIVE);
            node.setCreatedBy(audit.userId());
            node.setCreatedTime(now);
            node.setUpdatedBy(audit.userId());
            node.setUpdatedTime(now);
            node.setVersion(0);
            nodeMapper.insert(node);
            for (WorkflowApprovalApproverRequest approverRequest : nodeRequest.approvers()) {
                WorkflowApprovalNodeApproverEntity approver = new WorkflowApprovalNodeApproverEntity();
                approver.setCompanyId(audit.companyId());
                approver.setNodeId(node.getId());
                approver.setApproverType(normalizeApproverType(approverRequest.approverType()));
                approver.setApproverId(approverRequest.approverId());
                approver.setCreatedBy(audit.userId());
                approver.setCreatedTime(now);
                approver.setUpdatedBy(audit.userId());
                approver.setUpdatedTime(now);
                approver.setVersion(0);
                approverMapper.insert(approver);
            }
            index++;
        }
    }

    private WorkflowApprovalConfigResponse toResponse(WorkflowApprovalConfigEntity config) {
        List<WorkflowApprovalNodeEntity> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                .eq(WorkflowApprovalNodeEntity::getCompanyId, config.getCompanyId())
                .eq(WorkflowApprovalNodeEntity::getConfigId, config.getId())
                .orderByAsc(WorkflowApprovalNodeEntity::getNodeOrder)
                .orderByAsc(WorkflowApprovalNodeEntity::getId));
        Map<Long, List<WorkflowApprovalNodeApproverEntity>> approversByNodeId = loadApproversByNodeId(config.getCompanyId(), nodes);
        List<WorkflowApprovalNodeResponse> nodeResponses = nodes.stream()
                .map(node -> new WorkflowApprovalNodeResponse(
                        node.getId(),
                        node.getNodeName(),
                        node.getNodeOrder(),
                        node.getApprovalMode(),
                        node.getStatus(),
                        approversByNodeId.getOrDefault(node.getId(), List.of()).stream()
                                .map(approver -> new WorkflowApprovalApproverResponse(
                                        approver.getId(),
                                        approver.getApproverType(),
                                        approver.getApproverId()))
                                .toList()
                ))
                .toList();
        return new WorkflowApprovalConfigResponse(
                config.getId(),
                config.getBusinessType(),
                config.getConfigName(),
                config.getStatus(),
                config.getRemark(),
                nodeResponses
        );
    }

    private Map<Long, List<WorkflowApprovalNodeApproverEntity>> loadApproversByNodeId(
            Long companyId,
            List<WorkflowApprovalNodeEntity> nodes
    ) {
        List<Long> nodeIds = nodes.stream().map(WorkflowApprovalNodeEntity::getId).toList();
        if (nodeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return approverMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalNodeApproverEntity>()
                        .eq(WorkflowApprovalNodeApproverEntity::getCompanyId, companyId)
                        .in(WorkflowApprovalNodeApproverEntity::getNodeId, nodeIds)
                        .orderByAsc(WorkflowApprovalNodeApproverEntity::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        WorkflowApprovalNodeApproverEntity::getNodeId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(WorkflowApprovalNodeApproverEntity::getId))
                                .toList())
                ));
    }

    private WorkflowApprovalConfigEntity findConfig(AuditMetadata audit, String businessType) {
        return configMapper.selectOne(new LambdaQueryWrapper<WorkflowApprovalConfigEntity>()
                .eq(WorkflowApprovalConfigEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalConfigEntity::getAccountBookId, audit.accountBookId())
                .eq(WorkflowApprovalConfigEntity::getBusinessType, businessType)
                .eq(WorkflowApprovalConfigEntity::getDeletedFlag, 0)
                .last("limit 1"));
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : STATUS_ACTIVE;
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new IllegalArgumentException("审批配置状态无效");
        }
        return normalized;
    }

    private String normalizeApprovalMode(String approvalMode) {
        String normalized = StringUtils.hasText(approvalMode) ? approvalMode.trim().toUpperCase(Locale.ROOT) : "ANY";
        if (!"ANY".equals(normalized) && !"ALL".equals(normalized)) {
            throw new IllegalArgumentException("审批模式无效");
        }
        return normalized;
    }

    private String normalizeApproverType(String approverType) {
        String normalized = normalizeCode(approverType);
        if (!APPROVER_USER.equals(normalized) && !APPROVER_ROLE.equals(normalized)) {
            throw new IllegalArgumentException("审批人类型无效");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("编码不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
