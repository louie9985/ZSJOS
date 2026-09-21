import AccountPositioningSummary from './AccountPositioningSummary'
import AccountPositioningHistory from './AccountPositioningHistory'
import { Alert, App, Button, Empty, Radio, Skeleton, Space, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { api, type PositioningCard } from '../services/api'
import CardSnapshot from './PositioningSnapshot'
import PositioningDialog from './PositioningDialog'
import { createPortal } from 'react-dom'
import { SwapOutlined } from '@ant-design/icons'

export { default as CardSnapshot } from './PositioningSnapshot'

export default function AccountPositioningCard({ accountId, canQuery, studentName, studentContact, serviceRelationId, canReadInterview = false, refresh = 0, onApplied, hideHistory = false, statusTarget }: { accountId: number; canQuery: boolean; studentName?: string; studentContact?: string; serviceRelationId?: number; canReadInterview?: boolean; refresh?: number; onApplied?: () => void; hideHistory?: boolean; statusTarget?: HTMLElement | null }) {
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
  }, [accountId, canQuery, retry, refresh])
  const statusArea = (content: React.ReactNode) => statusTarget ? createPortal(content, statusTarget) : content
  if (!canQuery) return statusArea(<Typography.Text type="secondary">暂无查看定位卡权限</Typography.Text>)
  if (loading) return <><Skeleton active />{statusTarget && statusArea(<Skeleton active title={false} paragraph={{ rows: 2 }} />)}</>
  if (error) return statusArea(<Alert type="error" message={error} action={<Button onClick={() => setRetry(value => value + 1)}>重试</Button>} />)
  const choose = () => { setSelected(options?.submissionId); setRequestKey(crypto.randomUUID()); setSelectOpen(true) }
  const selectedCard = options?.candidates.find(card => card.submissionId === selected)
  const apply = () => {
    if (!selected || !options || busy) return
    modal.confirm({ title: '应用此定位卡版本？', content: '账号及后续新工单将使用所选版本，已创建工单保留原快照。',
      okText: '确认应用', cancelText: '取消', mask: { closable: false }, keyboard: false,
      onOk: async () => {
        setBusy(true)
        try { await api.positioningCard.apply({ accountId, submissionId: selected, version: options.version, idempotencyKey: requestKey }); setSelectOpen(false); if (onApplied) onApplied(); else setRetry(v => v + 1); message.success('定位卡版本已应用') }
        catch (cause) { message.error(cause instanceof Error ? cause.message : '应用失败'); throw cause }
        finally { setBusy(false) }
      },
    })
  }
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {statusArea(<div className="account-positioning-status">
      <div className="account-positioning-status-version"><Typography.Text type="secondary">当前应用</Typography.Text><Tag color={data?.effective ? 'success' : 'default'}>{data?.effective ? `第 ${data.effective.submissionNo} 次提交` : '尚未应用'}</Tag></div>
      {options?.newerAvailable && <Typography.Text className="account-positioning-status-hint">有新版定位卡可用，当前账号仍使用原版本</Typography.Text>}
      {!data?.effective && <Typography.Text type="secondary">请选择本课程服务下的已确认版本</Typography.Text>}
      {options?.canApply && <Button block type="primary" icon={<SwapOutlined />} onClick={choose}>{data?.effective ? '更换应用版本' : '选择应用定位卡'}</Button>}
    </div>)}
    <AccountPositioningSummary card={data?.effective} studentName={studentName} studentContact={studentContact}
      history={hideHistory ? undefined : <AccountPositioningHistory key={accountId} relationId={serviceRelationId || data?.effective?.serviceRelationId} canReadInterview={canReadInterview} />} />
    <PositioningDialog title="选择已确认定位卡版本" open={selectOpen} mask={{ closable: false }} keyboard={false}
      onCancel={() => { if (!busy) setSelectOpen(false) }} onOk={apply} okText="应用所选版本" confirmLoading={busy} okButtonProps={{ disabled: !selected || selected === options?.submissionId }}>
      {options?.candidates.length ? <div className="positioning-version-sheet">
        <div className="positioning-version-picker">
          <Radio.Group className="positioning-version-options" value={selected} onChange={e => { setSelected(e.target.value); setRequestKey(crypto.randomUUID()) }}>
            <Space orientation="vertical" style={{ width: '100%' }}>{options.candidates.map(card =>
              <Radio key={card.submissionId} value={card.submissionId}>
                {card.cardNo} · 第 {card.submissionNo} 次提交 · 确认于 {card.studentDecidedAt ? String(card.studentDecidedAt) : '历史未记录'}
              </Radio>)}</Space>
          </Radio.Group>
          {/* The preview lives outside the radio group: antd renders that group as an inline-flex
              box, which would collapse the card snapshot and break its grid and container queries. */}
          <div className="positioning-version-preview">
            {selectedCard ? <CardSnapshot card={selectedCard} />
              : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择要预览的版本" />}
          </div>
        </div>
      </div> : <Empty description="暂无已确认版本，请先完成定位卡审核与学员确认" />}
    </PositioningDialog>
  </Space>
}
