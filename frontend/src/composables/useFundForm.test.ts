import { describe, expect, it, vi } from 'vitest'

import type { BankStatement } from '@/api/fund'
import { formatBusinessDate } from '@/utils/locale'
import { useFundForm } from './useFundForm'

const t = (key: string) => key

const statement = (overrides: Partial<BankStatement> = {}) => ({
  id: 's1',
  fundAccountId: '1',
  statementNo: 'BS001',
  transactionDate: '2026-07-20',
  direction: 'IN',
  amount: 100,
  summary: '货款',
  status: 'UNMATCHED',
  ...overrides
} as BankStatement)

const createForm = (overrides: Partial<Parameters<typeof useFundForm>[1]> = {}) =>
  useFundForm(t, {
    createAccount: vi.fn(async () => ({})),
    createStatement: vi.fn(async () => ({})),
    matchStatement: vi.fn(async () => ({})),
    unmatchStatement: vi.fn(async () => ({})),
    prompt: vi.fn(async () => ({ value: '录错单据' })),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onAccountSubmitted: vi.fn(),
    onStatementSubmitted: vi.fn(),
    ...overrides
  })

describe('fund form', () => {
  it('opens the account dialog on a clean form', () => {
    const form = createForm()

    form.accountForm.accountCode = 'BANK009'
    form.accountForm.openingBalance = 500
    form.openAccountDialog()

    expect(form.accountDialogVisible.value).toBe(true)
    expect(form.accountForm.accountCode).toBe('')
    expect(form.accountForm.accountType).toBe('BANK')
    expect(form.accountForm.currencyCode).toBe('CNY')
    expect(form.accountForm.openingBalance).toBe(0)
  })

  it('defaults the statement date to the business time zone, not the browser', () => {
    const form = createForm()

    form.statementForm.summary = '旧摘要'
    form.openStatementDialog()

    expect(form.statementDialogVisible.value).toBe(true)
    expect(form.statementForm.summary).toBe('')
    expect(form.statementForm.direction).toBe('IN')
    expect(form.statementForm.transactionDate).toBe(formatBusinessDate())
  })

  it('creates an account, closes the dialog and refreshes the list', async () => {
    const createAccount = vi.fn(async () => ({}))
    const onAccountSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ createAccount, onAccountSubmitted, onSuccess })

    form.openAccountDialog()
    Object.assign(form.accountForm, { accountCode: 'BANK002', accountName: '中行户' })

    expect(await form.submitAccount()).toBe(true)
    expect(createAccount).toHaveBeenCalledWith(expect.objectContaining({
      accountCode: 'BANK002',
      accountName: '中行户',
      currencyCode: 'CNY'
    }))
    expect(form.accountDialogVisible.value).toBe(false)
    expect(form.accountSubmitting.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.funds.message.accountCreated')
    expect(onAccountSubmitted).toHaveBeenCalledTimes(1)
  })

  it('keeps the dialog open when creation fails so the input is not lost', async () => {
    const onError = vi.fn()
    const onAccountSubmitted = vi.fn()
    const form = createForm({
      createAccount: vi.fn(async () => { throw new Error('duplicate') }),
      onAccountSubmitted,
      onError
    })

    form.openAccountDialog()
    form.accountForm.accountCode = 'BANK002'

    expect(await form.submitAccount()).toBe(false)
    expect(form.accountDialogVisible.value).toBe(true)
    expect(form.accountForm.accountCode).toBe('BANK002')
    expect(form.accountSubmitting.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.accountCreateFailed')
    expect(onAccountSubmitted).not.toHaveBeenCalled()
  })

  it('creates a statement and reports failures without closing', async () => {
    const createStatement = vi.fn(async () => ({}))
    const onStatementSubmitted = vi.fn()
    const form = createForm({ createStatement, onStatementSubmitted })

    form.openStatementDialog()
    Object.assign(form.statementForm, { fundAccountId: '1', amount: 250, summary: '收货款' })

    expect(await form.submitStatement()).toBe(true)
    expect(createStatement).toHaveBeenCalledWith(expect.objectContaining({
      fundAccountId: '1',
      amount: 250,
      summary: '收货款'
    }))
    expect(form.statementDialogVisible.value).toBe(false)
    expect(onStatementSubmitted).toHaveBeenCalledTimes(1)

    const onError = vi.fn()
    const failing = createForm({
      createStatement: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    failing.openStatementDialog()
    expect(await failing.submitStatement()).toBe(false)
    expect(failing.statementDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.statementCreateFailed')
  })

  it('preselects the business type that the statement direction allows', () => {
    const form = createForm()

    form.openMatchDialog(statement({ direction: 'IN' }))
    expect(form.matchDialogVisible.value).toBe(true)
    expect(form.matchForm.bizType).toBe('RECEIPT')
    expect(form.matchForm.bizId).toBe('')

    form.matchForm.bizId = '99'
    form.openMatchDialog(statement({ id: 's2', direction: 'OUT' }))
    expect(form.matchForm.bizType).toBe('PAYMENT')
    expect(form.matchForm.bizId).toBe('')
    expect(form.selectedStatement.value?.id).toBe('s2')
  })

  it('matches the selected statement and refreshes the statement list', async () => {
    const matchStatement = vi.fn(async () => ({}))
    const onStatementSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ matchStatement, onStatementSubmitted, onSuccess })

    form.openMatchDialog(statement())
    form.matchForm.bizId = '3001'

    expect(await form.submitMatch()).toBe(true)
    expect(matchStatement).toHaveBeenCalledWith('s1', expect.objectContaining({
      bizType: 'RECEIPT',
      bizId: '3001'
    }))
    expect(form.matchDialogVisible.value).toBe(false)
    expect(form.matchSubmitting.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.funds.message.matched')
    expect(onStatementSubmitted).toHaveBeenCalledTimes(1)
  })

  it('never calls the match API without a selected statement', async () => {
    const matchStatement = vi.fn(async () => ({}))
    const form = createForm({ matchStatement })

    expect(await form.submitMatch()).toBe(false)
    expect(matchStatement).not.toHaveBeenCalled()
    expect(form.matchSubmitting.value).toBe(false)
  })

  it('reports match failures and keeps the dialog open', async () => {
    const onError = vi.fn()
    const onStatementSubmitted = vi.fn()
    const form = createForm({
      matchStatement: vi.fn(async () => { throw new Error('amount mismatch') }),
      onStatementSubmitted,
      onError
    })

    form.openMatchDialog(statement())
    form.matchForm.bizId = '3001'

    expect(await form.submitMatch()).toBe(false)
    expect(form.matchDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.matchFailed')
    expect(onStatementSubmitted).not.toHaveBeenCalled()
  })

  it('requires a non-empty reason before unmatching', async () => {
    const prompt = vi.fn(async (
      _message: string,
      _title: string,
      _options: { inputValue?: string; inputPattern?: RegExp; inputErrorMessage?: string }
    ) => ({ value: '匹配到错误收款单' }))
    const unmatchStatement = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const form = createForm({ prompt, unmatchStatement, onSuccess })

    expect(await form.handleUnmatch(statement({ status: 'MATCHED' }))).toBe(true)
    expect(prompt).toHaveBeenCalledWith(
      'financeReportPages.funds.message.unmatchPrompt',
      'financeReportPages.funds.message.unmatchTitle',
      expect.objectContaining({
        inputValue: 'financeReportPages.funds.message.unmatchDefaultReason',
        inputErrorMessage: 'financeReportPages.funds.validation.unmatchReason'
      })
    )
    expect(prompt.mock.calls[0][2].inputPattern?.test('   ')).toBe(false)
    expect(unmatchStatement).toHaveBeenCalledWith('s1', '匹配到错误收款单')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.funds.message.unmatched')
  })

  it('stays silent when the unmatch prompt is dismissed', async () => {
    const unmatchStatement = vi.fn(async () => ({}))
    const onError = vi.fn()
    const form = createForm({
      prompt: vi.fn(async () => { throw 'cancel' }),
      unmatchStatement,
      onError
    })

    expect(await form.handleUnmatch(statement({ status: 'MATCHED' }))).toBe(false)
    expect(unmatchStatement).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })

  it('reports unmatch failures separately from a dismissed prompt', async () => {
    const onError = vi.fn()
    const onStatementSubmitted = vi.fn()
    const form = createForm({
      unmatchStatement: vi.fn(async () => { throw new Error('period closed') }),
      onStatementSubmitted,
      onError
    })

    expect(await form.handleUnmatch(statement({ status: 'MATCHED' }))).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.unmatchFailed')
    expect(onStatementSubmitted).not.toHaveBeenCalled()
  })
})
