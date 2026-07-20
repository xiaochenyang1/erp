<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 左侧：字典类型列表 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典类型</span>
              <el-button v-permission="'system:dict:create'" type="primary" :icon="Plus" size="small" @click="handleAddType">
                新增
              </el-button>
            </div>
          </template>

          <!-- 搜索 -->
          <el-input
            v-model="typeSearchText"
            placeholder="搜索字典类型"
            :prefix-icon="Search"
            clearable
            style="margin-bottom: 15px"
          />

          <!-- 字典类型列表 -->
          <el-table
            v-loading="typeLoading"
            :data="filteredTypeList"
            highlight-current-row
            @current-change="handleTypeSelect"
            style="width: 100%"
            max-height="600px"
          >
            <el-table-column prop="code" label="字典编码" show-overflow-tooltip />
            <el-table-column prop="name" label="字典名称" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:update'" type="primary" link :icon="Edit" @click="handleEditType(row)">
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  v-permission="'system:dict:disable'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDisableType(row)"
                >
                  停用
                </el-button>
                <el-button v-else v-permission="'system:dict:enable'" type="success" link @click="handleEnableType(row)">
                  启用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：字典项列表 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典项 {{ currentType ? `- ${currentType.name}` : '' }}</span>
              <el-button
                v-permission="'system:dict:create'"
                type="primary"
                :icon="Plus"
                size="small"
                :disabled="!currentType"
                @click="handleAddItem"
              >
                新增
              </el-button>
            </div>
          </template>

          <!-- 字典项表格 -->
          <el-table
            v-loading="itemLoading"
            :data="itemList"
            border
            stripe
            style="width: 100%"
          >
            <el-table-column prop="label" label="字典标签" width="150" />
            <el-table-column prop="value" label="字典值" width="150" />
            <el-table-column prop="orderNum" label="排序" width="80" align="center" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:update'" type="primary" link :icon="Edit" @click="handleEditItem(row)">
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  v-permission="'system:dict:disable'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDisableItem(row)"
                >
                  停用
                </el-button>
                <el-button v-else v-permission="'system:dict:enable'" type="success" link @click="handleEnableItem(row)">
                  启用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型对话框 -->
    <el-dialog
      v-model="typeDialogVisible"
      :title="typeDialogTitle"
      width="600px"
      @close="handleTypeDialogClose"
    >
      <el-form
        ref="typeFormRef"
        :model="typeFormData"
        :rules="typeFormRules"
        label-width="100px"
      >
        <el-form-item label="字典编码" prop="code">
          <el-input v-model="typeFormData.code" placeholder="请输入字典编码" />
        </el-form-item>
        <el-form-item label="字典名称" prop="name">
          <el-input v-model="typeFormData.name" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="typeFormData.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="typeFormData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleTypeSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 字典项对话框 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogTitle"
      width="600px"
      @close="handleItemDialogClose"
    >
      <el-form
        ref="itemFormRef"
        :model="itemFormData"
        :rules="itemFormRules"
        label-width="100px"
      >
        <el-form-item label="字典类型">
          <el-input :value="currentType?.name" disabled />
        </el-form-item>
        <el-form-item label="字典标签" prop="label">
          <el-input v-model="itemFormData.label" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典值" prop="value">
          <el-input v-model="itemFormData.value" placeholder="请输入字典值" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="itemFormData.orderNum" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="itemFormData.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleItemSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getDictTypes,
  getDictType,
  createDictType,
  updateDictType,
  deleteDictType,
  enableDictType,
  getDictItems,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  enableDictItem,
  type DictType,
  type DictItem
} from '@/api/system'

// 字典类型
const typeLoading = ref(false)
const typeList = ref<DictType[]>([])
const currentType = ref<DictType | null>(null)
const typeSearchText = ref('')

// 过滤后的字典类型列表
const filteredTypeList = computed(() => {
  if (!typeSearchText.value) return typeList.value
  return typeList.value.filter(
    (item) =>
      item.code.toLowerCase().includes(typeSearchText.value.toLowerCase()) ||
      item.name.toLowerCase().includes(typeSearchText.value.toLowerCase())
  )
})

// 字典项
const itemLoading = ref(false)
const itemList = ref<DictItem[]>([])

// 字典类型对话框
const typeDialogVisible = ref(false)
const typeDialogTitle = ref('')
const typeFormRef = ref<FormInstance>()
const typeFormData = reactive({
  id: undefined as number | undefined,
  code: '',
  name: '',
  status: 'ACTIVE',
  remark: ''
})

const typeFormRules: FormRules = {
  code: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入字典名称', trigger: 'blur' }]
}

// 字典项对话框
const itemDialogVisible = ref(false)
const itemDialogTitle = ref('')
const itemFormRef = ref<FormInstance>()
const itemFormData = reactive({
  id: undefined as number | undefined,
  typeCode: '',
  label: '',
  value: '',
  orderNum: 0,
  status: 'ACTIVE'
})

const itemFormRules: FormRules = {
  label: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  value: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
}

const submitLoading = ref(false)

// 加载字典类型列表
const loadTypeList = async () => {
  typeLoading.value = true
  try {
    const res = await getDictTypes({ page: 1, size: 1000 })
    typeList.value = res.records || []
  } catch (error) {
    console.error('加载字典类型失败:', error)
    ElMessage.error('加载字典类型失败')
  } finally {
    typeLoading.value = false
  }
}

// 选择字典类型
const handleTypeSelect = (row: DictType | null) => {
  currentType.value = row
  if (row) {
    loadItemList(row.code)
  } else {
    itemList.value = []
  }
}

// 加载字典项列表
const loadItemList = async (typeCode: string) => {
  itemLoading.value = true
  try {
    const res = await getDictItems(typeCode)
    itemList.value = res || []
  } catch (error) {
    console.error('加载字典项失败:', error)
    ElMessage.error('加载字典项失败')
  } finally {
    itemLoading.value = false
  }
}

// 新增字典类型
const handleAddType = () => {
  typeDialogTitle.value = '新增字典类型'
  typeDialogVisible.value = true
}

// 编辑字典类型
const handleEditType = async (row: DictType) => {
  typeDialogTitle.value = '编辑字典类型'
  try {
    const res = await getDictType(row.id)
    Object.assign(typeFormData, {
      id: res.id,
      code: res.code,
      name: res.name,
      status: res.status,
      remark: res.remark
    })
    typeDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载字典类型详情失败')
  }
}

// 停用字典类型
const handleDisableType = async (row: DictType) => {
  try {
    await ElMessageBox.confirm(`确定要停用字典类型"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    await deleteDictType(row.id)
    ElMessage.success('停用成功')
    loadTypeList()
    if (currentType.value?.id === row.id) {
      currentType.value = null
      itemList.value = []
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

// 启用字典类型
const handleEnableType = async (row: DictType) => {
  try {
    await ElMessageBox.confirm(`确定要启用字典类型"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    await enableDictType(row.id)
    ElMessage.success('启用成功')
    loadTypeList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

// 提交字典类型
const handleTypeSubmit = async () => {
  if (!typeFormRef.value) return

  await typeFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (typeFormData.id) {
        await updateDictType(typeFormData.id, typeFormData)
        ElMessage.success('更新成功')
      } else {
        await createDictType(typeFormData)
        ElMessage.success('创建成功')
      }
      typeDialogVisible.value = false
      loadTypeList()
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 字典类型对话框关闭
const handleTypeDialogClose = () => {
  typeFormRef.value?.resetFields()
  Object.assign(typeFormData, {
    id: undefined,
    code: '',
    name: '',
    status: 'ACTIVE',
    remark: ''
  })
}

// 新增字典项
const handleAddItem = () => {
  if (!currentType.value) return
  itemDialogTitle.value = '新增字典项'
  itemFormData.typeCode = currentType.value.code
  itemDialogVisible.value = true
}

// 编辑字典项
const handleEditItem = (row: DictItem) => {
  itemDialogTitle.value = '编辑字典项'
  Object.assign(itemFormData, {
    id: row.id,
    typeCode: row.typeCode,
    label: row.label,
    value: row.value,
    orderNum: row.orderNum,
    status: row.status
  })
  itemDialogVisible.value = true
}

// 停用字典项
const handleDisableItem = async (row: DictItem) => {
  try {
    await ElMessageBox.confirm(`确定要停用字典项"${row.label}"吗？`, '提示', {
      type: 'warning'
    })
    await deleteDictItem(row.id)
    ElMessage.success('停用成功')
    if (currentType.value) {
      loadItemList(currentType.value.code)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败')
    }
  }
}

// 启用字典项
const handleEnableItem = async (row: DictItem) => {
  try {
    await ElMessageBox.confirm(`确定要启用字典项"${row.label}"吗？`, '提示', {
      type: 'warning'
    })
    await enableDictItem(row.id)
    ElMessage.success('启用成功')
    if (currentType.value) {
      loadItemList(currentType.value.code)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

// 提交字典项
const handleItemSubmit = async () => {
  if (!itemFormRef.value) return

  await itemFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (itemFormData.id) {
        await updateDictItem(itemFormData.id, itemFormData)
        ElMessage.success('更新成功')
      } else {
        await createDictItem(itemFormData)
        ElMessage.success('创建成功')
      }
      itemDialogVisible.value = false
      if (currentType.value) {
        loadItemList(currentType.value.code)
      }
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 字典项对话框关闭
const handleItemDialogClose = () => {
  itemFormRef.value?.resetFields()
  Object.assign(itemFormData, {
    id: undefined,
    typeCode: '',
    label: '',
    value: '',
    orderNum: 0,
    status: 'ACTIVE'
  })
}

onMounted(() => {
  loadTypeList()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
