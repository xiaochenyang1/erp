<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="关键字">
          <el-input
            v-model="queryForm.keyword"
            placeholder="工作中心编码/名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>工作中心</span>
          <el-button
            v-permission="'production:work-center:create'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >新增工作中心</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="workCenterCode" label="编码" width="180" />
        <el-table-column prop="workCenterName" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:work-center:update'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'production:work-center:enable'"
              type="success"
              link
              @click="handleEnable(row)"
            >启用</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:work-center:disable'"
              type="danger"
              link
              @click="handleDisable(row)"
            >停用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="handleQuery"
        @current-change="loadData"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="编码" prop="workCenterCode">
          <el-input
            v-model="formData.workCenterCode"
            placeholder="请输入工作中心编码"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="名称" prop="workCenterName">
          <el-input v-model="formData.workCenterName" placeholder="请输入工作中心名称" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import {
  getWorkCenters,
  createWorkCenter,
  updateWorkCenter,
  enableWorkCenter,
  disableWorkCenter,
  type WorkCenter
} from '@/api/production'

const queryForm = reactive({
  keyword: '',
  status: ''
})

const loading = ref(false)
const tableData = ref<WorkCenter[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | number | undefined,
  workCenterCode: '',
  workCenterName: '',
  remark: ''
})
const isEdit = computed(() => formData.id != null)

const formRules: FormRules = {
  workCenterCode: [{ required: true, message: '请输入工作中心编码', trigger: 'blur' }],
  workCenterName: [{ required: true, message: '请输入工作中心名称', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getWorkCenters({
      ...queryForm,
      pageNo: pagination.page,
      pageSize: pagination.size
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载工作中心失败:', error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.keyword = ''
  queryForm.status = ''
  pagination.page = 1
  loadData()
}

const handleAdd = () => {
  dialogTitle.value = '新增工作中心'
  dialogVisible.value = true
}

const handleEdit = (row: WorkCenter) => {
  dialogTitle.value = '编辑工作中心'
  Object.assign(formData, {
    id: row.id,
    workCenterCode: row.workCenterCode,
    workCenterName: row.workCenterName,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

const handleEnable = async (row: WorkCenter) => {
  try {
    await ElMessageBox.confirm(`确认启用工作中心「${row.workCenterName}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await enableWorkCenter(row.id)
    ElMessage.success('已启用')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

const handleDisable = async (row: WorkCenter) => {
  try {
    await ElMessageBox.confirm(`确认停用工作中心「${row.workCenterName}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await disableWorkCenter(row.id)
    ElMessage.success('已停用')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (formData.id != null) {
        await updateWorkCenter(formData.id, {
          workCenterName: formData.workCenterName,
          remark: formData.remark
        })
        ElMessage.success('更新成功')
      } else {
        await createWorkCenter({
          workCenterCode: formData.workCenterCode,
          workCenterName: formData.workCenterName,
          remark: formData.remark
        })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // 拦截器已提示
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    workCenterCode: '',
    workCenterName: '',
    remark: ''
  })
}

const getStatusLabel = (status: string) => ({ ACTIVE: '启用', DISABLED: '已停用' }[status] || status)
const getStatusType = (status: string) =>
  (({ ACTIVE: 'success', DISABLED: 'danger' } as Record<string, string>)[status] || 'info') as
    'primary' | 'success' | 'warning' | 'info' | 'danger'

onMounted(loadData)
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
