import { reactive, ref, type Ref } from 'vue'

import type { FinanceAccountStatus } from '@/api/finance'
import type { PageQuery, PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

interface FinanceAccountDocument {
  id: string | number
  status: FinanceAccountStatus
}

/**
 * List, export and detail workflow for one finance-account side. Receivables
 * and payables use isolated instances configured with their own API methods.
 */
export const useFinanceAccountList = <
  TDocument extends FinanceAccountDocument,
  TQuery extends PageQuery
>(
  t: Translate,
  options: {
    canView: () => boolean
    initialQuery: TQuery
    getList: (params: TQuery) => Promise<PageResponse<TDocument>>
    getDetail: (id: string | number) => Promise<TDocument>
    exportList: (params: TQuery) => Promise<Blob>
    listFailedKey: string
    detailFailedKey: string
    fileNameKey: string
    downloadBlob: (blob: Blob, filename: string) => void
    now?: () => number
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const query = reactive(options.initialQuery) as TQuery
  const loading = ref(false)
  const tableData = ref([]) as Ref<TDocument[]>
  const total = ref(0)
  const detailVisible = ref(false)
  const selectedDocument = ref() as Ref<TDocument | undefined>
  const detailLoading = ref(false)

  const loadData = async () => {
    if (!options.canView()) return false
    loading.value = true
    try {
      const response = await options.getList(query)
      tableData.value = response.records || []
      total.value = response.total || 0
      return true
    } catch {
      options.onError?.(t(options.listFailedKey))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleExport = async () => {
    if (!options.canView()) return false
    try {
      const blob = await options.exportList(query)
      options.downloadBlob(blob, t(options.fileNameKey, {
        timestamp: options.now?.() ?? Date.now()
      }))
      options.onSuccess?.(t('financeAccount.message.exported'))
      return true
    } catch {
      options.onError?.(t('financeAccount.message.exportFailed'))
      return false
    }
  }

  const handleView = async (row: TDocument) => {
    if (!options.canView()) return false
    detailVisible.value = true
    selectedDocument.value = undefined
    detailLoading.value = true
    try {
      selectedDocument.value = await options.getDetail(row.id)
      return true
    } catch {
      options.onError?.(t(options.detailFailedKey))
      detailVisible.value = false
      return false
    } finally {
      detailLoading.value = false
    }
  }

  return {
    detailLoading,
    detailVisible,
    handleExport,
    handleView,
    loadData,
    loading,
    query,
    selectedDocument,
    tableData,
    total
  }
}
