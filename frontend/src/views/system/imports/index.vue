<template>
  <div class="app-container import-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('systemImports.importType')">
          <el-select
            v-model="queryForm.importType"
            :placeholder="$t('systemImports.allTypes')"
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
        <el-form-item :label="$t('systemImports.jobStatus')">
          <el-select
            v-model="queryForm.status"
            :placeholder="$t('systemImports.allStatuses')"
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
        <el-form-item :label="$t('systemImports.createdById')">
          <el-input
            v-model="queryForm.createdBy"
            :placeholder="$t('systemImports.createdByPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemImports.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemImports.reset') }}</el-button>
          <el-button v-permission="'import:init:manage'" :icon="Download" @click="handleDownloadTemplate">{{ $t('systemImports.downloadTemplate') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="upload-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('systemImports.previewTitle') }}</span>
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
          <el-button :icon="Upload">{{ $t('systemImports.selectCsv') }}</el-button>
        </el-upload>
        <el-button
          type="primary"
          :icon="Document"
          :loading="previewing"
          :disabled="!selectedFile"
          @click="handlePreview"
        >
          {{ $t('systemImports.uploadAndPreview') }}
        </el-button>
      </div>

      <div v-if="previewJob" class="preview-summary">
        <el-statistic :title="$t('systemImports.totalRows')" :value="previewJob.totalRows" />
        <el-statistic :title="$t('systemImports.validRows')" :value="previewJob.validRows" />
        <el-statistic :title="$t('systemImports.errorRows')" :value="previewJob.errorRows" />
        <el-statistic :title="$t('systemImports.committedRows')" :value="previewJob.committedRows" />
        <el-button
          type="success"
          :icon="Check"
          :loading="committingId === previewJob.jobId"
          :disabled="previewJob.status !== 'VALIDATED'"
          @click="handleCommit(previewJob)"
        >
          {{ $t('systemImports.commitImport') }}
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="jobsWithErrors > 0"
      class="error-tip-banner"
      type="error"
      show-icon
      :closable="false"
      :title="$t('systemImports.errorJobsNotice', { count: jobsWithErrors })"
      style="margin-bottom: 12px"
    />
    <el-card shadow="never" class="table-card">
      <template #header>
        <span>{{ $t('systemImports.jobsTitle') }}</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="jobId" :label="$t('systemImports.jobId')" width="180" show-overflow-tooltip />
        <el-table-column prop="importType" :label="$t('systemImports.importType')" min-width="180">
          <template #default="{ row }">
            {{ importTypeLabel(row.importType) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileName" :label="$t('systemImports.fileName')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" :label="$t('systemImports.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRows" :label="$t('systemImports.total')" width="90" align="right" />
        <el-table-column prop="validRows" :label="$t('systemImports.valid')" width="90" align="right" />
        <el-table-column prop="errorRows" :label="$t('systemImports.errors')" width="90" align="right" />
        <el-table-column prop="committedRows" :label="$t('systemImports.committed')" width="90" align="right" />
        <el-table-column prop="errorMessage" :label="$t('systemImports.errorMessage')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('systemImports.operations')" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewDetail(row)">
              {{ $t('systemImports.detail') }}
            </el-button>
            <el-button
              v-if="row.errorRows > 0"
              type="warning"
              link
              :icon="Warning"
              @click="handleExportErrors(row)"
            >
              {{ $t('systemImports.errorRows') }}
            </el-button>
            <el-button
              v-if="canCommit(row)"
              type="success"
              link
              :icon="Check"
              :loading="committingId === row.jobId"
              @click="handleCommit(row)"
            >
              {{ row.status === 'FAILED' ? $t('systemImports.retry') : $t('systemImports.commit') }}
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
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="detailVisible" :title="$t('systemImports.detailTitle')" width="1100px">
      <template v-if="detailJob">
        <el-descriptions :column="4" border class="detail-descriptions">
          <el-descriptions-item :label="$t('systemImports.jobId')">{{ detailJob.jobId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.importType')">
            {{ importTypeLabel(detailJob.importType) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.status')">
            <el-tag :type="statusTagType(detailJob.status)">
              {{ statusLabel(detailJob.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.fileName')">{{ detailJob.fileName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.total')">{{ detailJob.totalRows }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.valid')">{{ detailJob.validRows }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.errors')">{{ detailJob.errorRows }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemImports.committed')">{{ detailJob.committedRows }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="detailJob.rows" border stripe max-height="460">
          <el-table-column prop="rowNo" :label="$t('systemImports.rowNo')" width="80" align="right" />
          <el-table-column prop="valid" :label="$t('systemImports.validation')" width="90">
            <template #default="{ row }">
              <el-tag :type="row.valid ? 'success' : 'danger'">
                {{ row.valid ? $t('systemImports.passed') : $t('systemImports.failed') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('systemImports.rawData')" min-width="260">
            <template #default="{ row }">
              <pre class="json-cell">{{ formatJson(row.raw) }}</pre>
            </template>
          </el-table-column>
          <el-table-column :label="$t('systemImports.normalizedData')" min-width="260">
            <template #default="{ row }">
              <pre class="json-cell">{{ formatJson(row.normalized) }}</pre>
            </template>
          </el-table-column>
          <el-table-column :label="$t('systemImports.errors')" min-width="220">
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
        <el-button @click="detailVisible = false">{{ $t('systemImports.close') }}</el-button>
        <el-button
          v-if="detailJob && detailJob.errorRows > 0"
          :icon="Download"
          @click="handleExportErrors(detailJob)"
        >
          {{ $t('systemImports.exportErrorRows') }}
        </el-button>
        <el-button
          v-if="detailJob && canCommit(detailJob)"
          type="success"
          :icon="Check"
          :loading="committingId === detailJob.jobId"
          @click="handleCommit(detailJob)"
        >
          {{ detailJob.status === 'FAILED' ? $t('systemImports.retryCommit') : $t('systemImports.commitImport') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile, type UploadInstance } from 'element-plus'
import { Check, Document, Download, Refresh, Search, Upload, View } from '@element-plus/icons-vue'
import {
  commitImportJob,
  downloadImportTemplate,
  exportImportErrorRows,
  getImportJob,
  listImportJobs,
  previewImportJob
} from '@/api/imports'
import { downloadBlob } from '@/utils/download'
import { useSystemImportPresentation } from '@/composables/useSystemImportPresentation'
import { useSystemImportList } from '@/composables/useSystemImportList'

const { t } = useI18n()
const uploaderRef = ref<UploadInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  committingId,
  currentImportType,
  detailJob,
  detailVisible,
  handleCommit,
  handleDownloadTemplate,
  handleExportErrors,
  handleFileChange: setSelectedFile,
  handleFileRemove,
  handlePageChange,
  handlePreview,
  handleQuery,
  handleReset,
  handleSizeChange,
  handleViewDetail,
  jobsWithErrors,
  loadData,
  loading,
  pagination,
  previewJob,
  previewing,
  queryForm,
  selectedFile,
  tableData
} = useSystemImportList(t, {
  listJobs: listImportJobs,
  getJob: getImportJob,
  previewJob: previewImportJob,
  commitJob: commitImportJob,
  downloadTemplate: downloadImportTemplate,
  exportErrorRows: exportImportErrorRows,
  downloadBlob,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  canCommit,
  formatJson,
  importTypeLabel,
  importTypeOptions,
  statusLabel,
  statusOptions,
  statusTagType
} = useSystemImportPresentation(t)

const currentImportTypeLabel = computed(() => importTypeLabel(currentImportType.value))

const handleFileChange = (uploadFile: UploadFile) => {
  setSelectedFile(uploadFile.raw || null)
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
