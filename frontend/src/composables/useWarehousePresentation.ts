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
type DisplayPreferencesLike = { locale: string; timeZone?: string }

export const useWarehousePresentation = (
  displayPreferences: ComputedRef<DisplayPreferencesLike> | Ref<DisplayPreferencesLike>
) => {
  const interpolate = (template: string, params: Record<string, string | number>) =>
    template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))

  const joinNames = (items: string[], locale: string) => (
    locale === 'en-US' ? items.join(', ') : items.join('、')
  )

  const labelWithCount = (label: string, count: number) => (
    count > 0 ? `${label} (${count})` : label
  )

  const formatCurrency = (value?: number | string | null) => {
    const amount = Number(value)
    if (!Number.isFinite(amount)) return '-'
    return formatLocalizedCurrency(amount, {}, displayPreferences.value as DisplayPreferences)
  }

  const formatDateTime = (value?: string | null) => (
    value ? formatLocalizedDateTime(value, {}, displayPreferences.value as DisplayPreferences) || '-' : '-'
  )

  const formatNumber = (value?: number | string | null) => (
    formatLocalizedNumber(Number(value || 0), { maximumFractionDigits: 2 }, displayPreferences.value as DisplayPreferences)
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
    joinNames,
    labelWithCount,
    managerLabel,
    userLabel,
    warehouseLabel
  }
}
