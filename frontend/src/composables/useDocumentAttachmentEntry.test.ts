import { describe, expect, it } from 'vitest'

import { useDocumentAttachmentEntry } from './useDocumentAttachmentEntry'

const checker = (...permissions: string[]) => (permission: string) => permissions.includes(permission)

const fullAccess = checker('system:attachment:view', 'system:attachment:manage', 'system:attachment:delete')

describe('document attachment entry', () => {
  it('starts closed and opens on the picked document', () => {
    const entry = useDocumentAttachmentEntry(fullAccess)

    expect(entry.attachmentVisible.value).toBe(false)
    expect(entry.openAttachments('9007199254740993', 'SO-001')).toBe(true)
    expect(entry.attachmentVisible.value).toBe(true)
    expect(entry.attachmentBusinessId.value).toBe('9007199254740993')
    expect(entry.attachmentBusinessNo.value).toBe('SO-001')
  })

  it('stringifies numeric ids and tolerates a missing document number', () => {
    const entry = useDocumentAttachmentEntry(fullAccess)

    entry.openAttachments(4096)

    expect(entry.attachmentBusinessId.value).toBe('4096')
    expect(entry.attachmentBusinessNo.value).toBe('')
  })

  it('stays closed for a row without an id instead of loading another document', () => {
    const entry = useDocumentAttachmentEntry(fullAccess)

    expect(entry.openAttachments(null)).toBe(false)
    expect(entry.openAttachments(undefined)).toBe(false)
    expect(entry.openAttachments('')).toBe(false)
    expect(entry.attachmentVisible.value).toBe(false)
    expect(entry.attachmentBusinessId.value).toBe('')
  })

  it('exposes the three attachment permissions independently', () => {
    const viewOnly = useDocumentAttachmentEntry(checker('system:attachment:view'))

    expect(viewOnly.canViewAttachments.value).toBe(true)
    expect(viewOnly.canUploadAttachments.value).toBe(false)
    expect(viewOnly.canDeleteAttachments.value).toBe(false)

    const full = useDocumentAttachmentEntry(fullAccess)
    expect(full.canViewAttachments.value).toBe(true)
    expect(full.canUploadAttachments.value).toBe(true)
    expect(full.canDeleteAttachments.value).toBe(true)
  })

  it('hides the entry from an account without the attachment view permission', () => {
    const entry = useDocumentAttachmentEntry(checker('sales:order:view'))

    expect(entry.canViewAttachments.value).toBe(false)
  })
})
