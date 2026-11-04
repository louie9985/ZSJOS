import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Collapse, Spin, Tag } from 'antd';
import { archiveImageReference, studentArchiveApi, type StudentArchive } from '../services/studentArchive';
import type { StudentInfoImage } from '../services/studentInfo';
import AttachmentCard, { type AttachmentResource } from './AttachmentCard';
import { formatTimestamp } from '../services/time';
import { archiveChanges } from '../services/studentArchiveDiff';

export default function StudentArchiveHistoryRecord({ relationId, entry, sensitive, attachment, submissionNumber }: {
  relationId: number;
  entry: StudentArchive['history'][number];
  sensitive: boolean;
  submissionNumber?: number;
  attachment: (image: StudentInfoImage) => AttachmentResource;
}) {
  const [data, setData] = useState<StudentArchive>();
  const [previous, setPrevious] = useState<StudentArchive>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const sequence = useRef(0);
  const load = useCallback(async () => {
    const current = ++sequence.current; setLoading(true); setError(''); setData(undefined); setPrevious(undefined);
    try {
      const result = entry.source === 'submission' && entry.submissionId
        ? await studentArchiveApi.submission(relationId, entry.submissionId, sensitive)
        : await studentArchiveApi.history(relationId, entry.revision ?? 0, sensitive);
      const prior = entry.source === 'service' && (entry.revision ?? 0) > 0
        ? await studentArchiveApi.history(relationId, entry.revision! - 1, sensitive) : undefined;
      if (current === sequence.current) { setData(result); setPrevious(prior); }
    } catch (e) {
      if (current === sequence.current) setError(e instanceof Error ? e.message : '历史资料加载失败');
    } finally { if (current === sequence.current) setLoading(false); }
  }, [relationId, entry.source, entry.submissionId, entry.revision, sensitive]);
  useEffect(() => { sequence.current++; setData(undefined); setPrevious(undefined); setError(''); setLoading(false); return () => { sequence.current++; }; }, [load]);
  useEffect(() => { if (open) void load(); }, [open, load]);
  const label = entry.source === 'submission' ? entry.taskTitle || '考试资料填写'
    : entry.revision === 0 ? '原报名档案' : `V${entry.revision} · ${entry.submissionId ? '学员提交' : '人工维护'}`;
  const difference = data && previous ? archiveChanges(previous, data, sensitive) : undefined;
  const files = (items: StudentInfoImage[]) => <div className="student-info-attachments">{items.length ? items.map(image => sensitive
    ? <AttachmentCard key={archiveImageReference(image)} name={image.name} load={async () => attachment(image)} />
    : <span key={archiveImageReference(image)}>{image.name}</span>) : '未提交'}</div>;
  return <Collapse className="student-info-panel" activeKey={open ? ['record'] : []} onChange={keys => setOpen(Boolean(keys.length))} items={[{
    key: 'record',
    label: <span>{submissionNumber ? `第 ${submissionNumber} 次填写 · ` : ''}{label}{entry.modifiedAt ? ` · ${formatTimestamp(entry.modifiedAt)}` : ''}{entry.operatorName ? ` · ${entry.operatorName}` : ''}{entry.source === 'submission' && <Tag color={entry.submissionStatus === 'ARCHIVED' ? 'success' : entry.submissionStatus === 'EXCEPTION' ? 'warning' : undefined}>{({ARCHIVED:'已归档',EXCEPTION:'待处理异常',INVALID:'已标记无效'} as Record<string,string>)[entry.submissionStatus || ''] || '历史状态未记录'}</Tag>}</span>,
    children: <>
      {entry.reason && <p>{entry.reason}</p>}
      {loading && <Spin aria-label="正在加载历史资料" />}
      {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
      {difference && <section aria-label="本次档案修改对照">
        <p>本次修改 · 对比{entry.revision === 1 ? '原报名档案' : ` V${entry.revision! - 1}`}</p>
        {difference.hidden && <Alert type="info" title="当前为脱敏展示，敏感字段不参与对照。" />}
        {!difference.changes.length && <p>{difference.hidden ? '可见字段无变化' : '字段内容无变化'}</p>}
        {difference.changes.map(change => <section key={change.key} aria-label={`${change.label}修改对照`}>
          <p><strong>{change.label}</strong></p>
          <dl className="student-info-fields">
            <div className="student-info-field"><dt>修改前</dt><dd>{change.images ? files(change.beforeImages) : change.before || '未填写'}</dd></div>
            <div className="student-info-field"><dt>修改后</dt><dd>{change.images ? files(change.afterImages) : change.after || '未填写'}</dd></div>
          </dl>
        </section>)}
        <p>以下为本版本完整档案</p>
      </section>}
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
