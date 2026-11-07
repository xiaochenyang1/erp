type PermissionChecker = (permission: string) => boolean

/**
 * The generic attachment endpoints are guarded by system-level permissions, not
 * by the host document's permissions: a user who may edit a sales order still
 * needs `system:attachment:manage` to attach a file to it.
 */
export const ATTACHMENT_VIEW_PERMISSION = 'system:attachment:view'
export const ATTACHMENT_UPLOAD_PERMISSION = 'system:attachment:manage'
export const ATTACHMENT_DELETE_PERMISSION = 'system:attachment:delete'

export const canViewDocumentAttachments = (hasPermission: PermissionChecker) =>
  hasPermission(ATTACHMENT_VIEW_PERMISSION)

export const canUploadDocumentAttachments = (hasPermission: PermissionChecker) =>
  hasPermission(ATTACHMENT_VIEW_PERMISSION) && hasPermission(ATTACHMENT_UPLOAD_PERMISSION)

export const canDeleteDocumentAttachments = (hasPermission: PermissionChecker) =>
  hasPermission(ATTACHMENT_VIEW_PERMISSION) && hasPermission(ATTACHMENT_DELETE_PERMISSION)
