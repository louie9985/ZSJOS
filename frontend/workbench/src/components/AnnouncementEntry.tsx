import { Badge, Button, Tooltip } from 'antd'
import { NotificationOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from './AnnouncementProvider'

export function AnnouncementButton() {
  const navigate = useNavigate()
  const { unreadCount, error } = useAnnouncements()
  return <Tooltip title={error || '公告中心'}>
    <Badge count={unreadCount} size="small" overflowCount={99}>
      <Button type="text" shape="circle" icon={<NotificationOutlined/>} aria-label="公告中心" onClick={() => navigate(APP_ROUTES.ANNOUNCEMENTS)}/>
    </Badge>
  </Tooltip>
}

export function AnnouncementBar() {
  const navigate = useNavigate()
  const { latest, unreadCount } = useAnnouncements()
  if (!latest || unreadCount === 0) return null
  return <div className="announcement-bar" role="status">
    <button type="button" className="announcement-bar-main" onClick={() => navigate(`${APP_ROUTES.ANNOUNCEMENTS}?announcementId=${latest.id}`)}>
      <NotificationOutlined/>
      <strong>新公告</strong>
      <span className="announcement-bar-title">{latest.title}</span>
      <time>{formatTimestamp(latest.publishTime)}</time>
    </button>
    <Button type="link" onClick={() => navigate(APP_ROUTES.ANNOUNCEMENTS)}>查看全部</Button>
  </div>
}
