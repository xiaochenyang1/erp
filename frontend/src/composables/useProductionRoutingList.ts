import { reactive, ref } from 'vue'

import type {
  BOM,
  BOMQuery,
  Routing,
  RoutingQuery,
  WorkCenter,
  WorkCenterQuery
} from '@/api/production'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

export interface ProductionRoutingQueryForm {
  keyword: string
  status: string
}

/**
 * Query, options, detail, print and enable/disable for the production routing page.
 */
export const useProductionRoutingList = (
  t: Translate,
  options: {
    getRoutings: (params: RoutingQuery) => Promise<PageResponse<Routing>>
    getRouting: (id: string | number) => Promise<Routing>
    getWorkCenters: (params: WorkCenterQuery) => Promise<PageResponse<WorkCenter>>
    getBOMs: (params: BOMQuery) => Promise<PageResponse<BOM>>
    enableRouting: (id: string | number) => Promise<unknown>
    disableRouting: (id: string | number) => Promise<unknown>
    printRouting: (doc: Routing) => void
    decoratePrint?: (doc: Routing) => Routing
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive<ProductionRoutingQueryForm>({
    keyword: '',
    status: ''
  })

  const loading = ref(false)
  const tableData = ref<Routing[]>([])
  const workCenterOptions = ref<WorkCenter[]>([])
  const bomOptions = ref<BOM[]>([])

  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const viewDialogVisible = ref(false)
  const viewData = ref<Routing>({} as Routing)

  const loadOptions = async () => {
    try {
      const [wcRes, bomRes] = await Promise.all([
        options.getWorkCenters({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
        options.getBOMs({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      workCenterOptions.value = wcRes.records || []
      bomOptions.value = bomRes.records || []
    } catch {
      options.onError?.(t('productionRouting.message.optionsLoadFailed'))
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getRoutings({
        keyword: queryForm.keyword || undefined,
        status: queryForm.status || undefined,
        pageNo: pagination.page,
        pageSize: pagination.size
      })
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch {
      options.onError?.(t('productionRouting.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.page = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.size = size
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.keyword = ''
    queryForm.status = ''
    pagination.page = 1
    await loadData()
  }

  const handleView = async (row: Routing) => {
    try {
      viewData.value = await options.getRouting(row.id)
      viewDialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('productionRouting.message.detailLoadFailed'))
      return false
    }
  }

  const handlePrint = async (row: Routing) => {
    try {
      const detail = await options.getRouting(row.id)
      options.printRouting(options.decoratePrint ? options.decoratePrint(detail) : detail)
      return true
    } catch {
      options.onError?.(t('productionRouting.message.printLoadFailed'))
      return false
    }
  }

  const handleEnable = async (row: Routing) => {
    try {
      await options.confirm(
        t('productionRouting.message.enableConfirm', { name: row.routingName }),
        t('productionRouting.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.enableRouting(row.id)
      options.onSuccess?.(t('productionRouting.message.enabled'))
      await loadData()
      return true
    } catch {
      // Global interceptor already surfaces API errors.
      return false
    }
  }

  const handleDisable = async (row: Routing) => {
    try {
      await options.confirm(
        t('productionRouting.message.disableConfirm', { name: row.routingName }),
        t('productionRouting.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.disableRouting(row.id)
      options.onSuccess?.(t('productionRouting.message.disabled'))
      await loadData()
      return true
    } catch {
      // Global interceptor already surfaces API errors.
      return false
    }
  }

  return {
    bomOptions,
    handleDisable,
    handleEnable,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleView,
    loadData,
    loadOptions,
    loading,
    pagination,
    queryForm,
    tableData,
    viewData,
    viewDialogVisible,
    workCenterOptions
  }
}
