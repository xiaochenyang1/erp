<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="BOM编码">
          <el-input v-model="queryForm.bomCode" placeholder="请输入BOM编码" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="产品">
          <el-select
            v-model="queryForm.productId"
            placeholder="请选择产品"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.productName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>BOM管理</span>
          <el-button v-permission="'production:bom:manage'" type="primary" :icon="Plus" @click="handleAdd">新增BOM</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="bomCode" label="BOM编码" width="150" />
        <el-table-column label="产品" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ productLabelById(row.productId) }}
          </template>
        </el-table-column>
        <el-table-column prop="baseQty" label="基准数量" width="120" align="right">
          <template #default="{ row }">
            {{ row.baseQty }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:bom:manage'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="1000px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-select
                v-model="formData.productId"
                placeholder="请选择产品"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in productOptions"
                  :key="item.id"
                  :label="productLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基准数量" prop="baseQty">
              <el-input-number
                v-model="formData.baseQty"
                :min="1"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">物料清单</el-divider>

        <el-form-item label="物料明细" required>
          <el-table :data="formData.items" border style="width: 100%">
            <el-table-column prop="materialId" label="物料" width="250">
              <template #default="{ row }">
                <el-select
                  v-model="row.materialId"
                  placeholder="请选择物料"
                  filterable
                  style="width: 100%"
                  @change="(val) => handleMaterialChange(val, row)"
                >
                  <el-option
                    v-for="item in productOptions"
                    :key="item.id"
                    :label="productLabel(item)"
                    :value="item.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="用量" width="150">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="0"
                  :precision="4"
                  controls-position="right"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column prop="scrapRate" label="损耗率(%)" width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.scrapRate"
                  :min="0"
                  :max="100"
                  :precision="2"
                  controls-position="right"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.remark" placeholder="备注" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDeleteItem($index)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            type="primary"
            :icon="Plus"
            style="margin-top: 10px"
            @click="handleAddItem"
          >
            添加物料
          </el-button>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="BOM详情" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="BOM编码">{{ viewData.bomCode }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ productLabelById(viewData.productId) }}</el-descriptions-item>
        <el-descriptions-item label="基准数量">{{ viewData.baseQty }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(viewData.status)">
            {{ getStatusLabel(viewData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ viewData.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewData.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <h4>物料清单</h4>
      <el-table :data="viewData.items" border stripe style="margin-top: 10px">
        <el-table-column label="物料" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ materialLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="用量" width="120" align="right">
          <template #default="{ row }">
            {{ row.quantity }} {{ materialUnit(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="scrapRate" label="损耗率" width="100" align="right">
          <template #default="{ row }">
            {{ row.scrapRate || 0 }}%
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, Delete } from '@element-plus/icons-vue'
import {
  getBOMs,
  getBOM,
  createBOM,
  updateBOM,
  type BOM,
  type BOMItem
} from '@/api/production'
import { getProducts, type Product } from '@/api/masterdata'

// 查询表单
const queryForm = reactive({
  bomCode: '',
  productId: undefined as string | number | undefined,
  status: ''
})

// 表格数据
const loading = ref(false)
const tableData = ref<BOM[]>([])

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 产品选项
const productOptions = ref<Product[]>([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | number | undefined,
  productId: undefined as string | number | undefined,
  baseQty: 1,
  items: [] as BOMItem[],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  baseQty: [{ required: true, message: '请输入基准数量', trigger: 'blur' }]
}

// 查看对话框
const viewDialogVisible = ref(false)
const viewData = ref<BOM>({} as BOM)

// 加载产品选项
const loadProducts = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const res = await getProducts(optionPageQuery)
    productOptions.value = res.records || []
  } catch (error) {
    console.error('加载产品列表失败:', error)
  }
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...queryForm,
      pageNo: pagination.page,
      pageSize: pagination.size
    }
    const res = await getBOMs(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载BOM列表失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  pagination.page = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryForm.bomCode = ''
  queryForm.productId = undefined
  queryForm.status = ''
  pagination.page = 1
  loadData()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增BOM'
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: BOM) => {
  dialogTitle.value = '编辑BOM'
  try {
    const res = await getBOM(row.id)
    Object.assign(formData, {
      id: res.id,
      productId: res.productId,
      baseQty: res.baseQty,
      items: res.items || [],
      remark: res.remark
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载BOM详情失败')
  }
}

// 查看
const handleView = async (row: BOM) => {
  try {
    const res = await getBOM(row.id)
    viewData.value = res
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载BOM详情失败')
  }
}

// 物料变化
const handleMaterialChange = (materialId: string | number, row: BOMItem) => {
  const material = productOptions.value.find((p) => String(p.id) === String(materialId))
  if (material) {
    row.materialCode = material.productCode
    row.materialName = material.productName
    row.unit = material.unitName || ''
  }
}

// 添加物料
const handleAddItem = () => {
  formData.items.push({
    materialId: '',
    materialCode: '',
    materialName: '',
    quantity: 1,
    unit: '',
    scrapRate: 0,
    remark: ''
  })
}

// 删除物料
const handleDeleteItem = (index: number) => {
  formData.items.splice(index, 1)
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return

  if (formData.items.length === 0) {
    ElMessage.warning('请添加物料明细')
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updateBOM(formData.id, formData)
        ElMessage.success('更新成功')
      } else {
        await createBOM(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    productId: undefined,
    baseQty: 1,
    items: [],
    remark: ''
  })
}

const productLabel = (product: Product) => {
  const code = product.productCode || product.code || ''
  const name = product.productName || product.name || ''
  return code && name ? `${code} - ${name}` : name || code || `产品${product.id}`
}

const productById = (id?: string | number) => {
  if (id == null || id === '') return undefined
  return productOptions.value.find((product) => String(product.id) === String(id))
}

const productLabelById = (id?: string | number) => {
  const product = productById(id)
  return product ? productLabel(product) : id || '-'
}

const materialLabel = (row: BOMItem) => {
  const product = productById(row.materialId ?? row.materialProductId)
  if (product) return productLabel(product)
  const code = row.materialCode || ''
  const name = row.materialName || ''
  return code && name ? `${code} - ${name}` : name || code || row.materialId || '-'
}

const materialUnit = (row: BOMItem) => {
  const product = productById(row.materialId ?? row.materialProductId)
  return product?.unitName || product?.unit || row.unit || ''
}

// 获取状态标签
const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: '启用',
    DISABLED: '已停用'
  }
  return map[status] || status
}

// 获取状态类型
const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    ACTIVE: 'success',
    DISABLED: 'danger'
  }
  return map[status] || ''
}

onMounted(() => {
  loadProducts()
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
