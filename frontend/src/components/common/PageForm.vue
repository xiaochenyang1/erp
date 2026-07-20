<template>
  <div class="page-form">
    <el-form
      ref="formRef"
      :model="modelValue"
      :rules="rules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :size="size"
      :disabled="disabled"
      class="elegant-form"
    >
      <div class="form-grid" :style="{ gridTemplateColumns: gridColumns }">
        <slot></slot>
      </div>

      <div v-if="showFooter" class="form-footer">
        <slot name="footer">
          <el-button @click="handleCancel">{{ cancelText }}</el-button>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            {{ submitText }}
          </el-button>
        </slot>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  modelValue: Record<string, any>
  rules?: FormRules
  labelWidth?: string | number
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  disabled?: boolean
  loading?: boolean
  columns?: number
  showFooter?: boolean
  submitText?: string
  cancelText?: string
}

const props = withDefaults(defineProps<Props>(), {
  labelWidth: '100px',
  labelPosition: 'right',
  size: 'default',
  disabled: false,
  loading: false,
  columns: 2,
  showFooter: true,
  submitText: '提交',
  cancelText: '取消'
})

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
  submit: [values: Record<string, any>]
  cancel: []
}>()

const formRef = ref<FormInstance>()

const gridColumns = `repeat(${props.columns}, 1fr)`

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    emit('submit', props.modelValue)
  } catch (error) {
    console.error('Form validation failed:', error)
  }
}

const handleCancel = () => {
  emit('cancel')
}

const validate = () => {
  return formRef.value?.validate()
}

const resetFields = () => {
  formRef.value?.resetFields()
}

const clearValidate = () => {
  formRef.value?.clearValidate()
}

defineExpose({
  validate,
  resetFields,
  clearValidate
})
</script>

<style scoped>
.page-form {
  background: #ffffff;
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.elegant-form {
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

.form-grid {
  display: grid;
  gap: 24px 20px;
}

.elegant-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.elegant-form :deep(.el-form-item__label) {
  color: #495057;
  font-weight: 500;
  font-size: 13px;
  letter-spacing: 0.3px;
}

.elegant-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;
  border: 1px solid #e9ecef;
}

.elegant-form :deep(.el-input__wrapper:hover) {
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.elegant-form :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.elegant-form :deep(.el-textarea__inner) {
  border-radius: 8px;
  border: 1px solid #e9ecef;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;
}

.elegant-form :deep(.el-textarea__inner:hover) {
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.elegant-form :deep(.el-textarea__inner:focus) {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.elegant-form :deep(.el-select) {
  width: 100%;
}

.elegant-form :deep(.el-date-editor) {
  width: 100%;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e9ecef;
}

.form-footer :deep(.el-button) {
  border-radius: 8px;
  padding: 10px 24px;
  font-weight: 500;
  letter-spacing: 0.3px;
  transition: all 0.2s ease;
}

.form-footer :deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.form-footer :deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.form-footer :deep(.el-button--primary:active) {
  transform: translateY(0);
}
</style>
