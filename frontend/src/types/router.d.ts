import 'vue-router'

/**
 * 扩展路由元信息类型
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题 */
    title?: string
    /** 图标 */
    icon?: string
    /** 是否隐藏菜单 */
    hidden?: boolean
    /** 是否总是显示 */
    alwaysShow?: boolean
    /** 权限代码 */
    permission?: string
    /** 角色列表 */
    roles?: string[]
    /** 是否缓存 */
    keepAlive?: boolean
    /** 面包屑 */
    breadcrumb?: boolean
    /** 是否固定在标签栏 */
    affix?: boolean
    /** 外部链接 */
    link?: string
    /** 排序 */
    sort?: number
  }
}

export {}
