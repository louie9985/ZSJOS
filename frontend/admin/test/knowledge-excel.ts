// UTF-8. Production components, synthetic transport; no live account or file storage.
import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { Dialog } from '../src/components/Dialog'
const state = { fail: false, calls: [] as { url: string; params: unknown }[] }
Object.assign(window, { excelFixture: state })
service.defaults.adapter = async (config) => {
  state.calls.push({ url: config.url || '', params: config.params })
  const p = config.params || {}
  if (p.documentId === 10) await new Promise(resolve => setTimeout(resolve, 600))
  let data: unknown
  let code = 0, msg = ''
  if (config.url?.endsWith('/url')) data = 'data:application/octet-stream,fixture'
  else if (state.fail) { code = 1053130071; msg = '无权访问该知识内容' }
  else data = {
    sheets: [{ index: 0, name: '培训数据' }, { index: 1, name: '空表' }, { index: 2, name: '超限表' }],
    sheetIndex: p.sheetIndex === -1 ? 0 : p.sheetIndex,
    rows: p.sheetIndex === 1 ? [] : [[p.documentId === 11 ? '最新文件' : '中文内容', '12.5%', '2026-10-18', '<script>window.bad=1</script>'], ['合并标题', '', '纵向合并', '旁边内容'], ['', '', '', '下一行']],
    merges: p.sheetIndex === 1 ? [] : [{ row: 1, column: 0, rowSpan: 2, colSpan: 2 }, { row: 1, column: 2, rowSpan: 2, colSpan: 1 }],
    columnCount: 4, truncated: p.sheetIndex === 2,
    notices: p.sheetIndex === 2 ? ['内容已截断：最多展示前 500 行、100 列及 200 万字符，请下载查看完整内容。'] : []
  }
  return { status: 200, statusText: 'OK', headers: {}, config, data: { code, data, msg }, request: { responseType: 'json' } }
}
const boot = createApp({}); setupStore(boot)
const { default: Preview } = await import('../src/views/pms/kb/document/KnowledgeExcelPreview.vue')
const { default: Attachments } = await import('../src/views/pms/kb/document/KnowledgeAttachments.vue')
const app = createApp({
  setup() {
    const documentId = ref(1)
    const mode = ref('internal')
    Object.assign(window, { setExcelDocument: (id: number) => { documentId.value = id } })
    return () => h('main', { style: 'max-width:100%;padding:12px;box-sizing:border-box' }, [
      h('h1', '知识库 Excel 预览验证'),
      ...['internal', 'recycle', 'share'].map(name => h('button', { onClick: () => { mode.value = name } }, name)),
      h(Preview, { documentId: documentId.value, recordId: mode.value === 'recycle' ? 8 : undefined, shareToken: mode.value === 'share' ? 'fixture' : undefined }),
      h(Attachments, { documentId: 1, files: [
        { fileId: 9, fileName: '培训.xlsx', mimeType: 'application/octet-stream', fileSize: 100 },
        { fileId: 10, fileName: '文档.pdf', mimeType: 'application/pdf', fileSize: 100, url: 'about:blank' },
        { fileId: 11, fileName: '图片.png', mimeType: 'image/png', fileSize: 100, url: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=' }
      ] })
    ])
  }
})
setupStore(app); await setupI18n(app); app.use(ElementPlus)
app.component('Dialog', Dialog); app.component('Icon', { render: () => h('span') })
app.mount('#app')
