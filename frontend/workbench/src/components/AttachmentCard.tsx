import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Image, Modal, Spin, Typography } from 'antd'
import { DownloadOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons'

export type AttachmentResource = { name: string; url?: string; type?: string; size?: number }
export function attachmentKind(name: string, mime = '') {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (mime.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif', 'svg', 'bmp'].includes(ext)) return 'image'
  if (mime.startsWith('video/') || ['mp4', 'mov', 'webm', 'mkv'].includes(ext)) return 'video'
  if (mime.startsWith('audio/') || ['mp3', 'wav', 'm4a', 'ogg'].includes(ext)) return 'audio'
  if (mime === 'application/pdf' || ext === 'pdf') return 'pdf'
  if (['xls', 'xlsx', 'csv', 'ods'].includes(ext)) return 'sheet'
  if (['ppt', 'pptx', 'odp'].includes(ext)) return 'slides'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'archive'
  if (['doc', 'docx', 'md', 'txt', 'rtf'].includes(ext)) return 'document'
  return 'file'
}
const captions = { image: '图片', video: '视频', audio: '音频', pdf: 'PDF', sheet: '表格', slides: '演示文稿', archive: '压缩包', document: '文档', file: '文件' }

export function AttachmentTypeIcon({ kind, extension }: { kind: ReturnType<typeof attachmentKind>; extension: string }) {
  return <svg className={`attachment-type-icon kind-${kind}`} viewBox="0 0 48 56" role="img" aria-label={`${captions[kind]}图标`}>
    <path d="M9 2h20l12 12v36a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4V6a4 4 0 0 1 4-4Z" fill="currentColor" opacity=".12" />
    <path d="M29 2v10a2 2 0 0 0 2 2h10" fill="currentColor" opacity=".35" />
    <g stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
      {kind === 'image' ? <><rect x="13" y="20" width="22" height="16" rx="2"/><circle cx="20" cy="25" r="2"/><path d="m14 34 7-6 5 4 5-7 4 5"/></>
        : kind === 'video' ? <><rect x="12" y="20" width="24" height="16" rx="3"/><path d="m22 24 7 4-7 4Z" fill="currentColor"/></>
        : kind === 'audio' ? <><path d="M22 33V21l11-3v12M22 24l11-3"/><ellipse cx="18" cy="34" rx="4" ry="3"/><ellipse cx="29" cy="31" rx="4" ry="3"/></>
        : kind === 'sheet' ? <><rect x="13" y="20" width="22" height="17" rx="1"/><path d="M13 26h22M13 31h22M21 20v17"/></>
        : kind === 'archive' ? <><path d="M24 18v12m-3-10h6m-6 4h6m-6 4h6"/><rect x="21" y="31" width="6" height="7" rx="1"/></>
        : kind === 'slides' ? <><path d="M12 20h24v14H12zm12 14v5m-5 0 5-5 5 5"/><path d="m17 29 5-5 4 3 5-4"/></>
        : <><path d="M14 22h18M14 28h18M14 34h12"/></>}
    </g><text x="24" y="47" textAnchor="middle" fontSize="7" fontWeight="700" fill="currentColor">{extension.toUpperCase().slice(0, 6) || 'FILE'}</text>
  </svg>
}

// The caller owns authorization and signed-URL retrieval; this component owns
// presentation only and never knows a business endpoint or guesses access.
export default function AttachmentCard({ name, load }: { name: string; load: () => Promise<AttachmentResource> }) {
  const [file, setFile] = useState<AttachmentResource>(), [error, setError] = useState(''), [busy, setBusy] = useState(false), [preview, setPreview] = useState(false)
  const request = useRef(0)
  const [previewRevision, setPreviewRevision] = useState(0)
  const fetchFile = async () => {
    const version = ++request.current; setBusy(true); setError('')
    try {
      const result = await load()
      if (!result.url || !['http:', 'https:'].includes(new URL(result.url).protocol)) throw new Error('附件地址不可用')
      if (version === request.current) { setFile(result); setPreviewRevision(version) }
      return version === request.current ? result : undefined
    } catch (cause) { if (version === request.current) { setFile(undefined); setError(cause instanceof Error ? cause.message : '附件读取失败') } }
    finally { if (version === request.current) setBusy(false) }
  }
  useEffect(() => { void fetchFile(); return () => { request.current++ } }, [])
  const kind = attachmentKind(name, file?.type)
  const canPreview = ['image', 'video', 'audio', 'pdf'].includes(kind)
  const openPreview = async () => { if (await fetchFile()) setPreview(true) }
  const download = async () => {
    const result = await fetchFile(); if (!result?.url) return
    const anchor = document.createElement('a'); anchor.href = result.url; anchor.download = name; anchor.target = '_blank'; anchor.rel = 'noopener noreferrer'; anchor.click()
  }
  const size = file?.size == null ? '' : file.size < 1024 ? `${file.size} B` : file.size < 1024 ** 2 ? `${(file.size / 1024).toFixed(1)} KB` : `${(file.size / 1024 ** 2).toFixed(1)} MB`
  return <article className="attachment-card">
    <div className="attachment-card-main">
      {kind === 'image' && file?.url && !error ? <button className="attachment-image-preview" aria-label={`预览图片：${name}`} title="查看大图" disabled={busy} onClick={() => void openPreview()}><img src={file.url} alt={name} onError={() => setError('图片加载失败，请重试附件')} /><span><EyeOutlined /></span></button>
        : <AttachmentTypeIcon kind={kind} extension={name.includes('.') ? name.split('.').pop()! : ''} />}
      <div className="attachment-card-copy"><Typography.Text className="attachment-card-name" title={name}>{name}</Typography.Text><div className="attachment-card-meta">{captions[kind]}{size && ` · ${size}`}{busy && <Spin size="small" />}</div></div>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void fetchFile()}>重试附件</Button>} />}
    <div className="attachment-card-actions">
      {canPreview && <Button type="text" size="small" icon={<EyeOutlined />} disabled={busy || !file} onClick={() => void openPreview()}>预览</Button>}
      <Button type="text" size="small" icon={<DownloadOutlined />} disabled={busy || !file} onClick={() => void download()}>下载</Button>
      {error && <Button type="text" size="small" icon={<ReloadOutlined />} disabled={busy} onClick={() => void fetchFile()}>重新加载</Button>}
    </div>
    <Modal title={name} open={preview} onCancel={() => setPreview(false)} footer={<><Button icon={<DownloadOutlined />} disabled={busy || !file} onClick={() => void download()}>下载原文件</Button><Button onClick={() => setPreview(false)}>关闭</Button></>} width="min(960px, 94vw)" destroyOnHidden>
      {error && <Alert type="error" message={error} action={<Button onClick={() => void fetchFile()}>重试附件</Button>} />}
      {kind === 'image' ? <Image key={previewRevision} preview={false} src={file?.url} alt={name} style={{ width: '100%', maxHeight: '65vh', objectFit: 'contain' }} onError={() => setError('图片加载失败，请重试附件')} />
        : kind === 'audio' ? <audio controls src={file?.url} style={{ width: '100%' }} onError={() => setError('音频加载失败，请重试附件')} />
        : kind === 'video' ? <video controls src={file?.url} style={{ width: '100%', maxHeight: '65vh' }} onError={() => setError('视频加载失败，请重试附件')} />
        : <iframe title={name} src={file?.url} style={{ width: '100%', height: '65vh', border: 0 }} />}
    </Modal>
  </article>
}
