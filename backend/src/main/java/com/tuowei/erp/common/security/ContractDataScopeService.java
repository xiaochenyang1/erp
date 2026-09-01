package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

import static com.tuowei.erp.common.security.DataScopePolicySupport.assertSameTenant;
import static com.tuowei.erp.common.security.DataScopePolicySupport.visibleCreatorIds;

@Service
public class ContractDataScopeService {

    public LambdaQueryWrapper<ContractEntity> applyContractScope(
            LambdaQueryWrapper<ContractEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        if (snapshot.hasAllScope()) {
            return wrapper;
        }

        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.in(ContractEntity::getCreatedBy, visibleCreatorIds);
    }

    public void assertCanViewContract(
            ContractEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该合同");
        if (snapshot.hasAllScope()) {
            return;
        }
        if (snapshot.selfScoped() && Objects.equals(entity.getCreatedBy(), currentUser.userId())) {
            return;
        }
        if (snapshot.deptScoped() && Objects.equals(creatorDeptId, currentUser.deptId())) {
            return;
        }
        if (snapshot.postScoped() && Objects.equals(creatorPostId, currentUser.postId())) {
            return;
        }
        throw new AccessDeniedException("无权访问该合同");
    }
}
