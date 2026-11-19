import { Empty, Space, Tag, Timeline, Typography } from 'antd'
import type { BpmApprovalDetail, BpmComment, BpmTask } from '../../services/api'
import DateTimeText from '../DateTimeText'
import AttachmentGrid from './AttachmentGrid'
import { approvalHistory, approvalStatus, type HistoryTask } from './approvalHistoryModel'
import { bpmStatusColor, bpmStatusLabel } from './bpmStatus'

function Task({ task, claimable = false }: { task: HistoryTask; claimable?: boolean }) {
  return <div className="bpm-approval-timeline-task" data-history-task={task.id}>
    <Space wrap><Typography.Text>{task.assigneeUser?.nickname || task.ownerUser?.nickname || (claimable ? '待领取' : '待分配')}</Typography.Text>
      <Tag color={bpmStatusColor(task.status)}>{approvalStatus(task.status, bpmStatusLabel)}</Tag>
      {task.parentTaskId && <Tag>加签</Tag>}</Space>
    <div>到达：<DateTimeText value={task.createTime}/></div>
    {task.endTime && <div>完成：<DateTimeText value={task.endTime}/></div>}
    {task.reason && <Typography.Paragraph>意见：{task.reason}</Typography.Paragraph>}
    {task.signPicUrl && <img src={task.signPicUrl} alt="签名" className="bpm-signature-preview"/>}
    <AttachmentGrid attachments={task.attachments?.map((url, index) => ({ url, name: '审批附件 ' + (index + 1) }))}/>
  </div>
}

export default function ApprovalHistory({ detail, records, comments, incomplete = false }: { detail?: BpmApprovalDetail; records: BpmTask[]; comments: BpmComment[]; incomplete?: boolean }) {
  const history = approvalHistory(detail?.activityNodes ?? [], records, comments)
  return <>
    {!incomplete && !history.nodes.length && !history.extra.length && <Empty description="暂无审批记录"/>}
    <Timeline className="bpm-approval-timeline" items={history.nodes.map(node => ({
      children: <div><Space wrap><Typography.Text strong>{node.name}</Typography.Text>
        <Tag color={bpmStatusColor(node.status)}>{approvalStatus(node.status, bpmStatusLabel)}</Tag></Space>
        {node.tasks.map(task => <Task key={task.id} task={task} claimable={task.status === 1 && !!node.candidateUsers?.length}/>)}
        {(!node.tasks.length || node.tasks.some(task => task.status === 1 && !task.assigneeUser && !task.ownerUser)) && !!node.candidateUsers?.length && <p>候选审批人：{node.candidateUsers.map(user => user.nickname).join('、')}</p>}
      </div>
    }))}/>
    {history.extra.length > 0 && <section><Typography.Title level={5}>补充流转记录</Typography.Title>
      {history.extra.map(task => <div key={task.id}><Typography.Text strong>{task.name}</Typography.Text><Task task={task}/></div>)}
    </section>}
  </>
}
