import { describe, expect, it, vi } from 'vitest'

import type { Receipt, ReceiptQuery } from '@/api/finance'
import { useSettlementList } from './useSettlementList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.no != null ? `${key}:${params.no}` : key

const createList = (
  overrides: Partial<Parameters<typeof useSettlementList>[1]> = {}
) =>
  useSettlementList<Receipt, ReceiptQuery>(t, {
    partyKey: 'customerId',
    documentNoKey: 'receiptNo',
    listFailedKey: 'financeReportPages.payments.message.receiptsLoadFailed',
    cancelConfirmKey: 'financeReportPages.payments.message.receiptCancelConfirm',
    getList: vi.fn(async () => ({
      records: [{ id: 'r1', receiptNo: 'RC001', status: 'DRAFT' }],
      total: 1
    } as any)),
    getDetail: vi.fn(async () => ({ id: 'r1', receiptNo: 'RC001' } as Receipt)),
    cancelDoc: vi.fn(async () => ({})),
    printDoc: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  } as any)

describe('settlement list', () => {
  it('seeds the query with the side-specific party key', () => {
    const list = createList()

    expect(list.query.pageNo).toBe(1)
    expect(list.query.pageSize).toBe(10)
    expect(list.query.status).toBe('')
    expect('customerId' in list.query).toBe(true)
    expect(list.query.customerId).toBeUndefined()
  })

  it('loads rows and clears loading on failure', async () => {
    const list = createList()
    await list.loadData()
    expect(list.tableData.value).toHaveLength(1)
    expect(list.total.value).toBe(1)
    expect(list.loading.value).toBe(false)

    const onError = vi.fn()
    const failing = createList({
      getList: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadData()
    expect(onError).toHaveBeenCalledWith('financeReportPages.payments.message.receiptsLoadFailed')
    expect(failing.loading.value).toBe(false)
  })

  it('resets to the first page on search but keeps it when paginating', async () => {
    const getList = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getList })

    list.query.pageNo = 5
    await list.handleSearch()
    expect(getList).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 1 }))

    list.query.pageNo = 3
    await list.loadData()
    expect(getList).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))
  })

  it('prints the fetched detail and lets the caller decorate the party name', async () => {
    const printDoc = vi.fn()
    const getDetail = vi.fn(async () => ({ id: 'r9', receiptNo: 'RC009' } as Receipt))
    const list = createList({
      printDoc,
      getDetail,
      decoratePrint: (doc: Receipt) => ({ ...doc, customerName: '甲客户' })
    })

    await list.handlePrint({ id: 'r9' } as Receipt)
    expect(getDetail).toHaveBeenCalledWith('r9')
    expect(printDoc).toHaveBeenCalledWith(expect.objectContaining({
      receiptNo: 'RC009',
      customerName: '甲客户'
    }))

    const onError = vi.fn()
    const failing = createList({
      getDetail: vi.fn(async () => { throw new Error('boom') }),
      printDoc,
      onError
    })
    await failing.handlePrint({ id: 'r9' } as Receipt)
    expect(onError).toHaveBeenCalledWith('financeReportPages.payments.message.printLoadFailed')
  })

  it('cancels after confirmation using the document number in the prompt', async () => {
    const confirm = vi.fn(async () => true)
    const cancelDoc = vi.fn(async () => ({}))
    const getList = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const list = createList({ confirm, cancelDoc, getList, onSuccess })

    const result = await list.handleCancel({ id: 'r1', receiptNo: 'RC001' } as Receipt)

    expect(result).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'financeReportPages.payments.message.receiptCancelConfirm:RC001',
      'financeReportPages.common.prompt',
      { type: 'warning' }
    )
    expect(cancelDoc).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.payments.message.cancelled')
    expect(getList).toHaveBeenCalledTimes(1)
  })

  it('stays silent when the confirmation is dismissed and reports call failures', async () => {
    const cancelDoc = vi.fn(async () => ({}))
    const onError = vi.fn()
    const dismissed = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      cancelDoc,
      onError
    })

    expect(await dismissed.handleCancel({ id: 'r1', receiptNo: 'RC001' } as Receipt)).toBe(false)
    expect(cancelDoc).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({
      cancelDoc: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failing.handleCancel({ id: 'r1', receiptNo: 'RC001' } as Receipt)).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.payments.message.cancelFailed')
  })
})
