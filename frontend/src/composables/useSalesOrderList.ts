import { reactive, ref } from 'vue'

import type {
  SalesOrder,
  SalesOrderQuery
} from '@/api/sales'
import type { Customer, Product, Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title?: string, options?: { type?: string }) => Promise<unknown>
type Prompt = (
  message: string,
  title?: string,
  options?: {
    inputPlaceholder?: string
    confirmButtonText?: string
    cancelButtonText?: string
  }
) => Promise<{ value: string }>
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const useSalesOrderList = (
  t: Translate,
  options: {
    getOrders: (params: SalesOrderQuery) => Promise<PageResponse<SalesOrder>>
    getOrder: (id: string | number) => Promise<SalesOrder>
    submitOrder: (id: string | number) => Promise<unknown>
    approveOrder: (id: string | number) => Promise<unknown>
    unapproveOrder: (id: string | number) => Promise<unknown>
    rejectOrder: (id: string | number, reason: string) => Promise<unknown>
    cancelOrder: (id: string | number) => Promise<unknown>
    getCustomers: (params: PageQuery) => Promise<PageResponse<Customer>>
    getWarehouses: (params: PageQuery) => Promise<PageResponse<Warehouse>>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    printOrder: (order: SalesOrder) => void
    confirm: Confirm
    prompt: Prompt
    initialKeyword?: string
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<SalesOrderQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: options.initialKeyword || '',
    customerId: undefined,
    status: '',
    approvalStatus: ''
  })
  const loading = ref(false)
  const tableData = ref<SalesOrder[]>([])
  const total = ref(0)
  const customers = ref<Customer[]>([])
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getOrders(queryParams)
      tableData.value = page.records || []
      total.value = page.total || 0
    } catch (error) {
      console.error(t('salesOrder.message.loadFailed'), error)
      options.onError?.(t('salesOrder.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    const [customerPage, warehousePage, productPage] = await Promise.all([
      options.getCustomers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
      options.getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
      options.getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    ])
    customers.value = customerPage.records || []
    warehouses.value = warehousePage.records || []
    products.value = productPage.records || []
  }

  const handleQuery = () => {
    queryParams.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    queryParams.keyword = ''
    queryParams.customerId = undefined
    queryParams.status = ''
    queryParams.approvalStatus = ''
    queryParams.pageNo = 1
    void loadData()
  }

  const handlePrint = async (row: SalesOrder) => {
    try {
      const order = await options.getOrder(row.id)
      options.printOrder(order)
    } catch {
      options.onError?.(t('salesOrder.message.printLoadFailed'))
    }
  }

  const runOrderAction = async (
    row: SalesOrder,
    message: string,
    action: () => Promise<unknown>,
    successMessage: string
  ) => {
    try {
      await options.confirm(`${message}\n${row.orderNo}`, t('salesOrder.message.prompt'), { type: 'warning' })
      await action()
      options.onSuccess?.(successMessage)
      await loadData()
    } catch (error) {
      if (error !== 'cancel' && !(error instanceof Error)) {
        options.onError?.(t('salesOrder.message.actionFailed'))
      }
    }
  }

  const handleSubmitOrder = async (row: SalesOrder) => {
    await runOrderAction(
      row,
      t('salesOrder.message.submitConfirm'),
      () => options.submitOrder(row.id),
      t('salesOrder.message.submitted')
    )
  }

  const handleApprove = async (row: SalesOrder) => {
    await runOrderAction(
      row,
      t('salesOrder.message.approveConfirm'),
      () => options.approveOrder(row.id),
      t('salesOrder.message.approved')
    )
  }

  const handleUnapprove = async (row: SalesOrder) => {
    await runOrderAction(
      row,
      t('salesOrder.message.unapproveConfirm'),
      () => options.unapproveOrder(row.id),
      t('salesOrder.message.unapproved')
    )
  }

  const handleCancel = async (row: SalesOrder) => {
    await runOrderAction(
      row,
      t('salesOrder.message.cancelConfirm'),
      () => options.cancelOrder(row.id),
      t('salesOrder.message.cancelled')
    )
  }

  const handleReject = async (row: SalesOrder) => {
    try {
      const { value } = await options.prompt(
        t('salesOrder.message.rejectReason'),
        t('salesOrder.message.rejectTitle'),
        {
          inputPlaceholder: t('salesOrder.message.rejectReason'),
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel')
        }
      )
      await options.rejectOrder(row.id, value)
      options.onSuccess?.(t('salesOrder.message.rejected'))
      await loadData()
    } catch (error) {
      if (error !== 'cancel' && !(error instanceof Error)) {
        options.onError?.(t('salesOrder.message.rejectFailed'))
      }
    }
  }

  return {
    customers,
    handleApprove,
    handleCancel,
    handlePrint,
    handleQuery,
    handleReject,
    handleReset,
    handleSubmitOrder,
    handleUnapprove,
    loadData,
    loadOptions,
    loading,
    products,
    queryParams,
    tableData,
    total,
    warehouses
  }
}
