<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('inventorySerial.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('inventorySerial.keywordPlaceholder')"
            @keyup.enter="loadData"
          />
        </el-form-item>
        <el-form-item :label="t('inventorySerial.warehouse')">
          <el-select
            v-model="query.warehouseId"
            clearable
            filterable
            style="width: 180px"
            :placeholder="t('inventorySerial.selectWarehouse')"
            @change="handleQueryWarehouseChange"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse.id)"
              :value="String(warehouse.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventorySerial.location')">
          <el-select
            v-model="query.locationId"
            clearable
            filterable
            style="width: 180px"
            :placeholder="t('inventorySerial.selectLocation')"
          >
            <el-option
              v-for="location in locationsForQuery"
              :key="location.id"
              :label="`${location.locationCode} ${location.locationName}`"
              :value="String(location.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventorySerial.status')">
          <el-select
            v-model="query.status"
            clearable
            style="width: 140px"
            :placeholder="t('inventorySerial.selectStatus')"
          >
            <el-option :label="t('inventorySerial.statusValue.inStock')" value="IN_STOCK" />
            <el-option :label="t('inventorySerial.statusValue.issued')" value="ISSUED" />
            <el-option :label="t('inventorySerial.statusValue.scrapped')" value="SCRAPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ t('inventorySerial.search') }}</el-button>
          <el-button v-permission="'inventory:serial:manage'" type="success" @click="openCreate">
            {{ t('inventorySerial.create') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="serialNo" :label="t('inventorySerial.serialNo')" min-width="160" />
        <el-table-column prop="productCode" :label="t('inventorySerial.productCode')" width="120" />
        <el-table-column prop="productName" :label="t('inventorySerial.productName')" min-width="140" />
        <el-table-column :label="t('inventorySerial.warehouse')" min-width="140">
          <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column :label="t('inventorySerial.location')" min-width="140">
          <template #default="{ row }">{{ locationLabel(row.locationId) }}</template>
        </el-table-column>
        <el-table-column :label="t('inventorySerial.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inboundBizNo" :label="t('inventorySerial.inboundBizNo')" min-width="140" />
        <el-table-column prop="outboundBizNo" :label="t('inventorySerial.outboundBizNo')" min-width="140" />
        <el-table-column :label="t('inventorySerial.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'IN_STOCK'"
              v-permission="'inventory:serial:manage'"
              link
              type="primary"
              @click="issue(row)"
            >
              {{ t('inventorySerial.issue') }}
            </el-button>
            <el-button
              v-if="row.status === 'IN_STOCK'"
              v-permission="'inventory:serial:manage'"
              link
              type="warning"
              @click="scrap(row)"
            >
              {{ t('inventorySerial.scrap') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t('inventorySerial.createTitle')" width="560px">
      <el-form label-width="110px">
        <el-form-item :label="t('inventorySerial.product')">
          <el-select
            v-model="form.productId"
            filterable
            style="width: 100%"
            :placeholder="t('inventorySerial.selectProduct')"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="productLabel(product)"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventorySerial.warehouse')">
          <el-select
            v-model="form.warehouseId"
            clearable
            filterable
            style="width: 100%"
            :placeholder="t('inventorySerial.selectWarehouse')"
            @change="handleFormWarehouseChange"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse.id)"
              :value="String(warehouse.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventorySerial.location')">
          <el-select
            v-model="form.locationId"
            clearable
            filterable
            style="width: 100%"
            :placeholder="t('inventorySerial.selectLocation')"
          >
            <el-option
              v-for="location in locationsForForm"
              :key="location.id"
              :label="`${location.locationCode} ${location.locationName}`"
              :value="String(location.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventorySerial.serialNo')">
          <el-input v-model="form.serialNo" :placeholder="t('inventorySerial.serialNoPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('inventorySerial.inboundBizNo')">
          <el-input v-model="form.inboundBizNo" :placeholder="t('inventorySerial.inboundBizNoPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('inventorySerial.remark')">
          <el-input v-model="form.remark" :placeholder="t('inventorySerial.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('inventorySerial.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('inventorySerial.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  createInventorySerial,
  getInventorySerials,
  issueInventorySerial,
  scrapInventorySerial,
  type InventorySerial
} from '@/api/inventory'
import { getLocations, getProducts, getWarehouses, type Location, type Product, type Warehouse } from '@/api/masterdata'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const rows = ref<InventorySerial[]>([])
const products = ref<Product[]>([])
const warehouses = ref<Warehouse[]>([])
const locations = ref<Location[]>([])
const dialogVisible = ref(false)
const query = reactive({
  keyword: '',
  status: '',
  warehouseId: '',
  locationId: '',
  pageNo: 1,
  pageSize: 50
})
const form = reactive({
  productId: '',
  warehouseId: '',
  locationId: '',
  serialNo: '',
  inboundBizNo: '',
  remark: ''
})

const locationsForQuery = computed(() => {
  if (!query.warehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(query.warehouseId))
})
const locationsForForm = computed(() => {
  if (!form.warehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(form.warehouseId))
})

const warehouseLabel = (warehouseId?: string | number | null) => {
  if (warehouseId == null || warehouseId === '') return '-'
  const warehouse = warehouses.value.find((item) => String(item.id) === String(warehouseId))
  return warehouse ? (warehouse.name || warehouse.warehouseName || String(warehouseId)) : String(warehouseId)
}

const locationLabel = (locationId?: string | number | null) => {
  if (locationId == null || locationId === '') return '-'
  const location = locations.value.find((item) => String(item.id) === String(locationId))
  return location ? `${location.locationCode} ${location.locationName}` : String(locationId)
}

const productLabel = (product: Product) => {
  const code = product.code || product.productCode || ''
  const name = product.name || product.productName || ''
  return [code, name].filter(Boolean).join(' ') || String(product.id)
}

const statusLabel = (status?: string) => {
  const map: Record<string, string> = {
    IN_STOCK: t('inventorySerial.statusValue.inStock'),
    ISSUED: t('inventorySerial.statusValue.issued'),
    SCRAPPED: t('inventorySerial.statusValue.scrapped')
  }
  return map[String(status || '')] || status || '-'
}

const statusType = (status?: string) => {
  return ({
    IN_STOCK: 'success',
    ISSUED: 'info',
    SCRAPPED: 'warning'
  }[String(status || '')] || 'info') as 'success' | 'info' | 'warning'
}

const loadOptions = async () => {
  try {
    const [warehousePage, locationPage] = await Promise.all([
      getWarehouses({ pageNo: 1, pageSize: 500, status: 'ACTIVE' }),
      getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
    ])
    warehouses.value = warehousePage.records || []
    locations.value = locationPage.records || []
  } catch {
    ElMessage.error(t('inventorySerial.message.optionsLoadFailed'))
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const page = await getInventorySerials({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      warehouseId: query.warehouseId || undefined,
      locationId: query.locationId || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    rows.value = page.records || []
  } catch {
    ElMessage.error(t('inventorySerial.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleQueryWarehouseChange = () => {
  query.locationId = ''
}

const handleFormWarehouseChange = () => {
  form.locationId = ''
}

const openCreate = async () => {
  try {
    const page = await getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    products.value = (page.records || []).filter((product) => Boolean(product.serialControlled))
  } catch {
    products.value = []
    ElMessage.error(t('inventorySerial.message.productsLoadFailed'))
  }
  form.productId = ''
  form.warehouseId = ''
  form.locationId = ''
  form.serialNo = ''
  form.inboundBizNo = ''
  form.remark = ''
  dialogVisible.value = true
}

const save = async () => {
  if (!form.productId || !form.serialNo) {
    ElMessage.warning(t('inventorySerial.validation.required'))
    return
  }
  saving.value = true
  try {
    await createInventorySerial({
      productId: form.productId,
      warehouseId: form.warehouseId || undefined,
      locationId: form.locationId || undefined,
      serialNo: form.serialNo,
      inboundBizNo: form.inboundBizNo || undefined,
      remark: form.remark || undefined
    })
    ElMessage.success(t('inventorySerial.message.created'))
    dialogVisible.value = false
    await loadData()
  } catch {
    ElMessage.error(t('inventorySerial.message.createFailed'))
  } finally {
    saving.value = false
  }
}

const issue = async (row: InventorySerial) => {
  try {
    await issueInventorySerial(row.id)
    ElMessage.success(t('inventorySerial.message.issued'))
    await loadData()
  } catch {
    ElMessage.error(t('inventorySerial.message.issueFailed'))
  }
}

const scrap = async (row: InventorySerial) => {
  try {
    await scrapInventorySerial(row.id)
    ElMessage.success(t('inventorySerial.message.scrapped'))
    await loadData()
  } catch {
    ElMessage.error(t('inventorySerial.message.scrapFailed'))
  }
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
