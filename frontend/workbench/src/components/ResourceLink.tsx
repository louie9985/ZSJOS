import { App, Button } from 'antd'
import { ArrowRightOutlined, CopyOutlined, ExportOutlined, LinkOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { createContext, type MouseEvent, type KeyboardEvent } from 'react'
import '../styles/components/remark-links.css'

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

export default function ResourceLink({ href, title, variant = 'text', isolateEvents = false }: { href: string; title?: string; variant?: 'text' | 'resource'; isolateEvents?: boolean }) {
  const target = resourceTarget(href)
  const { message } = App.useApp()
  if (!target) return <span className="resource-link-invalid">{title && title !== href ? `${title} · ` : ''}{href}</span>
  const label = title || target.label
  const contents = <><span className="resource-link-label">{label}</span>{target.internal ? <ArrowRightOutlined /> : <ExportOutlined />}</>
  const linkEvents = isolateEvents ? {
    onClick: (event: MouseEvent) => event.stopPropagation(),
    onAuxClick: (event: MouseEvent) => event.stopPropagation(),
    onKeyDown: (event: KeyboardEvent) => { if (event.key === 'Enter') event.stopPropagation() },
    onKeyUp: (event: KeyboardEvent) => { if (event.key === 'Enter') event.stopPropagation() }
  } : {}
  const anchor = target.internal
    ? <Link className="resource-link-anchor" to={target.href} title={href} {...linkEvents}>{contents}</Link>
    : <a className="resource-link-anchor" href={target.href} target="_blank" rel="noopener noreferrer" title={href} aria-label={`${label}（新标签页打开）`} {...linkEvents}>{contents}</a>
  if (variant === 'text') return <span className="resource-link-text">{anchor}</span>
  return <div className="resource-link-card"><span className="resource-link-icon"><LinkOutlined /></span><div className="resource-link-copy">{anchor}<small>{target.domain}</small></div>
    <Button type="text" size="small" icon={<CopyOutlined />} aria-label="复制链接" onClick={() => void navigator.clipboard.writeText(target.href).then(() => message.success('链接已复制')).catch(() => message.error('复制失败，请通过链接菜单复制地址'))} />
  </div>
}

// Only explicit HTTP(S) addresses become links. Other text and line breaks stay intact.
export function LinkedText({ text, resource = false, mode = 'default' }: { text: string; resource?: boolean; mode?: 'default' | 'remark' }) {
  if (mode === 'remark') return <span className="resource-linked-text resource-linked-text-remark">{remarkLinkParts(text).map((part, index) => part.href
    ? <ResourceLink key={index} href={part.href} title={part.text} isolateEvents /> : part.text)}</span>
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

export const REMARK_LINK_HINT = '支持 http://、https:// 链接；链接前后请用空格或换行分隔。'

export type RemarkLinkPart = { text: string; href?: string }

/** Slices always refer to the input: parsing must never rewrite persisted remark text. */
export function remarkLinkParts(text: string): RemarkLinkPart[] {
  const parts: RemarkLinkPart[] = []
  const candidates = /https?:\/\/[^\s<>"'“”‘’，。；！？：（）【】《》、]+/gi
  let start = 0
  for (const match of text.matchAll(candidates)) {
    let href = match[0]
    while (href) {
      const closing = href.at(-1)!
      const opening = ({ ')': '(', ']': '[', '}': '{' } as Record<string, string>)[closing]
      if (opening && [...href].filter(char => char === closing).length > [...href].filter(char => char === opening).length) {
        href = href.slice(0, -1)
      } else if (!/[?#]/.test(href) && /[.,;!]$/.test(href)) {
        // Query/hash punctuation can be significant (including signed links); never guess it away.
        href = href.slice(0, -1)
      } else break
    }
    // URL() repairs missing authority slashes; prose must contain an explicit authority instead.
    if (!/^https?:\/\/[^/?#]/i.test(href)) continue
    const target = resourceTarget(href)
    if (!target || target.internal) continue
    if (match.index > start) parts.push({ text: text.slice(start, match.index) })
    parts.push({ text: href, href: target.href })
    start = match.index + href.length
  }
  if (start < text.length) parts.push({ text: text.slice(start) })
  return parts
}
