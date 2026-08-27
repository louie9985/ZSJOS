import { useMemo } from 'react'

const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:'])

export function sanitizeRichText(html: string) {
  const document = new DOMParser().parseFromString(html, 'text/html')
  document.querySelectorAll('script,iframe,object,embed,form').forEach(node => node.remove())
  document.querySelectorAll<HTMLElement>('*').forEach(node => {
    for (const attribute of Array.from(node.attributes)) {
      if (attribute.name.toLowerCase().startsWith('on')) node.removeAttribute(attribute.name)
    }
  })
  document.querySelectorAll<HTMLAnchorElement>('a[href]').forEach(link => {
    try {
      const url = new URL(link.href, window.location.origin)
      if (!SAFE_PROTOCOLS.has(url.protocol)) link.removeAttribute('href')
      else { link.target = '_blank'; link.rel = 'noopener noreferrer' }
    } catch { link.removeAttribute('href') }
  })
  document.querySelectorAll<HTMLImageElement>('img[src]').forEach(image => {
    try {
      const url = new URL(image.src, window.location.origin)
      if (!SAFE_PROTOCOLS.has(url.protocol) && url.protocol !== 'data:') image.removeAttribute('src')
      image.loading = 'lazy'
    } catch { image.removeAttribute('src') }
  })
  return document.body.innerHTML
}

export default function SafeRichText({ html }: { html: string }) {
  const safeHtml = useMemo(() => sanitizeRichText(html), [html])
  return <div className="announcement-rich-text" dangerouslySetInnerHTML={{ __html: safeHtml }}/>
}
