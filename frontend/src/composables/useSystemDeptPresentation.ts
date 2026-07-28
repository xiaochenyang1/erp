type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

import type { Dept } from '@/api/system'

/** Display helpers for system department tree labels. */
export const useSystemDeptPresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (status === 'ACTIVE') return t('systemDept.active')
    if (status === 'INACTIVE') return t('systemDept.inactive')
    return status || ''
  }

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'danger'

  /** Build parent picker options with a synthetic root node. */
  const buildParentTree = (data: Dept[]): Dept[] => [
    { id: '0', name: t('systemDept.root'), children: data, orderNum: 0, status: 'ACTIVE' }
  ]

  return {
    buildParentTree,
    statusText,
    statusType
  }
}
