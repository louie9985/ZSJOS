import { Alert, Button, Image, Pagination, Space, Spin, Typography } from 'antd';
import { HistoryOutlined, FormOutlined, EditOutlined } from '@ant-design/icons';
import { useEffect, useState, type ReactNode } from 'react';
import { accountProfileApi, type ProfileEntry } from '../services/mediaAccountProfile';
import { formatTimestamp } from '../services/time';

export default function AccountReviewRecord({ accountId, fieldKey, latest, canHistory, text, action, onRevise }: {
  accountId: number; fieldKey: string; latest?: ProfileEntry; canHistory: boolean;
  text: (entry: ProfileEntry) => string | undefined; action?: ReactNode; onRevise?: (entry: ProfileEntry) => void;
}) {
  const [expanded, setExpanded] = useState(false), [page, setPage] = useState(1);
  const [history, setHistory] = useState<ProfileEntry[]>([]), [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [retry, setRetry] = useState(0);
  useEffect(() => { setExpanded(false); setPage(1); setHistory([]); }, [accountId, fieldKey]);
  useEffect(() => {
    let active = true;
    if (!expanded || !canHistory) return;
    setLoading(true); setError('');
    void accountProfileApi.history(accountId, page, { fieldKey }).then(r => { if (active) { setHistory(r.list); setTotal(r.total); } })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : '历史加载失败'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [accountId, fieldKey, expanded, canHistory, page, retry, latest?.id]);
  const render = (entry: ProfileEntry) => <article key={entry.id}>
    <Typography.Text type="secondary">{entry.operatedBy || '未记录填写人'} · {formatTimestamp(entry.operatedAt)}</Typography.Text>
    <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{text(entry)}</Typography.Paragraph>
    <Space wrap>{entry.files.map(file => {
      const url = file.previewUrl && /^https?:\/\//i.test(file.previewUrl) ? file.previewUrl : undefined;
      return <span key={file.id}>{url ? <>{file.type?.startsWith('image/') && <Image width={80} src={url} />}<a href={url} target="_blank" rel="noreferrer" download={file.name}>{file.name} · 下载</a></> : `${file.name} · 暂不可读取`}</span>;
    })}</Space>
  </article>;
  return <div>
    {latest ? render(latest) : <Typography.Text type="secondary">尚未填写</Typography.Text>}
    <Space className="account-review-record-actions" size={6} wrap={false}>{action && <span className="account-review-record-primary">{action}</span>}{latest && onRevise && <Button size="small" icon={<EditOutlined />} onClick={() => onRevise(latest)}>基于最新记录修订</Button>}
      {canHistory && <Button size="small" icon={<HistoryOutlined />} onClick={() => setExpanded(!expanded)}>{expanded ? '收起历史' : '查看本项历史'}</Button>}</Space>
    {expanded && <div>{error ? <Alert type="error" title={error} action={<Button onClick={() => setRetry(x => x + 1)}>重试</Button>} /> : loading ? <Spin /> : <>{history.map(render)}{!history.length && <p>暂无历史</p>}<Pagination current={page} pageSize={10} total={total} onChange={setPage} hideOnSinglePage /></>}</div>}
  </div>;
}
