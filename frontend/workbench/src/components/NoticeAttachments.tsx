import { Space } from 'antd'
import type { AnnouncementAttachment } from '../services/api'
import { noticeAttachmentResource } from '../services/noticeAttachment'
import AttachmentCard from './AttachmentCard'

export default function NoticeAttachments({ files, reload }: {
  files: AnnouncementAttachment[]
  reload?: () => Promise<AnnouncementAttachment[]>
}) {
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {files.map(file => <AttachmentCard key={`${file.infraFileId}:${file.downloadUrl || ''}`} name={file.fileName} load={async () => {
      const current = reload ? (await reload()).find(item => item.infraFileId === file.infraFileId) : file
      return noticeAttachmentResource(current)
    }} />)}
  </Space>
}
