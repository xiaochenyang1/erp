<template>
  <div class="sales-returns-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('salesReturnOps.returnNo')">
          <el-input
            v-model="queryParams.returnNo"
            :placeholder="$t('salesReturnOps.placeholder.returnNo')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('salesReturnOps.salesDelivery')">
          <el-select
            v-model="queryParams.deliveryId"
            :placeholder="$t('salesReturnOps.placeholder.salesDelivery')"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="delivery in deliveries"
              :key="delivery.id"
              :label="deliveryLabel(delivery)"
              :value="delivery.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesReturnOps.statusLabel')">
          <el-select
            v-model="queryParams.status"
            :placeholder="$t('salesReturnOps.placeholder.status')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('salesReturnOps.status.draft')" value="DRAFT" />
            <el-option :label="$t('salesReturnOps.status.posted')" value="POSTED" />
            <el-option :label="$t('salesReturnOps.status.completed')" value="COMPLETED" />
            <el-option :label="$t('salesReturnOps.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesReturnOps.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('salesReturnOps.rangeSeparator')"
            :start-placeholder="$t('salesReturnOps.placeholder.startDate')"
            :end-placeholder="$t('salesReturnOps.placeholder.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ $t('salesReturnOps.action.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ $t('salesReturnOps.action.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'sales:return:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ $t('salesReturnOps.action.create') }}
      </el-button>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="returnNo" :label="$t('salesReturnOps.returnNo')" width="180" />
        <el-table-column prop="deliveryId" :label="$t('salesReturnOps.salesDelivery')" width="180">
          <template #default="{ row }">
            {{ deliveryLabelById(row.deliveryId) || row.deliveryId }}
          </template>
        </el-table-column>
        <el-table-column prop="customerName" :label="$t('salesReturnOps.customer')" width="150" />
        <el-table-column prop="warehouseName" :label="$t('salesReturnOps.returnWarehouse')" width="140" />
        <el-table-column prop="returnDate" :label="$t('salesReturnOps.returnDate')" width="120" />
        <el-table-column prop="totalAmount" :label="$t('salesReturnOps.returnAmount')" width="140" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('salesReturnOps.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">{{ $t('salesReturnOps.status.draft') }}</el-tag>
            <el-tag v-else-if="row.status === 'POSTED' || row.status === 'COMPLETED'" type="success">{{ $t('salesReturnOps.status.posted') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">{{ $t('salesReturnOps.status.cancelled') }}</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('salesReturnOps.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="$t('salesReturnOps.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="$t('salesReturnOps.createdTime')" width="160" />
        <el-table-column :label="$t('salesReturnOps.actions')" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ $t('salesReturnOps.action.view') }}
            </el-button>
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('salesReturnOps.action.print') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:return:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              {{ $t('salesReturnOps.action.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:return:post'"
              link
              type="success"
              @click="handlePost(row)"
            >
              {{ $t('salesReturnOps.action.post') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:return:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              {{ $t('salesReturnOps.action.cancel') }}
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

    <!-- 新增/查看对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="85%"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('salesReturnOps.salesDelivery')" prop="deliveryId">
              <el-select
                v-model="formData.deliveryId"
                :placeholder="$t('salesReturnOps.placeholder.salesDelivery')"
                style="width: 100%"
                :disabled="isView || !!editingId"
                @change="handleDeliveryChange"
                filterable
              >
                <el-option
                  v-for="delivery in deliveries"
                  :key="delivery.id"
                  :label="deliveryLabel(delivery)"
                  :value="delivery.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('salesReturnOps.customer')">
              <el-input :model-value="selectedDelivery?.customerName || '-'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('salesReturnOps.returnWarehouse')">
              <el-input :model-value="selectedDelivery?.warehouseName || '-'" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('salesReturnOps.returnDate')" prop="returnDate">
              <el-date-picker
                v-model="formData.returnDate"
                type="date"
                :placeholder="$t('salesReturnOps.placeholder.returnDate')"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('salesReturnOps.remark')">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            :placeholder="$t('salesReturnOps.placeholder.remark')"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 退货明细 -->
        <el-divider content-position="left">{{ $t('salesReturnOps.details') }}</el-divider>
        <el-table :data="formData.items" border max-height="400">
          <el-table-column :label="$t('salesReturnOps.product')" prop="productName" width="250" />
          <el-table-column :label="$t('salesReturnOps.productCode')" prop="productCode" width="140" />
          <el-table-column :label="$t('salesReturnOps.productName')" prop="productName" width="150" />
          <el-table-column :label="$t('salesReturnOps.returnQuantity')" prop="quantity" width="130">
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
                @change="handleQuantityChange($index)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.returnUnitPrice')" prop="price" width="130">
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.price"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
                @change="handleQuantityChange($index)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.returnAmount')" prop="amount" width="140" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.amount) }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.location')" width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.locationId"
                clearable
                filterable
                :placeholder="$t('salesReturnOps.placeholder.location')"
                :disabled="isView"
                style="width: 100%"
              >
                <el-option
                  v-for="location in locationsForSelectedDelivery"
                  :key="location.id"
                  :label="`${location.locationCode} ${location.locationName}`"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.serialNos')" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                :placeholder="row.serialControlled
                  ? $t('salesReturnOps.placeholder.serialNos')
                  : $t('salesReturnOps.placeholder.reason')"
                :disabled="isView || row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.quantity).complete }"
              >
                {{ $t('salesReturnOps.serialProgress', serialCaptureProgress(row.serialNos, row.quantity)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.lotNo')" width="130">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                :placeholder="row.lotControlled
                  ? $t('salesReturnOps.placeholder.lotNo')
                  : $t('salesReturnOps.placeholder.reason')"
                :disabled="isView || row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.productionDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.productionDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                :disabled="isView || row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesReturnOps.expiryDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                :disabled="isView || (row.shelfLifeControlled === false && row.lotControlled === false)"
              />
            </template>
          </el-table-column>

          <el-table-column :label="$t('salesReturnOps.reason')" prop="reason">
            <template #default="{ row }">
              <el-input
                v-model="row.reason"
                :placeholder="$t('salesReturnOps.placeholder.reason')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" :label="$t('salesReturnOps.actions')" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                @click="handleDeleteItem($index)"
              >
                {{ $t('salesReturnOps.action.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 汇总信息 -->
        <div style="margin-top: 20px; text-align: right; font-size: 16px">
          <span style="margin-right: 20px">
            {{ $t('salesReturnOps.totalQuantity') }}: <strong>{{ totalQuantity }}</strong>
          </span>
          <span>
            {{ $t('salesReturnOps.totalAmount') }}: <strong style="color: #f56c6c">{{ formatMoney(totalAmount) }}</strong>
          </span>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('salesReturnOps.action.cancel') }}</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('salesReturnOps.action.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSalesReturns,
  getSalesReturn,
  createSalesReturn,
  updateSalesReturn,
  postSalesReturn,
  cancelSalesReturn,
  getSalesDeliveries,
  getSalesDelivery
} from '@/api/sales'
import { getLocations, getProducts } from '@/api/masterdata'
import {
  serialCaptureProgress
} from '@/utils/productLines'
import { printSalesReturn } from '@/utils/bizPrint'
import { formatBusinessDate } from '@/utils/locale'
import { useSalesReturnPresentation } from '@/composables/useSalesReturnPresentation'
import { useSalesReturnList } from '@/composables/useSalesReturnList'
import { useSalesReturnForm } from '@/composables/useSalesReturnForm'

const { t } = useI18n()

const {
  dateRange,
  deliveries,
  handleCancel,
  handlePost,
  handlePrint,
  handleQuery,
  handleReset,
  loadData,
  loadOptions,
  loading,
  locations,
  products,
  queryParams,
  tableData,
  total
} = useSalesReturnList(t, {
  getReturns: getSalesReturns,
  getReturn: getSalesReturn,
  postReturn: postSalesReturn,
  cancelReturn: cancelSalesReturn,
  getDeliveries: getSalesDeliveries,
  getProducts,
  getLocations,
  printReturn: printSalesReturn,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  enrichReturnRow: (item) => ({
    ...item,
    deliveryNo: item.deliveryNo || deliveryNoById(item.deliveryId),
    customerName: item.customerName || deliveryCustomerNameById(item.deliveryId),
    warehouseName: item.warehouseName || deliveryWarehouseNameById(item.deliveryId)
  }),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  dialogTitle,
  dialogVisible,
  editingId,
  formData,
  formRef,
  formRules,
  handleCreate,
  handleDeleteItem,
  handleDeliveryChange,
  handleEdit,
  handleQuantityChange,
  handleSubmit,
  handleView,
  isView,
  submitLoading,
  totalAmount: totalAmountFn,
  totalQuantity: totalQuantityFn
} = useSalesReturnForm(t, {
  products,
  deliveries,
  getDelivery: getSalesDelivery,
  getReturn: getSalesReturn,
  createReturn: createSalesReturn,
  updateReturn: updateSalesReturn,
  formatBusinessDate,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onCompleted: () => loadData()
})

const {
  deliveryCustomerNameById,
  deliveryLabel,
  deliveryLabelById,
  deliveryNoById,
  deliveryWarehouseNameById,
  formatMoney,
  locationsForSelectedDelivery,
  selectedDelivery
} = useSalesReturnPresentation(deliveries, locations, () => formData.deliveryId)

const totalQuantity = computed(() => totalQuantityFn())
const totalAmount = computed(() => totalAmountFn())

onMounted(async () => {
  await loadOptions()
  loadData()
})
</script>

<style scoped lang="scss">
.sales-returns-container {
  padding: 20px;

  .search-card,
  .toolbar-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}

.serial-progress {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.2;
}
.serial-progress--ok {
  color: var(--el-color-success);
}
</style>
