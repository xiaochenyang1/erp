import { reactive, ref } from 'vue'

import type {
  SequenceRule,
  SequenceRuleQuery,
  SystemConfig,
  SystemConfigQuery
} from '@/api/system'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>

export type SystemConfigTab = 'configs' | 'sequenceRules'

/**
 * Dual-tab list for system configs and sequence rules, including enable/disable.
 * Dialog create/edit flows live in useSystemConfigForm.
 */
export const useSystemConfigList = (
  t: Translate,
  options: {
    getConfigs: (params: SystemConfigQuery) => Promise<PageResponse<SystemConfig>>
    enableConfig: (id: string | number) => Promise<unknown>
    disableConfig: (id: string | number) => Promise<unknown>
    getSequenceRules: (params: SequenceRuleQuery) => Promise<PageResponse<SequenceRule>>
    enableSequenceRule: (id: string | number) => Promise<unknown>
    disableSequenceRule: (id: string | number) => Promise<unknown>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const activeTab = ref<SystemConfigTab>('configs')

  const queryForm = reactive({
    configKey: ''
  })
  const loading = ref(false)
  const tableData = ref<SystemConfig[]>([])
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const sequenceRuleQuery = reactive<SequenceRuleQuery>({
    keyword: '',
    status: ''
  })
  const sequenceRuleLoading = ref(false)
  const sequenceRuleData = ref<SequenceRule[]>([])
  const sequenceRulePagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getConfigs({
        configKey: queryForm.configKey || undefined,
        pageNo: pagination.page,
        pageSize: pagination.size
      })
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch {
      options.onError?.(t('systemConfigs.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.configKey = ''
    pagination.page = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.page = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.size = size
    pagination.page = 1
    await loadData()
  }

  const handleToggleConfigStatus = async (row: SystemConfig) => {
    const nextAction = row.status === 'ACTIVE'
      ? t('systemConfigs.disable')
      : t('systemConfigs.enable')
    try {
      await options.confirm(
        t('systemConfigs.message.toggleConfigConfirm', { action: nextAction, key: row.configKey }),
        t('systemConfigs.prompt'),
        {
          confirmButtonText: t('systemConfigs.confirm'),
          cancelButtonText: t('systemConfigs.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      if (row.status === 'ACTIVE') {
        await options.disableConfig(row.id)
      } else {
        await options.enableConfig(row.id)
      }
      options.onSuccess?.(t('systemConfigs.message.operationSuccess', { action: nextAction }))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemConfigs.message.operationFailed', { action: nextAction }))
      return false
    }
  }

  const loadSequenceRules = async () => {
    sequenceRuleLoading.value = true
    try {
      const res = await options.getSequenceRules({
        keyword: sequenceRuleQuery.keyword?.trim() || undefined,
        status: sequenceRuleQuery.status || undefined,
        pageNo: sequenceRulePagination.page,
        pageSize: sequenceRulePagination.size
      })
      sequenceRuleData.value = res.records || []
      sequenceRulePagination.total = res.total || 0
    } catch {
      options.onError?.(t('systemConfigs.message.sequenceRulesLoadFailed'))
    } finally {
      sequenceRuleLoading.value = false
    }
  }

  const handleSequenceRuleQuery = async () => {
    sequenceRulePagination.page = 1
    await loadSequenceRules()
  }

  const handleSequenceRuleReset = async () => {
    sequenceRuleQuery.keyword = ''
    sequenceRuleQuery.status = ''
    sequenceRulePagination.page = 1
    await loadSequenceRules()
  }

  const handleSequenceRulePageChange = async (page: number) => {
    sequenceRulePagination.page = page
    await loadSequenceRules()
  }

  const handleSequenceRuleSizeChange = async (size: number) => {
    sequenceRulePagination.size = size
    sequenceRulePagination.page = 1
    await loadSequenceRules()
  }

  const handleToggleSequenceRuleStatus = async (row: SequenceRule) => {
    const nextAction = row.status === 'ACTIVE'
      ? t('systemConfigs.disable')
      : t('systemConfigs.enable')
    try {
      await options.confirm(
        t('systemConfigs.message.toggleSequenceRuleConfirm', {
          action: nextAction,
          bizType: row.bizType
        }),
        t('systemConfigs.prompt'),
        {
          confirmButtonText: t('systemConfigs.confirm'),
          cancelButtonText: t('systemConfigs.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      if (row.status === 'ACTIVE') {
        await options.disableSequenceRule(row.id)
      } else {
        await options.enableSequenceRule(row.id)
      }
      options.onSuccess?.(t('systemConfigs.message.operationSuccess', { action: nextAction }))
      await loadSequenceRules()
      return true
    } catch {
      options.onError?.(t('systemConfigs.message.operationFailed', { action: nextAction }))
      return false
    }
  }

  const handleTabChange = async (tab?: string) => {
    const next = (tab || activeTab.value) as SystemConfigTab
    activeTab.value = next
    if (next === 'sequenceRules' && sequenceRuleData.value.length === 0) {
      await loadSequenceRules()
    }
  }

  return {
    activeTab,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSequenceRulePageChange,
    handleSequenceRuleQuery,
    handleSequenceRuleReset,
    handleSequenceRuleSizeChange,
    handleSizeChange,
    handleTabChange,
    handleToggleConfigStatus,
    handleToggleSequenceRuleStatus,
    loadData,
    loadSequenceRules,
    loading,
    pagination,
    queryForm,
    sequenceRuleData,
    sequenceRuleLoading,
    sequenceRulePagination,
    sequenceRuleQuery,
    tableData
  }
}
