import { computed, reactive, ref, watch, type Ref } from 'vue'

import type {
  ReadinessItem,
  ReadinessItemCreateRequest,
  ReadinessItemResultRequest,
  ReadinessDecisionRequest,
  ReadinessEvidenceCreateRequest,
  ReadinessRun,
  ReadinessRunCreateRequest,
  ReadinessRunDetail
} from '@/api/readiness'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/** Align with backend hasUnpassedP0P1Items: GO is blocked by unfinished P0/P1 items. */
export const getDecisionBlockingItems = (items: ReadinessItem[]) =>
  items.filter(
    (item) => (item.priority === 'P0' || item.priority === 'P1') && item.status !== 'PASSED'
  )

export const isDecisionGoBlocked = (decision: string, items: ReadinessItem[]) =>
  decision === 'GO' && getDecisionBlockingItems(items).length > 0

/**
 * Dialog state and submit flows for run / item / evidence / result / decision.
 * Element form validation stays on the page around each submit* method.
 */
export const useReadinessForms = (
  t: Translate,
  options: {
    createRun: (data: ReadinessRunCreateRequest) => Promise<ReadinessRun>
    addItem: (runId: string | number, data: ReadinessItemCreateRequest) => Promise<unknown>
    addEvidence: (itemId: string | number, data: ReadinessEvidenceCreateRequest) => Promise<unknown>
    markResult: (itemId: string | number, data: ReadinessItemResultRequest) => Promise<unknown>
    decideRun: (runId: string | number, data: ReadinessDecisionRequest) => Promise<unknown>
    getRunDetail: (id: string | number) => Promise<ReadinessRunDetail>
    selectedDetail: Ref<ReadinessRunDetail | null>
    selectedRun: Ref<ReadinessRun | null>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onRunCreated?: (run: ReadinessRun) => void | Promise<void>
    onSubmitted?: () => void | Promise<void>
    onDetailChanged?: () => void | Promise<void>
  }
) => {
  const selectedItem = ref<ReadinessItem | null>(null)

  const runDialogVisible = ref(false)
  const itemDialogVisible = ref(false)
  const evidenceDialogVisible = ref(false)
  const resultDialogVisible = ref(false)
  const decisionDialogVisible = ref(false)

  const runSubmitting = ref(false)
  const itemSubmitting = ref(false)
  const evidenceSubmitting = ref(false)
  const resultSubmitting = ref(false)
  const decisionSubmitting = ref(false)

  const runForm = reactive({
    releaseCommit: '',
    releaseVersion: '',
    environment: 'LOCAL',
    databaseInstance: 'erp_codex_runtime',
    redisInstance: '',
    dockerProfile: 'local',
    generateDefaultItems: true,
    recordPreflightEvidence: true,
    remark: ''
  })

  const itemForm = reactive({
    itemCode: '',
    itemName: '',
    category: '',
    priority: 'P1',
    expectedResult: ''
  })

  const evidenceForm = reactive({
    evidenceType: 'NOTE',
    requestMethod: '',
    requestUri: '',
    httpStatus: undefined as number | undefined,
    businessType: '',
    businessId: '',
    businessNo: '',
    summary: '',
    detail: '',
    attachmentBusinessType: '',
    attachmentBusinessId: ''
  })

  const resultForm = reactive({
    status: 'PASSED',
    actualResult: '',
    failureReason: ''
  })

  const decisionForm = reactive({
    decision: 'GO',
    status: 'PASSED',
    decisionComment: ''
  })

  const decisionItems = ref<ReadinessItem[]>([])
  const decisionItemsLoading = ref(false)

  const decisionStatusOptions = computed(() => {
    if (decisionForm.decision === 'GO') {
      return [{ label: t('systemReadiness.statuses.passed'), value: 'PASSED' }]
    }
    return [
      { label: t('systemReadiness.statuses.failed'), value: 'FAILED' },
      { label: t('systemReadiness.statuses.blocked'), value: 'BLOCKED' },
      { label: t('systemReadiness.statuses.noGo'), value: 'NO_GO' }
    ]
  })

  const decisionBlockingItems = computed(() => getDecisionBlockingItems(decisionItems.value))
  const decisionGoBlocked = computed(() =>
    isDecisionGoBlocked(decisionForm.decision, decisionItems.value)
  )

  watch(
    () => decisionForm.decision,
    (decision) => {
      decisionForm.status = decision === 'GO' ? 'PASSED' : 'NO_GO'
    }
  )

  const resetRunForm = () => {
    Object.assign(runForm, {
      releaseCommit: '',
      releaseVersion: '',
      environment: 'LOCAL',
      databaseInstance: 'erp_codex_runtime',
      redisInstance: '',
      dockerProfile: 'local',
      generateDefaultItems: true,
      recordPreflightEvidence: true,
      remark: ''
    })
  }

  const resetItemForm = () => {
    Object.assign(itemForm, {
      itemCode: '',
      itemName: '',
      category: '',
      priority: 'P1',
      expectedResult: ''
    })
  }

  const resetEvidenceForm = () => {
    Object.assign(evidenceForm, {
      evidenceType: 'NOTE',
      requestMethod: '',
      requestUri: '',
      httpStatus: undefined,
      businessType: '',
      businessId: '',
      businessNo: '',
      summary: '',
      detail: '',
      attachmentBusinessType: '',
      attachmentBusinessId: ''
    })
  }

  const resetResultForm = () => {
    Object.assign(resultForm, { status: 'PASSED', actualResult: '', failureReason: '' })
  }

  const resetDecisionForm = () => {
    Object.assign(decisionForm, { decision: 'GO', status: 'PASSED', decisionComment: '' })
  }

  const openRunDialog = () => {
    resetRunForm()
    runForm.releaseCommit = `local-${Date.now()}`
    runDialogVisible.value = true
  }

  const submitRun = async () => {
    runSubmitting.value = true
    try {
      const run = await options.createRun({ ...runForm })
      options.onSuccess?.(t('systemReadiness.message.runCreated'))
      runDialogVisible.value = false
      await options.onSubmitted?.()
      await options.onRunCreated?.(run)
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.createRunFailed'))
      return false
    } finally {
      runSubmitting.value = false
    }
  }

  const openItemDialog = () => {
    resetItemForm()
    itemDialogVisible.value = true
  }

  const submitItem = async () => {
    if (!options.selectedDetail.value) return false
    itemSubmitting.value = true
    try {
      await options.addItem(options.selectedDetail.value.run.id, { ...itemForm })
      options.onSuccess?.(t('systemReadiness.message.itemAdded'))
      itemDialogVisible.value = false
      await options.onDetailChanged?.()
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.addItemFailed'))
      return false
    } finally {
      itemSubmitting.value = false
    }
  }

  const openEvidenceDialog = (row: ReadinessItem) => {
    selectedItem.value = row
    resetEvidenceForm()
    evidenceForm.summary = t('systemReadiness.defaultEvidenceSummary', { name: row.itemName })
    evidenceDialogVisible.value = true
  }

  const submitEvidence = async () => {
    if (!selectedItem.value) return false
    evidenceSubmitting.value = true
    try {
      await options.addEvidence(selectedItem.value.id, {
        ...evidenceForm,
        httpStatus: evidenceForm.httpStatus || undefined,
        businessId: evidenceForm.businessId || undefined,
        attachmentBusinessId: evidenceForm.attachmentBusinessId || undefined
      })
      options.onSuccess?.(t('systemReadiness.message.evidenceAdded'))
      evidenceDialogVisible.value = false
      await options.onDetailChanged?.()
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.addEvidenceFailed'))
      return false
    } finally {
      evidenceSubmitting.value = false
    }
  }

  const openResultDialog = (row: ReadinessItem) => {
    selectedItem.value = row
    resetResultForm()
    resultForm.status = row.status === 'PENDING' ? 'PASSED' : row.status
    resultForm.actualResult = row.actualResult || ''
    resultForm.failureReason = row.failureReason || ''
    resultDialogVisible.value = true
  }

  const submitResult = async () => {
    if (!selectedItem.value) return false
    resultSubmitting.value = true
    try {
      await options.markResult(selectedItem.value.id, { ...resultForm })
      options.onSuccess?.(t('systemReadiness.message.resultRecorded'))
      resultDialogVisible.value = false
      await options.onDetailChanged?.()
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.recordResultFailed'))
      return false
    } finally {
      resultSubmitting.value = false
    }
  }

  const openDecisionDialog = async (row: ReadinessRun) => {
    options.selectedRun.value = row
    resetDecisionForm()
    decisionDialogVisible.value = true
    decisionItemsLoading.value = true
    try {
      if (options.selectedDetail.value?.run.id === row.id) {
        decisionItems.value = options.selectedDetail.value.items
      } else {
        const detail = await options.getRunDetail(row.id)
        decisionItems.value = detail.items
      }
      return true
    } catch {
      decisionItems.value = []
      options.onWarning?.(t('systemReadiness.message.loadItemsWarning'))
      return false
    } finally {
      decisionItemsLoading.value = false
    }
  }

  const submitDecision = async () => {
    if (!options.selectedRun.value) return false
    if (decisionGoBlocked.value) {
      options.onError?.(
        t('systemReadiness.message.decisionBlocked', {
          count: decisionBlockingItems.value.length
        })
      )
      return false
    }
    decisionSubmitting.value = true
    try {
      await options.decideRun(options.selectedRun.value.id, { ...decisionForm })
      options.onSuccess?.(t('systemReadiness.message.decisionSaved'))
      decisionDialogVisible.value = false
      await options.onSubmitted?.()
      if (options.selectedDetail.value?.run.id === options.selectedRun.value.id) {
        await options.onDetailChanged?.()
      }
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.saveDecisionFailed'))
      return false
    } finally {
      decisionSubmitting.value = false
    }
  }

  return {
    decisionBlockingItems,
    decisionDialogVisible,
    decisionForm,
    decisionGoBlocked,
    decisionItems,
    decisionItemsLoading,
    decisionStatusOptions,
    decisionSubmitting,
    evidenceDialogVisible,
    evidenceForm,
    evidenceSubmitting,
    itemDialogVisible,
    itemForm,
    itemSubmitting,
    openDecisionDialog,
    openEvidenceDialog,
    openItemDialog,
    openResultDialog,
    openRunDialog,
    resetDecisionForm,
    resetEvidenceForm,
    resetItemForm,
    resetResultForm,
    resetRunForm,
    resultDialogVisible,
    resultForm,
    resultSubmitting,
    runDialogVisible,
    runForm,
    runSubmitting,
    selectedItem,
    submitDecision,
    submitEvidence,
    submitItem,
    submitResult,
    submitRun
  }
}
