import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App,
  Avatar,
  Button,
  Empty,
  Input,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Timeline,
  Typography
} from 'antd'
import { AuditOutlined, LinkOutlined, SendOutlined } from '@ant-design/icons'
import DateTimeText from '../DateTimeText'
import DetailFieldGrid from '../DetailFieldGrid'
import {
  api,
  ApiError,
  AuthenticationError,
  type BpmApprovalDetail as BpmApprovalDetailData,
  type BpmComment,
  type BpmTask,
  type DictData,
  type SimpleUser
} from '../../services/api'
import BpmApprovalActions from './BpmApprovalActions'
import BpmDynamicForm, {
  formatBpmVariable,
  parseBpmFormConf,
  parseBpmFormFields
} from './BpmDynamicForm'
import { bpmStatusColor, bpmStatusLabel } from './bpmStatus'

/** 流程表单：字段定义存于 BPM，可由前端渲染。 */
const FORM_TYPE_NORMAL = 10

const TIMELINE_COLORS: Record<number, string> = {
  2: 'green',
  3: 'red',
  4: 'gray',
  5: 'orange'
}

/** 审批记录时间线：节点顺序与状态均由后端 activityNodes 给出。 */
function ApprovalTimeline({ detail }: { detail?: BpmApprovalDetailData }) {
  const nodes = detail?.activityNodes ?? []
  if (nodes.length === 0) return <Empty description="暂无审批记录"/>

  return <Timeline
    className="bpm-approval-timeline"
    items={nodes.map(node => ({
      color: node.status === undefined ? 'blue' : TIMELINE_COLORS[node.status] ?? 'blue',
      children: <div className="bpm-approval-timeline-node">
        <Space size={8} wrap>
          <Typography.Text strong>{node.name}</Typography.Text>
          {node.status !== undefined && <Tag color={bpmStatusColor(node.status)}>{bpmStatusLabel(node.status)}</Tag>}
        </Space>
        {node.tasks?.map(task => <div key={task.id} className="bpm-approval-timeline-task">
          <Typography.Text type="secondary">
            {task.assigneeUser?.nickname || task.ownerUser?.nickname || '待分配'}
            {task.endTime ? ' · 已处理 ' : ' · 到达 '}
          </Typography.Text>
          <DateTimeText value={task.endTime ?? task.createTime}/>
          {task.reason && <Typography.Paragraph className="bpm-approval-timeline-reason">
            意见：{task.reason}
          </Typography.Paragraph>}
          {task.signPicUrl && <img src={task.signPicUrl} alt="签名" className="bpm-signature-preview"/>}
        </div>)}
        {/* 未生成任务的候选人，仅用于展示后续将由谁审批。 */}
        {node.candidateUsers && node.candidateUsers.length > 0 && !node.tasks?.length && (
          <Typography.Text type="secondary">
            候选审批人：{node.candidateUsers.map(user => user.nickname).join('、')}
          </Typography.Text>
        )}
      </div>
    }))}
  />
}

/** 流转记录：任务级明细，含加签产生的子任务。 */
function TaskRecords({ tasks, loading }: { tasks: BpmTask[]; loading: boolean }) {
  if (loading) return <Skeleton active paragraph={{ rows: 4 }}/>
  if (tasks.length === 0) return <Empty description="暂无流转记录"/>

  return <div className="bpm-approval-records">
    {tasks.map(task => <div key={task.id} className="bpm-approval-record">
      <Space size={8} wrap>
        <Typography.Text strong>{task.name}</Typography.Text>
        <Tag color={bpmStatusColor(task.status)}>{bpmStatusLabel(task.status)}</Tag>
        {task.parentTaskId && <Tag>加签</Tag>}
      </Space>
      <DetailFieldGrid columns={2} items={[
        { key: 'assignee', label: '处理人', value: task.assigneeUser?.nickname || task.ownerUser?.nickname || '-' },
        { key: 'createTime', label: '到达时间', value: <DateTimeText value={task.createTime}/> },
        { key: 'endTime', label: '完成时间', value: <DateTimeText value={task.endTime}/> },
        { key: 'reason', label: '审批意见', value: task.reason || '-', span: 2 as const }
      ]}/>
    </div>)}
  </div>
}

/** 流程评论：与审批意见分开，任何可处理任务的人都能追加。 */
function CommentList({
  comments,
  loading,
  canComment,
  taskId,
  onRefresh
}: {
  comments: BpmComment[]
  loading: boolean
  canComment: boolean
  taskId?: string
  onRefresh: () => void
}) {
  const { message } = App.useApp()
  const [text, setText] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async () => {
    const trimmed = text.trim()
    if (!trimmed || !taskId) return
    setSubmitting(true)
    try {
      await api.createBpmComment({ taskId, message: trimmed })
      setText('')
      message.success('评论已提交')
      onRefresh()
    } catch (error) {
      message.error(error instanceof ApiError ? error.message : '评论提交失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  return <div className="bpm-approval-comments">
    {canComment && taskId && <Space.Compact className="bpm-approval-comment-input">
      <Input.TextArea
        rows={2}
        maxLength={500}
        value={text}
        onChange={event => setText(event.target.value)}
        placeholder="输入评论内容"
      />
      <Button
        type="primary"
        icon={<SendOutlined/>}
        loading={submitting}
        disabled={!text.trim()}
        onClick={() => void submit()}
      >发表</Button>
    </Space.Compact>}
    {loading
      ? <Skeleton active paragraph={{ rows: 3 }}/>
      : comments.length === 0
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

export type BpmApprovalDetailProps = {
  /** 列表选中的任务，提供 processInstanceId / taskId 作为查询入口。 */
  task?: BpmTask
  view: 'todo' | 'done'
  canUpdate: boolean
  users: SimpleUser[]
  dictDataByType?: Record<string, DictData[]>
  /** 该流程已接入员工端业务页时的入口；未接入则不展示。 */
  businessEntry?: { loading: boolean; onOpen: () => void }
  onActionSuccess?: () => void
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
  businessEntry,
  onActionSuccess
}: BpmApprovalDetailProps) {
  const [detail, setDetail] = useState<BpmApprovalDetailData>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [records, setRecords] = useState<BpmTask[]>([])
  const [recordsLoading, setRecordsLoading] = useState(false)
  const [comments, setComments] = useState<BpmComment[]>([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('form')

  const processInstanceId = task?.processInstanceId

  const loadDetail = useCallback(async () => {
    if (!processInstanceId) {
      setDetail(undefined)
      return
    }
    setDetailLoading(true)
    setDetailError('')
    try {
      setDetail(await api.bpmApprovalDetail({ processInstanceId, taskId: task?.id }))
    } catch (error) {
      setDetail(undefined)
      setDetailError(error instanceof AuthenticationError
        ? '登录已失效，请重新登录'
        : error instanceof Error ? error.message : '审批详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }, [processInstanceId, task?.id])

  const loadRecords = useCallback(async () => {
    if (!processInstanceId) return
    setRecordsLoading(true)
    try {
      setRecords(await api.bpmTaskListByProcessInstance(processInstanceId))
    } catch {
      setRecords([])
    } finally {
      setRecordsLoading(false)
    }
  }, [processInstanceId])

  const loadComments = useCallback(async () => {
    if (!processInstanceId) return
    setCommentsLoading(true)
    try {
      setComments(await api.bpmCommentList(processInstanceId))
    } catch {
      setComments([])
    } finally {
      setCommentsLoading(false)
    }
  }, [processInstanceId])

  useEffect(() => { void loadDetail() }, [loadDetail])
  useEffect(() => { setActiveTab('form') }, [processInstanceId])
  useEffect(() => {
    // 流转记录与评论按需加载，避免打开详情就发三个请求。
    if (activeTab === 'record') void loadRecords()
    if (activeTab === 'comment') void loadComments()
  }, [activeTab, loadComments, loadRecords])

  const refresh = useCallback(() => {
    void loadDetail()
    if (activeTab === 'record') void loadRecords()
    if (activeTab === 'comment') void loadComments()
    onActionSuccess?.()
  }, [activeTab, loadComments, loadDetail, loadRecords, onActionSuccess])

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

  /** 业务表单等无字段定义的流程，用流程变量与摘要兜底展示。 */
  const fallbackItems = useMemo(() => {
    const summary = processInstance?.summary ?? []
    if (summary.length > 0) {
      return summary.map((item, index) => ({
        key: `summary-${item.key}-${index}`,
        label: item.key,
        value: item.value
      }))
    }
    const variables = processInstance?.formVariables ?? {}
    return Object.entries(variables).map(([key, value]) => ({
      key: `variable-${key}`,
      label: key,
      value: formatBpmVariable(value)
    }))
  }, [processInstance?.formVariables, processInstance?.summary])

  if (!task) return <Empty description="从左侧选择一条审批任务"/>

  const startUser = processInstance?.startUser?.nickname
    || task.processInstance?.startUser?.nickname
    || '-'
  const subject = processInstance?.name || task.processInstance?.name || task.name || '审批任务'

  return <article className="business-inbox-detail bpm-approval-detail">
    <header className="business-inbox-detail-hero bpm-approval-detail-hero">
      <Avatar size={44} icon={<AuditOutlined/>}/>
      <div className="business-inbox-detail-heading">
        <div>
          <Space size={8} wrap>
            <Typography.Title level={4}>{subject}</Typography.Title>
            <Tag color={bpmStatusColor(task.status)}>{bpmStatusLabel(task.status)}</Tag>
          </Space>
          <Typography.Text type="secondary">{task.name || '流程节点'} · 发起人：{startUser}</Typography.Text>
        </div>
      </div>
      {businessEntry && <Button
        icon={<LinkOutlined/>}
        loading={businessEntry.loading}
        onClick={businessEntry.onOpen}
      >{view === 'todo' ? '打开业务详情' : '查看业务单据'}</Button>}
    </header>

    {detailError && <Alert
      className="business-inbox-error"
      type="error"
      showIcon
      message={detailError}
      action={<Button size="small" onClick={() => void loadDetail()}>重试</Button>}
    />}

    <Tabs
      activeKey={activeTab}
      onChange={setActiveTab}
      items={[
        {
          key: 'form',
          label: '审批详情',
          children: <div className="bpm-approval-form-pane">
            <section className="business-inbox-card bpm-approval-card">
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
                      <Typography.Text type="secondary">流程信息</Typography.Text>
                      <DetailFieldGrid columns={2} items={fallbackItems}/>
                    </>
                    : <Empty description="该流程没有可展示的表单信息"/>}
            </section>

            <section className="business-inbox-card bpm-approval-card">
              <DetailFieldGrid columns={2} items={[
                { key: 'processName', label: '流程名称', value: subject },
                { key: 'taskName', label: '当前节点', value: task.name || '-' },
                { key: 'startUser', label: '发起人', value: startUser },
                {
                  key: 'assignee',
                  label: view === 'todo' ? '当前处理人' : '已处理人',
                  value: task.assigneeUser?.nickname || task.ownerUser?.nickname || '-'
                },
                {
                  key: 'startTime',
                  label: '发起时间',
                  value: <DateTimeText value={processInstance?.startTime ?? task.processInstance?.createTime}/>
                },
                {
                  key: 'taskCreatedAt',
                  label: view === 'todo' ? '到达时间' : '任务到达',
                  value: <DateTimeText value={task.createTime}/>
                },
                ...(view === 'done'
                  ? [{ key: 'taskEndedAt', label: '完成时间', value: <DateTimeText value={task.endTime}/> }]
                  : []),
                { key: 'formName', label: '表单名称', value: processDefinition?.formName || task.formName || '-' },
                { key: 'processInstanceId', label: '流程实例', value: task.processInstanceId, span: 2 as const },
                { key: 'taskId', label: '任务编号', value: task.id, span: 2 as const },
                ...(task.reason
                  ? [{ key: 'reason', label: '审批意见', value: task.reason, span: 2 as const }]
                  : [])
              ]}/>
            </section>
          </div>
        },
        {
          key: 'timeline',
          label: '审批记录',
          children: detailLoading
            ? <Skeleton active paragraph={{ rows: 6 }}/>
            : <ApprovalTimeline detail={detail}/>
        },
        {
          key: 'record',
          label: '流转记录',
          children: <TaskRecords tasks={records} loading={recordsLoading}/>
        },
        {
          key: 'comment',
          label: '流程评论',
          children: <CommentList
            comments={comments}
            loading={commentsLoading}
            canComment={canUpdate && todoTask !== undefined}
            taskId={todoTask?.id}
            onRefresh={loadComments}
          />
        }
      ]}
    />

    {view === 'todo' && !detailLoading && (
      todoTask
        ? <BpmApprovalActions
          task={todoTask}
          canUpdate={canUpdate}
          users={users}
          onSuccess={refresh}
        />
        : <Alert
          type="info"
          showIcon
          message="当前任务不可由本人处理"
          description="该任务已被处理或不在你的待办范围内，可刷新列表确认最新状态。"
        />
    )}
  </article>
}
