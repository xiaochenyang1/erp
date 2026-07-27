import type { Component } from 'vue'

import type {
  BusinessTraceDocument,
  BusinessTraceResponse
} from '@/api/businessTrace'
import { formatLocalizedCurrency, formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'info' | 'primary' | 'success' | 'warning' | 'danger'

/** Labels, tags and formatters for the business trace page. */
export const useBusinessTracePresentation = (
  t: Translate,
  icons: {
    document: Component
    clock: Component
    money: Component
    box: Component
    warning: Component
    connection: Component
  }
) => {
  const formatMoney = (amount?: number) => formatLocalizedCurrency(Number(amount || 0))

  const formatNumber = (value?: number) =>
    formatLocalizedNumber(Number(value || 0), {
      minimumFractionDigits: 4,
      maximumFractionDigits: 4
    })

  const formatDateTime = (value?: string) => formatLocalizedDateTime(value)

  const documentTagType = (type?: string): TagType => {
    const value = type || ''
    if (value.includes('SALES')) return 'success'
    if (value.includes('PURCHASE')) return 'primary'
    if (value.includes('RECEIVABLE')) return 'warning'
    if (value.includes('PAYABLE')) return 'danger'
    return 'info'
  }

  const timelineItemType = (severity?: string): TagType => {
    if (severity === 'ERROR') return 'danger'
    if (severity === 'WARNING') return 'warning'
    return 'primary'
  }

  const eventTagType = (severity?: string): TagType => {
    if (severity === 'ERROR') return 'danger'
    if (severity === 'WARNING') return 'warning'
    return 'info'
  }

  const priorityLabel = (priority?: string) => {
    const map: Record<string, string> = {
      LOW: t('financeReportPages.traces.priorityLabel.low'),
      MEDIUM: t('financeReportPages.traces.priorityLabel.medium'),
      HIGH: t('financeReportPages.traces.priorityLabel.high'),
      URGENT: t('financeReportPages.traces.priorityLabel.urgent')
    }
    return priority ? map[priority] || priority : '-'
  }

  const priorityTagType = (priority?: string): TagType => {
    const map: Record<string, TagType> = {
      LOW: 'info',
      MEDIUM: 'primary',
      HIGH: 'warning',
      URGENT: 'danger'
    }
    return priority ? map[priority] || 'info' : 'info'
  }

  const ticketStatusLabel = (status?: string) => {
    const map: Record<string, string> = {
      OPEN: t('financeReportPages.traces.ticketStatus.open'),
      PROCESSING: t('financeReportPages.traces.ticketStatus.processing'),
      RESOLVED: t('financeReportPages.traces.ticketStatus.resolved'),
      CLOSED: t('financeReportPages.traces.ticketStatus.closed')
    }
    return status ? map[status] || status : '-'
  }

  const ticketStatusTagType = (status?: string): TagType => {
    const map: Record<string, TagType> = {
      OPEN: 'warning',
      PROCESSING: 'primary',
      RESOLVED: 'success',
      CLOSED: 'info'
    }
    return status ? map[status] || 'info' : 'info'
  }

  const traceStatusLabel = (status?: string) => {
    const labels: Record<string, string> = {
      DRAFT: t('financeReportPages.traces.status.draft'),
      SUBMITTED: t('financeReportPages.traces.status.submitted'),
      PENDING: t('financeReportPages.traces.status.pending'),
      APPROVED: t('financeReportPages.traces.status.approved'),
      REJECTED: t('financeReportPages.traces.status.rejected'),
      POSTED: t('financeReportPages.traces.status.posted'),
      COMPLETED: t('financeReportPages.traces.status.completed'),
      CANCELLED: t('financeReportPages.traces.status.cancelled'),
      CLOSED: t('financeReportPages.traces.status.closed'),
      SUCCESS: t('financeReportPages.traces.status.success'),
      FAILED: t('financeReportPages.traces.status.failed')
    }
    return status ? labels[status] || status : '-'
  }

  const eventIcon = (type?: string) => {
    const iconMap: Record<string, Component> = {
      ORDER: icons.document,
      FULFILLMENT: icons.box,
      FINANCE: icons.money,
      INVENTORY: icons.box,
      WORKFLOW: icons.connection,
      OPERATION_LOG: icons.warning
    }
    return iconMap[type || ''] || icons.document
  }

  const businessTimelineEventLabel = (type?: string) => {
    const map: Record<string, string> = {
      COMMENT: t('financeReportPages.traces.event.comment'),
      ATTACHMENT_UPLOADED: t('financeReportPages.traces.event.uploaded'),
      ATTACHMENT_DELETED: t('financeReportPages.traces.event.deleted')
    }
    return map[type || ''] || type || ''
  }

  const businessTimelineTagType = (type?: string): TagType => {
    const map: Record<string, TagType> = {
      COMMENT: 'primary',
      ATTACHMENT_UPLOADED: 'success',
      ATTACHMENT_DELETED: 'warning'
    }
    return map[type || ''] || 'info'
  }

  const businessTimelineItemType = (type?: string): TagType => {
    if (type === 'ATTACHMENT_DELETED') return 'warning'
    if (type === 'ATTACHMENT_UPLOADED') return 'success'
    return 'primary'
  }

  const buildSummaryItems = (trace: BusinessTraceResponse) => [
    {
      label: t('financeReportPages.traces.relatedDocuments'),
      value: trace.summary.documentCount,
      icon: icons.document,
      tone: 'blue'
    },
    {
      label: t('financeReportPages.traces.timelineEvents'),
      value: trace.summary.timelineCount,
      icon: icons.clock,
      tone: 'green'
    },
    {
      label: t('financeReportPages.traces.openReceivables'),
      value: formatMoney(trace.summary.openReceivableAmount),
      icon: icons.money,
      tone: 'orange'
    },
    {
      label: t('financeReportPages.traces.openPayables'),
      value: formatMoney(trace.summary.openPayableAmount),
      icon: icons.money,
      tone: 'red'
    },
    {
      label: t('financeReportPages.traces.inventoryMovement'),
      value: formatNumber(trace.summary.inventoryMovementQuantity),
      icon: icons.box,
      tone: 'purple'
    },
    {
      label: t('financeReportPages.traces.failedOperations'),
      value: trace.summary.failedOperationCount,
      icon: icons.warning,
      tone: 'red'
    },
    {
      label: t('financeReportPages.traces.openExceptions'),
      value: trace.summary.openExceptionTicketCount,
      icon: icons.warning,
      tone: 'orange'
    }
  ]

  const documentTitle = (doc: BusinessTraceDocument) =>
    doc.bizNo || doc.documentNo || doc.documentId || '-'

  return {
    buildSummaryItems,
    businessTimelineEventLabel,
    businessTimelineItemType,
    businessTimelineTagType,
    documentTagType,
    documentTitle,
    eventIcon,
    eventTagType,
    formatDateTime,
    formatMoney,
    formatNumber,
    priorityLabel,
    priorityTagType,
    ticketStatusLabel,
    ticketStatusTagType,
    timelineItemType,
    traceStatusLabel
  }
}
