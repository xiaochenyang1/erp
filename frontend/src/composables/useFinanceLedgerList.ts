import { reactive, ref } from 'vue'

import type {
  AccountSubject,
  BalanceSheet,
  IncomeStatement,
  LedgerEntry,
  LedgerSummary,
  TrialBalance
} from '@/api/finance'
import {
  findSubjectByCode,
  findSubjectById,
  paginateEntries
} from './useFinanceLedgerPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type LedgerQueryParams = {
  subjectCode?: string
  startDate?: string
  endDate?: string
}

/**
 * Finance general/detail ledger list with subject tree options and export.
 */
export const useFinanceLedgerList = (
  t: Translate,
  options: {
    getAccountSubjectTree: () => Promise<AccountSubject[]>
    getLedgerSummary: (params: LedgerQueryParams) => Promise<LedgerSummary[]>
    getLedgerEntries: (params: LedgerQueryParams) => Promise<LedgerEntry[]>
    getTrialBalance?: (params: LedgerQueryParams) => Promise<TrialBalance>
    getBalanceSheet?: (asOfDate: string) => Promise<BalanceSheet>
    getIncomeStatement?: (params: LedgerQueryParams) => Promise<IncomeStatement>
    exportLedger: (params: LedgerQueryParams) => Promise<Blob>
    exportTrialBalance?: (params: LedgerQueryParams) => Promise<Blob>
    exportBalanceSheet?: (asOfDate: string) => Promise<Blob>
    exportIncomeStatement?: (params: LedgerQueryParams) => Promise<Blob>
    downloadBlob: (blob: Blob, fileName: string) => void
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const activeTab = ref<'general' | 'detail' | 'trial' | 'balance' | 'income'>('general')
  const queryForm = reactive({
    subjectId: undefined as string | number | undefined,
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<string[]>([])
  const subjectOptions = ref<AccountSubject[]>([])
  const generalLoading = ref(false)
  const generalLedger = ref<LedgerSummary[]>([])
  const detailLoading = ref(false)
  const detailLedger = ref<LedgerEntry[]>([])
  const trialLoading = ref(false)
  const trialBalance = ref<TrialBalance | null>(null)
  const statementLoading = ref(false)
  const balanceSheet = ref<BalanceSheet | null>(null)
  const incomeStatement = ref<IncomeStatement | null>(null)
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const syncDateRange = () => {
    if (dateRange.value?.length === 2) {
      queryForm.startDate = dateRange.value[0]
      queryForm.endDate = dateRange.value[1]
    } else {
      queryForm.startDate = ''
      queryForm.endDate = ''
    }
  }

  const selectedSubjectCode = () => {
    const subject = findSubjectById(subjectOptions.value, queryForm.subjectId)
    return subject?.code || subject?.subjectCode || undefined
  }

  const buildLedgerQueryParams = (): LedgerQueryParams => ({
    subjectCode: selectedSubjectCode(),
    startDate: queryForm.startDate || undefined,
    endDate: queryForm.endDate || undefined
  })

  const loadSubjects = async () => {
    try {
      const subjects = await options.getAccountSubjectTree()
      subjectOptions.value = subjects || []
      return true
    } catch {
      subjectOptions.value = []
      options.onError?.(t('financeReportPages.ledger.message.subjectsLoadFailed'))
      return false
    }
  }

  const loadGeneralLedger = async () => {
    generalLoading.value = true
    try {
      const res = await options.getLedgerSummary(buildLedgerQueryParams())
      generalLedger.value = res || []
      return true
    } catch {
      options.onError?.(t('financeReportPages.ledger.message.generalLoadFailed'))
      return false
    } finally {
      generalLoading.value = false
    }
  }

  const loadDetailLedger = async () => {
    detailLoading.value = true
    try {
      const entries = await options.getLedgerEntries(buildLedgerQueryParams())
      pagination.total = entries.length
      detailLedger.value = paginateEntries(entries, pagination.page, pagination.size)
      return true
    } catch {
      options.onError?.(t('financeReportPages.ledger.message.detailLoadFailed'))
      return false
    } finally {
      detailLoading.value = false
    }
  }

  const loadTrialBalance = async () => {
    syncDateRange()
    if (!queryForm.startDate || !queryForm.endDate) {
      options.onError?.(t('financeReportPages.ledger.message.trialDateRequired'))
      return false
    }
    if (!options.getTrialBalance) {
      options.onError?.(t('financeReportPages.ledger.message.trialLoadFailed'))
      return false
    }
    trialLoading.value = true
    try {
      trialBalance.value = await options.getTrialBalance(buildLedgerQueryParams())
      return true
    } catch {
      options.onError?.(t('financeReportPages.ledger.message.trialLoadFailed'))
      return false
    } finally {
      trialLoading.value = false
    }
  }

  const requireDateRange = (messageKey: string) => {
    syncDateRange()
    if (queryForm.startDate && queryForm.endDate) return true
    options.onError?.(t(messageKey))
    return false
  }

  const loadBalanceSheet = async () => {
    if (!requireDateRange('financeReportPages.ledger.message.statementDateRequired')) return false
    if (!options.getBalanceSheet) return false
    statementLoading.value = true
    try {
      balanceSheet.value = await options.getBalanceSheet(queryForm.endDate)
      return true
    } catch {
      options.onError?.(t('financeReportPages.ledger.message.balanceLoadFailed'))
      return false
    } finally {
      statementLoading.value = false
    }
  }

  const loadIncomeStatement = async () => {
    if (!requireDateRange('financeReportPages.ledger.message.statementDateRequired')) return false
    if (!options.getIncomeStatement) return false
    statementLoading.value = true
    try {
      incomeStatement.value = await options.getIncomeStatement(buildLedgerQueryParams())
      return true
    } catch {
      options.onError?.(t('financeReportPages.ledger.message.incomeLoadFailed'))
      return false
    } finally {
      statementLoading.value = false
    }
  }

  const handleQuery = () => {
    syncDateRange()
    if (activeTab.value === 'general') {
      return loadGeneralLedger()
    }
    if (activeTab.value === 'trial') {
      return loadTrialBalance()
    }
    if (activeTab.value === 'balance') {
      return loadBalanceSheet()
    }
    if (activeTab.value === 'income') {
      return loadIncomeStatement()
    }
    pagination.page = 1
    return loadDetailLedger()
  }

  const handleReset = () => {
    queryForm.subjectId = undefined
    queryForm.startDate = ''
    queryForm.endDate = ''
    dateRange.value = []
    pagination.page = 1
    return handleQuery()
  }

  const handleTabChange = (tabName: string | number) => {
    const name = String(tabName) as 'general' | 'detail' | 'trial' | 'balance' | 'income'
    activeTab.value = name
    if (name === 'general') {
      return loadGeneralLedger()
    }
    if (name === 'trial') {
      return loadTrialBalance()
    }
    if (name === 'balance') {
      return loadBalanceSheet()
    }
    if (name === 'income') {
      return loadIncomeStatement()
    }
    pagination.page = 1
    return loadDetailLedger()
  }

  const handleViewDetail = (row: LedgerSummary) => {
    const subject = findSubjectByCode(subjectOptions.value, row.subjectCode)
    if (subject) {
      queryForm.subjectId = subject.id
    }
    activeTab.value = 'detail'
    pagination.page = 1
    return loadDetailLedger()
  }

  const handlePageChange = (page: number) => {
    pagination.page = page
    return loadDetailLedger()
  }

  const handleSizeChange = (size: number) => {
    pagination.size = size
    pagination.page = 1
    return loadDetailLedger()
  }

  const handleExport = async () => {
    try {
      syncDateRange()
      let blob: Blob
      let fileNameKey = 'financeReportPages.ledger.fileName'
      if (activeTab.value === 'trial') {
        if (!requireDateRange('financeReportPages.ledger.message.trialDateRequired') || !options.exportTrialBalance) {
          return false
        }
        blob = await options.exportTrialBalance(buildLedgerQueryParams())
        fileNameKey = 'financeReportPages.ledger.trialFileName'
      } else if (activeTab.value === 'balance') {
        if (!requireDateRange('financeReportPages.ledger.message.statementDateRequired') || !options.exportBalanceSheet) {
          return false
        }
        blob = await options.exportBalanceSheet(queryForm.endDate)
        fileNameKey = 'financeReportPages.ledger.balanceFileName'
      } else if (activeTab.value === 'income') {
        if (!requireDateRange('financeReportPages.ledger.message.statementDateRequired') || !options.exportIncomeStatement) {
          return false
        }
        blob = await options.exportIncomeStatement(buildLedgerQueryParams())
        fileNameKey = 'financeReportPages.ledger.incomeFileName'
      } else {
        blob = await options.exportLedger(buildLedgerQueryParams())
      }
      options.downloadBlob(
        blob,
        t(fileNameKey, { timestamp: Date.now() })
      )
      options.onSuccess?.(t('financeReportPages.ledger.message.exported'))
      return true
    } catch {
      options.onError?.(t('financeReportPages.ledger.message.exportFailed'))
      return false
    }
  }

  return {
    activeTab,
    balanceSheet,
    buildLedgerQueryParams,
    dateRange,
    detailLedger,
    detailLoading,
    generalLedger,
    generalLoading,
    handleExport,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleTabChange,
    handleViewDetail,
    incomeStatement,
    loadBalanceSheet,
    loadDetailLedger,
    loadGeneralLedger,
    loadIncomeStatement,
    loadTrialBalance,
    loadSubjects,
    pagination,
    queryForm,
    subjectOptions,
    statementLoading,
    trialBalance,
    trialLoading
  }
}
