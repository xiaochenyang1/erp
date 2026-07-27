import { computed } from 'vue'

import type { Role, User } from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string

export type WorkflowBusinessTypeOption = {
  label: string
  value: string
}

/** Labels and option lists for workflow approval configuration. */
export const useWorkflowConfigPresentation = (t: Translate) => {
  const businessTypes = computed<WorkflowBusinessTypeOption[]>(() => [
    { label: t('workflowConfig.businessTypes.purchaseOrder'), value: 'PURCHASE_ORDER' },
    { label: t('workflowConfig.businessTypes.salesOrder'), value: 'SALES_ORDER' },
    { label: t('workflowConfig.businessTypes.expense'), value: 'EXPENSE' }
  ])

  const businessTypeLabel = (value?: string) =>
    businessTypes.value.find((item) => item.value === value)?.label || value || ''

  const userLabel = (user: Pick<User, 'id' | 'username' | 'realName'>) =>
    t('workflowConfig.userOption', {
      name: user.realName || user.username,
      username: user.username
    })

  const roleLabel = (role: Pick<Role, 'id' | 'name' | 'roleName' | 'code' | 'roleCode'>) =>
    t('workflowConfig.roleOption', {
      name: role.name || role.roleName || role.code,
      code: role.code || role.roleCode
    })

  return {
    businessTypeLabel,
    businessTypes,
    roleLabel,
    userLabel
  }
}
