import type { ReactNode } from 'react'
import { Typography } from 'antd'
import '../styles/pages/registration.css'

/** Shared public-pool detail structure; callers retain their own workflow and permissions. */
export default function RegistrationDetailLayout({ hero, information, children, actions }: {
  hero: ReactNode; information: ReactNode; children: ReactNode; actions?: ReactNode
}) {
  return <div className="registration-detail">
    <div className="registration-detail-hero">{hero}</div>
    <div className="registration-detail-columns">
      <aside className="registration-information" aria-label="相关信息" tabIndex={0}>
        <Typography.Title level={5}>相关信息</Typography.Title>{information}
      </aside>
      <div className="registration-operation-column">
        <div className="registration-operation-scroll" aria-label="办理流程" tabIndex={0}>
          <Typography.Title level={5}>办理流程</Typography.Title>{children}
        </div>
        {actions && <div className="registration-detail-actions">{actions}</div>}
      </div>
    </div>
  </div>
}
