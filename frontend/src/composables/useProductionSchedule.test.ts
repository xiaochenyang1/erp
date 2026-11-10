import { describe, expect, it, vi } from 'vitest'

import type { ProductionScheduleResult } from '@/api/production'
import { useProductionSchedule } from './useProductionSchedule'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const result = (committed = false): ProductionScheduleResult => ({
  startDate: '2026-11-09',
  endDate: '2026-11-20',
  committed,
  orderCount: 1,
  operationCount: 2,
  scheduledCount: 2,
  unscheduledCount: 0,
  delayedCount: 0,
  generatedTime: '2026-11-08T10:00:00',
  items: [],
  workCenters: []
})

const createSchedule = (overrides: Partial<Parameters<typeof useProductionSchedule>[1]> = {}) =>
  useProductionSchedule(t, {
    previewSchedule: vi.fn(async () => result()),
    runSchedule: vi.fn(async () => result(true)),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('production schedule', () => {
  it('previews the selected horizon and stores the result', async () => {
    const previewSchedule = vi.fn(async () => result())
    const schedule = createSchedule({ previewSchedule })
    schedule.dateRange.value = ['2026-11-09', '2026-11-20']

    expect(await schedule.preview()).toBe(true)

    expect(previewSchedule).toHaveBeenCalledWith({
      startDate: '2026-11-09', endDate: '2026-11-20'
    })
    expect(schedule.result.value).toEqual(result())
    expect(schedule.hasResult.value).toBe(true)
    expect(schedule.loading.value).toBe(false)
  })

  it('confirms and runs a committed schedule', async () => {
    const runSchedule = vi.fn(async () => result(true))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const schedule = createSchedule({ runSchedule, confirm, onSuccess })
    schedule.dateRange.value = ['2026-11-09', '2026-11-20']

    expect(await schedule.execute()).toBe(true)

    expect(confirm).toHaveBeenCalledWith(
      'productionSchedule.message.runConfirm:{"startDate":"2026-11-09","endDate":"2026-11-20"}',
      'productionSchedule.message.runTitle'
    )
    expect(runSchedule).toHaveBeenCalledWith({
      startDate: '2026-11-09', endDate: '2026-11-20'
    })
    expect(onSuccess).toHaveBeenCalledWith(
      'productionSchedule.message.runSucceeded:{"scheduled":2,"unscheduled":0}'
    )
    expect(schedule.result.value?.committed).toBe(true)
    expect(schedule.running.value).toBe(false)
  })

  it('does not run when confirmation is cancelled', async () => {
    const runSchedule = vi.fn(async () => result(true))
    const schedule = createSchedule({
      runSchedule,
      confirm: vi.fn(async () => { throw new Error('cancel') })
    })
    schedule.dateRange.value = ['2026-11-09', '2026-11-20']

    expect(await schedule.execute()).toBe(false)
    expect(runSchedule).not.toHaveBeenCalled()
  })

  it('reports validation and API errors without leaving loading state behind', async () => {
    const onError = vi.fn()
    const schedule = createSchedule({
      previewSchedule: vi.fn(async () => { throw new Error('preview failed') }),
      onError
    })

    schedule.dateRange.value = ['', '']
    expect(await schedule.execute()).toBe(false)
    expect(onError).toHaveBeenCalledWith('productionSchedule.validation.dateRange')

    schedule.dateRange.value = ['2026-11-09', '2026-11-20']
    expect(await schedule.preview()).toBe(false)
    expect(onError).toHaveBeenLastCalledWith('preview failed')
    expect(schedule.loading.value).toBe(false)
  })
})
