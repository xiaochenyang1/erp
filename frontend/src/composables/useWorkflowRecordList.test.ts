import { describe, expect, it, vi } from 'vitest'

import type { WorkflowRecord } from '@/api/workflow'
import {
  cleanWorkflowRecordQuery,
  filterBusinessRecords,
  paginateRecords,
  useWorkflowRecordList
} from './useWorkflowRecordList'

const t = (key: string) => key

const record = (overrides: Partial<WorkflowRecord> = {}): WorkflowRecord => ({
  id: '1',
  businessType: 'SALES_ORDER',
  businessId: '100',
  businessNo: 'SO1',
  action: 'SUBMIT',
  operatorUserId: 'u1',
  actionTime: '2026-07-01T00:00:00',
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useWorkflowRecordList>[1]> = {}) =>
  useWorkflowRecordList(t, {
    getWorkflowRecords: vi.fn(async () => ({
      records: [record()],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })),
    getBusinessWorkflowRecords: vi.fn(async () => [
      record({ id: '1', action: 'SUBMIT', businessNo: 'SO1' }),
      record({ id: '2', action: 'APPROVE', businessNo: 'SO1' }),
      record({ id: '3', action: 'REJECT', businessNo: 'SO2' })
    ]),
    withdrawWorkflow: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('workflow record list helpers', () => {
  it('cleans empty query fields and filters/paginates business records', () => {
    expect(cleanWorkflowRecordQuery({
      pageNo: 1,
      pageSize: 10,
      businessType: '',
      businessNo: 'SO',
      action: ''
    })).toEqual({
      pageNo: 1,
      pageSize: 10,
      businessType: undefined,
      businessId: undefined,
      businessNo: 'SO',
      action: undefined
    })

    const filtered = filterBusinessRecords(
      [record({ businessNo: 'SO1', action: 'SUBMIT' }), record({ businessNo: 'SO2', action: 'APPROVE' })],
      { businessNo: 'SO1', action: 'SUBMIT' }
    )
    expect(filtered).toHaveLength(1)
    expect(paginateRecords([1, 2, 3, 4], 2, 2)).toEqual([3, 4])
  })
})

describe('workflow record list', () => {
  it('loads global and business-scoped records', async () => {
    const getWorkflowRecords = vi.fn(async () => ({
      records: [record()],
      total: 5,
      pageNo: 1,
      pageSize: 20
    }))
    const getBusinessWorkflowRecords = vi.fn(async () => [
      record({ id: '1', action: 'SUBMIT', businessNo: 'SO1' }),
      record({ id: '2', action: 'APPROVE', businessNo: 'SO1' }),
      record({ id: '3', action: 'REJECT', businessNo: 'SO2' })
    ])
    const list = createList({ getWorkflowRecords, getBusinessWorkflowRecords })

    expect(await list.loadData()).toBe(true)
    expect(getWorkflowRecords).toHaveBeenCalled()
    expect(list.total.value).toBe(5)

    list.queryParams.businessType = 'SALES_ORDER'
    list.queryParams.businessId = '100'
    list.queryParams.businessNo = 'SO1'
    list.queryParams.action = 'APPROVE'
    expect(await list.loadData()).toBe(true)
    expect(getBusinessWorkflowRecords).toHaveBeenCalledWith('SALES_ORDER', '100')
    expect(list.total.value).toBe(1)
    expect(list.tableData.value[0].action).toBe('APPROVE')
  })

  it('resets filters and pages independently', async () => {
    const list = createList()
    list.queryParams.businessType = 'EXPENSE'
    list.queryParams.businessNo = 'FE1'
    await list.handleReset()
    expect(list.queryParams.businessType).toBe('')
    expect(list.queryParams.businessNo).toBe('')
    expect(list.queryParams.pageNo).toBe(1)

    await list.handleSizeChange(50)
    expect(list.queryParams.pageSize).toBe(50)
    expect(list.queryParams.pageNo).toBe(1)
    await list.handlePageChange(3)
    expect(list.queryParams.pageNo).toBe(3)
  })

  it('withdraws a workflow for the selected record', async () => {
    const withdrawWorkflow = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ withdrawWorkflow, onSuccess })
    list.openWithdraw(record({ businessId: '55', businessType: 'SALES_ORDER' }))
    expect(list.withdrawVisible.value).toBe(true)
    list.withdrawForm.comment = '  undo '
    expect(await list.submitWithdraw()).toBe(true)
    expect(withdrawWorkflow).toHaveBeenCalledWith({
      businessType: 'SALES_ORDER',
      businessId: '55',
      comment: 'undo'
    })
    expect(onSuccess).toHaveBeenCalledWith('workflowRecord.message.withdrawSuccess')
    list.resetWithdraw()
    expect(list.currentRecord.value).toBeNull()
    expect(list.withdrawForm.comment).toBe('')
  })
})
