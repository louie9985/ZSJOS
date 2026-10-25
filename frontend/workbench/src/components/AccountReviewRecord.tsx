import { Button, Space, Typography } from 'antd';
import { HistoryOutlined } from '@ant-design/icons';
import { useState, type ReactNode } from 'react';
import { type ProfileEntry } from '../services/mediaAccountProfile';
import AccountReviewHistory from './AccountReviewHistory';

export default function AccountReviewRecord(props: {
  accountId: number; fieldKey: string; title?: string; latest?: ProfileEntry; canHistory: boolean;
  action?: ReactNode;
  extra?: (entry: ProfileEntry) => ReactNode;
}) {
  // A changed record scope or revoked history access also discards the open dialog.
  return <AccountReviewRecordContent key={`${props.accountId}:${props.fieldKey}:${props.canHistory}`} {...props} />;
}

function AccountReviewRecordContent({ accountId, fieldKey, title, latest, canHistory, action, extra }: Parameters<typeof AccountReviewRecord>[0]) {
  const [historyOpen, setHistoryOpen] = useState(false);
  return <div className="account-review-record">
    {!latest && <Typography.Text type="secondary">尚未填写</Typography.Text>}
    <Space className="account-review-record-actions" size={6} wrap={false}>{action && <span className="account-review-record-primary">{action}</span>}
      {canHistory && <Button size="small" icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>查看本项历史</Button>}</Space>
    {historyOpen && canHistory && <AccountReviewHistory accountId={accountId} fieldKey={fieldKey}
      title={title || latest?.title || '本项记录'} latestId={latest?.id} extra={extra}
      onClose={() => setHistoryOpen(false)} />}
  </div>;
}
