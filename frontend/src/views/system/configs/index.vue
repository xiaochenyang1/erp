<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="$t('systemConfigs.tabs.configs')" name="configs">
        <el-card shadow="never" class="search-card">
          <el-form :model="queryForm" inline>
            <el-form-item :label="$t('systemConfigs.configKey')">
              <el-input v-model="queryForm.configKey" :placeholder="$t('systemConfigs.configKeyPlaceholder')" clearable style="width: 250px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemConfigs.search') }}</el-button>
              <el-button :icon="Refresh" @click="handleReset">{{ $t('systemConfigs.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('systemConfigs.managementTitle') }}</span>
              <el-button v-permission="'system:config:create'" type="primary" :icon="Plus" @click="handleCreate">{{ $t('systemConfigs.addConfig') }}</el-button>
            </div>
          </template>

            <el-table v-loading="loading" :data="tableData" border stripe>
              <el-table-column prop="configKey" :label="$t('systemConfigs.configKey')" width="250" show-overflow-tooltip />
              <el-table-column prop="configValue" :label="$t('systemConfigs.configValue')" min-width="200" show-overflow-tooltip />
              <el-table-column prop="description" :label="$t('systemConfigs.description')" min-width="200" show-overflow-tooltip />
              <el-table-column prop="status" :label="$t('systemConfigs.status')" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                    {{ row.status === 'ACTIVE' ? $t('systemConfigs.active') : $t('systemConfigs.disabled') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="updatedAt" :label="$t('systemConfigs.updatedAt')" width="160" />
              <el-table-column :label="$t('systemConfigs.operations')" width="190" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button v-permission="'system:config:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">
                    {{ $t('systemConfigs.edit') }}
                  </el-button>
                  <el-button
                    v-if="row.status === 'ACTIVE'"
                    v-permission="'system:config:disable'"
                    type="warning"
                    link
                    @click="handleToggleConfigStatus(row)"
                  >
                    {{ $t('systemConfigs.disable') }}
                  </el-button>
                  <el-button
                    v-else
                    v-permission="'system:config:enable'"
                    type="success"
                    link
                    @click="handleToggleConfigStatus(row)"
                  >
                    {{ $t('systemConfigs.enable') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            class="pager"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$t('systemConfigs.tabs.sequenceRules')" name="sequenceRules">
        <el-card shadow="never" class="search-card">
          <el-form :model="sequenceRuleQuery" inline>
            <el-form-item :label="$t('systemConfigs.keyword')">
              <el-input
                v-model="sequenceRuleQuery.keyword"
                :placeholder="$t('systemConfigs.keywordPlaceholder')"
                clearable
                style="width: 220px"
              />
            </el-form-item>
            <el-form-item :label="$t('systemConfigs.status')">
              <el-select v-model="sequenceRuleQuery.status" :placeholder="$t('systemConfigs.allStatuses')" clearable style="width: 140px">
                <el-option :label="$t('systemConfigs.active')" value="ACTIVE" />
                <el-option :label="$t('systemConfigs.disabled')" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSequenceRuleQuery">{{ $t('systemConfigs.search') }}</el-button>
              <el-button :icon="Refresh" @click="handleSequenceRuleReset">{{ $t('systemConfigs.reset') }}</el-button>
              <el-button v-permission="'system:sequence-rule:create'" type="primary" :icon="Plus" @click="handleCreateSequenceRule">{{ $t('systemConfigs.add') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <span>{{ $t('systemConfigs.sequenceRules') }}</span>
          </template>

          <el-table v-loading="sequenceRuleLoading" :data="sequenceRuleData" border stripe>
            <el-table-column prop="bizType" :label="$t('systemConfigs.businessType')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="prefix" :label="$t('systemConfigs.prefix')" width="140" show-overflow-tooltip />
            <el-table-column prop="datePattern" :label="$t('systemConfigs.datePattern')" width="160" />
            <el-table-column prop="seqLength" :label="$t('systemConfigs.sequenceLength')" width="100" align="right" />
            <el-table-column prop="currentValue" :label="$t('systemConfigs.currentSequence')" width="140" align="right" />
            <el-table-column prop="companyId" :label="$t('systemConfigs.companyId')" width="160" show-overflow-tooltip />
            <el-table-column prop="accountBookId" :label="$t('systemConfigs.accountBookId')" width="160" show-overflow-tooltip />
            <el-table-column prop="status" :label="$t('systemConfigs.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? $t('systemConfigs.active') : $t('systemConfigs.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('systemConfigs.operations')" width="190" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:sequence-rule:update'" type="primary" link :icon="Edit" @click="handleEditSequenceRule(row)">
                  {{ $t('systemConfigs.edit') }}
                </el-button>
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  v-permission="'system:sequence-rule:disable'"
                  type="warning"
                  link
                  @click="handleToggleSequenceRuleStatus(row)"
                >
                  {{ $t('systemConfigs.disable') }}
                </el-button>
                <el-button
                  v-else
                  v-permission="'system:sequence-rule:enable'"
                  type="success"
                  link
                  @click="handleToggleSequenceRuleStatus(row)"
                >
                  {{ $t('systemConfigs.enable') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="sequenceRulePagination.page"
            v-model:page-size="sequenceRulePagination.size"
            :total="sequenceRulePagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            class="pager"
            @size-change="handleSequenceRuleSizeChange"
            @current-change="handleSequenceRulePageChange"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="configDialogTitle"
      width="700px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item :label="$t('systemConfigs.configKey')" prop="configKey">
          <el-input v-model="formData.configKey" :disabled="configMode === 'edit'" :placeholder="$t('systemConfigs.configKeyPlaceholder')" />
        </el-form-item>

        <el-form-item :label="$t('systemConfigs.configName')" prop="configName">
          <el-input v-model="formData.configName" :placeholder="$t('systemConfigs.configNamePlaceholder')" />
        </el-form-item>

        <el-form-item :label="$t('systemConfigs.configValue')" prop="configValue">
          <el-input
            v-model="formData.configValue"
            type="textarea"
            :rows="5"
            :placeholder="$t('systemConfigs.configValuePlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="$t('systemConfigs.description')" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            :placeholder="$t('systemConfigs.descriptionPlaceholder')"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('systemConfigs.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('systemConfigs.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sequenceRuleDialogVisible"
      :title="sequenceRuleDialogTitle"
      width="720px"
      @close="handleSequenceRuleDialogClose"
    >
      <el-form
        ref="sequenceRuleFormRef"
        :model="sequenceRuleForm"
        :rules="sequenceRuleRules"
        label-width="110px"
      >
        <el-form-item :label="$t('systemConfigs.businessType')" prop="bizType">
          <el-input
            v-model="sequenceRuleForm.bizType"
            :disabled="sequenceRuleMode === 'edit'"
            :placeholder="$t('systemConfigs.businessTypePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('systemConfigs.prefix')" prop="prefix">
          <el-input v-model="sequenceRuleForm.prefix" :placeholder="$t('systemConfigs.prefixPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemConfigs.datePattern')" prop="datePattern">
          <el-input v-model="sequenceRuleForm.datePattern" :placeholder="$t('systemConfigs.datePatternPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemConfigs.sequenceLength')" prop="seqLength">
          <el-input v-model="sequenceRuleForm.seqLength" :placeholder="$t('systemConfigs.positiveIntegerPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemConfigs.currentSequence')" prop="currentValue">
          <el-input v-model="sequenceRuleForm.currentValue" :placeholder="$t('systemConfigs.nonNegativeIntegerPlaceholder')" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="sequenceRuleDialogVisible = false">{{ $t('systemConfigs.cancel') }}</el-button>
        <el-button type="primary" :loading="sequenceRuleSubmitting" @click="handleSubmitSequenceRule">
          {{ $t('systemConfigs.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createSequenceRule,
  createSystemConfig,
  disableSequenceRule,
  disableSystemConfig,
  enableSequenceRule,
  enableSystemConfig,
  getSequenceRule,
  getSequenceRules,
  getSystemConfig,
  getSystemConfigs,
  updateSequenceRule,
  updateSystemConfig
} from '@/api/system'
import { useSystemConfigPresentation } from '@/composables/useSystemConfigPresentation'
import { useSystemConfigList } from '@/composables/useSystemConfigList'
import { useSystemConfigForm } from '@/composables/useSystemConfigForm'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const sequenceRuleFormRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
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
} = useSystemConfigList(t, {
  getConfigs: getSystemConfigs,
  enableConfig: enableSystemConfig,
  disableConfig: disableSystemConfig,
  getSequenceRules,
  enableSequenceRule,
  disableSequenceRule,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  validateNonNegativeInteger,
  validatePositiveInteger
} = useSystemConfigPresentation(t)

const {
  configDialogTitle,
  configMode,
  dialogVisible,
  formData,
  handleCreate,
  handleCreateSequenceRule,
  handleEdit,
  handleEditSequenceRule,
  handleSubmit: saveConfig,
  handleSubmitSequenceRule: saveSequenceRule,
  resetConfigForm,
  resetSequenceRuleForm,
  sequenceRuleDialogTitle,
  sequenceRuleDialogVisible,
  sequenceRuleForm,
  sequenceRuleMode,
  sequenceRuleSubmitting,
  submitLoading
} = useSystemConfigForm(t, {
  getConfig: getSystemConfig,
  createConfig: createSystemConfig,
  updateConfig: updateSystemConfig,
  getSequenceRule,
  createSequenceRule,
  updateSequenceRule,
  onConfigSubmitted: loadData,
  onSequenceRuleSubmitted: loadSequenceRules,
  ...notify
})

const formRules = computed<FormRules>(() => ({
  configKey: [{ required: true, message: t('systemConfigs.validation.configKey'), trigger: 'blur' }],
  configName: [{ required: true, message: t('systemConfigs.validation.configName'), trigger: 'blur' }],
  configValue: [{ required: true, message: t('systemConfigs.validation.configValue'), trigger: 'blur' }]
}))

const sequenceRuleRules = computed<FormRules>(() => ({
  bizType: [{ required: true, message: t('systemConfigs.validation.businessType'), trigger: 'blur' }],
  prefix: [{ required: true, message: t('systemConfigs.validation.prefix'), trigger: 'blur' }],
  datePattern: [{ required: true, message: t('systemConfigs.validation.datePattern'), trigger: 'blur' }],
  seqLength: [{ validator: validatePositiveInteger, trigger: 'blur' }],
  currentValue: [{ validator: validateNonNegativeInteger, trigger: 'blur' }]
}))

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await saveConfig()
  })
}

const handleSubmitSequenceRule = async () => {
  if (!sequenceRuleFormRef.value) return
  await sequenceRuleFormRef.value.validate(async (valid) => {
    if (!valid) return
    await saveSequenceRule()
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetConfigForm()
}

const handleSequenceRuleDialogClose = () => {
  sequenceRuleFormRef.value?.resetFields()
  resetSequenceRuleForm()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pager {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
