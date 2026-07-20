<template>
  <el-config-provider :locale="zhCn" size="default">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { useAppStore } from '@/store/modules/app'

const appStore = useAppStore()

onMounted(() => {
  // 初始化主题（用户信息/权限由路由守卫在进入受保护页面前加载，避免竞态）
  appStore.initTheme()
})
</script>

<style>
#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}
</style>
