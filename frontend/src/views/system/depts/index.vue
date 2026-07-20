<template>
  <div class="depts-container">
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'system:dept:create'" type="primary" @click="handleCreate(null)">
        <el-icon><Plus /></el-icon>
        新增部门
      </el-button>
      <el-button @click="expandAll">
        <el-icon><DCaret /></el-icon>
        展开/折叠
      </el-button>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        row-key="id"
        border
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" label="部门名称" width="200" />
        <el-table-column prop="code" label="部门编码" width="150" />
        <el-table-column prop="manager" label="负责人" width="120" />
        <el-table-column prop="contact" label="联系方式" width="150" />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">正常</el-tag>
            <el-tag v-else type="danger">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:create'" link type="primary" @click="handleCreate(row)">新增</el-button>
            <el-button v-permission="'system:dept:update'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:dept:disable'" link type="danger" @click="handleDisable(row)">停用</el-button>
            <el-button v-else v-permission="'system:dept:enable'" link type="success" @click="handleEnable(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="formData.parentId"
            :data="deptTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择上级部门"
            clearable
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="formData.code" placeholder="请输入部门编码" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="formData.manager" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="formData.contact" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="formData.orderNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getDeptTree, getDept, createDept, updateDept, deleteDept, enableDept, type Dept, type DeptSaveRequest } from '@/api/system'

const loading = ref(false)
const tableData = ref<Dept[]>([])
const deptTree = ref<Dept[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()
const tableRef = ref()

const formData = reactive<DeptSaveRequest>({
  parentId: undefined,
  name: '',
  code: '',
  manager: '',
  contact: '',
  orderNum: 0,
  status: 'ACTIVE'
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  orderNum: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getDeptTree()
    tableData.value = data
    deptTree.value = [{ id: 0, name: '顶级部门', children: data } as any]
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const expandAll = () => {
  tableRef.value?.toggleAllRowExpansion()
}

const handleCreate = (row: Dept | null) => {
  dialogTitle.value = '新增部门'
  isEdit.value = false
  resetForm()
  if (row) {
    formData.parentId = row.id
  }
  dialogVisible.value = true
}

const handleEdit = async (row: Dept) => {
  dialogTitle.value = '编辑部门'
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getDept(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

const handleDisable = async (row: Dept) => {
  try {
    await ElMessageBox.confirm(`确认停用部门"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteDept(row.id)
    ElMessage.success('停用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

const handleEnable = async (row: Dept) => {
  try {
    await ElMessageBox.confirm(`确认启用部门"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await enableDept(row.id)
    ElMessage.success('启用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value) {
          await updateDept(currentId.value, formData)
        } else {
          await createDept(formData)
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

const resetForm = () => {
  formData.parentId = undefined
  formData.name = ''
  formData.code = ''
  formData.manager = ''
  formData.contact = ''
  formData.orderNum = 0
  formData.status = 'ACTIVE'
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.depts-container {
  padding: 20px;
  .toolbar-card, .table-card {
    margin-bottom: 20px;
  }
}
</style>
