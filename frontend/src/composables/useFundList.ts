import { reactive, ref } from 'vue'

import type {
  BankStatement,
  BankStatementQuery,
  FundAccount,
  FundAccountQuery
} from '@/api/fund'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Queries and detail dialogs for the fund accounts / bank statements tabs.
 * `allAccounts` backs both the statement filter and the statement editor, so it
 * is kept separate from the paged account list.
 */
export const useFundList = (
  t: Translate,
  options: {
    getAccounts: (params: FundAccountQuery) => Promise<PageResponse<FundAccount>>
    getAccount: (id: string | number) => Promise<FundAccount>
    getStatements: (params: BankStatementQuery) => Promise<PageResponse<BankStatement>>
    getStatement: (id: string | number) => Promise<BankStatement>
    onError?: Notify
  }
) => {
  const accountQuery = reactive<FundAccountQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    accountType: '',
    status: ''
  })
  const statementQuery = reactive<BankStatementQuery>({
    pageNo: 1,
    pageSize: 20,
    fundAccountId: undefined,
    direction: '',
    status: ''
  })

  const accountLoading = ref(false)
  const statementLoading = ref(false)
  const accountData = ref<FundAccount[]>([])
  const allAccounts = ref<FundAccount[]>([])
  const statementData = ref<BankStatement[]>([])
  const accountTotal = ref(0)
  const statementTotal = ref(0)

  const accountDetailVisible = ref(false)
  const statementDetailVisible = ref(false)
  const detailLoading = ref(false)
  const selectedAccount = ref<FundAccount>()
  const selectedStatementDetail = ref<BankStatement>()

  /** Only enabled accounts are offered as options for new statements. */
  const loadAllAccounts = async () => {
    try {
      const response = await options.getAccounts({ pageNo: 1, pageSize: 200, status: 'ENABLED' })
      allAccounts.value = response.records || []
    } catch {
      options.onError?.(t('financeReportPages.funds.message.accountsLoadFailed'))
    }
  }

  const loadAccounts = async () => {
    accountLoading.value = true
    try {
      const response = await options.getAccounts(accountQuery)
      accountData.value = response.records || []
      accountTotal.value = response.total || 0
    } catch {
      options.onError?.(t('financeReportPages.funds.message.accountsLoadFailed'))
    } finally {
      accountLoading.value = false
    }
    await loadAllAccounts()
  }

  const loadStatements = async () => {
    statementLoading.value = true
    try {
      const response = await options.getStatements(statementQuery)
      statementData.value = response.records || []
      statementTotal.value = response.total || 0
    } catch {
      options.onError?.(t('financeReportPages.funds.message.statementsLoadFailed'))
    } finally {
      statementLoading.value = false
    }
  }

  /** Statement names need the account options, so the tab loads both. */
  const loadStatementTab = async () => {
    if (allAccounts.value.length === 0) await loadAllAccounts()
    await loadStatements()
  }

  const searchAccounts = async () => {
    accountQuery.pageNo = 1
    await loadAccounts()
  }

  const searchStatements = async () => {
    statementQuery.pageNo = 1
    await loadStatements()
  }

  const resetAccountQuery = async () => {
    Object.assign(accountQuery, { keyword: '', accountType: '', status: '' })
    await searchAccounts()
  }

  const resetStatementQuery = async () => {
    Object.assign(statementQuery, { fundAccountId: undefined, direction: '', status: '' })
    await searchStatements()
  }

  const handleViewAccount = async (row: FundAccount) => {
    accountDetailVisible.value = true
    selectedAccount.value = undefined
    detailLoading.value = true
    try {
      selectedAccount.value = await options.getAccount(row.id)
      return true
    } catch {
      options.onError?.(t('financeReportPages.funds.message.accountDetailLoadFailed'))
      accountDetailVisible.value = false
      return false
    } finally {
      detailLoading.value = false
    }
  }

  const handleViewStatement = async (row: BankStatement) => {
    statementDetailVisible.value = true
    selectedStatementDetail.value = undefined
    detailLoading.value = true
    try {
      selectedStatementDetail.value = await options.getStatement(row.id)
      return true
    } catch {
      options.onError?.(t('financeReportPages.funds.message.statementDetailLoadFailed'))
      statementDetailVisible.value = false
      return false
    } finally {
      detailLoading.value = false
    }
  }

  return {
    accountData,
    accountDetailVisible,
    accountLoading,
    accountQuery,
    accountTotal,
    allAccounts,
    detailLoading,
    handleViewAccount,
    handleViewStatement,
    loadAccounts,
    loadAllAccounts,
    loadStatementTab,
    loadStatements,
    resetAccountQuery,
    resetStatementQuery,
    searchAccounts,
    searchStatements,
    selectedAccount,
    selectedStatementDetail,
    statementData,
    statementDetailVisible,
    statementLoading,
    statementQuery,
    statementTotal
  }
}
