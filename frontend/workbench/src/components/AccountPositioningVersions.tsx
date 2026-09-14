import { Alert, Button, Empty, Modal, Pagination, Skeleton, Space, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { accountProfileApi, type ProfileEntry } from '../services/mediaAccountProfile'
import { formatTimestamp } from '../services/time'

export default function AccountPositioningVersions({ accountId }: { accountId: number }) {
  const [open, setOpen] = useState(false)
  const [rows, setRows] = useState<ProfileEntry[]>([])
  const [page, setPage] = useState(1), [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const generation = useRef(0)
  useEffect(() => { setOpen(false); setRows([]); generation.current++; return () => { generation.current++ } }, [accountId])
  const load = async (next: number) => {
    const current = ++generation.current
    setLoading(true); setError('')
    try {
      const result = await accountProfileApi.positioningVersions(accountId, next)
      if (current !== generation.current) return
      setRows(result.list); setTotal(result.total); setPage(next)
    } catch (cause) {
      if (current === generation.current) setError(cause instanceof Error ? cause.message : '历史版本加载失败')
    } finally { if (current === generation.current) setLoading(false) }
  }
  return <>
    <Button onClick={() => { setOpen(true); void load(1) }}>历史定位卡</Button>
    <Modal open={open} title="定位卡提交历史" width="min(1100px, calc(100vw - 32px))" footer={null} onCancel={() => setOpen(false)}>
      {error ? <Alert type="error" message={error} action={<Button onClick={() => void load(page)}>重试</Button>} />
        : loading ? <Skeleton active /> : !rows.length ? <Empty description="暂无正式提交的定位卡" />
          : rows.map((entry, index) => <details key={entry.id}>
            <summary>第 {total - (page - 1) * 10 - index} 次提交 · {formatTimestamp(entry.operatedAt)} · {entry.operatedBy || '未记录'}</summary>
            <Space direction="vertical" style={{ width: '100%' }}>
              {entry.positioning?.fields.map(field => <div key={field.key}>
                <Typography.Text strong>{field.label}</Typography.Text>
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{entry.positioning?.values.find(value => value.key === field.key)?.displayValue || '未填写'}</Typography.Paragraph>
              </div>)}
              {entry.files.map(file => <div key={file.id}>{file.previewUrl && /^https?:\/\//i.test(file.previewUrl)
                ? <a href={file.previewUrl} target="_blank" rel="noreferrer">{file.name} · 查看/下载</a>
                : `${file.name} · 暂不可用`}</div>)}
            </Space>
          </details>)}
      {total > 10 && <Pagination current={page} total={total} pageSize={10} showSizeChanger={false} onChange={next => void load(next)} />}
    </Modal>
  </>
}
