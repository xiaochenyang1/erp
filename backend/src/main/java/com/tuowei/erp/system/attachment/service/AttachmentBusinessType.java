package com.tuowei.erp.system.attachment.service;

import java.util.Locale;
import java.util.Set;

/**
 * 附件强制留痕闸门支持的业务类型。
 *
 * 每个常量都必须在对应单据的提交/过账写事务里调用
 * {@link AttachmentService#requireIfConfigured(String, Long)}，否则
 * {@code erp.attachment.required-business-types} 里配了该类型也不会真正拦截。
 * 这条约束由 {@code AttachmentGateCoverageTest} 扫描源码守住，
 * 配置里出现未挂闸门的类型则由 {@code AttachmentRequiredTypeValidator} 在启动时拒绝。
 *
 * 未收录 PAYMENT / RECEIPT：收付款单在 create 里直接落 POSTED，没有草稿态，
 * 单据拿到 ID 时已经过账，不存在可以先传附件再过账的时点。要挂闸门得先给它们
 * 补草稿生命周期，属于独立需求。
 */
public final class AttachmentBusinessType {

    public static final String EXPENSE = "EXPENSE";
    public static final String MANUAL_VOUCHER = "MANUAL_VOUCHER";
    public static final String FIN_INVOICE = "FIN_INVOICE";
    public static final String SALES_ORDER = "SALES_ORDER";
    public static final String SALES_DELIVERY = "SALES_DELIVERY";
    public static final String SALES_RETURN = "SALES_RETURN";
    public static final String PURCHASE_REQUISITION = "PURCHASE_REQUISITION";
    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    public static final String PURCHASE_RETURN = "PURCHASE_RETURN";
    public static final String INVENTORY_ADJUSTMENT = "INVENTORY_ADJUSTMENT";
    public static final String INVENTORY_TRANSFER = "INVENTORY_TRANSFER";
    public static final String INVENTORY_CHECK = "INVENTORY_CHECK";
    public static final String QC_INSPECTION = "QC_INSPECTION";
    public static final String PRODUCTION_ORDER = "PRODUCTION_ORDER";
    public static final String COMMERCIAL_CONTRACT = "COMMERCIAL_CONTRACT";

    /** 已挂闸门、允许出现在 required-business-types 里的业务类型。 */
    public static final Set<String> GATED = Set.of(
            EXPENSE,
            MANUAL_VOUCHER,
            FIN_INVOICE,
            SALES_ORDER,
            SALES_DELIVERY,
            SALES_RETURN,
            PURCHASE_REQUISITION,
            PURCHASE_ORDER,
            PURCHASE_RECEIPT,
            PURCHASE_RETURN,
            INVENTORY_ADJUSTMENT,
            INVENTORY_TRANSFER,
            INVENTORY_CHECK,
            QC_INSPECTION,
            PRODUCTION_ORDER,
            COMMERCIAL_CONTRACT
    );

    private AttachmentBusinessType() {
    }

    public static boolean isGated(String businessType) {
        return businessType != null && GATED.contains(businessType.trim().toUpperCase(Locale.ROOT));
    }
}
