import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { setupDirectives } from './directives'
import { i18n, setI18nLocale } from './i18n'
import { useUserStore } from './store/modules/user'
import { configureAuthSessionHandlers } from './utils/authSession'

import './assets/styles/main.css'
import './styles/index.scss'

const app = createApp(App)
const pinia = createPinia()

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册全局指令
setupDirectives(app)

app.use(pinia)

configureAuthSessionHandlers({
  resetRuntimeState: () => useUserStore(pinia).clearSessionState(),
  redirectToLogin: () => {
    if (router.currentRoute.value.path !== '/login') {
      void router.push('/login')
    }
  }
})

app.use(i18n)
app.use(router)

setI18nLocale(i18n.global.locale.value)

app.mount('#app')
