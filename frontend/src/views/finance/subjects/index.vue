<template>
  <div class="app-container">
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

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('financeReportPages.subjects.management') }}</span>
          <el-button v-permission="'finance:subject:manage'" type="primary" :icon="Plus" @click="handleAdd">{{ $t('financeReportPages.subjects.create') }}</el-button>
        </div>
      </template>

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
            <el-tag :type="leafType(row.isLeaf)" size="small">
              {{ leafLabel(row.isLeaf) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="onDialogClose"
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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, CircleClose, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createAccountSubject,
  disableAccountSubject,
  enableAccountSubject,
  getAccountSubject,
  getAccountSubjects,
  getAccountSubjectTree,
  updateAccountSubject
} from '@/api/finance'
import { useAccountSubjectForm } from '@/composables/useAccountSubjectForm'
import { useAccountSubjectList } from '@/composables/useAccountSubjectList'
import { useAccountSubjectPresentation } from '@/composables/useAccountSubjectPresentation'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  getCategoryLabel,
  getCategoryType,
  leafLabel,
  leafType,
  statusLabel,
  statusType,
  subjectDisplayName
} = useAccountSubjectPresentation(t)

const {
  handleDisable,
  handleEnable,
  handleQuery,
  handleReset,
  loadData,
  loading,
  queryForm,
  subjectTree,
  subjectTreeOptions
} = useAccountSubjectList(t, {
  getAccountSubjectTree,
  getAccountSubjects,
  enableAccountSubject,
  disableAccountSubject,
  subjectDisplayName,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAdd,
  handleAddChild,
  handleDialogClose,
  handleEdit,
  submitLoading,
  submitSave
} = useAccountSubjectForm(t, {
  getAccountSubject,
  createAccountSubject,
  updateAccountSubject,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onSubmitted: () => loadData()
})

const formRules = computed<FormRules>(() => ({
  subjectCode: [{ required: true, message: t('financeReportPages.subjects.validation.code'), trigger: 'blur' }],
  subjectName: [{ required: true, message: t('financeReportPages.subjects.validation.name'), trigger: 'blur' }],
  subjectType: [{ required: true, message: t('financeReportPages.subjects.validation.category'), trigger: 'change' }],
  balanceDirection: [{ required: true, message: t('financeReportPages.subjects.validation.direction'), trigger: 'change' }]
}))

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await submitSave()
  })
}

const onDialogClose = () => {
  formRef.value?.resetFields()
  handleDialogClose()
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
