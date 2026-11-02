import type { ReactNode } from 'react'
import { Card } from 'antd'
import '../styles/pages/student-archive.css'

/** Shared enrollment archive surface; each caller owns its data and write permissions. */
export default function RegistrationArchiveCard({ children, description, extra }: {
  children: ReactNode; description?: string; extra?: ReactNode
}) {
  return <Card className="student-archive-card" extra={extra} title={<div className="archive-card-title"><span>
    <small>01 · 报名资料</small><strong>报名收集资料</strong>{description&&<em>{description}</em>}
  </span></div>}>{children}</Card>
}
