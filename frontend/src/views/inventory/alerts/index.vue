<template>
  <div class="inventory-alerts-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
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
        <el-form-item label="产品">
          <el-select
            v-model="queryParams.productId"
            placeholder="请选择产品"
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
        <el-form-item label="预警类型">
          <el-select
            v-model="queryParams.alertType"
            placeholder="请选择预警类型"
            clearable
            style="width: 150px"
          >
            <el-option label="库存不足" value="LOW_STOCK" />
            <el-option label="缺货" value="OUT_OF_STOCK" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置状态">
          <el-select
            v-model="queryParams.status"
            placeholder="全部"
            clearable
            style="width: 150px"
          >
            <el-option label="待处置" value="ACTIVE" />
            <el-option label="已忽略" value="IGNORED" />
            <el-option label="已处理" value="RESOLVED" />
          </el-select>
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
          <el-button type="success" @click="handleCreateRule">
            <el-icon><Plus /></el-icon>
            新增规则
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
              <div class="stat-label">总预警数</div>
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
              <div class="stat-label">缺货</div>
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
              <div class="stat-label">低于安全库存</div>
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
              <div class="stat-label">缺口合计</div>
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
        <el-table-column prop="warehouseName" label="仓库" width="150" />
        <el-table-column prop="productCode" label="产品编码" width="150" />
        <el-table-column prop="productName" label="产品名称" width="180" />
        <el-table-column prop="currentQuantity" label="当前库存" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.currentQuantity <= 0 }">
              {{ row.currentQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="minQuantity" label="最小库存" width="120" align="right" />
        <el-table-column prop="alertType" label="预警类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.alertType === 'LOW_STOCK'" type="warning">库存不足</el-tag>
            <el-tag v-else-if="row.alertType === 'OUT_OF_STOCK'" type="danger">缺货</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertDate" label="预警时间" width="160" />
        <el-table-column prop="status" label="处置状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="danger">待处置</el-tag>
            <el-tag v-else-if="row.status === 'IGNORED'" type="info">已忽略</el-tag>
            <el-tag v-else-if="row.status === 'RESOLVED'" type="success">已处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="handleViewStock(row)"
            >
              查看库存
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'inventory:replenishment:create'"
              link
              type="warning"
              @click="handleCreateSuggestion(row)"
            >
              生成补货建议
            </el-button>
            <el-button
              v-if="row.status !== 'RESOLVED'"
              v-permission="'inventory:alert:handle'"
              link
              type="success"
              @click="handleDispose(row, 'RESOLVED')"
            >
              标记已处理
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'inventory:alert:handle'"
              link
              type="info"
              @click="handleDispose(row, 'IGNORED')"
            >
              忽略
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'inventory:alert:handle'"
              link
              type="primary"
              @click="handleReactivate(row)"
            >
              重新激活
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

    <el-dialog v-model="ruleDialogVisible" title="新增低库存规则" width="560px" @close="resetRuleForm">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="100px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="ruleForm.warehouseId" placeholder="请选择仓库" filterable style="width: 100%">
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <el-select v-model="ruleForm.productId" placeholder="请选择产品" filterable style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code} - ${product.name}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="最小库存" prop="minQty">
          <el-input-number v-model="ruleForm.minQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitLoading" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="suggestionDialogVisible"
      title="生成补货建议"
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
          <el-descriptions-item label="仓库">{{ currentAlert.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="商品">{{ currentAlert.productCode }} - {{ currentAlert.productName }}</el-descriptions-item>
          <el-descriptions-item label="当前库存">{{ formatNumber(currentAlert.currentQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="安全库存">{{ formatNumber(currentAlert.minQuantity) }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="建议数量" prop="suggestedQty">
          <el-input-number
            v-model="suggestionForm.suggestedQty"
            :min="0.0001"
            :precision="4"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="suggestionForm.supplierId"
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
        <el-form-item label="预计到货">
          <el-date-picker
            v-model="suggestionForm.expectedArrivalDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="suggestionForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="补货说明（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suggestionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="suggestionSubmitLoading" @click="submitSuggestion">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createInventoryReplenishmentSuggestion,
  createInventoryAlertRule,
  getInventoryAlerts,
  ignoreInventoryAlert,
  reactivateInventoryAlert,
  resolveInventoryAlert,
  type InventoryAlertRuleCreateRequest,
  type InventoryAlertQuery,
  type InventoryAlert
} from '@/api/inventory'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { getProducts, type Product } from '@/api/masterdata'
import { getSuppliers, type Supplier } from '@/api/masterdata'
import { formatLocalizedNumber } from '@/utils/locale'

const router = useRouter()

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
const ruleRules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  minQty: [{ required: true, message: '请输入最小库存', trigger: 'blur' }]
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
  suggestedQty: [{ required: true, message: '请输入建议数量', trigger: 'blur' }]
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
    ElMessage.error('加载数据失败')
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
    ElMessage.warning('加载预警统计失败')
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const response = await getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error('加载仓库列表失败')
  }
}

// 加载产品列表
const loadProducts = async () => {
  try {
    const response = await getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    products.value = response.records
  } catch (error) {
    ElMessage.error('加载产品列表失败')
  }
}

// 加载供应商列表
const loadSuppliers = async () => {
  try {
    const response = await getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    suppliers.value = response.records
  } catch (error) {
    ElMessage.error('加载供应商列表失败')
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
      await createInventoryAlertRule(ruleForm)
      ElMessage.success('低库存规则已创建')
      ruleDialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error('创建低库存规则失败')
    } finally {
      ruleSubmitLoading.value = false
    }
  })
}

const resetRuleForm = () => {
  ruleFormRef.value?.clearValidate()
  Object.assign(ruleForm, {
    warehouseId: '',
    productId: '',
    minQty: 0,
    remark: ''
  })
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
      ElMessage.success('补货建议已生成')
      suggestionDialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error('生成补货建议失败')
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
  const actionLabel = status === 'IGNORED' ? '忽略' : '标记已处理'
  try {
    const { value } = await ElMessageBox.prompt(
      `确定${actionLabel}【${row.warehouseName} / ${row.productName}】的低库存预警吗？可填写处置说明。`,
      `${actionLabel}预警`,
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '处置说明（选填）',
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
    ElMessage.success(`已${actionLabel}`)
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`${actionLabel}失败`)
    }
  }
}

const handleReactivate = async (row: InventoryAlert) => {
  try {
    await ElMessageBox.confirm(
      `确定重新激活【${row.warehouseName} / ${row.productName}】的低库存预警吗？`,
      '重新激活预警',
      { type: 'warning', confirmButtonText: '重新激活', cancelButtonText: '取消' }
    )
    await reactivateInventoryAlert({
      warehouseId: row.warehouseId,
      productId: row.productId
    })
    ElMessage.success('预警已重新激活')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重新激活失败')
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
</style>
