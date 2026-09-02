package com.tuowei.erp.system.post.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostResponse;
import com.tuowei.erp.system.post.web.PostUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 17, 30)
    );
    private static final Long POST_ID = 7001L;
    private static final Long DEPT_ID = 6001L;

    @Mock private PostMapper postMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private PostQueryService postQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createValidatesDepartmentAndBuildsTenantAuditedActivePost() {
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept("ACTIVE", AUDIT.companyId(), AUDIT.accountBookId()));
        when(postMapper.insert(any(PostEntity.class))).thenAnswer(invocation -> {
            PostEntity entity = invocation.getArgument(0);
            entity.setId(POST_ID);
            return 1;
        });
        PostResponse expected = response("ACTIVE");
        when(postQueryService.toResponse(any(PostEntity.class))).thenReturn(expected);

        PostResponse actual = service().create(new PostCreateRequest(
                DEPT_ID, "BUYER", " 采购员 ", "created"
        ));

        assertThat(actual).isSameAs(expected);
        verify(deptMapper).selectById(DEPT_ID);
        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).insert(captor.capture());
        PostEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(POST_ID);
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getDeptId()).isEqualTo(DEPT_ID);
        assertThat(inserted.getPostCode()).isEqualTo("BUYER");
        assertThat(inserted.getPostName()).isEqualTo(" 采购员 ");
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
    void createAllowsDisabledButUndeletedDepartmentInCurrentTenant() {
        when(deptMapper.selectById(DEPT_ID))
                .thenReturn(dept("DISABLED", AUDIT.companyId(), AUDIT.accountBookId()));
        when(postMapper.insert(any(PostEntity.class))).thenReturn(1);
        when(postQueryService.toResponse(any(PostEntity.class))).thenReturn(response("ACTIVE"));

        service().create(new PostCreateRequest(DEPT_ID, "BUYER", "采购员", null));

        verify(postMapper).insert(any(PostEntity.class));
    }

    @Test
    void createRejectsMissingDeletedOrCrossTenantDepartmentBeforeInsert() {
        when(deptMapper.selectById(DEPT_ID)).thenReturn(null);
        assertDepartmentMissing();

        DeptEntity deleted = dept("ACTIVE", AUDIT.companyId(), AUDIT.accountBookId());
        deleted.setDeletedFlag(1);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(deleted);
        assertDepartmentMissing();

        when(deptMapper.selectById(DEPT_ID))
                .thenReturn(dept("ACTIVE", 999L, AUDIT.accountBookId()));
        assertDepartmentMissing();

        when(deptMapper.selectById(DEPT_ID))
                .thenReturn(dept("ACTIVE", AUDIT.companyId(), 999L));
        assertDepartmentMissing();

        verify(postMapper, never()).insert(any(PostEntity.class));
        verify(postQueryService, never()).toResponse(any());
    }

    @Test
    void updateAuditsPostAndMapsPersistedEntity() {
        PostEntity entity = post();
        when(postQueryService.requirePost(POST_ID)).thenReturn(entity);
        when(postMapper.updateById(entity)).thenReturn(1);
        PostResponse expected = response("ACTIVE");
        when(postQueryService.toResponse(entity)).thenReturn(expected);

        PostResponse actual = service().update(POST_ID, new PostUpdateRequest(
                "高级采购员", "updated"
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(entity.getPostName()).isEqualTo("高级采购员");
        assertThat(entity.getRemark()).isEqualTo("updated");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeResponseMapping() {
        PostEntity entity = post();
        when(postQueryService.requirePost(POST_ID)).thenReturn(entity);
        when(postMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(
                POST_ID, new PostUpdateRequest("冲突岗位", null)
        ))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("岗位已被其他操作修改，请刷新后重试");

        verify(postQueryService, never()).toResponse(entity);
    }

    @Test
    void enableAndDisableSetExpectedStatusAndAuditFields() {
        PostEntity entity = post();
        when(postQueryService.requirePost(POST_ID)).thenReturn(entity);
        when(postMapper.updateById(entity)).thenReturn(1);
        when(postQueryService.toResponse(entity)).thenAnswer(invocation -> response(entity.getStatus()));

        assertThat(service().disable(POST_ID).status()).isEqualTo("DISABLED");
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());

        assertThat(service().enable(POST_ID).status()).isEqualTo("ACTIVE");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        verify(postMapper, times(2)).updateById(entity);
    }

    @Test
    void statusConflictStopsBeforeResponseMapping() {
        PostEntity entity = post();
        when(postQueryService.requirePost(POST_ID)).thenReturn(entity);
        when(postMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().disable(POST_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("岗位已被其他操作修改，请刷新后重试");

        verify(postQueryService, never()).toResponse(entity);
    }

    @Test
    void commandLookupFailureDoesNotTouchMappers() {
        when(postQueryService.requirePost(POST_ID))
                .thenThrow(new IllegalArgumentException("岗位不存在"));

        assertThatThrownBy(() -> service().enable(POST_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在");

        verifyNoInteractions(postMapper, deptMapper);
    }

    private void assertDepartmentMissing() {
        assertThatThrownBy(() -> service().create(
                new PostCreateRequest(DEPT_ID, "BUYER", "采购员", null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
    }

    private PostCommandService service() {
        return new PostCommandService(postMapper, deptMapper, auditMetadataFactory, postQueryService);
    }

    private PostEntity post() {
        PostEntity entity = new PostEntity();
        entity.setId(POST_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setDeptId(DEPT_ID);
        entity.setPostCode("BUYER");
        entity.setPostName("采购员");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("post");
        entity.setVersion(0);
        return entity;
    }

    private DeptEntity dept(String status, Long companyId, Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(DEPT_ID);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        return entity;
    }

    private PostResponse response(String status) {
        return new PostResponse(
                POST_ID, DEPT_ID, "BUYER", "采购员", status, "post"
        );
    }
}
