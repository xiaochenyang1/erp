<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="queryForm.username" placeholder="请输入用户名" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 140px">
            <el-option label="在线" value="ACTIVE" />
            <el-option label="已撤销" value="REVOKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>在线会话</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="姓名" width="140">
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginIp" label="登录IP" width="150" />
        <el-table-column prop="issuedAt" label="签发时间" width="170" />
        <el-table-column prop="lastUsedAt" label="最近使用" width="170">
          <template #default="{ row }">{{ row.lastUsedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="过期时间" width="170" />
        <el-table-column prop="userAgent" label="User-Agent" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'system:user-session:revoke'"
              type="danger"
              link
              :icon="Close"
              @click="handleRevoke(row)"
            >
              撤销
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'system:user-session:revoke'"
              type="warning"
              link
              @click="handleRevokeUser(row)"
            >
              撤销该用户
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Refresh, Search } from '@element-plus/icons-vue'
import {
  getUserSessions,
  revokeUserSession,
  revokeUserSessionsByUser,
  type UserSession,
  type UserSessionQuery
} from '@/api/userSession'

const queryForm = reactive<UserSessionQuery>({
  username: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loading = ref(false)
const tableData = ref<UserSession[]>([])

const buildQueryParams = (): UserSessionQuery => ({
  ...queryForm,
  pageNo: pagination.page,
  pageSize: pagination.size
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getUserSessions(buildQueryParams())
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载在线会话失败:', error)
    ElMessage.error('加载会话失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.username = ''
  queryForm.status = ''
  pagination.page = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleRevoke = async (row: UserSession) => {
  try {
    await ElMessageBox.confirm(`确定撤销用户「${row.username || row.userId}」的当前会话吗？`, '提示', {
      type: 'warning'
    })
    await revokeUserSession(row.id)
    ElMessage.success('会话已撤销')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('撤销会话失败')
  }
}

const handleRevokeUser = async (row: UserSession) => {
  try {
    await ElMessageBox.confirm(`确定撤销用户「${row.username || row.userId}」的全部在线会话吗？`, '提示', {
      type: 'warning'
    })
    await revokeUserSessionsByUser(row.userId)
    ElMessage.success('用户会话已撤销')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('撤销用户会话失败')
  }
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: '在线',
    REVOKED: '已撤销'
  }
  return map[status] || status
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card,
.table-card {
  margin-bottom: 20px;
}
</style>
