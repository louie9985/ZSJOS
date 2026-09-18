import { App, Button } from 'antd'
import { ArrowRightOutlined, CopyOutlined, ExportOutlined, LinkOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { createContext } from 'react'

export const ResourceLinkPresentation = createContext(false)

export function resourceTarget(href: string) {
  const raw = href.trim()
  if (/[\u0000-\u0020\\]/.test(raw)) return undefined
  if (raw.startsWith('/') && !raw.startsWith('//')) return { href: raw, internal: true, label: raw, domain: '站内页面' }
  try {
    const url = new URL(raw)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return undefined
    return { href: url.href, internal: false, label: url.hostname + (url.pathname === '/' ? '' : url.pathname), domain: url.hostname }
  } catch { return undefined }
}

export default function ResourceLink({ href, title, variant = 'text' }: { href: string; title?: string; variant?: 'text' | 'resource' }) {
  const target = resourceTarget(href)
  const { message } = App.useApp()
  if (!target) return <span className="resource-link-invalid">{title && title !== href ? `${title} · ` : ''}{href}</span>
  const label = title || target.label
  const contents = <><span className="resource-link-label">{label}</span>{target.internal ? <ArrowRightOutlined /> : <ExportOutlined />}</>
  const anchor = target.internal
    ? <Link className="resource-link-anchor" to={target.href} title={href}>{contents}</Link>
    : <a className="resource-link-anchor" href={target.href} target="_blank" rel="noopener noreferrer" title={href} aria-label={`${label}（新标签页打开）`}>{contents}</a>
  if (variant === 'text') return <span className="resource-link-text">{anchor}</span>
  return <div className="resource-link-card"><span className="resource-link-icon"><LinkOutlined /></span><div className="resource-link-copy">{anchor}<small>{target.domain}</small></div>
    <Button type="text" size="small" icon={<CopyOutlined />} aria-label="复制链接" onClick={() => void navigator.clipboard.writeText(target.href).then(() => message.success('链接已复制')).catch(() => message.error('复制失败，请通过链接菜单复制地址'))} />
  </div>
}

// Only explicit HTTP(S) addresses become links. Other text and line breaks stay intact.
export function LinkedText({ text, resource = false }: { text: string; resource?: boolean }) {
  const exact = resourceTarget(text)
  if (exact && !exact.internal) return <ResourceLink href={text} variant={resource ? 'resource' : 'text'} />
  const pattern = /https?:\/\/[^\s<>"'，。；！？（）【】]+/g
  const parts = []; let start = 0
  for (const match of text.matchAll(pattern)) {
    const href = match[0].replace(/[.,;!?)}\]]+$/, '')
    parts.push(text.slice(start, match.index), <ResourceLink key={match.index} href={href} title={href} />)
    start = match.index! + href.length
  }
  parts.push(text.slice(start))
  return <span className="resource-linked-text">{parts}</span>
}
