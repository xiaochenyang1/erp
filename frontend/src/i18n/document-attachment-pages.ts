/**
 * Copy for the in-page document attachment entry. The `type` map must stay in
 * step with the backend `AttachmentBusinessType.GATED` set: every gated document
 * type needs a label here because the panel shows which document it is editing.
 */
export const documentAttachmentMessages = {
  'zh-CN': {
    documentAttachment: {
      entry: '附件', title: '单据附件', documentType: '单据类型', documentNo: '单据号',
      upload: '上传附件', refresh: '刷新', close: '关闭', actions: '操作', download: '下载', delete: '删除',
      filename: '文件名', fileSize: '大小', uploadedAt: '上传时间', uploadedBy: '上传人',
      empty: '暂无附件', hint: '部分单据在提交或过账前必须有附件，缺少附件会被后端闸门拒绝。',
      readOnlyHint: '当前账号只能查看和下载附件。',
      truncatedHint: '该单据附件较多，此处仅显示 {count} 个，其余请到系统附件页查看。',
      type: {
        COMMERCIAL_CONTRACT: '商务合同', EXPENSE: '费用单', FIN_INVOICE: '发票',
        INVENTORY_ADJUSTMENT: '库存调整单', INVENTORY_CHECK: '盘点单', INVENTORY_TRANSFER: '调拨单',
        MANUAL_VOUCHER: '手工凭证', PRODUCTION_ORDER: '生产工单',
        PURCHASE_ORDER: '采购订单', PURCHASE_RECEIPT: '采购收货单', PURCHASE_REQUISITION: '采购请购单', PURCHASE_RETURN: '采购退货单',
        QC_INSPECTION: '质检单', SALES_DELIVERY: '销售发货单', SALES_ORDER: '销售订单', SALES_RETURN: '销售退货单'
      },
      message: {
        loadFailed: '加载附件失败', uploaded: '附件已上传', uploadFailed: '上传附件失败',
        downloadFailed: '下载附件失败', deleted: '附件已删除', deleteFailed: '删除附件失败',
        confirmDelete: '确认删除附件“{filename}”吗？', prompt: '确认', confirm: '确定', cancel: '取消'
      }
    }
  },
  'en-US': {
    documentAttachment: {
      entry: 'Attachments', title: 'Document attachments', documentType: 'Document type', documentNo: 'Document no.',
      upload: 'Upload', refresh: 'Refresh', close: 'Close', actions: 'Actions', download: 'Download', delete: 'Delete',
      filename: 'File name', fileSize: 'Size', uploadedAt: 'Uploaded at', uploadedBy: 'Uploaded by',
      empty: 'No attachments yet', hint: 'Some documents require an attachment before submit or posting; the backend gate rejects them otherwise.',
      readOnlyHint: 'This account can only view and download attachments.',
      truncatedHint: 'This document has more attachments than the panel shows; only {count} are listed, use the system attachment console for the rest.',
      type: {
        COMMERCIAL_CONTRACT: 'Commercial contract', EXPENSE: 'Expense', FIN_INVOICE: 'Invoice',
        INVENTORY_ADJUSTMENT: 'Inventory adjustment', INVENTORY_CHECK: 'Inventory check', INVENTORY_TRANSFER: 'Inventory transfer',
        MANUAL_VOUCHER: 'Manual voucher', PRODUCTION_ORDER: 'Production order',
        PURCHASE_ORDER: 'Purchase order', PURCHASE_RECEIPT: 'Purchase receipt', PURCHASE_REQUISITION: 'Purchase requisition', PURCHASE_RETURN: 'Purchase return',
        QC_INSPECTION: 'QC inspection', SALES_DELIVERY: 'Sales delivery', SALES_ORDER: 'Sales order', SALES_RETURN: 'Sales return'
      },
      message: {
        loadFailed: 'Failed to load attachments', uploaded: 'Attachment uploaded', uploadFailed: 'Failed to upload the attachment',
        downloadFailed: 'Failed to download the attachment', deleted: 'Attachment deleted', deleteFailed: 'Failed to delete the attachment',
        confirmDelete: 'Delete attachment “{filename}”?', prompt: 'Confirmation', confirm: 'Confirm', cancel: 'Cancel'
      }
    }
  }
} as const
