<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
        <el-form :model="queryForm" inline>
          <el-form-item label="科目编码">
          <el-input v-model="queryForm.subjectCode" placeholder="请输入科目编码" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="科目名称">
          <el-input v-model="queryForm.subjectName" placeholder="请输入科目名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="科目类别">
          <el-select v-model="queryForm.subjectType" placeholder="请选择" clearable style="width: 150px">
            <el-option label="资产" value="ASSET" />
            <el-option label="负债" value="LIABILITY" />
            <el-option label="权益" value="EQUITY" />
            <el-option label="收入" value="REVENUE" />
            <el-option label="费用" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>会计科目管理</span>
          <el-button v-permission="'finance:subject:manage'" type="primary" :icon="Plus" @click="handleAdd">新增科目</el-button>
        </div>
      </template>

      <!-- 树形表格 -->
      <el-table
        v-loading="loading"
        :data="subjectTree"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="code" label="科目编码" width="180" />
        <el-table-column prop="name" label="科目名称" width="200" />
        <el-table-column prop="category" label="科目类别" width="120">
          <template #default="{ row }">
            <el-tag :type="getCategoryType(row.category)">
              {{ getCategoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级次" width="80" align="center" />
        <el-table-column prop="isLeaf" label="末级科目" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isLeaf ? 'success' : 'info'" size="small">
              {{ row.isLeaf ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'finance:subject:manage'" type="primary" link :icon="Plus" @click="handleAddChild(row)">
              新增下级
            </el-button>
            <el-button v-permission="'finance:subject:manage'" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'finance:subject:manage'"
              type="warning"
              link
              :icon="CircleClose"
              @click="handleDisable(row)"
            >
              停用
            </el-button>
            <el-button
              v-else
              v-permission="'finance:subject:manage'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handleEnable(row)"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-form-item label="上级科目" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="subjectTreeOptions"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择上级科目（不选则为一级科目）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="科目编码" prop="subjectCode">
          <el-input v-model="formData.subjectCode" placeholder="请输入科目编码" :disabled="Boolean(formData.id)" />
        </el-form-item>
        <el-form-item label="科目名称" prop="subjectName">
          <el-input v-model="formData.subjectName" placeholder="请输入科目名称" />
        </el-form-item>
        <el-form-item label="科目类别" prop="subjectType">
          <el-select v-model="formData.subjectType" placeholder="请选择科目类别" style="width: 100%">
            <el-option label="资产" value="ASSET" />
            <el-option label="负债" value="LIABILITY" />
            <el-option label="权益" value="EQUITY" />
            <el-option label="收入" value="REVENUE" />
            <el-option label="费用" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="余额方向" prop="balanceDirection">
          <el-radio-group v-model="formData.balanceDirection">
            <el-radio value="DEBIT">借方</el-radio>
            <el-radio value="CREDIT">贷方</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
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
import { Search, Refresh, Plus, Edit, CircleClose, CircleCheck } from '@element-plus/icons-vue'
import {
  getAccountSubjects,
  getAccountSubjectTree,
  getAccountSubject,
  createAccountSubject,
  updateAccountSubject,
  enableAccountSubject,
  disableAccountSubject,
  type AccountSubject
} from '@/api/finance'

// 查询表单
const queryForm = reactive({
  subjectCode: '',
  subjectName: '',
  subjectType: '',
  status: ''
})

// 表格数据
const loading = ref(false)
const subjectTree = ref<AccountSubject[]>([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | undefined,
  parentId: undefined as string | undefined,
  subjectCode: '',
  subjectName: '',
  subjectType: '',
  balanceDirection: 'DEBIT',
  remark: ''
})

// 树形选择器选项
const subjectTreeOptions = ref<AccountSubject[]>([])

// 表单验证规则
const formRules: FormRules = {
  subjectCode: [{ required: true, message: '请输入科目编码', trigger: 'blur' }],
  subjectName: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  subjectType: [{ required: true, message: '请选择科目类别', trigger: 'change' }],
  balanceDirection: [{ required: true, message: '请选择余额方向', trigger: 'change' }]
}

const hasSubjectQuery = () => {
  return Boolean(queryForm.subjectCode || queryForm.subjectName || queryForm.subjectType || queryForm.status)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const subjects = await getAccountSubjectTree()
    subjectTreeOptions.value = subjects || []
    if (hasSubjectQuery()) {
      const page = await getAccountSubjects({
        ...queryForm,
        pageNo: 1,
        pageSize: 200,
      })
      subjectTree.value = page.records || []
    } else {
      subjectTree.value = subjects || []
    }
  } catch (error) {
    console.error('加载科目树失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  loadData()
}

// 重置
const handleReset = () => {
  queryForm.subjectCode = ''
  queryForm.subjectName = ''
  queryForm.subjectType = ''
  queryForm.status = ''
  loadData()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增科目'
  dialogVisible.value = true
}

// 新增下级
const handleAddChild = (row: AccountSubject) => {
  dialogTitle.value = '新增下级科目'
  formData.parentId = row.id
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: AccountSubject) => {
  dialogTitle.value = '编辑科目'
  try {
    const subject = await getAccountSubject(row.id)
    Object.assign(formData, {
      id: subject.id,
      parentId: subject.parentId,
      subjectCode: subject.subjectCode || subject.code || '',
      subjectName: subject.subjectName || subject.name || '',
      subjectType: subject.subjectType || subject.category || '',
      balanceDirection: subject.balanceDirection || defaultBalanceDirection(subject.subjectType || subject.category),
      remark: subject.remark || ''
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载科目详情失败')
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updateAccountSubject(formData.id, formData)
        ElMessage.success('更新成功')
      } else {
        await createAccountSubject(formData)
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

// 启用
const handleEnable = async (row: AccountSubject) => {
  try {
    await ElMessageBox.confirm(`确定要启用科目"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    await enableAccountSubject(row.id)
    ElMessage.success('启用成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

// 停用
const handleDisable = async (row: AccountSubject) => {
  try {
    await ElMessageBox.confirm(`确定要停用科目"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    await disableAccountSubject(row.id)
    ElMessage.success('停用成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    parentId: undefined,
    subjectCode: '',
    subjectName: '',
    subjectType: '',
    balanceDirection: 'DEBIT',
    remark: ''
  })
}

const defaultBalanceDirection = (subjectType?: string) => {
  return ['LIABILITY', 'EQUITY', 'REVENUE'].includes(subjectType || '') ? 'CREDIT' : 'DEBIT'
}

// 获取类别标签
const getCategoryLabel = (category: string) => {
  const map: Record<string, string> = {
    ASSET: '资产',
    LIABILITY: '负债',
    EQUITY: '权益',
    REVENUE: '收入',
    EXPENSE: '费用'
  }
  return map[category] || category
}

// 获取类别类型
const getCategoryType = (category: string) => {
  const map: Record<string, any> = {
    ASSET: 'success',
    LIABILITY: 'warning',
    EQUITY: 'info',
    REVENUE: 'success',
    EXPENSE: 'danger'
  }
  return map[category] || ''
}

onMounted(() => {
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
