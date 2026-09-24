import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Alert, Button, Modal, Space, Switch } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import { AnnouncementPanelView } from '../src/components/HomeAnnouncementPanel'
import { THEME_METAS, DENSITY_OPTIONS, FONT_SCALE_OPTIONS, type ThemePreset, type Density, type FontScale } from '../src/constants'
import type { Announcement } from '../src/services/api'
import '../src/styles/index.css'
import './home-announcement-demo.css'

const initial: Announcement[] = [
  { id: 1, title: '关于国庆假期工作安排的通知', highlighted: true, read: false, publishTime: '2026-09-24 09:30:00', type: 1, attachments: [] },
  { id: 2, title: '新版客户服务规范已发布，请及时查阅', highlighted: true, read: true, publishTime: '2026-09-23 16:00:00', type: 1, attachments: [] },
  { id: 3, title: '本周业务培训报名通知', highlighted: false, read: false, publishTime: '2026-09-24 09:00:00', type: 1, attachments: [] },
  { id: 4, title: '员工工作台功能更新说明', highlighted: false, read: false, publishTime: '2026-09-23 14:20:00', type: 1, attachments: [] },
  { id: 5, title: '办公区域日常使用温馨提示', highlighted: false, read: true, publishTime: '2026-09-22 10:00:00', type: 1, attachments: [] },
]

function Demo() {
  const theme = useTheme()
  const [items, setItems] = useState(initial)
  const [incoming, setIncoming] = useState(false)
  const [selected, setSelected] = useState<Announcement>()
  const [failure, setFailure] = useState('')
  const [detailError, setDetailError] = useState(false)
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [summaryError, setSummaryError] = useState('')
  const [hasSummary, setHasSummary] = useState(true)
  const [denied, setDenied] = useState(false)
  const [sequence, setSequence] = useState(6)
  const open = (id: number) => {
    const item = items.find(x => x.id === id)!
    setSelected(item)
    const fail = failure === 'read'
    setDetailError(fail)
    if (!fail) setItems(old => old.map(x => x.id === id ? { ...x, read: true } : x))
  }
  const refresh = () => {
    setLoading(true)
    window.setTimeout(() => {
      setLoading(false)
      if (failure === 'list') { setListError('公告加载失败（演示）'); return }
      setListError('')
      if (failure === 'summary') { setSummaryError('未读汇总暂不可用（演示）'); return }
      setSummaryError(''); setHasSummary(true)
      if (incoming) {
        const news: Announcement = { ...initial[2], id: sequence, title: '新公告：本周团队交流活动安排' }
        setSequence(n => n + 1)
        setItems(old => [...old.filter(x => x.highlighted), news, ...old.filter(x => !x.highlighted)])
        setIncoming(false)
      }
    }, 350)
  }
  const reset = () => { setItems(initial); setIncoming(false); setFailure(''); setListError(''); setSummaryError(''); setHasSummary(true); setDenied(false) }
  return <main className="notice-demo" style={{ background: theme.backgroundValue }}>
    <header className="notice-demo-heading"><span>中视健 · 员工工作台</span><span>交互预览 / 示例数据</span></header>
    <h1>重要消息，一眼看见。</h1><p>红色标记未读，蓝色突出置顶。主题来自工作台实际配置，动效持续运行。</p>
    <div className="notice-demo-controls">
      <label>UI 主题<select aria-label="UI主题" value={theme.preset} onChange={e => theme.setPreset(e.target.value as ThemePreset)}>{THEME_METAS.map(x => <option key={x.key} value={x.key}>{x.label}</option>)}</select></label>
      <label>密度<select aria-label="密度" value={theme.density} onChange={e => theme.setDensity(e.target.value as Density)}>{DENSITY_OPTIONS.map(x => <option key={x.value} value={x.value}>{x.label}</option>)}</select></label>
      <label>字号<select aria-label="字号" value={theme.fontScale} onChange={e => theme.setFontScale(e.target.value as FontScale)}>{FONT_SCALE_OPTIONS.map(x => <option key={x.value} value={x.value}>{x.label}</option>)}</select></label>
      <label>主题主色<input aria-label="主题主色" type="color" value={theme.colorPrimary} disabled={!theme.customizable} onChange={e => theme.setColorPrimary(e.target.value)} /></label>
      <label>持续动画<Switch aria-label="持续动画" checked={theme.animation} onChange={theme.setAnimation} /></label>
      <label>玻璃背景<Switch aria-label="玻璃背景" checked={Boolean(theme.backgroundValue)} onChange={v => theme.setBackground(v ? (theme.isDark ? 'ocean' : 'lavender') : 'theme')} /></label>
    </div>
    <Space wrap className="notice-demo-actions"><Button type="primary" onClick={() => setIncoming(true)}>模拟新公告</Button><Button onClick={reset}>重置演示</Button><label>验证场景 <select aria-label="验证场景" value={failure} onChange={e => {
      const v = e.target.value; setFailure(v); setDenied(v === 'denied')
      if (v === 'empty') setItems([])
      if (v === 'summary') { setHasSummary(false); setSummaryError('未读汇总暂不可用（演示）') }
    }}><option value="">正常</option><option value="read">阅读失败</option><option value="list">刷新失败</option><option value="summary">汇总失败</option><option value="empty">空列表</option><option value="denied">无权限</option></select></label></Space>
    <div className="notice-demo-grid"><AnnouncementPanelView enabled={!denied} items={items} loading={loading} error={listError}
      unreadCount={items.filter(x => !x.read).length} summaryLoading={loading} summaryError={summaryError} hasSummary={hasSummary} incoming={incoming}
      onRefresh={refresh} onRefreshSummary={refresh} onOpen={open} onAll={() => Modal.info({ title: '演示说明', content: '正式首页会进入现有公告中心，此处仅展示示例公告。' })} />
      <aside><h2>体验重点</h2><h3>01 / 红色未读</h3><p>光圈持续扩散，标签与总数呼吸。读取成功后，对应动效停止。</p><h3>02 / 蓝色置顶</h3><p>图钉轻摆、边线循环流动；无论主色如何变化，置顶始终保持蓝色。</p><h3>03 / 全主题预览</h3><p>试试深色、插画、玻璃、大字号与紧凑布局。系统减少动态效果设置仍然优先。</p><h3>04 / 新消息</h3><p>先出现提示，再由你点击更新，避免阅读时列表突然跳动。</p></aside></div>
    <footer>独立 Demo · 所有内容均为虚构示例，不连接业务接口。更改配色仅影响预览。</footer>
    <Modal open={Boolean(selected)} title={selected?.title} onCancel={() => setSelected(undefined)} footer={<Button onClick={() => setSelected(undefined)}>关闭详情</Button>}>
      {detailError ? <Alert type="error" showIcon title="阅读确认失败，仍保留未读" action={<Button onClick={() => { setFailure(''); setDetailError(false); setItems(old => old.map(x => x.id === selected?.id ? { ...x, read: true } : x)) }}>重试阅读确认</Button>} /> : <p>已模拟成功读取公告。返回后可以看到未读标识消失、未读数量减少，置顶状态保留。</p>}
      <p>这是中视健公告演示内容，不影响真实数据。</p>
    </Modal>
  </main>
}

createRoot(document.getElementById('root')!).render(<ThemeProvider><Demo /></ThemeProvider>)
