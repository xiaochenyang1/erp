<template>
  <!-- 单级菜单 -->
  <template v-if="!hasChildren">
    <el-menu-item :index="resolvePath">
      <el-icon v-if="item.meta?.icon">
        <component :is="item.meta.icon" />
      </el-icon>
      <template #title>{{ item.meta?.title }}</template>
    </el-menu-item>
  </template>

  <!-- 多级菜单 -->
  <el-sub-menu v-else :index="resolvePath">
    <template #title>
      <el-icon v-if="item.meta?.icon">
        <component :is="item.meta.icon" />
      </el-icon>
      <span>{{ item.meta?.title }}</span>
    </template>
    <SidebarItem
      v-for="child in item.children"
      :key="child.path"
      :item="child"
      :base-path="resolvePath"
    />
  </el-sub-menu>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'

interface Props {
  item: RouteRecordRaw
  basePath?: string
}

const props = withDefaults(defineProps<Props>(), {
  basePath: ''
})

// 是否有子菜单
const hasChildren = computed(() => {
  return props.item.children && props.item.children.length > 0
})

// 解析完整路径
const resolvePath = computed(() => {
  if (props.basePath) {
    return `${props.basePath}/${props.item.path}`
  }
  return props.item.path
})
</script>
