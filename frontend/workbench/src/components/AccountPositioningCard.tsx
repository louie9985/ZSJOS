import { Alert, App, Button, Empty, Modal, Radio, Skeleton, Space, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { api, type PositioningCard } from '../services/api'
import PositioningCardFields from './PositioningCardFields'
import { formatPositioningSnapshotValue } from './ProductionTicketPositioningCard'

export function CardSnapshot({ card }: { card: PositioningCard }) {
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const download = async (id: number) => {
    try {
      setError('')
      const file = await api.positioningCard.attachment(card.id, id)
      if (!file.url || !['http:', 'https:'].includes(new URL(file.url).protocol)) throw new Error('附件下载地址不可用')
      window.open(file.url, '_blank', 'noopener,noreferrer')
    } catch (cause) { setError(cause instanceof Error ? cause.message : '附件读取失败') }
  }
  return <>
    <Typography.Paragraph>{formatPositioningSnapshotValue(card.valuesSnapshot?.pc_account_name)}</Typography.Paragraph>
    <Button onClick={() => setOpen(true)}>查看完整定位卡</Button>
    <Modal title="定位卡（只读）" width="min(1180px, calc(100vw - 32px))" open={open} onCancel={() => setOpen(false)} footer={null}>
    <PositioningCardFields fields={card.fieldsSnapshot || []} render={field => {
      const value = card.valuesSnapshot?.[field.key]
      const snapshot = card.dictSnapshot?.[field.key]
      if (field.type === 'attachment' && Array.isArray(value)) return <Space orientation="vertical">{value.map(id => <Button key={String(id)} type="link" onClick={() => void download(Number(id))}>查看附件</Button>)}</Space>
      if (field.type === 'material_picker' && Array.isArray(snapshot)) return <Space orientation="vertical">{snapshot.map((item, index) => <Typography.Text key={index}>{String(item?.titleSnapshot || '已保存参考素材')}</Typography.Text>)}</Space>
      if (field.type === 'system_history') return <Typography.Text type="secondary">历史提交见下方；访谈稿在学员定位访谈中查看</Typography.Text>
      return <Typography.Text>{formatPositioningSnapshotValue(value, snapshot)}</Typography.Text>
    }} />
    {error && <Alert type="error" message={error} />}
    </Modal>
  </>
}

export default function AccountPositioningCard({ accountId, canQuery }: { accountId: number; canQuery: boolean }) {
  const { modal, message } = App.useApp()
  const [options, setOptions] = useState<Awaited<ReturnType<typeof api.positioningCard.applicationOptions>>>()
  const [selectOpen, setSelectOpen] = useState(false), [selected, setSelected] = useState<number>(), [busy, setBusy] = useState(false)
  const [requestKey, setRequestKey] = useState(() => crypto.randomUUID())
  const [data, setData] = useState<Awaited<ReturnType<typeof api.positioningCard.accountOverview>>>()
  const [error, setError] = useState(''), [loading, setLoading] = useState(true), [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setData(undefined); setError(''); setLoading(canQuery)
    if (canQuery) Promise.all([api.positioningCard.accountOverview(accountId), api.positioningCard.applicationOptions(accountId)]).then(([result, available]) => { if (active) { setData(result); setOptions(available) } })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '定位卡加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [accountId, canQuery, retry])
  if (!canQuery) return <Alert type="info" message="暂无查看定位卡权限" />
  if (loading) return <Skeleton active />
  if (error) return <Alert type="error" message={error} action={<Button onClick={() => setRetry(value => value + 1)}>重试</Button>} />
  const choose = () => { setSelected(options?.submissionId); setRequestKey(crypto.randomUUID()); setSelectOpen(true) }
  const apply = () => {
    if (!selected || !options || busy) return
    modal.confirm({ title: '应用此定位卡版本？', content: '账号及后续新工单将使用所选版本，已创建工单保留原快照。',
      okText: '确认应用', cancelText: '取消', mask: { closable: false }, keyboard: false,
      onOk: async () => {
        setBusy(true)
        try { await api.positioningCard.apply({ accountId, submissionId: selected, version: options.version, idempotencyKey: requestKey }); setSelectOpen(false); setRetry(v => v + 1); message.success('定位卡版本已应用') }
        catch (cause) { message.error(cause instanceof Error ? cause.message : '应用失败'); throw cause }
        finally { setBusy(false) }
      },
    })
  }
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {options?.newerAvailable && <Alert type="info" message="有新版定位卡可用，当前账号仍使用原版本" />}
    {data?.effective ? <><Tag color="success">当前应用：第 {data.effective.submissionNo} 次提交</Tag><CardSnapshot card={data.effective} /></>
      : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未应用定位卡，请选择本课程服务下的已确认版本" />}
    {options?.canApply && <Button onClick={choose}>{data?.effective ? '更换应用版本' : '选择应用定位卡'}</Button>}
    <Modal title="选择已确认定位卡版本" width="min(900px, calc(100vw - 32px))" open={selectOpen} mask={{ closable: false }} keyboard={false}
      onCancel={() => { if (!busy) setSelectOpen(false) }} onOk={apply} okText="应用所选版本" confirmLoading={busy} okButtonProps={{ disabled: !selected || selected === options?.submissionId }}>
      {options?.candidates.length ? <Radio.Group value={selected} onChange={e => { setSelected(e.target.value); setRequestKey(crypto.randomUUID()) }}>
        <Space orientation="vertical">{options.candidates.map(card => <div key={card.submissionId}><Radio value={card.submissionId}>{card.cardNo} · 第 {card.submissionNo} 次提交 · 确认于 {card.studentDecidedAt ? String(card.studentDecidedAt) : '历史未记录'}</Radio><CardSnapshot card={card} /></div>)}</Space>
      </Radio.Group> : <Empty description="暂无已确认版本，请先完成定位卡审核与学员确认" />}
    </Modal>
  </Space>
}
