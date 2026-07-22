import { defineStore } from 'pinia'
import { ref } from 'vue'
import { readStoredLocale, setI18nLocale, type SupportedLocale } from '@/i18n'
import {
  DEFAULT_TIME_ZONE,
  isSupportedTimeZone,
  type SupportedTimeZone
} from '@/utils/locale'

export const useAppStore = defineStore('app', () => {
  // 侧边栏是否折叠
  const sidebarCollapsed = ref(false)

  // 切换侧边栏折叠状态
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  // 主题模式 - 从localStorage读取初始值
  const isDark = ref(localStorage.getItem('theme') === 'dark')
  const locale = ref<SupportedLocale>(readStoredLocale())
  const storedTimeZone = localStorage.getItem('timeZone')
  const timeZone = ref<SupportedTimeZone>(
    isSupportedTimeZone(storedTimeZone) ? storedTimeZone : DEFAULT_TIME_ZONE
  )

  // 初始化主题
  const initTheme = () => {
    if (isDark.value) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  const initPreferences = () => {
    initTheme()
    setI18nLocale(locale.value)
  }

  const setLocale = (nextLocale: SupportedLocale) => {
    locale.value = nextLocale
    localStorage.setItem('locale', nextLocale)
    setI18nLocale(nextLocale)
  }

  const setTimeZone = (nextTimeZone: SupportedTimeZone) => {
    timeZone.value = nextTimeZone
    localStorage.setItem('timeZone', nextTimeZone)
  }

  // 切换主题
  const toggleTheme = () => {
    isDark.value = !isDark.value
    if (isDark.value) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }

  return {
    sidebarCollapsed,
    toggleSidebar,
    isDark,
    initTheme,
    toggleTheme,
    locale,
    timeZone,
    initPreferences,
    setLocale,
    setTimeZone
  }
})
