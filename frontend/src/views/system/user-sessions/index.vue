<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('userSessions.username')">
          <el-input v-model="queryForm.username" :placeholder="$t('userSessions.usernamePlaceholder')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('userSessions.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('userSessions.selectStatus')" clearable style="width: 140px">
            <el-option :label="$t('userSessions.statusValue.active')" value="ACTIVE" />
            <el-option :label="$t('userSessions.statusValue.revoked')" value="REVOKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('userSessions.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('userSessions.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>{{ $t('userSessions.title') }}</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="username" :label="$t('userSessions.username')" width="140" />
        <el-table-column prop="realName" :label="$t('userSessions.realName')" width="140">
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('userSessions.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginIp" :label="$t('userSessions.loginIp')" width="150" />
        <el-table-column prop="issuedAt" :label="$t('userSessions.issuedAt')" width="170">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.issuedAt) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" :label="$t('userSessions.lastUsedAt')" width="170">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.lastUsedAt) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="expiresAt" :label="$t('userSessions.expiresAt')" width="170">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.expiresAt) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="userAgent" :label="$t('userSessions.userAgent')" min-width="260" show-overflow-tooltip />
        <el-table-column :label="$t('userSessions.actions')" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'system:user-session:revoke'"
              type="danger"
              link
              :icon="Close"
              @click="handleRevoke(row)"
            >
              {{ $t('userSessions.revoke') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'system:user-session:revoke'"
              type="warning"
              link
              @click="handleRevokeUser(row)"
            >
              {{ $t('userSessions.revokeUser') }}
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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Refresh, Search } from '@element-plus/icons-vue'
import {
  getUserSessions,
  revokeUserSession,
  revokeUserSessionsByUser,
  type UserSession,
  type UserSessionQuery
} from '@/api/userSession'
import { formatLocalizedDateTime } from '@/utils/locale'

const { t } = useI18n()

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
    console.error('Failed to load online sessions:', error)
    ElMessage.error(t('userSessions.message.loadFailed'))
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
    await ElMessageBox.confirm(t('userSessions.message.revokeConfirm', { user: row.username || row.userId }), t('userSessions.message.prompt'), {
      type: 'warning'
    })
    await revokeUserSession(row.id)
    ElMessage.success(t('userSessions.message.revoked'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('userSessions.message.revokeFailed'))
  }
}

const handleRevokeUser = async (row: UserSession) => {
  try {
    await ElMessageBox.confirm(t('userSessions.message.revokeUserConfirm', { user: row.username || row.userId }), t('userSessions.message.prompt'), {
      type: 'warning'
    })
    await revokeUserSessionsByUser(row.userId)
    ElMessage.success(t('userSessions.message.userRevoked'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('userSessions.message.revokeUserFailed'))
  }
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: t('userSessions.statusValue.active'),
    REVOKED: t('userSessions.statusValue.revoked')
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
