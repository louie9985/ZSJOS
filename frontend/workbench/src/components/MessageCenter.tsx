import { Badge, Button, Tooltip } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { useNotifyMessages } from './NotifyMessageProvider'

export default function MessageCenter() {
  const navigate = useNavigate()
  const { unreadCount, loading, error } = useNotifyMessages()
  const title = error ? `消息中心：${error}` : '消息中心'

  return <Tooltip title={title}>
    <Badge count={unreadCount} size="small" overflowCount={99}>
      <Button
        type="text"
        aria-label="消息中心"
        loading={loading && unreadCount === 0}
        icon={<BellOutlined/>}
        onClick={() => navigate(APP_ROUTES.ALL_MESSAGES)}
      />
    </Badge>
  </Tooltip>
}
