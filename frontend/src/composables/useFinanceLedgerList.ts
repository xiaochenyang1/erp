import { reactive, ref } from 'vue'

import type {
  AccountSubject,
  LedgerEntry,
  LedgerSummary
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
    exportLedger: (params: LedgerQueryParams) => Promise<Blob>
    downloadBlob: (blob: Blob, fileName: string) => void
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const activeTab = ref<'general' | 'detail'>('general')
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

  const handleQuery = () => {
    syncDateRange()
    if (activeTab.value === 'general') {
      return loadGeneralLedger()
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
    const name = String(tabName) as 'general' | 'detail'
    activeTab.value = name
    if (name === 'general') {
      return loadGeneralLedger()
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
      const blob = await options.exportLedger(buildLedgerQueryParams())
      options.downloadBlob(
        blob,
        t('financeReportPages.ledger.fileName', { timestamp: Date.now() })
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
    loadDetailLedger,
    loadGeneralLedger,
    loadSubjects,
    pagination,
    queryForm,
    subjectOptions
  }
}
