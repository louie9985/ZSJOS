import { Space } from 'antd'
import { APP_CONFIG } from '../constants'
import ResourceLink, { resourceTarget } from './ResourceLink'

export function pmsShareTargets(params?: Record<string, unknown>) {
  const publicUrl = typeof params?.publicUrl === 'string' ? params.publicUrl : ''
  const documentRoute = typeof params?.documentRoute === 'string' ? params.documentRoute : ''
  if (!/^\/pms\/kb\/library\/\d+\/document\/\d+$/.test(documentRoute)) return undefined
  const publicTarget = resourceTarget(publicUrl)
  if (!publicTarget || publicTarget.internal) return undefined
  return { publicUrl: publicTarget.href, documentRoute }
}

export default function PmsShareMessageLinks({ params }: { params?: Record<string, unknown> }) {
  const targets = pmsShareTargets(params)
  if (!targets) return null
  // PMS detail routes belong to Admin; Workbench only owns its configured menu entries.
  const base = APP_CONFIG.ADMIN_EMBED_BASE.replace(/\/?$/, '/')
  const documentUrl = new URL(base + targets.documentRoute.slice(1), window.location.origin).href
  return <Space orientation="vertical">
    <ResourceLink href={targets.publicUrl} title="公开查看" variant="resource" />
    <ResourceLink href={documentUrl} title="在知识库中查看" variant="resource" />
  </Space>
}
