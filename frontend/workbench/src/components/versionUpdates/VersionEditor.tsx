import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Form, Input, Modal, Space, Spin, Tree, Upload } from 'antd'
import { versionUpdates, versionRecipientTree, type VersionUpdate, type VersionOptions, type VersionAttachment, type VersionUpdateInput } from '../../services/versionUpdates'
import { useBusinessOverlay } from '../OverlayCoordinator'
const Editor = lazy(() => import('../ConfigurableRichTextEditor'))

export default function VersionEditor({ initial, onClose, onSaved }: { initial?: VersionUpdate; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm<VersionUpdateInput>()
  const { modal } = App.useApp()
  const [options, setOptions] = useState<VersionOptions>()
  const [optionsError, setOptionsError] = useState('')
  const [files, setFiles] = useState<VersionAttachment[]>(initial?.attachments || [])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [uploads, setUploads] = useState(0)
  const [failures, setFailures] = useState(0)
  const [search, setSearch] = useState('')
  const [dirty, setDirty] = useState(false)
  const inlineFiles = useRef<VersionAttachment[]>([])
  const saving = useRef(false)
  const uploadsRef = useRef(0)
  const ids = Form.useWatch('targetUserIds', form) || []
  useBusinessOverlay(true)
  const loadOptions = async () => {
    setOptionsError('')
    try { setOptions(await versionUpdates.options()) }
    catch (cause) { setOptionsError(cause instanceof Error ? cause.message : '人员加载失败') }
  }
  useEffect(() => { void loadOptions() }, [])
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty || uploads > 0) { event.preventDefault(); event.returnValue = '' } }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, uploads])
  const changeUploads = (delta: number) => { uploadsRef.current += delta; setUploads(uploadsRef.current) }
  const close = async () => {
    if (saving.current || uploadsRef.current) return
    if (!dirty || await modal.confirm({ title: '放弃未保存的版本更新？', okText: '放弃修改', cancelText: '继续编辑' })) onClose()
  }
  const save = async () => {
    if (saving.current || uploadsRef.current || failures || !options) return
    let values: VersionUpdateInput
    try { values = await form.validateFields() } catch { return }
    saving.current = true; setBusy(true); setError('')
    try {
      const data = { ...values, attachmentIds: files.map(file => file.id), inlineFiles: inlineFiles.current }
      if (initial) await versionUpdates.update(initial.id, data)
      else await versionUpdates.create(data)
      onSaved(); onClose()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '保存失败') }
    finally { saving.current = false; setBusy(false) }
  }
  return <Modal open title={initial ? '编辑版本更新' : '新建版本更新'} width="min(1100px, calc(100vw - 24px))" mask={{ closable: false }} onCancel={() => void close()} keyboard={!busy && !uploads}
    footer={<Space><Button disabled={busy || uploads > 0} onClick={() => void close()}>取消</Button><Button type="primary" loading={busy} disabled={uploads > 0 || failures > 0 || !options} onClick={() => void save()}>保存草稿</Button></Space>}>
    {error && <Alert type="error" showIcon title={error} />}
    {optionsError && <Alert type="error" showIcon title={optionsError} action={<Button onClick={() => void loadOptions()}>重试</Button>} />}
    <Form form={form} layout="vertical" disabled={busy} onValuesChange={() => setDirty(true)} initialValues={{ title: initial?.title || '', content: initial?.content || '', targetUserIds: initial?.targetUserIds || [] }}>
      <Form.Item name="title" label="更新标题" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} placeholder="例如 V1.2.0 版本更新" /></Form.Item>
      <Form.Item name="targetUserIds" hidden rules={[{ required: true, type: 'array', min: 1, message: '请选择接收人员' }]}><Input /></Form.Item>
      <Form.Item label={`接收范围 · 已选 ${ids.length} 人`}><Input.Search allowClear placeholder="搜索部门或人员" value={search} onChange={event => setSearch(event.target.value)} />
        <Spin spinning={!options && !optionsError}><div className="version-update-tree"><Tree key={options ? 'ready' : 'loading'} defaultExpandAll checkable treeData={options ? versionRecipientTree(options) : []} checkedKeys={ids.map((id: number) => `u:${id}`)}
          filterTreeNode={node => !!search && String(node.title).includes(search)} onCheck={keys => { const checked = Array.isArray(keys) ? keys : keys.checked; form.setFieldValue('targetUserIds', checked.filter(key => String(key).startsWith('u:')).map(key => Number(String(key).slice(2)))); setDirty(true) }} /></div></Spin>
      </Form.Item>
      <div inert={busy ? true : undefined}><Suspense fallback={<Spin />}><Form.Item name="content" label="更新内容" rules={[{ required: true, message: '请填写更新内容' }]}><Editor noteMode onUploadChange={changeUploads} onFailureChange={setFailures} onError={setError} uploadFile={async file => {
        const result = await versionUpdates.upload(file)
        inlineFiles.current.push(result)
        if (!result.url) throw new Error('图片地址不可用')
        return result.url
      }} /></Form.Item></Suspense></div>
      <Form.Item label="附件"><Upload showUploadList={false} disabled={busy || files.length >= 10 || uploads > 0} beforeUpload={file => {
        if (files.length >= 10 || uploadsRef.current || saving.current) return false
        changeUploads(1); setError('')
        void versionUpdates.upload(file).then(result => { setFiles(current => [...current, result]); setDirty(true) }).catch(cause => setError(cause instanceof Error ? cause.message : '上传失败')).finally(() => changeUploads(-1))
        return false
      }}><Button loading={uploads > 0}>上传附件</Button></Upload>
        {files.map(file => <div key={file.id}>{file.name}<Button type="link" disabled={busy} onClick={() => { setFiles(current => current.filter(item => item.id !== file.id)); setDirty(true) }}>移除</Button></div>)}
      </Form.Item>
    </Form>
  </Modal>
}
