import { Button, Descriptions, Tabs, Typography } from 'antd'
import { useState } from 'react'
import NoticeShareDialog from './NoticeShareDialog'
import { noticePermission, noticeAudienceText } from '../services/noticeManagement'
import NoticeReadStatistics from './NoticeReadStatistics'
import { NOTICE_STATUSES, noticeManagement, type ManagedNotice } from '../services/noticeManagement'
import NoticeAttachments from './NoticeAttachments'
import DateTimeText from './DateTimeText'
import SafeRichText from './SafeRichText'

export default function NoticeManagementDetail({ notice, preview = false, permissions = [] }: { notice: ManagedNotice; preview?: boolean; permissions?: string[] }) {
  const [sharing, setSharing] = useState(false)
  const content = <article className="announcement-detail">
    <Typography.Title level={3}>{notice.title}</Typography.Title>
    {!preview && notice.publishStatus === 'PUBLISHED' && noticePermission(permissions, 'query') && noticePermission(permissions, 'share') && <Button onClick={() => setSharing(true)}>对外分享</Button>}
    {sharing && <NoticeShareDialog notice={notice} onClose={() => setSharing(false)} />}
    <Descriptions column={1} size="small" items={[
      { key: 'status', label: '发布状态', children: NOTICE_STATUSES[notice.publishStatus] },
      { key: 'source', label: '文章来源', children: notice.sourceDeptName || '未记录' },
      { key: 'publisher', label: '发布人', children: notice.publishStatus === 'DRAFT' ? '发布时自动记录' : notice.publisherName || '未记录' },
      { key: 'audience', label: '接收部门/人员', children: noticeAudienceText(notice) },
      { key: 'publish', label: '发布时间', children: <DateTimeText value={notice.publishTime} /> },
      { key: 'highlight', label: '高亮截止时间', children: <DateTimeText value={notice.highlightUntil} /> }
    ]} />
    <SafeRichText announcementTables html={notice.content || ''} />
    {!!notice.attachments?.length && <>
      <Typography.Title level={5}>附件</Typography.Title>
      <NoticeAttachments files={notice.attachments} reload={preview ? undefined : async () => (await noticeManagement.get(notice.id)).attachments} />
    </>}
  </article>
  return preview ? content : <Tabs key={notice.id} destroyOnHidden items={[
    { key: 'content', label: '公告正文', children: content },
    { key: 'reading', label: '阅读情况', children: <NoticeReadStatistics key={notice.id} id={notice.id} /> }
  ]} />
}
