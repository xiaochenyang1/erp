<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('inventorySerial.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('inventorySerial.keywordPlaceholder')"
            @keyup.enter="handleSearch"
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
          <el-button type="primary" @click="handleSearch">{{ t('inventorySerial.search') }}</el-button>
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
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  createInventorySerial,
  getInventorySerials,
  issueInventorySerial,
  scrapInventorySerial
} from '@/api/inventory'
import { getLocations, getProducts, getWarehouses } from '@/api/masterdata'
import { useInventorySerialForm } from '@/composables/useInventorySerialForm'
import { useInventorySerialList } from '@/composables/useInventorySerialList'
import { useInventorySerialPresentation } from '@/composables/useInventorySerialPresentation'

const { t } = useI18n()

const {
  handleQueryWarehouseChange,
  handleSearch,
  issue,
  loadData,
  loadOptions,
  loading,
  locations,
  locationsForQuery,
  query,
  rows,
  scrap,
  warehouses
} = useInventorySerialList(t, {
  getInventorySerials,
  issueInventorySerial,
  scrapInventorySerial,
  getWarehouses,
  getLocations,
  locationsForWarehouse: (warehouseId, all = locations.value) => {
    if (!warehouseId) return all
    return all.filter((location) => String(location.warehouseId) === String(warehouseId))
  },
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  locationLabel,
  locationsForWarehouse,
  productLabel,
  statusLabel,
  statusType,
  warehouseLabel
} = useInventorySerialPresentation(t, { warehouses, locations })

const {
  dialogVisible,
  form,
  handleFormWarehouseChange,
  locationsForForm,
  openCreate,
  products,
  save,
  saving
} = useInventorySerialForm(t, {
  createInventorySerial,
  getProducts,
  locationsForWarehouse,
  allLocations: locations,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSubmitted: async () => { await loadData() }
})

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
