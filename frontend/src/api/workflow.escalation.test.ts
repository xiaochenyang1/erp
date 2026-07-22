import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()
const get = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    post: (...args: unknown[]) => post(...args),
    get: (...args: unknown[]) => get(...args),
    put: (...args: unknown[]) => put(...args)
  }
}))

import { escalateWorkflowTask, getWorkflowApprovalConfig, saveWorkflowApprovalConfig } from '@/api/workflow'

beforeEach(() => {
  post.mockReset()
  get.mockReset()
  put.mockReset()
})

describe('审批超时升级 API', () => {
  it('提交升级目标并归一化雪花 ID', async () => {
    post.mockResolvedValue({
      id: '9007199254740993',
      businessId: '9007199254740995',
      approverUserId: '9007199254740997',
      status: 'PENDING',
      overdue: false,
      escalationCount: 1
    })

    const task = await escalateWorkflowTask({
      taskId: '9007199254740993',
      targetUserId: '9007199254740997',
      comment: 'manager escalation'
    })

    expect(post).toHaveBeenCalledWith('/workflow/tasks/9007199254740993/escalate', {
      targetUserId: '9007199254740997',
      comment: 'manager escalation'
    })
    expect(task.id).toBe('9007199254740993')
    expect(task.approverUserId).toBe('9007199254740997')
    expect(task.escalationCount).toBe(1)
  })

  it('round-trips the configurable task timeout', async () => {
    get.mockResolvedValue({ businessType: 'SALES_ORDER', status: 'ACTIVE', taskTimeoutHours: 6, nodes: [] })
    put.mockResolvedValue({ businessType: 'SALES_ORDER', status: 'ACTIVE', taskTimeoutHours: 12, nodes: [] })

    await expect(getWorkflowApprovalConfig('SALES_ORDER')).resolves.toMatchObject({ taskTimeoutHours: 6 })
    await expect(saveWorkflowApprovalConfig('SALES_ORDER', {
      configName: 'Sales approval', status: 'ACTIVE', taskTimeoutHours: 12, nodes: []
    })).resolves.toMatchObject({ taskTimeoutHours: 12 })
    expect(put).toHaveBeenCalledWith('/workflow/configs/SALES_ORDER', expect.objectContaining({ taskTimeoutHours: 12 }))
  })
})
