package com.tuowei.erp.qc.inspection.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 创建检验单。
 * <ul>
 *   <li>IQC：receiptId 必填</li>
 *   <li>OQC：deliveryId 必填</li>
 *   <li>IPQC：productionOrderId 必填（生产过程检）</li>
 * </ul>
 */
public record QcInspectionCreateRequest(
        String inspectionType,
        Long receiptId,
        Long deliveryId,
        Long productionOrderId,
        @NotNull(message = "inspectionDate不能为空") LocalDate inspectionDate,
        String remark
) {
}
