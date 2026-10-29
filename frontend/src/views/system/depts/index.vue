<template>
  <div class="depts-container">
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'system:dept:create'" type="primary" @click="handleCreate(null)">
        <el-icon><Plus /></el-icon>
        {{ $t('systemDept.create') }}
      </el-button>
      <el-button @click="expandAll">
        <el-icon><DCaret /></el-icon>
        {{ $t('systemDept.expandCollapse') }}
      </el-button>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        row-key="id"
        border
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" :label="$t('systemDept.name')" width="200" />
        <el-table-column prop="code" :label="$t('systemDept.code')" width="150" />
        <el-table-column prop="manager" :label="$t('systemDept.manager')" width="120" />
        <el-table-column prop="contact" :label="$t('systemDept.contact')" width="150" />
        <el-table-column prop="orderNum" :label="$t('systemDept.order')" width="80" />
        <el-table-column prop="status" :label="$t('systemDept.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('systemDept.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:create'" link type="primary" @click="handleCreate(row)">{{ $t('systemDept.add') }}</el-button>
            <el-button v-permission="'system:dept:update'" link type="primary" @click="handleEdit(row)">{{ $t('systemDept.edit') }}</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:dept:disable'" link type="danger" @click="handleDisable(row)">{{ $t('systemDept.disable') }}</el-button>
            <el-button v-else v-permission="'system:dept:enable'" link type="success" @click="handleEnable(row)">{{ $t('systemDept.enable') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('systemDept.parent')">
          <el-tree-select
            v-model="formData.parentId"
            :data="deptTree"
            :props="{ label: 'name', value: 'id' }"
            :placeholder="$t('systemDept.selectParent')"
            clearable
          />
        </el-form-item>
        <el-form-item :label="$t('systemDept.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('systemDept.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDept.code')">
          <el-input v-model="formData.code" :placeholder="$t('systemDept.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDept.manager')">
          <el-input v-model="formData.manager" :placeholder="$t('systemDept.managerPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDept.contact')">
          <el-input v-model="formData.contact" :placeholder="$t('systemDept.contactPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDept.order')" prop="orderNum">
          <el-input-number v-model="formData.orderNum" :min="0" />
        </el-form-item>
        <el-form-item :label="$t('systemDept.status')">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ $t('systemDept.active') }}</el-radio>
            <el-radio value="INACTIVE">{{ $t('systemDept.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('systemDept.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('systemDept.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, DCaret } from '@element-plus/icons-vue'
import {
  getDeptTree,
  getDept,
  createDept,
  updateDept,
  deleteDept,
  enableDept,
  type Dept
} from '@/api/system'
import { useSystemDeptPresentation } from '@/composables/useSystemDeptPresentation'
import { useSystemDeptList } from '@/composables/useSystemDeptList'
import { useSystemDeptForm } from '@/composables/useSystemDeptForm'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const tableRef = ref()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const { statusText, statusType, buildParentTree } = useSystemDeptPresentation(t)

const {
  deptTree,
  handleDisable,
  handleEnable,
  loadData,
  loading,
  tableData
} = useSystemDeptList(t, {
  getDeptTree,
  deleteDept,
  enableDept,
  buildParentTree,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleCreate,
  handleEdit: openEdit,
  handleSubmit: saveDept,
  submitLoading
} = useSystemDeptForm(t, {
  getDept,
  createDept,
  updateDept,
  onSubmitted: async () => { await loadData() },
  ...notify
})

const formRules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('systemDept.validation.name'), trigger: 'blur' }],
  orderNum: [{ required: true, message: t('systemDept.validation.order'), trigger: 'blur' }]
}))

const expandAll = () => {
  tableRef.value?.toggleAllRowExpansion()
}

const handleEdit = async (row: Dept) => {
  await openEdit(row)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await saveDept()
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.depts-container {
  padding: 20px;
  .toolbar-card, .table-card {
    margin-bottom: 20px;
  }
}
</style>
