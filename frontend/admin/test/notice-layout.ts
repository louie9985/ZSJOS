// UTF-8. Synthetic content through the production sanitized reader.
import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import 'element-plus/dist/index.css'
import NoticeContent from '../src/views/system/notice/NoticeContent.vue'
const image = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="300" height="180"%3E%3Crect width="300" height="180" fill="skyblue"/%3E%3C/svg%3E'
const html = `<p><img src='${image}' style="width:32%"><img src='${image}' style="width:32%"><img src='${image}' style="width:32%"></p><table style="width:1352px"><colgroup><col width="200"><col width="180"><col width="140"><col width="360"></colgroup><tr><th>考试轮次</th><th>考试日期</th><th>报名截止</th><th>科目安排</th></tr><tr><td>第一期考试</td><td>2027年3月27日</td><td>2027年3月3日</td><td>基础知识及专业技能考试安排，中文长文本。</td></tr></table>`
createApp({ render: () => h('main', { style: 'max-width:1352px;margin:auto' }, h(NoticeContent, { html })) }).use(ElementPlus).use(VueDOMPurifyHTML).mount('#app')
