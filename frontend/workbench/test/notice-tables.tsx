// UTF-8. Local component fixture; no business records are read or written.
import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import NoticeRichTextEditor from '../src/components/NoticeRichTextEditor'
import SafeRichText from '../src/components/SafeRichText'
import '../src/styles/index.css'

const sample = '<p>中世健考试安排</p><table style="width: auto;"><tbody><tr><th style="width: 200px;">考试轮次</th><th>报名截止时间</th><th>考试日期</th></tr><tr><td>第一期考试</td><td>2027年3月18日下午13：00</td><td>2027年3月28日</td></tr><tr><td colspan="2">合并单元格：请提前报名</td><td>中文与 English 123</td></tr></tbody></table><p>正文结束</p>'
const narrowSample = sample.replace('<table style="width: auto;">', '<table style="width: 240px; table-layout: fixed;"><colgroup><col width="200"><col width="20"><col width="20"></colgroup>')
function Fixture() {
  const [html, setHtml] = useState(sample)
  const [saved, setSaved] = useState(sample)
  const [key, setKey] = useState(0)
  const [narrow, setNarrow] = useState(false)
  const [error, setError] = useState('')
  return <main style={{ width: narrow ? 340 : 920, maxWidth: '100%', margin: 'auto' }}>
    <h1>公告表格验收</h1>
    <button onClick={() => setNarrow(value => !value)}>切换窄屏容器</button>
    <button onClick={() => { setHtml(''); setKey(value => value + 1) }}>新建空正文</button>
    <button onClick={() => { setHtml(sample); setKey(value => value + 1) }}>载入复制表格样例</button>
    <button onClick={() => { setHtml(narrowSample); setKey(value => value + 1) }}>载入固定窄列样例</button>
    <button onClick={() => setSaved(html)}>保存到本地样例</button>
    <button onClick={() => { setHtml(saved); setKey(value => value + 1) }}>重新打开样例</button>
    <p role="alert">{error}</p>
    <NoticeRichTextEditor key={key} value={html} onChange={setHtml} onUploadChange={() => {}} onError={setError} />
    <h2>公告预览</h2><SafeRichText announcementTables html={html} />
    <details><summary>当前 HTML</summary><pre data-testid="html">{html}</pre></details>
  </main>
}
createRoot(document.getElementById('root')!).render(<StrictMode><Fixture /></StrictMode>)
