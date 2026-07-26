import { computed, reactive, ref, type Ref } from 'vue'

import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/** One selectable open item (receivable or payable) with its running allocation. */
export interface SettlementAllocationRow {
  id: string
  documentNo?: string
  remainingAmount: number
  allocatedAmount: number
}

/**
 * Field names are side-neutral so one template binding works for both tabs;
 * they are mapped to the backend's receipt/payment names only on submit.
 */
export interface SettlementFormState {
  partyId: string | number
  selectedIds: string[]
  documentDate: string
  amount: number
  remark: string
}

interface OpenItem {
  id: string | number
  remainingAmount?: number
}

const round2 = (value: number) => Number(value.toFixed(2))

/**
 * Greedy oldest-first allocation: each selected item absorbs as much of the
 * remaining amount as it still owes, so a partial payment settles the earliest
 * documents in full rather than spreading thin across all of them.
 */
export const allocateAcrossRows = (rows: SettlementAllocationRow[], totalAmount: number) => {
  let left = Number(totalAmount || 0)
  for (const row of rows) {
    const take = Math.min(Math.max(left, 0), Number(row.remainingAmount || 0))
    row.allocatedAmount = round2(take)
    left = round2(left - take)
  }
  return rows
}

export const useSettlementForm = <TOpenItem extends OpenItem>(
  t: Translate,
  options: {
    /** Party field the backend expects: `customerId` for receipts, `supplierId` for payments. */
    partyKey: 'customerId' | 'supplierId'
    dateKey: 'receiptDate' | 'paymentDate'
    documentNoKey: 'receivableNo' | 'payableNo'
    allocationIdKey: 'receivableId' | 'payableId'
    amountKey: 'receiptAmount' | 'paymentAmount'
    method: string
    methodKey: 'receiptMethod' | 'paymentMethod'
    allocationExceededKey: string
    createdKey: string
    createFailedKey: string
    getOpenItems: (params: Record<string, unknown>) => Promise<PageResponse<TOpenItem>>
    createDoc: (data: Record<string, unknown>) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitting = ref(false)
  const openItems = ref<TOpenItem[]>([]) as Ref<TOpenItem[]>
  const allocationRows = ref<SettlementAllocationRow[]>([])

  const form = reactive<SettlementFormState>({
    partyId: '',
    selectedIds: [],
    documentDate: '',
    amount: 0,
    remark: ''
  })

  const formAmount = () => Number(form.amount || 0)

  const allocatedTotal = computed(() =>
    allocationRows.value.reduce((sum, row) => sum + Number(row.allocatedAmount || 0), 0)
  )
  const unallocated = computed(() => round2(formAmount() - allocatedTotal.value))

  const resetForm = () => {
    form.partyId = ''
    form.selectedIds = []
    form.documentDate = ''
    form.amount = 0
    form.remark = ''
    allocationRows.value = []
    openItems.value = []
  }

  const handleCreate = () => {
    resetForm()
    form.documentDate = formatBusinessDate()
    dialogVisible.value = true
  }

  /** Only items that still owe something are selectable. */
  const loadOpenItems = async () => {
    form.selectedIds = []
    allocationRows.value = []
    form.amount = 0
    const partyId = form.partyId
    if (!partyId) {
      openItems.value = []
      return
    }
    try {
      const page = await options.getOpenItems({
        pageNo: 1,
        pageSize: 1000,
        [options.partyKey]: partyId
      })
      openItems.value = (page.records || []).filter(
        (item) => Number(item.remainingAmount) > 0
      )
    } catch {
      openItems.value = []
      options.onError?.(t('financeReportPages.payments.message.openItemsLoadFailed'))
    }
  }

  /** Defaults the amount to the full selected balance when the user left it blank. */
  const autoAllocate = () => {
    if (!formAmount() || formAmount() <= 0) {
      const sum = allocationRows.value.reduce(
        (total, row) => total + Number(row.remainingAmount || 0),
        0
      )
      form.amount = round2(sum)
    }
    allocateAcrossRows(allocationRows.value, formAmount())
  }

  const onSelectionChange = () => {
    const selected = new Set(form.selectedIds.map(String))
    allocationRows.value = openItems.value
      .filter((item) => selected.has(String(item.id)))
      .map((item) => ({
        id: String(item.id),
        documentNo: (item as Record<string, unknown>)[options.documentNoKey] as string | undefined,
        remainingAmount: Number(item.remainingAmount || 0),
        allocatedAmount: 0
      }))
    autoAllocate()
  }

  const rebalance = () => allocateAcrossRows(allocationRows.value, formAmount())

  const buildAllocations = () =>
    allocationRows.value
      .filter((row) => Number(row.allocatedAmount) > 0)
      .map((row) => ({
        [options.allocationIdKey]: row.id,
        allocatedAmount: Number(row.allocatedAmount)
      }))

  /** Allocations may under-run the amount (leaving it on account) but never exceed it. */
  const validateAllocations = (allocations: Array<Record<string, unknown>>) => {
    if (!allocations.length) {
      options.onWarning?.(t('financeReportPages.payments.validation.allocationRequired'))
      return false
    }
    const allocated = allocations.reduce(
      (sum, item) => sum + Number(item.allocatedAmount || 0),
      0
    )
    if (allocated - formAmount() > 0.0001) {
      options.onWarning?.(t(options.allocationExceededKey))
      return false
    }
    return true
  }

  const submit = async () => {
    const allocations = buildAllocations()
    if (!validateAllocations(allocations)) return false

    submitting.value = true
    try {
      await options.createDoc({
        [options.partyKey]: form.partyId,
        [options.dateKey]: form.documentDate,
        [options.amountKey]: formAmount(),
        [options.methodKey]: options.method,
        allocations,
        remark: form.remark
      })
      options.onSuccess?.(t(options.createdKey))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t(options.createFailedKey))
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    allocatedTotal,
    allocationRows,
    autoAllocate,
    buildAllocations,
    dialogVisible,
    form,
    handleCreate,
    loadOpenItems,
    onSelectionChange,
    openItems,
    rebalance,
    resetForm,
    submit,
    submitting,
    unallocated,
    validateAllocations
  }
}
