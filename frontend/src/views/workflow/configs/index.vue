<template>
  <div class="workflow-config-container">
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <el-form :model="configForm" inline>
          <el-form-item label="业务类型">
            <el-select
              v-model="activeBusinessType"
              placeholder="请选择业务类型"
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
          <el-form-item label="配置名称">
            <el-input v-model="configForm.configName" placeholder="请输入配置名称" style="width: 240px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="configForm.status" style="width: 130px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="loadConfig">刷新</el-button>
          <el-button v-permission="'workflow:config:update'" type="primary" :icon="Check" :loading="saving" @click="submitConfig">保存配置</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="config-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>审批配置</span>
          <el-button type="primary" plain :icon="Plus" @click="addNode">新增节点</el-button>
        </div>
      </template>

      <el-form v-loading="loading" label-width="92px">
        <el-form-item label="备注">
          <el-input
            v-model="configForm.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="请输入备注"
          />
        </el-form-item>

        <div class="node-list">
          <div v-for="(node, nodeIndex) in configForm.nodes" :key="node.localKey" class="node-panel">
            <div class="node-header">
              <div class="node-title">
                <span class="node-index">{{ nodeIndex + 1 }}</span>
                <span>{{ node.nodeName || `审批节点 ${nodeIndex + 1}` }}</span>
              </div>
              <el-button
                link
                type="danger"
                :icon="Delete"
                :disabled="configForm.nodes.length === 1"
                @click="removeNode(nodeIndex)"
              >
                删除节点
              </el-button>
            </div>

            <el-row :gutter="16">
              <el-col :span="10">
                <el-form-item label="节点名称" required>
                  <el-input v-model="node.nodeName" placeholder="请输入节点名称" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="审批方式">
                  <el-select v-model="node.approvalMode" style="width: 100%">
                    <el-option label="任一审批人通过" value="ANY" />
                    <el-option label="全部审批人通过" value="ALL" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="节点顺序">
                  <el-input :model-value="String(nodeIndex + 1)" disabled />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="approver-header">
              <span>审批人</span>
              <el-button link type="primary" :icon="Plus" @click="addApprover(nodeIndex)">新增审批人</el-button>
            </div>
            <div class="approver-list">
              <div v-for="(approver, approverIndex) in node.approvers" :key="approver.localKey" class="approver-row">
                <el-select v-model="approver.approverType" style="width: 140px" @change="approver.approverId = ''">
                  <el-option label="指定用户" value="USER" />
                  <el-option label="指定角色" value="ROLE" />
                </el-select>
                <el-select
                  v-if="approver.approverType === 'USER'"
                  v-model="approver.approverId"
                  placeholder="请选择用户"
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
                  placeholder="请选择角色"
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
                  删除
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
import { onMounted, reactive, ref } from 'vue'
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

const businessTypes: BusinessTypeOption[] = [
  { label: '采购订单', value: 'PURCHASE_ORDER' },
  { label: '销售订单', value: 'SALES_ORDER' },
  { label: '费用单', value: 'EXPENSE' }
]

const activeBusinessType = ref('PURCHASE_ORDER')
const loading = ref(false)
const saving = ref(false)
const users = ref<User[]>([])
const roles = ref<Role[]>([])

const configForm = reactive({
  id: undefined as string | undefined,
  configName: '',
  status: 'ACTIVE',
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
  nodeName: `审批节点 ${order}`,
  approvalMode: 'ANY',
  approvers: [createApprover()]
})

const currentBusinessTypeLabel = () => {
  return businessTypes.find((item) => item.value === activeBusinessType.value)?.label || activeBusinessType.value
}

const applyConfig = (config: WorkflowApprovalConfig) => {
  configForm.id = config.id
  configForm.configName = config.configName || `${currentBusinessTypeLabel()}审批配置`
  configForm.status = config.status || 'ACTIVE'
  configForm.remark = config.remark || ''
  configForm.nodes = config.nodes.length
    ? config.nodes.map((node, index) => ({
      localKey: newLocalKey(),
      id: node.id,
      nodeName: node.nodeName || `审批节点 ${index + 1}`,
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
    ElMessage.warning('请输入配置名称')
    return false
  }
  if (!configForm.nodes.length) {
    ElMessage.warning('至少保留一个审批节点')
    return false
  }
  for (const [nodeIndex, node] of configForm.nodes.entries()) {
    if (!node.nodeName.trim()) {
      ElMessage.warning(`请输入第 ${nodeIndex + 1} 个节点名称`)
      return false
    }
    if (!node.approvers.length) {
      ElMessage.warning(`第 ${nodeIndex + 1} 个节点至少需要一个审批人`)
      return false
    }
    for (const [approverIndex, approver] of node.approvers.entries()) {
      if (!approver.approverId) {
        ElMessage.warning(`请选择第 ${nodeIndex + 1} 个节点的第 ${approverIndex + 1} 个审批人`)
        return false
      }
    }
  }
  return true
}

const toPayload = (): WorkflowApprovalConfigRequest => ({
  configName: configForm.configName.trim(),
  status: configForm.status,
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
    ElMessage.success('审批配置已保存')
  } finally {
    saving.value = false
  }
}

const userLabel = (user: User) => `${user.realName || user.username}（${user.username}）`
const roleLabel = (role: Role) => `${role.name || role.roleName || role.code}（${role.code || role.roleCode}）`

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
