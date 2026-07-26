import { computed, reactive, ref } from 'vue'

import type {
  SequenceRule,
  SequenceRuleSaveRequest,
  SystemConfig,
  SystemConfigSaveRequest
} from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface SystemConfigFormState {
  id: string
  configKey: string
  configName: string
  configValue: string
  description: string
}

export interface SequenceRuleFormState {
  id: string
  bizType: string
  prefix: string
  datePattern: string
  seqLength: string
  currentValue: string
}

const emptyConfigForm = (): SystemConfigFormState => ({
  id: '',
  configKey: '',
  configName: '',
  configValue: '',
  description: ''
})

const emptySequenceRuleForm = (): SequenceRuleFormState => ({
  id: '',
  bizType: '',
  prefix: '',
  datePattern: 'yyyyMMdd',
  seqLength: '4',
  currentValue: '0'
})

/**
 * Create/edit dialogs for system configs and sequence rules.
 * Element form validation stays on the page around submit handlers.
 */
export const useSystemConfigForm = (
  t: Translate,
  options: {
    getConfig: (id: string | number) => Promise<SystemConfig>
    createConfig: (data: SystemConfigSaveRequest) => Promise<unknown>
    updateConfig: (id: string | number, data: SystemConfigSaveRequest) => Promise<unknown>
    getSequenceRule: (id: string | number) => Promise<SequenceRule>
    createSequenceRule: (data: SequenceRuleSaveRequest) => Promise<unknown>
    updateSequenceRule: (id: string | number, data: SequenceRuleSaveRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onConfigSubmitted?: () => void | Promise<void>
    onSequenceRuleSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const configMode = ref<'create' | 'edit'>('edit')
  const formData = reactive<SystemConfigFormState>(emptyConfigForm())
  const configDialogTitle = computed(() =>
    configMode.value === 'create'
      ? t('systemConfigs.dialog.addConfig')
      : t('systemConfigs.dialog.editConfig')
  )

  const sequenceRuleDialogVisible = ref(false)
  const sequenceRuleSubmitting = ref(false)
  const sequenceRuleMode = ref<'create' | 'edit'>('create')
  const sequenceRuleForm = reactive<SequenceRuleFormState>(emptySequenceRuleForm())
  const sequenceRuleDialogTitle = computed(() =>
    sequenceRuleMode.value === 'create'
      ? t('systemConfigs.dialog.addSequenceRule')
      : t('systemConfigs.dialog.editSequenceRule')
  )

  const resetConfigForm = () => {
    Object.assign(formData, emptyConfigForm())
  }

  const trimConfigForm = () => {
    formData.configKey = formData.configKey.trim()
    formData.configName = formData.configName.trim()
    formData.configValue = formData.configValue.trim()
    formData.description = formData.description.trim()
  }

  const handleCreate = () => {
    configMode.value = 'create'
    resetConfigForm()
    dialogVisible.value = true
  }

  const handleEdit = async (row: SystemConfig) => {
    try {
      const res = await options.getConfig(row.id)
      configMode.value = 'edit'
      Object.assign(formData, {
        id: res.id,
        configKey: res.configKey,
        configName: res.configName || res.configKey,
        configValue: res.configValue,
        description: res.description || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemConfigs.message.configDetailLoadFailed'))
      return false
    }
  }

  const handleSubmit = async () => {
    trimConfigForm()
    submitLoading.value = true
    try {
      if (configMode.value === 'create') {
        await options.createConfig({
          configKey: formData.configKey,
          configName: formData.configName || formData.configKey,
          configValue: formData.configValue,
          description: formData.description
        })
        options.onSuccess?.(t('systemConfigs.message.createSuccess'))
      } else {
        await options.updateConfig(formData.id, {
          configKey: formData.configKey,
          configName: formData.configName || formData.configKey,
          configValue: formData.configValue,
          description: formData.description
        })
        options.onSuccess?.(t('systemConfigs.message.updateSuccess'))
      }
      dialogVisible.value = false
      await options.onConfigSubmitted?.()
      return true
    } catch {
      options.onError?.(
        configMode.value === 'create'
          ? t('systemConfigs.message.createFailed')
          : t('systemConfigs.message.updateFailed')
      )
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const resetSequenceRuleForm = () => {
    Object.assign(sequenceRuleForm, emptySequenceRuleForm())
  }

  const handleCreateSequenceRule = () => {
    sequenceRuleMode.value = 'create'
    resetSequenceRuleForm()
    sequenceRuleDialogVisible.value = true
  }

  const handleEditSequenceRule = async (row: SequenceRule) => {
    try {
      const res = await options.getSequenceRule(row.id)
      sequenceRuleMode.value = 'edit'
      Object.assign(sequenceRuleForm, {
        id: res.id,
        bizType: res.bizType,
        prefix: res.prefix,
        datePattern: res.datePattern,
        seqLength: String(res.seqLength),
        currentValue: String(res.currentValue)
      })
      sequenceRuleDialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemConfigs.message.sequenceRuleDetailLoadFailed'))
      return false
    }
  }

  const handleSubmitSequenceRule = async () => {
    sequenceRuleSubmitting.value = true
    try {
      const payload: SequenceRuleSaveRequest = {
        bizType: sequenceRuleMode.value === 'create'
          ? sequenceRuleForm.bizType.trim()
          : undefined,
        prefix: sequenceRuleForm.prefix.trim(),
        datePattern: sequenceRuleForm.datePattern.trim(),
        seqLength: Number(sequenceRuleForm.seqLength),
        currentValue: sequenceRuleForm.currentValue.trim()
      }
      if (sequenceRuleMode.value === 'create') {
        await options.createSequenceRule(payload)
      } else {
        await options.updateSequenceRule(sequenceRuleForm.id, payload)
      }
      options.onSuccess?.(t('systemConfigs.message.saveSuccess'))
      sequenceRuleDialogVisible.value = false
      await options.onSequenceRuleSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemConfigs.message.saveFailed'))
      return false
    } finally {
      sequenceRuleSubmitting.value = false
    }
  }

  return {
    configDialogTitle,
    configMode,
    dialogVisible,
    formData,
    handleCreate,
    handleCreateSequenceRule,
    handleEdit,
    handleEditSequenceRule,
    handleSubmit,
    handleSubmitSequenceRule,
    resetConfigForm,
    resetSequenceRuleForm,
    sequenceRuleDialogTitle,
    sequenceRuleDialogVisible,
    sequenceRuleForm,
    sequenceRuleMode,
    sequenceRuleSubmitting,
    submitLoading,
    trimConfigForm
  }
}
