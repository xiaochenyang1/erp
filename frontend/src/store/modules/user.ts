import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login, logout, getUserInfo, type LoginRequest, type UserInfo } from '@/api/auth'
import { useMenuStore } from '@/store/modules/menu'
import router from '@/router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/store/modules/app'
import { isSupportedLocale } from '@/i18n'
import { isSupportedTimeZone } from '@/utils/locale'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])

  // 登录
  const doLogin = async (loginData: LoginRequest) => {
    try {
      const res = await login(loginData)
      token.value = res.accessToken
      localStorage.setItem('token', res.accessToken)
      localStorage.setItem('refreshToken', res.refreshToken)

      // 保存用户信息（登录接口已返回）
      userInfo.value = res.user
      permissions.value = res.permissions || []

      ElMessage.success('登录成功')

      // 跳转到首页
      router.push('/')

      return res
    } catch (error) {
      console.error('登录失败:', error)
      throw error
    }
  }

  // 获取用户信息
  const getUserInfo2 = async () => {
    try {
      const info = await getUserInfo()
      userInfo.value = info
      permissions.value = info.permissions || []
      const appStore = useAppStore()
      if (isSupportedLocale(info.locale)) appStore.setLocale(info.locale)
      if (isSupportedTimeZone(info.timeZone)) appStore.setTimeZone(info.timeZone)
      return info
    } catch (error) {
      console.error('获取用户信息失败:', error)
      throw error
    }
  }

  // 登出
  const doLogout = async () => {
    try {
      const storedRefreshToken = localStorage.getItem('refreshToken')
      if (storedRefreshToken) {
        await logout(storedRefreshToken)
      }
    } catch (error) {
      console.error('登出失败:', error)
    } finally {
      // 清除本地数据
      token.value = ''
      userInfo.value = null
      permissions.value = []
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')

      // 清空运行时菜单，避免换账号后残留上一用户的菜单
      useMenuStore().reset()

      // 跳转到登录页
      router.push('/login')
      ElMessage.success('已退出登录')
    }
  }

  // 检查权限
  const hasPermission = (permission: string): boolean => {
    if (!permission) return true
    return permissions.value.includes(permission)
  }

  return {
    token,
    userInfo,
    permissions,
    doLogin,
    getUserInfo: getUserInfo2,
    doLogout,
    hasPermission
  }
})
