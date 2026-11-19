import { useEffect, useState } from 'react'
import { Alert, Button, Empty, Input, Pagination, Space, Spin, Typography, message } from 'antd'
import { appendNote, getNotes, type NotePage, type NoteSubject } from '../services/contactNotes'
import { createIdempotencyKey } from '../services/idempotency'
import { formatTimestamp } from '../services/time'

export default function ContactNotes({ subject, id }: { subject: NoteSubject; id: number }) {
  const [page, setPage] = useState(1)
  const [revision, reload] = useState(0)
  const [data, setData] = useState<NotePage>()
  const [error, setError] = useState('')
  const [content, setContent] = useState('')
  const [key, setKey] = useState(createIdempotencyKey)
  const [saving, setSaving] = useState(false)
  useEffect(() => {
    let active = true
    setData(undefined)
    setError('')
    getNotes(subject, id, page).then(result => { if (active) setData(result) })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : '沟通记录加载失败') })
    return () => { active = false }
  }, [subject, id, page, revision])

  async function save() {
    setSaving(true)
    try {
      await appendNote(subject, id, content.trim(), key)
      setContent('')
      setKey(createIdempotencyKey())
      setPage(1)
      reload(n => n + 1)
      message.success('沟通记录已追加')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '追加失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  if (error) return <Alert type="error" message={error} action={<Button onClick={() => reload(n => n + 1)}>重试</Button>} />
  if (!data) return <Spin />
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {data.canAppend && <>
      <Input.TextArea aria-label="沟通内容" placeholder="填写本次沟通内容" autoSize={{ minRows: 3 }} maxLength={2000}
        value={content} disabled={saving} onChange={e => { setContent(e.target.value); setKey(createIdempotencyKey()) }} />
      <Button type="primary" disabled={!content.trim()} loading={saving} onClick={() => void save()}>追加跟进记录</Button>
    </>}
    {data.list.length ? data.list.map(note => <article key={note.id}>
      <Typography.Text strong>{note.authorName}</Typography.Text> · {formatTimestamp(note.createdAt)}
      <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{note.content}</Typography.Paragraph>
    </article>) : <Empty description="暂无追加的沟通记录" />}
    {data.total > 10 && <Pagination current={page} pageSize={10} total={data.total} showSizeChanger={false} onChange={setPage} />}
  </Space>
}
