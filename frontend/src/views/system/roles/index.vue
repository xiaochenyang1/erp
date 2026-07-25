<template>
  <div class="roles-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('systemRoles.code')">
          <el-input
            v-model="queryParams.code"
            :placeholder="$t('systemRoles.codePlaceholder')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('systemRoles.name')">
          <el-input
            v-model="queryParams.name"
            :placeholder="$t('systemRoles.namePlaceholder')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('systemRoles.status')">
          <el-select
            v-model="queryParams.status"
            :placeholder="$t('systemRoles.selectStatus')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('systemRoles.active')" value="ACTIVE" />
            <el-option :label="$t('systemRoles.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ $t('systemRoles.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ $t('systemRoles.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'system:role:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ $t('systemRoles.addRole') }}
      </el-button>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="code" :label="$t('systemRoles.code')" width="150" />
        <el-table-column prop="name" :label="$t('systemRoles.name')" width="150" />
        <el-table-column prop="permissions" :label="$t('systemRoles.permissionCount')" width="100">
          <template #default="{ row }">
            {{ row.permissions?.length || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('systemRoles.status')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">{{ $t('systemRoles.active') }}</el-tag>
            <el-tag v-else type="danger">{{ $t('systemRoles.inactive') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('systemRoles.remark')" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="$t('systemRoles.createdAt')" width="160" />
        <el-table-column :label="$t('systemRoles.operations')" width="380" fixed="right">
          <template #default="{ row }: { row: Role }">
            <el-button v-permission="'system:role:update'" link type="primary" @click="handleEdit(row)">
              {{ $t('systemRoles.edit') }}
            </el-button>
            <el-button v-permission="'system:role:assign-menu'" link type="primary" @click="handlePermission(row)">
              {{ $t('systemRoles.permissionSettings') }}
            </el-button>
            <el-button v-permission="'system:role:assign-data-scope'" link type="primary" @click="handleAssignDataScope(row)">
              {{ $t('systemRoles.dataScope') }}
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:role:disable'" link type="danger" @click="handleDisable(row)">
              {{ $t('systemRoles.disable') }}
            </el-button>
            <el-button v-else v-permission="'system:role:enable'" link type="success" @click="handleEnable(row)">
              {{ $t('systemRoles.enable') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item :label="$t('systemRoles.code')" prop="code">
          <el-input
            v-model="formData.code"
            :placeholder="$t('systemRoles.codePlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="$t('systemRoles.name')" prop="name">
          <el-input
            v-model="formData.name"
            :placeholder="$t('systemRoles.namePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('systemRoles.status')" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ $t('systemRoles.active') }}</el-radio>
            <el-radio value="INACTIVE">{{ $t('systemRoles.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('systemRoles.remark')">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            :placeholder="$t('systemRoles.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('systemRoles.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('systemRoles.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 权限设置对话框 -->
    <el-dialog
      v-model="permissionDialogVisible"
      :title="$t('systemRoles.permissionSettings')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-tree
        ref="permissionTreeRef"
        :data="permissionTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        show-checkbox
        :default-checked-keys="selectedPermissions"
      />

      <template #footer>
        <el-button @click="permissionDialogVisible = false">{{ $t('systemRoles.cancel') }}</el-button>
        <el-button type="primary" :loading="permissionSubmitLoading" @click="handleSavePermission">
          {{ $t('systemRoles.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataScopeDialogVisible" :title="$t('systemRoles.dataScopeDialog')" width="640px" destroy-on-close>
      <div v-loading="dataScopeLoading">
        <el-alert
          class="role-alert"
          :title="$t('systemRoles.currentRole', { role: currentRoleName || '-' })"
          type="info"
          show-icon
          :closable="false"
          :description="$t('systemRoles.dataScopeDescription')"
        />
        <el-alert
          v-if="dataScopeForm.hasAllScope"
          class="role-alert"
          :title="$t('systemRoles.allDataNotice')"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-form label-width="110px" class="data-scope-form">
          <el-form-item :label="$t('systemRoles.allData')">
            <el-switch v-model="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemRoles.ownDepartment')">
            <el-switch v-model="dataScopeForm.deptScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemRoles.ownPost')">
            <el-switch v-model="dataScopeForm.postScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemRoles.selfOnly')">
            <el-switch v-model="dataScopeForm.selfScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemRoles.warehouseScope')">
            <el-select
              v-model="dataScopeForm.warehouseIds"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              :disabled="dataScopeForm.hasAllScope"
              :placeholder="$t('systemRoles.selectWarehouses')"
              style="width: 100%"
            >
              <el-option
                v-for="warehouse in warehouses"
                :key="warehouse.id"
                :label="warehouseOptionLabel(warehouse)"
                :value="warehouse.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dataScopeDialogVisible = false">{{ $t('systemRoles.cancel') }}</el-button>
        <el-button type="primary" :loading="dataScopeSubmitLoading" @click="submitDataScopeAssignment">{{ $t('systemRoles.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import {
  getRoles,
  getRole,
  createRole,
  updateRole,
  deleteRole,
  enableRole,
  getMenuTree,
  getAssignedRoleMenus,
  assignRoleMenus,
  getAssignedRoleDataScope,
  assignRoleDataScope,
  type Menu,
  type RoleQuery,
  type RoleSaveRequest,
  type Role
} from '@/api/system'
import { getWarehouses, type Warehouse } from '@/api/masterdata'

const { t } = useI18n()

// 查询参数
const queryParams = reactive<RoleQuery>({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  status: ''
})

// 表格数据
const loading = ref(false)
const tableData = ref<Role[]>([])
const total = ref(0)

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const permissionSubmitLoading = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<RoleSaveRequest>({
  code: '',
  name: '',
  permissions: [],
  status: 'ACTIVE',
  remark: ''
})

// 表单验证规则
const formRules = computed<FormRules>(() => ({
  code: [
    { required: true, message: t('systemRoles.codePlaceholder'), trigger: 'blur' },
    { pattern: /^[A-Z_]+$/, message: t('systemRoles.validation.codePattern'), trigger: 'blur' }
  ],
  name: [{ required: true, message: t('systemRoles.namePlaceholder'), trigger: 'blur' }],
  status: [{ required: true, message: t('systemRoles.selectStatus'), trigger: 'change' }]
}))

// 权限设置对话框
const permissionDialogVisible = ref(false)
const permissionTreeRef = ref<InstanceType<typeof ElTree>>()
const currentRoleId = ref<string | number>('')
const currentRoleName = ref('')
const selectedPermissions = ref<string[]>([])
const permissionTree = ref<Menu[]>([])

// 数据范围
const dataScopeDialogVisible = ref(false)
const dataScopeLoading = ref(false)
const dataScopeSubmitLoading = ref(false)
const warehouses = ref<Warehouse[]>([])
const dataScopeForm = reactive({
  hasAllScope: false,
  deptScoped: false,
  postScoped: false,
  selfScoped: false,
  warehouseIds: [] as string[]
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getRoles(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error(t('systemRoles.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.code = ''
  queryParams.name = ''
  queryParams.status = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = t('systemRoles.dialog.add')
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: Role) => {
  dialogTitle.value = t('systemRoles.dialog.edit')
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getRole(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('systemRoles.message.detailLoadFailed'))
  }
}

// 停用
const handleDisable = async (row: Role) => {
  try {
    await ElMessageBox.confirm(t('systemRoles.message.disableConfirm', { name: row.name }), t('systemRoles.prompt'), {
      confirmButtonText: t('systemRoles.confirm'),
      cancelButtonText: t('systemRoles.cancel'),
      type: 'warning'
    })
    await deleteRole(row.id)
    ElMessage.success(t('systemRoles.message.disableSuccess'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemRoles.message.disableFailed'))
    }
  }
}

// 启用
const handleEnable = async (row: Role) => {
  try {
    await ElMessageBox.confirm(t('systemRoles.message.enableConfirm', { name: row.name }), t('systemRoles.prompt'), {
      confirmButtonText: t('systemRoles.confirm'),
      cancelButtonText: t('systemRoles.cancel'),
      type: 'warning'
    })
    await enableRole(row.id)
    ElMessage.success(t('systemRoles.message.enableSuccess'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemRoles.message.enableFailed'))
    }
  }
}

// 权限设置
const handlePermission = async (row: Role) => {
  currentRoleId.value = row.id
  currentRoleName.value = row.name || row.code
  try {
    const [menus, assignment] = await Promise.all([
      getMenuTree(),
      getAssignedRoleMenus(row.id)
    ])
    permissionTree.value = menus
    selectedPermissions.value = assignment.menuIds
    permissionDialogVisible.value = true
    await nextTick()
    permissionTreeRef.value?.setCheckedKeys(selectedPermissions.value, false)
  } catch (error) {
    ElMessage.error(t('systemRoles.message.permissionsLoadFailed'))
  }
}

const warehouseOptionLabel = (warehouse: Warehouse) => {
  const name = warehouse.name || warehouse.warehouseName || t('systemRoles.warehouseFallback', { id: warehouse.id })
  const code = warehouse.code || warehouse.warehouseCode
  return code ? t('systemRoles.warehouseOption', { name, code }) : name
}

const handleAssignDataScope = async (row: Role) => {
  currentRoleId.value = row.id
  currentRoleName.value = row.name || row.code
  dataScopeDialogVisible.value = true
  dataScopeLoading.value = true
  try {
    if (!warehouses.value.length) {
      const page = await getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      warehouses.value = page.records || []
    }
    const scope = await getAssignedRoleDataScope(row.id)
    dataScopeForm.hasAllScope = !!scope.hasAllScope
    dataScopeForm.deptScoped = !!scope.deptScoped
    dataScopeForm.postScoped = !!scope.postScoped
    dataScopeForm.selfScoped = !!scope.selfScoped
    dataScopeForm.warehouseIds = scope.warehouseIds || []
  } catch (error) {
    dataScopeForm.hasAllScope = false
    dataScopeForm.deptScoped = false
    dataScopeForm.postScoped = false
    dataScopeForm.selfScoped = false
    dataScopeForm.warehouseIds = []
    ElMessage.error(t('systemRoles.message.dataScopeLoadFailed'))
  } finally {
    dataScopeLoading.value = false
  }
}

const submitDataScopeAssignment = async () => {
  if (!currentRoleId.value) return
  dataScopeSubmitLoading.value = true
  try {
    await assignRoleDataScope(currentRoleId.value, {
      hasAllScope: dataScopeForm.hasAllScope,
      deptScoped: dataScopeForm.deptScoped,
      postScoped: dataScopeForm.postScoped,
      selfScoped: dataScopeForm.selfScoped,
      warehouseIds: dataScopeForm.warehouseIds
    })
    ElMessage.success(t('systemRoles.message.dataScopeSaved'))
    dataScopeDialogVisible.value = false
  } catch (error) {
    ElMessage.error(t('systemRoles.message.dataScopeSaveFailed'))
  } finally {
    dataScopeSubmitLoading.value = false
  }
}

// 保存权限
const handleSavePermission = async () => {
  if (!permissionTreeRef.value) return

  permissionSubmitLoading.value = true
  try {
    const checkedKeys = permissionTreeRef.value.getCheckedKeys(false) as Array<string | number>
    const halfCheckedKeys = permissionTreeRef.value.getHalfCheckedKeys() as Array<string | number>
    const menuIds = [...new Set([...checkedKeys, ...halfCheckedKeys].map(String))]
    if (menuIds.length === 0) {
      ElMessage.error(t('systemRoles.message.menuRequired'))
      return
    }

    await assignRoleMenus(currentRoleId.value, menuIds)

    ElMessage.success(t('systemRoles.message.permissionsSaved'))
    permissionDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('systemRoles.message.permissionsSaveFailed'))
  } finally {
    permissionSubmitLoading.value = false
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value) {
          await updateRole(currentId.value, formData)
        } else {
          await createRole(formData)
        }
        ElMessage.success(t('systemRoles.message.operationSuccess'))
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(t('systemRoles.message.operationFailed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.code = ''
  formData.name = ''
  formData.permissions = []
  formData.status = 'ACTIVE'
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.roles-container {
  padding: 20px;

  .role-alert {
    margin-bottom: 14px;
  }

  .data-scope-form {
    margin-top: 8px;
  }

  .search-card,
  .toolbar-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
