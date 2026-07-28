type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

import type { Menu } from '@/api/system'

/** Display helpers for system menu tree labels. */
export const useSystemMenuPresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (status === 'ACTIVE') return t('systemMenu.active')
    if (status === 'INACTIVE') return t('systemMenu.inactive')
    return status || ''
  }

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'danger'

  const typeText = (type?: string) => {
    if (type === 'MENU') return t('systemMenu.menu')
    if (type === 'BUTTON') return t('systemMenu.button')
    return type || ''
  }

  const typeTagType = (type?: string): TagType =>
    type === 'MENU' ? 'primary' : 'info'

  /** Build parent picker options with a synthetic root node. */
  const buildParentTree = (data: Menu[]): Menu[] => [
    {
      id: '0',
      name: t('systemMenu.root'),
      children: data,
      orderNum: 0,
      type: 'MENU',
      status: 'ACTIVE'
    }
  ]

  return {
    buildParentTree,
    statusText,
    statusType,
    typeTagType,
    typeText
  }
}
