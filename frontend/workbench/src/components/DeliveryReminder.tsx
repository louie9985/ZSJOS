import { Alert, Button, Modal, Space } from 'antd';
import { useEffect, useState } from 'react';
import { api, type DeliveryReminder as Reminder } from '../services/api';
import { formatTimestamp } from '../services/time';

export default function DeliveryReminder({ enabled }: { enabled: boolean }) {
  const [items, setItems] = useState<Reminder[]>([]), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    const load = () => { void api.studentDelivery.reminders().then(r => { if (active) setItems(r); }).catch(e => { if (active) setError(e instanceof Error ? e.message : '交付提醒加载失败'); }); };
    load(); const timer = window.setInterval(load, 60000);
    return () => { active = false; window.clearInterval(timer); };
  }, [enabled]);
  const close = async () => {
    if (busy) return; setBusy(true);
    try { await api.studentDelivery.acknowledge(items.map(x => x.stageId)); setItems([]); setError(''); }
    catch(e) { setError(e instanceof Error ? e.message : '提醒确认失败，请重试'); } finally { setBusy(false); }
  };
  return <Modal title="今日账号交付提醒" open={items.length > 0} onCancel={() => void close()} onOk={() => void close()} okText="知道了，稍后处理" confirmLoading={busy} cancelButtonProps={{style:{display:'none'}}}>
    {error && <Alert type="error" title={error} />}
    <Space orientation="vertical">{items.map(x => <div key={x.stageId}><strong>{x.accountName || '未命名账号'} · {x.stageCode}</strong><p>{x.weeklyLeads != null ? `上周有效客资 ${x.weeklyLeads} 条，请确认继续观察或重新定位` : `交付截止：${formatTimestamp(x.dueAt)}`}</p></div>)}</Space>
    <p>请在对应账号的“账号复盘记录”中完成确认、延期或重新定位。</p>
    {error && <Button onClick={() => void close()}>重试</Button>}
  </Modal>;
}
