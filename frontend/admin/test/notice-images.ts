// UTF-8. Synthetic content, production renderer, no business requests.
import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import 'element-plus/dist/index.css'
import NoticeContent from '../src/views/system/notice/NoticeContent.vue'
const html = '<p>公告图片</p><img width="160" height="100" src="https://notice-image.test/1.png" alt="第一张"><a href="https://notice-image.test/link"><img width="160" height="100" src="https://notice-image.test/2.png" alt="第二张">普通链接</a>'
const app = createApp({ setup() {
  const content = ref(html)
  return () => h('main', [h('button', { onClick: () => { content.value = '<p>另一篇公告</p>' } }, '更换正文'), h(NoticeContent, { html: content.value })])
} })
app.use(ElementPlus).use(VueDOMPurifyHTML).mount('#app')
