<template>
  <div class="system-users-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('systemUsers.keyword')">
          <el-input
            v-model="queryParams.keyword"
            :placeholder="$t('systemUsers.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.department')">
          <el-select v-model="queryParams.deptId" :placeholder="$t('systemUsers.selectDepartment')" clearable style="width: 180px">
            <el-option v-for="dept in flatDepts" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemUsers.post')">
          <el-select v-model="queryParams.postId" :placeholder="$t('systemUsers.selectPost')" clearable style="width: 180px">
            <el-option v-for="post in posts" :key="post.id" :label="post.name" :value="post.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemUsers.status')">
          <el-select v-model="queryParams.status" :placeholder="$t('systemUsers.selectStatus')" clearable style="width: 140px">
            <el-option :label="$t('systemUsers.active')" value="ACTIVE" />
            <el-option :label="$t('systemUsers.inactive')" value="INACTIVE" />
            <el-option :label="$t('systemUsers.locked')" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemUsers.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemUsers.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('systemUsers.title') }}</span>
          <el-button v-permission="'system:user:create'" type="primary" :icon="Plus" @click="handleCreate">{{ $t('systemUsers.addUser') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="username" :label="$t('systemUsers.username')" min-width="140" fixed />
        <el-table-column prop="employeeNo" :label="$t('systemUsers.employeeNo')" width="120" />
        <el-table-column prop="realName" :label="$t('systemUsers.realName')" width="140" />
        <el-table-column prop="email" :label="$t('systemUsers.email')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="mobile" :label="$t('systemUsers.mobile')" width="140" />
        <el-table-column prop="deptId" :label="$t('systemUsers.department')" width="160">
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="postId" :label="$t('systemUsers.post')" width="160">
          <template #default="{ row }">{{ postName(row.postId) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('systemUsers.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('systemUsers.roles')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ roleNames(row.roles) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('systemUsers.remark')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('systemUsers.operations')" width="460" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:user:update'" link type="primary" :icon="Edit" @click="handleEdit(row)">{{ $t('systemUsers.edit') }}</el-button>
            <el-button v-permission="'system:user:assign-role'" link type="primary" :icon="UserFilled" @click="handleAssignRoles(row)">{{ $t('systemUsers.roles') }}</el-button>
            <el-button v-permission="'system:user:assign-data-scope'" link type="primary" @click="handleAssignDataScope(row)">{{ $t('systemUsers.dataScope') }}</el-button>
            <el-button v-permission="'system:user:reset-password'" link type="warning" :icon="Key" @click="handleResetPassword(row)">{{ $t('systemUsers.resetPassword') }}</el-button>
            <el-button
              v-if="row.status !== 'INACTIVE'"
              v-permission="'system:user:disable'"
              link
              type="danger"
              :icon="Delete"
              @click="handleDisable(row)"
            >
              {{ $t('systemUsers.disable') }}
            </el-button>
            <el-button
              v-else
              v-permission="'system:user:enable'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              {{ $t('systemUsers.enable') }}
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
        <el-form-item :label="$t('systemUsers.username')" prop="username">
          <el-input v-model="formData.username" :placeholder="$t('systemUsers.usernamePlaceholder')" :disabled="Boolean(formData.id)" />
        </el-form-item>
        <el-form-item v-if="!formData.id" :label="$t('systemUsers.initialPassword')" prop="password">
          <el-input v-model="formData.password" type="password" show-password :placeholder="$t('systemUsers.initialPasswordPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.employeeNo')">
          <el-input v-model="formData.employeeNo" :placeholder="$t('systemUsers.employeeNoPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.realName')" prop="realName">
          <el-input v-model="formData.realName" :placeholder="$t('systemUsers.realNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.email')" prop="email">
          <el-input v-model="formData.email" :placeholder="$t('systemUsers.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.mobile')">
          <el-input v-model="formData.mobile" :placeholder="$t('systemUsers.mobilePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.avatarUrl')">
          <el-input v-model="formData.avatar" :placeholder="$t('systemUsers.avatarPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemUsers.department')">
          <el-select v-model="formData.deptId" :placeholder="$t('systemUsers.selectDepartment')" clearable style="width: 100%">
            <el-option v-for="dept in flatDepts" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemUsers.post')">
          <el-select v-model="formData.postId" :placeholder="$t('systemUsers.selectPost')" clearable style="width: 100%">
            <el-option v-for="post in posts" :key="post.id" :label="post.name" :value="post.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemUsers.remark')">
          <el-input v-model="formData.remark" type="textarea" :rows="3" :placeholder="$t('systemUsers.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('systemUsers.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('systemUsers.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" :title="$t('systemUsers.assignRoles')" width="560px" destroy-on-close>
      <div v-loading="roleLoading">
        <el-alert
          class="role-alert"
          :title="$t('systemUsers.currentUser', { username: currentUsername || '-' })"
          type="info"
          show-icon
          :closable="false"
        />
        <el-checkbox-group v-model="selectedRoleIds" class="role-list">
          <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">
            {{ $t('systemUsers.roleOption', { name: role.name, code: role.code }) }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">{{ $t('systemUsers.cancel') }}</el-button>
        <el-button type="primary" :loading="roleSubmitLoading" @click="submitRoleAssignment">{{ $t('systemUsers.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataScopeDialogVisible" :title="$t('systemUsers.dataScopeDialog')" width="640px" destroy-on-close>
      <div v-loading="dataScopeLoading">
        <el-alert
          class="role-alert"
          :title="$t('systemUsers.currentUser', { username: currentUsername || '-' })"
          type="info"
          show-icon
          :closable="false"
          :description="$t('systemUsers.dataScopeDescription')"
        />
        <div class="effective-scope-box">
          <div class="effective-scope-title">{{ $t('systemUsers.effectiveScope') }}</div>
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
            <span v-else class="effective-scope-empty">{{ $t('systemUsers.noEffectiveScope') }}</span>
          </div>
        </div>
        <el-form label-width="110px" class="data-scope-form">
          <el-form-item :label="$t('systemUsers.allData')">
            <el-switch v-model="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemUsers.ownDepartment')">
            <el-switch v-model="dataScopeForm.deptScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemUsers.ownPost')">
            <el-switch v-model="dataScopeForm.postScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemUsers.selfOnly')">
            <el-switch v-model="dataScopeForm.selfScoped" :disabled="dataScopeForm.hasAllScope" />
          </el-form-item>
          <el-form-item :label="$t('systemUsers.warehouseScope')">
            <el-select
              v-model="dataScopeForm.warehouseIds"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              :disabled="dataScopeForm.hasAllScope"
              :placeholder="$t('systemUsers.selectWarehouses')"
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
        <el-button @click="dataScopeDialogVisible = false">{{ $t('systemUsers.cancel') }}</el-button>
        <el-button type="primary" :loading="dataScopeSubmitLoading" @click="submitDataScopeAssignment">{{ $t('systemUsers.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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
    return { tags: [t('systemUsers.allData')] }
  }
  const tags: string[] = []
  if (effectiveScope.deptScoped) tags.push(t('systemUsers.ownDepartment'))
  if (effectiveScope.postScoped) tags.push(t('systemUsers.ownPost'))
  if (effectiveScope.selfScoped) tags.push(t('systemUsers.selfOnly'))
  for (const id of effectiveScope.warehouseIds) {
    const warehouse = warehouses.value.find((item) => String(item.id) === String(id))
    tags.push(warehouse
      ? t('systemUsers.warehouseScopeTag', { warehouse: warehouseOptionLabel(warehouse) })
      : t('systemUsers.warehouseFallback', { id }))
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

const formRules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('systemUsers.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: t('systemUsers.initialPasswordPlaceholder'), trigger: 'blur' }],
  realName: [{ required: true, message: t('systemUsers.realNamePlaceholder'), trigger: 'blur' }],
  email: [{ type: 'email', message: t('systemUsers.validation.email'), trigger: 'blur' }]
}))

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
    console.error(t('systemUsers.message.loadFailed'), error)
    ElMessage.error(t('systemUsers.message.loadFailed'))
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
  dialogTitle.value = t('systemUsers.dialog.add')
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: User) => {
  dialogTitle.value = t('systemUsers.dialog.edit')
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
    ElMessage.error(t('systemUsers.message.detailLoadFailed'))
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
    ElMessage.error(t('systemUsers.message.rolesLoadFailed'))
  } finally {
    roleLoading.value = false
  }
}

const submitRoleAssignment = async () => {
  if (selectedRoleIds.value.length === 0) {
    ElMessage.warning(t('systemUsers.message.roleRequired'))
    return
  }
  roleSubmitLoading.value = true
  try {
    await assignUserRoles(currentUserId.value, selectedRoleIds.value)
    ElMessage.success(t('systemUsers.message.rolesSaved'))
    roleDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('systemUsers.message.rolesSaveFailed'))
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
    ElMessage.error(t('systemUsers.message.dataScopeLoadFailed'))
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
    ElMessage.success(t('systemUsers.message.dataScopeSaved'))
    dataScopeDialogVisible.value = false
  } catch (error) {
    ElMessage.error(t('systemUsers.message.dataScopeSaveFailed'))
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
        ElMessage.success(t('systemUsers.message.updateSuccess'))
      } else {
        await createUser(payload)
        ElMessage.success(t('systemUsers.message.createSuccess'))
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(t('systemUsers.message.saveFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDisable = async (row: User) => {
  try {
    await ElMessageBox.confirm(t('systemUsers.message.disableConfirm', { username: row.username }), t('systemUsers.prompt'), { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success(t('systemUsers.message.disableSuccess'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemUsers.message.disableFailed'))
    }
  }
}

const handleEnable = async (row: User) => {
  try {
    await ElMessageBox.confirm(t('systemUsers.message.enableConfirm', { username: row.username }), t('systemUsers.prompt'), { type: 'warning' })
    await enableUser(row.id)
    ElMessage.success(t('systemUsers.message.enableSuccess'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemUsers.message.enableFailed'))
    }
  }
}

const handleResetPassword = async (row: User) => {
  try {
    const { value } = await ElMessageBox.prompt(t('systemUsers.message.newPassword'), t('systemUsers.message.resetPasswordTitle', { username: row.username }), {
      inputType: 'password',
      inputPlaceholder: t('systemUsers.message.passwordRule'),
      confirmButtonText: t('systemUsers.confirm'),
      cancelButtonText: t('systemUsers.cancel')
    })
    await resetUserPassword(row.id, value)
    ElMessage.success(t('systemUsers.message.passwordReset'))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemUsers.message.passwordResetFailed'))
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

const deptName = (id?: string) => (id ? deptMap.value.get(String(id)) || t('systemUsers.departmentFallback', { id }) : '-')
const postName = (id?: string) => (id ? postMap.value.get(String(id)) || t('systemUsers.postFallback', { id }) : '-')
const roleNames = (items?: Role[]) => (items?.length
  ? items.map((role) => role.name || role.code).join(t('systemUsers.listSeparator'))
  : '-')
const warehouseOptionLabel = (warehouse: Warehouse) => {
  const name = warehouse.name || warehouse.warehouseName || t('systemUsers.warehouseFallback', { id: warehouse.id })
  const code = warehouse.code || warehouse.warehouseCode
  return code ? t('systemUsers.warehouseOption', { name, code }) : name
}
const statusText = (status: string) => ({
  ACTIVE: t('systemUsers.active'),
  INACTIVE: t('systemUsers.inactive'),
  LOCKED: t('systemUsers.locked')
}[status] || status)
const statusType = (status: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'LOCKED') return 'warning'
  return 'info'
}

onMounted(async () => {
  try {
    await loadOptions()
  } catch (error) {
    console.error(t('systemUsers.message.optionsLoadFailed'), error)
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
