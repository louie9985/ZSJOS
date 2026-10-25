import { Modal, message } from 'antd'
import { useState } from 'react'
import StudentEntryForm from './StudentEntryForm'
import type { EntryType } from '../services/studentEntry'

export default function StudentEntryDialog({ open, type, classId, onClose, onSaved }: { open: boolean; type: EntryType; classId?: number; onClose: () => void; onSaved: () => void }) {
  const [busy, setBusy] = useState(false)
  return <Modal open={open} title={type === 'media' ? '添加编导运营学员' : '添加教学学员'} width={560} footer={null}
    destroyOnHidden mask={{ closable: false }} closable={!busy} keyboard={!busy} onCancel={onClose}>
    {open && <StudentEntryForm type={type} classId={classId} onBusy={setBusy} onSaved={r => {
      message.success(`学员 ${r.personNo} 已添加${r.canRead ? '' : '，请由已指派的负责人处理'}`); onClose(); onSaved()
    }} />}
  </Modal>
}
