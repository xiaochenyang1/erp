<template>
  <div class="document-attachments">
    <div class="document-attachments__header">
      <div class="document-attachments__title">
        <b>{{ title || t('documentAttachment.title') }}</b>
        <el-tag v-if="required" size="small" type="warning">{{ t('documentAttachment.requiredTag') }}</el-tag>
        <span v-if="canView" class="document-attachments__count">{{ t('documentAttachment.count', { count }) }}</span>
      </div>
      <div v-if="canUpload" class="document-attachments__actions">
        <input ref="fileInput" type="file" hidden @change="handleFileChange" />
        <el-button
          size="small"
          type="primary"
          :disabled="!hasTarget || uploading"
          :loading="uploading"
          @click="fileInput?.click()"
        >
          {{ t('documentAttachment.upload') }}
        </el-button>
      </div>
    </div>
    <div v-if="required" class="document-attachments__hint">{{ t('documentAttachment.requiredHint') }}</div>
    <el-alert
      v-if="!canView"
      :title="t('documentAttachment.viewDenied')"
      type="info"
      :closable="false"
      show-icon
    />
    <el-table
      v-else
      v-loading="loading"
      :data="rows"
      size="small"
      border
      :empty-text="t('documentAttachment.empty')"
    >
      <el-table-column prop="originalFilename" :label="t('documentAttachment.filename')" min-width="220" show-overflow-tooltip />
      <el-table-column :label="t('documentAttachment.fileSize')" width="110" align="right">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="createdTime" :label="t('documentAttachment.uploadedAt')" width="180" />
      <el-table-column :label="t('documentAttachment.actions')" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">{{ t('documentAttachment.download') }}</el-button>
          <el-button
            v-if="canDelete"
            link
            type="danger"
            :loading="removingId === row.id"
            @click="remove(row)"
          >
            {{ t('documentAttachment.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { DEFAULT_MAX_ATTACHMENT_BYTES, useDocumentAttachments } from '@/composables/useDocumentAttachments'
import { useUserStore } from '@/store/modules/user'
import { downloadBlob } from '@/utils/download'

/**
 * 单据附件面板。
 *
 * 附件走通用接口 /system/attachments，权限码与单据自身权限无关：
 * 列表/下载需要 system:attachment:view，上传需要 system:attachment:manage，
 * 删除需要 system:attachment:delete。无查看权限时直接不发请求，避免必然 403 的调用。
 */
const props = withDefaults(defineProps<{
  businessType: string
  businessId?: string | number | null
  businessNo?: string
  title?: string
  required?: boolean
  readonly?: boolean
  maxFileSizeBytes?: number
  viewPermission?: string
  uploadPermission?: string
  deletePermission?: string
}>(), {
  businessId: null,
  businessNo: undefined,
  title: undefined,
  required: false,
  readonly: false,
  maxFileSizeBytes: undefined,
  viewPermission: 'system:attachment:view',
  uploadPermission: 'system:attachment:manage',
  deletePermission: 'system:attachment:delete'
})

const emit = defineEmits<{ (event: 'change', count: number): void }>()

const { t } = useI18n()
const userStore = useUserStore()
const fileInput = ref<HTMLInputElement>()

const canView = computed(() => userStore.hasPermission(props.viewPermission))
const canUpload = computed(() => !props.readonly && userStore.hasPermission(props.uploadPermission))
const canDelete = computed(() => !props.readonly && userStore.hasPermission(props.deletePermission))
const hasTarget = computed(() => Boolean(props.businessType) && props.businessId != null && props.businessId !== '')

const { clear, count, download, load, loading, remove, removingId, rows, upload, uploading } = useDocumentAttachments({
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options),
  downloadBlob,
  onError: (messageKey, params) => ElMessage.error(t(messageKey, params || {})),
  onSuccess: (messageKey) => ElMessage.success(t(messageKey)),
  t: (key, params) => t(key, params || {}),
  target: () => (hasTarget.value
    ? { businessType: props.businessType, businessId: props.businessId as string | number, businessNo: props.businessNo }
    : null),
  maxFileSizeBytes: () => props.maxFileSizeBytes ?? DEFAULT_MAX_ATTACHMENT_BYTES
})

const formatFileSize = (value?: number) => {
  const bytes = Number(value || 0)
  if (bytes <= 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    await upload(file)
  }
  input.value = ''
}

const reload = async () => {
  if (canView.value && hasTarget.value) {
    return load()
  }
  clear()
  return false
}

watch(
  () => [props.businessType, props.businessId, canView.value] as const,
  () => { void reload() },
  { immediate: true }
)

watch(count, (value) => emit('change', value))

defineExpose({ reload })
</script>

<style scoped>
.document-attachments { margin-top: 20px; }
.document-attachments__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.document-attachments__title { display: flex; align-items: center; gap: 8px; }
.document-attachments__count { font-size: 12px; color: #909399; }
.document-attachments__hint { font-size: 12px; color: #e6a23c; margin-bottom: 8px; }
</style>
