import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert, Button, Descriptions, Drawer, Dropdown, Empty, InputNumber, Modal, Pagination,
  Select, Space, Tag, message
} from 'antd'
import { DeleteOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmRecruitCandidate, type HrmRecruitInterview } from '../services/api'
import { useDict } from '../services/useDict'
import {
  HRM_DICT, RECRUIT_CANDIDATE_STATUS_COLORS, RECRUIT_CANDIDATE_STATUS_LABELS
} from '../services/hrm'
import {
  InterviewDeleteButton, RecruitCandidateConvertModal, RecruitCandidateFormModal,
  RecruitInterviewFormModal, RecruitInterviewResultModal
} from '../components/HrmRecruitCandidateModals'
import type { ColumnsType } from 'antd/es/table'
import type { MenuProps } from 'antd'
import dayjs from 'dayjs'

const PAGE_SIZE = 10
const DELETE_STATUSES = [1, 2, 3, 4, 7]
const STATUS_TABS = [1, 2, 3, 4, 5, 6, 7, 8].map(value => ({
  value: String(value), label: RECRUIT_CANDIDATE_STATUS_LABELS[value]
}))

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

export default function HrmRecruitCandidatePage({ permissions }: { permissions: string[] }) {
  const [tab, setTab] = useState('1')
  const [items, setItems] = useState<HrmRecruitCandidate[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [detail, setDetail] = useState<HrmRecruitCandidate>()
  const [interviews, setInterviews] = useState<HrmRecruitInterview[]>([])
  const [interviewsLoading, setInterviewsLoading] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmRecruitCandidate>()
  const [interviewTarget, setInterviewTarget] = useState<HrmRecruitCandidate>()
  const [interviewEditing, setInterviewEditing] = useState<HrmRecruitInterview>()
  const [resultInterview, setResultInterview] = useState<HrmRecruitInterview>()
  const [cancelMode, setCancelMode] = useState(false)
  const [convertTarget, setConvertTarget] = useState<HrmRecruitCandidate>()
  const [eliminateTarget, setEliminateTarget] = useState<HrmRecruitCandidate>()
  const [eliminateReason, setEliminateReason] = useState('')
  const [eliminateReasons, setEliminateReasons] = useState<string[]>([])
  const [adjustTarget, setAdjustTarget] = useState<HrmRecruitCandidate>()
  const [adjustType, setAdjustType] = useState<'post' | 'channel'>('post')
  const [adjustValue, setAdjustValue] = useState<number>()
  const [cleanOpen, setCleanOpen] = useState(false)
  const [cleanStatuses, setCleanStatuses] = useState<number[]>([1, 2, 3, 4, 7])
  const [cleanDays, setCleanDays] = useState(30)
  const [cleanIds, setCleanIds] = useState<number[]>()
  const [acting, setActing] = useState(false)
  const [posts, setPosts] = useState<Array<{ value: number; label: string }>>([])
  const [channels, setChannels] = useState<Array<{ value: number; label: string }>>([])

  const candidateEducation = useDict(HRM_DICT.RECRUIT_CANDIDATE_EDUCATION)
  const interviewTypes = useDict(HRM_DICT.RECRUIT_INTERVIEW_TYPE)
  const interviewResults = useDict(HRM_DICT.RECRUIT_INTERVIEW_RESULT)
  const canUpdate = permissions.includes('hrm:recruit:candidate:update')
  const canCreate = permissions.includes('hrm:recruit:candidate:create')
  const canDelete = permissions.includes('hrm:recruit:candidate:delete')
  const canInterviewQuery = permissions.includes('hrm:recruit:interview:query')
  const canInterviewCreate = permissions.includes('hrm:recruit:interview:create')
  const canInterviewUpdate = permissions.includes('hrm:recruit:interview:update')
  const canInterviewDelete = permissions.includes('hrm:recruit:interview:delete')
  const canConfirmEntry = permissions.includes('hrm:employee:update')

  useEffect(() => {
    void Promise.all([
      api.hrm.recruit.post.page({ pageNo: 1, pageSize: 200, status: 1 }),
      api.hrm.recruit.channel.page({ pageNo: 1, pageSize: 200 }),
      api.hrm.recruit.eliminateReason.list()
    ]).then(([postResult, channelResult, reasons]) => {
      setPosts(postResult.list.flatMap(item => item.id != null ? [{ value: item.id, label: item.postName }] : []))
      setChannels(channelResult.list.flatMap(item => item.id != null ? [{ value: item.id, label: item.name }] : []))
      setEliminateReasons(reasons)
    }).catch(() => undefined)
  }, [])

  const loadPage = useCallback(async (page: number, status: string) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.recruit.candidate.page({ pageNo: page, pageSize: PAGE_SIZE, status: Number(status) })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '候选人加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, tab) }, [loadPage, pageNo, tab])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, tab) }, [loadPage, tab])

  const loadInterviewList = async (candidateId: number) => {
    if (!canInterviewQuery) return
    setInterviewsLoading(true)
    try { setInterviews(await api.hrm.recruit.interview.listByCandidate(candidateId)) }
    catch (e) { message.error(e instanceof Error ? e.message : '面试记录加载失败') }
    finally { setInterviewsLoading(false) }
  }

  const openDetail = async (row: HrmRecruitCandidate) => {
    setDetail(row); setInterviews([])
    try {
      const value = await api.hrm.recruit.candidate.get(row.id!)
      setDetail(value)
      if (value.id) void loadInterviewList(value.id)
    } catch (e) { message.error(e instanceof Error ? e.message : '候选人详情加载失败') }
  }

  const openCandidateForm = async (row?: HrmRecruitCandidate) => {
    if (!row) { setEditing(undefined); setFormOpen(true); return }
    try { setEditing(await api.hrm.recruit.candidate.get(row.id!)); setFormOpen(true) }
    catch (e) { message.error(e instanceof Error ? e.message : '候选人详情加载失败') }
  }

  const updateStatus = (row: HrmRecruitCandidate, status: number, action: string) => {
    Modal.confirm({
      title: action, content: `确定将「${row.name}」更新为「${RECRUIT_CANDIDATE_STATUS_LABELS[status]}」吗？`,
      onOk: async () => { await api.hrm.recruit.candidate.updateStatus({ id: row.id!, status }); message.success('状态已更新'); reload() }
    })
  }

  const confirmEntry = (row: HrmRecruitCandidate) => {
    Modal.confirm({
      title: '确认员工入职', content: `确定「${row.name}」已入职吗？`,
      onOk: async () => { await api.hrm.employee.confirmEntry({ id: row.employeeId! }); message.success('已确认入职'); reload() }
    })
  }

  const currentInterview = async (row: HrmRecruitCandidate) => {
    if (!row.interviewId) return undefined
    try { return await api.hrm.recruit.interview.get(row.interviewId) }
    catch (e) { message.error(e instanceof Error ? e.message : '面试详情加载失败'); return undefined }
  }

  const editInterview = async (row: HrmRecruitCandidate) => {
    const interview = await currentInterview(row)
    if (!interview) return
    setInterviewEditing(interview); setInterviewTarget(row)
  }

  const recordResult = async (row: HrmRecruitCandidate, canceled = false) => {
    const interview = await currentInterview(row)
    if (!interview) return
    setCancelMode(canceled); setResultInterview(interview)
  }

  const primaryAction = (row: HrmRecruitCandidate) => {
    if (canInterviewUpdate && row.status === 3 && row.interviewId && row.interviewResult === 4) {
      return <Button type="link" size="small" onClick={() => void editInterview(row)}>重新安排</Button>
    }
    if (canInterviewUpdate && row.status === 3 && row.interviewId) {
      return <Button type="link" size="small" onClick={() => void recordResult(row)}>登记结果</Button>
    }
    if (canConfirmEntry && row.status === 6 && row.employeeId) {
      return <Button type="link" size="small" onClick={() => confirmEntry(row)}>确认入职</Button>
    }
    if (canUpdate && (row.status === 4 || row.status === 5) && !row.employeeId) {
      return <Button type="link" size="small" onClick={() => setConvertTarget(row)}>转为员工</Button>
    }
    if (canInterviewCreate && (row.status === 1 || row.status === 2 || row.status === 4)) {
      return <Button type="link" size="small" onClick={() => { setInterviewEditing(undefined); setInterviewTarget(row) }}>{row.status === 4 ? '安排复试' : '安排面试'}</Button>
    }
    if (canUpdate && row.status === 1) return <Button type="link" size="small" onClick={() => updateStatus(row, 2, '初选通过')}>初选通过</Button>
    if (canUpdate && row.status === 4) return <Button type="link" size="small" onClick={() => updateStatus(row, 5, '发 Offer')}>发 Offer</Button>
    if (canUpdate && row.status === 7) return <Button type="link" size="small" onClick={() => updateStatus(row, 1, '恢复候选人')}>恢复</Button>
    return null
  }

  const openAdjust = (row: HrmRecruitCandidate, type: 'post' | 'channel') => {
    setAdjustTarget(row); setAdjustType(type); setAdjustValue(type === 'post' ? row.postId : row.channelId)
  }

  const moreItems = (row: HrmRecruitCandidate): MenuProps['items'] => {
    const values: MenuProps['items'] = []
    if (canUpdate) {
      values.push({ key: 'edit', label: '编辑候选人' }, { key: 'post', label: '调整职位' }, { key: 'channel', label: '调整渠道' })
      if (row.status !== 7 && row.status !== 8) values.push({ key: 'eliminate', label: '淘汰' })
      if (row.status === 4) values.push({ key: 'offer', label: '发 Offer' })
      if (row.status === 7) values.push({ key: 'restore', label: '恢复为新候选人' })
    }
    if (canInterviewUpdate && row.status === 3 && row.interviewId && row.interviewResult !== 4) {
      values.push({ key: 'interview-change', label: '更改面试安排' }, { key: 'interview-cancel', label: '取消面试' })
    }
    if (canDelete && !row.employeeId && row.status != null && DELETE_STATUSES.includes(row.status)) values.push({ key: 'delete', label: '删除', danger: true })
    return values
  }

  const moreAction = (key: string, row: HrmRecruitCandidate) => {
    if (key === 'edit') void openCandidateForm(row)
    else if (key === 'post' || key === 'channel') openAdjust(row, key)
    else if (key === 'eliminate') { setEliminateTarget(row); setEliminateReason('') }
    else if (key === 'offer') updateStatus(row, 5, '发 Offer')
    else if (key === 'restore') updateStatus(row, 1, '恢复候选人')
    else if (key === 'interview-change') void editInterview(row)
    else if (key === 'interview-cancel') void recordResult(row, true)
    else if (key === 'delete') Modal.confirm({
      title: '删除候选人', content: `确定删除「${row.name}」吗？`, okType: 'danger',
      onOk: async () => { await api.hrm.recruit.candidate.delete(row.id!); message.success('已删除'); reload() }
    })
  }

  const saveAdjust = async () => {
    if (!adjustTarget?.id || !adjustValue) return
    setActing(true)
    try {
      if (adjustType === 'post') await api.hrm.recruit.candidate.updatePost({ id: adjustTarget.id, postId: adjustValue })
      else await api.hrm.recruit.candidate.updateChannel({ id: adjustTarget.id, channelId: adjustValue })
      message.success('已更新'); setAdjustTarget(undefined); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '更新失败') }
    finally { setActing(false) }
  }

  const eliminate = async () => {
    if (!eliminateTarget?.id || !eliminateReason.trim()) return
    setActing(true)
    try {
      await api.hrm.recruit.candidate.eliminate({ id: eliminateTarget.id, eliminate: eliminateReason.trim() })
      message.success('已淘汰'); setEliminateTarget(undefined); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '淘汰失败') }
    finally { setActing(false) }
  }

  const previewClean = async () => {
    if (!cleanStatuses.length || cleanDays < 1) return
    setActing(true)
    try { setCleanIds(await api.hrm.recruit.candidate.cleanIds(cleanStatuses, cleanDays)) }
    catch (e) { message.error(e instanceof Error ? e.message : '清理范围预览失败') }
    finally { setActing(false) }
  }

  const executeClean = () => {
    if (!cleanIds?.length) return
    Modal.confirm({
      title: `删除 ${cleanIds.length} 名候选人`,
      content: `将删除所选状态下持续超过 ${cleanDays} 天的 ${cleanIds.length} 名候选人，删除后无法恢复。`,
      okType: 'danger', okText: '确认删除',
      onOk: async () => {
        const results = await Promise.allSettled(cleanIds.map(id => api.hrm.recruit.candidate.delete(id)))
        const failed = results.filter(result => result.status === 'rejected').length
        if (failed) message.warning(`已删除 ${results.length - failed} 人，${failed} 人删除失败`)
        else message.success(`已删除 ${results.length} 人`)
        setCleanOpen(false); reload()
      }
    })
  }

  const columns: ColumnsType<HrmRecruitCandidate> = [
    { title: '姓名', dataIndex: 'name', width: 100, fixed: 'left' },
    { title: '手机号', dataIndex: 'mobile', width: 130, render: value => value || '-' },
    { title: '应聘职位', dataIndex: 'postName', width: 150, ellipsis: true, render: value => value || '-' },
    { title: '渠道', dataIndex: 'channelName', width: 110, render: value => value || '-' },
    { title: '学历', dataIndex: 'education', width: 90, render: value => value != null ? candidateEducation.labels[String(value)] || value : '-' },
    { title: '工作年限', dataIndex: 'workTime', width: 90, align: 'right', render: value => value != null ? `${value} 年` : '-' },
    { title: '状态', dataIndex: 'status', width: 110, align: 'center', render: value => value != null ? <Tag color={RECRUIT_CANDIDATE_STATUS_COLORS[value]}>{RECRUIT_CANDIDATE_STATUS_LABELS[value]}</Tag> : '-' },
    { title: '面试时间', dataIndex: 'interviewTime', width: 160, render: fmtTime },
    { title: '操作', width: 210, align: 'center', fixed: 'right', render: (_, row) => <Space size="small">
      <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
      {primaryAction(row)}
      {!!moreItems(row)?.length && <Dropdown menu={{ items: moreItems(row), onClick: ({ key }) => moreAction(key, row) }} trigger={['click']}>
        <Button type="text" size="small" icon={<MoreOutlined/>} title="更多操作"/>
      </Dropdown>}
    </Space> }
  ]

  const interviewColumns: ColumnsType<HrmRecruitInterview> = [
    { title: '轮次', dataIndex: 'stageNumber', width: 70, render: value => value ? `第 ${value} 轮` : '-' },
    { title: '方式', dataIndex: 'type', width: 100, render: value => interviewTypes.labels[String(value)] || '-' },
    { title: '面试官', dataIndex: 'interviewEmployeeName', width: 110, render: value => value || '-' },
    { title: '时间', dataIndex: 'interviewTime', width: 150, render: fmtTime },
    { title: '结果', dataIndex: 'result', width: 90, render: value => interviewResults.labels[String(value)] || '-' },
    { title: '评价/取消原因', width: 180, ellipsis: true, render: (_, row) => row.evaluate || row.cancelReason || '-' },
    { title: '操作', width: 120, align: 'center', render: (_, row) => <Space size="small">
      {canInterviewUpdate && row.result === 1 && <Button type="link" size="small" onClick={() => { setInterviewEditing(row); setInterviewTarget(detail) }}>编辑</Button>}
      {canInterviewDelete && <InterviewDeleteButton interview={row} onDeleted={() => void loadInterviewList(row.candidateId)}/>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无候选人"/>
        : <>
          <HrmProTable<HrmRecruitCandidate> advanced persistenceKey="recruit-candidate" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 1300 }}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 人`}/>
        </>

  return <section className="workspace-page hrm-page hrm-recruit-candidate-page">
    <div className="page-heading">
      <Select value={tab} onChange={value => { setTab(String(value)); setPageNo(1) }} style={{ width: 180 }} options={STATUS_TABS}/>
      <Space>
        {canDelete && <Button danger icon={<DeleteOutlined/>} onClick={() => { setCleanIds(undefined); setCleanOpen(true) }}>候选人清理</Button>}
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openCandidateForm()}>新增候选人</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.name || '候选人'} width="min(920px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detail && <>
        <Descriptions column={2} size="small" bordered items={[
          { key: 'name', label: '姓名', children: detail.name },
          { key: 'mobile', label: '手机号', children: detail.mobile || '-' },
          { key: 'post', label: '应聘职位', children: detail.postName || '-' },
          { key: 'channel', label: '渠道', children: detail.channelName || '-' },
          { key: 'education', label: '学历', children: detail.education != null ? candidateEducation.labels[String(detail.education)] || detail.education : '-' },
          { key: 'school', label: '毕业院校', children: detail.graduateSchool || '-' },
          { key: 'work', label: '最近工作单位', children: detail.latestWorkPlace || '-' },
          { key: 'owner', label: '招聘负责人', children: detail.ownerEmployeeName || '-' },
          { key: 'status', label: '状态', children: detail.status != null ? <Tag color={RECRUIT_CANDIDATE_STATUS_COLORS[detail.status]}>{RECRUIT_CANDIDATE_STATUS_LABELS[detail.status]}</Tag> : '-' },
          { key: 'statusTime', label: '状态更新时间', children: fmtTime(detail.statusUpdateTime) },
          { key: 'eliminate', label: '淘汰原因', children: detail.eliminate || '-', span: 2 },
          { key: 'remark', label: '备注', children: detail.remark || '-', span: 2 },
          { key: 'resume', label: '简历附件', span: 2, children: detail.resumeUrls?.length ? <Space wrap>{detail.resumeUrls.map((url, index) => <a key={url} href={url} target="_blank" rel="noreferrer">简历 {index + 1}</a>)}</Space> : '-' }
        ]}/>
        {canInterviewQuery && <div className="hrm-drawer-section">
          <h4 className="hrm-drawer-subtitle">面试记录</h4>
          <HrmProTable<HrmRecruitInterview> rowKey="id" size="small" columns={interviewColumns} dataSource={interviews} loading={interviewsLoading} pagination={false} scroll={{ x: 850 }}/>
        </div>}
      </>}
    </Drawer>

    <RecruitCandidateFormModal open={formOpen} candidate={editing} posts={posts} channels={channels} onClose={() => setFormOpen(false)} onSaved={reload}/>
    <RecruitInterviewFormModal open={!!interviewTarget} candidate={interviewTarget} interview={interviewEditing} onClose={() => { setInterviewTarget(undefined); setInterviewEditing(undefined) }} onSaved={() => { setInterviewTarget(undefined); setInterviewEditing(undefined); reload(); if (detail?.id) void loadInterviewList(detail.id) }}/>
    <RecruitInterviewResultModal open={!!resultInterview} interview={resultInterview} cancelMode={cancelMode} onClose={() => setResultInterview(undefined)} onSaved={() => { setResultInterview(undefined); reload(); if (detail?.id) void loadInterviewList(detail.id) }}/>
    <RecruitCandidateConvertModal open={!!convertTarget} candidate={convertTarget} onClose={() => setConvertTarget(undefined)} onSaved={() => { setConvertTarget(undefined); reload() }}/>

    <Modal title="淘汰候选人" open={!!eliminateTarget} onCancel={() => setEliminateTarget(undefined)} onOk={() => void eliminate()} okButtonProps={{ disabled: !eliminateReason.trim() }} confirmLoading={acting} width="min(760px, 96vw)" destroyOnClose>
      <Select mode="tags" maxCount={1} value={eliminateReason ? [eliminateReason] : []} onChange={values => setEliminateReason(values.at(-1) || '')} options={eliminateReasons.map(reason => ({ value: reason, label: reason }))} placeholder="请选择或输入淘汰原因" style={{ width: '100%' }}/>
    </Modal>

    <Modal title={adjustType === 'post' ? '调整应聘职位' : '调整招聘渠道'} open={!!adjustTarget} onCancel={() => setAdjustTarget(undefined)} onOk={() => void saveAdjust()} okButtonProps={{ disabled: !adjustValue }} confirmLoading={acting} width="min(760px, 96vw)" destroyOnClose>
      <Select showSearch optionFilterProp="label" value={adjustValue} onChange={setAdjustValue} options={adjustType === 'post' ? posts : channels} style={{ width: '100%' }}/>
    </Modal>

    <Modal title="候选人清理" open={cleanOpen} onCancel={() => setCleanOpen(false)} width="min(960px, 96vw)" footer={<Space>
      <Button onClick={() => setCleanOpen(false)}>取消</Button>
      <Button loading={acting} onClick={() => void previewClean()}>预览范围</Button>
      <Button danger type="primary" disabled={!cleanIds?.length} onClick={executeClean}>删除 {cleanIds?.length || 0} 人</Button>
    </Space>} destroyOnClose>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Select mode="multiple" value={cleanStatuses} onChange={value => { setCleanStatuses(value); setCleanIds(undefined) }} options={STATUS_TABS.map(item => ({ value: Number(item.value), label: item.label }))} style={{ width: '100%' }}/>
        <Space>状态持续超过 <InputNumber min={1} value={cleanDays} onChange={value => { setCleanDays(value || 1); setCleanIds(undefined) }}/> 天</Space>
        {cleanIds && <Alert type={cleanIds.length ? 'warning' : 'info'} showIcon message={cleanIds.length ? `找到 ${cleanIds.length} 名待清理候选人` : '当前范围没有待清理候选人'}/>}
      </Space>
    </Modal>
  </section>
}
