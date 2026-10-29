<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('documentAttachment.title')"
    width="820px"
    class="document-attachment-dialog"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @closed="reset"
  >
    <div class="attachment-toolbar">
      <div class="document-info">
        <el-tag size="small" type="info">{{ typeLabel }}</el-tag>
        <span class="document-no">{{ businessNo || businessId }}</span>
      </div>
      <div class="toolbar-actions">
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">
          {{ t('documentAttachment.refresh') }}
        </el-button>
        <template v-if="canUpload">
          <input ref="fileInput" type="file" hidden @change="handleFileChange" />
          <el-button size="small" type="primary" :icon="Upload" :loading="uploading" @click="fileInput?.click()">
            {{ t('documentAttachment.upload') }}
          </el-button>
        </template>
      </div>
    </div>

    <el-alert
      :title="canUpload ? t('documentAttachment.hint') : t('documentAttachment.readOnlyHint')"
      :type="canUpload ? 'info' : 'warning'"
      show-icon
      :closable="false"
      class="attachment-alert"
    />

    <el-alert
      v-if="truncated"
      :title="t('documentAttachment.truncatedHint', { count: attachments.length })"
      type="warning"
      show-icon
      :closable="false"
      class="attachment-alert"
    />

    <el-table v-loading="loading" :data="attachments" border stripe :empty-text="t('documentAttachment.empty')">
      <el-table-column prop="originalFilename" :label="t('documentAttachment.filename')" min-width="240" show-overflow-tooltip />
      <el-table-column :label="t('documentAttachment.fileSize')" width="120" align="right">
        <template #default="{ row }">{{ formatFileSize(Number(row.fileSize || 0)) }}</template>
      </el-table-column>
      <el-table-column :label="t('documentAttachment.uploadedAt')" width="180">
        <template #default="{ row }">{{ row.createdTime ? formatLocalizedDateTime(row.createdTime) : '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('documentAttachment.uploadedBy')" width="120">
        <template #default="{ row }">{{ row.createdBy || '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('documentAttachment.actions')" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">{{ t('documentAttachment.download') }}</el-button>
          <el-button v-if="canDelete" link type="danger" @click="remove(row)">{{ t('documentAttachment.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('documentAttachment.close') }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  deleteAttachment,
  downloadAttachment,
  getAttachments,
  uploadAttachment
} from '@/api/attachment'
import { useAttachmentPresentation } from '@/composables/useAttachmentPresentation'
import { useDocumentAttachments } from '@/composables/useDocumentAttachments'
import { downloadBlob } from '@/utils/download'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    businessType: string
    businessId: string
    businessNo?: string
    canUpload?: boolean
    canDelete?: boolean
  }>(),
  { businessNo: '', canUpload: false, canDelete: false }
)

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const { t, te } = useI18n()

const fileInput = ref<HTMLInputElement>()

/** Falls back to the raw enum name if a new gated type has no label yet. */
const typeLabel = computed(() => {
  const key = `documentAttachment.type.${props.businessType}`
  return te(key) ? t(key) : props.businessType
})

const { formatFileSize } = useAttachmentPresentation({ formatNumber: formatLocalizedNumber })

const {
  attachments,
  download,
  load,
  loading,
  open,
  remove,
  reset,
  truncated,
  upload,
  uploading
} = useDocumentAttachments(t, {
  getAttachments,
  uploadAttachment,
  downloadAttachment,
  deleteAttachment,
  downloadBlob,
  confirm: (message, title) => ElMessageBox.confirm(message, title, { type: 'warning' }),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  try {
    await upload(file)
  } finally {
    input.value = ''
  }
}

watch(
  () => [props.modelValue, props.businessType, props.businessId, props.businessNo] as const,
  ([visible, businessType, businessId, businessNo]) => {
    if (visible && businessId) {
      void open({ businessType, businessId, businessNo: businessNo || undefined })
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.attachment-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.document-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.document-no {
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.attachment-alert {
  margin-bottom: 12px;
}
</style>
