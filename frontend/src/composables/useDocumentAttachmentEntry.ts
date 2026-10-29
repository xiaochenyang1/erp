import { computed, ref } from 'vue'

import {
  canDeleteDocumentAttachments,
  canUploadDocumentAttachments,
  canViewDocumentAttachments
} from '@/components/attachment/document-attachment-access'

type PermissionChecker = (permission: string) => boolean

/**
 * Page-side state for the document attachment entry: which document the dialog
 * is pointed at, and whether this account may see, add or remove attachments.
 *
 * The permission checker is injected instead of read from the store so the host
 * page keeps one reactive source and this stays testable without Pinia. Return
 * values are destructured by callers, which keeps template auto-unwrapping.
 */
export const useDocumentAttachmentEntry = (hasPermission: PermissionChecker) => {
  const attachmentVisible = ref(false)
  const attachmentBusinessId = ref('')
  const attachmentBusinessNo = ref('')

  const canViewAttachments = computed(() => canViewDocumentAttachments(hasPermission))
  const canUploadAttachments = computed(() => canUploadDocumentAttachments(hasPermission))
  const canDeleteAttachments = computed(() => canDeleteDocumentAttachments(hasPermission))

  /** Opens the dialog for one document; a missing id is ignored on purpose. */
  const openAttachments = (businessId: string | number | null | undefined, businessNo?: string | null) => {
    const id = businessId != null ? String(businessId) : ''
    if (!id) return false
    attachmentBusinessId.value = id
    attachmentBusinessNo.value = businessNo || ''
    attachmentVisible.value = true
    return true
  }

  return {
    attachmentBusinessId,
    attachmentBusinessNo,
    attachmentVisible,
    canDeleteAttachments,
    canUploadAttachments,
    canViewAttachments,
    openAttachments
  }
}
