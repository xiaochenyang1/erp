<template>
  <div class="inventory-transfers-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="调拨单号">
          <el-input
            v-model="queryParams.transferNo"
            placeholder="请输入调拨单号"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="调出仓库">
          <el-select
            v-model="queryParams.fromWarehouseId"
            placeholder="请选择调出仓库"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse)"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调入仓库">
          <el-select
            v-model="queryParams.toWarehouseId"
            placeholder="请选择调入仓库"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse)"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 150px"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
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

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'inventory:transfer:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新增调拨
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
        <el-table-column prop="transferNo" label="调拨单号" width="180" />
        <el-table-column prop="fromWarehouseName" label="调出仓库" width="140" />
        <el-table-column prop="toWarehouseName" label="调入仓库" width="140" />
        <el-table-column prop="transferDate" label="调拨日期" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'COMPLETED'" type="success">已完成</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">已取消</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              查看
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:transfer:post'"
              link
              type="success"
              @click="handleShip(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:transfer:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
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
      width="80%"
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
            <el-form-item label="调出仓库" prop="fromWarehouseId">
              <el-select
                v-model="formData.fromWarehouseId"
                placeholder="请选择调出仓库"
                style="width: 100%"
                :disabled="isView"
              >
                <el-option
                  v-for="warehouse in warehouses"
                  :key="warehouse.id"
                  :label="warehouseLabel(warehouse)"
                  :value="warehouse.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调入仓库" prop="toWarehouseId">
              <el-select
                v-model="formData.toWarehouseId"
                placeholder="请选择调入仓库"
                style="width: 100%"
                :disabled="isView"
              >
                <el-option
                  v-for="warehouse in warehouses"
                  :key="warehouse.id"
                  :label="warehouseLabel(warehouse)"
                  :value="warehouse.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调拨日期" prop="transferDate">
              <el-date-picker
                v-model="formData.transferDate"
                type="date"
                placeholder="请选择调拨日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 调拨明细 -->
        <el-divider content-position="left">调拨明细</el-divider>
        <el-button
          v-if="!isView"
          type="primary"
          size="small"
          @click="handleAddItem"
          style="margin-bottom: 10px"
        >
          <el-icon><Plus /></el-icon>
          添加产品
        </el-button>
        <el-table :data="formData.items" border>
          <el-table-column label="产品" prop="productId" width="250">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.productId"
                placeholder="请选择产品"
                filterable
                style="width: 100%"
                :disabled="isView"
                @change="handleProductChange($index)"
              >
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="productLabel(product)"
                  :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="产品编码" prop="productCode" width="150" />
          <el-table-column label="产品名称" prop="productName" width="150" />
          <el-table-column label="调拨数量" prop="quantity" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark">
            <template #default="{ row }">
              <el-input
                v-model="row.remark"
                placeholder="请输入备注"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                @click="handleDeleteItem($index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getInventoryTransfers,
  getInventoryTransfer,
  createInventoryTransfer,
  shipInventoryTransfer,
  cancelInventoryTransfer,
  type InventoryTransferQuery,
  type InventoryTransferCreateRequest,
  type InventoryTransfer
} from '@/api/inventory'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { getProducts, type Product } from '@/api/masterdata'

// 查询参数
const queryParams = reactive<InventoryTransferQuery>({
  pageNo: 1,
  pageSize: 10,
  transferNo: '',
  fromWarehouseId: undefined,
  toWarehouseId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryTransfer[]>([])
const total = ref(0)

// 仓库列表
const warehouses = ref<Warehouse[]>([])

// 产品列表
const products = ref<Product[]>([])

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isView = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<InventoryTransferCreateRequest>({
  fromWarehouseId: 0,
  toWarehouseId: 0,
  transferDate: '',
  items: [],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  fromWarehouseId: [{ required: true, message: '请选择调出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓库', trigger: 'change' }],
  transferDate: [{ required: true, message: '请选择调拨日期', trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryTransfers(queryParams)
    tableData.value = response.records.map((transfer) => ({
      ...transfer,
      fromWarehouseName: transfer.fromWarehouseName || warehouseNameById(transfer.fromWarehouseId),
      toWarehouseName: transfer.toWarehouseName || warehouseNameById(transfer.toWarehouseId)
    }))
    total.value = response.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const response = await getWarehouses(optionPageQuery)
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error('加载仓库列表失败')
  }
}

// 加载产品列表
const loadProducts = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const response = await getProducts(optionPageQuery)
    products.value = response.records
  } catch (error) {
    ElMessage.error('加载产品列表失败')
  }
}

// 查询
const handleQuery = () => {
  if (dateRange.value) {
    queryParams.startDate = dateRange.value[0]
    queryParams.endDate = dateRange.value[1]
  } else {
    queryParams.startDate = ''
    queryParams.endDate = ''
  }
  queryParams.pageNo = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.transferNo = ''
  queryParams.fromWarehouseId = undefined
  queryParams.toWarehouseId = undefined
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = '新增库存调拨'
  isView.value = false
  resetForm()
  dialogVisible.value = true
}

// 查看
const handleView = async (row: InventoryTransfer) => {
  dialogTitle.value = '查看库存调拨'
  isView.value = true
  try {
    const data = await getInventoryTransfer(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 过账
const handleShip = async (row: InventoryTransfer) => {
  try {
    await ElMessageBox.confirm('确认过账此库存调拨吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await shipInventoryTransfer(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 取消
const handleCancel = async (row: InventoryTransfer) => {
  try {
    await ElMessageBox.confirm('确认取消此调拨单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelInventoryTransfer(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 添加明细
const handleAddItem = () => {
  formData.items.push({
    productId: 0,
    productCode: '',
    productName: '',
    quantity: 0,
    unitCost: 0,
    remark: ''
  })
}

// 删除明细
const handleDeleteItem = (index: number) => {
  formData.items.splice(index, 1)
}

// 产品变化
const handleProductChange = (index: number) => {
  const item = formData.items[index]
  const product = products.value.find(p => p.id === item.productId)
  if (product) {
    item.productCode = product.code || product.productCode || ''
    item.productName = product.name || product.productName || ''
    item.unitCost = product.purchasePrice ?? item.unitCost ?? 0
  }
}

const warehouseLabel = (warehouse: Warehouse) => warehouse.name || warehouse.warehouseName || `仓库${warehouse.id}`

const warehouseNameById = (warehouseId: string | number) => {
  const warehouse = warehouses.value.find(item => String(item.id) === String(warehouseId))
  return warehouse ? warehouseLabel(warehouse) : ''
}

const productLabel = (product: Product) => {
  const code = product.code || product.productCode || ''
  const name = product.name || product.productName || ''
  return code && name ? `${code} - ${name}` : name || code || `产品${product.id}`
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.fromWarehouseId === formData.toWarehouseId) {
        ElMessage.warning('调出仓库和调入仓库不能相同')
        return
      }

      if (formData.items.length === 0) {
        ElMessage.warning('请至少添加一条调拨明细')
        return
      }

      submitLoading.value = true
      try {
        await createInventoryTransfer(formData)
        ElMessage.success('操作成功')
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error('操作失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.fromWarehouseId = 0
  formData.toWarehouseId = 0
  formData.transferDate = new Date().toISOString().split('T')[0]
  formData.items = []
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(async () => {
  await Promise.all([loadWarehouses(), loadProducts()])
  loadData()
})
</script>

<style scoped lang="scss">
.inventory-transfers-container {
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
</style>
