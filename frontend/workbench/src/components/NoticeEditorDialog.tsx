import { Alert, App, Button, DatePicker, Empty, Form, Input, Modal, Progress, Radio, Select, Space, Spin, Tree, Typography, Upload } from 'antd'
import dayjs from 'dayjs'
import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import type { AnnouncementAttachment, DictData } from '../services/api'
import { noticeManagement, noticePermission, noticeRecipientTree, type ManagedNotice, type NoticeInput, type NoticeRecipients } from '../services/noticeManagement'
import NoticeManagementDetail from './NoticeManagementDetail'

const RichTextEditor = lazy(() => import('./NoticeRichTextEditor'))
type Values = Omit<NoticeInput, 'id' | 'attachments' | 'highlightUntil'> & { highlightUntil?: dayjs.Dayjs | null }
type UploadTask = { uid: string; name: string; progress: number; error?: string }

export default function NoticeEditorDialog({ initial, permissions, onClose, onChanged }: {
  initial?: ManagedNotice; permissions: string[]; onClose: () => void; onChanged: () => void
}) {
  const { message, modal } = App.useApp()
  const [form] = Form.useForm<Values>()
  const [id, setId] = useState(initial?.id)
  const [attachments, setAttachments] = useState<AnnouncementAttachment[]>(initial?.attachments || [])
  const [types, setTypes] = useState<DictData[]>([])
  const [recipients, setRecipients] = useState<NoticeRecipients>()
  const [optionsLoading, setOptionsLoading] = useState(true)
  const [optionsError, setOptionsError] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [dirty, setDirty] = useState(false)
  const [stateChanged, setStateChanged] = useState(false)
  const [uploads, setUploads] = useState<UploadTask[]>([])
  const pendingUploads = useRef(0)
  const attachmentCount = useRef(attachments.length)
  attachmentCount.current = attachments.length
  const [contentUploads, setContentUploads] = useState(0)
  const [preview, setPreview] = useState<ManagedNotice>()
  const [filter, setFilter] = useState('')
  const closePrompt = useRef(false)
  const audienceType = Form.useWatch('audienceType', form)
  const deptIds = Form.useWatch('targetDeptIds', form) || []
  const userIds = Form.useWatch('targetUserIds', form) || []
  const canSave = !stateChanged && noticePermission(permissions, id ? 'update' : 'create')
  const uploading = uploads.some(task => !task.error) || contentUploads > 0
  const blocked = busy || uploading
  const loadOptions = useCallback(async () => {
    setOptionsLoading(true); setOptionsError('')
    try {
      const [nextTypes, nextRecipients] = await Promise.all([noticeManagement.types(), noticeManagement.recipients()])
      setTypes(nextTypes); setRecipients(nextRecipients)
    } catch (cause) { setOptionsError(cause instanceof Error ? cause.message : '表单选项加载失败') }
    finally { setOptionsLoading(false) }
  }, [])
  useEffect(() => { void loadOptions() }, [loadOptions])
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty || blocked) { event.preventDefault(); event.returnValue = '' } }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, blocked])
  const close = () => {
    if (blocked) { void message.info('请等待保存或上传完成'); return }
    if (dirty) {
      if (closePrompt.current) return
      closePrompt.current = true
      modal.confirm({ title: '放弃未保存的公告修改？', content: '离开后未保存的内容将丢失。', okText: '放弃修改', cancelText: '继续编辑', onOk: onClose, afterClose: () => { closePrompt.current = false } })
    }
    else onClose()
  }
  const closeRef = useRef(close)
  closeRef.current = close
  useEffect(() => {
    let disposed = false
    const marker = `notice-editor-${Date.now()}-${Math.random()}`
    const baseState = window.history.state
    const baseUrl = window.location.href
    const restore = () => window.history.pushState({ ...baseState, idx: (baseState?.idx ?? 0) + 1, noticeEditor: marker }, '', baseUrl)
    const back = (event: PopStateEvent) => {
      event.stopImmediatePropagation()
      restore()
      closeRef.current()
    }
    // BrowserRouter has no route blocker. A same-URL dialog entry makes Back close the editor through its unsaved/upload guard.
    queueMicrotask(() => {
      if (disposed) return
      restore()
      window.addEventListener('popstate', back, true)
    })
    return () => {
      disposed = true
      window.removeEventListener('popstate', back, true)
      if (window.history.state?.noticeEditor === marker) window.history.back()
    }
  }, [])
  const payload = (values: Values): NoticeInput => ({
    ...values, id, highlightUntil: values.highlightUntil?.valueOf() ?? null,
    targetDeptIds: values.audienceType === 'TARGET' ? values.targetDeptIds || [] : [],
    targetUserIds: values.audienceType === 'TARGET' ? values.targetUserIds || [] : [],
    attachments: attachments.map((file, sort) => ({ ...file, sort }))
  })
  const save = async (publish: boolean) => {
    if (busyRef.current || uploading || pendingUploads.current > 0 || uploads.length > 0 || !canSave || optionsError || optionsLoading) return
    if (publish && !noticePermission(permissions, 'publish')) return
    let values: Values
    try { values = await form.validateFields() } catch { return }
    if (values.audienceType === 'TARGET' && !values.targetDeptIds?.length && !values.targetUserIds?.length) { setError('请选择接收部门或用户'); return }
    busyRef.current = true; setBusy(true); setError('')
    try {
      if (publish && !await modal.confirm({ title: '确认发布公告？', content: '发布后内容不可直接修改。', okText: '发布', cancelText: '取消' })) return
      const data = payload(values)
      const savedId = id || await noticeManagement.create(data)
      if (id) await noticeManagement.update(data)
      setId(savedId); setDirty(false); onChanged()
      if (publish) { await noticeManagement.publish(savedId); onChanged(); void message.success('公告已发布'); onClose() }
      else void message.success('草稿已保存')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '公告保存失败'); onChanged()
      if (id) {
        try { const current = await noticeManagement.get(id); if (current.publishStatus !== 'DRAFT') setStateChanged(true) }
        catch { /* Preserve the original command error when a follow-up read is unavailable. */ }
      }
    }
    finally { busyRef.current = false; setBusy(false) }
  }
  const upload = async (file: File & { uid: string }) => {
    const extensions = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'zip']
    if (!extensions.includes(file.name.split('.').pop()?.toLowerCase() || '')) { void message.error('不支持该文件格式'); return }
    if (attachmentCount.current + pendingUploads.current >= 10) { void message.error('公告附件不能超过 10 个'); return }
    pendingUploads.current += 1
    setUploads(current => [...current, { uid: file.uid, name: file.name, progress: 0 }])
    try {
      const attached = await noticeManagement.upload(file, progress => setUploads(current => current.map(task => task.uid === file.uid ? { ...task, progress } : task)))
      attachmentCount.current += 1
      setAttachments(current => [...current, attached]); setDirty(true)
      setUploads(current => current.filter(task => task.uid !== file.uid))
    } catch (cause) { setUploads(current => current.map(task => task.uid === file.uid ? { ...task, error: cause instanceof Error ? cause.message : '上传失败，请重新选择文件' } : task)) }
    finally { pendingUploads.current -= 1 }
  }
  return <>
    <Modal open title={id ? '编辑公告草稿' : '新建公告'} width={1000} onCancel={close} mask={{ closable: false }} keyboard={!blocked}
      footer={<Space wrap><Button onClick={close} disabled={blocked}>关闭</Button><Button onClick={() => setPreview({ ...payload(form.getFieldsValue(true)), id: id || 0, publishStatus: 'DRAFT', highlightUntil: form.getFieldValue('highlightUntil')?.valueOf() })}>预览</Button>
        {canSave && <Button loading={busy} disabled={uploading || uploads.length > 0 || !!optionsError || optionsLoading} onClick={() => void save(false)}>保存草稿</Button>}
        {canSave && noticePermission(permissions, 'publish') && <Button type="primary" loading={busy} disabled={uploading || uploads.length > 0 || !!optionsError || optionsLoading} onClick={() => void save(true)}>保存并发布</Button>}
      </Space>}>
      {error && <Alert type="error" showIcon title={error} />}
      {optionsError && <Alert type="error" showIcon title={optionsError} action={<Button onClick={() => void loadOptions()}>重试</Button>} />}
      {!canSave && <Alert type="info" title={stateChanged ? '公告状态已变化，不能继续编辑。请关闭后查看最新详情。' : '草稿已保存。继续编辑需要公告修改权限；发布可从管理列表操作。'} />}
      <Spin spinning={optionsLoading}>
        <Form form={form} layout="vertical" disabled={busy || !canSave} onValuesChange={() => setDirty(true)} initialValues={{
          title: initial?.title || '', type: initial?.type, content: initial?.content || '', audienceType: initial?.audienceType || 'ALL',
          targetDeptIds: initial?.targetDeptIds || [], targetUserIds: initial?.targetUserIds || [], highlightUntil: initial?.highlightUntil ? dayjs(initial.highlightUntil) : null
        }}>
          <Form.Item name="title" label="公告标题" rules={[{ required: true, whitespace: true, message: '请输入公告标题' }, { max: 50 }]}><Input maxLength={50} showCount /></Form.Item>
          <Form.Item name="type" label="公告类型" rules={[{ required: true, message: '请选择公告类型' }]}><Select options={types.map(type => ({ value: Number(type.value), label: type.label }))} notFoundContent={<Empty description="暂无公告类型，请联系管理员配置字典" />} /></Form.Item>
          <Form.Item name="audienceType" label="发送范围"><Radio.Group options={[{ value: 'ALL', label: '全员' }, { value: 'TARGET', label: '指定部门/用户' }]} /></Form.Item>
          <Form.Item name="targetDeptIds" hidden><Input /></Form.Item><Form.Item name="targetUserIds" hidden><Input /></Form.Item>
          {audienceType === 'TARGET' && <Form.Item label="指定部门/用户" help="勾选部门将发送给该部门及全部子部门成员；部门与用户独立勾选，发布时合并去重。">
            <Input.Search value={filter} onChange={event => setFilter(event.target.value)} placeholder="搜索部门或用户（匹配项高亮）" allowClear />
            {recipients && !recipients.departments.length && !recipients.users.length && <Empty description="暂无可选部门或用户" />}
            {recipients && <Tree checkable checkStrictly disabled={busy || !canSave} defaultExpandAll treeData={noticeRecipientTree(recipients)}
              checkedKeys={[...deptIds.map((id: number) => `d:${id}`), ...userIds.map((id: number) => `u:${id}`)]}
              filterTreeNode={node => !!filter && String(node.title).toLowerCase().includes(filter.toLowerCase())}
              onCheck={keys => { const checked = Array.isArray(keys) ? keys : keys.checked; form.setFieldsValue({ targetDeptIds: checked.filter(key => String(key).startsWith('d:')).map(key => Number(String(key).slice(2))), targetUserIds: checked.filter(key => String(key).startsWith('u:')).map(key => Number(String(key).slice(2))) }); setDirty(true) }} />}
          </Form.Item>}
          <Form.Item name="highlightUntil" label="高亮提醒截止时间"><DatePicker showTime style={{ width: '100%' }} placeholder="不设置则不高亮" /></Form.Item>
          <div inert={busy || !canSave ? true : undefined}><Suspense fallback={<Spin />}><Form.Item name="content" label="正文" rules={[{ required: true, message: '请输入公告正文' }]}><RichTextEditor onUploadChange={delta => setContentUploads(current => current + delta)} onError={setError} /></Form.Item></Suspense></div>
          <Form.Item label="附件">
            <Upload multiple showUploadList={false} disabled={busy || !canSave} beforeUpload={file => { void upload(file); return false }}><Button disabled={busy || !canSave}>上传附件（最多 10 个）</Button></Upload>
            <Typography.Text type="secondary">支持图片、Office、PDF 和 ZIP；上传完成后可保存。</Typography.Text>
            {uploads.map(task => <div key={task.uid}>{task.name}{task.error ? <Alert type="error" title={task.error} action={<Button onClick={() => setUploads(current => current.filter(item => item.uid !== task.uid))}>移除失败项</Button>} /> : <Progress percent={task.progress} />}</div>)}
            {attachments.map(file => <div key={file.infraFileId}><Space wrap><span>{file.fileName}</span><Button type="link" disabled={busy || !canSave} onClick={() => { setAttachments(current => current.filter(item => item.infraFileId !== file.infraFileId)); setDirty(true) }}>移除</Button></Space></div>)}
          </Form.Item>
        </Form>
      </Spin>
    </Modal>
    <Modal open={!!preview} title="公告预览" width={800} footer={null} onCancel={() => setPreview(undefined)}>{preview && <NoticeManagementDetail notice={preview} />}</Modal>
  </>
}
