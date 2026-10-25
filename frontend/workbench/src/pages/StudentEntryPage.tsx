import { Alert, Segmented, Typography, message } from 'antd'
import { useState } from 'react'
import StudentEntryForm from '../components/StudentEntryForm'
import { STUDENT_CREATE, type EntryType } from '../services/studentEntry'

export default function StudentEntryPage({ permissions = [] }: { permissions?: string[] }) {
  const [type, setType] = useState<EntryType>('media'), [revision, setRevision] = useState(0), [busy, setBusy] = useState(false)
  if (!permissions.includes('*:*:*') && !permissions.includes(STUDENT_CREATE)) return <Alert type="warning" message="暂无添加学员权限" showIcon />
  return <section className="workspace-page"><Typography.Title level={4}>学员录入</Typography.Title>
    <Segmented disabled={busy} value={type} onChange={v => setType(v as EntryType)} options={[{ value: 'media', label: '编导运营学员' }, { value: 'teaching', label: '教学学员' }]} />
    <div style={{ maxWidth: 560, marginTop: 20 }}><StudentEntryForm key={`${type}:${revision}`} type={type} onBusy={setBusy} onSaved={r => { message.success(`学员 ${r.personNo} 已添加`); setRevision(x => x + 1) }} /></div>
  </section>
}
