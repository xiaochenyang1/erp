import { describe, expect, it, vi } from 'vitest'

import type { AccountSubject } from '@/api/finance'
import { hasSubjectQuery, useAccountSubjectList } from './useAccountSubjectList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const subject = (overrides: Partial<AccountSubject> = {}): AccountSubject => ({
  id: '1',
  code: '1001',
  name: 'Cash',
  category: 'ASSET',
  status: 'ACTIVE',
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useAccountSubjectList>[1]> = {}) =>
  useAccountSubjectList(t, {
    getAccountSubjectTree: vi.fn(async () => [subject()]),
    getAccountSubjects: vi.fn(async () => ({
      records: [subject({ id: '2', name: 'Bank' })],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })),
    enableAccountSubject: vi.fn(async () => ({})),
    disableAccountSubject: vi.fn(async () => ({})),
    subjectDisplayName: (row) => row.name || String(row.id),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('account subject list helpers', () => {
  it('detects active filters', () => {
    expect(hasSubjectQuery({})).toBe(false)
    expect(hasSubjectQuery({ subjectCode: '1' })).toBe(true)
    expect(hasSubjectQuery({ status: 'ACTIVE' })).toBe(true)
  })
})

describe('account subject list', () => {
  it('loads tree by default and flat search when filtered', async () => {
    const getAccountSubjectTree = vi.fn(async () => [subject()])
    const getAccountSubjects = vi.fn(async () => ({
      records: [subject({ id: '2', name: 'Bank' })],
      total: 1,
      pageNo: 1,
      pageSize: 20
    }))
    const list = createList({ getAccountSubjectTree, getAccountSubjects })

    expect(await list.loadData()).toBe(true)
    expect(getAccountSubjectTree).toHaveBeenCalled()
    expect(getAccountSubjects).not.toHaveBeenCalled()
    expect(list.subjectTree.value[0].name).toBe('Cash')

    list.queryForm.subjectCode = '1002'
    await list.handleQuery()
    expect(getAccountSubjects).toHaveBeenCalledWith(
      expect.objectContaining({ subjectCode: '1002', pageNo: 1, pageSize: 200 })
    )
    expect(list.subjectTree.value[0].name).toBe('Bank')

    await list.handleReset()
    expect(list.queryForm.subjectCode).toBe('')
    expect(getAccountSubjects.mock.calls.length).toBe(1)
  })

  it('enables and disables with confirmation', async () => {
    const enableAccountSubject = vi.fn(async () => ({}))
    const disableAccountSubject = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ enableAccountSubject, disableAccountSubject, onSuccess })

    expect(await list.handleEnable(subject({ status: 'DISABLED' }))).toBe(true)
    expect(enableAccountSubject).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.subjects.message.enabled')

    expect(await list.handleDisable(subject())).toBe(true)
    expect(disableAccountSubject).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.subjects.message.disabled')
  })

  it('aborts enable when confirm is dismissed', async () => {
    const enableAccountSubject = vi.fn(async () => ({}))
    const list = createList({
      enableAccountSubject,
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })
    expect(await list.handleEnable(subject())).toBe(false)
    expect(enableAccountSubject).not.toHaveBeenCalled()
  })
})
