<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="系统配置" name="configs">
        <el-card shadow="never" class="search-card">
          <el-form :model="queryForm" inline>
            <el-form-item label="配置键">
              <el-input v-model="queryForm.configKey" placeholder="请输入配置键" clearable style="width: 250px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>系统配置管理</span>
              <el-button v-permission="'system:config:create'" type="primary" :icon="Plus" @click="handleCreate">新增配置</el-button>
            </div>
          </template>

            <el-table v-loading="loading" :data="tableData" border stripe>
              <el-table-column prop="configKey" label="配置键" width="250" show-overflow-tooltip />
              <el-table-column prop="configValue" label="配置值" min-width="200" show-overflow-tooltip />
              <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                    {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="updatedAt" label="更新时间" width="160" />
              <el-table-column label="操作" width="190" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button v-permission="'system:config:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">
                    编辑
                  </el-button>
                  <el-button
                    v-if="row.status === 'ACTIVE'"
                    v-permission="'system:config:disable'"
                    type="warning"
                    link
                    @click="handleToggleConfigStatus(row)"
                  >
                    停用
                  </el-button>
                  <el-button
                    v-else
                    v-permission="'system:config:enable'"
                    type="success"
                    link
                    @click="handleToggleConfigStatus(row)"
                  >
                    启用
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

      <el-tab-pane label="编号规则" name="sequenceRules">
        <el-card shadow="never" class="search-card">
          <el-form :model="sequenceRuleQuery" inline>
            <el-form-item label="关键字">
              <el-input
                v-model="sequenceRuleQuery.keyword"
                placeholder="业务类型或前缀"
                clearable
                style="width: 220px"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="sequenceRuleQuery.status" placeholder="全部状态" clearable style="width: 140px">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSequenceRuleQuery">查询</el-button>
              <el-button :icon="Refresh" @click="handleSequenceRuleReset">重置</el-button>
              <el-button v-permission="'system:sequence-rule:create'" type="primary" :icon="Plus" @click="handleCreateSequenceRule">新增</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <span>编号规则</span>
          </template>

          <el-table v-loading="sequenceRuleLoading" :data="sequenceRuleData" border stripe>
            <el-table-column prop="bizType" label="业务类型" min-width="180" show-overflow-tooltip />
            <el-table-column prop="prefix" label="前缀" width="140" show-overflow-tooltip />
            <el-table-column prop="datePattern" label="日期格式" width="160" />
            <el-table-column prop="seqLength" label="流水长度" width="100" align="right" />
            <el-table-column prop="currentValue" label="当前流水" width="140" align="right" />
            <el-table-column prop="companyId" label="公司ID" width="160" show-overflow-tooltip />
            <el-table-column prop="accountBookId" label="账套ID" width="160" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:sequence-rule:update'" type="primary" link :icon="Edit" @click="handleEditSequenceRule(row)">
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  v-permission="'system:sequence-rule:disable'"
                  type="warning"
                  link
                  @click="handleToggleSequenceRuleStatus(row)"
                >
                  停用
                </el-button>
                <el-button
                  v-else
                  v-permission="'system:sequence-rule:enable'"
                  type="success"
                  link
                  @click="handleToggleSequenceRuleStatus(row)"
                >
                  启用
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
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="formData.configKey" :disabled="configMode === 'edit'" placeholder="请输入配置键" />
        </el-form-item>

        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="请输入配置名称" />
        </el-form-item>

        <el-form-item label="配置值" prop="configValue">
          <el-input
            v-model="formData.configValue"
            type="textarea"
            :rows="5"
            placeholder="请输入配置值"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入配置描述"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          保存
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
        <el-form-item label="业务类型" prop="bizType">
          <el-input
            v-model="sequenceRuleForm.bizType"
            :disabled="sequenceRuleMode === 'edit'"
            placeholder="如 SALES_ORDER"
          />
        </el-form-item>
        <el-form-item label="前缀" prop="prefix">
          <el-input v-model="sequenceRuleForm.prefix" placeholder="如 SO" />
        </el-form-item>
        <el-form-item label="日期格式" prop="datePattern">
          <el-input v-model="sequenceRuleForm.datePattern" placeholder="如 yyyyMMdd" />
        </el-form-item>
        <el-form-item label="流水长度" prop="seqLength">
          <el-input v-model="sequenceRuleForm.seqLength" placeholder="请输入正整数" />
        </el-form-item>
        <el-form-item label="当前流水" prop="currentValue">
          <el-input v-model="sequenceRuleForm.currentValue" placeholder="请输入非负整数" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="sequenceRuleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sequenceRuleSubmitting" @click="handleSubmitSequenceRule">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
const configDialogTitle = computed(() => (configMode.value === 'create' ? '新增配置' : '编辑配置'))
const formData = reactive({
  id: '',
  configKey: '',
  configName: '',
  configValue: '',
  description: ''
})

const formRules: FormRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

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

const sequenceRuleDialogTitle = computed(() => (sequenceRuleMode.value === 'create' ? '新增编号规则' : '编辑编号规则'))

const validatePositiveInteger = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!/^[1-9]\d*$/.test(value || '')) {
    callback(new Error('请输入正整数'))
    return
  }
  callback()
}

const validateNonNegativeInteger = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!/^\d+$/.test(value || '')) {
    callback(new Error('请输入非负整数'))
    return
  }
  callback()
}

const sequenceRuleRules: FormRules = {
  bizType: [{ required: true, message: '请输入业务类型', trigger: 'blur' }],
  prefix: [{ required: true, message: '请输入前缀', trigger: 'blur' }],
  datePattern: [{ required: true, message: '请输入日期格式', trigger: 'blur' }],
  seqLength: [{ validator: validatePositiveInteger, trigger: 'blur' }],
  currentValue: [{ validator: validateNonNegativeInteger, trigger: 'blur' }]
}

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
    ElMessage.error('加载配置详情失败')
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
        ElMessage.success('创建成功')
      } else {
        await updateSystemConfig(formData.id, {
          ...formData,
          configName: formData.configName || formData.configKey,
          configValue: formData.configValue,
          description: formData.description
        })
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(configMode.value === 'create' ? '创建失败' : '更新失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleToggleConfigStatus = async (row: SystemConfig) => {
  const nextAction = row.status === 'ACTIVE' ? '停用' : '启用'
  await ElMessageBox.confirm(`确定${nextAction}系统配置「${row.configKey}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  if (row.status === 'ACTIVE') {
    await disableSystemConfig(row.id)
  } else {
    await enableSystemConfig(row.id)
  }
  ElMessage.success(`${nextAction}成功`)
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
      ElMessage.success('保存成功')
      sequenceRuleDialogVisible.value = false
      loadSequenceRules()
    } finally {
      sequenceRuleSubmitting.value = false
    }
  })
}

const handleToggleSequenceRuleStatus = async (row: SequenceRule) => {
  const nextAction = row.status === 'ACTIVE' ? '停用' : '启用'
  await ElMessageBox.confirm(`确定${nextAction}编号规则「${row.bizType}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  if (row.status === 'ACTIVE') {
    await disableSequenceRule(row.id)
  } else {
    await enableSequenceRule(row.id)
  }
  ElMessage.success(`${nextAction}成功`)
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
