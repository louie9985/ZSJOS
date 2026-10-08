import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Modal, Space, Spin, Tooltip, Typography, message } from 'antd'
import { EditOutlined, ExpandOutlined } from '@ant-design/icons'
import { ApiError } from '../services/api'
import { examCalendarNote, EXAM_NOTE_CONFLICT, noteDisplayHtml, noteSaveHtml, type ExamCalendarNote, type ExamNoteImage } from '../services/examCalendarNote'
import ExamNoteRichText from './ExamNoteRichText'
import '../styles/pages/exam-calendar-note.css'

const RichTextEditor = lazy(() => import('./ConfigurableRichTextEditor'))
const NOTE_TITLE = '日历说明'
export default function ExamCalendarNotePanel({ canRead, canManage }: { canRead: boolean; canManage: boolean }) {
  const [note, setNote] = useState<ExamCalendarNote>()
  const [loading, setLoading] = useState(false), [loadError, setLoadError] = useState('')
  const [viewing, setViewing] = useState(false), [editing, setEditing] = useState(false), [preview, setPreview] = useState(false)
  const [draft, setDraft] = useState(''), [version, setVersion] = useState(0), [editorKey, setEditorKey] = useState(0)
  const [dirty, setDirty] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('')
  const [uploads, setUploads] = useState(0), [failures, setFailures] = useState(0), [conflict, setConflict] = useState(false)
  const imageRegistry = useRef<ExamNoteImage[]>([])
  const requests = useRef(0)
  const [modal, contextHolder] = Modal.useModal()
  const load = useCallback(async () => {
    if (!canRead) return
    const request = ++requests.current
    setLoading(true); setLoadError('')
    try { const value = await examCalendarNote.get(); if (request === requests.current) setNote(value) }
    catch (cause) { if (request === requests.current) setLoadError(cause instanceof Error ? cause.message : '说明加载失败') }
    finally { if (request === requests.current) setLoading(false) }
  }, [canRead])
  useEffect(() => { void load(); return () => { requests.current++ } }, [load])
  const openEditor = () => {
    if (!note) return
    imageRegistry.current = [...note.images]
    setDraft(noteDisplayHtml(note.content, note.images)); setVersion(note.version)
    setDirty(false); setError(''); setConflict(false); setUploads(0); setFailures(0); setPreview(false)
    setEditorKey(key => key + 1); setEditing(true)
  }
  const closeEditor = () => {
    if (saving || uploads > 0) return
    if (dirty) modal.confirm({ title: '放弃未保存的修改？', content: '关闭后将保留上次保存的说明。', okText: '放弃修改', cancelText: '继续编辑', onOk: () => setEditing(false) })
    else setEditing(false)
  }
  const save = async () => {
    if (!canManage || uploads || failures || saving) return
    setSaving(true); setError(''); setConflict(false)
    try {
      const value = await examCalendarNote.save(noteSaveHtml(draft, imageRegistry.current), version)
      requests.current++; setLoading(false); setNote(value); setEditing(false); setDirty(false)
      message.success('说明已保存')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请重试')
      setConflict(cause instanceof ApiError && cause.code === EXAM_NOTE_CONFLICT)
    } finally { setSaving(false) }
  }
  const reloadConflict = async () => {
    try {
      const latest = await examCalendarNote.get()
      // Keep old signed URLs used by the draft alongside refreshed URLs for the same file.
      imageRegistry.current = [...new Map([...imageRegistry.current, ...latest.images].map(image => [image.url, image])).values()]
      setNote(latest); setVersion(latest.version); setViewing(true); setConflict(false)
      setError('已加载最新内容供对照，当前编辑内容已保留；请合并后再保存。')
    } catch (cause) { setError(cause instanceof Error ? cause.message : '重新加载失败') }
  }
  const upload = async (file: File) => {
    const image = await examCalendarNote.upload(file)
    imageRegistry.current.push(image); setDirty(true)
    return image.url
  }
  const display = note ? noteDisplayHtml(note.content, note.images) : ''
  return <aside className="exam-note-panel" aria-label={NOTE_TITLE}>
    {contextHolder}
    <div className="exam-note-heading"><Typography.Title level={5}>{NOTE_TITLE}</Typography.Title>
      <div className="exam-note-actions" role="group" aria-label="日历说明操作">
        {canRead && <Tooltip title="放大查看"><Button type="text" className="exam-note-expand" aria-label="放大查看"
          icon={<ExpandOutlined />} disabled={!note || !!loadError} onClick={() => setViewing(true)} /></Tooltip>}
        {canRead && canManage && <Button type="text" className="exam-note-edit" icon={<EditOutlined />}
          disabled={!note || !!loadError} onClick={openEditor}>编辑</Button>}
      </div>
    </div>
    {!canRead ? <Alert type="warning" title="无权查看考期说明" /> : loadError ? <Alert type="error" showIcon title={loadError} action={<Button onClick={() => void load()}>重试</Button>} />
      : <Spin spinning={loading}>{note && (note.content ? <ExamNoteRichText html={display} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无说明" />)}</Spin>}
    <Modal title={NOTE_TITLE} open={viewing && canRead} onCancel={() => setViewing(false)} footer={<Button onClick={() => setViewing(false)}>关闭</Button>}
      width="min(1000px, calc(100vw - 32px))" className="exam-note-modal" destroyOnHidden>
      {note?.content ? <ExamNoteRichText html={display} /> : <Empty description="暂无说明" />}
    </Modal>
    <Modal title={`编辑${NOTE_TITLE}`} open={editing && canRead && canManage} onCancel={closeEditor} mask={{ closable: false }}
      keyboard={!saving && uploads === 0} width="min(1000px, calc(100vw - 32px))" className="exam-note-modal" destroyOnHidden
      footer={<Space wrap><Button disabled={saving || uploads > 0} onClick={closeEditor}>取消</Button>
        <Button onClick={() => setPreview(value => !value)}>{preview ? '收起预览' : '预览'}</Button>
        <Button type="primary" loading={saving} disabled={uploads > 0 || failures > 0 || conflict} onClick={() => void save()}>保存</Button></Space>}>
      <Typography.Paragraph type="secondary">最多5000字、20张图片；单张图片不超过10MB。保存后所有有查看权限的同事都能看到。</Typography.Paragraph>
      {error && <Alert showIcon type={conflict ? 'warning' : 'error'} title={error} action={conflict && <Button onClick={() => void reloadConflict()}>加载最新内容对照</Button>} />}
      {uploads > 0 && <Alert type="info" title="图片上传中，请稍候再保存" />}
      <div inert={saving || undefined}><Suspense fallback={<Spin />}><RichTextEditor key={editorKey} noteMode value={draft}
        onChange={value => { setDraft(value); setDirty(true) }} uploadFile={upload} onUploadChange={delta => setUploads(count => Math.max(0, count + delta))}
        onError={setError} onFailureChange={setFailures} /></Suspense></div>
      {preview && <section aria-label="说明预览"><Typography.Title level={5}>预览</Typography.Title><ExamNoteRichText html={draft} /></section>}
    </Modal>
  </aside>
}
