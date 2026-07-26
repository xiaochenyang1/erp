import { describe, expect, it, vi } from 'vitest'

import type { ReadinessRun } from '@/api/readiness'
import { useReadinessList } from './useReadinessList'

const t = (key: string) => key

const run = (overrides: Partial<ReadinessRun> = {}) =>
  ({
    id: 'r1',
    runNo: 'RR001',
    releaseCommit: 'abc',
    environment: 'LOCAL',
    status: 'DRAFT',
    decision: 'PENDING',
    ...overrides
  }) as ReadinessRun

const createList = (overrides: Partial<Parameters<typeof useReadinessList>[1]> = {}) =>
  useReadinessList(t, {
    getPreflight: vi.fn(async () => ({
      overallStatus: 'PASS',
      checkedAt: '2026-07-26T10:00:00',
      items: []
    })),
    getRuns: vi.fn(async () => ({ records: [run()], total: 1 } as any)),
    getRunDetail: vi.fn(async () => ({
      run: run(),
      items: []
    })),
    recordPreflightEvidence: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('readiness list', () => {
  it('loads preflight and runs with filled filters', async () => {
    const getRuns = vi.fn(async () => ({ records: [], total: 3 } as any))
    const list = createList({ getRuns })

    await list.loadPreflight()
    expect(list.preflight.value.overallStatus).toBe('PASS')

    list.queryForm.pageNo = 4
    list.queryForm.releaseCommit = 'abc'
    list.queryForm.environment = 'LOCAL'
    list.queryForm.status = 'DRAFT'
    list.queryForm.decision = 'PENDING'
    await list.handleQuery()

    expect(getRuns).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      releaseCommit: 'abc',
      environment: 'LOCAL',
      status: 'DRAFT',
      decision: 'PENDING'
    })
    expect(list.runTotal.value).toBe(3)
  })

  it('pages and resets independently', async () => {
    const getRuns = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getRuns })

    await list.handlePageChange(2)
    expect(list.queryForm.pageNo).toBe(2)

    await list.handleSizeChange(50)
    expect(list.queryForm.pageSize).toBe(50)
    expect(list.queryForm.pageNo).toBe(1)

    list.queryForm.releaseCommit = 'x'
    await list.handleReset()
    expect(list.queryForm.releaseCommit).toBe('')
  })

  it('opens detail and records preflight evidence', async () => {
    const getRunDetail = vi.fn(async () => ({ run: run({ remark: 'detail' }), items: [] }))
    const recordPreflightEvidence = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ getRunDetail, recordPreflightEvidence, onSuccess })

    expect(await list.openDetail(run())).toBe(true)
    expect(list.detailVisible.value).toBe(true)
    expect(list.selectedDetail.value?.run.remark).toBe('detail')

    expect(await list.handleRecordPreflight(run())).toBe(true)
    expect(recordPreflightEvidence).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('systemReadiness.message.preflightRecorded')
  })

  it('reports load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getPreflight: vi.fn(async () => { throw new Error('boom') }),
      getRuns: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadPreflight()
    expect(onError).toHaveBeenCalledWith('systemReadiness.message.loadPreflightFailed')
    await list.loadRuns()
    expect(onError).toHaveBeenCalledWith('systemReadiness.message.loadRunsFailed')
  })
})
