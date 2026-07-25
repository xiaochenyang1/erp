<template>
  <div class="workflow-config-container">
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <el-form :model="configForm" inline>
          <el-form-item :label="$t('workflowConfig.businessType')">
            <el-select
              v-model="activeBusinessType"
              :placeholder="$t('workflowConfig.selectBusinessType')"
              style="width: 180px"
              @change="loadConfig"
            >
              <el-option
                v-for="item in businessTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('workflowConfig.configName')">
            <el-input v-model="configForm.configName" :placeholder="$t('workflowConfig.configNamePlaceholder')" style="width: 240px" />
          </el-form-item>
          <el-form-item :label="$t('workflowConfig.status')">
            <el-select v-model="configForm.status" style="width: 130px">
              <el-option :label="$t('workflowConfig.active')" value="ACTIVE" />
              <el-option :label="$t('workflowConfig.disabled')" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('workflowConfig.approvalTimeout')">
            <el-input v-model.number="configForm.taskTimeoutHours" type="number" min="1" max="720" step="1" style="width: 110px" />
            <span class="timeout-unit">{{ $t('workflowConfig.hours') }}</span>
          </el-form-item>
        </el-form>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="loadConfig">{{ $t('workflowConfig.refresh') }}</el-button>
          <el-button v-permission="'workflow:config:update'" type="primary" :icon="Check" :loading="saving" @click="submitConfig">{{ $t('workflowConfig.saveConfig') }}</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="config-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('workflowConfig.title') }}</span>
          <el-button type="primary" plain :icon="Plus" @click="addNode">{{ $t('workflowConfig.addNode') }}</el-button>
        </div>
      </template>

      <el-form v-loading="loading" label-width="92px">
        <el-form-item :label="$t('workflowConfig.remark')">
          <el-input
            v-model="configForm.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            :placeholder="$t('workflowConfig.remarkPlaceholder')"
          />
        </el-form-item>

        <div class="node-list">
          <div v-for="(node, nodeIndex) in configForm.nodes" :key="node.localKey" class="node-panel">
            <div class="node-header">
              <div class="node-title">
                <span class="node-index">{{ nodeIndex + 1 }}</span>
                <span>{{ node.nodeName || $t('workflowConfig.defaultNode', { order: nodeIndex + 1 }) }}</span>
              </div>
              <el-button
                link
                type="danger"
                :icon="Delete"
                :disabled="configForm.nodes.length === 1"
                @click="removeNode(nodeIndex)"
              >
                {{ $t('workflowConfig.deleteNode') }}
              </el-button>
            </div>

            <el-row :gutter="16">
              <el-col :span="10">
                <el-form-item :label="$t('workflowConfig.nodeName')" required>
                  <el-input v-model="node.nodeName" :placeholder="$t('workflowConfig.nodeNamePlaceholder')" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="$t('workflowConfig.approvalMode')">
                  <el-select v-model="node.approvalMode" style="width: 100%">
                    <el-option :label="$t('workflowConfig.anyApprover')" value="ANY" />
                    <el-option :label="$t('workflowConfig.allApprovers')" value="ALL" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item :label="$t('workflowConfig.nodeOrder')">
                  <el-input :model-value="String(nodeIndex + 1)" disabled />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="approver-header">
              <span>{{ $t('workflowConfig.approvers') }}</span>
              <el-button link type="primary" :icon="Plus" @click="addApprover(nodeIndex)">{{ $t('workflowConfig.addApprover') }}</el-button>
            </div>
            <div class="approver-list">
              <div v-for="(approver, approverIndex) in node.approvers" :key="approver.localKey" class="approver-row">
                <el-select v-model="approver.approverType" style="width: 140px" @change="approver.approverId = ''">
                  <el-option :label="$t('workflowConfig.userApprover')" value="USER" />
                  <el-option :label="$t('workflowConfig.roleApprover')" value="ROLE" />
                </el-select>
                <el-select
                  v-if="approver.approverType === 'USER'"
                  v-model="approver.approverId"
                  :placeholder="$t('workflowConfig.selectUser')"
                  filterable
                  style="width: 260px"
                >
                  <el-option
                    v-for="user in users"
                    :key="user.id"
                    :label="userLabel(user)"
                    :value="user.id"
                  />
                </el-select>
                <el-select
                  v-else
                  v-model="approver.approverId"
                  :placeholder="$t('workflowConfig.selectRole')"
                  filterable
                  style="width: 260px"
                >
                  <el-option
                    v-for="role in roles"
                    :key="role.id"
                    :label="roleLabel(role)"
                    :value="role.id"
                  />
                </el-select>
                <el-button
                  link
                  type="danger"
                  :icon="Delete"
                  :disabled="node.approvers.length === 1"
                  @click="removeApprover(nodeIndex, approverIndex)"
                >
                  {{ $t('workflowConfig.delete') }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getWorkflowApprovalConfig,
  saveWorkflowApprovalConfig,
  type WorkflowApprovalConfig,
  type WorkflowApprovalConfigRequest
} from '@/api/workflow'
import { getRoles, getUsers, type Role, type User } from '@/api/system'

type BusinessTypeOption = {
  label: string
  value: string
}

type ApproverForm = {
  localKey: string
  id?: string
  approverType: 'USER' | 'ROLE'
  approverId: string
}

type NodeForm = {
  localKey: string
  id?: string
  nodeName: string
  approvalMode: 'ANY' | 'ALL'
  approvers: ApproverForm[]
}

const { t } = useI18n()

const businessTypes = computed<BusinessTypeOption[]>(() => [
  { label: t('workflowConfig.businessTypes.purchaseOrder'), value: 'PURCHASE_ORDER' },
  { label: t('workflowConfig.businessTypes.salesOrder'), value: 'SALES_ORDER' },
  { label: t('workflowConfig.businessTypes.expense'), value: 'EXPENSE' }
])

const activeBusinessType = ref('PURCHASE_ORDER')
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
  nodes: [] as NodeForm[]
})

const newLocalKey = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`

const createApprover = (): ApproverForm => ({
  localKey: newLocalKey(),
  approverType: 'USER',
  approverId: ''
})

const createNode = (order: number): NodeForm => ({
  localKey: newLocalKey(),
  nodeName: t('workflowConfig.defaultNode', { order }),
  approvalMode: 'ANY',
  approvers: [createApprover()]
})

const currentBusinessTypeLabel = () => {
  return businessTypes.value.find((item) => item.value === activeBusinessType.value)?.label || activeBusinessType.value
}

const applyConfig = (config: WorkflowApprovalConfig) => {
  configForm.id = config.id
  configForm.configName = config.configName || t('workflowConfig.defaultConfigName', {
    businessType: currentBusinessTypeLabel()
  })
  configForm.status = config.status || 'ACTIVE'
  configForm.taskTimeoutHours = config.taskTimeoutHours || 24
  configForm.remark = config.remark || ''
  configForm.nodes = config.nodes.length
    ? config.nodes.map((node, index) => ({
      localKey: newLocalKey(),
      id: node.id,
      nodeName: node.nodeName || t('workflowConfig.defaultNode', { order: index + 1 }),
      approvalMode: node.approvalMode === 'ALL' ? 'ALL' : 'ANY',
      approvers: node.approvers.length
        ? node.approvers.map((approver) => ({
          localKey: newLocalKey(),
          id: approver.id,
          approverType: approver.approverType === 'ROLE' ? 'ROLE' : 'USER',
          approverId: approver.approverId
        }))
        : [createApprover()]
    }))
    : [createNode(1)]
}

const loadConfig = async () => {
  loading.value = true
  try {
    const config = await getWorkflowApprovalConfig(activeBusinessType.value)
    applyConfig(config)
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
  const [userPage, rolePage] = await Promise.all([
    getUsers(optionPageQuery),
    getRoles(optionPageQuery)
  ])
  users.value = userPage.records
  roles.value = rolePage.records
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
    ElMessage.warning(t('workflowConfig.validation.configName'))
    return false
  }
  if (!configForm.nodes.length) {
    ElMessage.warning(t('workflowConfig.validation.nodeRequired'))
    return false
  }
  if (configForm.taskTimeoutHours < 1 || configForm.taskTimeoutHours > 720) {
    ElMessage.warning(t('workflowConfig.validation.timeoutRange'))
    return false
  }
  for (const [nodeIndex, node] of configForm.nodes.entries()) {
    if (!node.nodeName.trim()) {
      ElMessage.warning(t('workflowConfig.validation.nodeName', { node: nodeIndex + 1 }))
      return false
    }
    if (!node.approvers.length) {
      ElMessage.warning(t('workflowConfig.validation.approverRequired', { node: nodeIndex + 1 }))
      return false
    }
    for (const [approverIndex, approver] of node.approvers.entries()) {
      if (!approver.approverId) {
        ElMessage.warning(t('workflowConfig.validation.selectApprover', {
          node: nodeIndex + 1,
          approver: approverIndex + 1
        }))
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
  if (!validateConfig()) return

  saving.value = true
  try {
    const config = await saveWorkflowApprovalConfig(activeBusinessType.value, toPayload())
    applyConfig(config)
    ElMessage.success(t('workflowConfig.message.saved'))
  } finally {
    saving.value = false
  }
}

const userLabel = (user: User) => t('workflowConfig.userOption', {
  name: user.realName || user.username,
  username: user.username
})
const roleLabel = (role: Role) => t('workflowConfig.roleOption', {
  name: role.name || role.roleName || role.code,
  code: role.code || role.roleCode
})

onMounted(async () => {
  await loadOptions()
  await loadConfig()
})
</script>

<style scoped lang="scss">
.workflow-config-container {
  padding: 20px;

  .toolbar-card,
  .config-card {
    margin-bottom: 20px;
  }

  .toolbar,
  .card-header,
  .node-header,
  .approver-header,
  .approver-row {
    display: flex;
    align-items: center;
  }

  .toolbar {
    justify-content: space-between;
    gap: 16px;

    :deep(.el-form-item) {
      margin-bottom: 0;
    }
  }

  .toolbar-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }

  .timeout-unit {
    margin-left: 6px;
    color: var(--el-text-color-secondary);
  }

  .card-header,
  .node-header,
  .approver-header {
    justify-content: space-between;
  }

  .node-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .node-panel {
    padding: 16px;
    border: 1px solid var(--el-border-color-light);
    border-radius: 6px;
    background: var(--el-fill-color-blank);
  }

  .node-header {
    margin-bottom: 14px;
  }

  .node-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
  }

  .node-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .approver-header {
    margin: 4px 0 10px;
    font-weight: 600;
  }

  .approver-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .approver-row {
    gap: 10px;
  }
}
</style>
