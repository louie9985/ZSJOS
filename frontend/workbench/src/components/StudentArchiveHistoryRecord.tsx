import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Collapse, Spin } from 'antd';
import { archiveImageReference, studentArchiveApi, type StudentArchive } from '../services/studentArchive';
import type { StudentInfoImage } from '../services/studentInfo';
import AttachmentCard, { type AttachmentResource } from './AttachmentCard';
import { formatTimestamp } from '../services/time';

export default function StudentArchiveHistoryRecord({ relationId, entry, sensitive, attachment }: {
  relationId: number;
  entry: StudentArchive['history'][number];
  sensitive: boolean;
  attachment: (image: StudentInfoImage) => AttachmentResource;
}) {
  const [data, setData] = useState<StudentArchive>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const sequence = useRef(0);
  const load = useCallback(async () => {
    const current = ++sequence.current; setLoading(true); setError('');
    try {
      const result = entry.source === 'submission' && entry.submissionId
        ? await studentArchiveApi.submission(relationId, entry.submissionId, sensitive)
        : await studentArchiveApi.history(relationId, entry.revision ?? 0, sensitive);
      if (current === sequence.current) setData(result);
    } catch (e) {
      if (current === sequence.current) setError(e instanceof Error ? e.message : '历史资料加载失败');
    } finally { if (current === sequence.current) setLoading(false); }
  }, [relationId, entry.source, entry.submissionId, entry.revision, sensitive]);
  useEffect(() => { sequence.current++; setData(undefined); setError(''); setLoading(false); return () => { sequence.current++; }; }, [load]);
  useEffect(() => { if (open) void load(); }, [open, load]);
  const label = entry.source === 'submission' ? entry.taskTitle || '考试资料填写'
    : entry.revision === 0 ? '原报名档案' : `V${entry.revision} · ${entry.submissionId ? '学员提交' : '人工维护'}`;
  return <Collapse className="student-info-panel" activeKey={open ? ['record'] : []} onChange={keys => setOpen(Boolean(keys.length))} items={[{
    key: 'record',
    label: <span>{label}{entry.modifiedAt ? ` · ${formatTimestamp(entry.modifiedAt)}` : ''}{entry.operatorName ? ` · ${entry.operatorName}` : ''}</span>,
    children: <>
      {entry.reason && <p>{entry.reason}</p>}
      {loading && <Spin aria-label="正在加载历史资料" />}
      {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
      {data && <dl className="student-info-fields">{data.information.fields.filter(f => f.enabled).map(field => <div
        key={field.key} className={`student-info-field${field.type === 'images' || field.type === 'textarea' ? ' is-wide' : ''}`}>
        <dt>{field.label}</dt>
        <dd>{field.type === 'images' ? <div className="student-info-attachments">{
          data.information.images.filter(i => i.fieldKey === field.key).map(i => sensitive
            ? <AttachmentCard key={archiveImageReference(i)} name={i.name} load={async () => attachment(i)} />
            : <span key={archiveImageReference(i)}>{i.name}</span>)
        }{!data.information.images.some(i => i.fieldKey === field.key) && '未提交'}</div> : data.information.values[field.key] || '未填写'}</dd>
      </div>)}</dl>}
    </>,
  }]} />;
}
