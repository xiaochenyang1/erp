<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('productionWorkCenter.keyword')">
          <el-input
            v-model="queryForm.keyword"
            :placeholder="t('productionWorkCenter.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="t('productionWorkCenter.statusLabel')">
          <el-select v-model="queryForm.status" :placeholder="t('productionWorkCenter.all')" clearable style="width: 120px">
            <el-option :label="t('productionWorkCenter.status.active')" value="ACTIVE" />
            <el-option :label="t('productionWorkCenter.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('productionWorkCenter.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('productionWorkCenter.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('productionWorkCenter.title') }}</span>
          <el-button
            v-permission="'production:work-center:create'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >{{ t('productionWorkCenter.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="workCenterCode" :label="t('productionWorkCenter.code')" width="180" />
        <el-table-column prop="workCenterName" :label="t('productionWorkCenter.name')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('productionWorkCenter.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('productionWorkCenter.remark')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('productionWorkCenter.actions')" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePrint(row)">{{ t('productionWorkCenter.print') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:work-center:update'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >{{ t('productionWorkCenter.edit') }}</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'production:work-center:enable'"
              type="success"
              link
              @click="handleEnable(row)"
            >{{ t('productionWorkCenter.enable') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:work-center:disable'"
              type="danger"
              link
              @click="handleDisable(row)"
            >{{ t('productionWorkCenter.disable') }}</el-button>
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
        <el-form-item :label="t('productionWorkCenter.code')" prop="workCenterCode">
          <el-input
            v-model="formData.workCenterCode"
            :placeholder="t('productionWorkCenter.codePlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('productionWorkCenter.name')" prop="workCenterName">
          <el-input v-model="formData.workCenterName" :placeholder="t('productionWorkCenter.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('productionWorkCenter.remark')" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" :placeholder="t('productionWorkCenter.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('productionWorkCenter.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ t('productionWorkCenter.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import {
  getWorkCenters,
  getWorkCenter,
  createWorkCenter,
  updateWorkCenter,
  enableWorkCenter,
  disableWorkCenter,
  type WorkCenter
} from '@/api/production'
import { printProductionWorkCenter } from '@/utils/bizPrint'

const { t } = useI18n()

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

const formRules = computed<FormRules>(() => ({
  workCenterCode: [{ required: true, message: t('productionWorkCenter.validation.code'), trigger: 'blur' }],
  workCenterName: [{ required: true, message: t('productionWorkCenter.validation.name'), trigger: 'blur' }]
}))

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
    console.error(t('productionWorkCenter.message.loadFailed'), error)
    ElMessage.error(t('productionWorkCenter.message.loadFailed'))
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
  dialogTitle.value = t('productionWorkCenter.dialog.create')
  dialogVisible.value = true
}

const handleEdit = (row: WorkCenter) => {
  dialogTitle.value = t('productionWorkCenter.dialog.edit')
  Object.assign(formData, {
    id: row.id,
    workCenterCode: row.workCenterCode,
    workCenterName: row.workCenterName,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

const handlePrint = async (row: WorkCenter) => {
  try {
    const detail = await getWorkCenter(row.id)
    printProductionWorkCenter(detail)
  } catch {
    ElMessage.error(t('productionWorkCenter.message.printLoadFailed'))
  }
}

const handleEnable = async (row: WorkCenter) => {
  try {
    await ElMessageBox.confirm(t('productionWorkCenter.message.enableConfirm', { name: row.workCenterName }), t('productionWorkCenter.message.prompt'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await enableWorkCenter(row.id)
    ElMessage.success(t('productionWorkCenter.message.enabled'))
    loadData()
  } catch {
    // 拦截器已提示
  }
}

const handleDisable = async (row: WorkCenter) => {
  try {
    await ElMessageBox.confirm(t('productionWorkCenter.message.disableConfirm', { name: row.workCenterName }), t('productionWorkCenter.message.prompt'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await disableWorkCenter(row.id)
    ElMessage.success(t('productionWorkCenter.message.disabled'))
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
        ElMessage.success(t('productionWorkCenter.message.updated'))
      } else {
        await createWorkCenter({
          workCenterCode: formData.workCenterCode,
          workCenterName: formData.workCenterName,
          remark: formData.remark
        })
        ElMessage.success(t('productionWorkCenter.message.created'))
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

const getStatusLabel = (status: string) => ({
  ACTIVE: t('productionWorkCenter.status.active'),
  DISABLED: t('productionWorkCenter.status.disabled')
}[status] || status)
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
