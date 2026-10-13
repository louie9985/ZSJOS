import { Alert, Button, ColorPicker, Empty, Input, Modal, Popconfirm, Space, Spin, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { calendarColors, type CalendarColor, type CalendarColorType, type CalendarPalette } from '../services/calendarColors'
import { examBackgroundStyle } from '../pages/examCalendarColor'
import '../styles/components/calendar-colors.css'

export function useCalendarPalette(type: CalendarColorType) {
  const [palette, setPalette] = useState<CalendarPalette>({ presets: [], colors: [] })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const reload = useCallback(() => setRevision(v => v + 1), [])
  useEffect(() => {
    let active = true
    setLoading(true); setError(''); setPalette({ presets: [], colors: [] })
    calendarColors.get(type).then(data => { if (active) setPalette(data) })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : '快捷色加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [type, revision])
  return { palette, setPalette, loading, error, reload }
}

export function CalendarColorLegend({ state }: { state: ReturnType<typeof useCalendarPalette> }) {
  return <div className="calendar-color-legend"><Space wrap>{state.palette.presets.map(c =>
    <span key={c.id}><span className="calendar-color-swatch" style={examBackgroundStyle(c.color)} />{c.name}</span>
  )}</Space><Typography.Text type="secondary">统一颜色为建议标注，课程可自选底色。</Typography.Text>
    {state.error && <Alert type="error" title={state.error} action={<Button onClick={state.reload}>重试颜色加载</Button>} />}
  </div>
}

export default function CalendarColorEditor({ value, onChange, type, canManage, state, title = '日程预览' }: {
  value?: string; onChange?: (value: string) => void; type: CalendarColorType; canManage: boolean;
  state: ReturnType<typeof useCalendarPalette>; title?: string
}) {
  const [dialog, setDialog] = useState<'save' | 'manage'>()
  const [editing, setEditing] = useState<CalendarColor>()
  const [name, setName] = useState('')
  const [color, setColor] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const openSave = (item?: CalendarColor) => { setEditing(item); setName(item?.name || ''); setColor(item?.color || value || ''); setError(''); setDialog('save') }
  const save = async () => {
    if (!name.trim() || Array.from(name.trim()).length > 20) { setError('名称须为1至20字'); return }
    if (!/^#[\da-f]{6}$/i.test(color)) { setError('请选择有效颜色'); return }
    setBusy(true); setError('')
    try { state.setPalette(await calendarColors.save(type, { name: name.trim(), color }, editing?.id)); setDialog(undefined) }
    catch (e) { setError(e instanceof Error ? e.message : '快捷色保存失败，请重试') }
    finally { setBusy(false) }
  }
  const remove = async (id: string) => {
    setBusy(true); setError('')
    try { state.setPalette(await calendarColors.remove(type, id)) }
    catch (e) { setError(e instanceof Error ? e.message : '删除失败，请重试') }
    finally { setBusy(false) }
  }
  const buttons = (colors: CalendarColor[]) => <Space wrap>{colors.map(c => <Button key={c.id} style={examBackgroundStyle(c.color)}
    aria-pressed={value?.toUpperCase() === c.color} onClick={() => onChange?.(c.color)}>{c.name}</Button>)}</Space>
  return <div className="calendar-color-editor">
    {state.loading ? <Spin size="small" /> : state.error ? <Alert type="error" title={state.error} action={<Button onClick={state.reload}>重试</Button>} /> : <>
      {state.palette.presets.length > 0 && <div><Typography.Text>统一快捷色</Typography.Text>{buttons(state.palette.presets)}</div>}
      <div><Typography.Text>我的快捷色</Typography.Text>{state.palette.colors.length ? buttons(state.palette.colors) : <Typography.Text type="secondary">暂无快捷色</Typography.Text>}</div>
    </>}
    <Space wrap><ColorPicker value={value || undefined} format="hex" disabledFormat disabledAlpha allowClear
      onChange={c => onChange?.(c.cleared ? '' : c.toHexString().toUpperCase())}>
      <Button style={examBackgroundStyle(value)} aria-label="自选颜色">{value || '自选颜色'}</Button>
    </ColorPicker><Button onClick={() => onChange?.('')}>恢复默认底色</Button>
      {canManage && <><Button disabled={!value || state.loading || !!state.error} onClick={() => openSave()}>保存为快捷色</Button>
        <Button disabled={state.loading || !!state.error} onClick={() => { setDialog('manage'); setError('') }}>管理快捷色</Button></>}
    </Space>
    <div className="calendar-color-preview" data-calendar-colored={!!value} style={examBackgroundStyle(value)}>{title || '日程预览'}</div>
    <Modal title={editing ? '编辑快捷色' : '保存为快捷色'} open={dialog === 'save'} onCancel={() => !busy && setDialog(undefined)}
      onOk={() => void save()} confirmLoading={busy} closable={!busy} maskClosable={!busy}>
      <Space orientation="vertical" style={{ width: '100%' }}><Typography.Text type="secondary">独立保存到当前账号；取消新建日程也会保留此快捷色。</Typography.Text>
        <Input aria-label="快捷色名称" value={name} onChange={e => setName(e.target.value)} placeholder="快捷色名称，1至20字" disabled={busy} />
        <ColorPicker value={color || undefined} format="hex" disabledFormat disabledAlpha disabled={busy} onChange={c => setColor(c.toHexString().toUpperCase())} showText />
        {error && <Alert type="error" title={error} />}</Space>
    </Modal>
    <Modal title="管理快捷色" open={dialog === 'manage'} onCancel={() => !busy && setDialog(undefined)} footer={null} closable={!busy} maskClosable={!busy}>
      <Typography.Paragraph type="secondary">修改或删除快捷色不会改变已经保存的日程。</Typography.Paragraph>
      {error && <Alert type="error" title={error} />}
      {!state.palette.colors.length && <Empty description="暂无快捷色" />}
      {state.palette.colors.map(c => <div className="calendar-color-manage-row" key={c.id}><span style={examBackgroundStyle(c.color)}>{c.name}</span>
        <Space><Button disabled={busy} onClick={() => openSave(c)}>编辑</Button><Popconfirm title="删除此快捷色？已有日程颜色不变。" onConfirm={() => remove(c.id)}>
          <Button danger disabled={busy}>删除</Button></Popconfirm></Space></div>)}
    </Modal>
  </div>
}
