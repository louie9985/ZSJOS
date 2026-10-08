import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Space } from 'antd'
import { createEditor, createToolbar, type IDomEditor } from '@wangeditor-next/editor'
import '@wangeditor-next/editor/dist/css/style.css'

type Props = {
  value?: string
  onChange?: (html: string) => void
  uploadFile: (file: File, kind: 'image' | 'video') => Promise<string>
  onUploadChange: (delta: number) => void
  onError: (text: string) => void
  onFailureChange?: (count: number) => void
  noteMode?: boolean
}
type FailedUpload = { id: number; file: File; retry: () => Promise<void>; error: string }

export default function ConfigurableRichTextEditor({ value = '', onChange, uploadFile, onUploadChange, onError, onFailureChange, noteMode = false }: Props) {
  const toolbar = useRef<HTMLDivElement>(null)
  const body = useRef<HTMLDivElement>(null)
  const editor = useRef<IDomEditor | undefined>(undefined)
  const callbacks = useRef({ onChange, uploadFile, onUploadChange, onError, onFailureChange })
  callbacks.current = { onChange, uploadFile, onUploadChange, onError, onFailureChange }
  const initial = useRef(value)
  const emitted = useRef(value)
  const sequence = useRef(0)
  const [failures, setFailures] = useState<FailedUpload[]>([])
  useEffect(() => { callbacks.current.onFailureChange?.(failures.length) }, [failures])
  useEffect(() => {
    if (!body.current || !toolbar.current) return
    let disposed = false
    const upload = async (file: File, kind: 'image' | 'video', insert: (url: string) => void, id = ++sequence.current) => {
      setFailures(current => current.filter(item => item.id !== id))
      callbacks.current.onUploadChange(1)
      try {
        if (noteMode && (file.size > 10 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type))) throw new Error('仅支持10MB以内的PNG、JPEG、GIF、WebP图片')
        if (noteMode && new DOMParser().parseFromString(editor.current?.getHtml() || '', 'text/html').images.length >= 20) throw new Error('每份说明最多20张图片')
        const url = await callbacks.current.uploadFile(file, kind)
        if (!disposed) insert(url)
      } catch (cause) {
        if (!disposed) {
          const error = cause instanceof Error ? cause.message : '正文文件上传失败'
          callbacks.current.onError(error)
          if (noteMode) setFailures(current => [...current, { id, file, error, retry: () => upload(file, kind, insert, id) }])
        }
      } finally { if (!disposed) callbacks.current.onUploadChange(-1) }
    }
    let instance: IDomEditor | undefined
    // StrictMode replays effects before the editor's asynchronous toolbar initialization.
    queueMicrotask(() => {
      if (disposed || !body.current || !toolbar.current) return
      instance = createEditor({ selector: body.current, html: initial.current, config: {
        autoFocus: false,
        onChange: next => { emitted.current = next.getHtml(); callbacks.current.onChange?.(emitted.current) },
        customAlert: text => callbacks.current.onError(text),
        customPaste: noteMode ? (next, event) => {
          const data = event.clipboardData
          if (!data || !Array.from(data.files).some(file => file.type.startsWith('image/'))) return true
          // Route clipboard files through the same upload/ownership pipeline as the toolbar.
          next.insertData(data)
          return false
        } : undefined,
        MENU_CONF: {
          uploadImage: { allowedFileTypes: noteMode ? ['image/png', 'image/jpeg', 'image/gif', 'image/webp'] : ['image/*'], customUpload: (file: File, insert: (url: string, alt: string, href: string) => void) => upload(file, 'image', url => insert(url, file.name, noteMode ? '' : url)) },
          uploadVideo: { allowedFileTypes: ['video/*'], customUpload: (file: File, insert: (url: string, poster: string) => void) => upload(file, 'video', url => insert(url, '')) }
        }
      } })
      editor.current = instance
      createToolbar({ editor: instance, selector: toolbar.current, ...(noteMode ? { config: { toolbarKeys: ['headerSelect', 'bold', 'italic', 'underline', 'color', 'bgColor', 'bulletedList', 'numberedList', 'insertLink', 'insertTable', 'uploadImage', 'undo', 'redo'] } } : {}) })
    })
    return () => { disposed = true; instance?.destroy(); editor.current = undefined }
  }, [noteMode])
  useEffect(() => {
    // Note drafts reset by remounting. Echoing HTML during a table edit replaces Slate nodes
    // while its selection still references them. Only the announcement adapter accepts external resets.
    if (!noteMode && editor.current && value !== emitted.current && editor.current.getHtml() !== value) editor.current.setHtml(value)
  }, [value, noteMode])
  return <div className={noteMode ? 'exam-note-editor' : undefined} style={{ border: '1px solid var(--crm-border)' }}>
    <div ref={toolbar} /><div ref={body} style={{ height: 360 }} />
    {failures.map(item => <Alert key={item.id} type="error" showIcon title={`${item.file.name}：${item.error}`} action={<Space wrap>
      <Button size="small" onClick={() => void item.retry()}>重试上传</Button>
      <Button size="small" onClick={() => setFailures(current => current.filter(value => value.id !== item.id))}>移除失败项</Button>
    </Space>} />)}
  </div>
}
