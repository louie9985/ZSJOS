import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Checkbox, Modal, Space, Spin, Tag, Typography } from 'antd'
import type { RegistrationCase } from '../services/api'
import { hasPermission } from '../services/managementAccess'
import { studentInfoApi, studentInfoError, studentInfoStatus, type StudentInfoLink } from '../services/studentInfo'
import { formatTimestamp } from '../services/time'
import StudentInfoLinkModal from './StudentInfoLinkModal'
import StudentInfoPanel from './StudentInfoPanel'

export default function RegistrationStudentInfo({ item, permissions, disabled, onChanged, onBusy }: { item: RegistrationCase; permissions: string[]; disabled: boolean; onChanged: () => void; onBusy?: (busy: boolean) => void }) {
  const [link, setLink] = useState<StudentInfoLink | null>(null)
  const [loading, setLoading] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('')
  const [mode, setMode] = useState<'generate' | 'view'>(), [detailOpen, setDetailOpen] = useState(false)
  const sequence = useRef(0)
  const canLink = hasPermission(permissions, 'zsjos:student-info-form:link-read')
  const editable = !disabled && ['pending','processing'].includes(item.status)
  const load = async () => { if (!canLink) return; const current=++sequence.current; setLoading(true); setError(''); try { const value=await studentInfoApi.link(item.id); if(current===sequence.current)setLink(value) } catch(e){if(current===sequence.current)setError(studentInfoError(e))} finally{if(current===sequence.current)setLoading(false)} }
  useEffect(()=>{setLink(null);void load();return()=>{sequence.current++}},[item.id,item.version,canLink])
  const sent = async (checked:boolean) => { if(!link)return;setSaving(true);onBusy?.(true);setError('');try{setLink(await studentInfoApi.sent(item.id,{formId:link.formId,version:item.version,sent:checked,idempotencyKey:crypto.randomUUID()}));onChanged()}catch(e){setError(studentInfoError(e))}finally{setSaving(false);onBusy?.(false)}}
  return <section aria-label="发送学员信息收集表">
    <Typography.Text strong>发送学员信息收集表</Typography.Text>
    <Typography.Paragraph type="secondary">生成链接并发送给学员后，勾选已发送即可完成此事项，无需等待学员提交。</Typography.Paragraph>
    {error && <Alert type="error" title={error} action={<Button onClick={()=>void load()}>重试</Button>} />}
    <Space wrap>
      {editable && hasPermission(permissions,'zsjos:student-info-form:create') && !link && <Button disabled={loading||saving} onClick={()=>setMode('generate')}>生成收集表链接</Button>}
      {canLink && link && <Button disabled={loading||saving} onClick={()=>setMode('view')}>收集表链接 / 二维码</Button>}
      {hasPermission(permissions,'zsjos:student-info-form:read') && <Button onClick={()=>setDetailOpen(true)}>查看学员资料</Button>}
      <Tag>{studentInfoStatus[link?.status || item.informationSubmissionStatus || 'NONE']}</Tag>
      {loading && <Spin size="small" />}
    </Space>
    <div style={{marginTop:12}}><Checkbox checked={Boolean(item.informationSent)} disabled={!editable||loading||saving||!link||!hasPermission(permissions,'zsjos:student-info-form:sent')} onChange={e=>void sent(e.target.checked)}>我已将收集表链接发送给该学员</Checkbox></div>
    {item.informationSentAt && <Typography.Text type="secondary">发送确认时间：{formatTimestamp(item.informationSentAt)}</Typography.Text>}
    {mode && <StudentInfoLinkModal caseId={item.id} version={item.version} mode={mode} onChanged={()=>{void load();onChanged()}} onClose={()=>{setMode(undefined);void load()}} />}
    <Modal open={detailOpen} title="学员报名资料" width={800} footer={null} onCancel={()=>setDetailOpen(false)} destroyOnHidden><StudentInfoPanel caseId={item.id}/></Modal>
  </section>
}
