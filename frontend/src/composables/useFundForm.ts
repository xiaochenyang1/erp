import { reactive, ref } from 'vue'

import type {
  BankStatement,
  BankStatementCreateRequest,
  BankStatementMatchRequest,
  FundAccountCreateRequest
} from '@/api/fund'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Prompt = (
  message: string,
  title: string,
  options: {
    inputValue?: string
    inputPattern?: RegExp
    inputErrorMessage?: string
  }
) => Promise<{ value: string }>

const emptyAccountForm = () => ({
  accountCode: '',
  accountName: '',
  accountType: 'BANK',
  bankName: '',
  bankAccountNo: '',
  currencyCode: 'CNY',
  openingBalance: 0,
  remark: ''
})

const emptyStatementForm = () => ({
  fundAccountId: '' as string | number,
  externalTxnNo: '',
  transactionDate: '',
  direction: 'IN',
  amount: 0,
  counterpartyName: '',
  summary: '',
  remark: ''
})

const emptyMatchForm = () => ({
  bizType: 'RECEIPT',
  bizId: '',
  remark: ''
})

/**
 * Create flows for fund accounts and bank statements, plus statement
 * match/unmatch. Reconciliation is the reason statements exist, so matching a
 * statement to a receipt/payment lives here rather than in the list.
 */
export const useFundForm = (
  t: Translate,
  options: {
    createAccount: (data: FundAccountCreateRequest) => Promise<unknown>
    createStatement: (data: BankStatementCreateRequest) => Promise<unknown>
    matchStatement: (id: string | number, data: BankStatementMatchRequest) => Promise<unknown>
    unmatchStatement: (id: string | number, reason: string) => Promise<unknown>
    prompt: Prompt
    onError?: Notify
    onSuccess?: Notify
    onAccountSubmitted?: () => void | Promise<void>
    onStatementSubmitted?: () => void | Promise<void>
  }
) => {
  const accountDialogVisible = ref(false)
  const statementDialogVisible = ref(false)
  const matchDialogVisible = ref(false)
  const accountSubmitting = ref(false)
  const statementSubmitting = ref(false)
  const matchSubmitting = ref(false)
  const selectedStatement = ref<BankStatement | null>(null)

  const accountForm = reactive(emptyAccountForm())
  const statementForm = reactive(emptyStatementForm())
  const matchForm = reactive(emptyMatchForm())

  const resetAccountForm = () => Object.assign(accountForm, emptyAccountForm())
  const resetStatementForm = () => Object.assign(statementForm, emptyStatementForm())
  const resetMatchForm = () => Object.assign(matchForm, emptyMatchForm())

  const openAccountDialog = () => {
    resetAccountForm()
    accountDialogVisible.value = true
  }

  const openStatementDialog = () => {
    resetStatementForm()
    statementForm.transactionDate = formatBusinessDate()
    statementDialogVisible.value = true
  }

  /** An inbound line can only match a receipt, an outbound one only a payment. */
  const openMatchDialog = (row: BankStatement) => {
    selectedStatement.value = row
    resetMatchForm()
    matchForm.bizType = row.direction === 'IN' ? 'RECEIPT' : 'PAYMENT'
    matchDialogVisible.value = true
  }

  const submitAccount = async () => {
    accountSubmitting.value = true
    try {
      await options.createAccount({ ...accountForm })
      options.onSuccess?.(t('financeReportPages.funds.message.accountCreated'))
      accountDialogVisible.value = false
      await options.onAccountSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.funds.message.accountCreateFailed'))
      return false
    } finally {
      accountSubmitting.value = false
    }
  }

  const submitStatement = async () => {
    statementSubmitting.value = true
    try {
      await options.createStatement({ ...statementForm })
      options.onSuccess?.(t('financeReportPages.funds.message.statementCreated'))
      statementDialogVisible.value = false
      await options.onStatementSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.funds.message.statementCreateFailed'))
      return false
    } finally {
      statementSubmitting.value = false
    }
  }

  const submitMatch = async () => {
    if (!selectedStatement.value) return false
    matchSubmitting.value = true
    try {
      await options.matchStatement(selectedStatement.value.id, { ...matchForm })
      options.onSuccess?.(t('financeReportPages.funds.message.matched'))
      matchDialogVisible.value = false
      await options.onStatementSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.funds.message.matchFailed'))
      return false
    } finally {
      matchSubmitting.value = false
    }
  }

  /** Unmatching needs a reason so the reconciliation trail stays auditable. */
  const handleUnmatch = async (row: BankStatement) => {
    let reason: string
    try {
      const result = await options.prompt(
        t('financeReportPages.funds.message.unmatchPrompt'),
        t('financeReportPages.funds.message.unmatchTitle'),
        {
          inputValue: t('financeReportPages.funds.message.unmatchDefaultReason'),
          inputPattern: /\S+/,
          inputErrorMessage: t('financeReportPages.funds.validation.unmatchReason')
        }
      )
      reason = result?.value
    } catch {
      return false
    }
    try {
      await options.unmatchStatement(row.id, reason)
      options.onSuccess?.(t('financeReportPages.funds.message.unmatched'))
      await options.onStatementSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.funds.message.unmatchFailed'))
      return false
    }
  }

  return {
    accountDialogVisible,
    accountForm,
    accountSubmitting,
    handleUnmatch,
    matchDialogVisible,
    matchForm,
    matchSubmitting,
    openAccountDialog,
    openMatchDialog,
    openStatementDialog,
    resetAccountForm,
    resetMatchForm,
    resetStatementForm,
    selectedStatement,
    statementDialogVisible,
    statementForm,
    statementSubmitting,
    submitAccount,
    submitMatch,
    submitStatement
  }
}
