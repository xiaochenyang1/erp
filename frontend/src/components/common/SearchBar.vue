<template>
  <div class="search-bar">
    <el-form
      ref="formRef"
      :model="modelValue"
      :inline="true"
      class="search-form"
    >
      <slot></slot>

      <el-form-item class="search-actions">
        <el-button type="primary" :icon="Search" @click="handleSearch">
          {{ texts.search }}
        </el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">
          {{ texts.reset }}
        </el-button>
        <el-button
          v-if="collapsible && hasExtraFields"
          link
          @click="collapsed = !collapsed"
          class="collapse-btn"
        >
          {{ collapsed ? texts.expand : texts.collapse }}
          <el-icon :class="{ 'is-reversed': !collapsed }">
            <ArrowDown />
          </el-icon>
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search, RefreshLeft, ArrowDown } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { useAppStore } from '@/store/modules/app'

interface Props {
  modelValue: Record<string, any>
  collapsible?: boolean
  hasExtraFields?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  collapsible: false,
  hasExtraFields: false
})

const appStore = useAppStore()
const texts = computed(() => appStore.locale === 'en-US'
  ? {
      search: 'Search',
      reset: 'Reset',
      expand: 'Expand',
      collapse: 'Collapse'
    }
  : {
      search: '搜索',
      reset: '重置',
      expand: '展开',
      collapse: '收起'
    })

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
  search: [values: Record<string, any>]
  reset: []
}>()

const formRef = ref<FormInstance>()
const collapsed = ref(true)

const handleSearch = () => {
  emit('search', props.modelValue)
}

const handleReset = () => {
  formRef.value?.resetFields()
  emit('reset')
}

defineExpose({
  collapsed
})
</script>

<style scoped>
.search-bar {
  background: #ffffff;
  border-radius: 10px;
  padding: 20px 24px 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  border: 1px solid transparent;
}

.search-bar:hover {
  border-color: #e9ecef;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.search-form {
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.search-form :deep(.el-form-item__label) {
  color: #495057;
  font-weight: 500;
  font-size: 13px;
}

.search-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  transition: all 0.2s ease;
  border: 1px solid #dee2e6;
}

.search-form :deep(.el-input__wrapper:hover) {
  border-color: #667eea;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.12);
}

.search-form :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.search-actions {
  margin-left: auto;
}

.search-actions :deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  letter-spacing: 0.3px;
  transition: all 0.2s ease;
}

.search-actions :deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  padding: 9px 20px;
}

.search-actions :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.collapse-btn {
  color: #667eea;
  font-weight: 500;
}

.collapse-btn :deep(.el-icon) {
  transition: transform 0.3s ease;
}

.collapse-btn :deep(.el-icon.is-reversed) {
  transform: rotate(180deg);
}
</style>
