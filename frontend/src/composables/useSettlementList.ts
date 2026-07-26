import { reactive, ref, type Ref } from 'vue'

import type { PageQuery, PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

interface SettlementDoc {
  id: string | number
  status?: string
}

/**
 * Query/print/cancel behaviour for one settlement tab. Receipts and payments
 * differ only in their party field and document number, so both tabs share this
 * composable with side-specific keys injected by the caller.
 */
export const useSettlementList = <TDoc extends SettlementDoc, TQuery extends PageQuery>(
  t: Translate,
  options: {
    /** Party filter key on the query: `customerId` for receipts, `supplierId` for payments. */
    partyKey: string
    /** Document number field, used in the cancel confirmation message. */
    documentNoKey: string
    listFailedKey: string
    cancelConfirmKey: string
    getList: (params: TQuery) => Promise<PageResponse<TDoc>>
    getDetail: (id: string | number) => Promise<TDoc>
    cancelDoc: (id: string | number) => Promise<unknown>
    printDoc: (doc: TDoc) => void
    /** Fills in the party name the print template needs when the API omits it. */
    decoratePrint?: (doc: TDoc) => TDoc
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const query = reactive({
    pageNo: 1,
    pageSize: 10,
    [options.partyKey]: undefined,
    status: ''
  }) as TQuery
  const tableData = ref([]) as Ref<TDoc[]>
  const total = ref(0)
  const loading = ref(false)

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getList(query)
      tableData.value = response.records || []
      total.value = response.total || 0
    } catch {
      options.onError?.(t(options.listFailedKey))
    } finally {
      loading.value = false
    }
  }

  const handleSearch = async () => {
    query.pageNo = 1
    await loadData()
  }

  const handlePrint = async (row: TDoc) => {
    try {
      const detail = await options.getDetail(row.id)
      options.printDoc(options.decoratePrint ? options.decoratePrint(detail) : detail)
    } catch {
      options.onError?.(t('financeReportPages.payments.message.printLoadFailed'))
    }
  }

  /** Dismissing the confirmation stays silent; a failed cancel reports the failure. */
  const handleCancel = async (row: TDoc) => {
    const documentNo = (row as Record<string, unknown>)[options.documentNoKey]
    try {
      await options.confirm(
        t(options.cancelConfirmKey, { no: documentNo }),
        t('financeReportPages.common.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.cancelDoc(row.id)
      options.onSuccess?.(t('financeReportPages.payments.message.cancelled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('financeReportPages.payments.message.cancelFailed'))
      return false
    }
  }

  return {
    handleCancel,
    handlePrint,
    handleSearch,
    loadData,
    loading,
    query,
    tableData,
    total
  }
}
