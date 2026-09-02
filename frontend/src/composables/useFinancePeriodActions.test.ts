import { describe, expect, it, vi } from 'vitest'

import type { AccountPeriod } from '@/api/finance'
import { useFinancePeriodActions } from './useFinancePeriodActions'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.period != null) return `${key}:${params.period}`
  if (params?.year != null) return `${key}:${params.year}`
  return key
}

describe('finance period actions', () => {
  const createActions = (overrides: Partial<Parameters<typeof useFinancePeriodActions>[1]> = {}) =>
    useFinancePeriodActions(t, {
      getPeriods: vi.fn(async () => ([
        { id: '1', periodMonth: '2026-07', status: 'OPEN' }
      ] as AccountPeriod[])),
      generatePeriods: vi.fn(async () => ([
        { id: '1', periodMonth: '2026-07', status: 'OPEN' },
        { id: '2', periodMonth: '2026-08', status: 'OPEN' }
      ] as AccountPeriod[])),
      checkClose: vi.fn(async () => ({
        passed: true,
        issues: []
      } as any)),
      getCloseSnapshots: vi.fn(async () => []),
      lockPeriod: vi.fn(async () => ({})),
      closePeriod: vi.fn(async () => ({})),
      unlockPeriod: vi.fn(async () => ({})),
      getReconciliation: vi.fn(async () => ({
        inventoryAmount: 100,
        financeAmount: 100,
        differenceAmount: 0
      } as any)),
      getDifferences: vi.fn(async () => ([
        {
          sourceType: 'SALES_DELIVERY',
          sourceNo: 'SD001',
          differenceType: 'AMOUNT_MISMATCH'
        }
      ] as any)),
      getDifferenceDetail: vi.fn(async () => ({
        sourceType: 'SALES_DELIVERY',
        sourceNo: 'SD001',
        inventoryTransactions: [],
        voucherEntries: []
      } as any)),
      confirm: vi.fn(async () => true),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      ...overrides
    })

  it('loads periods and generates year periods', async () => {
    const actions = createActions()
    await actions.loadData()
    expect(actions.tableData.value).toHaveLength(1)

    await actions.handleGenerate()
    expect(actions.tableData.value).toHaveLength(2)
  })

  it('locks and unlocks periods after confirmation', async () => {
    const lockPeriod = vi.fn(async () => ({}))
    const unlockPeriod = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const actions = createActions({ lockPeriod, unlockPeriod, onSuccess })

    await actions.handleLock({ id: '1', periodMonth: '2026-07', status: 'OPEN' } as AccountPeriod)
    await actions.handleUnlock({ id: '1', periodMonth: '2026-07', status: 'LOCKED' } as AccountPeriod)

    expect(lockPeriod).toHaveBeenCalledWith('1')
    expect(unlockPeriod).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.periods.message.locked')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.periods.message.unlocked')
  })

  it('loads reconciliation and difference detail', async () => {
    const actions = createActions()
    await actions.openReconciliation({ id: '1', periodMonth: '2026-07', status: 'OPEN' } as AccountPeriod)

    expect(actions.reconciliationVisible.value).toBe(true)
    expect(actions.reconciliationResult.value?.differenceAmount).toBe(0)
    expect(actions.differences.value).toHaveLength(1)

    await actions.openDifferenceDetail(actions.differences.value[0])
    expect(actions.differenceDetailVisible.value).toBe(true)
    expect(actions.differenceDetail.value?.sourceNo).toBe('SD001')
  })

  it('advances wizard steps and runs close check', async () => {
    const checkClose = vi.fn(async () => ({
      passed: false,
      issues: [{ type: 'OPEN_DOCUMENTS' }]
    } as any))
    const actions = createActions({ checkClose })
    await actions.openWizard({ id: '1', periodMonth: '2026-07', status: 'OPEN' } as AccountPeriod)
    expect(actions.wizardVisible.value).toBe(true)
    expect(actions.wizardStep.value).toBe(0)

    await actions.nextWizardStep()
    expect(actions.wizardStep.value).toBe(1)
    expect(checkClose).toHaveBeenCalledWith('1')
    expect(actions.wizardCheck.value?.passed).toBe(false)
  })

  it('blocks close when the latest check has issues', async () => {
    const closePeriod = vi.fn(async () => ({}))
    const actions = createActions({
      closePeriod,
      checkClose: vi.fn(async () => ({ passed: false, issues: [{ type: 'OPEN_DOCUMENTS' }] } as any))
    })
    await actions.handleClose({ id: '1', periodMonth: '2026-07', status: 'LOCKED' } as AccountPeriod)
    expect(closePeriod).not.toHaveBeenCalled()
  })
})
