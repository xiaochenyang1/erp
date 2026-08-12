import { type ComputedRef, type Ref } from 'vue'

import type { Dept, User } from '@/api/system'
import type { Warehouse } from '@/api/masterdata'
import {
  formatLocalizedCurrency,
  formatLocalizedDateTime,
  formatLocalizedNumber,
  type DisplayPreferences
} from '@/utils/locale'

export type WarehousePageTexts = {
  [key: string]: string
}

export const useWarehousePresentation = (
  displayPreferences: ComputedRef<DisplayPreferences> | Ref<DisplayPreferences>
) => {
  const interpolate = (template: string, params: Record<string, string | number>) =>
    template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))

  const formatCurrency = (value?: number | string | null) => {
    const amount = Number(value)
    if (!Number.isFinite(amount)) return '-'
    return formatLocalizedCurrency(amount, {}, displayPreferences.value)
  }

  const formatDateTime = (value?: string | null) => (
    value ? formatLocalizedDateTime(value, {}, displayPreferences.value) || '-' : '-'
  )

  const formatNumber = (value?: number | string | null) => (
    formatLocalizedNumber(Number(value || 0), { maximumFractionDigits: 2 }, displayPreferences.value)
  )

  const flattenDepts = (items: Dept[]): Dept[] =>
    items.flatMap((item) => [item, ...flattenDepts(item.children || [])])

  const userLabel = (user: User) => user.realName || user.username

  const deptLabel = (depts: Dept[], id?: string | number) => {
    if (id == null || id === '') return '-'
    const dept = flattenDepts(depts).find((item) => item.id === String(id))
    return dept?.name || String(id)
  }

  const managerLabel = (users: User[], id?: string | number) => {
    if (id == null || id === '') return '-'
    const user = users.find((item) => item.id === String(id))
    return user ? userLabel(user) : String(id)
  }

  const activeCount = (rows: Warehouse[]) =>
    rows.filter((item) => item.status === 'ACTIVE').length

  const warehouseLabel = (row: Warehouse) =>
    row.name || row.warehouseName || row.code || row.warehouseCode || String(row.id)

  return {
    activeCount,
    deptLabel,
    flattenDepts,
    formatCurrency,
    formatDateTime,
    formatNumber,
    interpolate,
    managerLabel,
    userLabel,
    warehouseLabel
  }
}
