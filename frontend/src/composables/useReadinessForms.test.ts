import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

import type { ReadinessItem, ReadinessRun, ReadinessRunDetail } from '@/api/readiness'
import {
  getDecisionBlockingItems,
  isDecisionGoBlocked,
  useReadinessForms
} from './useReadinessForms'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.count != null) return `${key}:${params.count}`
  if (params?.name != null) return `${key}:${params.name}`
  return key
}

const run = (overrides: Partial<ReadinessRun> = {}) =>
  ({
    id: 'r1',
    runNo: 'RR001',
    releaseCommit: 'abc',
    environment: 'LOCAL',
    status: 'IN_PROGRESS',
    decision: 'PENDING',
    ...overrides
  }) as ReadinessRun

const item = (overrides: Partial<ReadinessItem> = {}) =>
  ({
    id: 'i1',
    runId: 'r1',
    itemCode: 'A',
    itemName: '验收项',
    category: 'GATE',
    priority: 'P0',
    status: 'PENDING',
    evidence: [],
    ...overrides
  }) as ReadinessItem

const createForms = (
  overrides: Partial<Parameters<typeof useReadinessForms>[1]> = {}
) => {
  const selectedDetail = ref<ReadinessRunDetail | null>({
    run: run(),
    items: [item()]
  })
  const selectedRun = ref<ReadinessRun | null>(run())
  return useReadinessForms(t, {
    createRun: vi.fn(async () => run({ id: 'r2' })),
    addItem: vi.fn(async () => ({})),
    addEvidence: vi.fn(async () => ({})),
    markResult: vi.fn(async () => ({})),
    decideRun: vi.fn(async () => ({})),
    getRunDetail: vi.fn(async () => ({
      run: run(),
      items: [item({ status: 'PASSED' }), item({ id: 'i2', priority: 'P1', status: 'FAILED' })]
    })),
    selectedDetail,
    selectedRun,
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })
}

describe('readiness forms', () => {
  it('computes GO blocking items from unfinished P0/P1', () => {
    const items = [
      item({ priority: 'P0', status: 'PENDING' }),
      item({ id: 'i2', priority: 'P1', status: 'PASSED' }),
      item({ id: 'i3', priority: 'P2', status: 'FAILED' })
    ]
    expect(getDecisionBlockingItems(items)).toHaveLength(1)
    expect(isDecisionGoBlocked('GO', items)).toBe(true)
    expect(isDecisionGoBlocked('NO_GO', items)).toBe(false)
  })

  it('creates a run and opens create dialog with generated commit', async () => {
    const onRunCreated = vi.fn()
    const createRun = vi.fn(async () => run({ id: 'r9' }))
    const forms = createForms({ createRun, onRunCreated })
    forms.openRunDialog()
    expect(forms.runDialogVisible.value).toBe(true)
    expect(forms.runForm.releaseCommit).toMatch(/^local-\d+$/)
    expect(await forms.submitRun()).toBe(true)
    expect(createRun).toHaveBeenCalled()
    expect(onRunCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 'r9' }))
  })

  it('adds items/evidence/results against the selected detail/item', async () => {
    const addItem = vi.fn(async () => ({}))
    const addEvidence = vi.fn(async () => ({}))
    const markResult = vi.fn(async () => ({}))
    const forms = createForms({ addItem, addEvidence, markResult })

    forms.openItemDialog()
    forms.itemForm.itemCode = 'X'
    forms.itemForm.itemName = '项'
    forms.itemForm.category = 'C'
    expect(await forms.submitItem()).toBe(true)
    expect(addItem).toHaveBeenCalledWith('r1', expect.objectContaining({ itemCode: 'X' }))

    forms.openEvidenceDialog(item())
    expect(forms.evidenceForm.summary).toBe('systemReadiness.defaultEvidenceSummary:验收项')
    expect(await forms.submitEvidence()).toBe(true)
    expect(addEvidence).toHaveBeenCalledWith('i1', expect.objectContaining({ evidenceType: 'NOTE' }))

    forms.openResultDialog(item({ status: 'FAILED', actualResult: 'x' }))
    expect(forms.resultForm.status).toBe('FAILED')
    expect(await forms.submitResult()).toBe(true)
    expect(markResult).toHaveBeenCalledWith('i1', expect.objectContaining({ status: 'FAILED' }))
  })

  it('blocks GO decisions when unfinished P0/P1 items exist', async () => {
    const decideRun = vi.fn(async () => ({}))
    const onError = vi.fn()
    const forms = createForms({ decideRun, onError })
    await forms.openDecisionDialog(run())
    expect(forms.decisionBlockingItems.value.length).toBeGreaterThan(0)
    forms.decisionForm.decision = 'GO'
    expect(forms.decisionGoBlocked.value).toBe(true)
    expect(await forms.submitDecision()).toBe(false)
    expect(decideRun).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('systemReadiness.message.decisionBlocked'))
  })

  it('saves NO_GO decisions and syncs status options', async () => {
    const decideRun = vi.fn(async () => ({}))
    const forms = createForms({
      decideRun,
      getRunDetail: vi.fn(async () => ({ run: run(), items: [item({ status: 'PASSED' })] }))
    })
    await forms.openDecisionDialog(run())
    forms.decisionForm.decision = 'NO_GO'
    await nextTick()
    expect(forms.decisionForm.status).toBe('NO_GO')
    expect(forms.decisionStatusOptions.value.map((item) => item.value)).toEqual([
      'FAILED',
      'BLOCKED',
      'NO_GO'
    ])
    expect(await forms.submitDecision()).toBe(true)
    expect(decideRun).toHaveBeenCalledWith('r1', expect.objectContaining({ decision: 'NO_GO' }))
  })
})
