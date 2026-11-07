import { describe, expect, it, vi } from 'vitest'

import type { WorkflowTask } from '@/api/workflow'
import {
  cleanWorkflowTaskQuery,
  useWorkflowTaskList
} from './useWorkflowTaskList'

const t = (key: string) => key

const task = (overrides: Partial<WorkflowTask> = {}): WorkflowTask => ({
  id: '10',
  businessType: 'SALES_ORDER',
  businessId: '100',
  businessNo: 'SO1',
  title: 'Approve SO1',
  status: 'PENDING',
  createdTime: '2026-07-01T00:00:00',
  approverUserId: 'u1',
  overdue: false,
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useWorkflowTaskList>[1]> = {}) =>
  useWorkflowTaskList(t, {
    getWorkflowTasks: vi.fn(async () => ({
      records: [task()],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })),
    getWorkflowTask: vi.fn(async () => task({ title: 'Detail SO1' })),
    approveWorkflowTask: vi.fn(async () => ({})),
    rejectWorkflowTask: vi.fn(async () => ({})),
    transferWorkflowTask: vi.fn(async () => ({})),
    escalateWorkflowTask: vi.fn(async () => task()),
    getUsers: vi.fn(async () => ({
      records: [
        { id: 'u1', username: 'alice' },
        { id: 'u2', username: 'bob' }
      ],
      total: 2,
      pageNo: 1,
      pageSize: 200
    })),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('workflow task list', () => {
  it('cleans empty query fields and loads pages independently', async () => {
    expect(cleanWorkflowTaskQuery({
      pageNo: 2,
      pageSize: 20,
      businessType: '',
      businessNo: 'SO1',
      status: 'PENDING',
      overdueOnly: false
    })).toEqual({
      pageNo: 2,
      pageSize: 20,
      businessType: undefined,
      businessId: undefined,
      businessNo: 'SO1',
      status: 'PENDING',
      overdueOnly: undefined
    })

    const getWorkflowTasks = vi.fn(async () => ({
      records: [task()],
      total: 3,
      pageNo: 1,
      pageSize: 20
    }))
    const list = createList({ getWorkflowTasks })
    expect(await list.loadData()).toBe(true)
    expect(list.tableData.value).toHaveLength(1)
    expect(list.total.value).toBe(3)

    await list.handleSizeChange(50)
    expect(list.queryParams.pageSize).toBe(50)
    expect(list.queryParams.pageNo).toBe(1)
    await list.handlePageChange(2)
    expect(list.queryParams.pageNo).toBe(2)
    expect(getWorkflowTasks).toHaveBeenCalled()
  })

  it('resets filters and applies route query', async () => {
    const list = createList()
    list.queryParams.businessType = 'EXPENSE'
    list.queryParams.businessNo = 'FE1'
    list.queryParams.overdueOnly = true
    await list.handleReset()
    expect(list.queryParams.businessType).toBe('')
    expect(list.queryParams.businessNo).toBe('')
    expect(list.queryParams.status).toBe('PENDING')
    expect(list.queryParams.overdueOnly).toBe(false)
    expect(list.queryParams.pageNo).toBe(1)

    await list.applyRouteQuery({
      pageNo: 1,
      pageSize: 10,
      businessType: 'SALES_ORDER',
      businessId: '99',
      businessNo: 'SO9',
      status: 'PENDING',
      overdueOnly: true
    })
    expect(list.queryParams.businessId).toBe('99')
    expect(list.queryParams.overdueOnly).toBe(true)
  })

  it('opens detail and approves / rejects tasks', async () => {
    const getWorkflowTask = vi.fn(async () => task({ title: 'Detail' }))
    const approveWorkflowTask = vi.fn(async () => ({}))
    const rejectWorkflowTask = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onWarning = vi.fn()
    const list = createList({
      getWorkflowTask,
      approveWorkflowTask,
      rejectWorkflowTask,
      onSuccess,
      onWarning
    })

    expect(await list.handleView(task())).toBe(true)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentTask.value?.title).toBe('Detail')

    list.openApprove(task())
    expect(list.actionMode.value).toBe('approve')
    expect(list.actionVisible.value).toBe(true)
    expect(await list.handleConfirmAction()).toBe(true)
    expect(approveWorkflowTask).toHaveBeenCalledWith({
      taskId: '10',
      comment: undefined
    })
    expect(onSuccess).toHaveBeenCalledWith('workflow.approvalSuccess')

    list.openReject(task())
    expect(await list.handleConfirmAction()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('workflow.inputRejectionReason')

    list.actionForm.comment = '  no '
    expect(await list.handleConfirmAction()).toBe(true)
    expect(rejectWorkflowTask).toHaveBeenCalledWith({
      taskId: '10',
      reason: 'no'
    })
    expect(onSuccess).toHaveBeenCalledWith('workflow.rejectedSuccess')
  })

  it('transfers and escalates with user options', async () => {
    const transferWorkflowTask = vi.fn(async () => ({}))
    const escalateWorkflowTask = vi.fn(async () => task())
    const onSuccess = vi.fn()
    const onWarning = vi.fn()
    const list = createList({
      transferWorkflowTask,
      escalateWorkflowTask,
      onSuccess,
      onWarning
    })

    await list.openTransfer(task())
    expect(list.transferVisible.value).toBe(true)
    expect(list.transferUsers.value).toHaveLength(2)
    expect(await list.submitTransfer()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('workflow.selectTransferUser')

    list.transferUserId.value = 'u2'
    list.actionForm.comment = 'please handle'
    expect(await list.submitTransfer()).toBe(true)
    expect(transferWorkflowTask).toHaveBeenCalledWith({
      taskId: '10',
      targetUserId: 'u2',
      comment: 'please handle'
    })
    expect(onSuccess).toHaveBeenCalledWith('workflow.transferSuccess')

    await list.openEscalate(task({ overdue: true }))
    expect(list.escalateVisible.value).toBe(true)
    // current approver excluded
    expect(list.escalateUsers.value.map((u) => String(u.id))).toEqual(['u2'])
    expect(await list.submitEscalate()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('workflow.selectEscalationUser')

    list.escalateUserId.value = 'u2'
    list.escalateComment.value = ' overdue '
    expect(await list.submitEscalate()).toBe(true)
    expect(escalateWorkflowTask).toHaveBeenCalledWith({
      taskId: '10',
      targetUserId: 'u2',
      comment: 'overdue'
    })
    expect(onSuccess).toHaveBeenCalledWith('workflow.escalationSuccess')
  })
})
