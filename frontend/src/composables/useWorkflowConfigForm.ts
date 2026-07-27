import { reactive, ref } from 'vue'

import type {
  WorkflowApprovalConfig,
  WorkflowApprovalConfigRequest
} from '@/api/workflow'
import type { Role, User } from '@/api/system'
import type { PageResponse } from '@/types/common'
import { useWorkflowConfigPresentation } from './useWorkflowConfigPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type WorkflowApproverForm = {
  localKey: string
  id?: string
  approverType: 'USER' | 'ROLE'
  approverId: string
}

export type WorkflowNodeForm = {
  localKey: string
  id?: string
  nodeName: string
  approvalMode: 'ANY' | 'ALL'
  approvers: WorkflowApproverForm[]
}

const newLocalKey = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`

/**
 * Approval workflow config form: load/save, nodes and approvers.
 * Business-type options live in presentation; API deps are injected.
 */
export const useWorkflowConfigForm = (
  t: Translate,
  options: {
    getWorkflowApprovalConfig: (businessType: string) => Promise<WorkflowApprovalConfig>
    saveWorkflowApprovalConfig: (
      businessType: string,
      data: WorkflowApprovalConfigRequest
    ) => Promise<WorkflowApprovalConfig>
    getUsers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<User>>
    getRoles: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Role>>
    initialBusinessType?: string
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const { businessTypeLabel } = useWorkflowConfigPresentation(t)

  const activeBusinessType = ref(options.initialBusinessType || 'PURCHASE_ORDER')
  const loading = ref(false)
  const saving = ref(false)
  const users = ref<User[]>([])
  const roles = ref<Role[]>([])

  const configForm = reactive({
    id: undefined as string | undefined,
    configName: '',
    status: 'ACTIVE',
    taskTimeoutHours: 24,
    remark: '',
    nodes: [] as WorkflowNodeForm[]
  })

  const createApprover = (): WorkflowApproverForm => ({
    localKey: newLocalKey(),
    approverType: 'USER',
    approverId: ''
  })

  const createNode = (order: number): WorkflowNodeForm => ({
    localKey: newLocalKey(),
    nodeName: t('workflowConfig.defaultNode', { order }),
    approvalMode: 'ANY',
    approvers: [createApprover()]
  })

  const applyConfig = (config: WorkflowApprovalConfig) => {
    configForm.id = config.id
    configForm.configName = config.configName || t('workflowConfig.defaultConfigName', {
      businessType: businessTypeLabel(activeBusinessType.value)
    })
    configForm.status = config.status || 'ACTIVE'
    configForm.taskTimeoutHours = config.taskTimeoutHours || 24
    configForm.remark = config.remark || ''
    configForm.nodes = config.nodes.length
      ? config.nodes.map((node, index) => ({
        localKey: newLocalKey(),
        id: node.id,
        nodeName: node.nodeName || t('workflowConfig.defaultNode', { order: index + 1 }),
        approvalMode: (node.approvalMode === 'ALL' ? 'ALL' : 'ANY') as 'ANY' | 'ALL',
        approvers: node.approvers.length
          ? node.approvers.map((approver) => ({
            localKey: newLocalKey(),
            id: approver.id,
            approverType: (approver.approverType === 'ROLE' ? 'ROLE' : 'USER') as 'USER' | 'ROLE',
            approverId: String(approver.approverId || '')
          }))
          : [createApprover()]
      }))
      : [createNode(1)]
  }

  const loadConfig = async () => {
    loading.value = true
    try {
      const config = await options.getWorkflowApprovalConfig(activeBusinessType.value)
      applyConfig(config)
      return true
    } catch {
      options.onError?.(t('workflowConfig.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    try {
      const [userPage, rolePage] = await Promise.all([
        options.getUsers(optionPageQuery),
        options.getRoles(optionPageQuery)
      ])
      users.value = userPage.records || []
      roles.value = rolePage.records || []
      return true
    } catch {
      users.value = []
      roles.value = []
      options.onError?.(t('workflowConfig.message.optionsLoadFailed'))
      return false
    }
  }

  const addNode = () => {
    configForm.nodes.push(createNode(configForm.nodes.length + 1))
  }

  const removeNode = (index: number) => {
    if (configForm.nodes.length === 1) return
    configForm.nodes.splice(index, 1)
  }

  const addApprover = (nodeIndex: number) => {
    configForm.nodes[nodeIndex].approvers.push(createApprover())
  }

  const removeApprover = (nodeIndex: number, approverIndex: number) => {
    const approvers = configForm.nodes[nodeIndex].approvers
    if (approvers.length === 1) return
    approvers.splice(approverIndex, 1)
  }

  const validateConfig = () => {
    if (!configForm.configName.trim()) {
      options.onWarning?.(t('workflowConfig.validation.configName'))
      return false
    }
    if (!configForm.nodes.length) {
      options.onWarning?.(t('workflowConfig.validation.nodeRequired'))
      return false
    }
    if (configForm.taskTimeoutHours < 1 || configForm.taskTimeoutHours > 720) {
      options.onWarning?.(t('workflowConfig.validation.timeoutRange'))
      return false
    }
    for (const [nodeIndex, node] of configForm.nodes.entries()) {
      if (!node.nodeName.trim()) {
        options.onWarning?.(t('workflowConfig.validation.nodeName', { index: nodeIndex + 1 }))
        return false
      }
      if (!node.approvers.length) {
        options.onWarning?.(t('workflowConfig.validation.approverRequired', { index: nodeIndex + 1 }))
        return false
      }
      for (const approver of node.approvers) {
        if (!approver.approverId) {
          options.onWarning?.(t('workflowConfig.validation.selectApprover'))
          return false
        }
      }
    }
    return true
  }

  const toPayload = (): WorkflowApprovalConfigRequest => ({
    configName: configForm.configName.trim(),
    status: configForm.status,
    taskTimeoutHours: configForm.taskTimeoutHours,
    remark: configForm.remark.trim() || undefined,
    nodes: configForm.nodes.map((node, index) => ({
      nodeName: node.nodeName.trim(),
      nodeOrder: index + 1,
      approvalMode: node.approvalMode,
      approvers: node.approvers.map((approver) => ({
        approverType: approver.approverType,
        approverId: approver.approverId
      }))
    }))
  })

  const submitConfig = async () => {
    if (!validateConfig()) return false

    saving.value = true
    try {
      const config = await options.saveWorkflowApprovalConfig(
        activeBusinessType.value,
        toPayload()
      )
      applyConfig(config)
      options.onSuccess?.(t('workflowConfig.message.saved'))
      return true
    } catch {
      options.onError?.(t('workflowConfig.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    activeBusinessType,
    addApprover,
    addNode,
    configForm,
    loadConfig,
    loadOptions,
    loading,
    removeApprover,
    removeNode,
    roles,
    saving,
    submitConfig,
    users,
    validateConfig
  }
}
