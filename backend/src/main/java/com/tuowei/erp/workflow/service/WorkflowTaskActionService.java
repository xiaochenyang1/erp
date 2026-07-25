package com.tuowei.erp.workflow.service;

import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class WorkflowTaskActionService {

    private final WorkflowService workflowService;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseRequisitionService purchaseRequisitionService;
    private final ExpenseService expenseService;

    public WorkflowTaskActionService(
            WorkflowService workflowService,
            SalesOrderService salesOrderService,
            PurchaseOrderService purchaseOrderService,
            PurchaseRequisitionService purchaseRequisitionService,
            ExpenseService expenseService
    ) {
        this.workflowService = workflowService;
        this.salesOrderService = salesOrderService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchaseRequisitionService = purchaseRequisitionService;
        this.expenseService = expenseService;
    }

    @Transactional
    public void approve(Long taskId, String comment) {
        WorkflowTaskResponse task = workflowService.taskDetail(taskId);
        switch (normalizeBusinessType(task.businessType())) {
            case "SALES_ORDER" ->
                    salesOrderService.approveWorkflowTask(task.id(), task.businessId(), new SalesOrderApproveRequest(comment));
            case "PURCHASE_ORDER" ->
                    purchaseOrderService.approveWorkflowTask(task.id(), task.businessId(), new PurchaseOrderApproveRequest(comment));
            case "PURCHASE_REQUISITION" ->
                    purchaseRequisitionService.approveWorkflowTask(task.id(), task.businessId(), comment);
            case "EXPENSE" -> expenseService.approveWorkflowTask(task.id(), task.businessId(), comment);
            default -> throw new IllegalArgumentException("审批中心暂不支持处理该业务类型");
        }
    }

    @Transactional
    public void reject(Long taskId, String comment) {
        WorkflowTaskResponse task = workflowService.taskDetail(taskId);
        switch (normalizeBusinessType(task.businessType())) {
            case "SALES_ORDER" ->
                    salesOrderService.rejectWorkflowTask(task.id(), task.businessId(), new SalesOrderRejectRequest(comment));
            case "PURCHASE_ORDER" ->
                    purchaseOrderService.rejectWorkflowTask(task.id(), task.businessId(), new PurchaseOrderRejectRequest(comment));
            case "PURCHASE_REQUISITION" ->
                    purchaseRequisitionService.rejectWorkflowTask(task.id(), task.businessId(), comment);
            case "EXPENSE" -> expenseService.rejectWorkflowTask(task.id(), task.businessId(), comment);
            default -> throw new IllegalArgumentException("审批中心暂不支持处理该业务类型");
        }
    }

    private String normalizeBusinessType(String businessType) {
        if (businessType == null || businessType.isBlank()) {
            throw new IllegalArgumentException("审批任务缺少业务类型");
        }
        return businessType.trim().toUpperCase(Locale.ROOT);
    }
}
