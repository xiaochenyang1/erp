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
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Key, Plus, Refresh, Search, UserFilled } from '@element-plus/icons-vue'
import {
  assignUserDataScope,
  assignUserRoles,
  createUser,
  deleteUser,
  enableUser,
  getAllPosts,
  getAllRoles,
  getAssignedUserDataScope,
  getAssignedUserRoles,
  getDeptTree,
  getUser,
  getUsers,
  resetUserPassword,
  updateUser
} from '@/api/system'
import { getWarehouses } from '@/api/masterdata'
import { useSystemUserPresentation } from '@/composables/useSystemUserPresentation'
import { useSystemUserList } from '@/composables/useSystemUserList'
import { useSystemUserForm } from '@/composables/useSystemUserForm'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  depts,
  handleDisable,
  handleEnable,
  handlePageChange,
  handleQuery,
  handleReset,
  handleResetPassword,
  handleSizeChange,
  loadData,
  loadOptions,
  loading,
  posts,
  queryParams,
  roles,
  tableData,
  total
} = useSystemUserList(t, {
  getUsers,
  getDeptTree,
  getAllPosts,
  getAllRoles,
  deleteUser,
  enableUser,
  resetUserPassword,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  prompt: (message, title, options) => ElMessageBox.prompt(message, title, options as any),
  ...notify
})

const {
  deptName,
  flatDepts,
  postName,
  roleNames,
  statusText,
  statusType,
  warehouseOptionLabel
} = useSystemUserPresentation(t, { depts, posts })

const {
  currentUsername,
  dataScopeDialogVisible,
  dataScopeForm,
  dataScopeLoading,
  dataScopeSubmitLoading,
  dialogTitle,
  dialogVisible,
  effectiveScopeSummary,
  formData,
  handleAssignDataScope,
  handleAssignRoles,
  handleCreate,
  handleEdit,
  handleSubmit: saveUser,
  resetForm: resetFormState,
  roleDialogVisible,
  roleLoading,
  roleSubmitLoading,
  selectedRoleIds,
  submitDataScopeAssignment,
  submitLoading,
  submitRoleAssignment,
  warehouses
} = useSystemUserForm(t, {
  getUser,
  createUser,
  updateUser,
  getAllRoles,
  getAssignedUserRoles,
  assignUserRoles,
  getWarehouses,
  getAssignedUserDataScope,
  assignUserDataScope,
  roles,
  warehouseOptionLabel,
  onSubmitted: loadData,
  ...notify
})

const formRules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('systemUsers.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: t('systemUsers.initialPasswordPlaceholder'), trigger: 'blur' }],
  realName: [{ required: true, message: t('systemUsers.realNamePlaceholder'), trigger: 'blur' }],
  email: [{ type: 'email', message: t('systemUsers.validation.email'), trigger: 'blur' }]
}))

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await saveUser()
  })
}

const resetForm = () => {
  formRef.value?.clearValidate()
  resetFormState()
}

onMounted(async () => {
  await loadOptions()
  await loadData()
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
