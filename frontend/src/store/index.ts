import { createPinia } from 'pinia'

// 导出store模块
export * from './modules/user'
export * from './modules/app'

// 创建pinia实例
const pinia = createPinia()

export default pinia
