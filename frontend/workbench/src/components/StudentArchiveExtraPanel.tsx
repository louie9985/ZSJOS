import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Collapse, Form, Input, Modal, Space, Spin, Switch, Tooltip, Upload } from 'antd'
import { EditOutlined, SettingOutlined } from '@ant-design/icons'
import { studentArchiveExtraApi as api, type ArchiveExtra, type ArchiveExtraValue, type ArchiveExtraField } from '../services/studentArchiveExtra'
import type { StudentInfoImage } from '../services/studentInfo'
import ResourceLink from './ResourceLink'
import AttachmentCard from './AttachmentCard'
import { formatTimestamp } from '../services/time'
import '../styles/pages/student-archive.css'

const empty = (): ArchiveExtraValue => ({ text: '', links: [], attachmentIds: [] })

export default function StudentArchiveExtraPanel({ relationId }: { relationId: number }) {
  const [data, setData] = useState<ArchiveExtra>(), [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const [editingField, setEditingField] = useState<ArchiveExtraField>(), [changes, setChanges] = useState<Record<string, ArchiveExtraValue>>({})
  const [reason, setReason] = useState(''), [attachments, setAttachments] = useState<StudentInfoImage[]>([])
  const [settingsOpen, setSettingsOpen] = useState(false), [visibility, setVisibility] = useState<Record<string, boolean>>({})
  const sequence = useRef(0), key = useRef('')
  const message = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
  const load = async () => {
    const current = ++sequence.current; setBusy(true); setError('')
    try { const next = await api.detail(relationId); if (current === sequence.current) { setData(next); setAttachments(next.attachments); setVisibility(next.educationVisibility) } }
    catch (e) { if (current === sequence.current) { setData(undefined); setError(message(e)) } }
    finally { if (current === sequence.current) setBusy(false) }
  }
  useEffect(() => { void load(); return () => { sequence.current++ } }, [relationId])
  const value = (field: string) => changes[field] || data?.values[field] || empty()
  const change = (field: string, patch: Partial<ArchiveExtraValue>) => { setChanges(old => ({ ...old, [field]: { ...(old[field] || data?.values[field] || empty()), ...patch } })); key.current = crypto.randomUUID() }
  const openEdit = (field: ArchiveExtraField) => { setChanges({}); setReason(''); key.current = crypto.randomUUID(); setEditingField(field) }
  const upload = async (f: ArchiveExtraField, file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase() || ''
    if (!f.allowedExtensions.includes(ext) || file.size > 10 * 1024 * 1024) { setError('请选择符合字段格式要求且不超过 10MB 的附件'); return false }
    setBusy(true); setError(''); const generation = sequence.current
    try { const item = await api.upload(relationId, f.key, file); if (generation === sequence.current) { setAttachments(old => [...old, item]); change(f.key, { attachmentIds: [...value(f.key).attachmentIds, item.id] }) } }
    catch (e) { if (generation === sequence.current) setError(message(e)) } finally { if (generation === sequence.current) setBusy(false) }
    return false
  }
  const save = async () => {
    if (!data || !editingField) return
    setBusy(true); setError('')
    try { await api.save(relationId, data.revision, Object.fromEntries(Object.entries(changes).map(([field, v]) => [field, { ...v, links: v.links.map(s => s.trim()).filter(Boolean) }])), reason, key.current); setEditingField(undefined); await load() }
    catch (e) { setError(message(e)) } finally { setBusy(false) }
  }
  const saveVisibility = async () => {
    if (!data) return
    setBusy(true); setError('')
    try { await api.saveVisibility(relationId, data.visibilityRevision, visibility); setSettingsOpen(false); await load() }
    catch (e) { setError(message(e)) } finally { setBusy(false) }
  }
  const renderValue = (field: ArchiveExtraField) => {
    const current = data?.values[field.key] || empty()
    return <div className="archive-field-content"><span className={!current.text ? 'archive-empty' : undefined}>{current.text || '—'}</span>{current.links.map((link, i) => <ResourceLink key={`${link}-${i}`} href={link} title={`查看${field.label}`} variant="resource" />)}{current.attachmentIds.map(id => { const item = attachments.find(a => a.id === id); return item ? <AttachmentCard key={id} name={item.name} load={() => api.resource(relationId, item)} /> : null })}{!data?.canReadAttachments && !!current.attachmentIds.length && <span className="archive-sensitive-hint">附件需敏感信息查看权限</span>}</div>
  }
  return <section className="student-archive-section" aria-label="考试与证书信息"><Card className="student-archive-card" title={<div className="archive-card-title"><span><small>02 · 考试结果</small><strong>考试与证书信息</strong><em>按考试流程展示，字段逐项维护</em></span><Space wrap><Button size="small" disabled={busy} onClick={() => void load()}>刷新</Button>{data?.canConfigureVisibility && <Button size="small" icon={<SettingOutlined />} disabled={busy} onClick={() => setSettingsOpen(true)}>字段可见性</Button>}</Space></div>}>
    {error && <Alert type="error" showIcon title={error} action={!editingField && <Button disabled={busy} onClick={() => void load()}>重试</Button>} />}
    {busy && !data ? <Spin /> : data && <>{!data.fields.some(f => data.values[f.key]) && <div className="archive-empty-state">暂无考试与证书信息，可独立于报名资料维护</div>}<div className="archive-field-grid">{data.fields.map(field => <div className="archive-field-row" key={field.key}><span className="archive-field-label">{field.label}</span>{renderValue(field)}{data.canMaintain && <Tooltip title={`修改${field.label}`}><Button className="archive-field-edit" type="text" size="small" icon={<EditOutlined />} aria-label={`修改${field.label}`} onClick={() => openEdit(field)} /></Tooltip>}</div>)}</div><Collapse className="archive-history" items={[{ key: 'history', label: '查看修改历史', children: data.history.length ? data.history.map(h => <p key={`${h.revision}-${h.modifiedAt}`}><strong>第 {h.revision} 次修改</strong> · {formatTimestamp(h.modifiedAt)} · {h.operatorName} · {h.reason}</p>) : '暂无修改记录' }]} /></>}
  </Card>
  <Modal title={`修改${editingField?.label || '考试信息'}`} okText="保存此字段" cancelText="取消" open={!!editingField} width={720} onCancel={() => { if (!busy) setEditingField(undefined) }} onOk={() => void save()} confirmLoading={busy} okButtonProps={{ disabled: !reason.trim() || !Object.keys(changes).length }} destroyOnHidden>{error && <Alert type="error" showIcon title={error} />}{editingField && <Form layout="vertical"><Form.Item label={editingField.label}><Input.TextArea rows={editingField.type === 'textarea' ? 3 : 2} value={value(editingField.key).text} maxLength={2000} disabled={busy} onChange={e => change(editingField.key, { text: e.target.value })} placeholder={editingField.type === 'number' ? '填写数字，最多两位小数' : '填写文本'} />{editingField.type === 'composite' && <Space orientation="vertical" style={{ width: '100%', marginTop: 10 }}><Input.TextArea value={value(editingField.key).links.join('\n')} rows={2} disabled={busy} placeholder="链接（每行一个）" onChange={e => change(editingField.key, { links: e.target.value.split('\n') })} />{value(editingField.key).attachmentIds.map(id => <Space key={id}><span>{attachments.find(a => a.id === id)?.name || '已上传附件'}</span><Button disabled={busy || !data?.canReadAttachments} onClick={() => change(editingField.key, { attachmentIds: value(editingField.key).attachmentIds.filter(n => n !== id) })}>移除</Button></Space>)}<Upload accept={editingField.allowedExtensions.map(e => '.' + e).join(',')} showUploadList={false} disabled={busy || !data?.canReadAttachments || value(editingField.key).attachmentIds.length >= 9} beforeUpload={file => upload(editingField, file)}><Button disabled={busy || !data?.canReadAttachments || value(editingField.key).attachmentIds.length >= 9}>上传附件</Button></Upload><small>支持 {editingField.allowedExtensions.join('/').toUpperCase()}，单个不超过 10MB</small></Space>}</Form.Item><Form.Item label="维护原因" required><Input.TextArea value={reason} maxLength={500} disabled={busy} onChange={e => { setReason(e.target.value); key.current = crypto.randomUUID() }} /></Form.Item></Form>}</Modal>
  <Modal title="考试字段可见性" okText="保存设置" cancelText="取消" open={settingsOpen} width={620} onCancel={() => { if (!busy) setSettingsOpen(false) }} onOk={() => void saveVisibility()} confirmLoading={busy} destroyOnHidden><p className="archive-settings-note">关闭某个字段后，教务人员看不到该字段；考务主管、考务专员和管理员仍按服务端考试查询权限查看。</p><div className="archive-visibility-list">{data?.fields.map(field => <div key={field.key}><span>{field.label}</span><Switch checked={visibility[field.key] !== false} disabled={busy} checkedChildren="教务可见" unCheckedChildren="仅考务可见" onChange={checked => setVisibility(old => ({ ...old, [field.key]: checked }))} /></div>)}</div></Modal>
  </section>
}
