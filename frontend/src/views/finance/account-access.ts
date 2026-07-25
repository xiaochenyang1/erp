export type FinanceAccountTab = 'receivables' | 'payables'

type PermissionChecker = (permission: string) => boolean

const TAB_PERMISSIONS: Record<FinanceAccountTab, string> = {
  receivables: 'finance:receivable:view',
  payables: 'finance:payable:view'
}

const OPTION_PERMISSIONS: Record<FinanceAccountTab, string> = {
  receivables: 'masterdata:customer:view',
  payables: 'masterdata:supplier:view'
}

export const canViewFinanceAccountTab = (
  tab: FinanceAccountTab,
  hasPermission: PermissionChecker
) => hasPermission(TAB_PERMISSIONS[tab])

export const resolveFinanceAccountTab = (
  path: string,
  hasPermission: PermissionChecker
): FinanceAccountTab | null => {
  const preferred: FinanceAccountTab = path.includes('/finance/payables') ? 'payables' : 'receivables'
  if (canViewFinanceAccountTab(preferred, hasPermission)) return preferred

  const fallback: FinanceAccountTab = preferred === 'receivables' ? 'payables' : 'receivables'
  return canViewFinanceAccountTab(fallback, hasPermission) ? fallback : null
}

export const canLoadFinanceAccountOptions = (
  tab: FinanceAccountTab,
  hasPermission: PermissionChecker
) => canViewFinanceAccountTab(tab, hasPermission) && hasPermission(OPTION_PERMISSIONS[tab])
