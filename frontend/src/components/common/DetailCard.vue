<template>
  <div class="detail-card">
    <div v-if="title || $slots.title" class="card-header">
      <slot name="title">
        <div class="card-title">
          <el-icon v-if="icon" class="title-icon">
            <component :is="icon" />
          </el-icon>
          <span>{{ title }}</span>
        </div>
      </slot>
      <div v-if="$slots.extra" class="card-extra">
        <slot name="extra"></slot>
      </div>
    </div>

    <div class="card-body" :class="{ 'no-padding': noPadding }">
      <slot></slot>
    </div>

    <div v-if="$slots.footer" class="card-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  icon?: any
  noPadding?: boolean
}

withDefaults(defineProps<Props>(), {
  noPadding: false
})
</script>

<style scoped>
.detail-card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

.detail-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border-color: #e9ecef;
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: linear-gradient(to bottom, #fafbfc, #ffffff);
  border-bottom: 1px solid #e9ecef;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.title-icon {
  font-size: 20px;
  color: #667eea;
  animation: iconPulse 2s ease-in-out infinite;
}

@keyframes iconPulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.card-extra {
  color: #6c757d;
  font-size: 14px;
}

.card-body {
  padding: 24px;
}

.card-body.no-padding {
  padding: 0;
}

.card-footer {
  padding: 16px 24px;
  background: #fafbfc;
  border-top: 1px solid #e9ecef;
}

/* 详情行样式 */
.card-body :deep(.detail-row) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px 32px;
  margin-bottom: 20px;
}

.card-body :deep(.detail-row:last-child) {
  margin-bottom: 0;
}

.card-body :deep(.detail-item) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-body :deep(.detail-label) {
  color: #6c757d;
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-body :deep(.detail-value) {
  color: #2c3e50;
  font-size: 14px;
  font-weight: 500;
}

.card-body :deep(.detail-value.empty) {
  color: #adb5bd;
  font-style: italic;
}

/* 分组标题 */
.card-body :deep(.detail-section) {
  margin-bottom: 24px;
}

.card-body :deep(.detail-section:last-child) {
  margin-bottom: 0;
}

.card-body :deep(.section-title) {
  font-size: 14px;
  font-weight: 600;
  color: #495057;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #e9ecef;
  letter-spacing: 0.3px;
}
</style>
