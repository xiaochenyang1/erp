import { describe, expect, it, vi } from 'vitest'

import type { Payable, Receivable } from '@/api/finance'
import { allocateAcrossRows, useSettlementForm } from './useSettlementForm'

const t = (key: string) => key

const receivables = [
  { id: 'r1', receivableNo: 'AR001', remainingAmount: 100 },
  { id: 'r2', receivableNo: 'AR002', remainingAmount: 250 },
  { id: 'r3', receivableNo: 'AR003', remainingAmount: 0 }
] as unknown as Receivable[]

const payables = [
  { id: 'p1', payableNo: 'AP001', remainingAmount: 80 },
  { id: 'p2', payableNo: 'AP002', remainingAmount: 120 }
] as unknown as Payable[]

const createReceiptForm = (
  overrides: Partial<Parameters<typeof useSettlementForm>[1]> = {}
) =>
  useSettlementForm<Receivable>(t, {
    partyKey: 'customerId',
    dateKey: 'receiptDate',
    selectionKey: 'receivableIds',
    documentNoKey: 'receivableNo',
    allocationIdKey: 'receivableId',
    amountKey: 'receiptAmount',
    methodKey: 'receiptMethod',
    method: 'BANK_TRANSFER',
    allocationExceededKey: 'financeReportPages.payments.validation.receiptAllocationExceeded',
    createdKey: 'financeReportPages.payments.message.receiptCreated',
    createFailedKey: 'financeReportPages.payments.message.receiptCreateFailed',
    getOpenItems: vi.fn(async () => ({ records: receivables, total: 3 } as any)),
    createDoc: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  } as any)

const createPaymentForm = (
  overrides: Partial<Parameters<typeof useSettlementForm>[1]> = {}
) =>
  useSettlementForm<Payable>(t, {
    partyKey: 'supplierId',
    dateKey: 'paymentDate',
    selectionKey: 'payableIds',
    documentNoKey: 'payableNo',
    allocationIdKey: 'payableId',
    amountKey: 'paymentAmount',
    methodKey: 'paymentMethod',
    method: 'BANK_TRANSFER',
    allocationExceededKey: 'financeReportPages.payments.validation.paymentAllocationExceeded',
    createdKey: 'financeReportPages.payments.message.paymentCreated',
    createFailedKey: 'financeReportPages.payments.message.paymentCreateFailed',
    getOpenItems: vi.fn(async () => ({ records: payables, total: 2 } as any)),
    createDoc: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  } as any)

describe('settlement allocation maths', () => {
  it('fills earlier rows in full before moving on, and stops when exhausted', () => {
    const rows = [
      { id: 'a', remainingAmount: 100, allocatedAmount: 0 },
      { id: 'b', remainingAmount: 250, allocatedAmount: 0 },
      { id: 'c', remainingAmount: 40, allocatedAmount: 0 }
    ]

    allocateAcrossRows(rows, 180)

    expect(rows.map((row) => row.allocatedAmount)).toEqual([100, 80, 0])
  })

  it('never allocates more than a row still owes, even with surplus cash', () => {
    const rows = [
      { id: 'a', remainingAmount: 30, allocatedAmount: 0 },
      { id: 'b', remainingAmount: 20, allocatedAmount: 0 }
    ]

    allocateAcrossRows(rows, 500)

    expect(rows.map((row) => row.allocatedAmount)).toEqual([30, 20])
  })

  it('keeps cent precision instead of accumulating float drift', () => {
    const rows = [
      { id: 'a', remainingAmount: 33.33, allocatedAmount: 0 },
      { id: 'b', remainingAmount: 33.33, allocatedAmount: 0 },
      { id: 'c', remainingAmount: 33.34, allocatedAmount: 0 }
    ]

    allocateAcrossRows(rows, 100)

    expect(rows.map((row) => row.allocatedAmount)).toEqual([33.33, 33.33, 33.34])
    const sum = rows.reduce((total, row) => total + row.allocatedAmount, 0)
    expect(Number(sum.toFixed(2))).toBe(100)
  })

  it('zeroes every row when there is nothing to allocate', () => {
    const rows = [
      { id: 'a', remainingAmount: 10, allocatedAmount: 5 },
      { id: 'b', remainingAmount: 10, allocatedAmount: 5 }
    ]

    allocateAcrossRows(rows, 0)

    expect(rows.map((row) => row.allocatedAmount)).toEqual([0, 0])
  })
})

describe('settlement form', () => {
  it('opens with a business-date default and a cleared form', () => {
    const form = createReceiptForm()
    form.form.remark = 'stale'

    form.handleCreate()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.form.documentDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(form.form.remark).toBe('')
    expect(form.allocationRows.value).toEqual([])
  })

  it('loads only open items for the chosen party and drops fully settled ones', async () => {
    const getOpenItems = vi.fn(async () => ({ records: receivables, total: 3 } as any))
    const form = createReceiptForm({ getOpenItems })

    form.form.partyId = 'c9'
    await form.loadOpenItems()

    expect(getOpenItems).toHaveBeenCalledWith({ pageNo: 1, pageSize: 1000, customerId: 'c9' })
    expect(form.openItems.value.map((item: any) => item.id)).toEqual(['r1', 'r2'])
  })

  it('skips the lookup and clears items when no party is selected', async () => {
    const getOpenItems = vi.fn(async () => ({ records: receivables, total: 3 } as any))
    const form = createReceiptForm({ getOpenItems })

    form.form.partyId = ''
    await form.loadOpenItems()

    expect(getOpenItems).not.toHaveBeenCalled()
    expect(form.openItems.value).toEqual([])
  })

  it('reports open-item load failures and leaves the picker empty', async () => {
    const onError = vi.fn()
    const form = createReceiptForm({
      getOpenItems: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.form.partyId = 'c1'
    await form.loadOpenItems()

    expect(form.openItems.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('financeReportPages.payments.message.openItemsLoadFailed')
  })

  it('defaults the amount to the selected balance and auto-allocates it', async () => {
    const form = createReceiptForm()
    form.form.partyId = 'c1'
    await form.loadOpenItems()

    form.form.selectedIds = ['r1', 'r2']
    form.onSelectionChange()

    expect(form.form.amount).toBe(350)
    expect(form.allocationRows.value.map((row) => row.allocatedAmount)).toEqual([100, 250])
    expect(form.allocationRows.value[0].documentNo).toBe('AR001')
    expect(form.allocatedTotal.value).toBe(350)
    expect(form.unallocated.value).toBe(0)
  })

  it('rebalances a manually lowered amount and reports the shortfall', async () => {
    const form = createReceiptForm()
    form.form.partyId = 'c1'
    await form.loadOpenItems()
    form.form.selectedIds = ['r1', 'r2']
    form.onSelectionChange()

    form.form.amount = 150
    form.rebalance()

    expect(form.allocationRows.value.map((row) => row.allocatedAmount)).toEqual([100, 50])
    expect(form.allocatedTotal.value).toBe(150)
    expect(form.unallocated.value).toBe(0)
  })

  it('leaves an unallocated remainder visible when cash exceeds the open balance', async () => {
    const form = createReceiptForm()
    form.form.partyId = 'c1'
    await form.loadOpenItems()
    form.form.selectedIds = ['r1']
    form.onSelectionChange()

    form.form.amount = 400
    form.rebalance()

    expect(form.allocationRows.value[0].allocatedAmount).toBe(100)
    expect(form.unallocated.value).toBe(300)
  })

  it('refuses to submit with no positive allocation', async () => {
    const createDoc = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const form = createReceiptForm({ createDoc, onWarning })

    form.handleCreate()
    expect(await form.submit()).toBe(false)
    expect(createDoc).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('financeReportPages.payments.validation.allocationRequired')
  })

  it('refuses to submit when allocations exceed the document amount', async () => {
    const createDoc = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const form = createReceiptForm({ createDoc, onWarning })

    form.handleCreate()
    form.form.amount = 50
    form.allocationRows.value = [
      { id: 'r1', remainingAmount: 100, allocatedAmount: 80 }
    ]

    expect(await form.submit()).toBe(false)
    expect(createDoc).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith(
      'financeReportPages.payments.validation.receiptAllocationExceeded'
    )
  })

  it('sends receipt-shaped payload keys and reloads on success', async () => {
    const createDoc = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createReceiptForm({ createDoc, onSubmitted, onSuccess })

    form.handleCreate()
    form.form.partyId = 'c1'
    form.form.amount = 100
    form.allocationRows.value = [
      { id: 'r1', documentNo: 'AR001', remainingAmount: 100, allocatedAmount: 100 }
    ]

    expect(await form.submit()).toBe(true)
    expect(createDoc).toHaveBeenCalledWith(expect.objectContaining({
      customerId: 'c1',
      receiptAmount: 100,
      receiptMethod: 'BANK_TRANSFER',
      allocations: [{ receivableId: 'r1', allocatedAmount: 100 }]
    }))
    expect(form.dialogVisible.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.payments.message.receiptCreated')
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('sends payment-shaped payload keys from the same composable', async () => {
    const createDoc = vi.fn(async () => ({}))
    const form = createPaymentForm({ createDoc })

    form.handleCreate()
    form.form.partyId = 's1'
    form.form.amount = 80
    form.allocationRows.value = [
      { id: 'p1', documentNo: 'AP001', remainingAmount: 80, allocatedAmount: 80 }
    ]

    expect(await form.submit()).toBe(true)
    expect(createDoc).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 's1',
      paymentAmount: 80,
      paymentMethod: 'BANK_TRANSFER',
      allocations: [{ payableId: 'p1', allocatedAmount: 80 }]
    }))
  })

  it('keeps the dialog open and clears loading when creation fails', async () => {
    const onError = vi.fn()
    const form = createReceiptForm({
      createDoc: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleCreate()
    form.form.amount = 100
    form.allocationRows.value = [
      { id: 'r1', remainingAmount: 100, allocatedAmount: 100 }
    ]

    expect(await form.submit()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.submitting.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.payments.message.receiptCreateFailed')
  })

  it('clears selection and allocations when the party changes', async () => {
    const form = createReceiptForm()
    form.form.partyId = 'c1'
    await form.loadOpenItems()
    form.form.selectedIds = ['r1', 'r2']
    form.onSelectionChange()
    expect(form.allocationRows.value).toHaveLength(2)

    form.form.partyId = 'c2'
    await form.loadOpenItems()

    expect(form.form.selectedIds).toEqual([])
    expect(form.allocationRows.value).toEqual([])
    expect(form.form.amount).toBe(0)
  })
})
