import { ref } from 'vue'
import type { InventoryAdjustment, InventoryAdjustmentApprovalInfo } from '@/api/inventory'

type Action = 'submit' | 'approve' | 'reject' | 'withdraw'
type Translate = (key: string) => string

export const useInventoryAdjustmentWorkflow = (t: Translate, options: {
  submit: (id: string | number) => Promise<unknown>
  approve: (id: string | number) => Promise<unknown>
  reject: (id: string | number, comment: string) => Promise<unknown>
  withdraw: (id: string | number) => Promise<unknown>
  getApprovalInfo: (id: string | number) => Promise<InventoryAdjustmentApprovalInfo>
  confirm: (message: string) => Promise<unknown>
  prompt: (message: string) => Promise<{ value: string }>
  reload: () => Promise<unknown>
  onError: (message: string) => void
  onSuccess: (message: string) => void
}) => {
  const busy = ref(false)
  const historyVisible = ref(false)
  const historyLoading = ref(false)
  const historyNo = ref('')
  const approvalInfo = ref<InventoryAdjustmentApprovalInfo | null>(null)
  let historyRequest = 0

  const runApprovalAction = async (row: InventoryAdjustment, action: Action) => {
    if (busy.value) return
    if (action === 'submit' ? !['DRAFT', 'REJECTED'].includes(row.status) : row.status !== 'SUBMITTED') return
    busy.value = true
    try {
      if (action === 'reject') {
        const { value } = await options.prompt(t('inventoryAdjustments.approval.rejectReason'))
        if (!value.trim() || value.trim().length > 255) {
          options.onError(t('inventoryAdjustments.approval.rejectReason'))
          return
        }
        await options.reject(row.id, value.trim())
      } else {
        await options.confirm(t(`inventoryAdjustments.approval.${action}Confirm`))
        await options[action](row.id)
      }
      options.onSuccess(t('inventoryAdjustments.message.success'))
      await options.reload()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') options.onError(t('inventoryAdjustments.message.failed'))
    } finally {
      busy.value = false
    }
  }

  const openApprovalHistory = async (row: InventoryAdjustment) => {
    const request = ++historyRequest
    historyNo.value = row.adjustmentNo
    approvalInfo.value = null
    historyVisible.value = true
    historyLoading.value = true
    try {
      const result = await options.getApprovalInfo(row.id)
      if (request === historyRequest) approvalInfo.value = result
    } catch {
      if (request === historyRequest) options.onError(t('inventoryAdjustments.approval.loadFailed'))
    } finally {
      if (request === historyRequest) historyLoading.value = false
    }
  }

  return { busy, runApprovalAction, historyVisible, historyLoading, historyNo, approvalInfo, openApprovalHistory }
}
