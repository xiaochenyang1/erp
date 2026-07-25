<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('systemAttachments.businessType')">
          <el-input
            v-model="queryForm.businessType"
            :placeholder="$t('systemAttachments.businessTypePlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="$t('systemAttachments.businessId')">
          <el-input
            v-model="queryForm.businessId"
            :placeholder="$t('systemAttachments.businessIdPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="$t('systemAttachments.businessNo')">
          <el-input
            v-model="queryForm.businessNo"
            :placeholder="$t('systemAttachments.businessNoPlaceholder')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemAttachments.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemAttachments.reset') }}</el-button>
          <el-button v-permission="'system:attachment:manage'" type="primary" :icon="Upload" @click="handleOpenUpload">{{ $t('systemAttachments.upload') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>{{ $t('systemAttachments.title') }}</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="originalFilename" :label="$t('systemAttachments.filename')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="businessType" :label="$t('systemAttachments.businessType')" width="150" />
        <el-table-column prop="businessId" :label="$t('systemAttachments.businessId')" width="180" show-overflow-tooltip />
        <el-table-column prop="businessNo" :label="$t('systemAttachments.businessNo')" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.businessNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" :label="$t('systemAttachments.contentType')" width="160" show-overflow-tooltip />
        <el-table-column prop="fileSize" :label="$t('systemAttachments.size')" width="120" align="right">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" :label="$t('systemAttachments.uploadedBy')" width="120" />
        <el-table-column prop="createdTime" :label="$t('systemAttachments.uploadedAt')" width="190">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdTime) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('systemAttachments.actions')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Download" @click="handleDownload(row)">
              {{ $t('systemAttachments.download') }}
            </el-button>
            <el-button v-permission="'system:attachment:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)">
              {{ $t('systemAttachments.delete') }}
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
        @size-change="handlePageChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog
      v-model="uploadDialogVisible"
      :title="$t('systemAttachments.uploadTitle')"
      width="640px"
      @close="handleUploadDialogClose"
    >
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="100px">
        <el-form-item :label="$t('systemAttachments.businessType')" prop="businessType">
          <el-input v-model="uploadForm.businessType" :placeholder="$t('systemAttachments.businessTypePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemAttachments.businessId')" prop="businessId">
          <el-input
            v-model="uploadForm.businessId"
            :placeholder="$t('systemAttachments.businessIdPlaceholder')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('systemAttachments.businessNo')" prop="businessNo">
          <el-input v-model="uploadForm.businessNo" :placeholder="$t('systemAttachments.businessNoPlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemAttachments.file')" prop="file">
          <el-upload
            ref="uploaderRef"
            action="#"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button :icon="Paperclip">{{ $t('systemAttachments.selectFile') }}</el-button>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">{{ $t('systemAttachments.cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" @click="handleSubmitUpload">
          {{ $t('systemAttachments.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile, type UploadInstance } from 'element-plus'
import { Delete, Download, Paperclip, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  deleteAttachment,
  downloadAttachment,
  getAttachments,
  uploadAttachment,
  type Attachment,
  type AttachmentQuery
} from '@/api/attachment'
import { downloadBlob } from '@/utils/download'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

const { t } = useI18n()

const queryForm = reactive<AttachmentQuery>({
  businessType: '',
  businessId: '',
  businessNo: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loading = ref(false)
const tableData = ref<Attachment[]>([])

const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploaderRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)

const uploadForm = reactive({
  businessType: '',
  businessId: '',
  businessNo: '',
  file: null as File | null
})

const uploadRules = computed<FormRules>(() => ({
  businessType: [{ required: true, message: t('systemAttachments.validation.businessType'), trigger: 'blur' }],
  businessId: [{ required: true, message: t('systemAttachments.validation.businessId'), trigger: 'change' }],
  file: [
    {
      validator: (_rule, _value, callback) => {
        if (!selectedFile.value) {
          callback(new Error(t('systemAttachments.validation.file')))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}))

const buildQueryParams = (): AttachmentQuery => ({
  businessType: queryForm.businessType?.trim() || undefined,
  businessId: queryForm.businessId != null ? String(queryForm.businessId).trim() || undefined : undefined,
  businessNo: queryForm.businessNo?.trim() || undefined,
  pageNo: pagination.page,
  pageSize: pagination.size
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getAttachments(buildQueryParams())
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('Failed to load attachments:', error)
    ElMessage.error(t('systemAttachments.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleReset = () => {
  queryForm.businessType = ''
  queryForm.businessId = ''
  queryForm.businessNo = ''
  pagination.page = 1
  loadData()
}

const handleOpenUpload = () => {
  uploadForm.businessType = queryForm.businessType || 'SALES_ORDER'
  uploadForm.businessId = queryForm.businessId != null ? String(queryForm.businessId) : ''
  uploadForm.businessNo = queryForm.businessNo || ''
  selectedFile.value = null
  uploadForm.file = null
  uploadDialogVisible.value = true
}

const handleFileChange = (uploadFile: UploadFile) => {
  selectedFile.value = uploadFile.raw || null
  uploadForm.file = selectedFile.value
  uploadFormRef.value?.validateField('file')
}

const handleFileRemove = () => {
  selectedFile.value = null
  uploadForm.file = null
  uploadFormRef.value?.validateField('file')
}

const handleSubmitUpload = async () => {
  if (!uploadFormRef.value) return
  await uploadFormRef.value.validate(async (valid) => {
    const businessId = uploadForm.businessId.trim()
    if (!valid || !selectedFile.value || !businessId) return
    uploading.value = true
    try {
      await uploadAttachment(
        selectedFile.value,
        uploadForm.businessType,
        uploadForm.businessId.trim(),
        uploadForm.businessNo || undefined
      )
      ElMessage.success(t('systemAttachments.message.uploaded'))
      uploadDialogVisible.value = false
      queryForm.businessType = uploadForm.businessType
      queryForm.businessId = uploadForm.businessId.trim()
      queryForm.businessNo = uploadForm.businessNo
      pagination.page = 1
      loadData()
    } catch {
      ElMessage.error(t('systemAttachments.message.uploadFailed'))
    } finally {
      uploading.value = false
    }
  })
}

const handleUploadDialogClose = () => {
  uploadFormRef.value?.resetFields()
  uploaderRef.value?.clearFiles()
  selectedFile.value = null
  uploadForm.file = null
}

const handleDownload = async (row: Attachment) => {
  try {
    const blob = await downloadAttachment(row.id)
    downloadBlob(blob, row.originalFilename || `attachment-${row.id}`)
    ElMessage.success(t('systemAttachments.message.downloaded'))
  } catch {
    ElMessage.error(t('systemAttachments.message.downloadFailed'))
  }
}

const handleDelete = async (row: Attachment) => {
  try {
    await ElMessageBox.confirm(t('systemAttachments.message.deleteConfirm', { filename: row.originalFilename }), t('systemAttachments.message.prompt'), {
      confirmButtonText: t('systemAttachments.message.confirm'),
      cancelButtonText: t('systemAttachments.message.cancelled'),
      type: 'warning'
    })
    await deleteAttachment(row.id)
    ElMessage.success(t('systemAttachments.message.deleted'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemAttachments.message.deleteFailed'))
    }
  }
}

const formatFileSize = (size: number) => {
  if (size < 1024) return `${formatLocalizedNumber(size)} B`
  if (size < 1024 * 1024) {
    return `${formatLocalizedNumber(size / 1024, { maximumFractionDigits: 1 })} KB`
  }
  return `${formatLocalizedNumber(size / 1024 / 1024, { maximumFractionDigits: 1 })} MB`
}

onMounted(() => {
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
</style>
