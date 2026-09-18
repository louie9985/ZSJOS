// UTF-8. Production page mounted against isolated synthetic transport. No business writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const fixture = { mode: params.get('mode') || 'success', calls: [] as string[], writes: [] as { url: string; data: unknown; params: unknown }[], delay: 0 }
Object.assign(window, { overviewFixture: fixture })
const service = { serviceRelationId: 1, leadId: 11, status: 'active', courseName: '正式组件验收课程', skuName: '年度服务', className: '验收班级', ownerUserName: '规划师示例', contentDirectorUserName: '编导示例', operatorUserName: '运营示例', activatedAt: '2026-08-01 10:00:00' }
const students = [1, 2].map(id => ({ personId: id, personNo: `ST-TEST-${id}`, name: `验收学员${id}`, mobile: '13800000000', wechatId: 'synthetic-only', services: [{ ...service, serviceRelationId: id, leadId: id + 10 }] }))
const plans = [
  ...['douyin', 'xiaohongshu', 'channels', 'other'].map((key, index) => ({ key: `pc_homepage_${key}`, title: ['抖音', '小红书', '视频号', '其他'][index] + '平台主页搭建', materialTypeCode: 'viral_account' })),
  ...Array.from({ length: 7 }, (_, index) => ({ key: `pc_delivery_s${index}`, title: `S${index}期交付约定`, materialTypeCode: 'viral_content' })),
]
const fields = [
  { key: 'name', title: '账号名称建议', type: 'text', group: '账号基础', description: '完整填写提示，不得遗漏。' },
  { key: 'audience', title: '目标用户', type: 'textarea' },
  { key: 'format', title: '主要内容形式', type: 'textarea', group: '内容方向' },
  { key: 'ref', title: '参考账号', type: 'text', referenceFor: 'format', description: '参考提示原文' },
  { key: 'files', title: '采访稿附件', type: 'attachment' },
  { key: 'multi', title: '专业方向', type: 'multi_select' },
  ...plans.flatMap(plan => [{ key: plan.key, title: plan.title, type: 'textarea' }, ...(plan.key === 'pc_delivery_s0' ? [] : [{ key: `${plan.key}_refs`, title: `${plan.title}参考素材`, type: 'material_picker', referenceFor: plan.key, materialTypeCode: plan.materialTypeCode }])]),
].map((field, index) => ({ ...field, sort: index, enabled: true, required: false, systemField: false }))
const card = { id: 101, serviceRelationId: 1, cardNo: 'PC-TEST', status: 'operator_feasibility', submissionId: 301, submissionNo: 3, submissionVersion: 4, version: 7, directorName: '编导示例', operatorName: '运营示例', submittedAt: '2026-09-18 12:00:00', availableActions: ['APPROVE_POSITIONING_FEASIBILITY', 'REJECT_POSITIONING_FEASIBILITY'], fieldsSnapshot: fields, valuesSnapshot: { name: '学员内容账号', audience: '有明确学习需求的人群', format: '完整长文本与换行保留。\n'.repeat(12), ref: 'https://example.com/reference', multi: ['a', 'b'], ...Object.fromEntries(plans.map(plan => [plan.key, `${plan.title}的完整计划内容。`])) }, dictSnapshot: { files: [{ id: 1, name: '完整长文件名-定位访谈与内容方向说明.md' }, { id: 2, name: '第二份访谈稿.md' }, { id: 3, name: '确认图片.png' }], multi: [{ value: 'a', labelSnapshot: '历史专业甲' }, { value: 'b', labelSnapshot: '历史专业乙' }], ...Object.fromEntries(plans.filter(plan => plan.key !== 'pc_delivery_s0').map((plan, index) => [`${plan.key}_refs`, [{ materialVersionId: 500 + index, titleSnapshot: `已选${plan.materialTypeCode === 'viral_account' ? '爆款账号' : '爆款内容'}-${index}` }]])) }, operatorReviewComment: '运营意见必须保留', studentDecisionComment: '学员意见也必须保留', evidence: [{ id: 91, name: '当前版本确认凭证.md', uploadedAt: '2026-09-18 12:00:00' }] }
http.defaults.adapter = async config => {
  const url = config.url || ''; fixture.calls.push(url)
  let data: unknown = []
  if (config.method !== 'get') {
    if (!url.includes('/operator-approve') && !url.includes('/operator-reject')) throw new Error('验收禁止未预期写入')
    fixture.writes.push({ url, data: config.data, params: config.params }); data = true
  } else if (url.endsWith('/media-students/page')) data = { list: students.filter(student => !config.params.keyword || student.name.includes(config.params.keyword)), total: students.length }
  else if (/media-students\/\d+$/.test(url)) data = { student: students.find(student => student.personId === Number(url.split('/').pop())), accounts: [], contents: [], positioningCards: [], positioningDrafts: [] }
  else if (url.includes('contact-context')) data = { serviceRelationId: Number(url.match(/\/(\d+)\//)?.[1] || 1), availableActions: [], visibleTabs: fixture.mode === 'context-denied' ? [] : ['student-info'], currentStage: 'active', version: 1, directorStage: 'positioning_interview_completed', directorInterviewAt: '2026-09-10 10:00:00' }
  else if (url.endsWith('/student/context')) {
    if (fixture.mode === 'partner-error') throw new Error('兼职状态读取失败')
    data = { opened: fixture.mode !== 'unopened', operatorAssignmentConflict: false }
  } else if (url.endsWith('/service-overview')) {
    if (fixture.mode === 'card-error') throw new Error('定位卡读取失败')
    const current = { ...card, serviceRelationId: config.params.serviceRelationId, availableActions: fixture.mode === 'no-actions' ? [] : card.availableActions }
    data = { masterCardId: 101, current, canCreate: false, canSelectMaster: false, candidates: [], history: [{ ...current, submissionId: 201, submissionNo: 2, status: 'confirmed', availableActions: [], valuesSnapshot: { ...card.valuesSnapshot, name: '历史版本专属名称' }, evidence: [{ id: 92, name: '历史版本确认凭证.md' }] }] }
  } else if (url.includes('/snapshot/materials/')) {
    if (fixture.mode === 'material-error') throw new Error('参考素材读取失败')
    const id = Number(url.split('/').pop())
    const history = config.params?.submissionId === 201
    data = { id, materialId: id, versionNo: 1, title: '素材版本标题', fields: [{ key: 'detail', label: '完整素材正文', type: 'textarea' }], values: { detail: history ? '历史版本的素材内容' : '当前版本的素材内容' }, files: id === 501 ? [{ fieldKey: '__cover__', previewUrl: location.origin + '/test/material-cover.svg' }] : [], coverPreviewUrl: id !== 501 && id !== 502 ? location.origin + '/test/material-cover.svg' : undefined, dictSnapshot: {} }
  } else if (url.includes('/evidence/')) data = { id: Number(url.split('/').pop()), name: '确认凭证.md', type: 'text/markdown', url: location.origin + '/test/student-overview.html' }
  else if (url.endsWith('/snapshot/attachments/3')) data = { id: 3, name: '确认图片.png', type: 'image/png', size: 68, url: location.origin + '/test/overview-image.png' }
  else if (url.includes('/snapshot/attachments/')) data = { id: 1, name: '附件.md', type: 'text/markdown', size: 42, url: location.origin + '/test/student-overview.html' }
  else if (url.includes('/student-info-form/detail')) {
    if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
    if (fixture.mode === 'profile-error') throw new Error('学员资料读取失败')
    if (fixture.mode === 'profile-denied') return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 403, msg: '无权读取此学员资料' } }
    data = { status: fixture.mode === 'unsubmitted' ? 'DRAFT' : 'SUBMITTED', fields: ['employer', 'job', 'school', 'education_level', 'study_purpose'].map((key, index) => ({ key, label: ['工作单位', '岗位', '毕业院校', '现学历层次', '报名学习目的'][index], type: key === 'study_purpose' ? 'textarea' : 'text', enabled: true, sort: index, sensitive: false })), values: { employer: '示例单位', job: '示例岗位', school: '示例院校', education_level: '原始学历标签', study_purpose: '完整学习目的。'.repeat(30) }, canReadSensitive: false, canExport: false }
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const permissions = ['zsjos:media-account:query', 'zsjos:positioning-card:query', 'zsjos:partner-invitation:create-student', ...(params.has('no-profile-permission') ? [] : ['zsjos:student-info-form:read'])]
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/my-students?personId=1']}><div style={{ height: '100vh', padding: 12 }}><MediaStudentsPage permissions={permissions} /></div></MemoryRouter></App></ThemeProvider></ConfigProvider>)
