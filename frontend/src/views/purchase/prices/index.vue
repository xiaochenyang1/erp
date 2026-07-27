<template>
  <div class="purchase-price-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('purchasePrice.keyword')">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="$t('purchasePrice.keywordPlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.supplier')">
          <el-select
            v-model="searchForm.supplierId"
            clearable
            filterable
            :placeholder="$t('purchasePrice.allWithGeneral')"
            style="width: 200px"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="supplier.supplierName || supplier.name"
              :value="String(supplier.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.status')">
          <el-select v-model="searchForm.status" clearable :placeholder="$t('purchasePrice.all')" style="width: 140px">
            <el-option :label="$t('purchasePrice.active')" value="ACTIVE" />
            <el-option :label="$t('purchasePrice.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('purchasePrice.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('purchasePrice.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'purchase:price:manage'" type="primary" :icon="Plus" @click="handleCreate">
          {{ $t('purchasePrice.create') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column :label="$t('purchasePrice.scope')" min-width="160">
          <template #default="{ row }">
            <el-tag :type="scopeTagType(row.supplierId)" size="small">{{ scopeLabel(row.supplierId) }}</el-tag>
            <div class="sub">{{ scopeDetail(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchasePrice.product')" min-width="180">
          <template #default="{ row }">
            <div>{{ row.productCode }} {{ row.productName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="listPrice" :label="$t('purchasePrice.listPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.listPrice) }}</template>
        </el-table-column>
        <el-table-column prop="maxPrice" :label="$t('purchasePrice.maxPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.maxPrice) }}</template>
        </el-table-column>
        <el-table-column :label="$t('purchasePrice.effectivePeriod')" min-width="220">
          <template #default="{ row }">
            {{ formatEffectivePeriod(row.effectiveFrom, row.effectiveTo) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchasePrice.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('purchasePrice.remark')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('purchasePrice.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('purchasePrice.print') }}
            </el-button>
            <el-button v-permission="'purchase:price:manage'" link type="primary" @click="handleEdit(row)">
              {{ $t('purchasePrice.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'purchase:price:manage'"
              link
              type="warning"
              @click="handleDisable(row)"
            >
              {{ $t('purchasePrice.disable') }}
            </el-button>
            <el-button
              v-else
              v-permission="'purchase:price:manage'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              {{ $t('purchasePrice.enable') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="searchForm.pageNo"
        :page-size="searchForm.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? $t('purchasePrice.editTitle') : $t('purchasePrice.createTitle')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$t('purchasePrice.scope')">
          <el-radio-group v-model="scopeType">
            <el-radio-button value="PRODUCT">{{ $t('purchasePrice.productGeneral') }}</el-radio-button>
            <el-radio-button value="SUPPLIER">{{ $t('purchasePrice.supplierSpecific') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeType === 'SUPPLIER'" :label="$t('purchasePrice.supplier')" prop="supplierId">
          <el-select v-model="form.supplierId" filterable :placeholder="$t('purchasePrice.selectSupplier')" style="width: 100%">
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="supplier.supplierName || supplier.name"
              :value="String(supplier.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.product')" prop="productId">
          <el-select v-model="form.productId" filterable :placeholder="$t('purchasePrice.selectProduct')" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.productCode || ''} ${product.productName || ''}`.trim()"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.listPrice')" prop="listPrice">
          <el-input-number v-model="form.listPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.maxPrice')" prop="maxPrice">
          <el-input-number v-model="form.maxPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          <div class="form-tip">{{ $t('purchasePrice.maxPriceTip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.effectiveFrom')" prop="effectiveFrom">
          <el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.effectiveTo')">
          <el-date-picker
            v-model="form.effectiveTo"
            type="date"
            value-format="YYYY-MM-DD"
            clearable
            :placeholder="$t('purchasePrice.noExpiry')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('purchasePrice.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">{{ $t('purchasePrice.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createPurchasePrice,
  disablePurchasePrice,
  enablePurchasePrice,
  getPurchasePrice,
  getPurchasePrices,
  updatePurchasePrice
} from '@/api/purchase'
import { getProducts, getSuppliers } from '@/api/masterdata'
import { usePurchasePriceList } from '@/composables/usePurchasePriceList'
import { usePurchasePricePresentation } from '@/composables/usePurchasePricePresentation'
import { printPurchasePrice } from '@/utils/bizPrint'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  dialogVisible,
  editingId,
  form,
  handleCreate,
  handleDisable,
  handleEdit,
  handleEnable,
  handlePageChange,
  handlePrint,
  handleReset,
  handleSearch,
  handleSizeChange,
  loadData,
  loadOptions,
  loading,
  products,
  scopeType,
  searchForm,
  submitSave,
  submitting,
  suppliers,
  tableData,
  total
} = usePurchasePriceList(t, {
  getPurchasePrices,
  getPurchasePrice,
  createPurchasePrice,
  updatePurchasePrice,
  enablePurchasePrice,
  disablePurchasePrice,
  getSuppliers,
  getProducts,
  printPurchasePrice,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})

const {
  formatEffectivePeriod,
  formatMoney,
  scopeDetail,
  scopeLabel,
  scopeTagType,
  statusLabel,
  statusTagType
} = usePurchasePricePresentation(t)

const rules = computed<FormRules>(() => ({
  supplierId: [{ required: true, message: t('purchasePrice.validation.supplier'), trigger: 'change' }],
  productId: [{ required: true, message: t('purchasePrice.validation.product'), trigger: 'change' }],
  listPrice: [{ required: true, message: t('purchasePrice.validation.listPrice'), trigger: 'blur' }],
  maxPrice: [{ required: true, message: t('purchasePrice.validation.maxPrice'), trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: t('purchasePrice.validation.effectiveFrom'), trigger: 'change' }]
}))

const confirmSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await submitSave()
  })
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.purchase-price-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.search-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 16px;
}
.toolbar {
  margin-bottom: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>
