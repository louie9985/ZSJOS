import ResourceLink, { resourceTarget } from './ResourceLink'

export function pmsShareTargets(params?: Record<string, unknown>) {
  const publicUrl = typeof params?.publicUrl === 'string' ? params.publicUrl : ''
  const publicTarget = resourceTarget(publicUrl)
  if (!publicTarget || publicTarget.internal) return undefined
  return { publicUrl: publicTarget.href }
}

export default function PmsShareMessageLinks({ params }: { params?: Record<string, unknown> }) {
  const targets = pmsShareTargets(params)
  if (!targets) return null
  return <ResourceLink href={targets.publicUrl} title="公开查看" variant="resource" />
}
