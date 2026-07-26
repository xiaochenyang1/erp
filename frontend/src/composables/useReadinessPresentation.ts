import type { ReadinessRun } from '@/api/readiness'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const CLOSED_RUN_STATUSES = ['PASSED', 'FAILED', 'BLOCKED', 'NO_GO'] as const

/** Labels and tag types for readiness preflight / runs / items / decisions. */
export const useReadinessPresentation = (t: Translate) => {
  const isRunClosed = (run: Pick<ReadinessRun, 'status'>) =>
    CLOSED_RUN_STATUSES.includes(run.status as (typeof CLOSED_RUN_STATUSES)[number])

  const preflightStatusLabel = (status?: string) => {
    const map: Record<string, string> = {
      PASS: t('systemReadiness.statuses.passed'),
      WARN: t('systemReadiness.statuses.warning'),
      FAIL: t('systemReadiness.statuses.failed')
    }
    return map[status || ''] || status || t('systemReadiness.statuses.unchecked')
  }

  const runStatusLabel = (status?: string) => {
    const map: Record<string, string> = {
      DRAFT: t('systemReadiness.statuses.draft'),
      IN_PROGRESS: t('systemReadiness.statuses.inProgress'),
      PASSED: t('systemReadiness.statuses.passed'),
      FAILED: t('systemReadiness.statuses.failed'),
      BLOCKED: t('systemReadiness.statuses.blocked'),
      NO_GO: t('systemReadiness.statuses.noGo')
    }
    return map[status || ''] || status || ''
  }

  const itemStatusLabel = (status?: string) => {
    const map: Record<string, string> = {
      PENDING: t('systemReadiness.statuses.pending'),
      PASSED: t('systemReadiness.statuses.passed'),
      FAILED: t('systemReadiness.statuses.failed'),
      BLOCKED: t('systemReadiness.statuses.blocked'),
      SKIPPED: t('systemReadiness.statuses.skipped')
    }
    return map[status || ''] || status || ''
  }

  const decisionLabel = (decision?: string) => {
    const map: Record<string, string> = {
      PENDING: t('systemReadiness.decisions.pending'),
      GO: t('systemReadiness.decisions.go'),
      NO_GO: t('systemReadiness.decisions.noGo')
    }
    return map[decision || ''] || decision || ''
  }

  const preflightTagType = (status?: string): TagType => {
    if (status === 'PASS') return 'success'
    if (status === 'FAIL') return 'danger'
    if (status === 'WARN') return 'warning'
    return 'info'
  }

  const runStatusTagType = (status?: string): TagType => {
    if (status === 'PASSED') return 'success'
    if (status === 'FAILED' || status === 'NO_GO') return 'danger'
    if (status === 'BLOCKED') return 'warning'
    if (status === 'IN_PROGRESS') return 'primary'
    return 'info'
  }

  const itemStatusTagType = (status?: string): TagType => {
    if (status === 'PASSED') return 'success'
    if (status === 'FAILED') return 'danger'
    if (status === 'BLOCKED') return 'warning'
    if (status === 'SKIPPED') return 'info'
    return 'primary'
  }

  const decisionTagType = (decision?: string): TagType => {
    if (decision === 'GO') return 'success'
    if (decision === 'NO_GO') return 'danger'
    return 'info'
  }

  const priorityTagType = (priority?: string): TagType => {
    if (priority === 'P0') return 'danger'
    if (priority === 'P1') return 'warning'
    return 'info'
  }

  const sampleText = (sample?: string[]) => {
    if (!sample || sample.length === 0) return '-'
    return sample.join(t('systemReadiness.listSeparator'))
  }

  return {
    decisionLabel,
    decisionTagType,
    isRunClosed,
    itemStatusLabel,
    itemStatusTagType,
    preflightStatusLabel,
    preflightTagType,
    priorityTagType,
    runStatusLabel,
    runStatusTagType,
    sampleText
  }
}
