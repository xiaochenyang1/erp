import { computed, reactive, ref } from 'vue'

import type {
  QcInspection,
  QcInspectionJudgeRequest,
  QcInspectionUpdateRequest
} from '@/api/qc'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface QcInspectionEditLine {
  lineId: string | number
  lineNo: number
  productId: string | number
  inspectedQty: number
  defectReason: string
  remark: string
}

export interface QcInspectionJudgeLine {
  lineId: string | number
  productId: string | number
  lineNo: number
  inspectedQty: number
  qualifiedQty: number
  unqualifiedQty: number
  defectReason: string
}

export const useQcInspectionEdit = (
  t: Translate,
  options: {
    getInspection: (id: string | number) => Promise<QcInspection>
    updateInspection: (id: string | number, data: QcInspectionUpdateRequest) => Promise<unknown>
    judgeInspection: (id: string | number, data: QcInspectionJudgeRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const submitting = ref(false)

  const editVisible = ref(false)
  const editingId = ref<string | number | null>(null)
  const editingInspectionNo = ref('')
  const editLines = ref<QcInspectionEditLine[]>([])
  const editForm = reactive<{
    inspectionType: string
    receiptId: string | number | ''
    deliveryId: string | number | ''
    productionOrderId: string
    inspectionDate: string
    remark: string
  }>({
    inspectionType: 'IQC',
    receiptId: '',
    deliveryId: '',
    productionOrderId: '',
    inspectionDate: '',
    remark: ''
  })

  const judgeVisible = ref(false)
  const judgingId = ref<string | number | null>(null)
  const judgingInspectionNo = ref('')
  const judgeLines = ref<QcInspectionJudgeLine[]>([])

  const editDialogTitle = computed(() => {
    if (editForm.inspectionType === 'OQC') return t('qcInspection.dialog.editOqc')
    if (editForm.inspectionType === 'IPQC') return t('qcInspection.dialog.editIpqc')
    return t('qcInspection.dialog.editIqc')
  })

  const editSourceDocumentId = computed(() => {
    if (editForm.inspectionType === 'OQC') return editForm.deliveryId
    if (editForm.inspectionType === 'IPQC') return editForm.productionOrderId
    return editForm.receiptId
  })

  const resetEditForm = () => {
    editingId.value = null
    editingInspectionNo.value = ''
    editForm.inspectionType = 'IQC'
    editForm.receiptId = ''
    editForm.deliveryId = ''
    editForm.productionOrderId = ''
    editForm.inspectionDate = ''
    editForm.remark = ''
    editLines.value = []
  }

  /** Re-checks status on the fetched detail: the list row may be stale. */
  const handleEdit = async (row: QcInspection) => {
    if (row.status !== 'DRAFT') {
      options.onWarning?.(t('qcInspection.validation.draftOnly'))
      return false
    }
    try {
      const detail = await options.getInspection(row.id)
      if (detail.status !== 'DRAFT') {
        options.onWarning?.(t('qcInspection.validation.draftOnly'))
        return false
      }
      editingId.value = detail.id
      editingInspectionNo.value = detail.inspectionNo
      editForm.inspectionType = detail.inspectionType || 'IQC'
      editForm.receiptId = detail.receiptId ?? ''
      editForm.deliveryId = detail.deliveryId ?? ''
      editForm.productionOrderId = String(detail.productionOrderId ?? detail.orderId ?? '')
      editForm.inspectionDate = detail.inspectionDate
      editForm.remark = detail.remark || ''
      editLines.value = (detail.lines || []).map((line) => ({
        lineId: line.id,
        lineNo: line.lineNo,
        productId: line.productId,
        inspectedQty: Number(line.inspectedQty ?? 0),
        defectReason: line.defectReason || '',
        remark: line.remark || ''
      }))
      editVisible.value = true
      return true
    } catch {
      options.onError?.(t('qcInspection.message.detailLoadFailed'))
      return false
    }
  }

  const validateEdit = () => {
    if (editingId.value == null) {
      options.onWarning?.(t('qcInspection.validation.editableMissing'))
      return false
    }
    if (!editForm.inspectionDate) {
      options.onWarning?.(t('qcInspection.validation.date'))
      return false
    }
    for (const line of editLines.value) {
      if (line.inspectedQty == null || Number(line.inspectedQty) < 0) {
        options.onWarning?.(t('qcInspection.validation.negativeQuantity', { line: line.lineNo }))
        return false
      }
    }
    return true
  }

  const confirmEdit = async () => {
    if (!validateEdit() || editingId.value == null) return false

    submitting.value = true
    try {
      await options.updateInspection(editingId.value, {
        inspectionDate: editForm.inspectionDate,
        remark: editForm.remark?.trim() || undefined,
        lines: editLines.value.map((line) => ({
          lineId: line.lineId,
          inspectedQty: Number(line.inspectedQty),
          defectReason: line.defectReason?.trim() || undefined,
          remark: line.remark?.trim() || undefined
        }))
      })
      options.onSuccess?.(t('qcInspection.message.saved'))
      editVisible.value = false
      resetEditForm()
      await options.onSubmitted?.()
      return true
    } catch {
      // 拦截器已提示
      return false
    } finally {
      submitting.value = false
    }
  }

  /** Judging defaults every line to fully qualified; the user splits out rejects. */
  const handleJudge = async (row: QcInspection) => {
    try {
      const detail = await options.getInspection(row.id)
      judgingId.value = detail.id
      judgingInspectionNo.value = detail.inspectionNo
      judgeLines.value = (detail.lines || []).map((line) => ({
        lineId: line.id,
        productId: line.productId,
        lineNo: line.lineNo,
        inspectedQty: Number(line.inspectedQty ?? 0),
        qualifiedQty: Number(line.inspectedQty ?? 0),
        unqualifiedQty: 0,
        defectReason: line.defectReason || ''
      }))
      judgeVisible.value = true
      return detail
    } catch {
      options.onError?.(t('qcInspection.message.detailLoadFailed'))
      return undefined
    }
  }

  const validateJudge = () => {
    for (const line of judgeLines.value) {
      if (Number(line.qualifiedQty) + Number(line.unqualifiedQty) !== Number(line.inspectedQty)) {
        options.onWarning?.(t('qcInspection.validation.judgeQuantity', { line: line.lineNo }))
        return false
      }
    }
    return judgingId.value != null
  }

  const confirmJudge = async () => {
    if (!validateJudge() || judgingId.value == null) return false

    submitting.value = true
    try {
      await options.judgeInspection(judgingId.value, {
        lines: judgeLines.value.map((line) => ({
          lineId: line.lineId,
          qualifiedQty: Number(line.qualifiedQty),
          unqualifiedQty: Number(line.unqualifiedQty),
          defectReason: line.defectReason || undefined
        }))
      })
      options.onSuccess?.(t('qcInspection.message.judged'))
      judgeVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      // 拦截器已提示
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    confirmEdit,
    confirmJudge,
    editDialogTitle,
    editForm,
    editLines,
    editSourceDocumentId,
    editVisible,
    editingId,
    editingInspectionNo,
    handleEdit,
    handleJudge,
    judgeLines,
    judgeVisible,
    judgingId,
    judgingInspectionNo,
    resetEditForm,
    submitting,
    validateEdit,
    validateJudge
  }
}
