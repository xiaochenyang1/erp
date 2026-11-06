<template>
  <el-select
    :model-value="modelValue == null ? undefined : String(modelValue)" :placeholder="placeholder"
    :clearable="clearable" :disabled="disabled" filterable remote :remote-method="search"
    :loading="loading" style="width: 100%" @visible-change="onVisible"
    @update:model-value="(value: string) => emit('update:modelValue', value || null)"
  >
    <el-option v-for="warehouse in choices" :key="warehouse.id" :value="String(warehouse.id)"
      :label="warehouse.warehouseName || warehouse.name || String(warehouse.id)" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getWarehouse, getWarehouses, type Warehouse } from '@/api/masterdata'

const props = defineProps<{ modelValue?: string | number | null; placeholder?: string; clearable?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()
const options = ref<Warehouse[]>([])
const selected = ref<Warehouse>()
const loading = ref(false)
let searchVersion = 0
const choices = computed(() => {
  if (!props.modelValue || options.value.some(w => String(w.id) === String(props.modelValue))) return options.value
  const fallback = selected.value?.id === String(props.modelValue) ? selected.value
    : { id: String(props.modelValue), warehouseName: String(props.modelValue) } as Warehouse
  return [fallback, ...options.value]
})
const search = async (keyword = '') => {
  const version = ++searchVersion
  loading.value = true
  try {
    const page = await getWarehouses({ pageNo: 1, pageSize: 50, status: 'ACTIVE', keyword })
    if (version === searchVersion) options.value = page.records
  } catch { /* request interceptor shows the error */ }
  finally { if (version === searchVersion) loading.value = false }
}
const onVisible = (visible: boolean) => { if (visible) void search() }
watch(() => props.modelValue, async (id) => {
  selected.value = undefined
  if (!id) return
  try {
    const warehouse = await getWarehouse(id)
    if (String(props.modelValue) === String(id)) selected.value = warehouse
  } catch { /* Keep the saved ID visible when the warehouse cannot be read. */ }
}, { immediate: true })
</script>
