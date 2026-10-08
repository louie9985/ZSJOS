import { Space, message } from 'antd'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import AttachmentCard from './AttachmentCard'
import '../styles/pages/exam-schedule-attachments.css'
import { EXAM_ATTACHMENT_ACCEPT, examAttachmentError, examScheduleAttachments, type ExamAttachment, type ExamAttachmentItem } from '../services/examScheduleAttachments'

export function ExamAttachmentPicker({ value, onChange, disabled }: {
  value: ExamAttachmentItem[]; onChange: (items: ExamAttachmentItem[]) => void; disabled: boolean
}) {
  const change = (items: ExamAttachmentItem[]) => {
    const valid = items.filter(item => {
      if (!item.file || value.some(current => current.uid === item.uid)) return true
      const error = examAttachmentError(item.file)
      if (error) { message.error(`${item.name}：${error}`); if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) }
      return !error
    })
    value.filter(item => !valid.some(current => current.uid === item.uid)).forEach(item => {
      if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
    })
    onChange(valid)
  }
  return <DeferredAttachmentPicker value={value} onChange={change} imageOnly={false}
    accept={EXAM_ATTACHMENT_ACCEPT} maxCount={10} disabled={disabled} />
}

export default function ExamScheduleAttachments({ scheduleId, files = [] }: { scheduleId: number; files?: ExamAttachment[] }) {
  if (!files.length) return null
  return <div aria-label="考期备注附件"><Space orientation="vertical" style={{ width: '100%' }}>
    {files.map(file => <AttachmentCard key={file.fileId} name={file.name}
      load={() => examScheduleAttachments.read(scheduleId, file.fileId)} />)}
  </Space></div>
}
