<template>
  <div class="roles-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="角色编码">
          <el-input
            v-model="queryParams.code"
            placeholder="请输入角色编码"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入角色名称"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 150px"
          >
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'system:role:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新增角色
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
        <el-table-column prop="code" label="角色编码" width="150" />
        <el-table-column prop="name" label="角色名称" width="150" />
        <el-table-column prop="permissions" label="权限数量" width="100">
          <template #default="{ row }">
            {{ row.permissions?.length || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">正常</el-tag>
            <el-tag v-else type="danger">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }: { row: Role }">
            <el-button v-permission="'system:role:update'" link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'system:role:assign-menu'" link type="primary" @click="handlePermission(row)">
              权限设置
            </el-button>
            <el-button v-permission="'system:role:assign-data-scope'" link type="primary" @click="handleAssignDataScope(row)">
              数据范围
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:role:disable'" link type="danger" @click="handleDisable(row)">
              停用
            </el-button>
            <el-button v-else v-permission="'system:role:enable'" link type="success" @click="handleEnable(row)">
              启用
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
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="formData.code"
            placeholder="请输入角色编码"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入角色名称"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 权限设置对话框 -->
    <el-dialog
      v-model="permissionDialogVisible"
      title="权限设置"
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
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permissionSubmitLoading" @click="handleSavePermission">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataScopeDialogVisible" title="配置数据范围" width="640px" destroy-on-close>
      <div v-loading="dataScopeLoading">
        <el-alert
          class="role-alert"
          :title="`当前角色：${currentRoleName || '-'}`"
          type="info"
          show-icon
          :closable="false"
          description="角色数据范围为该角色下所有用户的默认可见范围，与用户级范围取并集。勾选「全部数据」时忽略部门/岗位/本人/仓库范围。"
        />
        <el-alert
          v-if="dataScopeForm.hasAllScope"
          class="role-alert"
          title="当前为「全部数据」：持有该角色的用户默认可见本账套全部业务数据（用户级未再收紧时）"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-form label-width="110px" class="data-scope-form">
          <el-form-item label="全部数据">
            <el-switch v-model="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item label="本部门">
            <el-switch v-model="dataScopeForm.deptScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item label="本岗位">
            <el-switch v-model="dataScopeForm.postScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item label="仅本人">
            <el-switch v-model="dataScopeForm.selfScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item label="仓库范围">
            <el-select
              v-model="dataScopeForm.warehouseIds"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              :disabled="dataScopeForm.hasAllScope"
              placeholder="选择可见仓库"
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
        <el-button @click="dataScopeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataScopeSubmitLoading" @click="submitDataScopeAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
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
const formRules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z_]+$/, message: '角色编码只能包含大写字母和下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

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
    ElMessage.error('加载数据失败')
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
  dialogTitle.value = '新增角色'
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: Role) => {
  dialogTitle.value = '编辑角色'
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getRole(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 停用
const handleDisable = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确认停用角色"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRole(row.id)
    ElMessage.success('停用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

// 启用
const handleEnable = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确认启用角色"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await enableRole(row.id)
    ElMessage.success('启用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
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
    ElMessage.error('加载权限失败')
  }
}

const warehouseOptionLabel = (warehouse: Warehouse) => {
  const name = warehouse.name || warehouse.warehouseName || `仓库 ${warehouse.id}`
  const code = warehouse.code || warehouse.warehouseCode
  return code ? `${name}（${code}）` : name
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
    ElMessage.error('加载数据范围失败')
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
    ElMessage.success('数据范围已保存（持有该角色的用户下次请求即生效）')
    dataScopeDialogVisible.value = false
  } catch (error) {
    ElMessage.error('保存数据范围失败')
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
      ElMessage.error('请至少选择一个菜单')
      return
    }

    await assignRoleMenus(currentRoleId.value, menuIds)

    ElMessage.success('权限设置成功')
    permissionDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('权限设置失败')
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
        ElMessage.success('操作成功')
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error('操作失败')
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
