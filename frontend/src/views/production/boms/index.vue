<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('productionBom.bomCode')">
          <el-input v-model="queryForm.bomCode" :placeholder="t('productionBom.bomCodePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('productionBom.product')">
          <el-select
            v-model="queryForm.productId"
            :placeholder="t('productionBom.selectProduct')"
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
        <el-form-item :label="t('productionBom.statusLabel')">
          <el-select v-model="queryForm.status" :placeholder="t('productionBom.select')" clearable style="width: 120px">
            <el-option :label="t('productionBom.status.active')" value="ACTIVE" />
            <el-option :label="t('productionBom.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('productionBom.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('productionBom.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('productionBom.title') }}</span>
          <el-button v-permission="'production:bom:manage'" type="primary" :icon="Plus" @click="handleAdd">{{ t('productionBom.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="bomCode" :label="t('productionBom.bomCode')" width="150" />
        <el-table-column :label="t('productionBom.product')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ productLabelById(row.productId) }}
          </template>
        </el-table-column>
        <el-table-column prop="baseQty" :label="t('productionBom.baseQuantity')" width="120" align="right">
          <template #default="{ row }">
            {{ row.baseQty }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('productionBom.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('productionBom.remark')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('productionBom.createdAt')" width="180">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('productionBom.actions')" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">{{ t('productionBom.view') }}</el-button>
            <el-button type="primary" link @click="handlePrint(row)">{{ t('productionBom.print') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:bom:manage'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ t('productionBom.edit') }}
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
            <el-form-item :label="t('productionBom.product')" prop="productId">
              <el-select
                v-model="formData.productId"
                :placeholder="t('productionBom.selectProduct')"
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
            <el-form-item :label="t('productionBom.baseQuantity')" prop="baseQty">
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

        <el-divider content-position="left">{{ t('productionBom.materialList') }}</el-divider>

        <el-form-item :label="t('productionBom.materialDetails')" required>
          <el-table :data="formData.items" border style="width: 100%">
            <el-table-column prop="materialId" :label="t('productionBom.material')" width="250">
              <template #default="{ row }">
                <el-select
                  v-model="row.materialId"
                  :placeholder="t('productionBom.selectMaterial')"
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
            <el-table-column prop="quantity" :label="t('productionBom.quantity')" width="150">
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
            <el-table-column prop="scrapRate" :label="t('productionBom.scrapRatePercent')" width="120">
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
            <el-table-column prop="remark" :label="t('productionBom.remark')" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.remark" :placeholder="t('productionBom.remark')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('productionBom.actions')" width="80" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDeleteItem($index)"
                >
                  {{ t('productionBom.delete') }}
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
            {{ t('productionBom.addMaterial') }}
          </el-button>
        </el-form-item>

        <el-form-item :label="t('productionBom.remark')" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            :placeholder="t('productionBom.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('productionBom.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ t('productionBom.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" :title="t('productionBom.detailTitle')" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('productionBom.bomCode')">{{ viewData.bomCode }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionBom.product')">{{ productLabelById(viewData.productId) }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionBom.baseQuantity')">{{ viewData.baseQty }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionBom.statusLabel')">
          <el-tag :type="getStatusType(viewData.status)">
            {{ getStatusLabel(viewData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('productionBom.createdBy')">{{ viewData.createdBy }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionBom.createdAt')">{{ formatLocalizedDateTime(viewData.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionBom.remark')" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <h4>{{ t('productionBom.materialList') }}</h4>
      <el-table :data="viewData.items" border stripe style="margin-top: 10px">
        <el-table-column :label="t('productionBom.material')" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ materialLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" :label="t('productionBom.quantity')" width="120" align="right">
          <template #default="{ row }">
            {{ row.quantity }} {{ materialUnit(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="scrapRate" :label="t('productionBom.scrapRate')" width="100" align="right">
          <template #default="{ row }">
            {{ row.scrapRate || 0 }}%
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('productionBom.remark')" min-width="150" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="viewDialogVisible = false">{{ t('productionBom.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { formatLocalizedDateTime } from '@/utils/locale'
import { printProductionBom } from '@/utils/bizPrint'

const { t } = useI18n()

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
const formRules = computed<FormRules>(() => ({
  productId: [{ required: true, message: t('productionBom.validation.product'), trigger: 'change' }],
  baseQty: [{ required: true, message: t('productionBom.validation.baseQuantity'), trigger: 'blur' }]
}))

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
    console.error(t('productionBom.message.productsLoadFailed'), error)
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
    console.error(t('productionBom.message.listLoadFailed'), error)
    ElMessage.error(t('productionBom.message.loadFailed'))
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
  dialogTitle.value = t('productionBom.dialog.create')
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: BOM) => {
  dialogTitle.value = t('productionBom.dialog.edit')
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
    ElMessage.error(t('productionBom.message.detailLoadFailed'))
  }
}

// 查看
const handleView = async (row: BOM) => {
  try {
    const res = await getBOM(row.id)
    viewData.value = res
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('productionBom.message.detailLoadFailed'))
  }
}

const handlePrint = async (row: BOM) => {
  try {
    const detail = await getBOM(row.id)
    printProductionBom({
      ...detail,
      productCode: productById(detail.productId)?.productCode || productById(detail.productId)?.code,
      productName: productLabelById(detail.productId),
      items: (detail.items || []).map((item) => ({
        ...item,
        materialCode: item.materialCode || productById(item.materialId ?? item.materialProductId)?.productCode,
        materialName: item.materialName || productById(item.materialId ?? item.materialProductId)?.productName,
        unit: materialUnit(item)
      }))
    })
  } catch {
    ElMessage.error(t('productionBom.message.printLoadFailed'))
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
    ElMessage.warning(t('productionBom.validation.materials'))
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updateBOM(formData.id, formData)
        ElMessage.success(t('productionBom.message.updated'))
      } else {
        await createBOM(formData)
        ElMessage.success(t('productionBom.message.created'))
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(t('productionBom.message.actionFailed'))
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
  return code && name ? `${code} - ${name}` : name || code || t('productionBom.productFallback', { id: product.id })
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
    ACTIVE: t('productionBom.status.active'),
    DISABLED: t('productionBom.status.disabled')
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
