import { Space, Typography } from 'antd'
import type { VersionUpdate } from '../../services/versionUpdates'
import SafeRichText from '../SafeRichText'
import DateTimeText from '../DateTimeText'
import AttachmentCard from '../AttachmentCard'

export default function VersionContent({ record, reload }: { record: VersionUpdate; reload: () => Promise<VersionUpdate> }) {
  return <article className="version-update-content">
    <Typography.Title level={3}>{record.title}</Typography.Title>
    <Space wrap><DateTimeText value={record.publishTime} /><Typography.Text type="secondary">{record.publisherName}</Typography.Text></Space>
    <SafeRichText previewImages html={record.content || ''} />
    <Space orientation="vertical" style={{ width: '100%' }}>{record.attachments.map(file => <AttachmentCard key={file.id} name={file.name} load={async () => {
      const latest = (await reload()).attachments.find(item => item.id === file.id)
      if (!latest?.url) throw new Error('附件暂不可用')
      return { name: latest.name, url: latest.url, type: latest.type, size: latest.size }
    }} />)}</Space>
  </article>
}
