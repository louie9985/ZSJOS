// UTF-8. Production editor and renderer, synthetic content without business writes.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import NoticeRichTextEditor from '../src/components/NoticeRichTextEditor'
import SafeRichText from '../src/components/SafeRichText'
import '../src/styles/index.css'

const image = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="300" height="180"%3E%3Crect width="300" height="180" fill="skyblue"/%3E%3C/svg%3E'
const initial = `<p><img src='${image}' style="width:32%"><img src='${image}' style="width:32%"><img src='${image}' style="width:32%"></p><table style="width:1352px;table-layout:fixed"><colgroup><col width="200"><col width="180"><col width="140"><col width="360"></colgroup><tbody><tr><th>考试轮次</th><th>考试日期</th><th>报名截止</th><th>科目安排</th></tr><tr><td>第一期考试</td><td>2027年3月27日</td><td>2027年3月3日</td><td>基础知识及专业技能考试安排，中文长文本。</td></tr></tbody></table>`
function Fixture() {
  const [html, setHtml] = useState(initial)
  const [revision, setRevision] = useState(0)
  return <main style={{ width: 'calc(100% - 32px)', maxWidth: 1352, margin: 'auto' }}>
    <button onClick={() => setRevision(value => value + 1)}>重新打开</button>
    <h1>编辑</h1><NoticeRichTextEditor key={revision} value={html} onChange={setHtml} onUploadChange={() => {}} onError={console.error} />
    <h1>实际阅读</h1><SafeRichText html={html} announcementTables />
  </main>
}
createRoot(document.getElementById('root')!).render(<Fixture />)
