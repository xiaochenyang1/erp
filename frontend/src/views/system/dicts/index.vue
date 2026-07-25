<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 左侧：字典类型列表 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $t('systemDicts.types') }}</span>
              <el-button v-permission="'system:dict:create'" type="primary" :icon="Plus" size="small" @click="handleAddType">
                {{ $t('systemDicts.add') }}
              </el-button>
            </div>
          </template>

          <!-- 搜索 -->
          <el-input
            v-model="typeSearchText"
            :placeholder="$t('systemDicts.searchTypes')"
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
            <el-table-column prop="code" :label="$t('systemDicts.code')" show-overflow-tooltip />
            <el-table-column prop="name" :label="$t('systemDicts.name')" show-overflow-tooltip />
            <el-table-column prop="status" :label="$t('systemDicts.status')" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? $t('systemDicts.active') : $t('systemDicts.inactive') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('systemDicts.operations')" width="150" align="center">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:update'" type="primary" link :icon="Edit" @click="handleEditType(row)">
                  {{ $t('systemDicts.edit') }}
                </el-button>
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  v-permission="'system:dict:disable'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDisableType(row)"
                >
                  {{ $t('systemDicts.disable') }}
                </el-button>
                <el-button v-else v-permission="'system:dict:enable'" type="success" link @click="handleEnableType(row)">
                  {{ $t('systemDicts.enable') }}
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
              <span>{{ currentType ? $t('systemDicts.itemsFor', { name: currentType.name }) : $t('systemDicts.items') }}</span>
              <el-button
                v-permission="'system:dict:create'"
                type="primary"
                :icon="Plus"
                size="small"
                :disabled="!currentType"
                @click="handleAddItem"
              >
                {{ $t('systemDicts.add') }}
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
            <el-table-column prop="label" :label="$t('systemDicts.label')" width="150" />
            <el-table-column prop="value" :label="$t('systemDicts.value')" width="150" />
            <el-table-column prop="orderNum" :label="$t('systemDicts.sort')" width="80" align="center" />
            <el-table-column prop="status" :label="$t('systemDicts.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? $t('systemDicts.active') : $t('systemDicts.inactive') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('systemDicts.operations')" width="200" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:update'" type="primary" link :icon="Edit" @click="handleEditItem(row)">
                  {{ $t('systemDicts.edit') }}
                </el-button>
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  v-permission="'system:dict:disable'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDisableItem(row)"
                >
                  {{ $t('systemDicts.disable') }}
                </el-button>
                <el-button v-else v-permission="'system:dict:enable'" type="success" link @click="handleEnableItem(row)">
                  {{ $t('systemDicts.enable') }}
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
        <el-form-item :label="$t('systemDicts.code')" prop="code">
          <el-input v-model="typeFormData.code" :placeholder="$t('systemDicts.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDicts.name')" prop="name">
          <el-input v-model="typeFormData.name" :placeholder="$t('systemDicts.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDicts.status')" prop="status">
          <el-radio-group v-model="typeFormData.status">
            <el-radio label="ACTIVE">{{ $t('systemDicts.active') }}</el-radio>
            <el-radio label="INACTIVE">{{ $t('systemDicts.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('systemDicts.remark')" prop="remark">
          <el-input
            v-model="typeFormData.remark"
            type="textarea"
            :rows="3"
            :placeholder="$t('systemDicts.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">{{ $t('systemDicts.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleTypeSubmit">
          {{ $t('systemDicts.confirm') }}
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
        <el-form-item :label="$t('systemDicts.types')">
          <el-input :value="currentType?.name" disabled />
        </el-form-item>
        <el-form-item :label="$t('systemDicts.label')" prop="label">
          <el-input v-model="itemFormData.label" :placeholder="$t('systemDicts.labelPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDicts.value')" prop="value">
          <el-input v-model="itemFormData.value" :placeholder="$t('systemDicts.valuePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemDicts.sort')" prop="orderNum">
          <el-input-number v-model="itemFormData.orderNum" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item :label="$t('systemDicts.status')" prop="status">
          <el-radio-group v-model="itemFormData.status">
            <el-radio label="ACTIVE">{{ $t('systemDicts.active') }}</el-radio>
            <el-radio label="INACTIVE">{{ $t('systemDicts.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">{{ $t('systemDicts.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleItemSubmit">
          {{ $t('systemDicts.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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

const typeFormRules = computed<FormRules>(() => ({
  code: [{ required: true, message: t('systemDicts.codePlaceholder'), trigger: 'blur' }],
  name: [{ required: true, message: t('systemDicts.namePlaceholder'), trigger: 'blur' }]
}))

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

const itemFormRules = computed<FormRules>(() => ({
  label: [{ required: true, message: t('systemDicts.labelPlaceholder'), trigger: 'blur' }],
  value: [{ required: true, message: t('systemDicts.valuePlaceholder'), trigger: 'blur' }]
}))

const submitLoading = ref(false)

// 加载字典类型列表
const loadTypeList = async () => {
  typeLoading.value = true
  try {
    const res = await getDictTypes({ page: 1, size: 1000 })
    typeList.value = res.records || []
  } catch (error) {
    console.error(t('systemDicts.message.loadTypesFailed'), error)
    ElMessage.error(t('systemDicts.message.loadTypesFailed'))
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
    console.error(t('systemDicts.message.loadItemsFailed'), error)
    ElMessage.error(t('systemDicts.message.loadItemsFailed'))
  } finally {
    itemLoading.value = false
  }
}

// 新增字典类型
const handleAddType = () => {
  typeDialogTitle.value = t('systemDicts.dialog.addType')
  typeDialogVisible.value = true
}

// 编辑字典类型
const handleEditType = async (row: DictType) => {
  typeDialogTitle.value = t('systemDicts.dialog.editType')
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
    ElMessage.error(t('systemDicts.message.loadTypeDetailFailed'))
  }
}

// 停用字典类型
const handleDisableType = async (row: DictType) => {
  try {
    await ElMessageBox.confirm(t('systemDicts.message.disableTypeConfirm', { name: row.name }), t('systemDicts.prompt'), {
      type: 'warning'
    })
    await deleteDictType(row.id)
    ElMessage.success(t('systemDicts.message.disableSuccess'))
    loadTypeList()
    if (currentType.value?.id === row.id) {
      currentType.value = null
      itemList.value = []
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemDicts.message.disableFailed'))
    }
  }
}

// 启用字典类型
const handleEnableType = async (row: DictType) => {
  try {
    await ElMessageBox.confirm(t('systemDicts.message.enableTypeConfirm', { name: row.name }), t('systemDicts.prompt'), {
      type: 'warning'
    })
    await enableDictType(row.id)
    ElMessage.success(t('systemDicts.message.enableSuccess'))
    loadTypeList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemDicts.message.enableFailed'))
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
        ElMessage.success(t('systemDicts.message.updateSuccess'))
      } else {
        await createDictType(typeFormData)
        ElMessage.success(t('systemDicts.message.createSuccess'))
      }
      typeDialogVisible.value = false
      loadTypeList()
    } catch (error) {
      ElMessage.error(t('systemDicts.message.operationFailed'))
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
  itemDialogTitle.value = t('systemDicts.dialog.addItem')
  itemFormData.typeCode = currentType.value.code
  itemDialogVisible.value = true
}

// 编辑字典项
const handleEditItem = (row: DictItem) => {
  itemDialogTitle.value = t('systemDicts.dialog.editItem')
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
    await ElMessageBox.confirm(t('systemDicts.message.disableItemConfirm', { label: row.label }), t('systemDicts.prompt'), {
      type: 'warning'
    })
    await deleteDictItem(row.id)
    ElMessage.success(t('systemDicts.message.disableSuccess'))
    if (currentType.value) {
      loadItemList(currentType.value.code)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemDicts.message.disableFailed'))
    }
  }
}

// 启用字典项
const handleEnableItem = async (row: DictItem) => {
  try {
    await ElMessageBox.confirm(t('systemDicts.message.enableItemConfirm', { label: row.label }), t('systemDicts.prompt'), {
      type: 'warning'
    })
    await enableDictItem(row.id)
    ElMessage.success(t('systemDicts.message.enableSuccess'))
    if (currentType.value) {
      loadItemList(currentType.value.code)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('systemDicts.message.enableFailed'))
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
        ElMessage.success(t('systemDicts.message.updateSuccess'))
      } else {
        await createDictItem(itemFormData)
        ElMessage.success(t('systemDicts.message.createSuccess'))
      }
      itemDialogVisible.value = false
      if (currentType.value) {
        loadItemList(currentType.value.code)
      }
    } catch (error) {
      ElMessage.error(t('systemDicts.message.operationFailed'))
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
