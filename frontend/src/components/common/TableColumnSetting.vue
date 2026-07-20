<template>
  <el-popover placement="bottom-end" :width="240" trigger="click">
    <template #reference>
      <el-button :icon="Setting" circle title="列设置" />
    </template>
    <div class="column-setting">
      <div class="column-setting__header">
        <span>列设置</span>
        <el-button link type="primary" size="small" @click="handleReset">重置</el-button>
      </div>
      <el-scrollbar max-height="320px">
        <el-checkbox-group v-model="visibleProps" class="column-setting__list">
          <el-checkbox
            v-for="col in hideableColumns"
            :key="col.prop"
            :value="col.prop"
            class="column-setting__item"
          >
            {{ col.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import type { TableColumnOption } from '@/composables/useTablePreference'

const props = defineProps<{
  columns: TableColumnOption[]
  /** 来自 useTablePreference 的 columnVisible reactive 映射 */
  modelValue: Record<string, boolean>
}>()

const emit = defineEmits<{
  (e: 'reset'): void
  (e: 'update:modelValue', value: Record<string, boolean>): void
}>()

const hideableColumns = computed(() =>
  props.columns.filter((col) => col.hideable !== false)
)

// el-checkbox-group 用勾选的 prop 数组双向绑定到 columnVisible 映射
const visibleProps = computed<string[]>({
  get() {
    return hideableColumns.value
      .filter((col) => props.modelValue[col.prop] !== false)
      .map((col) => col.prop)
  },
  set(next) {
    const nextSet = new Set(next)
    const nextValue = { ...props.modelValue }
    for (const col of hideableColumns.value) {
      nextValue[col.prop] = nextSet.has(col.prop)
    }
    emit('update:modelValue', nextValue)
  }
})

const handleReset = () => emit('reset')
</script>

<style scoped>
.column-setting__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
}

.column-setting__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.column-setting__item {
  height: 28px;
}
</style>
