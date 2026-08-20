package com.tuowei.erp.workflow.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for workflow commands, queries, and task transitions. */
@Service
public class WorkflowService {

    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;
    private final WorkflowTaskTransitionService taskTransitionService;

    public WorkflowService(
            WorkflowQueryService queryService,
            WorkflowCommandService commandService,
            WorkflowTaskTransitionService taskTransitionService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.taskTransitionService = taskTransitionService;
    }

    @Transactional
    public void submit(String businessType, Long businessId, String businessNo, String title, String comment) {
        commandService.submit(businessType, businessId, businessNo, title, comment);
    }

    @Transactional
    public void approve(String businessType, Long businessId, String comment) {
        commandService.approve(businessType, businessId, comment);
    }

    @Transactional
    public void approveTaskForBusiness(Long taskId, String businessType, Long businessId, String comment) {
        commandService.approveTaskForBusiness(taskId, businessType, businessId, comment);
    }

    @Transactional
    public void reject(String businessType, Long businessId, String comment) {
        commandService.reject(businessType, businessId, comment);
    }

    @Transactional
    public void rejectTaskForBusiness(Long taskId, String businessType, Long businessId, String comment) {
        commandService.rejectTaskForBusiness(taskId, businessType, businessId, comment);
    }

    @Transactional
    public void cancel(String businessType, Long businessId, String comment) {
        commandService.cancel(businessType, businessId, comment);
    }

    @Transactional
    public void withdraw(String businessType, Long businessId, String comment) {
        commandService.withdraw(businessType, businessId, comment);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowTaskResponse> listTasks(WorkflowTaskPageQuery query) {
        return queryService.listTasks(query);
    }

    @Transactional(readOnly = true)
    public WorkflowTaskResponse taskDetail(Long id) {
        return queryService.taskDetail(id);
    }

    @Transactional
    public WorkflowTaskResponse transfer(Long taskId, Long targetUserId, String comment) {
        return taskTransitionService.transfer(taskId, targetUserId, comment);
    }

    @Transactional
    public WorkflowTaskResponse escalate(Long taskId, Long targetUserId, String comment) {
        return taskTransitionService.escalate(taskId, targetUserId, comment);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowRecordResponse> listRecords(WorkflowRecordPageQuery query) {
        return queryService.listRecords(query);
    }

    @Transactional(readOnly = true)
    public WorkflowApprovalInfoResponse approvalInfo(String businessType, Long businessId) {
        return queryService.approvalInfo(businessType, businessId);
    }
}
