<template>
  <div class="app-container">
    <!-- Search filters -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('systemPost.code')">
          <el-input v-model="queryForm.code" :placeholder="$t('systemPost.codePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('systemPost.name')">
          <el-input v-model="queryForm.name" :placeholder="$t('systemPost.namePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('systemPost.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('systemPost.selectStatus')" clearable style="width: 120px">
            <el-option :label="$t('systemPost.active')" value="ACTIVE" />
            <el-option :label="$t('systemPost.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemPost.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemPost.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Results table -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('systemPost.title') }}</span>
          <el-button v-permission="'system:post:create'" type="primary" :icon="Plus" @click="handleAdd">{{ $t('systemPost.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="code" :label="$t('systemPost.code')" width="150" />
        <el-table-column prop="name" :label="$t('systemPost.name')" width="200" />
        <el-table-column prop="orderNum" :label="$t('systemPost.order')" width="100" align="center" />
        <el-table-column prop="status" :label="$t('systemPost.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('systemPost.remark')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('systemPost.actions')" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:post:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">
              {{ $t('systemPost.edit') }}
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:post:disable'" type="danger" link :icon="Delete" @click="handleDisable(row)">
              {{ $t('systemPost.disable') }}
            </el-button>
            <el-button v-else v-permission="'system:post:enable'" type="success" link @click="handleEnable(row)">
              {{ $t('systemPost.enable') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- Create/edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item :label="$t('systemPost.code')" prop="code">
          <el-input v-model="formData.code" :placeholder="$t('systemPost.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemPost.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('systemPost.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemPost.dept')" prop="deptId">
          <el-tree-select
            v-model="formData.deptId"
            :data="deptOptions"
            :props="{ label: 'name', value: 'id' }"
            :placeholder="$t('systemPost.selectDept')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('systemPost.order')" prop="orderNum">
          <el-input-number v-model="formData.orderNum" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('systemPost.status')" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ $t('systemPost.active') }}</el-radio>
            <el-radio value="INACTIVE">{{ $t('systemPost.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('systemPost.remark')" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            :placeholder="$t('systemPost.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('systemPost.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('systemPost.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getDeptTree,
  getPosts,
  getPost,
  createPost,
  updatePost,
  deletePost,
  enablePost,
  type Post
} from '@/api/system'
import { useSystemPostPresentation } from '@/composables/useSystemPostPresentation'
import { useSystemPostList } from '@/composables/useSystemPostList'
import { useSystemPostForm } from '@/composables/useSystemPostForm'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
  deptOptions,
  handleDisable,
  handleEnable,
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loadDeptOptions,
  loading,
  pagination,
  queryForm,
  tableData
} = useSystemPostList(t, {
  getPosts,
  getDeptTree,
  deletePost,
  enablePost,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const { statusText, statusType } = useSystemPostPresentation(t)

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAdd,
  handleEdit: openEdit,
  handleSubmit: savePost,
  resetForm: resetFormState,
  submitLoading
} = useSystemPostForm(t, {
  getPost,
  createPost,
  updatePost,
  onSubmitted: loadData,
  ...notify
})

const formRules = computed<FormRules>(() => ({
  code: [{ required: true, message: t('systemPost.validation.code'), trigger: 'blur' }],
  name: [{ required: true, message: t('systemPost.validation.name'), trigger: 'blur' }],
  deptId: [{ required: true, message: t('systemPost.validation.dept'), trigger: 'change' }]
}))

const handleEdit = async (row: Post) => {
  await openEdit(row)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await savePost()
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetFormState()
}

onMounted(() => {
  loadDeptOptions()
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
