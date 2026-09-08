import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getRuntimeMenuTree, type RuntimeMenu } from '@/api/auth'

/**
 * 运行时菜单 store。
 *
 * 后端 `GET /auth/runtime-menu-tree` 按当前用户角色返回可见菜单树
 * （SUPER_ADMIN 返回全树，其余角色按角色-菜单分配过滤）。
 *
 * 侧边栏消费 `visiblePaths` 作为“可见路径白名单”，对前端静态路由树做裁剪：
 * 前端静态路由仍是组件注册表与 icon/title 来源，后端菜单决定“哪些可见”。
 * 这样角色调整菜单后，重新登录/刷新即可实时生效，且不会因后端 path
 * 与前端未注册路由不一致而出现 404 或空白页。
 */
export const useMenuStore = defineStore('menu', () => {
  const menuTree = ref<RuntimeMenu[]>([])
  const loaded = ref(false)
  let loadingPromise: Promise<boolean> | null = null
  let loadVersion = 0

  // 后端菜单树里所有非空 path 的集合（CATALOG/MENU 节点），BUTTON 节点 path 为空自然忽略
  const visiblePaths = computed(() => {
    const set = new Set<string>()
    const walk = (nodes: RuntimeMenu[]) => {
      for (const node of nodes) {
        if (node.path) set.add(node.path)
        if (node.children && node.children.length) walk(node.children)
      }
    }
    walk(menuTree.value)
    return set
  })

  const loadMenus = async (): Promise<boolean> => {
    if (loaded.value) return true
    if (loadingPromise) return loadingPromise

    const version = loadVersion
    loadingPromise = (async () => {
      try {
        const tree = (await getRuntimeMenuTree()) || []
        if (version !== loadVersion) return false
        menuTree.value = tree
        // 空树也是一次成功响应，不能回退到静态权限菜单。
        loaded.value = true
        return true
      } catch (error) {
        if (version !== loadVersion) return false
        // 接口失败时保留静态菜单回退，路由守卫会继续使用 API 权限校验。
        console.error('加载运行时菜单失败，回退到静态菜单', error)
        loaded.value = false
        return false
      } finally {
        if (version === loadVersion) {
          loadingPromise = null
        }
      }
    })()

    return loadingPromise
  }

  const reset = () => {
    loadVersion++
    loadingPromise = null
    menuTree.value = []
    loaded.value = false
  }

  return {
    menuTree,
    loaded,
    visiblePaths,
    loadMenus,
    reset
  }
})
