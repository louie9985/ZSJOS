// UTF-8. Actual components with memory-only transport; never publishes a notice.
import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Modal, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import NoticeEditorDialog from '../src/components/NoticeEditorDialog'
import NoticeManagementDetail from '../src/components/NoticeManagementDetail'
import NoticeShareDialog from '../src/components/NoticeShareDialog'
import { AnnouncementDetail } from '../src/pages/AnnouncementCenterPage'
import SafeRichText, { sanitizeRichText } from '../src/components/SafeRichText'
import { noticeManagement, type ManagedNotice } from '../src/services/noticeManagement'
import { noticeShareApi } from '../src/services/noticeShare'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const samples = {
  fixed: '<p>中世健考试安排</p><table style="width:900px;table-layout:fixed"><colgroup><col width="200"><col width="180"><col width="140"><col width="380"></colgroup><tbody><tr style="height:56px"><th>考试轮次</th><th>考试日期</th><th>报名截止日期</th><th>科目安排</th></tr><tr style="height:64px"><td style="background-color:rgb(240,248,255);text-align:center">第一期考试</td><td>2027年3月27日</td><td>2027年3月3日</td><td>基础知识：下午13:00—14:30；专业技能：下午15:30—17:00</td></tr><tr><td colspan="2">合并单元格：请提前报名</td><td rowspan="2">保留跨行</td><td>中文与 English 123</td></tr><tr><td>第四行</td><td>日期待定</td><td>最后一列</td></tr></tbody></table><p>正文结束</p>',
  percent: '<p>百分比宽度</p><table style="width:100%"><tbody><tr><td style="width:25%">第一列</td><td style="width:75%">第二列可换行的长中文内容，用来验证百分比宽度依然随容器变化。</td></tr></tbody></table>',
  cell: '<table><tbody><tr><th style="width:200px">考试轮次</th><th>报名截止日期</th><th>考试日期</th></tr><tr><td>第一期</td><td>2027年3月18日下午</td><td>2027年3月28日</td></tr></tbody></table>',
  plain: '<p>无表格正文，包含<strong>加粗</strong>与<a href="https://example.com">链接</a>。</p>',
}
let saved: ManagedNotice = { id: 910, title: '公告表格格式验收', type: 2, content: samples.fixed, audienceType: 'ALL', targetDeptIds: [], targetUserIds: [], attachments: [], publishStatus: 'DRAFT' }
let saves = 0
http.defaults.adapter = async () => { throw new Error('Fixture forbids real HTTP requests') }
noticeManagement.types = async () => [{ id: 1, label: '公告', value: '2', dictType: 'system_notice_type', sort: 0, status: 0 }]
noticeManagement.recipients = async () => ({ departments: [{ id: 1, parentId: 0, name: '测试部门' }], users: [], defaultSourceDeptId: 1 })
noticeManagement.get = async () => structuredClone(saved)
noticeManagement.update = async input => { saved = { ...saved, ...structuredClone(input), id: 910 }; saves++; return true }
noticeShareApi.get = async () => ({ active: false, attachmentIds: [] })
noticeShareApi.open = async () => { throw new Error('Fixture forbids publishing') }

function Fixture() {
  const [html, setHtml] = useState(samples.fixed)
  const [mode, setMode] = useState('preview')
  const [revision, setRevision] = useState(0)
  const notice = { ...saved, content: html }
  const select = (key: keyof typeof samples) => { saved = { ...saved, content: samples[key] }; setHtml(samples[key]); setRevision(value => value + 1) }
  const proof = (() => {
    const doc = new DOMParser().parseFromString(sanitizeRichText(samples.fixed, true), 'text/html')
    const original = new DOMParser().parseFromString(samples.fixed, 'text/html')
    const defaultDoc = new DOMParser().parseFromString(sanitizeRichText(samples.fixed), 'text/html')
    return JSON.stringify({
      wrappers: doc.querySelectorAll('.announcement-table-scroll').length,
      defaultOff: !defaultDoc.querySelector('.announcement-table-scroll'),
      defaultTableUnchanged: defaultDoc.querySelector('table')?.outerHTML === original.querySelector('table')?.outerHTML,
      columnWidths: [...doc.querySelectorAll('col')].map(col => col.getAttribute('width')),
      colspan: doc.querySelector('[colspan]')?.getAttribute('colspan'), rowspan: doc.querySelector('[rowspan]')?.getAttribute('rowspan'),
      layout: (doc.querySelector('table') as HTMLElement).style.tableLayout,
      plainHasNoWrapper: !sanitizeRichText(samples.plain, true).includes('announcement-table-scroll'),
      independentTables: new DOMParser().parseFromString(sanitizeRichText(samples.fixed + samples.percent, true), 'text/html').querySelectorAll('.announcement-table-scroll > table').length === 2,
    })
  })()
  return <main style={{ padding: 12, minWidth: 0 }}>
    <h1>公告表格格式验收</h1>
    <Space wrap>{Object.keys(samples).map(key => <Button key={key} onClick={() => select(key as keyof typeof samples)}>{key}</Button>)}</Space>
    <Space wrap style={{ display: 'flex', marginBlock: 12 }}>{['preview', 'manage', 'reader', 'share', 'editor', 'material'].map(value => <Button key={value} onClick={() => setMode(value)}>{value}</Button>)}</Space>
    <p role="status">内存保存次数：{saves}；版本：{revision}</p>
    {mode === 'preview' && <Modal title="公告预览" open width={800} footer={null} onCancel={() => setMode('reader')}><NoticeManagementDetail notice={notice} preview /></Modal>}
    {mode === 'reader' && <AnnouncementDetail item={{ ...notice, publishTime: '2026-10-08 12:00:00', read: true }} />}
    {mode === 'manage' && <NoticeManagementDetail notice={notice} />}
    {mode === 'share' && <NoticeShareDialog notice={{ ...notice, publishStatus: 'PUBLISHED' }} onClose={() => setMode('reader')} />}
    {mode === 'editor' && <NoticeEditorDialog key={revision} initial={saved} permissions={['system:notice:update']} onClose={() => { setHtml(saved.content); setMode('reader') }} onChanged={() => { setHtml(saved.content) }} />}
    {mode === 'material' && <SafeRichText html={html} />}
    <output style={{ overflowWrap: 'anywhere' }} data-testid="projection-proof">{proof}</output>
    <details><summary>保存的 HTML</summary><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }} data-testid="saved-html">{saved.content}</pre></details>
  </main>
}
createRoot(document.getElementById('root')!).render(<StrictMode><ThemeProvider><App><Fixture /></App></ThemeProvider></StrictMode>)
