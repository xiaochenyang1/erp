package com.tuowei.erp.system.attachment.web;

import java.util.List;

/**
 * 附件闸门策略，供前端页内附件面板渲染「必传」提示与体积上限。
 *
 * 全部来自 {@code erp.attachment} 配置，不含任何业务数据，因此只要求登录、
 * 不挂附件权限码：没有 system:attachment:view 的用户也需要知道单据为什么被拦。
 *
 * @param maxFileSizeBytes      单个附件体积上限
 * @param minRequiredCount      被拦业务类型至少需要的有效附件数
 * @param requiredBusinessTypes 当前配置为强制留痕的业务类型
 * @param gatedBusinessTypes    已挂闸门、允许配进 required-business-types 的业务类型
 */
public record AttachmentPolicyResponse(
        long maxFileSizeBytes,
        int minRequiredCount,
        List<String> requiredBusinessTypes,
        List<String> gatedBusinessTypes
) {
}
