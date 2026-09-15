import { Alert, Button, Empty, Image, Modal, Pagination, Skeleton, Space, Tag, Typography } from 'antd'
import { FileImageOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { api, type MediaContent, type MediaStudentDetail } from '../services/api'
import { formatTimestamp } from '../services/time'
import { publishedWorkCover, publishedWorksForAccount, safeWorkUrl } from './publishedWorksModel'

const PAGE_SIZE = 12
export default function AccountPublishedWorks({ accountId, contents, canQuery }: {
  accountId: number; contents: MediaStudentDetail['contents']; canQuery: boolean
}) {
  const [page, setPage] = useState(1)
  const [reload, setReload] = useState(0)
  const [covers, setCovers] = useState<Record<number, string | undefined>>({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<number>()
  const [detail, setDetail] = useState<MediaContent>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detailReload, setDetailReload] = useState(0)
  const works = publishedWorksForAccount(contents, accountId)
  const currentPage = Math.min(page, Math.max(1, Math.ceil(works.length / PAGE_SIZE)))
  const visible = works.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)
  const versionKeys = JSON.stringify(visible.map(item => [item.id, item.currentVersionNo]))
  useEffect(() => {
    let active = true
    setCovers({}); setError('')
    if (!canQuery) return
    setLoading(true)
    const keys = JSON.parse(versionKeys) as Array<[number, number | undefined]>
    void Promise.allSettled(keys.map(async ([id, version]) => [id, publishedWorkCover(await api.mediaContent.versions(id), version)] as const))
      .then(results => {
        if (!active) return
        const next: Record<number, string | undefined> = {}
        const failures: string[] = []
        results.forEach(result => {
          if (result.status === 'fulfilled') next[result.value[0]] = result.value[1]
          else failures.push(result.reason instanceof Error ? result.reason.message : '封面加载失败')
        })
        setCovers(next); setError([...new Set(failures)].join('；')); setLoading(false)
      })
    return () => { active = false }
  }, [accountId, versionKeys, canQuery, reload])
  useEffect(() => {
    let active = true
    setDetail(undefined); setDetailError('')
    if (!selected || !canQuery) return
    setDetailLoading(true)
    void api.mediaContent.get(selected).then(value => {
      if (!active) return
      if (value.accountId !== accountId) { setDetailError('作品不属于当前账号'); return }
      setDetail(value)
    }).catch(cause => { if (active) setDetailError(cause instanceof Error ? cause.message : '作品加载失败') })
      .finally(() => { if (active) setDetailLoading(false) })
    return () => { active = false }
  }, [selected, accountId, canQuery, detailReload])
  return <section className="media-students-card account-published-works">
    <Typography.Title level={5}>内容发布历史 <Typography.Text type="secondary">{works.length} 个作品</Typography.Text></Typography.Title>
    {!canQuery && <Alert type="info" showIcon title="暂无内容详情查看权限" />}
    {error && <Alert type="warning" showIcon title={error} action={<Button size="small" onClick={() => setReload(value => value + 1)}>重试封面</Button>} />}
    {!works.length ? <Empty description="该账号暂无已发布作品" /> : <>
      <div className="account-published-grid" aria-busy={loading}>
        {visible.map(work => <article className="account-work-card" key={work.id}>
          <button className="account-work-open" disabled={!canQuery} onClick={() => setSelected(work.id)} aria-label={`查看作品：${work.title || work.contentNo}`}>
            <div className="account-work-cover">
              {covers[work.id] ? <img src={covers[work.id]} alt={work.title || '作品封面'} loading="lazy" /> : <span><FileImageOutlined /><br />{loading ? '封面加载中' : '暂无封面'}</span>}
            </div>
            <div className="account-work-caption"><span className="account-work-title">{work.title || '未填写标题'}</span><Typography.Text type="secondary">{formatTimestamp(work.publishedAt)}</Typography.Text><Tag>已发布</Tag></div>
          </button>
        </article>)}
      </div>
      <Pagination align="end" current={currentPage} total={works.length} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPage} hideOnSinglePage />
    </>}
    <Modal title="作品详情" open={Boolean(selected)} onCancel={() => setSelected(undefined)} footer={null} destroyOnHidden>
      {detailLoading ? <Skeleton active /> : detailError ? <Alert type="error" showIcon title={detailError} action={<Button onClick={() => setDetailReload(value => value + 1)}>重试</Button>} /> : detail && <Space orientation="vertical" style={{ width: '100%' }}>
        {selected && covers[selected] && <Image src={covers[selected]} alt={detail.title} />}
        <Typography.Title level={5}>{detail.title}</Typography.Title>
        <Typography.Text>{detail.contentNo}</Typography.Text>
        <Typography.Text type="secondary">发布时间：{formatTimestamp(detail.publishedAt)}</Typography.Text>
        {detail.topic && <Typography.Paragraph>{detail.topic}</Typography.Paragraph>}
        {safeWorkUrl(detail.publishedUrl) && <Button href={safeWorkUrl(detail.publishedUrl)} target="_blank" rel="noopener noreferrer">查看原作品</Button>}
      </Space>}
    </Modal>
  </section>
}
