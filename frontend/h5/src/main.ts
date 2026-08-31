import 'amfe-flexible'
import 'virtual:uno.css'
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import 'vant/es/notify/style'
import '@/styles/base.css'
import '@/styles/themes/coral.css'
import '@/styles/themes/lavender.css'
import '@/styles/themes/sky.css'
import '@/styles/vant-overrides.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { Locale } from 'vant/es/locale'
import zhCN from 'vant/es/locale/lang/zh-CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import App from './App.vue'
import router from './router'
import { useAppStore } from './stores/app'

dayjs.locale('zh-cn')
Locale.use('zh-CN', zhCN)

const MAX_ROOT_FONT_SIZE = 54

function capRootFontSize() {
  const root = document.documentElement
  const current = Number.parseFloat(root.style.fontSize)
  if (current > MAX_ROOT_FONT_SIZE) {
    root.style.fontSize = `${MAX_ROOT_FONT_SIZE}px`
  }
}

capRootFontSize()
window.addEventListener('resize', capRootFontSize)
window.addEventListener('pageshow', capRootFontSize)

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 初始化主题
const appStore = useAppStore()
appStore.initTheme()

app.mount('#app')
