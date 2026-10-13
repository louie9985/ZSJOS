import { message, Typography } from 'antd'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import AttachmentCard from './AttachmentCard'
import { COURSE_ATTACHMENT_ACCEPT, courseAttachmentError, courseAttachments, type CourseAttachmentItem } from '../services/courseAttachments'
import type { CourseCalendarEvent } from '../services/api'
export function CourseAttachmentPicker({ value, onChange, disabled }: {
 value: CourseAttachmentItem[]; onChange: (items: CourseAttachmentItem[]) => void; disabled: boolean
}) {
 const change = (items: CourseAttachmentItem[]) => {
  const valid = items.filter(item => {
   if (!item.file || value.some(current => current.uid === item.uid)) return true
   const error = courseAttachmentError(item.file)
   if (error) { message.error(item.name + '：' + error); if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) }
   return !error
  })
  value.filter(item => !valid.some(current => current.uid === item.uid)).forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) })
  onChange(valid)
 }
 return <DeferredAttachmentPicker value={value} onChange={change} accept={COURSE_ATTACHMENT_ACCEPT} maxCount={20} imageOnly={false} disabled={disabled} />
}
export function CourseDayAttachments({ rows }: { rows: CourseCalendarEvent[] }) {
 const courses = rows.filter(row => row.attachments?.length)
 if (!courses.length) return null
 return <section className="course-day-attachments" aria-label="当天课程附件">
  <Typography.Title level={5}>课程附件</Typography.Title>
  {courses.map(row => <section key={row.id}><Typography.Paragraph strong>{row.courseName}</Typography.Paragraph>
   {row.attachments!.map(file => <AttachmentCard key={file.fileId} name={file.name} load={() => courseAttachments.read(row.id, file.fileId)} />)}
  </section>)}
 </section>
}
