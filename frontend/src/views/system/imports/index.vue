<template>
  <div class="app-container import-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="导入类型">
          <el-select
            v-model="queryForm.importType"
            placeholder="全部类型"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="item in importTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创建人ID">
          <el-input
            v-model="queryForm.createdBy"
            placeholder="请输入创建人ID"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'import:init:manage'" :icon="Download" @click="handleDownloadTemplate">下载模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="upload-card">
      <template #header>
        <div class="card-header">
          <span>导入预览</span>
          <el-tag size="small" type="info">{{ currentImportTypeLabel }}</el-tag>
        </div>
      </template>

      <div class="upload-row">
        <el-upload
          ref="uploaderRef"
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".csv,text/csv"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-button :icon="Upload">选择CSV文件</el-button>
        </el-upload>
        <el-button
          type="primary"
          :icon="Document"
          :loading="previewing"
          :disabled="!selectedFile"
          @click="handlePreview"
        >
          上传并预览
        </el-button>
      </div>

      <div v-if="previewJob" class="preview-summary">
        <el-statistic title="总行数" :value="previewJob.totalRows" />
        <el-statistic title="有效行" :value="previewJob.validRows" />
        <el-statistic title="错误行" :value="previewJob.errorRows" />
        <el-statistic title="已提交" :value="previewJob.committedRows" />
        <el-button
          type="success"
          :icon="Check"
          :loading="committingId === previewJob.jobId"
          :disabled="previewJob.status !== 'VALIDATED'"
          @click="handleCommit(previewJob)"
        >
          提交导入
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="jobsWithErrors > 0"
      class="error-tip-banner"
      type="error"
      show-icon
      :closable="false"
      :title="`当前列表有 ${jobsWithErrors} 个任务含错误行，可在详情中查看并导出错误 CSV`"
      style="margin-bottom: 12px"
    />
    <el-card shadow="never" class="table-card">
      <template #header>
        <span>导入任务</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="jobId" label="任务ID" width="180" show-overflow-tooltip />
        <el-table-column prop="importType" label="导入类型" min-width="180">
          <template #default="{ row }">
            {{ importTypeLabel(row.importType) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRows" label="总行" width="90" align="right" />
        <el-table-column prop="validRows" label="有效" width="90" align="right" />
        <el-table-column prop="errorRows" label="错误" width="90" align="right" />
        <el-table-column prop="committedRows" label="提交" width="90" align="right" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="row.errorRows > 0"
              type="warning"
              link
              :icon="Warning"
              @click="handleExportErrors(row)"
            >
              错误行
            </el-button>
            <el-button
              v-if="canCommit(row)"
              type="success"
              link
              :icon="Check"
              :loading="committingId === row.jobId"
              @click="handleCommit(row)"
            >
              {{ row.status === 'FAILED' ? '重试' : '提交' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="导入任务详情" width="1100px">
      <template v-if="detailJob">
        <el-descriptions :column="4" border class="detail-descriptions">
          <el-descriptions-item label="任务ID">{{ detailJob.jobId }}</el-descriptions-item>
          <el-descriptions-item label="导入类型">
            {{ importTypeLabel(detailJob.importType) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailJob.status)">
              {{ statusLabel(detailJob.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件名">{{ detailJob.fileName }}</el-descriptions-item>
          <el-descriptions-item label="总行">{{ detailJob.totalRows }}</el-descriptions-item>
          <el-descriptions-item label="有效">{{ detailJob.validRows }}</el-descriptions-item>
          <el-descriptions-item label="错误">{{ detailJob.errorRows }}</el-descriptions-item>
          <el-descriptions-item label="提交">{{ detailJob.committedRows }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="detailJob.rows" border stripe max-height="460">
          <el-table-column prop="rowNo" label="行号" width="80" align="right" />
          <el-table-column prop="valid" label="校验" width="90">
            <template #default="{ row }">
              <el-tag :type="row.valid ? 'success' : 'danger'">
                {{ row.valid ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="原始数据" min-width="260">
            <template #default="{ row }">
              <pre class="json-cell">{{ formatJson(row.raw) }}</pre>
            </template>
          </el-table-column>
          <el-table-column label="归一化数据" min-width="260">
            <template #default="{ row }">
              <pre class="json-cell">{{ formatJson(row.normalized) }}</pre>
            </template>
          </el-table-column>
          <el-table-column label="错误" min-width="220">
            <template #default="{ row }">
              <div v-if="row.errors.length" class="error-list">
                <div v-for="item in row.errors" :key="`${row.rowNo}-${item.column}-${item.message}`">
                  {{ item.column }}：{{ item.message }}
                </div>
              </div>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailJob && detailJob.errorRows > 0"
          :icon="Download"
          @click="handleExportErrors(detailJob)"
        >
          导出错误行
        </el-button>
        <el-button
          v-if="detailJob && canCommit(detailJob)"
          type="success"
          :icon="Check"
          :loading="committingId === detailJob.jobId"
          @click="handleCommit(detailJob)"
        >
          {{ detailJob.status === 'FAILED' ? '重试提交' : '提交导入' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadInstance } from 'element-plus'
import { Check, Document, Download, Refresh, Search, Upload, View, Warning } from '@element-plus/icons-vue'
import {
  commitImportJob,
  downloadImportTemplate,
  exportImportErrorRows,
  getImportJob,
  listImportJobs,
  previewImportJob,
  type ImportJob,
  type ImportJobQuery,
  type ImportJobStatus,
  type ImportType
} from '@/api/imports'
import { downloadBlob } from '@/utils/download'

const importTypeOptions: Array<{ label: string; value: ImportType }> = [
  { label: '产品', value: 'PRODUCT' },
  { label: '客户', value: 'CUSTOMER' },
  { label: '供应商', value: 'SUPPLIER' },
  { label: '仓库', value: 'WAREHOUSE' },
  { label: '期初库存', value: 'OPENING_INVENTORY' },
  { label: '期初应收', value: 'OPENING_RECEIVABLE' },
  { label: '期初应付', value: 'OPENING_PAYABLE' },
  { label: '期初科目余额', value: 'OPENING_ACCOUNT_BALANCE' }
]

const statusOptions: Array<{ label: string; value: ImportJobStatus }> = [
  { label: '校验通过', value: 'VALIDATED' },
  { label: '校验失败', value: 'INVALID' },
  { label: '提交中', value: 'COMMITTING' },
  { label: '已提交', value: 'COMMITTED' },
  { label: '提交失败', value: 'FAILED' }
]

const queryForm = reactive<ImportJobQuery>({
  importType: '',
  status: '',
  createdBy: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loading = ref(false)
const previewing = ref(false)
const tableData = ref<ImportJob[]>([])
const jobsWithErrors = computed(() => (tableData.value || []).filter((j) => Number(j.errorRows || 0) > 0).length)
const previewJob = ref<ImportJob>()
const detailJob = ref<ImportJob>()
const detailVisible = ref(false)
const committingId = ref('')
const selectedFile = ref<File | null>(null)
const uploaderRef = ref<UploadInstance>()

const currentImportType = computed<ImportType>(() => {
  return (queryForm.importType || importTypeOptions[0].value) as ImportType
})

const currentImportTypeLabel = computed(() => importTypeLabel(currentImportType.value))

const buildQueryParams = (): ImportJobQuery => ({
  importType: queryForm.importType || undefined,
  status: queryForm.status || undefined,
  createdBy: queryForm.createdBy ? String(queryForm.createdBy).trim() : undefined,
  pageNo: pagination.page,
  pageSize: pagination.size
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await listImportJobs(buildQueryParams())
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.importType = ''
  queryForm.status = ''
  queryForm.createdBy = ''
  pagination.page = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleDownloadTemplate = async () => {
  const type = currentImportType.value
  const blob = await downloadImportTemplate(type)
  downloadBlob(blob, `${type.toLowerCase()}-template.csv`)
  ElMessage.success('模板下载已开始')
}

const handleFileChange = (uploadFile: UploadFile) => {
  selectedFile.value = uploadFile.raw || null
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const handlePreview = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择CSV文件')
    return
  }

  previewing.value = true
  try {
    const job = await previewImportJob(currentImportType.value, selectedFile.value)
    previewJob.value = job
    detailJob.value = job
    detailVisible.value = true
    queryForm.importType = job.importType
    pagination.page = 1
    await loadData()
    ElMessage.success(job.status === 'VALIDATED' ? '预览校验通过' : '预览完成，请处理错误行')
  } finally {
    previewing.value = false
  }
}

const handleViewDetail = async (row: ImportJob) => {
  const job = await getImportJob(row.jobId)
  detailJob.value = job
  detailVisible.value = true
}

const handleExportErrors = async (row: ImportJob) => {
  const blob = await exportImportErrorRows(row.jobId)
  downloadBlob(blob, `import-job-${row.jobId}-errors.csv`)
  ElMessage.success('错误行导出已开始')
}

const canCommit = (row: ImportJob) => {
  if (row.status === 'VALIDATED') return true
  return row.status === 'FAILED' && row.validRows > 0 && row.errorRows === 0 && row.committedRows === 0
}

const handleCommit = async (row: ImportJob) => {
  const actionText = row.status === 'FAILED' ? '重试提交' : '提交'
  await ElMessageBox.confirm(`确定${actionText}导入任务「${row.jobId}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  committingId.value = row.jobId
  try {
    const job = await commitImportJob(row.jobId)
    previewJob.value = previewJob.value?.jobId === job.jobId ? job : previewJob.value
    detailJob.value = detailJob.value?.jobId === job.jobId ? job : detailJob.value
    await loadData()
    ElMessage.success('提交成功')
  } finally {
    committingId.value = ''
  }
}

const importTypeLabel = (value: string) => {
  return importTypeOptions.find((item) => item.value === value)?.label || value
}

const statusLabel = (value: string) => {
  return statusOptions.find((item) => item.value === value)?.label || value
}

const statusTagType = (value: string) => {
  if (value === 'VALIDATED' || value === 'COMMITTED') return 'success'
  if (value === 'INVALID' || value === 'FAILED') return 'danger'
  if (value === 'COMMITTING') return 'warning'
  return 'info'
}

const formatJson = (value: unknown) => {
  return JSON.stringify(value || {}, null, 2)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.import-page {
  padding: 20px;
}

.search-card,
.upload-card,
.table-card {
  margin-bottom: 20px;
}

.card-header,
.upload-row,
.preview-summary {
  display: flex;
  align-items: center;
}

.card-header {
  justify-content: space-between;
}

.upload-row {
  gap: 12px;
}

.preview-summary {
  gap: 32px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}

.pager {
  margin-top: 20px;
  justify-content: flex-end;
}

.detail-descriptions {
  margin-bottom: 16px;
}

.json-cell {
  max-height: 160px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}

.error-list {
  display: grid;
  gap: 4px;
  color: var(--el-color-danger);
}
</style>
