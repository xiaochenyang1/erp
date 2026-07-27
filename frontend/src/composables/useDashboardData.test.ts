import { describe, expect, it, vi } from 'vitest'

import type { OperationsDashboard } from '@/api/dashboard'
import { createEmptyDashboard, useDashboardData } from './useDashboardData'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.time != null ? `${key}:${params.time}` : key

const dash = (): OperationsDashboard => ({
  ...createEmptyDashboard(),
  summary: {
    ...createEmptyDashboard().summary,
    pendingApprovals: 2,
    openReceivables: 3
  },
  todos: [{
    id: '1',
    type: 'WORKFLOW',
    title: '审批',
    priority: 'HIGH',
    route: '/workflow/tasks'
  }],
  generatedAt: '2026-07-26T10:00:00'
})

describe('dashboard data', () => {
  it('creates an empty dashboard shape', () => {
    const empty = createEmptyDashboard()
    expect(empty.summary.pendingApprovals).toBe(0)
    expect(empty.todos).toEqual([])
  })

  it('loads dashboard and aging summary', async () => {
    const onLoaded = vi.fn()
    const data = useDashboardData(t, {
      getDashboard: vi.fn(async () => dash()),
      getAgingSummary: vi.fn(async () => ({ asOfDate: '2026-07-26' } as any)),
      formatDateTime: (value) => value || '-',
      onLoaded
    })

    expect(await data.loadDashboard()).toBe(true)
    expect(data.summary.value.pendingApprovals).toBe(2)
    expect(data.todos.value).toHaveLength(1)
    expect(data.generatedTimeText.value).toBe('dashboard.updatedAt:2026-07-26T10:00:00')
    expect(data.aging.value).toEqual({ asOfDate: '2026-07-26' })
    expect(onLoaded).toHaveBeenCalled()
  })

  it('resets to empty dashboard on load failure and still notifies loaded', async () => {
    const onError = vi.fn()
    const onLoaded = vi.fn()
    const data = useDashboardData(t, {
      getDashboard: vi.fn(async () => { throw new Error('boom') }),
      getAgingSummary: vi.fn(async () => ({ asOfDate: 'x' } as any)),
      formatDateTime: (value) => value || '-',
      onError,
      onLoaded
    })

    expect(await data.loadDashboard()).toBe(false)
    expect(data.dashboard.value.summary.pendingApprovals).toBe(0)
    expect(onError).toHaveBeenCalledWith('dashboard.loadFailed')
    expect(onLoaded).toHaveBeenCalled()
    expect(data.loading.value).toBe(false)
  })
})
