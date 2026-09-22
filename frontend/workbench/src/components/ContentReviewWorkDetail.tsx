import { Image, Tag, Typography } from 'antd'
import { useRef } from 'react'
import ResourceLink from './ResourceLink'
import AttachmentCard from './AttachmentCard'
import DateTimeText from './DateTimeText'
import { contentReviewApi, type ContentReviewItem } from '../services/materialApi'

const text = (snapshot: Record<string, unknown>, ...keys: string[]) => {
  for (const key of keys) {
    const value = snapshot[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number') return String(value)
  }
  return ''
}

export default function ContentReviewWorkDetail({ item, batchId }: { item: ContentReviewItem; batchId: number }) {
  const pending = useRef<Promise<Awaited<ReturnType<typeof contentReviewApi.get>>>>(undefined)
  const refreshBatch = () => pending.current ?? (pending.current = contentReviewApi.get(batchId).finally(() => { pending.current = undefined }))
  const snapshot = item.contentSnapshot
  const cover = item.files?.find(file => file.fieldKey === 'cover' && file.contentType.startsWith('image/'))
  const files = item.files?.filter(file => file.fieldKey !== 'cover') || []
  const materials = (Array.isArray(snapshot.materialRefs) ? snapshot.materialRefs : []) as Array<Record<string, unknown>>
  const links = [['detailUrl', '作品详情链接'], ['leadResourceUrl', '引流资料链接'], ['referenceWorkUrl', '参考作品链接']]
    .map(([key, label]) => ({ key, label, value: text(snapshot, key) }))
  const topic = text(snapshot, 'topicSnapshot', 'topic')
  const deliverableUrl = text(snapshot, 'deliverableUrl')
  const plannedAt = typeof snapshot.plannedPublishAt === 'number' || typeof snapshot.plannedPublishAt === 'string' ? snapshot.plannedPublishAt : undefined
  return <>
    <div className="content-review-item-body">
      <div className="content-review-item-cover">
        {cover?.previewUrl ? <Image src={cover.previewUrl} alt={`作品封面图：${cover.originalName}`} /> : <Typography.Text type="secondary">暂无封面图</Typography.Text>}
        <span>作品封面图</span>
      </div>
      <div className="content-review-field-groups">
        <dl className="content-review-summary-fields">
          <div><dt>预计发布时间</dt><dd><Tag color={plannedAt ? 'blue' : undefined}><DateTimeText value={plannedAt} emptyText="未记录" /></Tag></dd></div>
          <div><dt>作品目的</dt><dd><Tag color={text(snapshot, 'purposeLabelSnapshot', 'purposeValue') ? 'purple' : undefined}>{text(snapshot, 'purposeLabelSnapshot', 'purposeValue') || '未记录'}</Tag></dd></div>
          <div><dt>作品形式</dt><dd><Tag color={text(snapshot, 'formatLabelSnapshot', 'formatValue') ? 'cyan' : undefined}>{text(snapshot, 'formatLabelSnapshot', 'formatValue') || '未记录'}</Tag></dd></div>
        </dl>
        <dl className="content-review-copy-fields">
          <div><dt>发布标题</dt><dd>{text(snapshot, 'titleSnapshot', 'title') || '未记录'}</dd></div>
          <div><dt>正文文稿</dt><dd className="content-review-field-text">{text(snapshot, 'scriptText') || '未记录'}</dd></div>
          <div><dt>评论区钩子</dt><dd>{text(snapshot, 'commentHook') || '未填写'}</dd></div>
        </dl>
      </div>
    </div>
    <dl className="content-review-resource-links">{links.map(link => <div key={link.key}><dt>{link.label}</dt><dd>{link.value ? <ResourceLink href={link.value} title={link.label} variant="resource" /> : <Typography.Text type="secondary">未填写</Typography.Text>}</dd></div>)}</dl>
    {(topic || deliverableUrl) && <section className="content-review-supplement"><Typography.Text strong>补充信息</Typography.Text><dl className="content-review-resource-links">
      {topic && <div><dt>选题</dt><dd>{topic}</dd></div>}
      {deliverableUrl && <div><dt>成品外链</dt><dd><ResourceLink href={deliverableUrl} title="成品外链" variant="resource" /></dd></div>}
    </dl></section>}
    {(materials.length > 0 || files.length > 0) && <div className="content-review-assets">
      {materials.length > 0 && <section><Typography.Text strong>参考素材</Typography.Text><div className="content-review-reference-grid">
        {materials.map((reference, index) => <article className="content-review-reference" key={text(reference, 'materialId') || index}>
          {text(reference, 'coverPreviewUrl') ? <Image src={text(reference, 'coverPreviewUrl')} alt={`参考素材：${text(reference, 'title')}`} /> : <Typography.Text type="secondary">暂无封面</Typography.Text>}
          <Typography.Text strong>{text(reference, 'title') || '素材'}</Typography.Text>
          <Typography.Text type="secondary">{text(reference, 'materialNo')}</Typography.Text>
        </article>)}
      </div></section>}
      {files.length > 0 && <section><Typography.Text strong>审核附件</Typography.Text><div className="content-review-attachment-grid">
        {files.map(file => <AttachmentCard key={`${batchId}:${item.id}:${file.id}`} name={file.originalName} load={async () => {
          // Each preview/retry refreshes signed URLs through the batch's object authorization.
          const batch = await refreshBatch()
          const refreshed = batch.items.find(row => row.id === item.id)?.files?.find(row => row.id === file.id)
          if (!refreshed) throw new Error('附件不存在或已无权查看')
          return { name: refreshed.originalName, url: refreshed.previewUrl, type: refreshed.contentType, size: refreshed.fileSize }
        }} />)}
      </div></section>}
    </div>}
  </>
}
