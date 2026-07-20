<template>
  <div class="menus-container">
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'system:menu:create'" type="primary" @click="handleCreate(null)">
        <el-icon><Plus /></el-icon>
        新增菜单
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
        <el-table-column prop="name" label="菜单名称" width="200" />
        <el-table-column prop="path" label="路径" width="200" />
        <el-table-column prop="component" label="组件" show-overflow-tooltip />
        <el-table-column prop="icon" label="图标" width="100" />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'MENU'">菜单</el-tag>
            <el-tag v-else type="info">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">正常</el-tag>
            <el-tag v-else type="danger">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:menu:create'" link type="primary" @click="handleCreate(row)">新增</el-button>
            <el-button v-permission="'system:menu:update'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'system:menu:disable'" link type="danger" @click="handleDisable(row)">停用</el-button>
            <el-button v-else v-permission="'system:menu:enable'" link type="success" @click="handleEnable(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择上级菜单"
            clearable
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio value="MENU">菜单</el-radio>
            <el-radio value="BUTTON">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="formData.type === 'MENU'" label="路由路径">
          <el-input v-model="formData.path" placeholder="请输入路由路径" />
        </el-form-item>
        <el-form-item v-if="formData.type === 'MENU'" label="组件路径">
          <el-input v-model="formData.component" placeholder="请输入组件路径" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="formData.permission" placeholder="请输入权限标识" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="formData.icon" placeholder="请输入图标名称" />
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
import { getMenuTree, getMenu, createMenu, updateMenu, deleteMenu, enableMenu, type Menu, type MenuSaveRequest } from '@/api/system'

const loading = ref(false)
const tableData = ref<Menu[]>([])
const menuTree = ref<Menu[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()
const tableRef = ref()

const formData = reactive<MenuSaveRequest>({
  parentId: undefined,
  name: '',
  path: '',
  component: '',
  icon: '',
  orderNum: 0,
  type: 'MENU',
  permission: '',
  status: 'ACTIVE'
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  orderNum: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getMenuTree()
    tableData.value = data
    menuTree.value = [{ id: 0, name: '顶级菜单', children: data } as any]
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const expandAll = () => {
  tableRef.value?.toggleAllRowExpansion()
}

const handleCreate = (row: Menu | null) => {
  dialogTitle.value = '新增菜单'
  isEdit.value = false
  resetForm()
  if (row) {
    formData.parentId = row.id
  }
  dialogVisible.value = true
}

const handleEdit = async (row: Menu) => {
  dialogTitle.value = '编辑菜单'
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getMenu(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

const handleDisable = async (row: Menu) => {
  try {
    await ElMessageBox.confirm(`确认停用菜单"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMenu(row.id)
    ElMessage.success('停用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

const handleEnable = async (row: Menu) => {
  try {
    await ElMessageBox.confirm(`确认启用菜单"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await enableMenu(row.id)
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
          await updateMenu(currentId.value, formData)
        } else {
          await createMenu(formData)
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
  formData.path = ''
  formData.component = ''
  formData.icon = ''
  formData.orderNum = 0
  formData.type = 'MENU'
  formData.permission = ''
  formData.status = 'ACTIVE'
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.menus-container {
  padding: 20px;
  .toolbar-card, .table-card {
    margin-bottom: 20px;
  }
}
</style>
