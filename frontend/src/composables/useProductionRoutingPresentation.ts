import { computed, type Ref } from 'vue'

import type { BOM, RoutingOperation, WorkCenter } from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const STATUS_KEYS: Record<string, string> = {
  ACTIVE: 'productionRouting.status.active',
  DISABLED: 'productionRouting.status.disabled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  ACTIVE: 'success',
  DISABLED: 'danger'
}

/** Display helpers for routing BOM / work-center labels and status tags. */
export const useProductionRoutingPresentation = (
  t: Translate,
  resources: {
    workCenters: Ref<WorkCenter[]>
  }
) => {
  const workCenterMap = computed(
    () => new Map(resources.workCenters.value.map((wc) => [String(wc.id), wc]))
  )

  const bomLabel = (bom: BOM) => {
    const code = bom.bomCode || bom.bomNo || ''
    const name = bom.productName || ''
    return code && name ? `${code} - ${name}` : code || name || `BOM${bom.id}`
  }

  /** Prefer denormalized operation fields, then live work-center options. */
  const workCenterLabel = (op: RoutingOperation) => {
    if (op.workCenterCode || op.workCenterName) {
      return `${op.workCenterCode || ''} ${op.workCenterName || ''}`.trim()
    }
    const wc = workCenterMap.value.get(String(op.workCenterId))
    return wc
      ? `${wc.workCenterCode} - ${wc.workCenterName}`
      : op.workCenterId || '-'
  }

  const getStatusLabel = (status?: string) => {
    if (!status) return ''
    const key = STATUS_KEYS[status]
    return key ? t(key) : status
  }

  const getStatusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  return {
    bomLabel,
    getStatusLabel,
    getStatusType,
    workCenterLabel
  }
}
