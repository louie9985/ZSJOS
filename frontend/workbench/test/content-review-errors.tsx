// UTF-8. Synthetic responses only; the adapter rejects every unexpected request.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { DraftEditDialog } from '../src/pages/ContentReviewBatchPage'
import { http, ApiError } from '../src/services/api'
import type { ContentReviewBatch } from '../src/services/materialApi'
import '../src/styles/index.css'

let mode = 'field', resetView = (_mode: string) => {}
const fixture = { saves: 0, submits: 0, reads: 0, closes: 0, requests: [] as unknown[], reset: (value: string) => resetView(value) }
Object.assign(window, { contentErrorFixture: fixture })
function original(): ContentReviewBatch {
  return { id: 7, batchNo: 'TEST-ERROR-7', studentPersonId: 3, accountId: 4, accountIds: [4], status: 'DRAFT', currentStage: 'DRAFT', version: 2,
    operatorUserId: 1, relationSnapshot: {}, availableActions: ['SUBMIT'], contextSnapshot: { accountSnapshots: [{ id: 4, accountNo: 'TEST-A4', nickname: '验收账号' }] },
    items: [0, 1].map(index => ({ id: index + 8, contentId: index + 9, contentVersionId: index + 10, contentRecordVersion: 0, sortNo: index + 1, version: 0,
      files: [{ fieldKey: 'cover', infraFileId: 99, originalName: '验收封面.png', contentType: 'image/png', fileSize: 10 }],
      contentSnapshot: { title: `保留输入作品 ${index + 1}`, scriptText: '保存失败时不要清空正文', purposeValue: 'test', purposeLabelSnapshot: '原作品目的', formatValue: 'test', formatLabelSnapshot: '原作品形式',
        leadResourceUrl: mode === 'invalid-link' && index === 1 ? 'http://example.com/lead' : 'https://example.com/lead',
        plannedPublishAt: mode === 'past-time' && index === 1 ? '2020-01-01T12:00:00' : '2099-01-01T12:00:00' } })) }
}
let current = original()
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  const response = (payload: unknown) => ({ config, data: payload, status: 200, statusText: 'OK', headers: {} })
  if (url.includes('dict-data')) {
    if (mode === 'dict-error') return response({ code: 403, msg: '字典读取无权限' })
    data = mode === 'empty-dict' ? [] : ['zsjos_content_purpose', 'zsjos_content_format'].map(dictType => ({ dictType, value: 'test', label: '现在的新名称' }))
  } else if (url.endsWith('/batch/get')) {
    fixture.reads++
    if (mode === 'refresh-error' && fixture.saves) throw new ApiError(0, '读取连接中断')
    data = mode === 'changed' ? { ...current, status: 'DIRECTOR_REVIEW', processInstanceId: 'test-p' } : structuredClone(current)
  } else if (url.endsWith('/save-student-draft')) {
    fixture.saves++; fixture.requests.push(JSON.parse(config.data))
    await new Promise(resolve => setTimeout(resolve, 100))
    if (mode === 'field') return response({ code: 1900012100, msg: '第 2 件作品：引流资料链接格式不正确，请填写包含有效域名的完整 HTTPS 地址', data: { fieldPath: 'works[1].leadResourceUrl', workIndex: 1 } })
    if (mode === 'conflict') return response({ code: 1900020024, msg: '审核批次或条目已变化，请刷新后重试' })
    const request = JSON.parse(config.data)
    current = { ...current, version: current.version + 1, items: current.items.map((item, index) => ({ ...item, contentSnapshot: request.works[index] })) }
    data = current.id
  } else if (url.endsWith('/submit')) {
    fixture.submits++
    if (mode === 'submit-rejected') return response({ code: 1900012124, msg: '内容审批流程尚未发布，请联系管理员发布后再提交' })
    if (mode === 'submit-unknown') throw new ApiError(0, '请求超时')
    current = { ...current, status: 'DIRECTOR_REVIEW', processInstanceId: 'test-p' }
    if (mode === 'submit-lost') throw new ApiError(0, '响应丢失')
    data = true
  } else throw new Error(`Unexpected fixture request: ${config.method} ${url}`)
  return response({ code: 0, data })
}
function Fixture() {
  const [key, setKey] = useState(0), [open, setOpen] = useState(true), [batch, setBatch] = useState(current)
  resetView = value => {
    mode = value; Object.assign(fixture, { saves: 0, submits: 0, reads: 0, closes: 0, requests: [] })
    current = original(); setBatch(current); setKey(key + 1); setOpen(true)
  }
  return <DraftEditDialog key={key} batch={batch} open={open} onClose={() => { fixture.closes++; setOpen(false) }} onSaved={() => {}} />
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ConfigProvider><App><Fixture /></App></ConfigProvider></MemoryRouter>)
