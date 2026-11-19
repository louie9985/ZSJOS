import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Checkbox, Empty, Modal, Space, Spin, Typography } from 'antd'
import { collectionTasksApi, type CollectionCommand, type CollectionGenerationConfig, type CollectionTask } from '../services/studentInfoTasks'
import { studentInfoError } from '../services/studentInfo'

const educationKeys = new Set(['registration_level', 'target_school', 'target_major'])

export default function StudentInfoGenerateModal({ task, onClose, onGenerated }: {
  task: CollectionTask; onClose: () => void; onGenerated: (task: CollectionTask) => void
}) {
  const [config, setConfig] = useState<CollectionGenerationConfig>()
  const [selected, setSelected] = useState<string[]>([])
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const sequence = useRef(0)
  const command = useRef<CollectionCommand | null>(null)
  const load = async () => {
    const run = ++sequence.current
    setLoading(true); setError(''); setConfig(undefined)
    try {
      const result = await collectionTasksApi.generationConfig(task.id)
      if (run === sequence.current) { setConfig(result); setSelected(result.defaultSelectedKeys ?? []); command.current = null }
    } catch (e) { if (run === sequence.current) setError(studentInfoError(e)) }
    finally { if (run === sequence.current) setLoading(false) }
  }
  useEffect(() => { void load(); return () => { sequence.current++ } }, [task.id])
  const generate = async () => {
    if (!config || busy) return
    setBusy(true); setError('')
    command.current ??= { version: task.version, idempotencyKey: crypto.randomUUID(), configVersionId: config.configVersionId, materialKeys: selected }
    try { onGenerated(await collectionTasksApi.command(task.id, 'generate', command.current)) }
    catch (e) { setError(studentInfoError(e)) }
    finally { setBusy(false) }
  }
  const materials = config?.fields.filter(f => config.selectableMaterialKeys.includes(f.key)) ?? []
  const sections = [
    { title: '礼品寄送', note: '录入订单时选择礼品则默认勾选', fields: materials.filter(f => f.key === 'gift_shipping_address') },
    { title: '学历报考信息', note: '报名学历产品则默认勾选', fields: materials.filter(f => educationKeys.has(f.key)) },
    { title: '可选考务信息', fields: materials.filter(f => f.key !== 'gift_shipping_address' && !educationKeys.has(f.key)) },
  ]
  const basic = config?.fields.filter(f => f.enabled && !config.selectableMaterialKeys.includes(f.key)) ?? []
  return <Modal open title="生成收集表" width={560} onCancel={busy ? undefined : onClose} closable={!busy} maskClosable={!busy}
    footer={<Space><Button disabled={busy} onClick={onClose}>取消</Button><Button type="primary" loading={busy} disabled={loading || !config} onClick={() => void generate()}>生成链接</Button></Space>}>
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      {error && <Alert type="error" title={error} action={<Button disabled={busy} onClick={() => void load()}>刷新配置</Button>} />}
      {loading ? <Spin /> : config && <>
        <Typography.Text type="secondary">{basic.map(f => f.label).join('、')}</Typography.Text>
        {materials.length ? <Checkbox.Group value={selected} disabled={busy || command.current !== null} onChange={keys => setSelected(keys as string[])} style={{ display: 'grid', gap: 16 }}>
          {sections.filter(section => section.fields.length > 0).map(({ title, note, fields }) => <section key={title} aria-label={title}>
            <Typography.Text strong>{title}（已选 {fields.filter(f => selected.includes(f.key)).length} 项）</Typography.Text>
            {note && <Typography.Text type="secondary"> {note}</Typography.Text>}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(180px, 100%), 1fr))', gap: 12, marginTop: 8 }}>
              {fields.map(f => <Checkbox key={f.key} value={f.key}>{f.label}{f.required ? '（勾选后必填）' : ''}</Checkbox>)}
            </div>
          </section>)}
        </Checkbox.Group> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前模板没有可选字段" />}
        <Typography.Text type="secondary">生成后按本次选择收集；已有链接和历史报名资料保持原样。</Typography.Text>
      </>}
    </Space>
  </Modal>
}
