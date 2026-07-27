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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getWorkflowApprovalConfig,
  saveWorkflowApprovalConfig
} from '@/api/workflow'
import { getRoles, getUsers } from '@/api/system'
import { useWorkflowConfigForm } from '@/composables/useWorkflowConfigForm'
import { useWorkflowConfigPresentation } from '@/composables/useWorkflowConfigPresentation'

const { t } = useI18n()

const {
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
  users
} = useWorkflowConfigForm(t, {
  getWorkflowApprovalConfig,
  saveWorkflowApprovalConfig,
  getUsers,
  getRoles,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})

const {
  businessTypes,
  roleLabel,
  userLabel
} = useWorkflowConfigPresentation(t)

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
