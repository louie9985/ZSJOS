import { useMemo } from 'react'
import { APP_CONFIG } from '../constants'

type AdminEmbedPageProps = { path: string }

function buildEmbedUrl(path: string) {
  const base = APP_CONFIG.ADMIN_EMBED_BASE.endsWith('/')
    ? APP_CONFIG.ADMIN_EMBED_BASE
    : `${APP_CONFIG.ADMIN_EMBED_BASE}/`
  const relativePath = path.replace(/^\/+/, '')
  return `${base}${relativePath}?embed=workbench`
}

/**
 * Admin 页面只在同源 iframe 中打开。认证由两个应用从共享 localStorage 读取，
 * 因此这里不向 URL、iframe query 或 postMessage 写入任何 token。
 */
export default function AdminEmbedPage({ path }: AdminEmbedPageProps) {
  const src = useMemo(() => buildEmbedUrl(path), [path])
  return (
    <section className="workspace-page admin-embed-page">
      <iframe
        key={src}
        src={src}
        title="管理页面"
        className="admin-embed-frame"
        referrerPolicy="same-origin"
      />
    </section>
  )
}
