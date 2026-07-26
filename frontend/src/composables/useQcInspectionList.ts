import { reactive, ref } from 'vue'

import type { QcInspection, QcInspectionQuery } from '@/api/qc'
import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

export const useQcInspectionList = (
  t: Translate,
  options: {
    getInspections: (params: QcInspectionQuery) => Promise<PageResponse<QcInspection>>
    getInspection: (id: string | number) => Promise<QcInspection>
    submitInspection: (id: string | number) => Promise<unknown>
    cancelInspection: (id: string | number) => Promise<unknown>
    exportInspections: (params: QcInspectionQuery) => Promise<Blob>
    printInspection: (doc: QcInspection) => void
    downloadBlob: (blob: Blob, fileName: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const searchForm = reactive<QcInspectionQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    status: '',
    inspectionType: ''
  })
  const tableData = ref<QcInspection[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detailVisible = ref(false)
  const current = ref<QcInspection>()

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getInspections(searchForm)
      tableData.value = response.records || []
      total.value = response.total || 0
    } catch {
      options.onError?.(t('qcInspection.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleSearch = async () => {
    searchForm.pageNo = 1
    await loadData()
  }

  const handleReset = async () => {
    searchForm.keyword = ''
    searchForm.status = ''
    searchForm.inspectionType = ''
    searchForm.pageNo = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    searchForm.pageNo = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    searchForm.pageSize = size
    searchForm.pageNo = 1
    await loadData()
  }

  const handleView = async (row: QcInspection) => {
    try {
      current.value = await options.getInspection(row.id)
      detailVisible.value = true
    } catch {
      options.onError?.(t('qcInspection.message.detailLoadFailed'))
    }
  }

  const handlePrint = async (row: QcInspection) => {
    try {
      const detail = await options.getInspection(row.id)
      options.printInspection(detail)
    } catch {
      options.onError?.(t('qcInspection.message.printLoadFailed'))
    }
  }

  /**
   * Confirmation dismissal must stay silent, while a failed call relies on the
   * request interceptor for its own message.
   */
  const confirmThen = async (
    confirmKey: string,
    successKey: string,
    action: (id: string | number) => Promise<unknown>,
    row: QcInspection
  ) => {
    try {
      await options.confirm(
        t(confirmKey, { no: row.inspectionNo }),
        t('qcInspection.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await action(row.id)
      options.onSuccess?.(t(successKey))
      await loadData()
      return true
    } catch {
      return false
    }
  }

  const handleSubmit = (row: QcInspection) =>
    confirmThen(
      'qcInspection.message.submitConfirm',
      'qcInspection.message.submitted',
      options.submitInspection,
      row
    )

  const handleCancel = (row: QcInspection) =>
    confirmThen(
      'qcInspection.message.cancelConfirm',
      'qcInspection.message.cancelled',
      options.cancelInspection,
      row
    )

  const handleExport = async () => {
    try {
      const blob = await options.exportInspections(searchForm)
      options.downloadBlob(
        blob,
        t('qcInspection.message.exportFile', { date: formatBusinessDate() })
      )
      options.onSuccess?.(t('qcInspection.message.exported'))
      return true
    } catch {
      options.onError?.(t('qcInspection.message.exportFailed'))
      return false
    }
  }

  return {
    current,
    detailVisible,
    handleCancel,
    handleExport,
    handlePageChange,
    handlePrint,
    handleReset,
    handleSearch,
    handleSizeChange,
    handleSubmit,
    handleView,
    loadData,
    loading,
    searchForm,
    tableData,
    total
  }
}
