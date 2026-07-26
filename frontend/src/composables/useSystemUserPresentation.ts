import { computed, type Ref } from 'vue'

import type { Dept, Post, Role } from '@/api/system'
import type { Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

export const flattenDepts = (items: Dept[]): Dept[] =>
  items.flatMap((item) => [item, ...flattenDepts(item.children || [])])

/** Display helpers for system user department/post/role/status labels. */
export const useSystemUserPresentation = (
  t: Translate,
  resources: {
    depts: Ref<Dept[]>
    posts: Ref<Post[]>
    warehouses?: Ref<Warehouse[]>
  }
) => {
  const flatDepts = computed(() => flattenDepts(resources.depts.value || []))
  const deptMap = computed(
    () => new Map(flatDepts.value.map((item) => [String(item.id), item.name]))
  )
  const postMap = computed(
    () => new Map(resources.posts.value.map((item) => [String(item.id), item.name]))
  )

  const deptName = (id?: string | number | null) =>
    id == null || id === ''
      ? '-'
      : deptMap.value.get(String(id)) || t('systemUsers.departmentFallback', { id })

  const postName = (id?: string | number | null) =>
    id == null || id === ''
      ? '-'
      : postMap.value.get(String(id)) || t('systemUsers.postFallback', { id })

  const roleNames = (items?: Role[]) =>
    items?.length
      ? items.map((role) => role.name || role.code).join(t('systemUsers.listSeparator'))
      : '-'

  const warehouseOptionLabel = (warehouse: Warehouse) => {
    const name = warehouse.name || warehouse.warehouseName || t('systemUsers.warehouseFallback', { id: warehouse.id })
    const code = warehouse.code || warehouse.warehouseCode
    return code ? t('systemUsers.warehouseOption', { name, code }) : name
  }

  const statusText = (status?: string) => {
    if (!status) return ''
    const map: Record<string, string> = {
      ACTIVE: t('systemUsers.active'),
      INACTIVE: t('systemUsers.inactive'),
      LOCKED: t('systemUsers.locked')
    }
    return map[status] || status
  }

  const statusType = (status?: string): TagType => {
    if (status === 'ACTIVE') return 'success'
    if (status === 'LOCKED') return 'warning'
    return 'info'
  }

  return {
    deptName,
    flatDepts,
    postName,
    roleNames,
    statusText,
    statusType,
    warehouseOptionLabel
  }
}
