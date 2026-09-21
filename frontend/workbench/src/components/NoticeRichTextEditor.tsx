import { useEffect, useRef } from 'react'
import { createEditor, createToolbar, type IDomEditor } from '@wangeditor-next/editor'
import '@wangeditor-next/editor/dist/css/style.css'
import { noticeManagement } from '../services/noticeManagement'

export default function NoticeRichTextEditor({ value = '', onChange, onUploadChange, onError }: {
  value?: string; onChange?: (html: string) => void; onUploadChange: (delta: number) => void; onError: (text: string) => void
}) {
  const toolbar = useRef<HTMLDivElement>(null)
  const body = useRef<HTMLDivElement>(null)
  const editor = useRef<IDomEditor | undefined>(undefined)
  const callbacks = useRef({ onChange, onUploadChange, onError })
  callbacks.current = { onChange, onUploadChange, onError }
  const initial = useRef(value)
  useEffect(() => {
    if (!body.current || !toolbar.current) return
    let disposed = false
    const upload = async (file: File, kind: 'image' | 'video', insert: (url: string) => void) => {
      callbacks.current.onUploadChange(1)
      try { const url = await noticeManagement.uploadContent(file, kind); if (!disposed) insert(url) }
      catch (error) { if (!disposed) callbacks.current.onError(error instanceof Error ? error.message : '正文文件上传失败') }
      finally { if (!disposed) callbacks.current.onUploadChange(-1) }
    }
    let instance: IDomEditor | undefined
    // StrictMode replays effects; do not construct an editor whose asynchronous toolbar setup would outlive its first cleanup.
    queueMicrotask(() => {
      if (disposed || !body.current || !toolbar.current) return
      instance = createEditor({ selector: body.current, html: initial.current, config: {
        autoFocus: false,
        onChange: next => callbacks.current.onChange?.(next.getHtml()),
        customAlert: text => callbacks.current.onError(text),
        MENU_CONF: {
          uploadImage: { allowedFileTypes: ['image/*'], customUpload: (file: File, insert: (url: string, alt: string, href: string) => void) => upload(file, 'image', url => insert(url, file.name, url)) },
          uploadVideo: { allowedFileTypes: ['video/*'], customUpload: (file: File, insert: (url: string, poster: string) => void) => upload(file, 'video', url => insert(url, '')) }
        }
      } })
      editor.current = instance
      createToolbar({ editor: instance, selector: toolbar.current })
    })
    return () => { disposed = true; instance?.destroy(); editor.current = undefined }
  }, [])
  useEffect(() => { if (editor.current && editor.current.getHtml() !== value) editor.current.setHtml(value) }, [value])
  return <div style={{ border: '1px solid var(--crm-border)' }}><div ref={toolbar} /><div ref={body} style={{ height: 360 }} /></div>
}
