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
            @size-change="handleQuery"
            @current-change="loadData"
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
            @size-change="handleSequenceRuleQuery"
            @current-change="loadSequenceRules"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createSequenceRule,
  createSystemConfig,
  disableSystemConfig,
  disableSequenceRule,
  enableSystemConfig,
  enableSequenceRule,
  getSequenceRule,
  getSequenceRules,
  getSystemConfig,
  getSystemConfigs,
  updateSequenceRule,
  updateSystemConfig,
  type SequenceRule,
  type SequenceRuleQuery,
  type SystemConfig
} from '@/api/system'

const { t } = useI18n()

const activeTab = ref('configs')

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

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const configMode = ref<'create' | 'edit'>('edit')
const configDialogTitle = computed(() =>
  configMode.value === 'create' ? t('systemConfigs.dialog.addConfig') : t('systemConfigs.dialog.editConfig')
)
const formData = reactive({
  id: '',
  configKey: '',
  configName: '',
  configValue: '',
  description: ''
})

const formRules = computed<FormRules>(() => ({
  configKey: [{ required: true, message: t('systemConfigs.validation.configKey'), trigger: 'blur' }],
  configName: [{ required: true, message: t('systemConfigs.validation.configName'), trigger: 'blur' }],
  configValue: [{ required: true, message: t('systemConfigs.validation.configValue'), trigger: 'blur' }]
}))

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
const sequenceRuleDialogVisible = ref(false)
const sequenceRuleSubmitting = ref(false)
const sequenceRuleFormRef = ref<FormInstance>()
const sequenceRuleMode = ref<'create' | 'edit'>('create')
const sequenceRuleForm = reactive({
  id: '',
  bizType: '',
  prefix: '',
  datePattern: 'yyyyMMdd',
  seqLength: '4',
  currentValue: '0'
})

const sequenceRuleDialogTitle = computed(() =>
  sequenceRuleMode.value === 'create'
    ? t('systemConfigs.dialog.addSequenceRule')
    : t('systemConfigs.dialog.editSequenceRule')
)

const validatePositiveInteger = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!/^[1-9]\d*$/.test(value || '')) {
    callback(new Error(t('systemConfigs.validation.positiveInteger')))
    return
  }
  callback()
}

const validateNonNegativeInteger = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!/^\d+$/.test(value || '')) {
    callback(new Error(t('systemConfigs.validation.nonNegativeInteger')))
    return
  }
  callback()
}

const sequenceRuleRules = computed<FormRules>(() => ({
  bizType: [{ required: true, message: t('systemConfigs.validation.businessType'), trigger: 'blur' }],
  prefix: [{ required: true, message: t('systemConfigs.validation.prefix'), trigger: 'blur' }],
  datePattern: [{ required: true, message: t('systemConfigs.validation.datePattern'), trigger: 'blur' }],
  seqLength: [{ validator: validatePositiveInteger, trigger: 'blur' }],
  currentValue: [{ validator: validateNonNegativeInteger, trigger: 'blur' }]
}))

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getSystemConfigs(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.configKey = ''
  pagination.page = 1
  loadData()
}

const handleCreate = () => {
  configMode.value = 'create'
  Object.assign(formData, { id: '', configKey: '', configName: '', configValue: '', description: '' })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

const trimConfigForm = () => {
  formData.configKey = formData.configKey.trim()
  formData.configName = formData.configName.trim()
  formData.configValue = formData.configValue.trim()
  formData.description = formData.description.trim()
}

const handleEdit = async (row: SystemConfig) => {
  try {
    const res = await getSystemConfig(row.id)
    configMode.value = 'edit'
    Object.assign(formData, {
      id: res.id,
      configKey: res.configKey,
      configName: res.configName || res.configKey,
      configValue: res.configValue,
      description: res.description
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('systemConfigs.message.configDetailLoadFailed'))
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  trimConfigForm()
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (configMode.value === 'create') {
        await createSystemConfig({
          configKey: formData.configKey,
          configName: formData.configName || formData.configKey,
          configValue: formData.configValue,
          description: formData.description
        })
        ElMessage.success(t('systemConfigs.message.createSuccess'))
      } else {
        await updateSystemConfig(formData.id, {
          ...formData,
          configName: formData.configName || formData.configKey,
          configValue: formData.configValue,
          description: formData.description
        })
        ElMessage.success(t('systemConfigs.message.updateSuccess'))
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(
        configMode.value === 'create'
          ? t('systemConfigs.message.createFailed')
          : t('systemConfigs.message.updateFailed')
      )
    } finally {
      submitLoading.value = false
    }
  })
}

const handleToggleConfigStatus = async (row: SystemConfig) => {
  const nextAction = row.status === 'ACTIVE' ? t('systemConfigs.disable') : t('systemConfigs.enable')
  await ElMessageBox.confirm(
    t('systemConfigs.message.toggleConfigConfirm', { action: nextAction, key: row.configKey }),
    t('systemConfigs.prompt'),
    {
      confirmButtonText: t('systemConfigs.confirm'),
      cancelButtonText: t('systemConfigs.cancel'),
      type: 'warning'
    }
  )

  if (row.status === 'ACTIVE') {
    await disableSystemConfig(row.id)
  } else {
    await enableSystemConfig(row.id)
  }
  ElMessage.success(t('systemConfigs.message.operationSuccess', { action: nextAction }))
  loadData()
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: '',
    configKey: '',
    configName: '',
    configValue: '',
    description: ''
  })
}

const loadSequenceRules = async () => {
  sequenceRuleLoading.value = true
  try {
    const res = await getSequenceRules({
      keyword: sequenceRuleQuery.keyword?.trim() || undefined,
      status: sequenceRuleQuery.status || undefined,
      pageNo: sequenceRulePagination.page,
      pageSize: sequenceRulePagination.size
    })
    sequenceRuleData.value = res.records || []
    sequenceRulePagination.total = res.total || 0
  } finally {
    sequenceRuleLoading.value = false
  }
}

const handleSequenceRuleQuery = () => {
  sequenceRulePagination.page = 1
  loadSequenceRules()
}

const handleSequenceRuleReset = () => {
  sequenceRuleQuery.keyword = ''
  sequenceRuleQuery.status = ''
  sequenceRulePagination.page = 1
  loadSequenceRules()
}

const handleCreateSequenceRule = () => {
  sequenceRuleMode.value = 'create'
  resetSequenceRuleForm()
  sequenceRuleDialogVisible.value = true
}

const handleEditSequenceRule = async (row: SequenceRule) => {
  const res = await getSequenceRule(row.id)
  sequenceRuleMode.value = 'edit'
  Object.assign(sequenceRuleForm, {
    id: res.id,
    bizType: res.bizType,
    prefix: res.prefix,
    datePattern: res.datePattern,
    seqLength: String(res.seqLength),
    currentValue: res.currentValue
  })
  sequenceRuleDialogVisible.value = true
}

const handleSubmitSequenceRule = async () => {
  if (!sequenceRuleFormRef.value) return
  await sequenceRuleFormRef.value.validate(async (valid) => {
    if (!valid) return
    sequenceRuleSubmitting.value = true
    try {
      const payload = {
        bizType: sequenceRuleMode.value === 'create' ? sequenceRuleForm.bizType.trim() : undefined,
        prefix: sequenceRuleForm.prefix.trim(),
        datePattern: sequenceRuleForm.datePattern.trim(),
        seqLength: Number(sequenceRuleForm.seqLength),
        currentValue: sequenceRuleForm.currentValue.trim()
      }
      if (sequenceRuleMode.value === 'create') {
        await createSequenceRule(payload)
      } else {
        await updateSequenceRule(sequenceRuleForm.id, payload)
      }
      ElMessage.success(t('systemConfigs.message.saveSuccess'))
      sequenceRuleDialogVisible.value = false
      loadSequenceRules()
    } finally {
      sequenceRuleSubmitting.value = false
    }
  })
}

const handleToggleSequenceRuleStatus = async (row: SequenceRule) => {
  const nextAction = row.status === 'ACTIVE' ? t('systemConfigs.disable') : t('systemConfigs.enable')
  await ElMessageBox.confirm(
    t('systemConfigs.message.toggleSequenceRuleConfirm', { action: nextAction, bizType: row.bizType }),
    t('systemConfigs.prompt'),
    {
      confirmButtonText: t('systemConfigs.confirm'),
      cancelButtonText: t('systemConfigs.cancel'),
      type: 'warning'
    }
  )

  if (row.status === 'ACTIVE') {
    await disableSequenceRule(row.id)
  } else {
    await enableSequenceRule(row.id)
  }
  ElMessage.success(t('systemConfigs.message.operationSuccess', { action: nextAction }))
  loadSequenceRules()
}

const handleSequenceRuleDialogClose = () => {
  resetSequenceRuleForm()
}

const resetSequenceRuleForm = () => {
  sequenceRuleFormRef.value?.resetFields()
  Object.assign(sequenceRuleForm, {
    id: '',
    bizType: '',
    prefix: '',
    datePattern: 'yyyyMMdd',
    seqLength: '4',
    currentValue: '0'
  })
}

const handleTabChange = () => {
  if (activeTab.value === 'sequenceRules' && sequenceRuleData.value.length === 0) {
    loadSequenceRules()
  }
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
