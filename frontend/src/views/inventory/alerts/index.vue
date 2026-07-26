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
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  Setting,
  Plus,
  Warning,
  WarningFilled,
  InfoFilled,
  CircleCheckFilled
} from '@element-plus/icons-vue'
import {
  getInventoryAlerts,
  createInventoryAlertRule,
  getInventoryAlertRules,
  updateInventoryAlertRule,
  enableInventoryAlertRule,
  disableInventoryAlertRule,
  ignoreInventoryAlert,
  resolveInventoryAlert,
  reactivateInventoryAlert,
  createInventoryReplenishmentSuggestion,
  type InventoryAlert
} from '@/api/inventory'
import { getWarehouses, getProducts, getSuppliers } from '@/api/masterdata'
import { useInventoryAlertPresentation } from '@/composables/useInventoryAlertPresentation'
import { useInventoryAlertList } from '@/composables/useInventoryAlertList'

const router = useRouter()
const { t } = useI18n()

const {
  formatNumber,
  alertTypeText,
  alertTypeTag,
  statusText,
  statusTag
} = useInventoryAlertPresentation(t)

const {
  currentAlert,
  editingRuleId,
  handleCreateRule,
  handleCreateSuggestion,
  handleDispose,
  handleEditRule,
  handleQuery,
  handleReactivate,
  handleReset,
  handleToggleRule,
  loadData,
  loadProducts,
  loadSuppliers,
  loadWarehouses,
  loading,
  openRulesDrawer,
  products,
  queryParams,
  resetRuleForm,
  resetSuggestionForm,
  ruleDialogVisible,
  ruleForm,
  ruleFormRef,
  ruleRows,
  ruleRules,
  ruleSubmitLoading,
  rulesDrawerVisible,
  rulesLoading,
  statistics,
  submitRule,
  submitSuggestion,
  suggestionDialogVisible,
  suggestionForm,
  suggestionFormRef,
  suggestionRules,
  suggestionSubmitLoading,
  suppliers,
  tableData,
  total,
  warehouses
} = useInventoryAlertList(t, {
  getAlerts: getInventoryAlerts,
  getRules: getInventoryAlertRules,
  createRule: createInventoryAlertRule,
  updateRule: updateInventoryAlertRule,
  enableRule: enableInventoryAlertRule,
  disableRule: disableInventoryAlertRule,
  ignoreAlert: ignoreInventoryAlert,
  resolveAlert: resolveInventoryAlert,
  reactivateAlert: reactivateInventoryAlert,
  createSuggestion: createInventoryReplenishmentSuggestion,
  getWarehouses,
  getProducts,
  getSuppliers,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts),
  prompt: (message, title, opts) => ElMessageBox.prompt(message, title, opts) as any,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})

const handleViewStock = (row: InventoryAlert) => {
  router.push({
    path: '/inventory/stocks',
    query: {
      warehouseId: row.warehouseId,
      productId: row.productId
    }
  })
}

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
