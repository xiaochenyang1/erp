<template>
  <div class="menus-container">
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'system:menu:create'" type="primary" @click="handleCreate(null)">
        <el-icon><Plus /></el-icon>
        {{ $t('systemMenu.create') }}
      </el-button>
      <el-button @click="expandAll">
        <el-icon><DCaret /></el-icon>
        {{ $t('systemMenu.expandCollapse') }}
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
        <el-table-column prop="name" :label="$t('systemMenu.name')" width="200" />
        <el-table-column prop="path" :label="$t('systemMenu.path')" width="200" />
        <el-table-column prop="component" :label="$t('systemMenu.component')" show-overflow-tooltip />
        <el-table-column prop="icon" :label="$t('systemMenu.icon')" width="100" />
        <el-table-column prop="orderNum" :label="$t('systemMenu.order')" width="80" />
        <el-table-column prop="type" :label="$t('systemMenu.type')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'MENU'">{{ $t('systemMenu.menu') }}</el-tag>
            <el-tag v-else type="info">{{ $t('systemMenu.button') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('systemMenu.status')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">{{ $t('systemMenu.active') }}</el-tag>
            <el-tag v-else type="danger">{{ $t('systemMenu.inactive') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('systemMenu.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:menu:create'" link type="primary" @click="handleCreate(row)">{{ $t('systemMenu.add') }}</el-button>
            <el-button v-permission="'system:menu:update'" link type="primary" @click="handleEdit(row)">{{ $t('systemMenu.edit') }}</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:menu:disable'" link type="danger" @click="handleDisable(row)">{{ $t('systemMenu.disable') }}</el-button>
            <el-button v-else v-permission="'system:menu:enable'" link type="success" @click="handleEnable(row)">{{ $t('systemMenu.enable') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('systemMenu.parent')">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuTree"
            :props="{ label: 'name', value: 'id' }"
            :placeholder="$t('systemMenu.selectParent')"
            clearable
          />
        </el-form-item>
        <el-form-item :label="$t('systemMenu.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('systemMenu.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemMenu.type')" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio value="MENU">{{ $t('systemMenu.menu') }}</el-radio>
            <el-radio value="BUTTON">{{ $t('systemMenu.button') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="formData.type === 'MENU'" :label="$t('systemMenu.routePath')">
          <el-input v-model="formData.path" :placeholder="$t('systemMenu.routePathPlaceholder')" />
        </el-form-item>
        <el-form-item v-if="formData.type === 'MENU'" :label="$t('systemMenu.componentPath')">
          <el-input v-model="formData.component" :placeholder="$t('systemMenu.componentPathPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemMenu.permission')">
          <el-input v-model="formData.permission" :placeholder="$t('systemMenu.permissionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemMenu.icon')">
          <el-input v-model="formData.icon" :placeholder="$t('systemMenu.iconPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemMenu.order')" prop="orderNum">
          <el-input-number v-model="formData.orderNum" :min="0" />
        </el-form-item>
        <el-form-item :label="$t('systemMenu.status')">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ $t('systemMenu.active') }}</el-radio>
            <el-radio value="INACTIVE">{{ $t('systemMenu.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('systemMenu.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('systemMenu.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getMenuTree, getMenu, createMenu, updateMenu, deleteMenu, enableMenu, type Menu, type MenuSaveRequest } from '@/api/system'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<Menu[]>([])
const menuTree = ref<Menu[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()
const tableRef = ref()

const formData = reactive<MenuSaveRequest>({
  parentId: undefined,
  name: '',
  path: '',
  component: '',
  icon: '',
  orderNum: 0,
  type: 'MENU',
  permission: '',
  status: 'ACTIVE'
})

const formRules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('systemMenu.validation.name'), trigger: 'blur' }],
  type: [{ required: true, message: t('systemMenu.validation.type'), trigger: 'change' }],
  orderNum: [{ required: true, message: t('systemMenu.validation.order'), trigger: 'blur' }]
}))

const loadData = async () => {
  loading.value = true
  try {
    const data = await getMenuTree()
    tableData.value = data
    menuTree.value = [{ id: 0, name: t('systemMenu.root'), children: data } as any]
  } catch {
    ElMessage.error(t('systemMenu.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const expandAll = () => {
  tableRef.value?.toggleAllRowExpansion()
}

const handleCreate = (row: Menu | null) => {
  dialogTitle.value = t('systemMenu.create')
  isEdit.value = false
  resetForm()
  if (row) {
    formData.parentId = row.id
  }
  dialogVisible.value = true
}

const handleEdit = async (row: Menu) => {
  dialogTitle.value = t('systemMenu.editTitle')
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getMenu(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch {
    ElMessage.error(t('systemMenu.message.detailLoadFailed'))
  }
}

const handleDisable = async (row: Menu) => {
  try {
    await ElMessageBox.confirm(t('systemMenu.message.disableConfirm', { name: row.name }), t('systemMenu.message.prompt'), {
      confirmButtonText: t('systemMenu.message.confirm'),
      cancelButtonText: t('systemMenu.message.cancel'),
      type: 'warning'
    })
    await deleteMenu(row.id)
    ElMessage.success(t('systemMenu.message.disabled'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemMenu.message.disableFailed'))
    }
  }
}

const handleEnable = async (row: Menu) => {
  try {
    await ElMessageBox.confirm(t('systemMenu.message.enableConfirm', { name: row.name }), t('systemMenu.message.prompt'), {
      confirmButtonText: t('systemMenu.message.confirm'),
      cancelButtonText: t('systemMenu.message.cancel'),
      type: 'warning'
    })
    await enableMenu(row.id)
    ElMessage.success(t('systemMenu.message.enabled'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemMenu.message.enableFailed'))
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
          await updateMenu(currentId.value, formData)
        } else {
          await createMenu(formData)
        }
        ElMessage.success(t('systemMenu.message.saved'))
        dialogVisible.value = false
        loadData()
      } catch {
        ElMessage.error(t('systemMenu.message.saveFailed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const resetForm = () => {
  formData.parentId = undefined
  formData.name = ''
  formData.path = ''
  formData.component = ''
  formData.icon = ''
  formData.orderNum = 0
  formData.type = 'MENU'
  formData.permission = ''
  formData.status = 'ACTIVE'
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.menus-container {
  padding: 20px;
  .toolbar-card, .table-card {
    margin-bottom: 20px;
  }
}
</style>
