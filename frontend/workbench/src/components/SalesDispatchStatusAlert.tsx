import { Alert, Button } from 'antd'
import { resolveDispatchWarning } from '../services/salesDispatch'
import { useSalesDispatchStatus } from './SalesDispatchStatusProvider'

export default function SalesDispatchStatusAlert() {
  const { enabled, status, loading, updating, error, pageActive, refresh, setAccepting } = useSalesDispatchStatus()
  if (!enabled || (!loading && status?.eligible === false)) return null

  const warning = resolveDispatchWarning(status, loading, error, pageActive)
  if (!warning) return null

  const action = warning.kind === 'paused'
    ? <Button type="primary" size="small" loading={updating} disabled={!pageActive} onClick={() => void setAccepting(true).catch(() => undefined)}>开启接单</Button>
    : <Button size="small" onClick={() => void refresh()}>刷新状态</Button>

  return <Alert
    className="sales-dispatch-status-alert"
    type={warning.kind === 'error' ? 'error' : 'warning'}
    showIcon
    message={warning.message}
    action={action}
  />
}
