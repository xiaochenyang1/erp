import { reactive, ref } from 'vue'

import type { AccountSubject, AccountSubjectQuery } from '@/api/finance'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: { type?: string }) => Promise<unknown>

export const hasSubjectQuery = (query: {
  subjectCode?: string
  subjectName?: string
  subjectType?: string
  status?: string
}) => Boolean(query.subjectCode || query.subjectName || query.subjectType || query.status)

/**
 * Account subject tree list with optional flat search and enable/disable.
 * Tree options for the form are loaded together so the page stays single-source.
 */
export const useAccountSubjectList = (
  t: Translate,
  options: {
    getAccountSubjectTree: () => Promise<AccountSubject[]>
    getAccountSubjects: (params: AccountSubjectQuery) => Promise<PageResponse<AccountSubject>>
    enableAccountSubject: (id: string | number) => Promise<unknown>
    disableAccountSubject: (id: string | number) => Promise<unknown>
    subjectDisplayName: (row: AccountSubject) => string
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive({
    subjectCode: '',
    subjectName: '',
    subjectType: '',
    status: ''
  })
  const loading = ref(false)
  const subjectTree = ref<AccountSubject[]>([])
  const subjectTreeOptions = ref<AccountSubject[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const subjects = await options.getAccountSubjectTree()
      subjectTreeOptions.value = subjects || []
      if (hasSubjectQuery(queryForm)) {
        const page = await options.getAccountSubjects({
          ...queryForm,
          pageNo: 1,
          pageSize: 200
        })
        subjectTree.value = page.records || []
      } else {
        subjectTree.value = subjects || []
      }
      return true
    } catch {
      options.onError?.(t('financeReportPages.subjects.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = () => loadData()

  const handleReset = () => {
    queryForm.subjectCode = ''
    queryForm.subjectName = ''
    queryForm.subjectType = ''
    queryForm.status = ''
    return loadData()
  }

  const confirmThen = async (
    action: (id: string | number) => Promise<unknown>,
    row: AccountSubject,
    keys: { confirm: string; success: string; failed: string }
  ) => {
    try {
      await options.confirm(
        t(keys.confirm, { name: options.subjectDisplayName(row) }),
        t('financeReportPages.common.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await action(row.id)
      options.onSuccess?.(t(keys.success))
      await loadData()
      return true
    } catch {
      options.onError?.(t(keys.failed))
      return false
    }
  }

  const handleEnable = (row: AccountSubject) =>
    confirmThen(options.enableAccountSubject, row, {
      confirm: 'financeReportPages.subjects.message.enableConfirm',
      success: 'financeReportPages.subjects.message.enabled',
      failed: 'financeReportPages.subjects.message.enableFailed'
    })

  const handleDisable = (row: AccountSubject) =>
    confirmThen(options.disableAccountSubject, row, {
      confirm: 'financeReportPages.subjects.message.disableConfirm',
      success: 'financeReportPages.subjects.message.disabled',
      failed: 'financeReportPages.subjects.message.disableFailed'
    })

  return {
    handleDisable,
    handleEnable,
    handleQuery,
    handleReset,
    loadData,
    loading,
    queryForm,
    subjectTree,
    subjectTreeOptions
  }
}
