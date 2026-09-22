import { Alert, Button, Empty, Modal, Space, Spin } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { contentReviewApi, type ContentReviewBatch } from '../services/materialApi'

export default function StudentContentDraftPicker({ studentPersonId, onClose, onCreate, onResume, canCreate = true }: {
  canCreate?: boolean; studentPersonId: number; onClose: () => void; onCreate: () => void; onResume: (batch: ContentReviewBatch) => void
}) {
  const [rows, setRows] = useState<ContentReviewBatch[]>([])
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const generation = useRef(0)
  const load = async () => {
    const run = ++generation.current; setLoading(true); setError('')
    try {
      const drafts: ContentReviewBatch[] = []
      // The existing API scopes by owner, but has no student filter. Read every page
      // before offering a new draft so older drafts are not silently missed.
      for (let pageNo = 1; ; pageNo++) {
        const page = await contentReviewApi.page({ pageNo, pageSize: 100, mine: true, status: 'DRAFT' })
        if (run !== generation.current) return
        drafts.push(...page.list.filter(row => row.studentPersonId === studentPersonId))
        if (!page.list.length || pageNo * 100 >= page.total) break
      }
      setRows(drafts)
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '草稿加载失败') }
    finally { if (run === generation.current) setLoading(false) }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [studentPersonId])
  const resume = async (id: number) => {
    const run = ++generation.current; setLoading(true); setError('')
    try {
      const batch = await contentReviewApi.get(id)
      if (run !== generation.current) return
      if (batch.studentPersonId !== studentPersonId || batch.status !== 'DRAFT' || !batch.availableActions.includes('SUBMIT')) throw new Error('该草稿已变化或无权编辑，请刷新草稿列表')
      onResume(batch)
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '读取草稿失败') }
    finally { if (run === generation.current) setLoading(false) }
  }
  return <Modal open title="内容审批草稿" onCancel={onClose} footer={<Space><Button onClick={onClose}>取消</Button>{canCreate && <Button disabled={loading || Boolean(error)} onClick={onCreate}>新建内容审批</Button>}</Space>}>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {loading ? <Spin /> : !error && <Space orientation="vertical">{rows.length ? rows.map(row => <Button key={row.id} disabled={!row.availableActions.includes('SUBMIT')} onClick={() => void resume(row.id)}>继续填写 · {row.batchNo}</Button>) : <Empty description="当前学员没有内容审批草稿" />}</Space>}
  </Modal>
}
