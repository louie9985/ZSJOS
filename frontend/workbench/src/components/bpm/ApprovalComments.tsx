import { useEffect, useRef, useState } from 'react'
import { App, Avatar, Button, Empty, Input, Skeleton, Space, Typography } from 'antd'
import { SendOutlined } from '@ant-design/icons'
import { api, type BpmComment } from '../../services/api'
import DateTimeText from '../DateTimeText'

/** 流程评论：与审批意见分开，任何可处理任务的人都能追加。 */
export default function ApprovalComments({
  comments,
  loading,
  canComment,
  taskId,
  onRefresh, onState, failed, initialText = '', onDraft, submitComment = api.createBpmComment
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
  submitComment?: (data: { taskId: string; message: string }) => Promise<unknown>
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
      await submitComment({ taskId, message: trimmed })
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

