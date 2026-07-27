import { computed, reactive, ref } from 'vue'

import type {
  ImportJob,
  ImportJobQuery,
  ImportType
} from '@/api/imports'
import type { PageResponse } from '@/types/common'
import { useSystemImportPresentation } from './useSystemImportPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>

/**
 * Import job list, template download, preview upload, detail and commit.
 * Element Upload instance stays on the page; selected File is held here.
 */
export const useSystemImportList = (
  t: Translate,
  options: {
    listJobs: (params: ImportJobQuery) => Promise<PageResponse<ImportJob>>
    getJob: (jobId: string | number) => Promise<ImportJob>
    previewJob: (type: ImportType, file: File) => Promise<ImportJob>
    commitJob: (jobId: string | number) => Promise<ImportJob>
    downloadTemplate: (type: ImportType) => Promise<Blob>
    exportErrorRows: (jobId: string | number) => Promise<Blob>
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const { importTypeOptions, countJobsWithErrors } = useSystemImportPresentation(t)

  const queryForm = reactive<ImportJobQuery>({
    importType: '',
    status: '',
    createdBy: ''
  })
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const loading = ref(false)
  const previewing = ref(false)
  const tableData = ref<ImportJob[]>([])
  const previewJob = ref<ImportJob>()
  const detailJob = ref<ImportJob>()
  const detailVisible = ref(false)
  const committingId = ref('')
  const selectedFile = ref<File | null>(null)

  const jobsWithErrors = computed(() => countJobsWithErrors(tableData.value))
  const currentImportType = computed<ImportType>(() =>
    (queryForm.importType || importTypeOptions.value[0].value) as ImportType
  )

  const buildQueryParams = (): ImportJobQuery => ({
    importType: queryForm.importType || undefined,
    status: queryForm.status || undefined,
    createdBy: queryForm.createdBy ? String(queryForm.createdBy).trim() : undefined,
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.listJobs(buildQueryParams())
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch {
      options.onError?.(t('systemImports.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.importType = ''
    queryForm.status = ''
    queryForm.createdBy = ''
    pagination.page = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.page = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.size = size
    pagination.page = 1
    await loadData()
  }

  const handleDownloadTemplate = async () => {
    try {
      const type = currentImportType.value
      const blob = await options.downloadTemplate(type)
      options.downloadBlob(blob, t('systemImports.templateFile', { type: type.toLowerCase() }))
      options.onSuccess?.(t('systemImports.message.templateDownloadStarted'))
      return true
    } catch {
      options.onError?.(t('systemImports.message.templateDownloadFailed'))
      return false
    }
  }

  const handleFileChange = (file?: File | null) => {
    selectedFile.value = file || null
  }

  const handleFileRemove = () => {
    selectedFile.value = null
  }

  const handlePreview = async () => {
    if (!selectedFile.value) {
      options.onWarning?.(t('systemImports.message.selectCsv'))
      return false
    }
    previewing.value = true
    try {
      const job = await options.previewJob(currentImportType.value, selectedFile.value)
      previewJob.value = job
      detailJob.value = job
      detailVisible.value = true
      queryForm.importType = job.importType
      pagination.page = 1
      await loadData()
      options.onSuccess?.(
        job.status === 'VALIDATED'
          ? t('systemImports.message.previewValidated')
          : t('systemImports.message.previewHasErrors')
      )
      return true
    } catch {
      options.onError?.(t('systemImports.message.previewFailed'))
      return false
    } finally {
      previewing.value = false
    }
  }

  const handleViewDetail = async (row: ImportJob) => {
    try {
      detailJob.value = await options.getJob(row.jobId)
      detailVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemImports.message.detailLoadFailed'))
      return false
    }
  }

  const handleExportErrors = async (row: ImportJob) => {
    try {
      const blob = await options.exportErrorRows(row.jobId)
      options.downloadBlob(blob, t('systemImports.errorFile', { jobId: row.jobId }))
      options.onSuccess?.(t('systemImports.message.errorExportStarted'))
      return true
    } catch {
      options.onError?.(t('systemImports.message.errorExportFailed'))
      return false
    }
  }

  const handleCommit = async (row: ImportJob) => {
    const action = row.status === 'FAILED'
      ? t('systemImports.retryCommit')
      : t('systemImports.commit')
    try {
      await options.confirm(
        t('systemImports.message.commitConfirm', { action, jobId: row.jobId }),
        t('systemImports.prompt'),
        {
          confirmButtonText: t('systemImports.confirm'),
          cancelButtonText: t('systemImports.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }

    committingId.value = row.jobId
    try {
      const job = await options.commitJob(row.jobId)
      previewJob.value = previewJob.value?.jobId === job.jobId ? job : previewJob.value
      detailJob.value = detailJob.value?.jobId === job.jobId ? job : detailJob.value
      await loadData()
      options.onSuccess?.(t('systemImports.message.commitSuccess'))
      return true
    } catch {
      options.onError?.(t('systemImports.message.commitFailed'))
      return false
    } finally {
      committingId.value = ''
    }
  }

  return {
    committingId,
    currentImportType,
    detailJob,
    detailVisible,
    handleCommit,
    handleDownloadTemplate,
    handleExportErrors,
    handleFileChange,
    handleFileRemove,
    handlePageChange,
    handlePreview,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleViewDetail,
    jobsWithErrors,
    loadData,
    loading,
    pagination,
    previewJob,
    previewing,
    queryForm,
    selectedFile,
    tableData
  }
}
