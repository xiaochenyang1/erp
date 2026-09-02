import { computed, ref } from 'vue'

import type {
  AccountPeriod,
  AccountPeriodCloseCheck,
  AccountPeriodCloseSnapshot,
  InventoryFinanceDifference,
  InventoryFinanceDifferenceDetail,
  InventoryFinanceReconciliation
} from '@/api/finance'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title?: string,
  options?: {
    type?: string
    confirmButtonText?: string
    cancelButtonText?: string
    confirmButtonClass?: string
  }
) => Promise<unknown>

export const useFinancePeriodActions = (
  t: Translate,
  options: {
    getPeriods: (year?: number) => Promise<AccountPeriod[]>
    generatePeriods: (year: number) => Promise<AccountPeriod[]>
    checkClose: (id: string | number) => Promise<AccountPeriodCloseCheck>
    getCloseSnapshots: (id: string | number) => Promise<AccountPeriodCloseSnapshot[]>
    lockPeriod: (id: string | number) => Promise<unknown>
    closePeriod: (id: string | number) => Promise<unknown>
    unlockPeriod: (id: string | number) => Promise<unknown>
    getReconciliation: (id: string | number) => Promise<InventoryFinanceReconciliation>
    getDifferences: (
      id: string | number,
      params?: { differenceType?: string }
    ) => Promise<InventoryFinanceDifference[]>
    getDifferenceDetail: (
      id: string | number,
      sourceType: string,
      sourceNo: string
    ) => Promise<InventoryFinanceDifferenceDetail>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const currentYear = new Date().getFullYear()
  const queryYear = ref(currentYear)
  const loading = ref(false)
  const generateLoading = ref(false)
  const tableData = ref<AccountPeriod[]>([])

  const closeCheckVisible = ref(false)
  const closeCheckLoading = ref(false)
  const closeCheckResult = ref<AccountPeriodCloseCheck>()

  const wizardVisible = ref(false)
  const wizardLoading = ref(false)
  const wizardActionLoading = ref(false)
  const wizardStep = ref(0)
  const wizardPeriod = ref<AccountPeriod>()
  const wizardCheck = ref<AccountPeriodCloseCheck>()
  const wizardSnapshots = ref<AccountPeriodCloseSnapshot[]>([])

  const reconciliationVisible = ref(false)
  const reconciliationLoading = ref(false)
  const differenceLoading = ref(false)
  const reconciliationResult = ref<InventoryFinanceReconciliation>()
  const differences = ref<InventoryFinanceDifference[]>([])
  const differenceType = ref('')
  const activePeriod = ref<AccountPeriod>()
  const differenceDetailVisible = ref(false)
  const differenceDetailLoading = ref(false)
  const differenceDetail = ref<InventoryFinanceDifferenceDetail>()

  const loadData = async () => {
    loading.value = true
    try {
      tableData.value = (await options.getPeriods(queryYear.value)) || []
    } catch {
      options.onError?.(t('financeReportPages.periods.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleReset = () => {
    queryYear.value = currentYear
    void loadData()
  }

  const handleGenerate = async () => {
    try {
      await options.confirm(
        t('financeReportPages.periods.message.generateConfirm', { year: queryYear.value }),
        t('financeReportPages.periods.message.generateTitle'),
        { type: 'warning' }
      )
      generateLoading.value = true
      tableData.value = (await options.generatePeriods(queryYear.value)) || []
      options.onSuccess?.(t('financeReportPages.periods.message.generated'))
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('financeReportPages.periods.message.generateFailed'))
      }
    } finally {
      generateLoading.value = false
    }
  }

  const handleCheck = async (row: AccountPeriod) => {
    activePeriod.value = row
    closeCheckVisible.value = true
    closeCheckLoading.value = true
    closeCheckResult.value = undefined
    try {
      closeCheckResult.value = await options.checkClose(row.id)
    } catch {
      options.onError?.(t('financeReportPages.periods.message.checkFailed'))
    } finally {
      closeCheckLoading.value = false
    }
  }

  const openWizard = async (row: AccountPeriod) => {
    wizardPeriod.value = row
    wizardStep.value = 0
    wizardCheck.value = undefined
    wizardSnapshots.value = []
    wizardVisible.value = true
  }

  const runWizardCheck = async () => {
    if (!wizardPeriod.value) return
    wizardLoading.value = true
    try {
      wizardCheck.value = await options.checkClose(wizardPeriod.value.id)
      try {
        wizardSnapshots.value = await options.getCloseSnapshots(wizardPeriod.value.id)
      } catch {
        options.onError?.(t('financeReportPages.periods.message.evidenceLoadFailed'))
      }
    } catch {
      options.onError?.(t('financeReportPages.periods.message.checkFailed'))
    } finally {
      wizardLoading.value = false
    }
  }

  const nextWizardStep = async () => {
    if (wizardStep.value === 0) {
      wizardStep.value = 1
      await runWizardCheck()
      return
    }
    if (wizardStep.value === 1) {
      wizardStep.value = 2
    }
  }

  const wizardActionTitle = computed(() => {
    if (wizardPeriod.value?.status === 'CLOSED') return t('financeReportPages.periods.wizardAction.closed')
    if (wizardPeriod.value?.status === 'LOCKED') return t('financeReportPages.periods.wizardAction.locked')
    if (wizardCheck.value?.passed) return t('financeReportPages.periods.wizardAction.ready')
    return t('financeReportPages.periods.wizardAction.blocked')
  })

  const wizardActionSubtitle = computed(() => {
    if (!wizardPeriod.value) return ''
    if (wizardPeriod.value.status === 'CLOSED') {
      return t('financeReportPages.periods.wizardAction.closedSubtitle', {
        period: wizardPeriod.value.periodMonth
      })
    }
    if (wizardPeriod.value.status === 'LOCKED') {
      return t('financeReportPages.periods.wizardAction.lockedSubtitle')
    }
    return wizardCheck.value?.passed
      ? t('financeReportPages.periods.wizardAction.readySubtitle')
      : t('financeReportPages.periods.wizardAction.blockedSubtitle')
  })

  const refreshWizardPeriod = async () => {
    await loadData()
    if (!wizardPeriod.value) return
    wizardPeriod.value = tableData.value.find(
      (item) => String(item.id) === String(wizardPeriod.value?.id)
    )
  }

  const handleLock = async (row: AccountPeriod) => {
    try {
      const check = await options.checkClose(row.id)
      if (!check.passed) {
        activePeriod.value = row
        closeCheckResult.value = check
        closeCheckVisible.value = true
        options.onWarning?.(t('financeReportPages.periods.message.checkBlocksLock'))
        return
      }

      await options.confirm(
        t('financeReportPages.periods.message.lockConfirm', { period: row.periodMonth }),
        t('financeReportPages.periods.lockPeriod'),
        { type: 'warning' }
      )
      await options.lockPeriod(row.id)
      options.onSuccess?.(t('financeReportPages.periods.message.locked'))
      await loadData()
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('financeReportPages.periods.message.lockFailed'))
      }
    }
  }

  const handleClose = async (row: AccountPeriod) => {
    // The backend re-checks as well; the UI blocks explicitly so users do not mistake a failed check for an override path.
    let check: AccountPeriodCloseCheck | undefined
    try {
      check = await options.checkClose(row.id)
    } catch {
      options.onError?.(t('financeReportPages.periods.message.checkBlocksClose'))
      return
    }

    if (check && !check.passed) {
      activePeriod.value = row
      closeCheckResult.value = check
      closeCheckVisible.value = true
      options.onWarning?.(t('financeReportPages.periods.message.checkBlocksClose'))
      return
    } else {
      try {
        await options.confirm(
          t('financeReportPages.periods.message.safeCloseConfirm', { period: row.periodMonth }),
          t('financeReportPages.periods.message.closeTitle'),
          { type: 'warning' }
        )
      } catch {
        return
      }
    }

    try {
      await options.closePeriod(row.id)
      options.onSuccess?.(t('financeReportPages.periods.message.closed'))
      closeCheckVisible.value = false
      await loadData()
    } catch {
      options.onError?.(t('financeReportPages.periods.message.closeFailed'))
    }
  }

  const handleUnlock = async (row: AccountPeriod) => {
    try {
      await options.confirm(
        t('financeReportPages.periods.message.unlockConfirm', { period: row.periodMonth }),
        t('financeReportPages.periods.message.unlockTitle'),
        {
          type: 'warning',
          confirmButtonText: t('financeReportPages.periods.message.confirmUnlock'),
          cancelButtonText: t('financeReportPages.common.cancel')
        }
      )
      await options.unlockPeriod(row.id)
      options.onSuccess?.(t('financeReportPages.periods.message.unlocked'))
      await loadData()
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('financeReportPages.periods.message.unlockFailed'))
      }
    }
  }

  const wizardLock = async () => {
    if (!wizardPeriod.value) return
    wizardActionLoading.value = true
    try {
      await handleLock(wizardPeriod.value)
      await refreshWizardPeriod()
      await runWizardCheck()
    } finally {
      wizardActionLoading.value = false
    }
  }

  const wizardClose = async () => {
    if (!wizardPeriod.value) return
    wizardActionLoading.value = true
    try {
      await handleClose(wizardPeriod.value)
      await refreshWizardPeriod()
      wizardVisible.value = false
    } finally {
      wizardActionLoading.value = false
    }
  }

  const wizardUnlock = async () => {
    if (!wizardPeriod.value) return
    wizardActionLoading.value = true
    try {
      await handleUnlock(wizardPeriod.value)
      await refreshWizardPeriod()
    } finally {
      wizardActionLoading.value = false
    }
  }

  const openReconciliation = async (row: AccountPeriod) => {
    activePeriod.value = row
    reconciliationVisible.value = true
    reconciliationResult.value = undefined
    differences.value = []
    differenceType.value = ''
    await loadReconciliation()
  }

  const loadReconciliation = async () => {
    if (!activePeriod.value) return
    reconciliationLoading.value = true
    differenceLoading.value = true
    try {
      const [summary, rows] = await Promise.all([
        options.getReconciliation(activePeriod.value.id),
        options.getDifferences(activePeriod.value.id)
      ])
      reconciliationResult.value = summary
      differences.value = rows || []
    } catch {
      options.onError?.(t('financeReportPages.periods.message.reconciliationLoadFailed'))
    } finally {
      reconciliationLoading.value = false
      differenceLoading.value = false
    }
  }

  const loadDifferences = async () => {
    if (!activePeriod.value) return
    differenceLoading.value = true
    try {
      differences.value = (await options.getDifferences(activePeriod.value.id, {
        differenceType: differenceType.value || undefined
      })) || []
    } catch {
      options.onError?.(t('financeReportPages.periods.message.differencesLoadFailed'))
    } finally {
      differenceLoading.value = false
    }
  }

  const openDifferenceDetail = async (row: InventoryFinanceDifference) => {
    if (!activePeriod.value) return
    differenceDetailVisible.value = true
    differenceDetailLoading.value = true
    differenceDetail.value = undefined
    try {
      differenceDetail.value = await options.getDifferenceDetail(
        activePeriod.value.id,
        row.sourceType,
        row.sourceNo
      )
    } catch {
      options.onError?.(t('financeReportPages.periods.message.differenceDetailLoadFailed'))
    } finally {
      differenceDetailLoading.value = false
    }
  }

  return {
    activePeriod,
    closeCheckLoading,
    closeCheckResult,
    closeCheckVisible,
    differenceDetail,
    differenceDetailLoading,
    differenceDetailVisible,
    differenceLoading,
    differences,
    differenceType,
    generateLoading,
    handleCheck,
    handleClose,
    handleGenerate,
    handleLock,
    handleReset,
    handleUnlock,
    loadData,
    loadDifferences,
    loadReconciliation,
    loading,
    nextWizardStep,
    openDifferenceDetail,
    openReconciliation,
    openWizard,
    queryYear,
    reconciliationLoading,
    reconciliationResult,
    reconciliationVisible,
    runWizardCheck,
    tableData,
    wizardActionLoading,
    wizardActionSubtitle,
    wizardActionTitle,
    wizardCheck,
    wizardSnapshots,
    wizardClose,
    wizardLoading,
    wizardLock,
    wizardPeriod,
    wizardStep,
    wizardUnlock,
    wizardVisible
  }
}
