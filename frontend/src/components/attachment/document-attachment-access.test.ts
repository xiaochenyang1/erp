import { describe, expect, it } from 'vitest'

import {
  ATTACHMENT_DELETE_PERMISSION,
  ATTACHMENT_UPLOAD_PERMISSION,
  ATTACHMENT_VIEW_PERMISSION,
  canDeleteDocumentAttachments,
  canUploadDocumentAttachments,
  canViewDocumentAttachments
} from './document-attachment-access'

const checker = (...permissions: string[]) => (permission: string) => permissions.includes(permission)

describe('document attachment access', () => {
  it('matches the permission codes the backend controller enforces', () => {
    expect(ATTACHMENT_VIEW_PERMISSION).toBe('system:attachment:view')
    expect(ATTACHMENT_UPLOAD_PERMISSION).toBe('system:attachment:manage')
    expect(ATTACHMENT_DELETE_PERMISSION).toBe('system:attachment:delete')
  })

  it('opens the panel for a view-only account without write affordances', () => {
    const viewOnly = checker(ATTACHMENT_VIEW_PERMISSION)

    expect(canViewDocumentAttachments(viewOnly)).toBe(true)
    expect(canUploadDocumentAttachments(viewOnly)).toBe(false)
    expect(canDeleteDocumentAttachments(viewOnly)).toBe(false)
  })

  /** The list call runs on open, so a write-only grant would only show errors. */
  it('requires the view permission alongside upload and delete', () => {
    expect(canUploadDocumentAttachments(checker(ATTACHMENT_UPLOAD_PERMISSION))).toBe(false)
    expect(canDeleteDocumentAttachments(checker(ATTACHMENT_DELETE_PERMISSION))).toBe(false)
    expect(canUploadDocumentAttachments(checker(ATTACHMENT_VIEW_PERMISSION, ATTACHMENT_UPLOAD_PERMISSION))).toBe(true)
    expect(canDeleteDocumentAttachments(checker(ATTACHMENT_VIEW_PERMISSION, ATTACHMENT_DELETE_PERMISSION))).toBe(true)
  })

  it('hides everything from an account with no attachment permission', () => {
    const none = checker('sales:order:view')

    expect(canViewDocumentAttachments(none)).toBe(false)
    expect(canUploadDocumentAttachments(none)).toBe(false)
    expect(canDeleteDocumentAttachments(none)).toBe(false)
  })

  it('separates upload from delete so a clerk cannot remove evidence', () => {
    const uploader = checker(ATTACHMENT_VIEW_PERMISSION, ATTACHMENT_UPLOAD_PERMISSION)

    expect(canUploadDocumentAttachments(uploader)).toBe(true)
    expect(canDeleteDocumentAttachments(uploader)).toBe(false)
  })
})
