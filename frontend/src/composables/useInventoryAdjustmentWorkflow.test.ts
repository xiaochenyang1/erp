import { describe, expect, it, vi } from 'vitest'
import type { InventoryAdjustment, InventoryAdjustmentApprovalInfo } from '@/api/inventory'
import { useInventoryAdjustmentWorkflow } from './useInventoryAdjustmentWorkflow'

const row = (status: InventoryAdjustment['status'], id = 'a1') => ({ id, adjustmentNo: `IA-${id}`, status } as InventoryAdjustment)
const history = (instanceId: string) => ({ instanceId, status: 'IN_APPROVAL', records: [] } as InventoryAdjustmentApprovalInfo)
const setup = (overrides: Partial<Parameters<typeof useInventoryAdjustmentWorkflow>[1]> = {}) => {
  const options = {
    submit: vi.fn(async () => ({})), approve: vi.fn(async () => ({})),
    reject: vi.fn(async () => ({})), withdraw: vi.fn(async () => ({})),
    getApprovalInfo: vi.fn(async () => history('i1')),
    confirm: vi.fn(async () => ({})), prompt: vi.fn(async () => ({ value: '  数量有误  ' })),
    reload: vi.fn(async () => ({})), onError: vi.fn(), onSuccess: vi.fn(),
    ...overrides
  }
  return { options, workflow: useInventoryAdjustmentWorkflow(key => key, options) }
}

describe('inventory adjustment approval actions', () => {
  it.each(['DRAFT', 'REJECTED'] as const)('submits %s and reloads after success', async status => {
    const { workflow, options } = setup()
    await workflow.runApprovalAction(row(status), 'submit')
    expect(options.confirm).toHaveBeenCalled()
    expect(options.submit).toHaveBeenCalledWith('a1')
    expect(options.reload).toHaveBeenCalledOnce()
    expect(workflow.busy.value).toBe(false)
  })

  it('ignores approval of a draft and resubmission of an approved adjustment', async () => {
    const { workflow, options } = setup()
    await workflow.runApprovalAction(row('DRAFT'), 'approve')
    await workflow.runApprovalAction(row('APPROVED'), 'submit')
    expect(options.confirm).not.toHaveBeenCalled()
    expect(options.approve).not.toHaveBeenCalled()
    expect(options.submit).not.toHaveBeenCalled()
  })

  it('requires a reason and trims it before rejecting', async () => {
    const { workflow, options } = setup()
    await workflow.runApprovalAction(row('SUBMITTED'), 'reject')
    expect(options.reject).toHaveBeenCalledWith('a1', '数量有误')
    expect(options.reload).toHaveBeenCalledOnce()
  })

  it.each(['   ', 'a'.repeat(256)])('does not send an invalid rejection reason', async value => {
    const { workflow, options } = setup({ prompt: vi.fn(async () => ({ value })) })
    await workflow.runApprovalAction(row('SUBMITTED'), 'reject')
    expect(options.reject).not.toHaveBeenCalled()
    expect(options.onError).toHaveBeenCalled()
    expect(workflow.busy.value).toBe(false)
  })

  it.each(['approve', 'withdraw'] as const)('confirms %s without posting inventory', async action => {
    const { workflow, options } = setup()
    await workflow.runApprovalAction(row('SUBMITTED'), action)
    expect(options[action]).toHaveBeenCalledWith('a1')
    expect(options.reload).toHaveBeenCalledOnce()
  })

  it('does not repeat an action while confirmation is pending', async () => {
    let confirm!: () => void
    const { workflow, options } = setup({ confirm: () => new Promise<void>(resolve => { confirm = resolve }) })
    const first = workflow.runApprovalAction(row('DRAFT'), 'submit')
    await workflow.runApprovalAction(row('DRAFT'), 'submit')
    confirm()
    await first
    expect(options.submit).toHaveBeenCalledOnce()
  })

  it.each(['cancel', 'close'])('leaves the document alone on dialog %s', async reason => {
    const { workflow, options } = setup({ confirm: vi.fn(async () => { throw reason }) })
    await workflow.runApprovalAction(row('DRAFT'), 'submit')
    expect(options.submit).not.toHaveBeenCalled()
    expect(options.onError).not.toHaveBeenCalled()
    expect(workflow.busy.value).toBe(false)
  })

  it('reports a rejected server action and releases the busy guard', async () => {
    const { workflow, options } = setup({ approve: vi.fn(async () => { throw new Error('not an approver') }) })
    await workflow.runApprovalAction(row('SUBMITTED'), 'approve')
    expect(options.reload).not.toHaveBeenCalled()
    expect(options.onError).toHaveBeenCalled()
    expect(workflow.busy.value).toBe(false)
  })

  it('does not show a previous documents history when a later request finishes first', async () => {
    let finishFirst!: (value: InventoryAdjustmentApprovalInfo) => void
    const { workflow } = setup({ getApprovalInfo: vi.fn(id => id === 'a1'
      ? new Promise<InventoryAdjustmentApprovalInfo>(resolve => { finishFirst = resolve }) : Promise.resolve(history('i2'))) })
    const first = workflow.openApprovalHistory(row('SUBMITTED'))
    await workflow.openApprovalHistory(row('SUBMITTED', 'a2'))
    finishFirst(history('i1'))
    await first
    expect(workflow.historyNo.value).toBe('IA-a2')
    expect(workflow.approvalInfo.value?.instanceId).toBe('i2')
    expect(workflow.historyLoading.value).toBe(false)
  })
})
