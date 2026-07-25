<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('warehouseLocation.warehouse')">
          <el-select v-model="query.warehouseId" clearable filterable style="width: 220px" :placeholder="t('warehouseLocation.selectWarehouse')">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseCode || ''} ${w.warehouseName || w.name || ''}`.trim()" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.keyword')">
          <el-input v-model="query.keyword" clearable :placeholder="t('warehouseLocation.keywordPlaceholder')" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.status')">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option :label="t('warehouseLocation.active')" value="ACTIVE" />
            <el-option :label="t('warehouseLocation.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ t('warehouseLocation.search') }}</el-button>
          <el-button v-permission="'masterdata:location:manage'" type="success" @click="openCreate">{{ t('warehouseLocation.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="warehouseName" :label="t('warehouseLocation.warehouse')" min-width="160" />
        <el-table-column prop="locationCode" :label="t('warehouseLocation.code')" width="120" />
        <el-table-column prop="locationName" :label="t('warehouseLocation.name')" min-width="160" />
        <el-table-column :label="t('warehouseLocation.default')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success">{{ t('warehouseLocation.yes') }}</el-tag>
            <span v-else>{{ t('warehouseLocation.no') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('warehouseLocation.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? t('warehouseLocation.active') : t('warehouseLocation.inactive') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('warehouseLocation.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('warehouseLocation.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'masterdata:location:manage'" link type="primary" @click="openEdit(row)">{{ t('warehouseLocation.edit') }}</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'masterdata:location:manage'" link type="warning" @click="toggle(row, false)">{{ t('warehouseLocation.disable') }}</el-button>
            <el-button v-else v-permission="'masterdata:location:manage'" link type="success" @click="toggle(row, true)">{{ t('warehouseLocation.enable') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('warehouseLocation.editTitle') : t('warehouseLocation.createTitle')" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item :label="t('warehouseLocation.warehouse')">
          <el-select v-model="form.warehouseId" :disabled="!!editingId" filterable style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseCode || ''} ${w.warehouseName || w.name || ''}`.trim()" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.code')">
          <el-input v-model="form.locationCode" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.name')">
          <el-input v-model="form.locationName" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.default')">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('warehouseLocation.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('warehouseLocation.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  createLocation,
  disableLocation,
  enableLocation,
  getLocations,
  getWarehouses,
  updateLocation,
  type Location,
  type Warehouse
} from '@/api/masterdata'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const rows = ref<Location[]>([])
const warehouses = ref<Warehouse[]>([])
const dialogVisible = ref(false)
const editingId = ref<string | number | null>(null)
const query = reactive({ warehouseId: '', status: '', keyword: '', pageNo: 1, pageSize: 50 })
const form = reactive({ warehouseId: '', locationCode: '', locationName: '', isDefault: false, remark: '' })

const loadOptions = async () => {
  const page = await getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
  warehouses.value = page.records || []
}

const loadData = async () => {
  loading.value = true
  try {
    const page = await getLocations({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      warehouseId: query.warehouseId || undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined
    })
    rows.value = page.records || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  form.warehouseId = query.warehouseId || (warehouses.value[0] ? String(warehouses.value[0].id) : '')
  form.locationCode = ''
  form.locationName = ''
  form.isDefault = false
  form.remark = ''
  dialogVisible.value = true
}

const openEdit = (row: Location) => {
  editingId.value = row.id
  form.warehouseId = String(row.warehouseId)
  form.locationCode = row.locationCode
  form.locationName = row.locationName
  form.isDefault = !!row.isDefault
  form.remark = row.remark || ''
  dialogVisible.value = true
}

const save = async () => {
  if (!form.warehouseId || !form.locationCode || !form.locationName) {
    ElMessage.warning(t('warehouseLocation.validation.required'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateLocation(editingId.value, {
        locationCode: form.locationCode,
        locationName: form.locationName,
        isDefault: form.isDefault,
        remark: form.remark || undefined
      })
      ElMessage.success(t('warehouseLocation.message.saved'))
    } else {
      await createLocation({
        warehouseId: form.warehouseId,
        locationCode: form.locationCode,
        locationName: form.locationName,
        isDefault: form.isDefault,
        remark: form.remark || undefined
      })
      ElMessage.success(t('warehouseLocation.message.created'))
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

const toggle = async (row: Location, enable: boolean) => {
  if (enable) await enableLocation(row.id)
  else await disableLocation(row.id)
  ElMessage.success(enable ? t('warehouseLocation.message.enabled') : t('warehouseLocation.message.disabled'))
  await loadData()
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
</style>
