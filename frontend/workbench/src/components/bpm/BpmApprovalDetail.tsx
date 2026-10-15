import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  App,
  Avatar,
  Button,
  Collapse,
  Empty,
  Input,
  Skeleton,
  Space,
  Tag,
  Typography
} from 'antd'
import { CopyOutlined, LinkOutlined, SendOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import DateTimeText from '../DateTimeText'
import DetailFieldGrid from '../DetailFieldGrid'
import {
  api,
  type BpmApprovalBrief,
  type BpmComment,
  type BpmTask,
  type DictData,
  type SimpleUser
} from '../../services/api'
import BpmApprovalActions from './BpmApprovalActions'
import ApprovalHistory from './ApprovalHistory'
import { useApprovalResource } from './useApprovalResource'
import { approvalStatus } from './approvalHistoryModel'
import BusinessContentCard from './BusinessContentCard'
import FeedbackBusinessContent from '../FeedbackBusinessContent'
import BpmDynamicForm, {
  formatBpmVariable,
  isBlank,
  parseBpmFormConf,
  parseBpmFormFields
} from './BpmDynamicForm'
import { bpmStatusColor, bpmStatusLabel, bpmVariableLabel, hasChinese } from './bpmStatus'

/**
 * 流程表单：字段定义存于 BPM，可由前端渲染。
 */
const FORM_TYPE_NORMAL = 10

/**
 * 流程实例与任务编号：UUID 对审批人没有信息量，但排查问题时要拿得到。
 * 默认折叠，展开后仍是完整值 + 一键复制，不做截断——截断的 ID 贴进日志查不到。
 */
function ProcessIdentifiers({ processInstanceId, taskId }: {
  processInstanceId?: string
  taskId?: string
}) {
  const { message } = App.useApp()
  if (!processInstanceId && !taskId) return null

  const copy = async (label: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      message.success(`已复制${label}`)
    } catch {
      message.error('复制失败，请手动选择文本复制')
    }
  }

  return <Collapse
    ghost
    className="bpm-approval-ids"
    items={[{
      key: 'ids',
      label: <Typography.Text type="secondary">流程标识（排查用）</Typography.Text>,
      children: <div className="bpm-approval-id-list">
        {processInstanceId && <div className="bpm-approval-id">
          <Typography.Text type="secondary">流程实例</Typography.Text>
          <Typography.Text code>{processInstanceId}</Typography.Text>
          <Button type="text" size="small" icon={<CopyOutlined/>} title="复制流程实例"
                  onClick={() => void copy('流程实例', processInstanceId)}/>
        </div>}
        {taskId && <div className="bpm-approval-id">
          <Typography.Text type="secondary">任务编号</Typography.Text>
          <Typography.Text code>{taskId}</Typography.Text>
          <Button type="text" size="small" icon={<CopyOutlined/>} title="复制任务编号"
                  onClick={() => void copy('任务编号', taskId)}/>
        </div>}
      </div>
    }]}
  />
}

/** 流程评论：与审批意见分开，任何可处理任务的人都能追加。 */
function CommentList({
  comments,
  loading,
  canComment,
  taskId,
  onRefresh, onState, failed, initialText = '', onDraft
}: {
  comments: BpmComment[]
  loading: boolean
  canComment: boolean
  taskId?: string
  onRefresh: () => void
  onState?: (dirty: boolean, busy: boolean) => void
  failed?: boolean
  initialText?: string
  onDraft?: (text: string) => void
}) {
  const { message } = App.useApp()
  const [text, setText] = useState(initialText)
  const [submitting, setSubmitting] = useState(false)
  const lock = useRef(false)
  useEffect(() => { onState?.(!!text.trim(), submitting) }, [text, submitting, onState])
  useEffect(() => { onDraft?.(text) }, [text, onDraft])

  const submit = async () => {
    const trimmed = text.trim()
    if (!trimmed || !taskId || !canComment || lock.current) return
    lock.current = true
    setSubmitting(true)
    try {
      await api.createBpmComment({ taskId, message: trimmed })
      setText('')
      message.success('评论已提交')
      onRefresh()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论提交失败，请重试')
    } finally {
      lock.current = false
      setSubmitting(false)
    }
  }

  return <div className="bpm-approval-comments">
    {(canComment || text) && taskId && <Space.Compact className="bpm-approval-comment-input">
      <Input.TextArea
        rows={2}
        maxLength={500}
        value={text}
        readOnly={!canComment || submitting}
        onChange={event => setText(event.target.value)}
        placeholder="输入评论内容"
      />
      <Button
        type="primary"
        icon={<SendOutlined/>}
        loading={submitting}
        disabled={!canComment || !text.trim()}
        onClick={() => void submit()}
      >发表</Button>
    </Space.Compact>}
    {loading
      ? <Skeleton active paragraph={{ rows: 3 }}/>
      : !failed && comments.length === 0
        ? <Empty description="暂无评论"/>
        : comments.map(comment => <div key={comment.id} className="bpm-approval-comment">
          <Space size={8}>
            <Avatar size={28}>{comment.user?.nickname?.slice(0, 1) || '?'}</Avatar>
            <Typography.Text strong>{comment.user?.nickname || '未知用户'}</Typography.Text>
            <Typography.Text type="secondary"><DateTimeText value={comment.createTime}/></Typography.Text>
          </Space>
          <Typography.Paragraph className="bpm-approval-comment-body">{comment.message}</Typography.Paragraph>
        </div>)}
  </div>
}

export type ApprovalDraft = { reason?: string; signPicUrl?: string; comment?: string }
export type BpmApprovalDetailProps = {
  /** 列表选中的任务，提供 processInstanceId / taskId 作为查询入口。 */
  task?: BpmTask
  view: 'todo' | 'done'
  canUpdate: boolean
  users: SimpleUser[]
  dictDataByType?: Record<string, DictData[]>
  /** 列表已按任务批量取到的业务摘要，作为详情卡加载前的占位。 */
  businessBrief?: BpmApprovalBrief
  /** 该流程已接入员工端业务页时的入口；未接入则不展示。 */
  businessEntry?: { loading: boolean; onOpen: () => void }
  /**
   * 业务详情卡下发的跳转信息，作为 businessEntry 的补充。
   *
   * <p>{@code businessEntry} 走的是后端白名单接口，目前只覆盖少数几个域；
   * 更多域由业务内容 Provider 下发 route。两者互斥展示：白名单接口的结论
   * 自带权限校验，更可靠，因此它可用时优先。
   */
  businessRoute?: { route: string; query?: Record<string, unknown> } | null
  onActionSuccess?: (taskCompleted?: boolean) => void
  onState?: (dirty: boolean, busy: boolean) => void
  refreshVersion?: number
  completed?: boolean
  confirmLeave?: () => Promise<boolean>
  initialDraft?: ApprovalDraft
  onDraft?: (draft: Partial<ApprovalDraft>) => void
}

/**
 * BPM 审批详情。表单区域按流程定义的 formType 自适应：
 * - formType=10 流程表单：渲染 BPM 表单设计器定义的字段；
 * - 其他（业务表单等）：渲染流程变量与摘要的只读字段表，保证不会空白。
 *
 * 审批动作全部来自 BpmApprovalActions，由后端按钮配置驱动，新增流程无需改动此处。
 */
export default function BpmApprovalDetail({
  task,
  view,
  canUpdate,
  users,
  dictDataByType,
  businessBrief,
  businessEntry,
  businessRoute,
  onActionSuccess, onState, refreshVersion = 0, completed, confirmLeave, initialDraft, onDraft
}: BpmApprovalDetailProps) {
  const navigate = useNavigate()
  const processInstanceId = task?.processInstanceId
  const taskId = task?.id
  const identity = (taskId ?? '') + view
  const { data: detail, loading: detailLoading, error: detailError, reload: loadDetail } = useApprovalResource(identity, () => processInstanceId ? api.bpmApprovalDetail({ processInstanceId, taskId }) : Promise.resolve(undefined))
  const { data: businessCard, loading: businessCardLoading, error: businessCardError, reload: loadBusinessCard } = useApprovalResource(identity, () => taskId ? api.bpmApprovalBusinessDetail(taskId, view) : Promise.resolve(null))
  const { data: records = [], loading: recordsLoading, error: recordsError, reload: loadRecords } = useApprovalResource(identity, () => processInstanceId ? api.bpmTaskListByProcessInstance(processInstanceId) : Promise.resolve([]))
  const { data: comments = [], loading: commentsLoading, error: commentsError, reload: loadComments } = useApprovalResource(identity, () => processInstanceId ? api.bpmCommentList(processInstanceId) : Promise.resolve([]))
  const [actionDirty, setActionDirty] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const [commentDirty, setCommentDirty] = useState(false)
  const [commentBusy, setCommentBusy] = useState(false)
  const updateAction = useCallback((dirty: boolean, busy: boolean) => { setActionDirty(dirty); setActionBusy(busy) }, [])
  const updateComment = useCallback((dirty: boolean, busy: boolean) => { setCommentDirty(dirty); setCommentBusy(busy) }, [])
  const saveComment = useCallback((comment: string) => onDraft?.({ comment }), [onDraft])
  useEffect(() => { onState?.(actionDirty || commentDirty, actionBusy || commentBusy) }, [actionDirty, commentDirty, actionBusy, commentBusy, onState])
  const containerRef = useRef<HTMLElement>(null)
  const [narrow, setNarrow] = useState(false)
  const [actionOpen, setActionOpen] = useState(false)
  useEffect(() => {
    const observer = new ResizeObserver(entries => setNarrow(entries[0].contentRect.width < 1100))
    if (containerRef.current) observer.observe(containerRef.current)
    return () => observer.disconnect()
  }, [taskId])
  const reloadAll = useCallback(() => { void loadDetail(); void loadBusinessCard(); void loadRecords(); void loadComments() }, [loadDetail, loadBusinessCard, loadRecords, loadComments])
  const previousRefresh = useRef(refreshVersion)
  useEffect(() => { if (previousRefresh.current !== refreshVersion) { previousRefresh.current = refreshVersion; reloadAll() } }, [refreshVersion, reloadAll])
  const refresh = useCallback((taskCompleted?: boolean) => { reloadAll(); onActionSuccess?.(taskCompleted) }, [reloadAll, onActionSuccess])
  const ready = view === 'todo' && detail?.todoTask?.status === 1 && !completed && !detailLoading && !detailError && !businessCardLoading && !businessCardError && detail?.todoTask?.id === taskId

  const processDefinition = detail?.processDefinition
  const processInstance = detail?.processInstance
  // 待办任务由详情接口按当前用户解析；已办视图没有可处理任务。
  const todoTask = view === 'todo' ? detail?.todoTask : undefined

  const formFields = useMemo(
    () => parseBpmFormFields(processDefinition?.formFields),
    [processDefinition?.formFields]
  )
  const formConf = useMemo(
    () => parseBpmFormConf(processDefinition?.formConf),
    [processDefinition?.formConf]
  )

  const isNormalForm = processDefinition?.formType === FORM_TYPE_NORMAL && formFields.length > 0

  /**
   * 业务表单等无字段定义的流程，用流程变量与摘要兜底展示。
   *
   * <p>只保留**已中文化**的条目：后端摘要给出的 label（流程表单字段标题）若已是中文则直接用；
   * 流程变量则必须先命中 {@link BPM_VARIABLE_LABELS}。两条路径都不放行英文 key——
   * 那是流程内部的路由变量，审批人看不懂，也不该看到。全部过滤掉时整块不渲染。
   */
  const fallbackItems = useMemo(() => {
    const summary = processInstance?.summary ?? []
    if (summary.length > 0) {
      return summary
        .map((item, index) => ({
          key: `summary-${item.key}-${index}`,
          label: item.label && hasChinese(item.label) ? item.label : bpmVariableLabel(item.key),
          value: item.value
        }))
        .filter((item): item is { key: string; label: string; value: string } =>
          !!item.label && !isBlank(item.value))
    }
    const variables = processInstance?.formVariables ?? {}
    return Object.entries(variables)
      .map(([key, value]) => ({ key: `variable-${key}`, label: bpmVariableLabel(key), value }))
      .filter((item): item is { key: string; label: string; value: unknown } => !!item.label)
      .map(item => ({
        key: item.key,
        label: item.label,
        value: formatBpmVariable(item.value)
      }))
  }, [processInstance?.formVariables, processInstance?.summary])

  /** 该流程有没有接入业务内容卡——没有时才需要解释"为什么只有流程信息"。 */
  const hasBusinessCard = !!(businessCard?.groups?.length || businessBrief?.fields?.length)

  if (!task) return <Empty description="从左侧选择一条审批任务"/>
  const startUser = processInstance?.startUser?.nickname
    || task.processInstance?.startUser?.nickname
    || '-'
  const subject = processInstance?.name || task.processInstance?.name || task.name || '审批任务'

  /**
   * 业务详情卡下发 route 时的兜底跳转——补上白名单接口未覆盖的业务域。
   * 白名单可用时按钮走 businessEntry（自带权限校验），否则走这里。
   */
  const openBusinessRoute = async () => {
    if (confirmLeave && !await confirmLeave()) return
    if (!businessRoute?.route) return
    const params = new URLSearchParams()
    Object.entries(businessRoute.query || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null) params.set(key, String(value))
    })
    const route = businessRoute.route.startsWith('/') ? businessRoute.route : `/${businessRoute.route}`
    navigate(params.toString() ? `${route}?${params.toString()}` : route, { state: businessRoute.query })
  }
  const showBusinessButton = !!businessEntry || !!businessRoute?.route

  const isFeedback = task.processDefinitionKey === 'zsjos_feedback_requirement_approval'
    || businessBrief?.bizType === 'feedback' || businessCard?.bizType === 'feedback'
  const sidebar = <aside className="bpm-unified-sidebar"><div className="bpm-unified-history">
      {detailError && <Alert type="error" showIcon message="审批详情加载失败" description={detailError} action={<Button onClick={loadDetail}>重试</Button>}/>}
          <Button disabled={actionBusy || commentBusy} size="small" onClick={reloadAll}>刷新流程</Button>
          {recordsError && <Alert type="error" message="流转记录加载失败" description={recordsError} action={<Button onClick={loadRecords}>重试</Button>}/>}
          {commentsError && <Alert type="error" message="评论加载失败" description={commentsError} action={<Button onClick={loadComments}>重试</Button>}/>}

      <Typography.Title level={5}>审批流程</Typography.Title>
      <p>{subject}</p><p>当前任务：{task.name}</p><p>当前处理人：{todoTask?.assigneeUser?.nickname || todoTask?.ownerUser?.nickname || task.assigneeUser?.nickname || task.ownerUser?.nickname || '待分配'}</p>
      {detailLoading && !detail ? <Skeleton active/> : <ApprovalHistory detail={detail} records={records} incomplete={!!detailError || !!recordsError || recordsLoading}/>}
      {recordsLoading && <Skeleton active paragraph={{ rows: 2 }}/>}
      <ProcessIdentifiers processInstanceId={processInstanceId} taskId={task.id}/>
      <Typography.Title level={5}>流程评论</Typography.Title>
      <CommentList comments={comments} loading={commentsLoading} canComment={canUpdate && ready && !actionBusy} taskId={task.id} onRefresh={loadComments} onState={updateComment} failed={!!commentsError} initialText={initialDraft?.comment} onDraft={saveComment}/>
    </div><div className={'bpm-unified-action-dock' + (actionOpen ? ' is-open' : '')}>{narrow && view === 'todo' && <Button disabled={actionBusy || commentBusy} onClick={() => setActionOpen(false)}>收起操作区</Button>}{view === 'done' && <section className="bpm-unified-result"><Typography.Title level={5}>本任务处理结果</Typography.Title><p>{approvalStatus(task.status, bpmStatusLabel)}</p><p>{task.reason || '未填写审批意见'}</p><DateTimeText value={task.endTime}/></section>}    {view === 'todo' && canUpdate && (
      (todoTask?.id === task.id || actionDirty)
        ? <BpmApprovalActions
          task={todoTask?.id === task.id ? todoTask : task}
          decisionOnly={detail?.processInstance?.businessKey?.startsWith('content-review-batch:')}
          canUpdate={canUpdate && ready && (!isFeedback || !!businessCard?.groups?.length) && !commentBusy}
          users={users}
          inline onState={updateAction} onFailure={reloadAll}
          initialDraft={initialDraft} onDraft={onDraft}
          onSuccess={refresh}
        />
        : <Alert
          type="info"
          showIcon
          message="当前任务不可由本人处理"
          description="该任务已被处理或不在你的待办范围内，可刷新列表确认最新状态。"
        />
    )}</div>{view === 'todo' && canUpdate && <div className="bpm-unified-mobile-action"><Button type="primary" onClick={() => setActionOpen(true)}>处理审批</Button></div>}</aside>

  // 需求审批直接在任务详情完成；业务卡由服务端按任务所属轮次提供，不能换成最新需求。
  if (isFeedback) {
    return <article ref={containerRef} className={'business-inbox-detail bpm-approval-detail bpm-unified-detail feedback-task-detail' + (narrow ? ' is-narrow' : '')}>
      <header className="feedback-detail-title">
        <div><Typography.Title level={4}>{businessCard?.title || businessBrief?.title || subject}</Typography.Title>
          <Typography.Text type="secondary">{task.name} · 发起人：{startUser} · 到达时间：<DateTimeText value={task.createTime}/></Typography.Text></div>
        <Tag color={bpmStatusColor(processInstance?.status)}>{approvalStatus(processInstance?.status, bpmStatusLabel)}</Tag>
      </header>
      <section className="feedback-approval-panel">
        <div className="bpm-unified-columns">
          <main className="bpm-unified-content">
            {view === 'done' && <Alert type="info" showIcon message="已办任务 · 本轮内容只读"/>}
            {businessCardError && <Alert type="error" message="业务详情未完整加载" description={businessCardError} action={<Button onClick={loadBusinessCard}>重试</Button>}/>}
            {businessCardLoading ? <Skeleton active paragraph={{ rows: 5 }}/> : businessCard?.message
              ? <Typography.Text type="secondary">{businessCard.message}</Typography.Text>
              : businessCard && <FeedbackBusinessContent fields={(businessCard.groups || [])
                // FeedbackContentProvider 的展示分组：流程及重复摘要已由标题与右侧面板承载。
                .filter(group => group.title !== '反馈信息' && group.title !== '审批')
                .flatMap((group, groupIndex) => (group.fields || []).map((field, index) => ({
                  key: `${groupIndex}:${index}`, label: field.label || group.title || '内容', value: field.value,
                  wide: field.span === 2 || (field.value?.length || 0) > 60,
                  attachments: field.attachments?.length ? field.attachments.map(file => ({
                    name: file.name || '附件', url: file.url, type: file.contentType
                  })) : undefined
                })))}/>}
            {!businessCardLoading && !businessCard && !businessCardError && <Alert type="error" showIcon message="需求内容加载失败，请重试"
              action={<Button onClick={() => void loadBusinessCard()}>重试</Button>}/>}
          </main>
          {sidebar}
        </div>
      </section>
    </article>
  }

  return <article ref={containerRef} className={'business-inbox-detail bpm-approval-detail bpm-unified-detail' + (narrow ? ' is-narrow' : '')}>
    <header className="business-inbox-detail-hero bpm-approval-detail-hero">

      <div className="business-inbox-detail-heading">
        <div>
          <Space size={8} wrap>
            <Typography.Title level={4}>{subject}</Typography.Title>
            <Tag color={bpmStatusColor(task.status)}>{approvalStatus(processInstance?.status ?? task.status, bpmStatusLabel)}</Tag>
          </Space>
          <Typography.Text type="secondary">{task.name || '流程节点'} · 发起人：{startUser}</Typography.Text>
        </div>
      </div>
      {showBusinessButton && <Button
        icon={<LinkOutlined/>}
        loading={businessEntry?.loading}
        onClick={async () => { if (businessEntry) { if (!confirmLeave || await confirmLeave()) businessEntry.onOpen() } else void openBusinessRoute() }}
      >{view === 'todo' ? '打开业务详情' : '查看业务单据'}</Button>}
    </header>



<div className="bpm-unified-columns"><main className="bpm-unified-content"><div className="bpm-approval-form-pane">
            {businessCardError && <Alert type="error" message="业务详情未完整加载" description={businessCardError} action={<Button onClick={loadBusinessCard}>重试</Button>}/>}
            {/* 业务内容卡：由后端按 businessKey 解析业务表得出，label/value 已中文化。 */}
            {(businessCardLoading || businessCard || businessBrief) &&
              <section className="business-inbox-card bpm-approval-card">
                <Typography.Text className="bpm-approval-card-title">业务信息</Typography.Text>
                <BusinessContentCard
                  loading={businessCardLoading && !businessBrief}
                  card={businessCard ?? (businessBrief ? {
                    bizType: businessBrief.bizType,
                    title: businessBrief.title,
                    statusText: businessBrief.subtitle,
                    groups: businessBrief.fields?.length
                      ? [{ title: '', fields: businessBrief.fields }]
                      : []
                  } : null)}
                />
              </section>}

            <section className="business-inbox-card bpm-approval-card bpm-approval-flow-card">
              {detailLoading
                ? <Skeleton active paragraph={{ rows: 6 }}/>
                : isNormalForm
                  ? <BpmDynamicForm
                    fields={formFields}
                    conf={formConf}
                    values={processInstance?.formVariables}
                    fieldsPermission={detail?.formFieldsPermission}
                    dictDataByType={dictDataByType}
                    readOnly
                  />
                  : fallbackItems.length > 0
                    ? <>
                      <Typography.Text className="bpm-approval-card-title">申请补充信息</Typography.Text>
                      <DetailFieldGrid columns={2} items={fallbackItems}/>
                    </>
                    : <Typography.Text type="secondary">
                      {/* 兜底块为空是常态而非故障：变量全是流程内部路由用的，
                          业务内容由上面的业务信息卡承载。有话直说，别给一个空白卡片。 */}
                      {hasBusinessCard
                        ? '本流程的业务内容已在上方「业务信息」中展示。'
                        : '该流程未接入业务内容，也没有可展示的流程字段。'}
                    </Typography.Text>}
            </section>

</div></main>{sidebar}</div>
  </article>
}
