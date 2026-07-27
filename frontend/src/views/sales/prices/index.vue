<template>
  <div class="sales-price-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('salesPrice.keyword')">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="$t('salesPrice.keywordPlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.customer')">
          <el-select
            v-model="searchForm.customerId"
            clearable
            filterable
            :placeholder="$t('salesPrice.allWithGeneral')"
            style="width: 200px"
          >
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.customerName || customer.name"
              :value="String(customer.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.status')">
          <el-select v-model="searchForm.status" clearable :placeholder="$t('salesPrice.all')" style="width: 140px">
            <el-option :label="$t('salesPrice.active')" value="ACTIVE" />
            <el-option :label="$t('salesPrice.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('salesPrice.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('salesPrice.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'sales:price:manage'" type="primary" :icon="Plus" @click="handleCreate">
          {{ $t('salesPrice.create') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column :label="$t('salesPrice.scope')" min-width="160">
          <template #default="{ row }">
            <el-tag :type="scopeTagType(row.customerId)" size="small">{{ scopeLabel(row.customerId) }}</el-tag>
            <div class="sub">{{ scopeDetail(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('salesPrice.product')" min-width="180">
          <template #default="{ row }">
            <div>{{ row.productCode }} {{ row.productName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="listPrice" :label="$t('salesPrice.listPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.listPrice) }}</template>
        </el-table-column>
        <el-table-column prop="minPrice" :label="$t('salesPrice.minPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.minPrice) }}</template>
        </el-table-column>
        <el-table-column :label="$t('salesPrice.effectivePeriod')" min-width="220">
          <template #default="{ row }">
            {{ formatEffectivePeriod(row.effectiveFrom, row.effectiveTo) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('salesPrice.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('salesPrice.remark')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('salesPrice.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('salesPrice.print') }}
            </el-button>
            <el-button v-permission="'sales:price:manage'" link type="primary" @click="handleEdit(row)">
              {{ $t('salesPrice.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'sales:price:manage'"
              link
              type="warning"
              @click="handleDisable(row)"
            >
              {{ $t('salesPrice.disable') }}
            </el-button>
            <el-button
              v-else
              v-permission="'sales:price:manage'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              {{ $t('salesPrice.enable') }}
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

    <el-dialog v-model="dialogVisible" :title="editingId ? $t('salesPrice.editTitle') : $t('salesPrice.createTitle')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$t('salesPrice.scope')">
          <el-radio-group v-model="scopeType">
            <el-radio-button value="PRODUCT">{{ $t('salesPrice.productGeneral') }}</el-radio-button>
            <el-radio-button value="CUSTOMER">{{ $t('salesPrice.customerSpecific') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeType === 'CUSTOMER'" :label="$t('salesPrice.customer')" prop="customerId">
          <el-select v-model="form.customerId" filterable :placeholder="$t('salesPrice.selectCustomer')" style="width: 100%">
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.customerName || customer.name"
              :value="String(customer.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.product')" prop="productId">
          <el-select v-model="form.productId" filterable :placeholder="$t('salesPrice.selectProduct')" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.productCode || ''} ${product.productName || ''}`.trim()"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.listPrice')" prop="listPrice">
          <el-input-number v-model="form.listPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.minPrice')" prop="minPrice">
          <el-input-number v-model="form.minPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          <div class="form-tip">{{ $t('salesPrice.minPriceTip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.effectiveFrom')" prop="effectiveFrom">
          <el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.effectiveTo')">
          <el-date-picker
            v-model="form.effectiveTo"
            type="date"
            value-format="YYYY-MM-DD"
            clearable
            :placeholder="$t('salesPrice.noExpiry')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('salesPrice.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">{{ $t('salesPrice.save') }}</el-button>
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
  createSalesPrice,
  disableSalesPrice,
  enableSalesPrice,
  getSalesPrice,
  getSalesPrices,
  updateSalesPrice
} from '@/api/sales'
import { getCustomers, getProducts } from '@/api/masterdata'
import { useSalesPriceList } from '@/composables/useSalesPriceList'
import { useSalesPricePresentation } from '@/composables/useSalesPricePresentation'
import { printSalesPrice } from '@/utils/bizPrint'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  customers,
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
  tableData,
  total
} = useSalesPriceList(t, {
  getSalesPrices,
  getSalesPrice,
  createSalesPrice,
  updateSalesPrice,
  enableSalesPrice,
  disableSalesPrice,
  getCustomers,
  getProducts,
  printSalesPrice,
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
} = useSalesPricePresentation(t)

const rules = computed<FormRules>(() => ({
  customerId: [{ required: true, message: t('salesPrice.validation.customer'), trigger: 'change' }],
  productId: [{ required: true, message: t('salesPrice.validation.product'), trigger: 'change' }],
  listPrice: [{ required: true, message: t('salesPrice.validation.listPrice'), trigger: 'blur' }],
  minPrice: [{ required: true, message: t('salesPrice.validation.minPrice'), trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: t('salesPrice.validation.effectiveFrom'), trigger: 'change' }]
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
.sales-price-page {
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
