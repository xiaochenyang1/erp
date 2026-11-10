import { ref } from 'vue'

import type { Attachment } from '@/api/attachment'
import type { ContractRecord, ContractVersionRecord } from '@/api/contracts'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

/**
 * Contract detail dialog: the refetched contract, its attachments and its
 * version history, plus the single-version snapshot viewer.
 *
 * Attachments live here rather than in the shared document panel because the
 * backend scopes contract attachments under `/contracts/{id}/attachments`.
 */
export const useContractDetail = (
  t: Translate,
  options: {
    getContract: (id: string | number) => Promise<ContractRecord>
    getContractAttachments: (contractId: string) => Promise<PageResponse<Attachment>>
    getContractVersions: (contractId: string) => Promise<ContractVersionRecord[]>
    getContractVersion: (contractId: string, versionId: string) => Promise<ContractVersionRecord>
    restoreContractVersion: (contractId: string, versionId: string) => Promise<unknown>
    uploadContractAttachment: (contractId: string, file: File) => Promise<unknown>
    downloadContractAttachment: (contractId: string, attachmentId: string) => Promise<Blob>
    deleteContractAttachment: (contractId: string, attachmentId: string) => Promise<unknown>
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onRestored?: () => void | Promise<void>
  }
) => {
  const detailVisible = ref(false)
  const selected = ref<ContractRecord | null>(null)
  const attachments = ref<Attachment[]>([])
  const versions = ref<ContractVersionRecord[]>([])
  const versionVisible = ref(false)
  const versionSnapshot = ref<ContractVersionRecord | null>(null)
  const uploading = ref(false)
  const loadAttachments = async (contractId: string) => {
    attachments.value = (await options.getContractAttachments(contractId)).records || []
  }

  /** The detail refetches the contract so lines and progress are never stale. */
  const openDetail = async (row: ContractRecord) => {
    try {
      selected.value = await options.getContract(row.id)
      const [attachmentPage, versionRows] = await Promise.all([
        options.getContractAttachments(row.id),
        options.getContractVersions(row.id)
      ])
      attachments.value = attachmentPage.records || []
      versions.value = versionRows || []
      detailVisible.value = true
      return true
    } catch {
      options.onError?.(t('contractPage.message.detailFailed'))
      return false
    }
  }

  const resetDetail = () => {
    selected.value = null
    attachments.value = []
    versions.value = []
    versionSnapshot.value = null
  }

  const resetVersion = () => {
    versionSnapshot.value = null
  }

  const openVersion = async (row: ContractVersionRecord) => {
    if (!selected.value) return false
    try {
      versionSnapshot.value = await options.getContractVersion(selected.value.id, row.id)
      versionVisible.value = true
      return true
    } catch {
      options.onError?.(t('contractPage.message.versionFailed'))
      return false
    }
  }
  /** Restoring never overwrites the contract in place; it opens a new draft. */
  const restoreVersion = async (row: ContractVersionRecord) => {
    const contract = selected.value
    if (!contract) return false
    try {
      await options.confirm(
        t('contractPage.message.confirmRestore'),
        t('contractPage.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.restoreContractVersion(contract.id, row.id)
      options.onSuccess?.(t('contractPage.message.restored'))
      detailVisible.value = false
      await options.onRestored?.()
      return true
    } catch {
      options.onError?.(t('contractPage.message.restoreFailed'))
      return false
    }
  }

  const uploadAttachment = async (file: File | null | undefined) => {
    const contract = selected.value
    if (!file || !contract) return false
    uploading.value = true
    try {
      await options.uploadContractAttachment(contract.id, file)
      await loadAttachments(contract.id)
      options.onSuccess?.(t('contractPage.message.attachmentUploaded'))
      return true
    } catch {
      options.onError?.(t('contractPage.message.attachmentFailed'))
      return false
    } finally {
      uploading.value = false
    }
  }

  const downloadAttachment = async (row: Attachment) => {
    const contract = selected.value
    if (!contract) return false
    try {
      const blob = await options.downloadContractAttachment(contract.id, row.id)
      options.downloadBlob(blob, row.originalFilename || `attachment-${row.id}`)
      return true
    } catch {
      options.onError?.(t('contractPage.message.attachmentFailed'))
      return false
    }
  }
  /** Deleting contract evidence is irreversible, so it always asks first. */
  const deleteAttachment = async (row: Attachment) => {
    const contract = selected.value
    if (!contract) return false
    try {
      await options.confirm(
        t('contractPage.message.confirmDeleteAttachment', { filename: row.originalFilename }),
        t('contractPage.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.deleteContractAttachment(contract.id, row.id)
      attachments.value = attachments.value.filter((item) => item.id !== row.id)
      options.onSuccess?.(t('contractPage.message.attachmentDeleted'))
      return true
    } catch {
      options.onError?.(t('contractPage.message.attachmentFailed'))
      return false
    }
  }

  return {
    attachments,
    deleteAttachment,
    detailVisible,
    downloadAttachment,
    openDetail,
    openVersion,
    resetDetail,
    resetVersion,
    restoreVersion,
    selected,
    uploadAttachment,
    uploading,
    versionSnapshot,
    versionVisible,
    versions
  }
}
