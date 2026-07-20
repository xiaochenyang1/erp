import type { App } from 'vue'
import { permission } from './permission'
import { role } from './role'

/**
 * 注册全局指令
 */
export function setupDirectives(app: App) {
  // 权限指令
  app.directive('permission', permission)

  // 角色指令
  app.directive('role', role)
}

export { permission, role }
