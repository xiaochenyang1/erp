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
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? $t('systemPost.active') : $t('systemPost.inactive') }}
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
        @size-change="handleQuery"
        @current-change="handleQuery"
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
import { computed, ref, reactive, onMounted } from 'vue'
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
  type Dept,
  type Post
} from '@/api/system'

const { t } = useI18n()

// Search form
const queryForm = reactive({
  code: '',
  name: '',
  status: ''
})

// Table data
const loading = ref(false)
const tableData = ref<Post[]>([])
const deptOptions = ref<Dept[]>([])

// Pagination
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// Dialog state
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | undefined,
  deptId: undefined as string | undefined,
  code: '',
  name: '',
  orderNum: 0,
  status: 'ACTIVE',
  remark: ''
})

// Validation rules
const formRules = computed<FormRules>(() => ({
  code: [{ required: true, message: t('systemPost.validation.code'), trigger: 'blur' }],
  name: [{ required: true, message: t('systemPost.validation.name'), trigger: 'blur' }],
  deptId: [{ required: true, message: t('systemPost.validation.dept'), trigger: 'change' }]
}))

// Data loading
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getPosts(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('Failed to load posts:', error)
    ElMessage.error(t('systemPost.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const loadDeptOptions = async () => {
  deptOptions.value = await getDeptTree()
}

// Search
const handleQuery = () => {
  pagination.page = 1
  loadData()
}

// Reset
const handleReset = () => {
  queryForm.code = ''
  queryForm.name = ''
  queryForm.status = ''
  pagination.page = 1
  loadData()
}

// Create
const handleAdd = () => {
  dialogTitle.value = t('systemPost.create')
  dialogVisible.value = true
}

// Edit
const handleEdit = async (row: Post) => {
  dialogTitle.value = t('systemPost.editTitle')
  try {
    const res = await getPost(row.id)
    Object.assign(formData, {
      id: res.id,
      deptId: res.deptId,
      code: res.code,
      name: res.name,
      orderNum: res.orderNum,
      status: res.status,
      remark: res.remark
    })
    dialogVisible.value = true
  } catch {
    ElMessage.error(t('systemPost.message.detailLoadFailed'))
  }
}

// Disable
const handleDisable = async (row: Post) => {
  try {
    await ElMessageBox.confirm(t('systemPost.message.disableConfirm', { name: row.name }), t('systemPost.message.prompt'), {
      type: 'warning'
    })
    await deletePost(row.id)
    ElMessage.success(t('systemPost.message.disabled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemPost.message.disableFailed'))
    }
  }
}

// Enable
const handleEnable = async (row: Post) => {
  try {
    await ElMessageBox.confirm(t('systemPost.message.enableConfirm', { name: row.name }), t('systemPost.message.prompt'), {
      type: 'warning'
    })
    await enablePost(row.id)
    ElMessage.success(t('systemPost.message.enabled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemPost.message.enableFailed'))
    }
  }
}

// Submit
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updatePost(formData.id, formData)
        ElMessage.success(t('systemPost.message.updated'))
      } else {
        await createPost(formData)
        ElMessage.success(t('systemPost.message.created'))
      }
      dialogVisible.value = false
      loadData()
    } catch {
      ElMessage.error(t('systemPost.message.saveFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

// Dialog cleanup
const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    deptId: undefined,
    code: '',
    name: '',
    orderNum: 0,
    status: 'ACTIVE',
    remark: ''
  })
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
