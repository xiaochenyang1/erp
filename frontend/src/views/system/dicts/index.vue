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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  enableDictItem,
  enableDictType,
  getDictItems,
  getDictType,
  getDictTypes,
  updateDictItem,
  updateDictType,
  type DictType
} from '@/api/system'
import { useSystemDictPresentation } from '@/composables/useSystemDictPresentation'
import { useSystemDictList } from '@/composables/useSystemDictList'
import { useSystemDictForm } from '@/composables/useSystemDictForm'

const { t } = useI18n()
const typeFormRef = ref<FormInstance>()
const itemFormRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
  currentType,
  filteredTypeList,
  handleDisableItem,
  handleDisableType,
  handleEnableItem,
  handleEnableType,
  handleTypeSelect,
  itemList,
  itemLoading,
  loadTypeList,
  refreshCurrentItems,
  typeLoading,
  typeSearchText
} = useSystemDictList(t, {
  getDictTypes,
  getDictItems,
  deleteDictType,
  enableDictType,
  deleteDictItem,
  enableDictItem,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  handleAddItem: openAddItem,
  handleAddType,
  handleEditItem,
  handleEditType,
  handleItemSubmit: saveItem,
  handleTypeSubmit: saveType,
  itemDialogTitle,
  itemDialogVisible,
  itemFormData,
  resetItemForm,
  resetTypeForm,
  submitLoading,
  typeDialogTitle,
  typeDialogVisible,
  typeFormData
} = useSystemDictForm(t, {
  getDictType,
  createDictType,
  updateDictType,
  createDictItem,
  updateDictItem,
  onTypeSubmitted: loadTypeList,
  onItemSubmitted: refreshCurrentItems,
  ...notify
})

const typeFormRules = computed<FormRules>(() => ({
  code: [{ required: true, message: t('systemDicts.codePlaceholder'), trigger: 'blur' }],
  name: [{ required: true, message: t('systemDicts.namePlaceholder'), trigger: 'blur' }]
}))

const itemFormRules = computed<FormRules>(() => ({
  label: [{ required: true, message: t('systemDicts.labelPlaceholder'), trigger: 'blur' }],
  value: [{ required: true, message: t('systemDicts.valuePlaceholder'), trigger: 'blur' }]
}))

const handleAddItem = () => {
  if (!currentType.value) return
  openAddItem(currentType.value.code)
}

const handleTypeSubmit = async () => {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate(async (valid) => {
    if (!valid) return
    await saveType()
  })
}

const handleItemSubmit = async () => {
  if (!itemFormRef.value) return
  await itemFormRef.value.validate(async (valid) => {
    if (!valid) return
    await saveItem()
  })
}

const handleTypeDialogClose = () => {
  typeFormRef.value?.resetFields()
  resetTypeForm()
}

const handleItemDialogClose = () => {
  itemFormRef.value?.resetFields()
  resetItemForm()
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
