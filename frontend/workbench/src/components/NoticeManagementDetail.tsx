import { Descriptions, Typography } from 'antd'
import { NOTICE_STATUSES, noticeDownloadUrl, type ManagedNotice } from '../services/noticeManagement'
import DateTimeText from './DateTimeText'
import SafeRichText from './SafeRichText'

export default function NoticeManagementDetail({ notice }: { notice: ManagedNotice }) {
  return <article className="announcement-detail">
    <Typography.Title level={3}>{notice.title}</Typography.Title>
    <Descriptions column={1} size="small" items={[
      { key: 'status', label: '发布状态', children: NOTICE_STATUSES[notice.publishStatus] },
      { key: 'audience', label: '接收范围', children: notice.audienceType === 'TARGET' ? `指定部门/用户（${notice.targetDeptIds?.length || 0} 个部门，${notice.targetUserIds?.length || 0} 个指定用户${notice.recipientCount == null ? '' : `，发布时接收人数 ${notice.recipientCount}`}）` : '全员' },
      { key: 'publish', label: '发布时间', children: <DateTimeText value={notice.publishTime} /> },
      { key: 'highlight', label: '高亮截止时间', children: <DateTimeText value={notice.highlightUntil} /> }
    ]} />
    <SafeRichText html={notice.content || ''} />
    <Typography.Title level={5}>附件</Typography.Title>
    {!notice.attachments?.length ? <Typography.Text type="secondary">无附件</Typography.Text> : notice.attachments.map(file => <div key={file.infraFileId}>
      {noticeDownloadUrl(file.downloadUrl) ? <a href={noticeDownloadUrl(file.downloadUrl)} target="_blank" rel="noopener noreferrer">{file.fileName}</a> : <span>{file.fileName}（文件不可用）</span>}
    </div>)}
  </article>
}
