package com.tuowei.erp.system.dept.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.dept.web.DeptCreateRequest;
import com.tuowei.erp.system.dept.web.DeptResponse;
import com.tuowei.erp.system.dept.web.DeptUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeptCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 16, 30)
    );
    private static final Long DEPT_ID = 7001L;
    private static final Long PARENT_ID = 6001L;

    @Mock private DeptMapper deptMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private DeptQueryService deptQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createGuardsParentAndBuildsTenantAuditedActiveDepartment() {
        DeptEntity parent = dept(PARENT_ID);
        when(deptQueryService.requireParentDept(
                PARENT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        )).thenReturn(parent);
        when(deptMapper.insert(any(DeptEntity.class))).thenAnswer(invocation -> {
            DeptEntity entity = invocation.getArgument(0);
            entity.setId(DEPT_ID);
            return 1;
        });
        DeptResponse expected = response("ACTIVE");
        when(deptQueryService.toResponse(any(DeptEntity.class))).thenReturn(expected);

        DeptResponse actual = service().create(new DeptCreateRequest(
                PARENT_ID, "FINANCE", " 财务部 ", 9001L, null, "created"
        ));

        assertThat(actual).isSameAs(expected);
        verify(deptQueryService).requireParentDept(
                PARENT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        );
        ArgumentCaptor<DeptEntity> captor = ArgumentCaptor.forClass(DeptEntity.class);
        verify(deptMapper).insert(captor.capture());
        DeptEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(DEPT_ID);
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getParentId()).isEqualTo(PARENT_ID);
        assertThat(inserted.getDeptCode()).isEqualTo("FINANCE");
        assertThat(inserted.getDeptName()).isEqualTo(" 财务部 ");
        assertThat(inserted.getLeaderUserId()).isEqualTo(9001L);
        assertThat(inserted.getSortNo()).isZero();
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getRemark()).isEqualTo("created");
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void createNormalizesNullParentToRootAndStillRunsParentGuard() {
        when(deptMapper.insert(any(DeptEntity.class))).thenReturn(1);
        when(deptQueryService.toResponse(any(DeptEntity.class))).thenReturn(response("ACTIVE"));

        service().create(new DeptCreateRequest(
                null, "ROOT", "总部", null, 3, null
        ));

        verify(deptQueryService).requireParentDept(
                0L, AUDIT.companyId(), AUDIT.accountBookId()
        );
        ArgumentCaptor<DeptEntity> captor = ArgumentCaptor.forClass(DeptEntity.class);
        verify(deptMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentId()).isZero();
        assertThat(captor.getValue().getSortNo()).isEqualTo(3);
    }

    @Test
    void createStopsBeforeInsertWhenParentGuardFails() {
        when(deptQueryService.requireParentDept(
                PARENT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        )).thenThrow(new IllegalArgumentException("上级部门不存在"));

        assertThatThrownBy(() -> service().create(new DeptCreateRequest(
                PARENT_ID, "FINANCE", "财务部", null, null, null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上级部门不存在");

        verify(deptMapper, never()).insert(any(DeptEntity.class));
        verify(deptQueryService, never()).toResponse(any());
    }

    @Test
    void updateAuditsFieldsAndMapsPersistedDepartment() {
        DeptEntity entity = dept(DEPT_ID);
        when(deptQueryService.requireDept(DEPT_ID)).thenReturn(entity);
        when(deptMapper.updateById(entity)).thenReturn(1);
        DeptResponse expected = response("ACTIVE");
        when(deptQueryService.toResponse(entity)).thenReturn(expected);

        DeptResponse actual = service().update(DEPT_ID, new DeptUpdateRequest(
                "财务中心", 9002L, null, "updated"
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(entity.getDeptName()).isEqualTo("财务中心");
        assertThat(entity.getLeaderUserId()).isEqualTo(9002L);
        assertThat(entity.getSortNo()).isZero();
        assertThat(entity.getRemark()).isEqualTo("updated");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeResponseMapping() {
        DeptEntity entity = dept(DEPT_ID);
        when(deptQueryService.requireDept(DEPT_ID)).thenReturn(entity);
        when(deptMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(DEPT_ID, new DeptUpdateRequest(
                "冲突部门", null, 1, null
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("部门已被其他操作修改，请刷新后重试");

        verify(deptQueryService, never()).toResponse(entity);
    }

    @Test
    void enableAndDisableSetExpectedStatusAndAuditFields() {
        DeptEntity entity = dept(DEPT_ID);
        when(deptQueryService.requireDept(DEPT_ID)).thenReturn(entity);
        when(deptMapper.updateById(entity)).thenReturn(1);
        when(deptQueryService.toResponse(entity)).thenAnswer(invocation -> response(entity.getStatus()));

        assertThat(service().disable(DEPT_ID).status()).isEqualTo("DISABLED");
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());

        assertThat(service().enable(DEPT_ID).status()).isEqualTo("ACTIVE");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void statusConflictStopsBeforeResponseMapping() {
        DeptEntity entity = dept(DEPT_ID);
        when(deptQueryService.requireDept(DEPT_ID)).thenReturn(entity);
        when(deptMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().disable(DEPT_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("部门已被其他操作修改，请刷新后重试");

        verify(deptQueryService, never()).toResponse(entity);
    }

    @Test
    void commandLookupFailureDoesNotTouchMapper() {
        when(deptQueryService.requireDept(DEPT_ID))
                .thenThrow(new IllegalArgumentException("部门不存在"));

        assertThatThrownBy(() -> service().enable(DEPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");

        verifyNoInteractions(deptMapper);
    }

    private DeptCommandService service() {
        return new DeptCommandService(deptMapper, auditMetadataFactory, deptQueryService);
    }

    private DeptEntity dept(Long id) {
        DeptEntity entity = new DeptEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setParentId(0L);
        entity.setDeptCode("FINANCE");
        entity.setDeptName("财务部");
        entity.setLeaderUserId(9001L);
        entity.setSortNo(1);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("department");
        entity.setVersion(0);
        return entity;
    }

    private DeptResponse response(String status) {
        return new DeptResponse(
                DEPT_ID, PARENT_ID, "FINANCE", "财务部", 9001L, 1,
                status, "department", List.of()
        );
    }
}
