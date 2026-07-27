import { describe, expect, it, vi } from 'vitest'

import type { AccountSubject } from '@/api/finance'
import { useAccountSubjectForm } from './useAccountSubjectForm'

const t = (key: string) => key

const subject = (overrides: Partial<AccountSubject> = {}): AccountSubject => ({
  id: '1',
  parentId: '0',
  subjectCode: '1001',
  subjectName: 'Cash',
  subjectType: 'ASSET',
  balanceDirection: 'DEBIT',
  status: 'ACTIVE',
  remark: 'note',
  ...overrides
})

const createForm = (overrides: Partial<Parameters<typeof useAccountSubjectForm>[1]> = {}) =>
  useAccountSubjectForm(t, {
    getAccountSubject: vi.fn(async () => subject()),
    createAccountSubject: vi.fn(async () => ({})),
    updateAccountSubject: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

describe('account subject form', () => {
  it('opens create and child modes', async () => {
    const form = createForm()
    form.handleAdd()
    expect(form.dialogMode.value).toBe('create')
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('financeReportPages.subjects.dialog.create')

    form.handleAddChild({ id: '9' })
    expect(form.dialogMode.value).toBe('createChild')
    expect(form.formData.parentId).toBe('9')
    expect(form.dialogTitle.value).toBe('financeReportPages.subjects.dialog.createChild')
  })

  it('creates and updates subjects', async () => {
    const createAccountSubject = vi.fn(async () => ({}))
    const updateAccountSubject = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onSubmitted = vi.fn()
    const form = createForm({
      createAccountSubject,
      updateAccountSubject,
      onSuccess,
      onSubmitted
    })

    form.handleAdd()
    form.formData.subjectCode = '2001'
    form.formData.subjectName = 'AP'
    form.formData.subjectType = 'LIABILITY'
    form.formData.balanceDirection = 'CREDIT'
    expect(await form.submitSave()).toBe(true)
    expect(createAccountSubject).toHaveBeenCalledWith({
      parentId: undefined,
      subjectCode: '2001',
      subjectName: 'AP',
      subjectType: 'LIABILITY',
      balanceDirection: 'CREDIT',
      remark: undefined
    })
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.subjects.message.created')
    expect(onSubmitted).toHaveBeenCalled()

    expect(await form.handleEdit({ id: '1' })).toBe(true)
    expect(form.formData.subjectCode).toBe('1001')
    form.formData.subjectName = 'Cash updated'
    expect(await form.submitSave()).toBe(true)
    expect(updateAccountSubject).toHaveBeenCalledWith(
      '1',
      expect.objectContaining({ subjectName: 'Cash updated' })
    )
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.subjects.message.updated')
  })

  it('defaults balance direction when detail omits it and surfaces load failure', async () => {
    const onError = vi.fn()
    const form = createForm({
      getAccountSubject: vi.fn(async () =>
        subject({ balanceDirection: undefined, subjectType: 'REVENUE' })
      ),
      onError
    })
    expect(await form.handleEdit({ id: '1' })).toBe(true)
    expect(form.formData.balanceDirection).toBe('CREDIT')

    const failing = createForm({
      getAccountSubject: vi.fn(async () => {
        throw new Error('missing')
      }),
      onError
    })
    expect(await failing.handleEdit({ id: 'x' })).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.subjects.message.detailLoadFailed')
  })
})
