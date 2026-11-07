import { describe, expect, it, vi } from 'vitest'

import type { ContractRecord } from '@/api/contracts'
import { useContractList } from './useContractList'

const t = (key: string) => key

const row = (overrides: Partial<ContractRecord> = {}) =>
  ({ id: 'c1', contractNo: 'HT001', contractName: '框架协议', status: 'DRAFT', ...overrides }) as ContractRecord

const createList = (overrides: Partial<Parameters<typeof useContractList>[1]> = {}) =>
  useContractList(t, {
    getContracts: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getContractAlerts: vi.fn(async () => [
      { contractId: 'c1', alertTypes: ['CONTRACT_EXPIRING'] } as any
    ]),
    exportContracts: vi.fn(async () => new Blob(['x'])),
    getCustomers: vi.fn(async () => ({ records: [{ id: 'cu1' }], total: 1 } as any)),
    getSuppliers: vi.fn(async () => ({ records: [{ id: 'su1' }], total: 1 } as any)),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
    downloadBlob: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('contract list', () => {
  it('loads a page of contracts and clears the loading flag', async () => {
    const getContracts = vi.fn(async () => ({ records: [row()], total: 7 } as any))
    const list = createList({ getContracts })

    expect(await list.loadData()).toBe(true)
    expect(getContracts).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 20, keyword: '', contractType: '', status: '' })
    )
    expect(list.rows.value).toHaveLength(1)
    expect(list.total.value).toBe(7)
    expect(list.loading.value).toBe(false)
  })

  it('reports a failed load and leaves no stale loading state', async () => {
    const onError = vi.fn()
    const list = createList({
      getContracts: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await list.loadData()).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('pages, resizes and resets the query', async () => {
    const getContracts = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getContracts })

    await list.handlePageChange(3)
    expect(list.query.pageNo).toBe(3)
    expect(getContracts).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    await list.handleSizeChange(50)
    expect(list.query.pageSize).toBe(50)
    expect(list.query.pageNo).toBe(1)

    list.query.keyword = 'HT'
    list.query.contractType = 'SALES'
    list.query.status = 'ACTIVE'
    list.query.pageNo = 4
    await list.resetQuery()

    expect(list.query).toMatchObject({ pageNo: 1, keyword: '', contractType: '', status: '' })
    expect(list.query.pageSize).toBe(50)
  })

  it('loads every picker in one round trip and reports a failure once', async () => {
    const list = createList()
    expect(await list.loadOptions()).toBe(true)
    expect(list.customers.value.map((item) => item.id)).toEqual(['cu1'])
    expect(list.suppliers.value.map((item) => item.id)).toEqual(['su1'])
    expect(list.products.value.map((item) => item.id)).toEqual(['p1'])

    const onError = vi.fn()
    const failing = createList({
      getProducts: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failing.loadOptions()).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.optionsFailed')
    expect(onError).toHaveBeenCalledTimes(1)
  })

  it('degrades to no alert badges without bothering the user', async () => {
    const list = createList()
    expect(await list.loadAlerts()).toBe(true)
    expect(list.alerts.value).toHaveLength(1)

    const onError = vi.fn()
    const failing = createList({
      getContractAlerts: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failing.loadAlerts()).toBe(false)
    expect(failing.alerts.value).toEqual([])
    expect(onError).not.toHaveBeenCalled()
  })

  it('exports with the current filters and names the file from the catalog', async () => {
    const exportContracts = vi.fn(async () => new Blob(['x']))
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ exportContracts, downloadBlob, onSuccess })

    list.query.status = 'ACTIVE'
    expect(await list.handleExport()).toBe(true)
    expect(exportContracts).toHaveBeenCalledWith(expect.objectContaining({ status: 'ACTIVE' }))
    expect(downloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'contractPage.fileName')
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.exported')

    const onError = vi.fn()
    const failing = createList({
      exportContracts: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failing.handleExport()).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.actionFailed')
  })

  it('confirms a status action, then runs it and reloads the page', async () => {
    const confirm = vi.fn(async () => true)
    const getContracts = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const action = vi.fn(async () => ({}))
    const list = createList({ confirm, getContracts, onSuccess })

    expect(await list.runAction(action, row(), 'contractPage.submit')).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'contractPage.message.confirmAction',
      'contractPage.message.prompt',
      { type: 'warning' }
    )
    expect(action).toHaveBeenCalledWith('c1')
    expect(getContracts).toHaveBeenCalledTimes(1)
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.actionDone')
  })

  it('stays silent when the confirmation is dismissed but reports action failures', async () => {
    const onError = vi.fn()
    const action = vi.fn(async () => ({}))
    const dismissed = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      onError
    })

    expect(await dismissed.runAction(action, row(), 'contractPage.submit')).toBe(false)
    expect(action).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({ onError })
    expect(await failing.runAction(
      vi.fn(async () => { throw new Error('boom') }),
      row(),
      'contractPage.submit'
    )).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.actionFailed')
  })
})
