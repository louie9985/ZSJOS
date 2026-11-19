import type { BpmApprovalDetail, BpmComment, FinanceApprovalProgress } from '../services/api'
import ApprovalHistory from './bpm/ApprovalHistory'

export default function SalesOrderApprovalHistory({ progress, comments = [], incomplete }: {
  progress?: FinanceApprovalProgress
  comments?: BpmComment[]
  incomplete?: boolean
}) {
  const detail: BpmApprovalDetail | undefined = progress && ({
    status: progress.status,
    activityNodes: progress.nodes.map(node => ({
      ...node,
      candidateUsers: node.candidates?.map(user => ({ id: user.id, nickname: user.name })),
      tasks: node.tasks?.map(task => ({
        ...task,
        assigneeUser: task.assigneeName ? { nickname: task.assigneeName } : undefined,
        ownerUser: task.ownerName ? { nickname: task.ownerName } : undefined,
      })),
    })),
  })

  return <ApprovalHistory detail={detail} records={[]} comments={comments} incomplete={incomplete}/>
}
