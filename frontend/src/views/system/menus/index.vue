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
            <el-tag :type="typeTagType(row.type)">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('systemMenu.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, DCaret } from '@element-plus/icons-vue'
import {
  getMenuTree,
  getMenu,
  createMenu,
  updateMenu,
  deleteMenu,
  enableMenu,
  type Menu
} from '@/api/system'
import { useSystemMenuPresentation } from '@/composables/useSystemMenuPresentation'
import { useSystemMenuList } from '@/composables/useSystemMenuList'
import { useSystemMenuForm } from '@/composables/useSystemMenuForm'

const { t } = useI18n()
const formRef = ref<FormInstance>()
const tableRef = ref()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
  buildParentTree,
  statusText,
  statusType,
  typeTagType,
  typeText
} = useSystemMenuPresentation(t)

const {
  handleDisable,
  handleEnable,
  loadData,
  loading,
  menuTree,
  tableData
} = useSystemMenuList(t, {
  getMenuTree,
  deleteMenu,
  enableMenu,
  buildParentTree,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleCreate: openCreate,
  handleEdit: openEdit,
  handleSubmit: saveMenu,
  submitLoading
} = useSystemMenuForm(t, {
  getMenu,
  createMenu,
  updateMenu,
  onSubmitted: async () => {
    await loadData()
  },
  ...notify
})

const formRules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('systemMenu.validation.name'), trigger: 'blur' }],
  type: [{ required: true, message: t('systemMenu.validation.type'), trigger: 'change' }],
  orderNum: [{ required: true, message: t('systemMenu.validation.order'), trigger: 'blur' }]
}))

const expandAll = () => {
  tableRef.value?.toggleAllRowExpansion()
}

const handleCreate = (row: Menu | null) => {
  openCreate(row)
  formRef.value?.clearValidate()
}

const handleEdit = async (row: Menu) => {
  if (await openEdit(row)) {
    formRef.value?.clearValidate()
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  await saveMenu()
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
