import { reactive, ref } from 'vue'

import type { PurchaseReceipt } from '@/api/purchase'
import type { QcInspectionCreateRequest, QcInspectionType } from '@/api/qc'
import type { SalesDelivery } from '@/api/sales'
import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type SourcePageQuery = { pageNo: number; pageSize: number; status: string }

export interface QcInspectionCreateForm {
  inspectionType: QcInspectionType
  receiptId: string | number | ''
  deliveryId: string | number | ''
  productionOrderId: string
  inspectionDate: string
  remark: string
}

export const useQcInspectionCreate = (
  t: Translate,
  options: {
    createInspection: (data: QcInspectionCreateRequest) => Promise<unknown>
    getReceipts: (params: SourcePageQuery) => Promise<PageResponse<PurchaseReceipt>>
    getDeliveries: (params: SourcePageQuery) => Promise<PageResponse<SalesDelivery>>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const createVisible = ref(false)
  const submitting = ref(false)
  const sourceLoading = ref(false)
  const draftReceipts = ref<PurchaseReceipt[]>([])
  const draftDeliveries = ref<SalesDelivery[]>([])

  const createForm = reactive<QcInspectionCreateForm>({
    inspectionType: 'IQC',
    receiptId: '',
    deliveryId: '',
    productionOrderId: '',
    inspectionDate: '',
    remark: ''
  })

  /** IPQC references a production order by id, so it needs no document lookup. */
  const loadCreateSources = async () => {
    if (createForm.inspectionType === 'IPQC') {
      draftReceipts.value = []
      draftDeliveries.value = []
      return
    }
    sourceLoading.value = true
    try {
      if (createForm.inspectionType === 'OQC') {
        const response = await options.getDeliveries({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
        draftDeliveries.value = response.records || []
      } else {
        const response = await options.getReceipts({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
        draftReceipts.value = response.records || []
      }
    } catch {
      options.onError?.(t('qcInspection.message.sourcesLoadFailed'))
    } finally {
      sourceLoading.value = false
    }
  }

  const clearSourceSelection = () => {
    createForm.receiptId = ''
    createForm.deliveryId = ''
    createForm.productionOrderId = ''
  }

  const handleCreate = async () => {
    createForm.inspectionType = 'IQC'
    clearSourceSelection()
    createForm.inspectionDate = formatBusinessDate()
    createForm.remark = ''
    createVisible.value = true
    await loadCreateSources()
  }

  const onCreateTypeChange = async () => {
    clearSourceSelection()
    await loadCreateSources()
  }

  const validateCreate = () => {
    if (createForm.inspectionType === 'OQC') {
      if (!createForm.deliveryId) {
        options.onWarning?.(t('qcInspection.validation.delivery'))
        return false
      }
    } else if (createForm.inspectionType === 'IPQC') {
      if (!createForm.productionOrderId.trim()) {
        options.onWarning?.(t('qcInspection.validation.productionOrder'))
        return false
      }
    } else if (!createForm.receiptId) {
      options.onWarning?.(t('qcInspection.validation.receipt'))
      return false
    }
    if (!createForm.inspectionDate) {
      options.onWarning?.(t('qcInspection.validation.date'))
      return false
    }
    return true
  }

  /** Only the source field matching the chosen type is sent to the backend. */
  const confirmCreate = async () => {
    if (!validateCreate()) return false

    submitting.value = true
    try {
      await options.createInspection({
        inspectionType: createForm.inspectionType,
        receiptId: createForm.inspectionType === 'IQC' ? createForm.receiptId || undefined : undefined,
        deliveryId: createForm.inspectionType === 'OQC' ? createForm.deliveryId || undefined : undefined,
        productionOrderId:
          createForm.inspectionType === 'IPQC' ? createForm.productionOrderId : undefined,
        inspectionDate: createForm.inspectionDate,
        remark: createForm.remark || undefined
      })
      options.onSuccess?.(t('qcInspection.message.created'))
      createVisible.value = false
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
    confirmCreate,
    createForm,
    createVisible,
    draftDeliveries,
    draftReceipts,
    handleCreate,
    loadCreateSources,
    onCreateTypeChange,
    sourceLoading,
    submitting,
    validateCreate
  }
}
