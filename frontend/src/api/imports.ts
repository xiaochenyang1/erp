import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export type ImportType =
  | 'PRODUCT'
  | 'CUSTOMER'
  | 'SUPPLIER'
  | 'WAREHOUSE'
  | 'LOCATION'
  | 'OPENING_INVENTORY'
  | 'OPENING_RECEIVABLE'
  | 'OPENING_PAYABLE'
  | 'OPENING_ACCOUNT_BALANCE'

export type ImportJobStatus = 'VALIDATED' | 'INVALID' | 'COMMITTING' | 'COMMITTED' | 'FAILED'

export interface ImportRowError {
  column: string
  message: string
}

export interface ImportRow {
  rowNo: number
  valid: boolean
  raw: Record<string, string>
  normalized: Record<string, unknown>
  errors: ImportRowError[]
}

export interface ImportJob {
  jobId: string
  importType: ImportType | string
  fileName: string
  status: ImportJobStatus | string
  totalRows: number
  validRows: number
  errorRows: number
  committedRows: number
  errorMessage?: string
  rows: ImportRow[]
}

export interface ImportJobQuery extends PageQuery {
  importType?: ImportType | string
  status?: ImportJobStatus | string
  createdBy?: string | number
  createdTimeFrom?: string
  createdTimeTo?: string
}

export const downloadImportTemplate = (type: ImportType) => {
  return request.get<Blob>(`/import/templates/${type}`, {
    responseType: 'blob'
  })
}

export const previewImportJob = (type: ImportType, file: File) => {
  const formData = new FormData()
  formData.append('file', file)

  return request.post<ImportJob>(`/import/jobs/${type}/preview`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  }).then(normalizeImportJob)
}

export const listImportJobs = (params: ImportJobQuery) => {
  return request.get<PageResponse<ImportJob>>('/import/jobs', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeImportJob)
  }))
}

export const getImportJob = (jobId: string | number) => {
  return request.get<ImportJob>(`/import/jobs/${jobId}`).then(normalizeImportJob)
}

export const exportImportErrorRows = (jobId: string | number) => {
  return request.get<Blob>(`/import/jobs/${jobId}/error-rows/export`, {
    responseType: 'blob'
  })
}

export const commitImportJob = (jobId: string | number) => {
  return request.post<ImportJob>(`/import/jobs/${jobId}/commit`).then(normalizeImportJob)
}

const normalizeImportRow = (row: ImportRow): ImportRow => ({
  ...row,
  rowNo: Number(row.rowNo || 0),
  valid: Boolean(row.valid),
  raw: row.raw || {},
  normalized: row.normalized || {},
  errors: row.errors || []
})

const normalizeImportJob = (job: ImportJob): ImportJob => ({
  ...job,
  jobId: String(job.jobId),
  totalRows: Number(job.totalRows || 0),
  validRows: Number(job.validRows || 0),
  errorRows: Number(job.errorRows || 0),
  committedRows: Number(job.committedRows || 0),
  rows: (job.rows || []).map(normalizeImportRow)
})
