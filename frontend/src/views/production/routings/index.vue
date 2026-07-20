<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="关键字">
          <el-input
            v-model="queryForm.keyword"
            placeholder="工艺路线编码/名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 120px">
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
          <span>工艺路线</span>
          <el-button
            v-permission="'production:routing:create'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >新增工艺路线</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="routingCode" label="编码" width="170" />
        <el-table-column prop="routingName" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="BOM" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bomNo || row.bomId }}</template>
        </el-table-column>
        <el-table-column label="工序数" width="90" align="right">
          <template #default="{ row }">{{ row.operations?.length || 0 }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:routing:update'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'production:routing:enable'"
              type="success"
              link
              @click="handleEnable(row)"
            >启用</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:routing:disable'"
              type="danger"
              link
              @click="handleDisable(row)"
            >停用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="handleQuery"
        @current-change="loadData"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1000px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路线编码" prop="routingCode">
              <el-input
                v-model="formData.routingCode"
                placeholder="请输入工艺路线编码"
                :disabled="isEdit"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路线名称" prop="routingName">
              <el-input v-model="formData.routingName" placeholder="请输入工艺路线名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="BOM" prop="bomId">
          <el-select
            v-model="formData.bomId"
            placeholder="请选择BOM"
            filterable
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option
              v-for="item in bomOptions"
              :key="item.id"
              :label="bomLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">工序清单</el-divider>

        <el-form-item label="工序明细" required>
          <el-table :data="formData.operations" border style="width: 100%">
            <el-table-column type="index" label="序" width="50" />
            <el-table-column label="工序编码" width="150">
              <template #default="{ row }">
                <el-input v-model="row.operationCode" placeholder="工序编码" />
              </template>
            </el-table-column>
            <el-table-column label="工序名称" width="160">
              <template #default="{ row }">
                <el-input v-model="row.operationName" placeholder="工序名称" />
              </template>
            </el-table-column>
            <el-table-column label="工作中心" min-width="200">
              <template #default="{ row }">
                <el-select v-model="row.workCenterId" placeholder="选择工作中心" filterable style="width: 100%">
                  <el-option
                    v-for="wc in workCenterOptions"
                    :key="wc.id"
                    :label="`${wc.workCenterCode} - ${wc.workCenterName}`"
                    :value="wc.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="标准工时(分)" width="140">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.standardMinutes"
                  :min="0.01"
                  :precision="2"
                  :controls="false"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.remark" placeholder="选填" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button type="danger" link :icon="Delete" @click="handleDeleteOperation($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" :icon="Plus" style="margin-top: 10px" @click="handleAddOperation">添加工序</el-button>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="工艺路线详情" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="路线编码">{{ viewData.routingCode }}</el-descriptions-item>
        <el-descriptions-item label="路线名称">{{ viewData.routingName }}</el-descriptions-item>
        <el-descriptions-item label="BOM">{{ viewData.bomNo || viewData.bomId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(viewData.status)">{{ getStatusLabel(viewData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <h4>工序清单</h4>
      <el-table :data="viewData.operations || []" border stripe style="margin-top: 10px">
        <el-table-column prop="lineNo" label="序" width="60" />
        <el-table-column prop="operationCode" label="工序编码" width="140" />
        <el-table-column prop="operationName" label="工序名称" min-width="150" />
        <el-table-column label="工作中心" min-width="180">
          <template #default="{ row }">{{ workCenterLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="standardMinutes" label="标准工时(分)" width="130" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, Delete } from '@element-plus/icons-vue'
import {
  getRoutings,
  getRouting,
  createRouting,
  updateRouting,
  enableRouting,
  disableRouting,
  getWorkCenters,
  getBOMs,
  type Routing,
  type RoutingOperation,
  type WorkCenter,
  type BOM
} from '@/api/production'

const queryForm = reactive({
  keyword: '',
  status: ''
})

const loading = ref(false)
const tableData = ref<Routing[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const workCenterOptions = ref<WorkCenter[]>([])
const bomOptions = ref<BOM[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | number | undefined,
  routingCode: '',
  routingName: '',
  bomId: undefined as string | number | undefined,
  remark: '',
  operations: [] as RoutingOperation[]
})
const isEdit = computed(() => formData.id != null)

const formRules: FormRules = {
  routingCode: [{ required: true, message: '请输入工艺路线编码', trigger: 'blur' }],
  routingName: [{ required: true, message: '请输入工艺路线名称', trigger: 'blur' }],
  bomId: [{ required: true, message: '请选择BOM', trigger: 'change' }]
}

const viewDialogVisible = ref(false)
const viewData = ref<Routing>({} as Routing)

const loadOptions = async () => {
  try {
    const [wcRes, bomRes] = await Promise.all([
      getWorkCenters({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
      getBOMs({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    ])
    workCenterOptions.value = wcRes.records || []
    bomOptions.value = bomRes.records || []
  } catch (error) {
    console.error('加载工作中心/BOM选项失败:', error)
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getRoutings({
      ...queryForm,
      pageNo: pagination.page,
      pageSize: pagination.size
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载工艺路线失败:', error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.keyword = ''
  queryForm.status = ''
  pagination.page = 1
  loadData()
}

const handleAdd = () => {
  dialogTitle.value = '新增工艺路线'
  dialogVisible.value = true
}

const handleEdit = async (row: Routing) => {
  dialogTitle.value = '编辑工艺路线'
  try {
    const res = await getRouting(row.id)
    Object.assign(formData, {
      id: res.id,
      routingCode: res.routingCode,
      routingName: res.routingName,
      bomId: res.bomId,
      remark: res.remark || '',
      operations: (res.operations || []).map((op) => ({ ...op }))
    })
    dialogVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const handleView = async (row: Routing) => {
  try {
    viewData.value = await getRouting(row.id)
    viewDialogVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const handleAddOperation = () => {
  formData.operations.push({
    operationCode: '',
    operationName: '',
    workCenterId: '',
    standardMinutes: 1,
    remark: ''
  })
}

const handleDeleteOperation = (index: number) => {
  formData.operations.splice(index, 1)
}

const handleEnable = async (row: Routing) => {
  try {
    await ElMessageBox.confirm(`确认启用工艺路线「${row.routingName}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await enableRouting(row.id)
    ElMessage.success('已启用')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

const handleDisable = async (row: Routing) => {
  try {
    await ElMessageBox.confirm(`确认停用工艺路线「${row.routingName}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await disableRouting(row.id)
    ElMessage.success('已停用')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  if (formData.operations.length === 0) {
    ElMessage.warning('请至少添加一道工序')
    return
  }
  for (const [i, op] of formData.operations.entries()) {
    if (!op.operationCode || !op.operationName || !op.workCenterId || !op.standardMinutes) {
      ElMessage.warning(`第 ${i + 1} 道工序：编码、名称、工作中心和标准工时均必填`)
      return
    }
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const operations = formData.operations.map((op) => ({
        operationCode: op.operationCode,
        operationName: op.operationName,
        workCenterId: op.workCenterId,
        standardMinutes: op.standardMinutes,
        remark: op.remark
      }))
      if (formData.id != null) {
        await updateRouting(formData.id, {
          routingName: formData.routingName,
          remark: formData.remark,
          operations
        })
        ElMessage.success('更新成功')
      } else {
        await createRouting({
          routingCode: formData.routingCode,
          routingName: formData.routingName,
          bomId: formData.bomId!,
          remark: formData.remark,
          operations
        })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // 拦截器已提示
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    routingCode: '',
    routingName: '',
    bomId: undefined,
    remark: '',
    operations: []
  })
}

const bomLabel = (bom: BOM) => {
  const code = bom.bomCode || bom.bomNo || ''
  const name = bom.productName || ''
  return code && name ? `${code} - ${name}` : code || name || `BOM${bom.id}`
}

const workCenterLabel = (op: RoutingOperation) => {
  if (op.workCenterCode || op.workCenterName) {
    return `${op.workCenterCode || ''} ${op.workCenterName || ''}`.trim()
  }
  const wc = workCenterOptions.value.find((w) => String(w.id) === String(op.workCenterId))
  return wc ? `${wc.workCenterCode} - ${wc.workCenterName}` : op.workCenterId || '-'
}

const getStatusLabel = (status: string) => ({ ACTIVE: '启用', DISABLED: '已停用' }[status] || status)
const getStatusType = (status: string) =>
  (({ ACTIVE: 'success', DISABLED: 'danger' } as Record<string, string>)[status] || 'info') as
    'primary' | 'success' | 'warning' | 'info' | 'danger'

onMounted(() => {
  loadOptions()
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
