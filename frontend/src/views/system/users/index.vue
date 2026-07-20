<template>
  <div class="system-users-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="用户名/姓名/工号"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="queryParams.deptId" placeholder="请选择部门" clearable style="width: 180px">
            <el-option v-for="dept in flatDepts" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="queryParams.postId" placeholder="请选择岗位" clearable style="width: 180px">
            <el-option v-for="post in posts" :key="post.id" :label="post.name" :value="post.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
            <el-option label="锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button v-permission="'system:user:create'" type="primary" :icon="Plus" @click="handleCreate">新增用户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="username" label="用户名" min-width="140" fixed />
        <el-table-column prop="employeeNo" label="工号" width="120" />
        <el-table-column prop="realName" label="姓名" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机号" width="140" />
        <el-table-column prop="deptId" label="部门" width="160">
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="postId" label="岗位" width="160">
          <template #default="{ row }">{{ postName(row.postId) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ roleNames(row.roles) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="460" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:user:update'" link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:user:assign-role'" link type="primary" :icon="UserFilled" @click="handleAssignRoles(row)">角色</el-button>
            <el-button v-permission="'system:user:assign-data-scope'" link type="primary" @click="handleAssignDataScope(row)">数据范围</el-button>
            <el-button v-permission="'system:user:reset-password'" link type="warning" :icon="Key" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button
              v-if="row.status !== 'INACTIVE'"
              v-permission="'system:user:disable'"
              link
              type="danger"
              :icon="Delete"
              @click="handleDisable(row)"
            >
              停用
            </el-button>
            <el-button
              v-else
              v-permission="'system:user:enable'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="请输入用户名" :disabled="Boolean(formData.id)" />
        </el-form-item>
        <el-form-item v-if="!formData.id" label="初始密码" prop="password">
          <el-input v-model="formData.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="formData.employeeNo" placeholder="请输入工号" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="formData.mobile" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="头像URL">
          <el-input v-model="formData.avatar" placeholder="可选，头像图片地址" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="formData.deptId" placeholder="请选择部门" clearable style="width: 100%">
            <el-option v-for="dept in flatDepts" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="formData.postId" placeholder="请选择岗位" clearable style="width: 100%">
            <el-option v-for="post in posts" :key="post.id" :label="post.name" :value="post.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="560px" destroy-on-close>
      <div v-loading="roleLoading">
        <el-alert
          class="role-alert"
          :title="`当前用户：${currentUsername || '-'}`"
          type="info"
          show-icon
          :closable="false"
        />
        <el-checkbox-group v-model="selectedRoleIds" class="role-list">
          <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">
            {{ role.name }}（{{ role.code }}）
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitLoading" @click="submitRoleAssignment">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataScopeDialogVisible" title="配置数据范围" width="640px" destroy-on-close>
      <div v-loading="dataScopeLoading">
        <el-alert
          class="role-alert"
          :title="`当前用户：${currentUsername || '-'}`"
          type="info"
          show-icon
          :closable="false"
          description="下方开关仅编辑用户级追加范围；生效范围 = 用户配置 ∪ 角色配置。勾选「全部数据」时忽略其它用户级项。"
        />
        <div class="effective-scope-box">
          <div class="effective-scope-title">当前生效范围（含角色并集）</div>
          <div class="effective-scope-tags">
            <template v-if="effectiveScopeSummary.tags.length">
              <el-tag
                v-for="tag in effectiveScopeSummary.tags"
                :key="tag"
                size="small"
                type="success"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </template>
            <span v-else class="effective-scope-empty">无生效范围（列表将不可见业务数据）</span>
          </div>
        </div>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Key, Plus, Refresh, Search, UserFilled } from '@element-plus/icons-vue'
import {
  assignUserDataScope,
  assignUserRoles,
  createUser,
  deleteUser,
  enableUser,
  getDeptTree,
  getAllPosts,
  getAllRoles,
  getAssignedUserDataScope,
  getAssignedUserRoles,
  getUser,
  getUsers,
  resetUserPassword,
  updateUser,
  type Dept,
  type Post,
  type Role,
  type User,
  type UserQuery
} from '@/api/system'
import { getWarehouses, type Warehouse } from '@/api/masterdata'

type UserForm = {
  id?: string
  username: string
  password?: string
  employeeNo?: string
  realName: string
  email?: string
  mobile?: string
  avatar?: string
  deptId?: string | number
  postId?: string | number
  remark?: string
}

const queryParams = reactive<UserQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  deptId: undefined,
  postId: undefined,
  status: ''
})

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<User[]>([])
const total = ref(0)
const depts = ref<Dept[]>([])
const posts = ref<Post[]>([])
const roles = ref<Role[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const roleDialogVisible = ref(false)
const roleLoading = ref(false)
const roleSubmitLoading = ref(false)
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
const effectiveScope = reactive({
  hasAllScope: false,
  deptScoped: false,
  postScoped: false,
  selfScoped: false,
  warehouseIds: [] as string[]
})
const effectiveScopeSummary = computed(() => {
  if (effectiveScope.hasAllScope) {
    return { tags: ['全部数据'] }
  }
  const tags: string[] = []
  if (effectiveScope.deptScoped) tags.push('本部门')
  if (effectiveScope.postScoped) tags.push('本岗位')
  if (effectiveScope.selfScoped) tags.push('仅本人')
  for (const id of effectiveScope.warehouseIds) {
    const warehouse = warehouses.value.find((item) => String(item.id) === String(id))
    tags.push(warehouse ? `仓:${warehouseOptionLabel(warehouse)}` : `仓库 ${id}`)
  }
  return { tags }
})
const currentUserId = ref<string | number>('')
const currentUsername = ref('')
const selectedRoleIds = ref<string[]>([])

const formData = reactive<UserForm>({
  username: '',
  password: '',
  employeeNo: '',
  realName: '',
  email: '',
  mobile: '',
  avatar: '',
  deptId: undefined,
  postId: undefined,
  remark: ''
})

const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}

const flatDepts = computed(() => {
  const result: Dept[] = []
  const walk = (items: Dept[]) => {
    items.forEach((item) => {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    })
  }
  walk(depts.value)
  return result
})

const deptMap = computed(() => new Map(flatDepts.value.map((item) => [String(item.id), item.name])))
const postMap = computed(() => new Map(posts.value.map((item) => [String(item.id), item.name])))

const loadData = async () => {
  loading.value = true
  try {
    const page = await getUsers(queryParams)
    tableData.value = page.records
    total.value = page.total
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  const [deptTree, postList, roleList] = await Promise.all([getDeptTree(), getAllPosts(), getAllRoles()])
  depts.value = deptTree
  posts.value = postList
  roles.value = roleList
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.deptId = undefined
  queryParams.postId = undefined
  queryParams.status = ''
  queryParams.pageNo = 1
  loadData()
}

const handleCreate = () => {
  dialogTitle.value = '新增用户'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: User) => {
  dialogTitle.value = '编辑用户'
  try {
    const user = await getUser(row.id)
    Object.assign(formData, {
      id: user.id,
      username: user.username,
      password: '',
      employeeNo: user.employeeNo || '',
      realName: user.realName,
      email: user.email || '',
      mobile: user.mobile || '',
      avatar: user.avatar || '',
      deptId: user.deptId,
      postId: user.postId,
      remark: user.remark || ''
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载用户详情失败')
  }
}

const handleAssignRoles = async (row: User) => {
  currentUserId.value = row.id
  currentUsername.value = row.username
  roleDialogVisible.value = true
  roleLoading.value = true
  selectedRoleIds.value = []
  try {
    if (roles.value.length === 0) {
      roles.value = await getAllRoles()
    }
    const assignment = await getAssignedUserRoles(row.id)
    selectedRoleIds.value = assignment.roleIds
  } catch (error) {
    ElMessage.error('加载用户角色失败')
  } finally {
    roleLoading.value = false
  }
}

const submitRoleAssignment = async () => {
  if (selectedRoleIds.value.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  roleSubmitLoading.value = true
  try {
    await assignUserRoles(currentUserId.value, selectedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('角色分配失败')
  } finally {
    roleSubmitLoading.value = false
  }
}

const applyEffectiveScope = (scope: {
  effectiveHasAllScope?: boolean
  effectiveDeptScoped?: boolean
  effectivePostScoped?: boolean
  effectiveSelfScoped?: boolean
  effectiveWarehouseIds?: string[]
}) => {
  effectiveScope.hasAllScope = !!scope.effectiveHasAllScope
  effectiveScope.deptScoped = !!scope.effectiveDeptScoped
  effectiveScope.postScoped = !!scope.effectivePostScoped
  effectiveScope.selfScoped = !!scope.effectiveSelfScoped
  effectiveScope.warehouseIds = scope.effectiveWarehouseIds || []
}

const resetDataScopeForm = () => {
  dataScopeForm.hasAllScope = false
  dataScopeForm.deptScoped = false
  dataScopeForm.postScoped = false
  dataScopeForm.selfScoped = false
  dataScopeForm.warehouseIds = []
  effectiveScope.hasAllScope = false
  effectiveScope.deptScoped = false
  effectiveScope.postScoped = false
  effectiveScope.selfScoped = false
  effectiveScope.warehouseIds = []
}

const handleAssignDataScope = async (row: User) => {
  currentUserId.value = row.id
  currentUsername.value = row.username
  dataScopeDialogVisible.value = true
  dataScopeLoading.value = true
  try {
    if (!warehouses.value.length) {
      const page = await getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      warehouses.value = page.records || []
    }
    const scope = await getAssignedUserDataScope(row.id)
    dataScopeForm.hasAllScope = !!scope.hasAllScope
    dataScopeForm.deptScoped = !!scope.deptScoped
    dataScopeForm.postScoped = !!scope.postScoped
    dataScopeForm.selfScoped = !!scope.selfScoped
    dataScopeForm.warehouseIds = scope.warehouseIds || []
    applyEffectiveScope(scope)
  } catch (error) {
    resetDataScopeForm()
    ElMessage.error('加载数据范围失败')
  } finally {
    dataScopeLoading.value = false
  }
}

const submitDataScopeAssignment = async () => {
  if (!currentUserId.value) return
  dataScopeSubmitLoading.value = true
  try {
    const saved = await assignUserDataScope(currentUserId.value, {
      hasAllScope: dataScopeForm.hasAllScope,
      deptScoped: dataScopeForm.deptScoped,
      postScoped: dataScopeForm.postScoped,
      selfScoped: dataScopeForm.selfScoped,
      warehouseIds: dataScopeForm.warehouseIds
    })
    applyEffectiveScope(saved)
    ElMessage.success('数据范围已保存（下次请求即生效）')
    dataScopeDialogVisible.value = false
  } catch (error) {
    ElMessage.error('保存数据范围失败')
  } finally {
    dataScopeSubmitLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = {
        username: formData.username,
        password: formData.password,
        employeeNo: formData.employeeNo,
        realName: formData.realName,
        email: formData.email || undefined,
        mobile: formData.mobile,
        avatar: formData.avatar || undefined,
        deptId: formData.deptId,
        postId: formData.postId,
        remark: formData.remark
      }
      if (formData.id) {
        await updateUser(formData.id, {
          employeeNo: payload.employeeNo,
          realName: payload.realName,
          email: payload.email,
          mobile: payload.mobile,
          avatar: payload.avatar,
          deptId: payload.deptId,
          postId: payload.postId,
          remark: payload.remark
        })
        ElMessage.success('更新成功')
      } else {
        await createUser(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error('保存用户失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDisable = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定停用用户"${row.username}"吗？`, '提示', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('停用成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

const handleEnable = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定启用用户"${row.username}"吗？`, '提示', { type: 'warning' })
    await enableUser(row.id)
    ElMessage.success('启用成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

const handleResetPassword = async (row: User) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', `重置 ${row.username} 密码`, {
      inputType: 'password',
      inputPlaceholder: '至少满足后端强密码规则',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await resetUserPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重置密码失败')
    }
  }
}

const resetForm = () => {
  formRef.value?.clearValidate()
  Object.assign(formData, {
    id: undefined,
    username: '',
    password: '',
    employeeNo: '',
    realName: '',
    email: '',
    mobile: '',
    avatar: '',
    deptId: undefined,
    postId: undefined,
    remark: ''
  })
}

const deptName = (id?: string) => (id ? deptMap.value.get(String(id)) || `部门 ${id}` : '-')
const postName = (id?: string) => (id ? postMap.value.get(String(id)) || `岗位 ${id}` : '-')
const roleNames = (items?: Role[]) => (items?.length ? items.map((role) => role.name || role.code).join('、') : '-')
const warehouseOptionLabel = (warehouse: Warehouse) => {
  const name = warehouse.name || warehouse.warehouseName || `仓库 ${warehouse.id}`
  const code = warehouse.code || warehouse.warehouseCode
  return code ? `${name}（${code}）` : name
}
const statusText = (status: string) => ({ ACTIVE: '启用', INACTIVE: '停用', LOCKED: '锁定' }[status] || status)
const statusType = (status: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'LOCKED') return 'warning'
  return 'info'
}

onMounted(async () => {
  try {
    await loadOptions()
  } catch (error) {
    console.error('加载用户筛选项失败:', error)
  }
  loadData()
})
</script>

<style scoped lang="scss">
.system-users-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .role-alert {
    margin-bottom: 14px;
  }

  .data-scope-form {
    margin-top: 8px;
  }

  .effective-scope-box {
    margin: 0 0 14px;
    padding: 10px 12px;
    border-radius: 6px;
    background: var(--el-fill-color-light);
  }

  .effective-scope-title {
    margin-bottom: 8px;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  .effective-scope-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .effective-scope-empty {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .role-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 14px;

    :deep(.el-checkbox) {
      height: 32px;
      margin-right: 0;
    }
  }
}
</style>
