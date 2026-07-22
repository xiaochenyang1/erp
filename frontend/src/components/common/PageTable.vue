<template>
  <div class="page-table">
    <!-- 搜索栏 -->
    <div v-if="showSearch" class="table-search">
      <slot name="search"></slot>
    </div>

    <!-- 工具栏 -->
    <div v-if="showToolbar" class="table-toolbar">
      <div class="toolbar-left">
        <slot name="toolbar-left">
          <el-button v-if="showCreate" type="primary" :icon="Plus" @click="handleCreate">
            {{ resolvedCreateText }}
          </el-button>
        </slot>
      </div>
      <div class="toolbar-right">
        <slot name="toolbar-right">
          <el-button v-if="showExport" :icon="Download" @click="handleExport">{{ resolvedExportText }}</el-button>
          <el-button :icon="Refresh" circle @click="handleRefresh" :title="resolvedRefreshTitle"></el-button>
        </slot>
        <slot name="column-setting"></slot>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-container">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="data"
        :stripe="stripe"
        :border="border"
        :size="tableSize"
        :height="height"
        :max-height="maxHeight"
        :row-key="rowKey"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
        @row-click="handleRowClick"
        class="elegant-table"
      >
        <slot></slot>
      </el-table>
    </div>

    <!-- 分页 -->
    <div v-if="showPagination" class="table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        :layout="paginationLayout"
        :background="true"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Plus, Download, Refresh } from '@element-plus/icons-vue'
import type { TableInstance } from 'element-plus'
import { useAppStore } from '@/store/modules/app'

interface Props {
  data: any[]
  total?: number
  loading?: boolean
  stripe?: boolean
  border?: boolean
  tableSize?: 'large' | 'default' | 'small'
  height?: string | number
  maxHeight?: string | number
  rowKey?: string
  showSearch?: boolean
  showToolbar?: boolean
  showCreate?: boolean
  showExport?: boolean
  showPagination?: boolean
  createText?: string
  exportText?: string
  refreshTitle?: string
  page?: number
  pageSize?: number
  pageSizes?: number[]
  paginationLayout?: string
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  total: 0,
  loading: false,
  stripe: true,
  border: false,
  tableSize: 'default',
  rowKey: 'id',
  showSearch: true,
  showToolbar: true,
  showCreate: true,
  showExport: true,
  showPagination: true,
  createText: '',
  exportText: '',
  refreshTitle: '',
  page: 1,
  pageSize: 20,
  pageSizes: () => [10, 20, 50, 100],
  paginationLayout: 'total, sizes, prev, pager, next, jumper'
})

const appStore = useAppStore()
const texts = computed(() => appStore.locale === 'en-US'
  ? {
      create: 'Create',
      export: 'Export',
      refresh: 'Refresh'
    }
  : {
      create: '新增',
      export: '导出',
      refresh: '刷新'
    })
const resolvedCreateText = computed(() => props.createText || texts.value.create)
const resolvedExportText = computed(() => props.exportText || texts.value.export)
const resolvedRefreshTitle = computed(() => props.refreshTitle || texts.value.refresh)

const emit = defineEmits<{
  create: []
  export: []
  refresh: []
  selectionChange: [selection: any[]]
  sortChange: [column: any]
  rowClick: [row: any]
  pageChange: [page: number, size: number]
}>()

const tableRef = ref<TableInstance>()
const currentPage = ref(props.page)
const pageSize = ref(props.pageSize)

const headerCellStyle = {
  background: '#f8f9fa',
  color: '#2c3e50',
  fontWeight: '600',
  fontSize: '13px',
  letterSpacing: '0.3px'
}

const cellStyle = {
  color: '#495057',
  fontSize: '13px'
}

const handleCreate = () => emit('create')
const handleExport = () => emit('export')
const handleRefresh = () => emit('refresh')
const handleSelectionChange = (selection: any[]) => emit('selectionChange', selection)
const handleSortChange = (column: any) => emit('sortChange', column)
const handleRowClick = (row: any) => emit('rowClick', row)

const handleSizeChange = (size: number) => {
  pageSize.value = size
  emit('pageChange', currentPage.value, size)
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  emit('pageChange', page, pageSize.value)
}

defineExpose({
  refresh: () => tableRef.value?.clearSelection()
})
</script>

<style scoped>
.page-table {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-table:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.table-search {
  padding: 20px 24px;
  background: linear-gradient(to bottom, #fafbfc, #ffffff);
  border-bottom: 1px solid #e9ecef;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e9ecef;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.table-container {
  padding: 0;
}

.elegant-table {
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

.elegant-table :deep(.el-table__header-wrapper) {
  border-radius: 0;
}

.elegant-table :deep(.el-table__row) {
  transition: all 0.2s ease;
}

.elegant-table :deep(.el-table__row:hover) {
  background: #f8f9fa !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.elegant-table :deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 20px 24px;
  background: #fafbfc;
  border-top: 1px solid #e9ecef;
}

.table-pagination :deep(.el-pagination) {
  font-weight: 500;
}

.table-pagination :deep(.el-pagination .btn-prev),
.table-pagination :deep(.el-pagination .btn-next) {
  border-radius: 8px;
  transition: all 0.2s ease;
}

.table-pagination :deep(.el-pagination .btn-prev:hover),
.table-pagination :deep(.el-pagination .btn-next:hover) {
  transform: scale(1.05);
}

.table-pagination :deep(.el-pager li) {
  border-radius: 8px;
  margin: 0 4px;
  transition: all 0.2s ease;
}

.table-pagination :deep(.el-pager li.is-active) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-weight: 600;
}
</style>
