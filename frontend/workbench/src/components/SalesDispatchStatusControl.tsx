import { App, Button, Space, Tag, Tooltip } from 'antd'
import { useMemo } from 'react'
import { dispatchActionLabel, dispatchModeLabel } from '../services/salesDispatch'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'
import { useSalesDispatchStatus } from './SalesDispatchStatusProvider'

export default function SalesDispatchStatusControl() {
  const { message } = App.useApp()
  const { enabled, status, loading, updating, error, pageActive, refresh, setAccepting } = useSalesDispatchStatus()
  const eligible = status?.eligible !== false
  const title = useMemo(() => error || (pageActive ? '页面心跳正常' : '页面离线期间不会收到自动派单'), [error, pageActive])

  if (!enabled || (!loading && !eligible)) return null

  const toggleMode = async () => {
    if (!status || !pageActive) return
    try {
      await setAccepting(status.mode !== 'accepting')
    } catch (updateError) {
      const text = updateError instanceof Error ? updateError.message : '接单状态更新失败'
      message.error(text)
    }
  }

  return <Tooltip title={title}>
    <Space size={8} className="sales-dispatch-control" aria-label="销售接单状态">
      <Tag color={status?.mode === 'accepting' ? 'success' : 'error'} className="dispatch-status-tag">
        {loading ? '状态加载' : dispatchModeLabel(status)}
      </Tag>
      <Tag color={pageActive ? 'processing' : 'error'} className="dispatch-status-tag">
        {pageActive ? '页面活跃' : '页面离线'}
      </Tag>
      <IrreversiblePopconfirm action="暂停接单" open={status?.mode === 'accepting' ? undefined : false} onConfirm={toggleMode} disabled={status?.mode !== 'accepting'}><Button className="dispatch-mode-button" disabled={!pageActive || loading} loading={updating}
        onClick={() => error && !status ? void refresh() : status?.mode === 'accepting' ? undefined : void toggleMode()}>
        {error && !status ? '重试状态' : dispatchActionLabel(status)}
      </Button></IrreversiblePopconfirm>
    </Space>
  </Tooltip>
}
