<template>
  <div class="inventory-checks-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="盘点单号">
          <el-input
            v-model="queryParams.checkNo"
            placeholder="请输入盘点单号"
            clearable
            style="width: 200px"
          />
        </el-form-item>
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
            <el-option label="已录入" value="COUNTED" />
            <el-option label="已调整" value="ADJUSTED" />
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
      <el-button v-permission="'inventory:check:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新增盘点
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
        <el-table-column prop="checkNo" label="盘点单号" width="180" />
        <el-table-column prop="warehouseName" label="仓库" width="150" />
        <el-table-column prop="checkDate" label="盘点日期" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'COUNTED'" type="warning">已录入</el-tag>
            <el-tag v-else-if="row.status === 'ADJUSTED'" type="success">已调整</el-tag>
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
              v-if="row.status === 'COUNTED'"
              v-permission="'inventory:check:create'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'COUNTED'"
              v-permission="'inventory:check:adjust'"
              link
              type="success"
              @click="handleComplete(row)"
            >
              调整
            </el-button>
            <el-button
              v-if="row.status === 'COUNTED'"
              v-permission="'inventory:check:cancel'"
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

    <!-- 新增/编辑对话框 -->
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
          <el-col :span="12">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select
                v-model="formData.warehouseId"
                placeholder="请选择仓库"
                style="width: 100%"
                :disabled="isView || isEdit"
                @change="handleWarehouseChange"
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
          <el-col :span="12">
            <el-form-item label="盘点日期" prop="checkDate">
              <el-date-picker
                v-model="formData.checkDate"
                type="date"
                placeholder="请选择盘点日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView || isEdit"
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
            :disabled="isView || isEdit"
          />
        </el-form-item>

        <!-- 盘点明细 -->
        <el-divider content-position="left">盘点明细</el-divider>
        <el-button
          v-if="!isView && !isEdit"
          type="primary"
          size="small"
          @click="handleAddItem"
          style="margin-bottom: 10px"
        >
          <el-icon><Plus /></el-icon>
          添加产品
        </el-button>
        <el-table :data="formData.items" border max-height="400">
          <el-table-column label="产品" prop="productId" width="250">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.productId"
                placeholder="请选择产品"
                filterable
                style="width: 100%"
                :disabled="isView || isEdit"
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
          <el-table-column label="产品编码" prop="productCode" width="130" />
          <el-table-column label="产品名称" prop="productName" width="150" />
          <el-table-column label="账面数量" prop="bookQuantity" width="120">
            <template #default="{ row }">
              {{ row.bookQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="实际数量" prop="actualQuantity" width="130">
            <template #default="{ row }">
              <el-input-number
                v-model="row.actualQuantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
                @change="handleQuantityChange(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="差异" prop="difference" width="100">
            <template #default="{ row }">
              <span :class="{ 'text-danger': row.difference < 0, 'text-success': row.difference > 0 }">
                {{ row.difference || 0 }}
              </span>
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
          <el-table-column v-if="!isView && !isEdit" label="操作" width="80" fixed="right">
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
  getInventoryChecks,
  getInventoryCheck,
  createInventoryCheck,
  updateInventoryCheck,
  completeInventoryCheck,
  cancelInventoryCheck,
  type InventoryCheckQuery,
  type InventoryCheckCreateRequest,
  type InventoryCheckUpdateRequest,
  type InventoryCheck,
  type InventoryCheckItem
} from '@/api/inventory'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { getProducts, type Product } from '@/api/masterdata'
import { getInventoryStocks, type InventoryStockQuery } from '@/api/inventory'

// 查询参数
const queryParams = reactive<InventoryCheckQuery>({
  pageNo: 1,
  pageSize: 10,
  checkNo: '',
  warehouseId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryCheck[]>([])
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
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<InventoryCheckCreateRequest & { items: InventoryCheckItem[] }>({
  warehouseId: 0,
  checkDate: '',
  items: [],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  checkDate: [{ required: true, message: '请选择盘点日期', trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryChecks(queryParams)
    tableData.value = response.records.map((check) => ({
      ...check,
      warehouseName: check.warehouseName || warehouseNameById(check.warehouseId)
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
  queryParams.checkNo = ''
  queryParams.warehouseId = undefined
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = '新增库存盘点'
  isView.value = false
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: InventoryCheck) => {
  dialogTitle.value = '编辑库存盘点'
  isView.value = false
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getInventoryCheck(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 查看
const handleView = async (row: InventoryCheck) => {
  dialogTitle.value = '查看库存盘点'
  isView.value = true
  isEdit.value = false
  try {
    const data = await getInventoryCheck(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 调整
const handleComplete = async (row: InventoryCheck) => {
  try {
    await ElMessageBox.confirm('确认根据此盘点差异调整库存吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await completeInventoryCheck(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 取消
const handleCancel = async (row: InventoryCheck) => {
  try {
    await ElMessageBox.confirm('确认取消此库存盘点吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelInventoryCheck(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 仓库变化 - 加载库存数据
const handleWarehouseChange = async () => {
  if (!formData.warehouseId) return

  try {
    const stockQuery: InventoryStockQuery = {
      pageNo: 1,
      pageSize: 1000,
      warehouseId: formData.warehouseId
    }
    const response = await getInventoryStocks(stockQuery)

    // 自动填充盘点明细
    formData.items = response.records.map(stock => ({
      productId: stock.productId,
      productCode: stock.productCode,
      productName: stock.productName,
      bookQuantity: stock.quantity,
      actualQuantity: undefined,
      difference: undefined,
      remark: ''
    }))
  } catch (error) {
    ElMessage.error('加载库存数据失败')
  }
}

// 添加明细
const handleAddItem = () => {
  formData.items.push({
    productId: 0,
    productCode: '',
    productName: '',
    bookQuantity: 0,
    actualQuantity: undefined,
    difference: undefined,
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
    item.productCode = product.code || product.productCode
    item.productName = product.name || product.productName
    item.bookQuantity = 0 // 需要从库存查询实际账面数量
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

// 数量变化
const handleQuantityChange = (item: InventoryCheckItem) => {
  if (item.actualQuantity !== undefined) {
    item.difference = item.actualQuantity - (item.bookQuantity || 0)
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.items.length === 0) {
        ElMessage.warning('请至少添加一条盘点明细')
        return
      }

      submitLoading.value = true
      try {
        if (isEdit.value) {
          // 编辑模式
          const updateData: InventoryCheckUpdateRequest = {
            items: formData.items
          }
          await updateInventoryCheck(currentId.value, updateData)
        } else {
          // 新增模式
          await createInventoryCheck(formData)
        }
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
  formData.warehouseId = 0
  formData.checkDate = new Date().toISOString().split('T')[0]
  formData.items = []
  formData.remark = ''
  currentId.value = ''
  formRef.value?.clearValidate()
}

onMounted(async () => {
  await Promise.all([
    loadWarehouses(),
    loadProducts()
  ])
  loadData()
})
</script>

<style scoped lang="scss">
.inventory-checks-container {
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

  .text-danger {
    color: #f56c6c;
    font-weight: bold;
  }

  .text-success {
    color: #67c23a;
    font-weight: bold;
  }
}
</style>
