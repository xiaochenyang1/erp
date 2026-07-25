<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('financeReportPages.subjects.code')">
          <el-input v-model="queryForm.subjectCode" :placeholder="$t('financeReportPages.subjects.codePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.subjects.name')">
          <el-input v-model="queryForm.subjectName" :placeholder="$t('financeReportPages.subjects.namePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.subjects.category')">
          <el-select v-model="queryForm.subjectType" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 150px">
            <el-option :label="$t('financeReportPages.subjects.categoryValue.asset')" value="ASSET" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.liability')" value="LIABILITY" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.equity')" value="EQUITY" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.revenue')" value="REVENUE" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.expense')" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 120px">
            <el-option :label="$t('financeReportPages.subjects.status.active')" value="ACTIVE" />
            <el-option :label="$t('financeReportPages.subjects.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('financeReportPages.subjects.management') }}</span>
          <el-button v-permission="'finance:subject:manage'" type="primary" :icon="Plus" @click="handleAdd">{{ $t('financeReportPages.subjects.create') }}</el-button>
        </div>
      </template>

      <!-- 树形表格 -->
      <el-table
        v-loading="loading"
        :data="subjectTree"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="code" :label="$t('financeReportPages.subjects.code')" width="180" />
        <el-table-column prop="name" :label="$t('financeReportPages.subjects.name')" width="200" />
        <el-table-column prop="category" :label="$t('financeReportPages.subjects.category')" width="120">
          <template #default="{ row }">
            <el-tag :type="getCategoryType(row.category)">
              {{ getCategoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" :label="$t('financeReportPages.subjects.level')" width="80" align="center" />
        <el-table-column prop="isLeaf" :label="$t('financeReportPages.subjects.leaf')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isLeaf ? 'success' : 'info'" size="small">
              {{ row.isLeaf ? $t('financeReportPages.subjects.yes') : $t('financeReportPages.subjects.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? $t('financeReportPages.subjects.status.active') : $t('financeReportPages.subjects.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.common.actions')" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'finance:subject:manage'" type="primary" link :icon="Plus" @click="handleAddChild(row)">
              {{ $t('financeReportPages.subjects.addChild') }}
            </el-button>
            <el-button v-permission="'finance:subject:manage'" type="primary" link :icon="Edit" @click="handleEdit(row)">{{ $t('financeReportPages.common.edit') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'finance:subject:manage'"
              type="warning"
              link
              :icon="CircleClose"
              @click="handleDisable(row)"
            >
              {{ $t('financeReportPages.subjects.status.disabled') }}
            </el-button>
            <el-button
              v-else
              v-permission="'finance:subject:manage'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handleEnable(row)"
            >
              {{ $t('financeReportPages.subjects.status.active') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item :label="$t('financeReportPages.subjects.parent')" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="subjectTreeOptions"
            :props="{ label: 'name', value: 'id' }"
            :placeholder="$t('financeReportPages.subjects.parentPlaceholder')"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.subjects.code')" prop="subjectCode">
          <el-input v-model="formData.subjectCode" :placeholder="$t('financeReportPages.subjects.codePlaceholder')" :disabled="Boolean(formData.id)" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.subjects.name')" prop="subjectName">
          <el-input v-model="formData.subjectName" :placeholder="$t('financeReportPages.subjects.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.subjects.category')" prop="subjectType">
          <el-select v-model="formData.subjectType" :placeholder="$t('financeReportPages.subjects.categoryPlaceholder')" style="width: 100%">
            <el-option :label="$t('financeReportPages.subjects.categoryValue.asset')" value="ASSET" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.liability')" value="LIABILITY" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.equity')" value="EQUITY" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.revenue')" value="REVENUE" />
            <el-option :label="$t('financeReportPages.subjects.categoryValue.expense')" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.subjects.balanceDirection')" prop="balanceDirection">
          <el-radio-group v-model="formData.balanceDirection">
            <el-radio value="DEBIT">{{ $t('financeReportPages.subjects.direction.debit') }}</el-radio>
            <el-radio value="CREDIT">{{ $t('financeReportPages.subjects.direction.credit') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('financeReportPages.common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, CircleClose, CircleCheck } from '@element-plus/icons-vue'
import {
  getAccountSubjects,
  getAccountSubjectTree,
  getAccountSubject,
  createAccountSubject,
  updateAccountSubject,
  enableAccountSubject,
  disableAccountSubject,
  type AccountSubject
} from '@/api/finance'

const { t } = useI18n()

// 查询表单
const queryForm = reactive({
  subjectCode: '',
  subjectName: '',
  subjectType: '',
  status: ''
})

// 表格数据
const loading = ref(false)
const subjectTree = ref<AccountSubject[]>([])

// 对话框
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'createChild' | 'edit'>('create')
const dialogTitle = computed(() => ({
  create: t('financeReportPages.subjects.dialog.create'),
  createChild: t('financeReportPages.subjects.dialog.createChild'),
  edit: t('financeReportPages.subjects.dialog.edit')
})[dialogMode.value])
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | undefined,
  parentId: undefined as string | undefined,
  subjectCode: '',
  subjectName: '',
  subjectType: '',
  balanceDirection: 'DEBIT',
  remark: ''
})

// 树形选择器选项
const subjectTreeOptions = ref<AccountSubject[]>([])

// 表单验证规则
const formRules = computed<FormRules>(() => ({
  subjectCode: [{ required: true, message: t('financeReportPages.subjects.validation.code'), trigger: 'blur' }],
  subjectName: [{ required: true, message: t('financeReportPages.subjects.validation.name'), trigger: 'blur' }],
  subjectType: [{ required: true, message: t('financeReportPages.subjects.validation.category'), trigger: 'change' }],
  balanceDirection: [{ required: true, message: t('financeReportPages.subjects.validation.direction'), trigger: 'change' }]
}))

const hasSubjectQuery = () => {
  return Boolean(queryForm.subjectCode || queryForm.subjectName || queryForm.subjectType || queryForm.status)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const subjects = await getAccountSubjectTree()
    subjectTreeOptions.value = subjects || []
    if (hasSubjectQuery()) {
      const page = await getAccountSubjects({
        ...queryForm,
        pageNo: 1,
        pageSize: 200,
      })
      subjectTree.value = page.records || []
    } else {
      subjectTree.value = subjects || []
    }
  } catch (error) {
    console.error('Failed to load account subjects:', error)
    ElMessage.error(t('financeReportPages.subjects.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  loadData()
}

// 重置
const handleReset = () => {
  queryForm.subjectCode = ''
  queryForm.subjectName = ''
  queryForm.subjectType = ''
  queryForm.status = ''
  loadData()
}

// 新增
const handleAdd = () => {
  dialogMode.value = 'create'
  dialogVisible.value = true
}

// 新增下级
const handleAddChild = (row: AccountSubject) => {
  dialogMode.value = 'createChild'
  formData.parentId = row.id
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: AccountSubject) => {
  dialogMode.value = 'edit'
  try {
    const subject = await getAccountSubject(row.id)
    Object.assign(formData, {
      id: subject.id,
      parentId: subject.parentId,
      subjectCode: subject.subjectCode || subject.code || '',
      subjectName: subject.subjectName || subject.name || '',
      subjectType: subject.subjectType || subject.category || '',
      balanceDirection: subject.balanceDirection || defaultBalanceDirection(subject.subjectType || subject.category),
      remark: subject.remark || ''
    })
    dialogVisible.value = true
  } catch {
    ElMessage.error(t('financeReportPages.subjects.message.detailLoadFailed'))
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updateAccountSubject(formData.id, formData)
        ElMessage.success(t('financeReportPages.subjects.message.updated'))
      } else {
        await createAccountSubject(formData)
        ElMessage.success(t('financeReportPages.subjects.message.created'))
      }
      dialogVisible.value = false
      loadData()
    } catch {
      ElMessage.error(t('financeReportPages.subjects.message.actionFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

// 启用
const handleEnable = async (row: AccountSubject) => {
  try {
    await ElMessageBox.confirm(t('financeReportPages.subjects.message.enableConfirm', {
      name: row.name || row.subjectName || row.id
    }), t('financeReportPages.common.prompt'), {
      type: 'warning'
    })
    await enableAccountSubject(row.id)
    ElMessage.success(t('financeReportPages.subjects.message.enabled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('financeReportPages.subjects.message.enableFailed'))
    }
  }
}

// 停用
const handleDisable = async (row: AccountSubject) => {
  try {
    await ElMessageBox.confirm(t('financeReportPages.subjects.message.disableConfirm', {
      name: row.name || row.subjectName || row.id
    }), t('financeReportPages.common.prompt'), {
      type: 'warning'
    })
    await disableAccountSubject(row.id)
    ElMessage.success(t('financeReportPages.subjects.message.disabled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('financeReportPages.subjects.message.disableFailed'))
    }
  }
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    parentId: undefined,
    subjectCode: '',
    subjectName: '',
    subjectType: '',
    balanceDirection: 'DEBIT',
    remark: ''
  })
}

const defaultBalanceDirection = (subjectType?: string) => {
  return ['LIABILITY', 'EQUITY', 'REVENUE'].includes(subjectType || '') ? 'CREDIT' : 'DEBIT'
}

// 获取类别标签
const getCategoryLabel = (category: string) => {
  const map: Record<string, string> = {
    ASSET: t('financeReportPages.subjects.categoryValue.asset'),
    LIABILITY: t('financeReportPages.subjects.categoryValue.liability'),
    EQUITY: t('financeReportPages.subjects.categoryValue.equity'),
    REVENUE: t('financeReportPages.subjects.categoryValue.revenue'),
    EXPENSE: t('financeReportPages.subjects.categoryValue.expense')
  }
  return map[category] || category
}

// 获取类别类型
const getCategoryType = (category: string) => {
  const map: Record<string, any> = {
    ASSET: 'success',
    LIABILITY: 'warning',
    EQUITY: 'info',
    REVENUE: 'success',
    EXPENSE: 'danger'
  }
  return map[category] || ''
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
  justify-content: space-between;
  align-items: center;
}
</style>
