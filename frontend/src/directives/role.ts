import type { Directive } from 'vue'
import { useUserStore } from '@/store/modules/user'

/**
 * 角色指令
 * 使用方式: v-role="'系统管理员'" 或 v-role="['系统管理员', '财务经理']"
 */
export const role: Directive = {
  mounted(el, binding) {
    const { value } = binding

    if (!value) return

    const userStore = useUserStore()
    const userRoles = userStore.userInfo?.roles || []

    let hasRole = false

    if (typeof value === 'string') {
      // 单个角色
      hasRole = userRoles.includes(value)
    } else if (Array.isArray(value)) {
      // 多个角色，满足任一即可
      hasRole = value.some(role => userRoles.includes(role))
    }

    if (!hasRole) {
      // 没有角色权限则移除元素
      el.parentNode?.removeChild(el)
    }
  }
}
