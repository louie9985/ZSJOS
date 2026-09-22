import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Collapse, Empty, Skeleton, Space, Tag, Timeline, Tooltip, Typography } from 'antd'
import { CheckCircleOutlined, HistoryOutlined } from '@ant-design/icons'
import DateTimeText from '../DateTimeText'
import {
  api,
  type BpmApprovalDetail,
  type BpmComment,
  type BpmTask,
  type SimpleUser
} from '../../services/api'
import BpmApprovalActions from './BpmApprovalActions'
import { bpmStatusColor, bpmStatusLabel } from './bpmStatus'

const TIMELINE_COLORS: Record<number, string> = {
  2: 'green',
  3: 'red',
  4: 'gray',
  5: 'orange'
}

/**
 * 业务页内嵌的 BPM 流程面板。
 *
 * 用于结论在业务页产生的流程（如生产内容批审）：业务结论仍由业务界面负责，
 * 这里只补齐"流程走到哪一步"的可见性和流程类审批动作（加签、转办、委派、抄送、评论）。
 *
 * 通过 allowDecision=false 明确不展示通过/拒绝，避免绕过业务侧的逐条结论。
 */
/**
 * 业务侧推进流程的动作。用于结论在业务页产生的流程：
 * 由业务界面提供"交卷"按钮，服务端在校验业务结论齐备后才推进 BPM。
 */
export type BpmBusinessAdvanceAction = {
  label: string
  alternative?: { label: string; disabledReason?: string; onTrigger: () => void }
  /** 未就绪时禁用并说明原因，例如逐条结论尚未填齐。 */
  disabledReason?: string
  /** 进度说明，例如「已完成 3/5 条结论」。 */
  progress?: string
  onTrigger: () => void
}

export default function BpmProcessPanel({
  processInstanceId,
  taskId,
  users,
  canUpdate,
  allowDecision = false,
  decisionOnly = false,
  businessAdvance,
  onActionSuccess
}: {
  processInstanceId?: string
  /** 当前待办任务编号；业务页通常已持有，可减少一次解析。 */
  taskId?: string
  users: SimpleUser[]
  canUpdate: boolean
  /** 是否允许在此面板直接做通过/拒绝的流程结论。 */
  allowDecision?: boolean
  decisionOnly?: boolean
  /** 业务侧的推进动作，与流程类动作并列展示。 */
  businessAdvance?: BpmBusinessAdvanceAction
  onActionSuccess?: () => void
}) {
  const [detail, setDetail] = useState<BpmApprovalDetail>()
  const [comments, setComments] = useState<BpmComment[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [commentError, setCommentError] = useState('')

  const load = useCallback(async () => {
    if (!processInstanceId) {
      setDetail(undefined)
      return
    }
    setLoading(true)
    setError(''); setCommentError('')
    try {
      const [approvalDetail, commentList] = await Promise.all([
        api.bpmApprovalDetail({ processInstanceId, taskId }),
        api.bpmCommentList(processInstanceId).catch(() => { setCommentError('历史评论加载失败，请重试'); return [] as BpmComment[] })
      ])
      setDetail(approvalDetail)
      setComments(commentList)
    } catch (cause) {
      setDetail(undefined)
      setError(cause instanceof Error ? cause.message : '审批流程加载失败')
    } finally {
      setLoading(false)
    }
  }, [processInstanceId, taskId])

  useEffect(() => { void load() }, [load])

  const refresh = useCallback(() => {
    void load()
    onActionSuccess?.()
  }, [load, onActionSuccess])

  const nodes = detail?.activityNodes ?? []
  // 面板内的动作只针对当前用户的待办任务；已办或非本人任务不提供动作。
  const todoTask: BpmTask | undefined = detail?.todoTask

  const advanceButton = businessAdvance && <Space wrap>
    <Tooltip title={businessAdvance.disabledReason}>
      <Button type="primary" icon={<CheckCircleOutlined/>}
        disabled={Boolean(businessAdvance.disabledReason)} onClick={businessAdvance.onTrigger}>
        {businessAdvance.label}
      </Button>
    </Tooltip>
    {businessAdvance.alternative && <Tooltip title={businessAdvance.alternative.disabledReason}>
      <Button danger disabled={Boolean(businessAdvance.alternative.disabledReason)}
        onClick={businessAdvance.alternative.onTrigger}>{businessAdvance.alternative.label}</Button>
    </Tooltip>}
  </Space>

  const timelineItems = useMemo(() => nodes.map(node => ({
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
      </div>)}
      {node.candidateUsers && node.candidateUsers.length > 0 && !node.tasks?.length && (
        <Typography.Text type="secondary">
          待审批：{node.candidateUsers.map(user => user.nickname).join('、')}
        </Typography.Text>
      )}
    </div>
  })), [nodes])

  if (!processInstanceId) return null

  return <section className="bpm-process-panel">
    <Collapse
      defaultActiveKey={['flow']}
      items={[{
        key: 'flow',
        label: <Space size={8}>
          <HistoryOutlined/>
          <Typography.Text strong>审批流程</Typography.Text>
          {detail?.processInstance?.status !== undefined && (
            <Tag color={bpmStatusColor(detail.processInstance.status)}>
              {bpmStatusLabel(detail.processInstance.status)}
            </Tag>
          )}
        </Space>,
        children: <>
          {commentError && <Alert type="warning" showIcon message={commentError} action={<Button onClick={() => void load()}>重试</Button>} />}
          {error && <Alert
            type="error"
            showIcon
            message={error}
            action={<Button size="small" onClick={() => void load()}>重试</Button>}
          />}

          {loading
            ? <Skeleton active paragraph={{ rows: 5 }}/>
            : nodes.length > 0
              ? <Timeline className="bpm-approval-timeline" items={timelineItems}/>
              : !error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批记录"/>}

          {comments.length > 0 && <div className="bpm-process-panel-comments">
            <Typography.Text strong>流程评论</Typography.Text>
            {comments.map(comment => <div key={comment.id} className="bpm-approval-comment">
              <Space size={8}>
                <Typography.Text strong>{comment.user?.nickname || '未知用户'}</Typography.Text>
                <Typography.Text type="secondary"><DateTimeText value={comment.createTime}/></Typography.Text>
              </Space>
              <Typography.Paragraph className="bpm-approval-comment-body">{comment.message}</Typography.Paragraph>
            </div>)}
          </div>}

          {/*
            没有解析到本人待办任务时（例如审批详情未返回 todoTask），
            业务侧推进动作仍需可用：它由业务权限授权，不依赖 BPM 任务解析。
          */}
          {!loading && !todoTask && businessAdvance && <div className="bpm-approval-actions">
            {businessAdvance.progress && (
              <Typography.Text type="secondary">{businessAdvance.progress}</Typography.Text>
            )}
            <Space wrap>{advanceButton}</Space>
          </div>}

          {!loading && todoTask && <BpmApprovalActions
            task={todoTask}
            canUpdate={canUpdate}
            users={users}
            allowDecision={allowDecision}
            decisionOnly={decisionOnly}
            decisionHint={allowDecision
              ? undefined
              : '先逐条保存审核结论，再选择通过或退回运营修改；退回会结束本轮审批。'}
            extraActions={advanceButton}
            extraHint={businessAdvance?.progress}
            onSuccess={refresh}
          />}
        </>
      }]}
    />
  </section>
}
