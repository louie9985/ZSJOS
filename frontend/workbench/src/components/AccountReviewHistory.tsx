import { Alert, Button, Empty, Modal, Pagination, Space, Spin, Timeline, Typography } from 'antd';
import { EditOutlined, FormOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { accountProfileApi, type ProfileEntry } from '../services/mediaAccountProfile';
import { formatTimestamp } from '../services/time';
import AccountReviewHistoryContent, { diagnosisHistoryData, historyAction } from './AccountReviewHistoryContent';
import '../styles/components/account-review-history.css';

export default function AccountReviewHistory({ accountId, fieldKey, title, latestId, extra, onClose }: {
  accountId: number; fieldKey: string; title: string; latestId?: number;
  extra?: (entry: ProfileEntry) => ReactNode; onClose: () => void;
}) {
  const [page, setPage] = useState(1), [retry, setRetry] = useState(0);
  const [history, setHistory] = useState<ProfileEntry[]>([]), [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true), [error, setError] = useState('');
  const top = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setHistory([]);
    void accountProfileApi.history(accountId, page, { fieldKey })
      .then(result => { if (active) { setHistory(result.list); setTotal(result.total); } })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '历史加载失败'); })
      .finally(() => { if (active) setLoading(false); });
    top.current?.closest('.ant-modal-body')?.scrollTo({ top: 0 });
    return () => { active = false; };
  }, [accountId, fieldKey, page, retry, latestId]);

  return <Modal title={`${title} · 历史记录`} open onCancel={onClose} width={1080}
    className="account-review-history-modal" style={{ top: 16, maxWidth: 'calc(100vw - 32px)', paddingBottom: 0 }}
    styles={{ container: { maxHeight: 'calc(100dvh - 32px)', display: 'flex', flexDirection: 'column' }, body: { minHeight: 0, overflowY: 'auto' }, footer: { flexShrink: 0 } }}
    footer={<div className="account-review-history-footer">
      <Pagination current={page} pageSize={10} total={total} disabled={loading || !!error} showSizeChanger={false}
        onChange={setPage} hideOnSinglePage size="small" />
      <Button onClick={onClose}>关闭</Button>
    </div>} destroyOnHidden>
    <div ref={top} className="account-review-history-heading">
      <Typography.Text strong>流程时间线</Typography.Text>
      <Typography.Text type="secondary">最新记录在前 · 北京时间{!loading && !error ? ` · 共 ${total} 条` : ''}</Typography.Text>
    </div>
    {loading ? <div className="account-review-history-state" role="status"><Space><Spin /><span>正在加载历史记录…</span></Space></div>
      : error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => setRetry(value => value + 1)}>重试</Button>} />
        : !history.length ? <Empty description="暂无本项历史记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          : <Timeline className="account-review-history-timeline" items={history.map(entry => {
            const data = diagnosisHistoryData(entry);
            return { key: entry.id, icon: data?.previousEntryId != null ? <EditOutlined /> : <FormOutlined />,
              content: <>
                <div className="account-review-history-event">
                  <Typography.Text strong>{formatTimestamp(entry.operatedAt, '未记录时间', 'second')}</Typography.Text>
                  <Typography.Text type="secondary">{entry.operatedBy || '未记录填写人'} · {historyAction(entry, data)}</Typography.Text>
                  {entry.id === latestId && <span className="account-review-history-latest">最新记录</span>}
                </div>
                <AccountReviewHistoryContent entry={entry} extra={extra?.(entry)} />
              </> };
          })} />}
  </Modal>;
}
