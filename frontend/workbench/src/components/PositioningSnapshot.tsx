import { LinkedText } from './ResourceLink'
import AttachmentCard from './AttachmentCard'
import { Alert, Button, Image, Spin, Typography } from 'antd'
import { EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type PositioningCard, type PositioningFile } from '../services/api'
import type { MaterialVersion } from '../services/materialApi'
import PositioningCardFields from './PositioningCardFields'
import PositioningReadingFields from './PositioningReadingFields'
import PositioningMaterialPreview, { positioningMaterialCover } from './PositioningMaterialPreview'
import PositioningDialog from './PositioningDialog'
import { formatPositioningSnapshotValue } from './ProductionTicketPositioningCard'

export function PositioningFileView({ load, name }: { load: () => Promise<PositioningFile>; name: string }) {
  return <AttachmentCard name={name} load={load} />
}

export function MaterialReference({ card, id, title }: { card: PositioningCard; id: number; title: string }) {
  const [version, setVersion] = useState<MaterialVersion>(), [error, setError] = useState(''), [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(true), [imageError, setImageError] = useState(false)
  const request = useRef(0)
  const load = useCallback(async () => {
    const run = ++request.current
    setError(''); setLoading(true); setImageError(false)
    try {
      const result = await api.positioningCard.snapshotMaterial(card.id, id, card.status === 'co_creating' ? undefined : card.submissionId)
      if (run === request.current) setVersion(result)
    } catch (cause) {
      if (run === request.current) { setVersion(undefined); setError(cause instanceof Error ? cause.message : '历史素材不可读取') }
    } finally { if (run === request.current) setLoading(false) }
  }, [card.id, card.submissionId, card.status, id])
  useEffect(() => { setVersion(undefined); void load(); return () => { request.current++ } }, [load])
  const cover = positioningMaterialCover(version)
  return <article className="positioning-material-card" aria-label={title}>
    <div className="positioning-material-cover">
      {loading ? <Spin size="small" /> : cover && !imageError ? <Image key={`${cover}-${request.current}`} src={cover} alt={title} onError={() => setImageError(true)} />
        : <Typography.Text type="secondary">{imageError ? '封面加载失败' : error ? '素材暂不可读取' : '暂无封面'}</Typography.Text>}
    </div>
    <strong className="positioning-material-title">{title}</strong>
    {error && <Alert type="warning" message={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {imageError && <Button size="small" icon={<ReloadOutlined />} onClick={() => void load()}>重试封面</Button>}
    <Button icon={<EyeOutlined />} disabled={loading} onClick={() => { setOpen(true); void load() }}>预览参考内容</Button>
    <PositioningDialog title={title} open={open} width="min(1480px, calc(100vw - 32px))" styles={{ body: { maxHeight: '78vh', overflowY: 'auto' } }} onCancel={() => setOpen(false)} footer={<><Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新预览</Button><Button onClick={() => setOpen(false)}>关闭</Button></>}>
      {error ? <Alert type="error" message={error} action={<Button onClick={() => void load()}>重试</Button>} /> : loading ? <Spin /> : version ? <PositioningMaterialPreview key={`${id}-${request.current}`} version={version} /> : null}
    </PositioningDialog>
  </article>
}

export default function PositioningSnapshot({ card, reading = false }: { card: PositioningCard; reading?: boolean }) {
  const Fields = reading ? PositioningReadingFields : PositioningCardFields
  const wideKeys = (card.fieldsSnapshot || []).filter(field => formatPositioningSnapshotValue(card.valuesSnapshot?.[field.key], card.dictSnapshot?.[field.key]).length > 120).map(field => field.key)
  return <Fields fields={card.fieldsSnapshot || []} {...(reading ? { wideKeys } : {})} render={field => {
    const value = card.valuesSnapshot?.[field.key], snapshot = card.dictSnapshot?.[field.key]
    if (field.type === 'attachment' && Array.isArray(snapshot)) return <div className="positioning-file-grid">{snapshot.map((item: PositioningFile) =>
      <PositioningFileView key={`${card.id}-${card.submissionId}-${item.id}`} name={item.name} load={() => api.positioningCard.snapshotAttachment(card.id, item.id, card.status === 'co_creating' ? undefined : card.submissionId)} />)}</div>
    if (field.type === 'material_picker' && Array.isArray(snapshot)) return snapshot.length ? <div className="positioning-material-grid">{snapshot.map((item: { materialVersionId: number; titleSnapshot: string }) =>
      <MaterialReference key={`${card.id}-${card.submissionId}-${item.materialVersionId}`} card={card} id={item.materialVersionId} title={item.titleSnapshot || '历史参考素材'} />)}</div> : <Typography.Text type="secondary">未选择参考素材</Typography.Text>
    if (field.type === 'system_history') return <Typography.Text type="secondary">历史提交保留在课程服务定位卡记录中；定位访谈稿可在学员概览查看。</Typography.Text>
    return <LinkedText text={formatPositioningSnapshotValue(value, snapshot)} resource={reading} />
  }} />
}
