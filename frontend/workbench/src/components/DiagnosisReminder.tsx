import { Alert, Button, Modal, Space } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { diagnosisApi, diagnosisTaskUrl, type DiagnosisTodo } from '../services/mediaAccountProfile';
import { useNavigate } from 'react-router-dom';
import { formatTimestamp } from '../services/time';
import { useOverlayCoordinator } from './OverlayCoordinator';

export default function DiagnosisReminder({ enabled }: { enabled: boolean }) {
  const navigate = useNavigate();
  const { businessOverlayCount } = useOverlayCoordinator();
  const [items, setItems] = useState<DiagnosisTodo[]>([]), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const itemsRef = useRef(items); itemsRef.current = items;
  const acknowledged = useRef('');
  const [retry, setRetry] = useState(0);
  useEffect(() => { if (!enabled) return; let active = true;
    const load = () => { if (itemsRef.current.length) return; void diagnosisApi.reminders().then(r => { if (active) { setItems(r); setError(''); } }).catch(e => { if (active) setError(e instanceof Error ? e.message : '诊断提醒加载失败'); }); };
    load(); const timer = window.setInterval(load, 60000); return () => { active=false; window.clearInterval(timer); };
  }, [enabled,retry]);
  const visible = items.length > 0 && businessOverlayCount === 0;
  const markSeen = useCallback(async () => {
    const ids = items.map(x => x.taskId), key = ids.join(',');
    if (!ids.length || acknowledged.current === key) return;
    await diagnosisApi.acknowledge(ids); acknowledged.current=key; setError('');
  }, [items]);
  // A displayed summary is recorded server-side; dismissing never completes a diagnosis.
  useEffect(() => { if (visible) void markSeen().catch(e => setError(e instanceof Error ? e.message : '提醒状态保存失败，请重试')); }, [visible,markSeen]);
  const dismiss = async (item?: DiagnosisTodo) => { if (busy) return; setBusy(true);
    try { await markSeen(); setItems([]); if (item) navigate(diagnosisTaskUrl(item)); }
    catch(e) { setError(e instanceof Error ? e.message : '提醒状态保存失败，请重试'); } finally { setBusy(false); }
  };
  return <>{error && !items.length && <Alert type="warning" message={error} action={<Button onClick={() => setRetry(x=>x+1)}>重试诊断提醒</Button>} closable />}
    <Modal title="今日账号诊断跟进" open={visible} onCancel={() => void dismiss()} onOk={() => void dismiss()} okText="稍后处理" cancelButtonProps={{style:{display:'none'}}} confirmLoading={busy}
      width={720} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={{container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column'},body:{overflowY:'auto',minHeight:0},footer:{flexShrink:0}}}>
      {error && <Alert type="error" message={error} action={<Button onClick={() => void markSeen().catch(e=>setError(String(e)))}>重试</Button>} />}
      <Space orientation="vertical" style={{width:'100%'}}>{items.map(item => <div key={item.taskId}><strong>{item.accountName || '未命名账号'} · {item.title}</strong><p>截止时间：{formatTimestamp(item.dueAt)}（北京时间）</p><p>{item.dueAt < Date.now() ? '已逾期，请尽快补填。' : '距截止不足或等于 1 天，请及时填写。'}</p><p style={{whiteSpace:'pre-wrap'}}>诊断要求：{String(item.payload.requirementSnapshot?.[item.templateType] ?? '未填写')}</p><Button disabled={busy} type="link" onClick={() => void dismiss(item)}>填写此项</Button></div>)}</Space>
    </Modal></>;
}
