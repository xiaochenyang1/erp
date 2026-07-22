<template>
  <el-config-provider :locale="elementLocale" size="default">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import { useAppStore } from '@/store/modules/app'

const appStore = useAppStore()
const elementLocale = computed(() => appStore.locale === 'en-US' ? en : zhCn)

onMounted(() => {
  // 初始化主题（用户信息/权限由路由守卫在进入受保护页面前加载，避免竞态）
  appStore.initPreferences()
})
</script>

<style>
#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}
</style>
