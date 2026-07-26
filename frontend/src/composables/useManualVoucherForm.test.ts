import { describe, expect, it, vi } from 'vitest'

import type { ManualVoucher } from '@/api/finance'
import { useManualVoucherForm } from './useManualVoucherForm'

const t = (key: string) => key

const detail = (overrides: Partial<ManualVoucher> = {}) => ({
  id: 'v1',
  voucherNo: 'MV001',
  bizDate: '2026-07-20',
  amount: 500,
  status: 'DRAFT',
  remark: '房租',
  lines: [
    { lineNo: 1, subjectId: 's1', subjectCode: '6602', subjectName: '管理费用', debitAmount: 500, creditAmount: 0, summary: '房租' },
    { lineNo: 2, subjectId: 's2', subjectCode: '1002', subjectName: '银行存款', debitAmount: 0, creditAmount: 500, summary: '付款' }
  ],
  ...overrides
} as ManualVoucher)

const createForm = (overrides: Partial<Parameters<typeof useManualVoucherForm>[1]> = {}) =>
  useManualVoucherForm(t, {
    getVoucher: vi.fn(async () => detail()),
    createVoucher: vi.fn(async () => ({})),
    updateVoucher: vi.fn(async () => ({})),
    ensureSubjects: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

/** Fill the two default lines with a balanced debit/credit pair. */
const fillBalanced = (form: ReturnType<typeof createForm>, amount = 500) => {
  form.editForm.bizDate = '2026-07-26'
  form.editForm.lines[0].subjectId = 's1'
  form.editForm.lines[0].debitAmount = amount
  form.editForm.lines[1].subjectId = 's2'
  form.editForm.lines[1].creditAmount = amount
}

describe('manual voucher form', () => {
  it('opens create mode with two blank entry lines and loads accounts', async () => {
    const ensureSubjects = vi.fn(async () => ({}))
    const form = createForm({ ensureSubjects })

    await form.openCreate()

    expect(form.editMode.value).toBe('create')
    expect(form.editVisible.value).toBe(true)
    expect(form.editingId.value).toBe('')
    expect(form.editForm.lines).toHaveLength(2)
    expect(ensureSubjects).toHaveBeenCalled()
  })

  it('keeps at least two lines when removing, and can add more', async () => {
    const form = createForm()
    await form.openCreate()

    form.removeLine(0)
    expect(form.editForm.lines).toHaveLength(2)

    form.addLine()
    expect(form.editForm.lines).toHaveLength(3)
    form.removeLine(2)
    expect(form.editForm.lines).toHaveLength(2)
  })

  it('keeps debit and credit mutually exclusive on a line', async () => {
    const form = createForm()
    await form.openCreate()
    const line = form.editForm.lines[0]

    line.debitAmount = 100
    line.creditAmount = 80
    form.onDebitChange(line)
    expect(line.creditAmount).toBe(0)

    line.creditAmount = 60
    form.onCreditChange(line)
    expect(line.debitAmount).toBe(0)
  })

  it('totals both sides and only balances on equal non-zero amounts', async () => {
    const form = createForm()
    await form.openCreate()

    expect(form.balanced.value).toBe(false)

    fillBalanced(form, 250)
    expect(form.debitTotal.value).toBe(250)
    expect(form.creditTotal.value).toBe(250)
    expect(form.balanced.value).toBe(true)

    form.editForm.lines[1].creditAmount = 240
    expect(form.balanced.value).toBe(false)
  })

  it('treats cent-level float drift as balanced', async () => {
    const form = createForm()
    await form.openCreate()

    form.editForm.bizDate = '2026-07-26'
    form.editForm.lines[0].subjectId = 's1'
    form.editForm.lines[0].debitAmount = 0.1 + 0.2
    form.editForm.lines[1].subjectId = 's2'
    form.editForm.lines[1].creditAmount = 0.3

    expect(form.balanced.value).toBe(true)
    expect(form.canSave.value).toBe(true)
  })

  it('blocks saving without a date, an account, or a single-sided amount', async () => {
    const form = createForm()
    await form.openCreate()

    fillBalanced(form)
    expect(form.canSave.value).toBe(true)

    form.editForm.bizDate = ''
    expect(form.canSave.value).toBe(false)
    form.editForm.bizDate = '2026-07-26'

    form.editForm.lines[0].subjectId = ''
    expect(form.canSave.value).toBe(false)
    form.editForm.lines[0].subjectId = 's1'

    // Both sides filled on one line is not a valid entry.
    form.editForm.lines[0].creditAmount = 500
    expect(form.canSave.value).toBe(false)
    form.editForm.lines[0].creditAmount = 0

    // Neither side filled is equally invalid.
    form.editForm.lines[0].debitAmount = 0
    expect(form.canSave.value).toBe(false)
  })

  it('warns instead of posting an unbalanced voucher', async () => {
    const createVoucher = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const form = createForm({ createVoucher, onWarning })
    await form.openCreate()

    expect(await form.handleSave()).toBe(false)
    expect(createVoucher).not.toHaveBeenCalled()
    expect(onWarning)
      .toHaveBeenCalledWith('financeReportPages.manualVouchers.message.invalidEntries')
  })

  it('creates in create mode and updates in edit mode', async () => {
    const createVoucher = vi.fn(async () => ({}))
    const updateVoucher = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createVoucher, updateVoucher, onSubmitted })

    await form.openCreate()
    fillBalanced(form)
    expect(await form.handleSave()).toBe(true)
    expect(createVoucher).toHaveBeenCalledWith(expect.objectContaining({
      bizDate: '2026-07-26',
      lines: [
        expect.objectContaining({ subjectId: 's1', debitAmount: 500, creditAmount: 0 }),
        expect.objectContaining({ subjectId: 's2', debitAmount: 0, creditAmount: 500 })
      ]
    }))
    expect(form.editVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()

    await form.openEdit({ id: 'v1' } as ManualVoucher)
    expect(await form.handleSave()).toBe(true)
    expect(updateVoucher).toHaveBeenCalledWith('v1', expect.objectContaining({ bizDate: '2026-07-20' }))
  })

  it('loads the full detail when editing and pads short line sets', async () => {
    const getVoucher = vi.fn(async () => detail({
      lines: [
        { lineNo: 1, subjectId: 's1', subjectCode: '6602', subjectName: '管理费用', debitAmount: 100, creditAmount: 0 }
      ] as any
    }))
    const form = createForm({ getVoucher })

    expect(await form.openEdit({ id: 'v1' } as ManualVoucher)).toBe(true)
    expect(form.editMode.value).toBe('edit')
    expect(form.editingId.value).toBe('v1')
    expect(getVoucher).toHaveBeenCalledWith('v1')
    expect(form.editForm.remark).toBe('房租')
    expect(form.editForm.lines).toHaveLength(2)
    expect(form.editForm.lines[1]).toMatchObject({ subjectId: '', debitAmount: 0, creditAmount: 0 })
  })

  it('reports a failed detail load without opening the editor', async () => {
    const onError = vi.fn()
    const form = createForm({
      getVoucher: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.openEdit({ id: 'v1' } as ManualVoucher)).toBe(false)
    expect(form.editVisible.value).toBe(false)
    expect(onError)
      .toHaveBeenCalledWith('financeReportPages.manualVouchers.message.detailLoadFailed')
  })

  it('keeps the editor open and clears saving when the save fails', async () => {
    const onError = vi.fn()
    const form = createForm({
      createVoucher: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await form.openCreate()
    fillBalanced(form)
    expect(await form.handleSave()).toBe(false)
    expect(form.editVisible.value).toBe(true)
    expect(form.saving.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.manualVouchers.message.saveFailed')
  })
})
