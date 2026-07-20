<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="岗位编码">
          <el-input v-model="queryForm.code" placeholder="请输入岗位编码" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="queryForm.name" placeholder="请输入岗位名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
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
          <span>岗位管理</span>
          <el-button v-permission="'system:post:create'" type="primary" :icon="Plus" @click="handleAdd">新增岗位</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="code" label="岗位编码" width="150" />
        <el-table-column prop="name" label="岗位名称" width="200" />
        <el-table-column prop="orderNum" label="排序" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:post:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:post:disable'" type="danger" link :icon="Delete" @click="handleDisable(row)">
              停用
            </el-button>
            <el-button v-else v-permission="'system:post:enable'" type="success" link @click="handleEnable(row)">
              启用
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
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="岗位编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入岗位编码" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-tree-select
            v-model="formData.deptId"
            :data="deptOptions"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择所属部门"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="formData.orderNum" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="INACTIVE">停用</el-radio>
          </el-radio-group>
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
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getDeptTree,
  getPosts,
  getPost,
  createPost,
  updatePost,
  deletePost,
  enablePost,
  type Dept,
  type Post
} from '@/api/system'

// 查询表单
const queryForm = reactive({
  code: '',
  name: '',
  status: ''
})

// 表格数据
const loading = ref(false)
const tableData = ref<Post[]>([])
const deptOptions = ref<Dept[]>([])

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | undefined,
  deptId: undefined as string | undefined,
  code: '',
  name: '',
  orderNum: 0,
  status: 'ACTIVE',
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getPosts(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载岗位列表失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadDeptOptions = async () => {
  deptOptions.value = await getDeptTree()
}

// 查询
const handleQuery = () => {
  pagination.page = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryForm.code = ''
  queryForm.name = ''
  queryForm.status = ''
  pagination.page = 1
  loadData()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增岗位'
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: Post) => {
  dialogTitle.value = '编辑岗位'
  try {
    const res = await getPost(row.id)
    Object.assign(formData, {
      id: res.id,
      deptId: res.deptId,
      code: res.code,
      name: res.name,
      orderNum: res.orderNum,
      status: res.status,
      remark: res.remark
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载岗位详情失败')
  }
}

// 停用
const handleDisable = async (row: Post) => {
  try {
    await ElMessageBox.confirm(`确定要停用岗位"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    await deletePost(row.id)
    ElMessage.success('停用成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

// 启用
const handleEnable = async (row: Post) => {
  try {
    await ElMessageBox.confirm(`确定要启用岗位"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    await enablePost(row.id)
    ElMessage.success('启用成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updatePost(formData.id, formData)
        ElMessage.success('更新成功')
      } else {
        await createPost(formData)
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
    deptId: undefined,
    code: '',
    name: '',
    orderNum: 0,
    status: 'ACTIVE',
    remark: ''
  })
}

onMounted(() => {
  loadDeptOptions()
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
