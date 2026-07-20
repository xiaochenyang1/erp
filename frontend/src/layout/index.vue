<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside :class="['layout-sidebar', { collapsed: appStore.sidebarCollapsed }]">
      <div class="sidebar-logo">
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">ERP系统</span>
        <span v-else class="logo-mini">ERP</span>
      </div>

      <el-scrollbar class="sidebar-menu-wrapper">
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          background-color="#001529"
          text-color="#fff"
          active-text-color="#409EFF"
          router
        >
          <SidebarItem v-for="route in routes" :key="route.path" :item="route" />
        </el-menu>
      </el-scrollbar>
    </aside>

    <!-- 主体区域 -->
    <div class="layout-main">
      <!-- 顶栏 -->
      <header class="layout-header">
        <div class="header-left">
          <el-icon class="toggle-icon" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>

        <div class="header-right">
          <!-- 全屏 -->
          <el-icon class="header-icon" @click="toggleFullscreen">
            <FullScreen />
          </el-icon>

          <!-- 主题切换 -->
          <el-icon class="header-icon" @click="appStore.toggleTheme">
            <Sunny v-if="!appStore.isDark" />
            <Moon v-else />
          </el-icon>

          <!-- 用户下拉菜单 -->
          <el-dropdown @command="handleCommand">
            <div class="user-dropdown">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar">
                {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="layout-content">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive>
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>

    <el-dialog
      v-model="profileDialogVisible"
      title="个人中心"
      width="560px"
      @close="resetProfileForm"
    >
      <el-descriptions :column="1" border class="profile-readonly">
        <el-descriptions-item label="用户ID">
          {{ userStore.userInfo?.id || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="用户名">
          {{ userStore.userInfo?.username || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          {{ userStore.userInfo?.roles?.join('、') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="仓库范围">
          {{ userStore.userInfo?.dataScope?.warehouseIds?.join('、') || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form
        ref="profileFormRef"
        class="profile-form"
        :model="profileForm"
        :rules="profileRules"
        label-width="100px"
      >
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="profileForm.realName" maxlength="64" show-word-limit placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" maxlength="128" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="profileForm.mobile" maxlength="32" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="头像URL" prop="avatar">
          <el-input v-model="profileForm.avatar" maxlength="512" placeholder="可选，头像图片地址" />
        </el-form-item>
        <el-form-item v-if="profileForm.avatar" label="头像预览">
          <el-avatar :size="48" :src="profileForm.avatar">
            {{ profileForm.realName?.charAt(0) || userStore.userInfo?.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSubmitting" @click="submitProfileChange">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="520px"
      @close="resetPasswordForm"
    >
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入原密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="12到72位，包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  Fold,
  Expand,
  FullScreen,
  Sunny,
  Moon,
  User,
  Lock,
  SwitchButton,
  ArrowDown
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changePassword,
  updateProfile,
  type ChangePasswordRequest,
  type UpdateProfileRequest
} from '@/api/auth'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { useMenuStore } from '@/store/modules/menu'
import SidebarItem from './components/SidebarItem.vue'
import router from '@/router'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()
const menuStore = useMenuStore()
const profileDialogVisible = ref(false)
const profileSubmitting = ref(false)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive<UpdateProfileRequest>({
  realName: '',
  email: '',
  mobile: '',
  avatar: ''
})
const profileRules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive<ChangePasswordRequest & { confirmPassword: string }>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateStrongPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入新密码'))
    return
  }
  if (value.length < 12 || value.length > 72) {
    callback(new Error('密码长度必须在12到72位之间'))
    return
  }
  if (/\s/.test(value) || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('密码必须包含字母和数字，且不能包含空白字符'))
    return
  }
  callback()
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ validator: validateStrongPassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

// 当前激活的菜单
const activeMenu = computed(() => route.path)

// 按权限过滤菜单：叶子节点按 meta.permission 校验，分组无可见子项则隐藏
// —— 作为后端菜单接口失败时的回退方案
const filterMenuByPermission = (list: any[]): any[] => {
  const result: any[] = []
  for (const item of list) {
    if (item.children && item.children.length) {
      const children = filterMenuByPermission(item.children)
      if (children.length) {
        result.push({ ...item, children })
      }
    } else {
      const perm = item.meta?.permission
      if (!perm || userStore.hasPermission(perm)) {
        result.push(item)
      }
    }
  }
  return result
}

// 解析节点完整路径，与 SidebarItem 的 resolvePath 保持一致
const resolveFullPath = (path: string, basePath: string): string => {
  return basePath ? `${basePath}/${path}` : path
}

// 按后端运行时菜单树裁剪静态路由：叶子 path 在白名单内才显示，分组无可见子项则隐藏。
// 首页 /dashboard 是免权限入口，恒常保留。
const filterMenuByBackendMenu = (list: any[], basePath = ''): any[] => {
  const result: any[] = []
  const whitelist = menuStore.visiblePaths
  for (const item of list) {
    const full = resolveFullPath(item.path, basePath)
    if (item.children && item.children.length) {
      const children = filterMenuByBackendMenu(item.children, full)
      if (children.length) {
        result.push({ ...item, children })
      }
    } else if (full === '/dashboard' || whitelist.has(full)) {
      result.push(item)
    }
  }
  return result
}

// 路由列表：优先由后端运行时菜单驱动可见性；接口未就绪/失败时回退到按权限过滤静态路由
const routes = computed(() => {
  const all = router.options.routes.find((r) => r.path === '/')?.children || []
  if (menuStore.loaded && menuStore.visiblePaths.size > 0) {
    return filterMenuByBackendMenu(all)
  }
  return filterMenuByPermission(all)
})

onMounted(() => {
  if (!menuStore.loaded) {
    menuStore.loadMenus()
  }
})

// 全屏切换
const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

// 用户菜单命令处理
const handleCommand = (command: string) => {
  switch (command) {
    case 'profile':
      openProfileDialog()
      break
    case 'password':
      openPasswordDialog()
      break
    case 'logout':
      handleLogout()
      break
  }
}

const openProfileDialog = () => {
  profileForm.realName = userStore.userInfo?.realName || ''
  profileForm.email = userStore.userInfo?.email || ''
  profileForm.mobile = userStore.userInfo?.mobile || ''
  profileForm.avatar = userStore.userInfo?.avatar || ''
  profileDialogVisible.value = true
}

const resetProfileForm = () => {
  profileFormRef.value?.clearValidate()
}

const submitProfileChange = async () => {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    profileSubmitting.value = true
    try {
      const updated = await updateProfile({
        realName: profileForm.realName.trim(),
        email: profileForm.email?.trim() || undefined,
        mobile: profileForm.mobile?.trim() || undefined,
        avatar: profileForm.avatar?.trim() || undefined
      })
      userStore.userInfo = {
        ...(userStore.userInfo || { id: updated.id, username: updated.username }),
        ...updated,
        // 个人资料接口不返回 dataScope，保留登录时的范围信息
        dataScope: userStore.userInfo?.dataScope
      }
      if (updated.permissions?.length) {
        userStore.permissions = updated.permissions
      }
      ElMessage.success('个人资料已保存')
      profileDialogVisible.value = false
    } catch (error) {
      console.error('保存个人资料失败:', error)
      ElMessage.error('保存个人资料失败')
    } finally {
      profileSubmitting.value = false
    }
  })
}

const openPasswordDialog = () => {
  passwordDialogVisible.value = true
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

const submitPasswordChange = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordSubmitting.value = true
    try {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      passwordDialogVisible.value = false
      await userStore.doLogout()
    } finally {
      passwordSubmitting.value = false
    }
  })
}

// 退出登录
const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      userStore.doLogout()
    })
    .catch(() => {})
}
</script>

<style scoped>
.layout-container {
  display: flex;
  width: 100%;
  height: 100vh;
}

/* 侧边栏 */
.layout-sidebar {
  width: 200px;
  height: 100%;
  background-color: #001529;
  transition: width 0.3s;
  overflow: hidden;
}

.layout-sidebar.collapsed {
  width: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-mini {
  font-size: 16px;
}

.sidebar-menu-wrapper {
  height: calc(100% - 50px);
}

/* 主体区域 */
.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* 顶栏 */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 20px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  transition: all 0.3s;
}

/* 暗黑模式 - 顶栏 */
html.dark .layout-header {
  background-color: #1d1e1f;
  border-bottom-color: #414243;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toggle-icon,
.header-icon {
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.toggle-icon:hover,
.header-icon:hover {
  color: #409eff;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.3s;
}

.user-dropdown:hover {
  background-color: #f5f7fa;
}

/* 暗黑模式 - 用户下拉菜单悬停 */
html.dark .user-dropdown:hover {
  background-color: #262727;
}

.username {
  font-size: 14px;
  color: #303133;
}

.profile-readonly {
  margin-bottom: 16px;
}

.profile-form {
  margin-top: 8px;
}

/* 暗黑模式 - 用户名 */
html.dark .username {
  color: #e5eaf3;
}

/* 内容区域 */
.layout-content {
  flex: 1;
  padding: 20px;
  background-color: #f0f2f5;
  overflow-y: auto;
  transition: all 0.3s;
}

/* 暗黑模式 - 内容区域 */
html.dark .layout-content {
  background-color: #0a0a0a;
}

/* 路由过渡动画 */
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
