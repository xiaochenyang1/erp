<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="业务类型">
          <el-input
            v-model="queryForm.businessType"
            placeholder="如 SALES_ORDER"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input
            v-model="queryForm.businessId"
            placeholder="请输入业务ID"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="业务编号">
          <el-input
            v-model="queryForm.businessNo"
            placeholder="请输入业务编号"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'system:attachment:manage'" type="primary" :icon="Upload" @click="handleOpenUpload">上传</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>附件中心</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="originalFilename" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="businessType" label="业务类型" width="150" />
        <el-table-column prop="businessId" label="业务ID" width="180" show-overflow-tooltip />
        <el-table-column prop="businessNo" label="业务编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.businessNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="文件类型" width="160" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小" width="120" align="right">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="上传人ID" width="120" />
        <el-table-column prop="createdTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Download" @click="handleDownload(row)">
              下载
            </el-button>
            <el-button v-permission="'system:attachment:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)">
              删除
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
      title="上传附件"
      width="640px"
      @close="handleUploadDialogClose"
    >
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="100px">
        <el-form-item label="业务类型" prop="businessType">
          <el-input v-model="uploadForm.businessType" placeholder="如 SALES_ORDER" />
        </el-form-item>
        <el-form-item label="业务ID" prop="businessId">
          <el-input
            v-model="uploadForm.businessId"
            placeholder="请输入业务ID"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="业务编号" prop="businessNo">
          <el-input v-model="uploadForm.businessNo" placeholder="请输入业务编号" clearable />
        </el-form-item>
        <el-form-item label="文件" prop="file">
          <el-upload
            ref="uploaderRef"
            action="#"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button :icon="Paperclip">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleSubmitUpload">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
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

const uploadRules: FormRules = {
  businessType: [{ required: true, message: '请输入业务类型', trigger: 'blur' }],
  businessId: [{ required: true, message: '请输入业务ID', trigger: 'change' }],
  file: [
    {
      validator: (_rule, _value, callback) => {
        if (!selectedFile.value) {
          callback(new Error('请选择文件'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}

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
    console.error('加载附件失败:', error)
    ElMessage.error('加载数据失败')
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
      ElMessage.success('上传成功')
      uploadDialogVisible.value = false
      queryForm.businessType = uploadForm.businessType
      queryForm.businessId = uploadForm.businessId.trim()
      queryForm.businessNo = uploadForm.businessNo
      pagination.page = 1
      loadData()
    } catch (error) {
      ElMessage.error('上传失败')
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
    ElMessage.success('下载成功')
  } catch (error) {
    ElMessage.error('下载失败')
  }
}

const handleDelete = async (row: Attachment) => {
  try {
    await ElMessageBox.confirm(`确定删除附件「${row.originalFilename}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAttachment(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
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
