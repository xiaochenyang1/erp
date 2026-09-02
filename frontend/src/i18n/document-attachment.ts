export const documentAttachmentMessages = {
  'zh-CN': {
    documentAttachment: {
      title: '单据附件',
      requiredTag: '必传',
      requiredHint: '该单据在提交/过账前必须至少上传一个附件，否则后端会拒绝操作',
      count: '共 {count} 个',
      upload: '上传附件',
      empty: '暂无附件',
      viewDenied: '当前账号没有附件查看权限（system:attachment:view），请联系管理员开通',
      filename: '文件名',
      fileSize: '大小',
      uploadedAt: '上传时间',
      actions: '操作',
      download: '下载',
      delete: '删除',
      message: {
        loadFailed: '加载单据附件失败',
        uploaded: '附件已上传',
        uploadFailed: '上传附件失败',
        downloadFailed: '下载附件失败',
        deleted: '附件已删除',
        deleteFailed: '删除附件失败',
        tooLarge: '单个附件不能超过 {limit} MB',
        confirmDelete: '确认删除附件“{filename}”吗？',
        prompt: '确认'
      }
    }
  },
  'en-US': {
    documentAttachment: {
      title: 'Document attachments',
      requiredTag: 'Required',
      requiredHint: 'This document needs at least one attachment before it can be submitted or posted; the server rejects the action otherwise',
      count: '{count} file(s)',
      upload: 'Upload attachment',
      empty: 'No attachments yet',
      viewDenied: 'This account cannot view attachments (system:attachment:view); ask an administrator to grant it',
      filename: 'File name',
      fileSize: 'Size',
      uploadedAt: 'Uploaded at',
      actions: 'Actions',
      download: 'Download',
      delete: 'Delete',
      message: {
        loadFailed: 'Failed to load document attachments',
        uploaded: 'Attachment uploaded',
        uploadFailed: 'Failed to upload the attachment',
        downloadFailed: 'Failed to download the attachment',
        deleted: 'Attachment deleted',
        deleteFailed: 'Failed to delete the attachment',
        tooLarge: 'A single attachment cannot exceed {limit} MB',
        confirmDelete: 'Delete attachment “{filename}”?',
        prompt: 'Confirmation'
      }
    }
  }
} as const
