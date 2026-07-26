import { describe, expect, it, vi } from 'vitest'

import type { OperationLog } from '@/api/system'
import { useSystemLogList } from './useSystemLogList'

const t = (key: string) => key

const op = (overrides: Partial<OperationLog> = {}) =>
  ({ id: 'o1', module: 'SYS', operation: 'UPDATE', status: 'SUCCESS', ...overrides }) as OperationLog

const createList = (overrides: Partial<Parameters<typeof useSystemLogList>[1]> = {}) =>
  useSystemLogList(t, {
    getOperationLogs: vi.fn(async () => ({ records: [op()], total: 1 } as any)),
    getOperationLog: vi.fn(async () => op({ message: 'detail' })),
    exportOperationLogs: vi.fn(async () => new Blob(['a'])),
    getLoginLogs: vi.fn(async () => ({ records: [{ id: 'l1', username: 'admin' }], total: 1 } as any)),
    getAuditLogs: vi.fn(async () => ({ records: [{ id: 'a1', auditType: 'CREATE' }], total: 1 } as any)),
    downloadBlob: vi.fn(),
    initialBizNo: 'KW1',
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system log list', () => {
  it('seeds operation and audit keyword filters from initial values', () => {
    const list = createList()
    expect(list.queryForm.bizNo).toBe('KW1')
    expect(list.auditQueryForm.businessNo).toBe('KW1')
  })

  it('queries operation logs with page reset and date range sync', async () => {
    const getOperationLogs = vi.fn(async () => ({ records: [], total: 5 } as any))
    const list = createList({ getOperationLogs })

    list.pagination.page = 4
    list.queryForm.module = 'SYS'
    list.dateRange.value = ['2026-07-01', '2026-07-02']
    await list.handleQuery()

    expect(getOperationLogs).toHaveBeenCalledWith(expect.objectContaining({
      module: 'SYS',
      startDate: '2026-07-01',
      endDate: '2026-07-02',
      pageNo: 1,
      pageSize: 20
    }))
    expect(list.pagination.total).toBe(5)
  })

  it('pages operation logs and returns to page 1 on size change', async () => {
    const getOperationLogs = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getOperationLogs })

    await list.handlePageChange(3)
    expect(list.pagination.page).toBe(3)
    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)
  })

  it('loads login and audit tabs lazily and supports their query/reset', async () => {
    const getLoginLogs = vi.fn(async () => ({ records: [{ id: 'l1' }], total: 2 } as any))
    const getAuditLogs = vi.fn(async () => ({ records: [{ id: 'a1' }], total: 3 } as any))
    const list = createList({ getLoginLogs, getAuditLogs })

    await list.handleTabChange('login')
    expect(getLoginLogs).toHaveBeenCalled()
    expect(list.loginPagination.total).toBe(2)

    list.loginQueryForm.username = 'admin'
    list.loginDateRange.value = ['2026-07-01', '2026-07-02']
    await list.handleLoginQuery()
    expect(getLoginLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      username: 'admin',
      loginTimeFrom: '2026-07-01T00:00:00',
      loginTimeTo: '2026-07-02T23:59:59',
      pageNo: 1
    }))

    await list.handleLoginReset()
    expect(list.loginQueryForm.username).toBe('')

    await list.handleTabChange('audit')
    expect(getAuditLogs).toHaveBeenCalled()
    list.auditQueryForm.businessNo = 'B1'
    await list.handleAuditQuery()
    expect(getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      businessNo: 'B1',
      pageNo: 1
    }))
  })

  it('opens detail and exports operation logs', async () => {
    const downloadBlob = vi.fn()
    const getOperationLog = vi.fn(async () => op({ message: 'detail' }))
    const onSuccess = vi.fn()
    const list = createList({ downloadBlob, getOperationLog, onSuccess })

    expect(await list.handleView(op())).toBe(true)
    expect(list.detailDialogVisible.value).toBe(true)
    expect(list.detailData.value.message).toBe('detail')

    expect(await list.handleExport()).toBe(true)
    expect(downloadBlob).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('systemLogs.message.exportSuccess')
  })

  it('reports load/export failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getOperationLogs: vi.fn(async () => { throw new Error('boom') }),
      exportOperationLogs: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await list.loadData()
    expect(onError).toHaveBeenCalledWith('systemLogs.message.loadDataFailed')
    expect(await list.handleExport()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemLogs.message.exportFailed')
  })
})
