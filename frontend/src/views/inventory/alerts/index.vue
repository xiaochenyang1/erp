<template>
  <div class="inventory-alerts-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('inventoryAlerts.warehouse')">
          <el-select
            v-model="queryParams.warehouseId"
            :placeholder="$t('inventoryAlerts.placeholder.warehouse')"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.product')">
          <el-select
            v-model="queryParams.productId"
            :placeholder="$t('inventoryAlerts.placeholder.product')"
            clearable
            filterable
            style="width: 250px"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code} - ${product.name}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.alertType')">
          <el-select
            v-model="queryParams.alertType"
            :placeholder="$t('inventoryAlerts.placeholder.alertType')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('inventoryAlerts.type.lowStock')" value="LOW_STOCK" />
            <el-option :label="$t('inventoryAlerts.type.outOfStock')" value="OUT_OF_STOCK" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.dispositionStatus')">
          <el-select
            v-model="queryParams.status"
            :placeholder="$t('inventoryAlerts.placeholder.all')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('inventoryAlerts.status.active')" value="ACTIVE" />
            <el-option :label="$t('inventoryAlerts.status.ignored')" value="IGNORED" />
            <el-option :label="$t('inventoryAlerts.status.resolved')" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ $t('inventoryAlerts.action.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ $t('inventoryAlerts.action.reset') }}
          </el-button>
          <el-button v-permission="'inventory:alert:view'" @click="openRulesDrawer">
            <el-icon><Setting /></el-icon>
            {{ $t('inventoryAlerts.action.manageRules') }}
          </el-button>
          <el-button v-permission="'inventory:alert:create'" type="success" @click="handleCreateRule">
            <el-icon><Plus /></el-icon>
            {{ $t('inventoryAlerts.action.createRule') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-icon class="stat-icon" color="#409eff" :size="40">
              <Warning />
            </el-icon>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.total }}</div>
              <div class="stat-label">{{ $t('inventoryAlerts.statistics.total') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-icon class="stat-icon" color="#f56c6c" :size="40">
              <WarningFilled />
            </el-icon>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.outOfStock }}</div>
              <div class="stat-label">{{ $t('inventoryAlerts.statistics.outOfStock') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-icon class="stat-icon" color="#e6a23c" :size="40">
              <InfoFilled />
            </el-icon>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.lowStock }}</div>
              <div class="stat-label">{{ $t('inventoryAlerts.statistics.belowSafetyStock') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-icon class="stat-icon" color="#67c23a" :size="40">
              <CircleCheckFilled />
            </el-icon>
            <div class="stat-content">
              <div class="stat-value">{{ formatNumber(statistics.shortageQty) }}</div>
              <div class="stat-label">{{ $t('inventoryAlerts.statistics.shortageTotal') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="warehouseName" :label="$t('inventoryAlerts.warehouse')" width="150" />
        <el-table-column prop="productCode" :label="$t('inventoryAlerts.productCode')" width="150" />
        <el-table-column prop="productName" :label="$t('inventoryAlerts.productName')" width="180" />
        <el-table-column prop="currentQuantity" :label="$t('inventoryAlerts.currentStock')" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.currentQuantity <= 0 }">
              {{ row.currentQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="minQuantity" :label="$t('inventoryAlerts.minimumStock')" width="120" align="right" />
        <el-table-column prop="alertType" :label="$t('inventoryAlerts.alertType')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.alertType === 'LOW_STOCK'" type="warning">{{ $t('inventoryAlerts.type.lowStock') }}</el-tag>
            <el-tag v-else-if="row.alertType === 'OUT_OF_STOCK'" type="danger">{{ $t('inventoryAlerts.type.outOfStock') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertDate" :label="$t('inventoryAlerts.alertTime')" width="160" />
        <el-table-column prop="status" :label="$t('inventoryAlerts.dispositionStatus')" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="danger">{{ $t('inventoryAlerts.status.active') }}</el-tag>
            <el-tag v-else-if="row.status === 'IGNORED'" type="info">{{ $t('inventoryAlerts.status.ignored') }}</el-tag>
            <el-tag v-else-if="row.status === 'RESOLVED'" type="success">{{ $t('inventoryAlerts.status.resolved') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('inventoryAlerts.actions')" width="340" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="handleViewStock(row)"
            >
              {{ $t('inventoryAlerts.action.viewStock') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'inventory:replenishment:create'"
              link
              type="warning"
              @click="handleCreateSuggestion(row)"
            >
              {{ $t('inventoryAlerts.action.createSuggestion') }}
            </el-button>
            <el-button
              v-if="row.status !== 'RESOLVED'"
              v-permission="'inventory:alert:handle'"
              link
              type="success"
              @click="handleDispose(row, 'RESOLVED')"
            >
              {{ $t('inventoryAlerts.action.resolve') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'inventory:alert:handle'"
              link
              type="info"
              @click="handleDispose(row, 'IGNORED')"
            >
              {{ $t('inventoryAlerts.action.ignore') }}
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'inventory:alert:handle'"
              link
              type="primary"
              @click="handleReactivate(row)"
            >
              {{ $t('inventoryAlerts.action.reactivate') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId ? $t('inventoryAlerts.dialog.editRule') : $t('inventoryAlerts.dialog.createRule')" width="560px" @close="resetRuleForm">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="100px">
        <el-form-item :label="$t('inventoryAlerts.warehouse')" prop="warehouseId">
          <el-select v-model="ruleForm.warehouseId" :placeholder="$t('inventoryAlerts.placeholder.warehouse')" filterable :disabled="!!editingRuleId" style="width: 100%">
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.product')" prop="productId">
          <el-select v-model="ruleForm.productId" :placeholder="$t('inventoryAlerts.placeholder.product')" filterable :disabled="!!editingRuleId" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code} - ${product.name}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.minimumStock')" prop="minQty">
          <el-input-number v-model="ruleForm.minQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.remark')">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="3" :placeholder="$t('inventoryAlerts.placeholder.remark')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">{{ $t('inventoryAlerts.action.cancel') }}</el-button>
        <el-button type="primary" :loading="ruleSubmitLoading" @click="submitRule">{{ $t('inventoryAlerts.action.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="suggestionDialogVisible"
      :title="$t('inventoryAlerts.dialog.createSuggestion')"
      width="620px"
      @close="resetSuggestionForm"
    >
      <el-form
        ref="suggestionFormRef"
        :model="suggestionForm"
        :rules="suggestionRules"
        label-width="110px"
      >
        <el-descriptions v-if="currentAlert" :column="2" border class="suggestion-alert-summary">
          <el-descriptions-item :label="$t('inventoryAlerts.warehouse')">{{ currentAlert.warehouseName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryAlerts.product')">{{ currentAlert.productCode }} - {{ currentAlert.productName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryAlerts.currentStock')">{{ formatNumber(currentAlert.currentQuantity) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryAlerts.safetyStock')">{{ formatNumber(currentAlert.minQuantity) }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item :label="$t('inventoryAlerts.suggestedQuantity')" prop="suggestedQty">
          <el-input-number
            v-model="suggestionForm.suggestedQty"
            :min="0.0001"
            :precision="4"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.supplier')">
          <el-select
            v-model="suggestionForm.supplierId"
            :placeholder="$t('inventoryAlerts.placeholder.supplier')"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code || supplier.supplierCode} - ${supplier.name || supplier.supplierName}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.expectedArrival')">
          <el-date-picker
            v-model="suggestionForm.expectedArrivalDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="$t('inventoryAlerts.placeholder.date')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryAlerts.remark')">
          <el-input
            v-model="suggestionForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="$t('inventoryAlerts.placeholder.suggestionRemark')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suggestionDialogVisible = false">{{ $t('inventoryAlerts.action.cancel') }}</el-button>
        <el-button type="primary" :loading="suggestionSubmitLoading" @click="submitSuggestion">{{ $t('inventoryAlerts.action.save') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="rulesDrawerVisible" :title="$t('inventoryAlerts.dialog.manageRules')" size="720px">
      <div class="rules-toolbar">
        <el-button v-permission="'inventory:alert:create'" type="primary" @click="handleCreateRule">
          {{ $t('inventoryAlerts.action.createRule') }}
        </el-button>
        <el-button :loading="rulesLoading" @click="loadRules">{{ $t('inventoryAlerts.action.refreshRules') }}</el-button>
      </div>
      <el-table v-loading="rulesLoading" :data="ruleRows" border stripe style="margin-top: 12px">
        <el-table-column prop="warehouseName" :label="$t('inventoryAlerts.warehouse')" min-width="120" />
        <el-table-column prop="productCode" :label="$t('inventoryAlerts.productCode')" width="120" />
        <el-table-column prop="productName" :label="$t('inventoryAlerts.productName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="minQty" :label="$t('inventoryAlerts.minimumStock')" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.minQty) }}</template>
        </el-table-column>
        <el-table-column prop="enabled" :label="$t('inventoryAlerts.ruleStatus')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? $t('inventoryAlerts.status.enabled') : $t('inventoryAlerts.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('inventoryAlerts.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'inventory:alert:create'" link type="primary" @click="handleEditRule(row)">
              {{ $t('inventoryAlerts.action.editRule') }}
            </el-button>
            <el-button
              v-if="!row.enabled"
              v-permission="'inventory:alert:create'"
              link
              type="success"
              @click="handleToggleRule(row, true)"
            >
              {{ $t('inventoryAlerts.action.enableRule') }}
            </el-button>
            <el-button
              v-else
              v-permission="'inventory:alert:create'"
              link
              type="warning"
              @click="handleToggleRule(row, false)"
            >
              {{ $t('inventoryAlerts.action.disableRule') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createInventoryReplenishmentSuggestion,
  createInventoryAlertRule,
  disableInventoryAlertRule,
  enableInventoryAlertRule,
  getInventoryAlertRules,
  getInventoryAlerts,
  ignoreInventoryAlert,
  reactivateInventoryAlert,
  resolveInventoryAlert,
  updateInventoryAlertRule,
  type InventoryAlertRule,
  type InventoryAlertRuleCreateRequest,
  type InventoryAlertQuery,
  type InventoryAlert
} from '@/api/inventory'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { getProducts, type Product } from '@/api/masterdata'
import { getSuppliers, type Supplier } from '@/api/masterdata'
import { formatLocalizedNumber } from '@/utils/locale'

const router = useRouter()
const { t } = useI18n()

// 查询参数
const queryParams = reactive<InventoryAlertQuery>({
  pageNo: 1,
  pageSize: 10,
  warehouseId: undefined,
  productId: undefined,
  alertType: '',
  status: 'ACTIVE'
})

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryAlert[]>([])
const total = ref(0)

// 仓库列表
const warehouses = ref<Warehouse[]>([])

// 产品列表
const products = ref<Product[]>([])

// 供应商列表
const suppliers = ref<Supplier[]>([])

// 统计数据
const statistics = reactive({
  total: 0,
  outOfStock: 0,
  lowStock: 0,
  shortageQty: 0
})
const ruleDialogVisible = ref(false)
const ruleSubmitLoading = ref(false)
const ruleFormRef = ref<FormInstance>()
const ruleForm = reactive<InventoryAlertRuleCreateRequest>({
  warehouseId: '',
  productId: '',
  minQty: 0,
  remark: ''
})
const rulesDrawerVisible = ref(false)
const rulesLoading = ref(false)
const ruleRows = ref<InventoryAlertRule[]>([])
const editingRuleId = ref('')
const ruleRules: FormRules = {
  warehouseId: [{ required: true, message: t('inventoryAlerts.validation.warehouse'), trigger: 'change' }],
  productId: [{ required: true, message: t('inventoryAlerts.validation.product'), trigger: 'change' }],
  minQty: [{ required: true, message: t('inventoryAlerts.validation.minimumStock'), trigger: 'blur' }]
}
const suggestionDialogVisible = ref(false)
const suggestionSubmitLoading = ref(false)
const suggestionFormRef = ref<FormInstance>()
const currentAlert = ref<InventoryAlert>()
const suggestionForm = reactive({
  ruleId: '',
  warehouseId: '',
  productId: '',
  supplierId: undefined as string | undefined,
  suggestedQty: 0,
  expectedArrivalDate: '',
  remark: ''
})
const suggestionRules: FormRules = {
  suggestedQty: [{ required: true, message: t('inventoryAlerts.validation.suggestedQuantity'), trigger: 'blur' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryAlerts(queryParams)
    tableData.value = response.records
    total.value = response.total

    // 更新统计数据
    await loadStatistics()
  } catch (error) {
    ElMessage.error(t('inventoryAlerts.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStatistics = async () => {
  try {
    // 获取所有预警
    const allResponse = await getInventoryAlerts({
      pageNo: 1,
      pageSize: 1000
    })

    statistics.total = allResponse.total
    statistics.outOfStock = allResponse.records.filter(
      (item: InventoryAlert) => item.alertType === 'OUT_OF_STOCK'
    ).length
    statistics.lowStock = allResponse.records.filter(
      (item: InventoryAlert) => item.alertType === 'LOW_STOCK'
    ).length
    statistics.shortageQty = allResponse.records.reduce(
      (sum: number, item: InventoryAlert) => sum + Number(item.shortageQty || 0),
      0
    )
  } catch {
    ElMessage.warning(t('inventoryAlerts.message.statisticsLoadFailed'))
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const response = await getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error(t('inventoryAlerts.message.warehousesLoadFailed'))
  }
}

// 加载产品列表
const loadProducts = async () => {
  try {
    const response = await getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    products.value = response.records
  } catch (error) {
    ElMessage.error(t('inventoryAlerts.message.productsLoadFailed'))
  }
}

// 加载供应商列表
const loadSuppliers = async () => {
  try {
    const response = await getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    suppliers.value = response.records
  } catch (error) {
    ElMessage.error(t('inventoryAlerts.message.suppliersLoadFailed'))
  }
}

// 查询
const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.warehouseId = undefined
  queryParams.productId = undefined
  queryParams.alertType = ''
  queryParams.status = 'ACTIVE'
  handleQuery()
}

const handleCreateRule = async () => {
  if (warehouses.value.length === 0) {
    await loadWarehouses()
  }
  if (products.value.length === 0) {
    await loadProducts()
  }
  resetRuleForm()
  ruleDialogVisible.value = true
}

const submitRule = async () => {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate(async (valid) => {
    if (!valid) return
    ruleSubmitLoading.value = true
    try {
      if (editingRuleId.value) {
        await updateInventoryAlertRule(editingRuleId.value, {
          minQty: Number(ruleForm.minQty),
          remark: ruleForm.remark
        })
        ElMessage.success(t('inventoryAlerts.message.ruleUpdated'))
      } else {
        await createInventoryAlertRule(ruleForm)
        ElMessage.success(t('inventoryAlerts.message.ruleCreated'))
      }
      ruleDialogVisible.value = false
      await loadData()
      if (rulesDrawerVisible.value) {
        await loadRules()
      }
    } catch (error) {
      ElMessage.error(editingRuleId.value
        ? t('inventoryAlerts.message.ruleUpdateFailed')
        : t('inventoryAlerts.message.ruleCreateFailed'))
    } finally {
      ruleSubmitLoading.value = false
    }
  })
}

const resetRuleForm = () => {
  ruleFormRef.value?.clearValidate()
  editingRuleId.value = ''
  Object.assign(ruleForm, {
    warehouseId: '',
    productId: '',
    minQty: 0,
    remark: ''
  })
}

const openRulesDrawer = async () => {
  rulesDrawerVisible.value = true
  await loadRules()
}

const loadRules = async () => {
  rulesLoading.value = true
  try {
    ruleRows.value = await getInventoryAlertRules({
      warehouseId: queryParams.warehouseId,
      productId: queryParams.productId
    })
  } catch {
    ElMessage.error(t('inventoryAlerts.message.rulesLoadFailed'))
  } finally {
    rulesLoading.value = false
  }
}

const handleEditRule = async (row: InventoryAlertRule) => {
  if (warehouses.value.length === 0) await loadWarehouses()
  if (products.value.length === 0) await loadProducts()
  editingRuleId.value = row.id
  Object.assign(ruleForm, {
    warehouseId: row.warehouseId,
    productId: row.productId,
    minQty: Number(row.minQty || 0),
    remark: row.remark || ''
  })
  ruleDialogVisible.value = true
}

const handleToggleRule = async (row: InventoryAlertRule, enable: boolean) => {
  try {
    if (enable) {
      await enableInventoryAlertRule(row.id)
      ElMessage.success(t('inventoryAlerts.message.ruleEnabled'))
    } else {
      await disableInventoryAlertRule(row.id)
      ElMessage.success(t('inventoryAlerts.message.ruleDisabled'))
    }
    await loadRules()
    await loadData()
  } catch {
    ElMessage.error(enable
      ? t('inventoryAlerts.message.ruleEnableFailed')
      : t('inventoryAlerts.message.ruleDisableFailed'))
  }
}

const handleCreateSuggestion = async (row: InventoryAlert) => {
  if (suppliers.value.length === 0) {
    await loadSuppliers()
  }
  currentAlert.value = row
  Object.assign(suggestionForm, {
    ruleId: row.ruleId,
    warehouseId: row.warehouseId,
    productId: row.productId,
    supplierId: undefined,
    suggestedQty: Number(row.shortageQty || 0),
    expectedArrivalDate: '',
    remark: ''
  })
  suggestionDialogVisible.value = true
}

const submitSuggestion = async () => {
  if (!suggestionFormRef.value) return
  await suggestionFormRef.value.validate(async (valid) => {
    if (!valid) return
    suggestionSubmitLoading.value = true
    try {
      await createInventoryReplenishmentSuggestion({
        ruleId: suggestionForm.ruleId,
        warehouseId: suggestionForm.warehouseId,
        productId: suggestionForm.productId,
        supplierId: suggestionForm.supplierId || undefined,
        suggestedQty: suggestionForm.suggestedQty,
        expectedArrivalDate: suggestionForm.expectedArrivalDate || undefined,
        remark: suggestionForm.remark || undefined
      })
      ElMessage.success(t('inventoryAlerts.message.suggestionCreated'))
      suggestionDialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(t('inventoryAlerts.message.suggestionCreateFailed'))
    } finally {
      suggestionSubmitLoading.value = false
    }
  })
}

const resetSuggestionForm = () => {
  suggestionFormRef.value?.clearValidate()
  currentAlert.value = undefined
  Object.assign(suggestionForm, {
    ruleId: '',
    warehouseId: '',
    productId: '',
    supplierId: undefined,
    suggestedQty: 0,
    expectedArrivalDate: '',
    remark: ''
  })
}

// 查看库存
const handleViewStock = (row: InventoryAlert) => {
  router.push({
    path: '/inventory/stocks',
    query: {
      warehouseId: row.warehouseId,
      productId: row.productId
    }
  })
}

// 处置预警：忽略 / 标记已处理
const handleDispose = async (row: InventoryAlert, status: 'IGNORED' | 'RESOLVED') => {
  const isIgnore = status === 'IGNORED'
  try {
    const { value } = await ElMessageBox.prompt(
      t(isIgnore ? 'inventoryAlerts.message.ignoreConfirm' : 'inventoryAlerts.message.resolveConfirm', {
        warehouse: row.warehouseName,
        product: row.productName
      }),
      t(isIgnore ? 'inventoryAlerts.dialog.ignore' : 'inventoryAlerts.dialog.resolve'),
      {
        confirmButtonText: t('inventoryAlerts.action.confirm'),
        cancelButtonText: t('inventoryAlerts.action.cancel'),
        inputType: 'textarea',
        inputPlaceholder: t('inventoryAlerts.placeholder.dispositionRemark'),
        inputValue: ''
      }
    )
    const payload = {
      warehouseId: row.warehouseId,
      productId: row.productId,
      remark: value || undefined
    }
    if (status === 'IGNORED') {
      await ignoreInventoryAlert(payload)
    } else {
      await resolveInventoryAlert(payload)
    }
    ElMessage.success(t(isIgnore ? 'inventoryAlerts.message.ignored' : 'inventoryAlerts.message.resolved'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t(isIgnore ? 'inventoryAlerts.message.ignoreFailed' : 'inventoryAlerts.message.resolveFailed'))
    }
  }
}

const handleReactivate = async (row: InventoryAlert) => {
  try {
    await ElMessageBox.confirm(
      t('inventoryAlerts.message.reactivateConfirm', {
        warehouse: row.warehouseName,
        product: row.productName
      }),
      t('inventoryAlerts.dialog.reactivate'),
      {
        type: 'warning',
        confirmButtonText: t('inventoryAlerts.action.reactivate'),
        cancelButtonText: t('inventoryAlerts.action.cancel')
      }
    )
    await reactivateInventoryAlert({
      warehouseId: row.warehouseId,
      productId: row.productId
    })
    ElMessage.success(t('inventoryAlerts.message.reactivated'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryAlerts.message.reactivateFailed'))
    }
  }
}

const formatNumber = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
  maximumFractionDigits: 4
})

onMounted(() => {
  loadData()
  loadWarehouses()
  loadProducts()
  loadSuppliers()
})
</script>

<style scoped lang="scss">
.inventory-alerts-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .stat-card {
    display: flex;
    align-items: center;
    gap: 20px;

    .stat-icon {
      flex-shrink: 0;
    }

    .stat-content {
      flex: 1;

      .stat-value {
        font-size: 28px;
        font-weight: bold;
        color: #303133;
        margin-bottom: 5px;
      }

      .stat-label {
        font-size: 14px;
        color: #909399;
      }
    }
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .text-danger {
    color: #f56c6c;
    font-weight: bold;
  }

  .suggestion-alert-summary {
    margin-bottom: 18px;
  }
}
.rules-toolbar {
  display: flex;
  gap: 8px;
}
</style>
