import { describe, expect, it, vi } from 'vitest'

import type { WorkflowApprovalConfig } from '@/api/workflow'
import { useWorkflowConfigForm } from './useWorkflowConfigForm'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const emptyConfig = (overrides: Partial<WorkflowApprovalConfig> = {}): WorkflowApprovalConfig => ({
  businessType: 'PURCHASE_ORDER',
  status: 'ACTIVE',
  taskTimeoutHours: 24,
  nodes: [],
  ...overrides
})

const createForm = (overrides: Partial<Parameters<typeof useWorkflowConfigForm>[1]> = {}) =>
  useWorkflowConfigForm(t, {
    getWorkflowApprovalConfig: vi.fn(async () => emptyConfig({
      configName: 'PO flow',
      nodes: [{
        id: 'n1',
        nodeName: 'Level 1',
        nodeOrder: 1,
        approvalMode: 'ANY',
        approvers: [{ id: 'a1', approverType: 'USER', approverId: 'u1' }]
      }]
    })),
    saveWorkflowApprovalConfig: vi.fn(async (_type, data) => emptyConfig({
      configName: data.configName,
      status: data.status || 'ACTIVE',
      taskTimeoutHours: data.taskTimeoutHours,
      nodes: data.nodes.map((node, index) => ({
        nodeName: node.nodeName,
        nodeOrder: index + 1,
        approvalMode: node.approvalMode || 'ANY',
        approvers: node.approvers.map((a) => ({
          approverType: a.approverType,
          approverId: a.approverId
        }))
      }))
    })),
    getUsers: vi.fn(async () => ({
      records: [{ id: 'u1', username: 'alice' } as any],
      total: 1,
      pageNo: 1,
      pageSize: 200
    })),
    getRoles: vi.fn(async () => ({
      records: [{ id: 'r1', name: 'Admin', code: 'ADMIN' } as any],
      total: 1,
      pageNo: 1,
      pageSize: 200
    })),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('workflow config form', () => {
  it('loads options and config, then mutates nodes/approvers', async () => {
    const form = createForm()
    expect(await form.loadOptions()).toBe(true)
    expect(form.users.value).toHaveLength(1)
    expect(form.roles.value).toHaveLength(1)

    expect(await form.loadConfig()).toBe(true)
    expect(form.configForm.configName).toBe('PO flow')
    expect(form.configForm.nodes).toHaveLength(1)
    expect(form.configForm.nodes[0].approvers[0].approverId).toBe('u1')

    form.addNode()
    expect(form.configForm.nodes).toHaveLength(2)
    form.addApprover(0)
    expect(form.configForm.nodes[0].approvers).toHaveLength(2)

    form.removeApprover(0, 1)
    expect(form.configForm.nodes[0].approvers).toHaveLength(1)
    form.removeNode(1)
    expect(form.configForm.nodes).toHaveLength(1)
    // cannot remove last node / last approver
    form.removeNode(0)
    form.removeApprover(0, 0)
    expect(form.configForm.nodes).toHaveLength(1)
    expect(form.configForm.nodes[0].approvers).toHaveLength(1)
  })

  it('validates required fields and timeout range', async () => {
    const onWarning = vi.fn()
    const form = createForm({ onWarning })
    await form.loadConfig()

    form.configForm.configName = '  '
    expect(form.validateConfig()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('workflowConfig.validation.configName')

    form.configForm.configName = 'PO'
    form.configForm.taskTimeoutHours = 0
    expect(form.validateConfig()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('workflowConfig.validation.timeoutRange')

    form.configForm.taskTimeoutHours = 24
    form.configForm.nodes[0].nodeName = ''
    expect(form.validateConfig()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith(
      'workflowConfig.validation.nodeName:{"index":1}'
    )

    form.configForm.nodes[0].nodeName = 'L1'
    form.configForm.nodes[0].approvers[0].approverId = ''
    expect(form.validateConfig()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('workflowConfig.validation.selectApprover')
  })

  it('saves a valid config payload', async () => {
    const saveWorkflowApprovalConfig = vi.fn(async (_type: string, data: any) =>
      emptyConfig({
        configName: data.configName,
        status: data.status,
        taskTimeoutHours: data.taskTimeoutHours,
        nodes: data.nodes
      })
    )
    const onSuccess = vi.fn()
    const form = createForm({ saveWorkflowApprovalConfig, onSuccess })
    await form.loadConfig()
    form.configForm.configName = ' Updated '
    form.configForm.remark = ' note '
    expect(await form.submitConfig()).toBe(true)
    expect(saveWorkflowApprovalConfig).toHaveBeenCalledWith(
      'PURCHASE_ORDER',
      expect.objectContaining({
        configName: 'Updated',
        remark: 'note',
        taskTimeoutHours: 24,
        nodes: [
          expect.objectContaining({
            nodeName: 'Level 1',
            nodeOrder: 1,
            approvers: [{ approverType: 'USER', approverId: 'u1' }]
          })
        ]
      })
    )
    expect(onSuccess).toHaveBeenCalledWith('workflowConfig.message.saved')
  })

  it('reports load and option failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getWorkflowApprovalConfig: vi.fn(async () => {
        throw new Error('boom')
      }),
      getUsers: vi.fn(async () => {
        throw new Error('users')
      }),
      onError
    })
    expect(await form.loadConfig()).toBe(false)
    expect(onError).toHaveBeenCalledWith('workflowConfig.message.loadFailed')
    expect(await form.loadOptions()).toBe(false)
    expect(onError).toHaveBeenCalledWith('workflowConfig.message.optionsLoadFailed')
  })
})
