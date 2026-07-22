<template>
  <div class="replenishment-suggestions-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="建议编号">
          <el-input
            v-model="queryParams.suggestionNo"
            placeholder="请输入建议编号"
            clearable
            style="width: 190px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已转单" value="CONVERTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            filterable
            style="width: 190px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name || warehouse.warehouseName"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品">
          <el-select
            v-model="queryParams.productId"
            placeholder="请选择产品"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code || product.productCode} - ${product.name || product.productName}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="queryParams.supplierId"
            placeholder="请选择供应商"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code || supplier.supplierCode} - ${supplier.name || supplier.supplierName}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="createdRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
            @change="handleCreatedRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="suggestionNo" label="建议编号" width="170" fixed />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="warning">草稿</el-tag>
            <el-tag v-else-if="row.status === 'CONVERTED'" type="success">已转单</el-tag>
            <el-tag v-else type="info">已取消</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fulfillmentStatus" label="履约状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="fulfillmentStatusMeta(row.fulfillmentStatus).type" effect="plain">
              {{ fulfillmentStatusMeta(row.fulfillmentStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="仓库" width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编码" width="140" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="suggestedQty" label="建议数量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.suggestedQty) }}</template>
        </el-table-column>
        <el-table-column prop="shortageQtySnapshot" label="缺口快照" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.shortageQtySnapshot) }}</template>
        </el-table-column>
        <el-table-column prop="expectedArrivalDate" label="预计到货" width="120">
          <template #default="{ row }">{{ row.expectedArrivalDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="purchaseOrderNo" label="采购订单" width="170">
          <template #default="{ row }">
            <el-button
              v-if="row.purchaseOrderNo"
              link
              type="primary"
              @click="goPurchaseOrder(row.purchaseOrderNo)"
            >
              {{ row.purchaseOrderNo }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:replenishment:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:replenishment:convert'"
              link
              type="success"
              @click="handleConvert(row)"
            >
              转采购订单
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:replenishment:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑补货建议"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item label="建议编号">
          <el-input v-model="editForm.suggestionNo" disabled />
        </el-form-item>
        <el-form-item label="仓库">
          <el-input v-model="editForm.warehouseName" disabled />
        </el-form-item>
        <el-form-item label="产品">
          <el-input v-model="editForm.productName" disabled />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="editForm.supplierId"
            placeholder="请选择供应商"
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
        <el-form-item label="建议数量" prop="suggestedQty">
          <el-input-number
            v-model="editForm.suggestedQty"
            :min="0.0001"
            :precision="4"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker
            v-model="editForm.expectedArrivalDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择预计到货日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
import {
  cancelInventoryReplenishmentSuggestion,
  convertInventoryReplenishmentSuggestion,
  getInventoryReplenishmentSuggestions,
  updateInventoryReplenishmentSuggestion,
  type InventoryReplenishmentSuggestion,
  type InventoryReplenishmentSuggestionQuery
} from '@/api/inventory'
import {
  getProducts,
  getSuppliers,
  getWarehouses,
  type Product,
  type Supplier,
  type Warehouse
} from '@/api/masterdata'

const router = useRouter()
const loading = ref(false)
const tableData = ref<InventoryReplenishmentSuggestion[]>([])
const total = ref(0)
const warehouses = ref<Warehouse[]>([])
const products = ref<Product[]>([])
const suppliers = ref<Supplier[]>([])
const createdRange = ref<[string, string]>()
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()

const editForm = reactive({
  id: '',
  suggestionNo: '',
  warehouseName: '',
  productName: '',
  supplierId: undefined as string | number | undefined,
  suggestedQty: 1,
  expectedArrivalDate: '',
  remark: ''
})

const editRules: FormRules = {
  suggestedQty: [
    { required: true, message: '请输入建议数量', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (Number(value) <= 0) {
          callback(new Error('建议数量必须大于0'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const fulfillmentStatusMap: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  SUGGESTED: { label: '待转采购', type: 'warning' },
  PURCHASE_CREATED: { label: '已生成采购', type: 'primary' },
  PARTIAL_RECEIVED: { label: '部分到货', type: 'warning' },
  REPLENISHED: { label: '已补足', type: 'success' },
  PURCHASE_CLOSED: { label: '采购关闭', type: 'info' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const queryParams = reactive<InventoryReplenishmentSuggestionQuery>({
  pageNo: 1,
  pageSize: 20,
  suggestionNo: '',
  status: '',
  warehouseId: undefined,
  productId: undefined,
  supplierId: undefined,
  createdTimeFrom: undefined,
  createdTimeTo: undefined
})

const fulfillmentStatusMeta = (status?: string) => fulfillmentStatusMap[status || ''] || {
  label: status || '-',
  type: 'info' as const
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryReplenishmentSuggestions(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error('加载补货建议失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const handleReset = () => {
  Object.assign(queryParams, {
    pageNo: 1,
    pageSize: 20,
    suggestionNo: '',
    status: '',
    warehouseId: undefined,
    productId: undefined,
    supplierId: undefined,
    createdTimeFrom: undefined,
    createdTimeTo: undefined
  })
  createdRange.value = undefined
  loadData()
}

const handleCreatedRangeChange = (value: [string, string] | null) => {
  queryParams.createdTimeFrom = value?.[0]
  queryParams.createdTimeTo = value?.[1]
}

const handleEdit = (row: InventoryReplenishmentSuggestion) => {
  Object.assign(editForm, {
    id: row.id,
    suggestionNo: row.suggestionNo,
    warehouseName: row.warehouseName || '-',
    productName: `${row.productCode || ''} ${row.productName || ''}`.trim() || '-',
    supplierId: row.supplierId,
    suggestedQty: Number(row.suggestedQty || 0),
    expectedArrivalDate: row.expectedArrivalDate || '',
    remark: row.remark || ''
  })
  editDialogVisible.value = true
}

const submitEdit = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  editSubmitting.value = true
  try {
    await updateInventoryReplenishmentSuggestion(editForm.id, {
      supplierId: editForm.supplierId,
      suggestedQty: editForm.suggestedQty,
      expectedArrivalDate: editForm.expectedArrivalDate || undefined,
      remark: editForm.remark || undefined
    })
    ElMessage.success('补货建议已更新')
    editDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('保存补货建议失败')
  } finally {
    editSubmitting.value = false
  }
}

const handleCancel = async (row: InventoryReplenishmentSuggestion) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认取消补货建议 ${row.suggestionNo} 吗？`,
      '取消补货建议',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '取消原因（选填）'
      }
    )
    await cancelInventoryReplenishmentSuggestion(row.id, value || undefined)
    ElMessage.success('已取消')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

const handleConvert = async (row: InventoryReplenishmentSuggestion) => {
  if (!row.supplierId) {
    ElMessage.warning('请先为补货建议选择供应商')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将补货建议 ${row.suggestionNo} 转为采购订单吗？`,
      '转采购订单',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const response = await convertInventoryReplenishmentSuggestion(row.id)
    ElMessage.success(`已生成采购订单 ${response.purchaseOrderNo}`)
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('转采购订单失败')
    }
  }
}

const goPurchaseOrder = (orderNo: string) => {
  router.push({
    path: '/purchase/orders',
    query: { keyword: orderNo }
  })
}

const loadOptions = async () => {
  try {
    const [warehousePage, productPage, supplierPage] = await Promise.all([
      getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
      getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
      getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    ])
    warehouses.value = warehousePage.records
    products.value = productPage.records
    suppliers.value = supplierPage.records
  } catch (error) {
    ElMessage.warning('加载筛选选项失败')
  }
}

const formatNumber = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
  maximumFractionDigits: 4
})

const formatDateTime = (value?: string) => {
  return formatLocalizedDateTime(value) || '-'
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped lang="scss">
.replenishment-suggestions-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
