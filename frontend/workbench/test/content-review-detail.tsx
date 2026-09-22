// UTF-8. Isolated response fixtures; no real business requests or mutations.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ContentReviewBatchPage from '../src/pages/ContentReviewBatchPage'
import { api, http } from '../src/services/api'
import type { ContentReviewBatch } from '../src/services/materialApi'
import '../src/styles/index.css'

const asset = (name: string) => `${location.origin}/test/review-assets/${name}`
const controls = { historyError: false, historyEmpty: false, attachmentError: false, detailDenied: false, gets: [] as number[] }
Object.assign(window, { reviewDetailFixture: controls })
const batch: ContentReviewBatch = {
  id: 3, batchNo: 'CR-TEST-3', studentPersonId: 20, studentName: '审核示例学员', accountId: 10,
  accountIds: [10], operatorUserId: 1, directorUserId: 2, currentStage: 'DIRECTOR', status: 'DIRECTOR_REVIEW',
  version: 1, submittedAt: 1790049600000, processInstanceId: 'process-3', currentTaskId: 'task-3',
  relationSnapshot: {}, availableActions: ['DIRECTOR_DECIDE'], contextSnapshot: { accountSnapshots: [{
    id: 10, nickname: '示例抖音账号', platformLabel: '抖音', operatorName: '示例运营', directorName: '示例编导',
    sStageLabel: '内容验证期', currentStatusLabel: '持续运营', platformAccountId: 'example-account',
    productGoal: '通过健康科普建立信任并支持产品服务转化', productFormLabel: '图文与短视频',
    publishFrequency: '每周三条', primaryProblems: [{ labelSnapshot: '长背景标签需要自然换行且不挤压右侧流程' }],
  }] },
  items: [{ id: 30, contentId: 300, contentVersionId: 3000, contentRecordVersion: 1, sortNo: 0, version: 1, collectMaterial: false,
    contentSnapshot: { contentNo: 'CT-TEST', contentVersionNo: 3, titleSnapshot: '一份健康早餐的搭配方法',
      plannedPublishAt: '2026-09-27T09:30:00', purposeLabelSnapshot: '建立信任', formatLabelSnapshot: '图文',
      scriptText: '早餐要兼顾蛋白质、蔬菜和主食。\n用简单食材，做适合自己的早餐。', commentHook: '你最喜欢哪种搭配？',
      materialRefs: [{ materialId: 1, materialNo: 'MAT-TEST-1', title: '竖版参考素材', coverPreviewUrl: asset('portrait.svg') },
        { materialId: 2, materialNo: 'MAT-TEST-2', title: '横版参考素材', coverPreviewUrl: asset('landscape.svg') }] },
    files: [
      { id: 1, infraFileId: 1, fieldKey: 'cover', sortNo: 0, originalName: '封面.svg', contentType: 'image/svg+xml', fileSize: 128, previewUrl: asset('cover.svg') },
      ...[['截图.svg', 'image/svg+xml', 'landscape.svg'], ['视频.mp4', 'video/mp4', 'video.mp4'], ['审核.pdf', 'application/pdf', 'review.pdf'],
        ['说明.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'review.docx'],
        ['数据.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'review.xlsx'],
        ['演示.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 'review.pptx']].map(([originalName, contentType, name], i) =>
        ({ id: i + 2, infraFileId: i + 2, fieldKey: 'deliverable', sortNo: i, originalName, contentType, fileSize: 2048, previewUrl: asset(name) })),
    ] },
  ],
}
const previous: ContentReviewBatch = { ...batch, id: 2, batchNo: 'CR-TEST-2', status: 'RESUBMITTED', currentStage: 'DONE',
  availableActions: [], currentTaskId: undefined, processInstanceId: 'process-2',
  items: batch.items.map(item => ({ ...item, id: 20, contentSnapshot: { ...item.contentSnapshot,
    topicSnapshot: '历史选题', deliverableUrl: 'https://example.com/finished', detailUrl: 'https://example.com/detail',
    leadResourceUrl: 'https://example.com/lead', referenceWorkUrl: 'https://example.com/reference' } })) }
api.bpmApprovalDetail = async ({ processInstanceId }) => ({ processInstance: { id: processInstanceId, status: 2 }, activityNodes: [
  { id: 'start', name: '发起人', status: 2, nodeType: 1, tasks: [] }, { id: 'review', name: '编导审核', status: 2, nodeType: 1, tasks: [] },
] }) as Awaited<ReturnType<typeof api.bpmApprovalDetail>>
api.bpmCommentList = async () => []
http.defaults.adapter = async config => {
  if (config.method !== 'get') throw new Error('隔离验收不执行业务写入')
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/content-review/batch/page')) data = { list: [batch], total: 1 }
  else if (url.endsWith('/content-review/batch/history')) {
    if (controls.historyError) throw new Error('历史轮次加载失败（模拟）')
    data = controls.historyEmpty ? [] : [previous, batch]
  } else if (url.endsWith('/content-review/batch/get')) {
    const id = Number(config.params.id); controls.gets.push(id)
    if (controls.detailDenied) return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 403, msg: '无权访问内容审核' } }
    if (controls.attachmentError) throw new Error('附件地址刷新失败（模拟）')
    data = id === 2 ? previous : batch
  } else if (url.endsWith('/simple-list')) data = []
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App><div style={{ height: '100dvh' }}><ContentReviewBatchPage permissions={['zsjos:content-review:query', 'zsjos:content-review:director-review']} /></div></App></ThemeProvider></MemoryRouter>)
