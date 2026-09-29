import { Descriptions, Tabs, Typography } from 'antd'
import NoticeReadStatistics from './NoticeReadStatistics'
import { NOTICE_STATUSES, noticeManagement, type ManagedNotice } from '../services/noticeManagement'
import NoticeAttachments from './NoticeAttachments'
import DateTimeText from './DateTimeText'
import SafeRichText from './SafeRichText'

export default function NoticeManagementDetail({ notice, preview = false }: { notice: ManagedNotice; preview?: boolean }) {
  const content = <article className="announcement-detail">
    <Typography.Title level={3}>{notice.title}</Typography.Title>
    <Descriptions column={1} size="small" items={[
      { key: 'status', label: '发布状态', children: NOTICE_STATUSES[notice.publishStatus] },
      { key: 'audience', label: '接收范围', children: notice.audienceType === 'TARGET' ? `指定部门/用户（${notice.targetDeptIds?.length || 0} 个部门，${notice.targetUserIds?.length || 0} 个指定用户${notice.recipientCount == null ? '' : `，发布时接收人数 ${notice.recipientCount}`}）` : '全员' },
      { key: 'publish', label: '发布时间', children: <DateTimeText value={notice.publishTime} /> },
      { key: 'highlight', label: '高亮截止时间', children: <DateTimeText value={notice.highlightUntil} /> }
    ]} />
    <SafeRichText html={notice.content || ''} />
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
