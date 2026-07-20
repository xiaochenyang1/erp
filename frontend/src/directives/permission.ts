import type { Directive } from 'vue'
import { useUserStore } from '@/store/modules/user'

/**
 * 权限指令
 * 使用方式: v-permission="'user:create'"
 */
export const permission: Directive = {
  mounted(el, binding) {
    const { value } = binding

    if (!value) return

    const userStore = useUserStore()
    const hasPermission = userStore.hasPermission(value)

    if (!hasPermission) {
      // 没有权限则移除元素
      el.parentNode?.removeChild(el)
    }
  }
}
