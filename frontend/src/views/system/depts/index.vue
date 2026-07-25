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
            <el-tag v-if="row.status === 'ACTIVE'" type="success">{{ $t('systemDept.active') }}</el-tag>
            <el-tag v-else type="danger">{{ $t('systemDept.inactive') }}</el-tag>
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
import { computed, ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getDeptTree, getDept, createDept, updateDept, deleteDept, enableDept, type Dept, type DeptSaveRequest } from '@/api/system'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<Dept[]>([])
const deptTree = ref<Dept[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()
const tableRef = ref()

const formData = reactive<DeptSaveRequest>({
  parentId: undefined,
  name: '',
  code: '',
  manager: '',
  contact: '',
  orderNum: 0,
  status: 'ACTIVE'
})

const formRules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('systemDept.validation.name'), trigger: 'blur' }],
  orderNum: [{ required: true, message: t('systemDept.validation.order'), trigger: 'blur' }]
}))

const loadData = async () => {
  loading.value = true
  try {
    const data = await getDeptTree()
    tableData.value = data
    deptTree.value = [{ id: 0, name: t('systemDept.root'), children: data } as any]
  } catch {
    ElMessage.error(t('systemDept.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const expandAll = () => {
  tableRef.value?.toggleAllRowExpansion()
}

const handleCreate = (row: Dept | null) => {
  dialogTitle.value = t('systemDept.create')
  isEdit.value = false
  resetForm()
  if (row) {
    formData.parentId = row.id
  }
  dialogVisible.value = true
}

const handleEdit = async (row: Dept) => {
  dialogTitle.value = t('systemDept.editTitle')
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getDept(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch {
    ElMessage.error(t('systemDept.message.detailLoadFailed'))
  }
}

const handleDisable = async (row: Dept) => {
  try {
    await ElMessageBox.confirm(t('systemDept.message.disableConfirm', { name: row.name }), t('systemDept.message.prompt'), {
      confirmButtonText: t('systemDept.message.confirm'),
      cancelButtonText: t('systemDept.message.cancel'),
      type: 'warning'
    })
    await deleteDept(row.id)
    ElMessage.success(t('systemDept.message.disabled'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemDept.message.disableFailed'))
    }
  }
}

const handleEnable = async (row: Dept) => {
  try {
    await ElMessageBox.confirm(t('systemDept.message.enableConfirm', { name: row.name }), t('systemDept.message.prompt'), {
      confirmButtonText: t('systemDept.message.confirm'),
      cancelButtonText: t('systemDept.message.cancel'),
      type: 'warning'
    })
    await enableDept(row.id)
    ElMessage.success(t('systemDept.message.enabled'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemDept.message.enableFailed'))
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value) {
          await updateDept(currentId.value, formData)
        } else {
          await createDept(formData)
        }
        ElMessage.success(t('systemDept.message.saved'))
        dialogVisible.value = false
        loadData()
      } catch {
        ElMessage.error(t('systemDept.message.saveFailed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const resetForm = () => {
  formData.parentId = undefined
  formData.name = ''
  formData.code = ''
  formData.manager = ''
  formData.contact = ''
  formData.orderNum = 0
  formData.status = 'ACTIVE'
  formRef.value?.clearValidate()
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
