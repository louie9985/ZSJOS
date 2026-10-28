import { ArrowDownOutlined, ArrowUpOutlined, HolderOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Flex, Modal, Spin, Switch, Typography, theme } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { getNavigationPreference, saveNavigationPreference } from '../services/navigationPreference'
import { applyNavigationOrder, visibleNavigation, findNavigationLevel, navigationKey, setSiblingOrder, setNavigationVisible, isNavigationVisible, type NavigationNode, type NavigationOrder } from '../services/navigationOrder'

type Props = { open: boolean; menus: NavigationNode[]; canSave: boolean; onClose: () => void }
export default function NavigationOrderModal({ open, menus, canSave, onClose }: Props) {
  const { token } = theme.useToken()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [draft, setDraft] = useState<NavigationOrder>({ groups: [] })
  const [original, setOriginal] = useState('')
  const [path, setPath] = useState<string[]>([])
  const [attempt, setAttempt] = useState(0)
  const dragged = useRef<string | null>(null)
  useEffect(() => {
    if (!open) return
    let active = true
    setLoading(true); setError(''); setOriginal(''); setPath([])
    getNavigationPreference().then(value => {
      if (active) { setDraft(value); setOriginal(JSON.stringify(value)) }
    }).catch(() => { if (active) setError('导航设置读取失败，请重试。') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [open, attempt])
  const tree = useMemo(() => applyNavigationOrder(visibleNavigation(menus), draft, 'root', true), [menus, draft])
  const rows = findNavigationLevel(tree, path)
  const dirty = JSON.stringify(draft) !== original
  function move(from: number, to: number) {
    if (!canSave || saving || from < 0 || to < 0 || to >= rows.length) return
    const keys = rows.map(navigationKey)
    keys.splice(to, 0, keys.splice(from, 1)[0])
    setDraft(value => setSiblingOrder(value, path.join('/') || 'root', keys))
  }
  async function save() {
    if (saving || !dirty || !canSave) return
    setSaving(true); setError('')
    try { await saveNavigationPreference(draft); onClose(); window.location.reload() }
    catch { setError('保存失败，调整内容已保留，请重试。') }
    finally { setSaving(false) }
  }
  return <Modal title="导航栏调整" open={open} width={620} onCancel={() => { if (!saving) onClose() }} closable={!saving} keyboard={!saving} maskClosable={false}
    footer={<Flex justify="space-between" gap="small" wrap><Button disabled={!canSave || saving || loading || !original} onClick={() => setDraft({ groups: [] })}>恢复默认</Button><Flex gap="small"><Button disabled={saving} onClick={onClose}>取消</Button><Button type="primary" loading={saving} disabled={!canSave || loading || !original || !dirty} onClick={() => void save()}>保存并关闭</Button></Flex></Flex>}>
    <Spin spinning={loading}><Flex vertical gap="small">
      {error && <Alert type="error" title={error} action={!original ? <Button onClick={() => setAttempt(value => value + 1)}>重试</Button> : undefined} />}
      {!loading && original && <>
        <Flex justify="space-between" align="center"><Typography.Text>{path.length ? '子菜单顺序' : '一级菜单顺序'}</Typography.Text>{path.length > 0 && <Button type="link" onClick={() => setPath(value => value.slice(0, -1))}>返回上一级</Button>}</Flex>
        <Typography.Text type="secondary">拖动或使用箭头排序，关闭开关可隐藏菜单；仅对我生效。</Typography.Text>
        {!rows.length && <Empty description="暂无可调整的导航菜单" />}
        {rows.map((row, index) => <div key={navigationKey(row)} draggable={canSave && !saving}
          onDragStart={event => { dragged.current = navigationKey(row); event.dataTransfer.setData('text/plain', dragged.current); event.dataTransfer.effectAllowed = 'move' }}
          onDragEnd={() => { dragged.current = null }} onDragOver={event => event.preventDefault()}
          onDrop={event => { event.preventDefault(); move(rows.findIndex(item => navigationKey(item) === dragged.current), index); dragged.current = null }}
          style={{ display: 'flex', alignItems: 'center', gap: token.marginXS, padding: token.paddingXS, border: '1px solid ' + token.colorBorderSecondary, borderRadius: token.borderRadius }}>
          <Switch size="small" aria-label={'显示' + row.name} checkedChildren="显示" unCheckedChildren="隐藏" disabled={!canSave || saving}
            checked={isNavigationVisible(draft, path.join('/') || 'root', navigationKey(row))}
            onChange={checked => setDraft(value => setNavigationVisible(value, path.join('/') || 'root', navigationKey(row), checked))} />
          <HolderOutlined style={{ color: token.colorTextSecondary, cursor: 'grab' }} />
          <span style={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>{row.name}</span>
          {!!row.children?.length && <Button type="link" size="small" disabled={saving} onClick={() => setPath(value => [...value, navigationKey(row)])}>子菜单</Button>}
          <Button type="text" size="small" icon={<ArrowUpOutlined />} aria-label={'上移' + row.name} disabled={!canSave || saving || index === 0} onClick={() => move(index, index - 1)} />
          <Button type="text" size="small" icon={<ArrowDownOutlined />} aria-label={'下移' + row.name} disabled={!canSave || saving || index === rows.length - 1} onClick={() => move(index, index + 1)} />
        </div>)}
        <Typography.Text type="secondary">保存成功后关闭弹窗，并自动刷新当前页面。</Typography.Text>
      </>}
    </Flex></Spin>
  </Modal>
}
