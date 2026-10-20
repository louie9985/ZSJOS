import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Card, Divider, Input, Space, Switch, Tag, Tooltip, Typography } from 'antd'
import { EditOutlined, HistoryOutlined, LockOutlined, SettingOutlined } from '@ant-design/icons'
import { BrowserRouter } from 'react-router-dom'
import ResourceLink from '../src/components/ResourceLink'
import AttachmentCard from '../src/components/AttachmentCard'
import '../src/styles/index.css'
import './exam-certificate-layout-preview.css'

type LayoutKey = 'cards' | 'compact' | 'timeline'
type Field = { label: string; value: string; kind?: 'link' | 'attachment'; editable?: boolean }

const registration: Field[] = [
  { label: '学员姓名', value: '张同学' },
  { label: '联系电话', value: '138****8821' },
  { label: '报考项目', value: '健康管理师（三级）' },
  { label: '报考地区', value: '北京市 · 朝阳区' },
  { label: '证书邮寄地址', value: '北京市朝阳区示例路 18 号', editable: true },
  { label: '报名资料链接', value: 'https://example.com/registration/张同学', kind: 'link', editable: true },
]

const exam: Field[] = [
  { label: '对接渠道', value: '教务转介绍', editable: true },
  { label: '报名费', value: '¥ 1,280', editable: true },
  { label: '准考证', value: '准考证-2026-04.pdf', kind: 'attachment', editable: true },
  { label: '理论', value: '2026-04-18 09:00 · 北京考点', editable: true },
  { label: '技能', value: '待安排', editable: true },
  { label: '论文 / 综合评审', value: '待提交', editable: true },
  { label: '评定结果', value: '未评定', editable: true },
  { label: '发证单位', value: '北京市职业技能鉴定中心', editable: true },
  { label: '证书图片', value: '2 个附件', kind: 'attachment', editable: true },
  { label: '证书邮寄单号', value: '—', editable: true },
]

const history = [
  ['2026-04-08 15:42', '李教务', '补充报名地址，更新理论考试时间'],
  ['2026-04-03 09:18', '王考务', '上传准考证并记录报名费'],
  ['2026-03-28 17:05', '张同学', '提交报名信息'],
]

function EditMarker({ label, onClick }: { label: string; onClick: () => void }) {
  return <Tooltip title={`修改${label}`}><Button className="field-edit" type="text" size="small" icon={<EditOutlined />} aria-label={`修改${label}`} onClick={onClick} /></Tooltip>
}

function ResourceValue({ field }: { field: Field }) {
  if (field.kind === 'link') return <ResourceLink href={field.value} title="查看报名资料" variant="resource" />
  if (field.kind === 'attachment') return <AttachmentCard name={field.value} load={async () => ({ name: field.value, type: 'application/pdf', size: 238000, url: `https://files.example.com/${encodeURIComponent(field.value)}` })} />
  return <span className={field.value === '—' ? 'muted' : undefined}>{field.value}</span>
}

function FieldRow({ field, onEdit, dense = false }: { field: Field; onEdit: (field: Field) => void; dense?: boolean }) {
  return <div className={`preview-field ${dense ? 'preview-field-dense' : ''}`}>
    <span className="preview-field-label">{field.label}</span>
    <span className="preview-field-value"><ResourceValue field={field} /></span>
    {field.editable && <EditMarker label={field.label} onClick={() => onEdit(field)} />}
  </div>
}

function HistoryBlock({ compact = false }: { compact?: boolean }) {
  const [open, setOpen] = useState(!compact)
  return <div className={`history-block ${compact ? 'history-block-compact' : ''}`}>
    <button className="history-heading" type="button" onClick={() => setOpen(value => !value)}>
      <span><HistoryOutlined /> 修改历史</span><span className="history-count">{history.length} 条 {open ? '收起' : '查看'}</span>
    </button>
    {open && <div className="history-list">{history.map(([time, operator, reason]) => <div className="history-item" key={`${time}-${operator}`}><span className="history-dot" /><div><strong>{reason}</strong><small>{time} · {operator}</small></div></div>)}</div>}
  </div>
}

function VisibilitySettings() {
  const [visible, setVisible] = useState(true)
  return <Card className="visibility-card" size="small">
    <div className="visibility-title"><span><SettingOutlined /> 考试信息设置</span><Switch checked={visible} onChange={setVisible} checkedChildren="教务可见" unCheckedChildren="仅考务可见" /></div>
    <p className="visibility-copy">控制考试信息字段是否展示给教务人员。</p>
    {!visible && <div className="visibility-locked"><LockOutlined /> 关闭后仅以下角色可见：<Tag>考务主管</Tag><Tag>考务专员</Tag><Tag>管理员</Tag></div>}
    {visible && <div className="visibility-open">当前状态：教务可查看考试信息；字段修改仍由后端权限校验。</div>}
  </Card>
}

function EditPreview({ field, onClose }: { field?: Field; onClose: () => void }) {
  if (!field) return null
  return <div className="edit-popover"><div><strong>修改{field.label}</strong><Button type="text" size="small" onClick={onClose}>关闭</Button></div><Input defaultValue={field.value} autoFocus /><Space><Button size="small" onClick={onClose}>取消</Button><Button size="small" type="primary" onClick={onClose}>保存此字段</Button></Space></div>
}

function CardsLayout() {
  const [editing, setEditing] = useState<Field>()
  return <div className="layout-content cards-layout">
    <Card className="preview-card registration-card" title={<SectionTitle eyebrow="01 · 报名资料" title="报名信息" hint="先确认报名事实，再维护考试结果" />} extra={<Tag color="blue">可逐字段修改</Tag>}>
      <div className="field-grid">{registration.map(field => <FieldRow key={field.label} field={field} onEdit={setEditing} />)}</div>
    </Card>
    <Card className="preview-card exam-card" title={<SectionTitle eyebrow="02 · 考试结果" title="考试信息" hint="按考试流程展示，避免与报名资料混排" />} extra={<Tag>最近更新 2026-04-08</Tag>}>
      <div className="field-grid">{exam.map(field => <FieldRow key={field.label} field={field} onEdit={setEditing} />)}</div>
      <VisibilitySettings />
    </Card>
    <HistoryBlock />
    <EditPreview field={editing} onClose={() => setEditing(undefined)} />
  </div>
}

function CompactLayout() {
  const [editing, setEditing] = useState<Field>()
  return <div className="layout-content compact-layout">
    <Card className="preview-card registration-card" title={<SectionTitle eyebrow="报名资料" title="报名信息" hint="摘要 + 字段明细" />}>
      <div className="summary-strip"><div><small>报考项目</small><strong>健康管理师（三级）</strong></div><div><small>报名状态</small><Tag color="green">资料已提交</Tag></div><div><small>资料更新时间</small><strong>2026-04-08</strong></div></div>
      <Divider />
      <div className="field-grid compact-grid">{registration.map(field => <FieldRow dense key={field.label} field={field} onEdit={setEditing} />)}</div>
    </Card>
    <Card className="preview-card exam-card" title={<SectionTitle eyebrow="考试结果" title="考试信息" hint="重要状态置顶，字段保持可追溯" />}>
      <div className="exam-status-strip"><span className="status-dot" /><strong>报名已确认</strong><span>理论考试：2026-04-18</span><span>技能：待安排</span></div>
      <div className="field-grid compact-grid">{exam.map(field => <FieldRow dense key={field.label} field={field} onEdit={setEditing} />)}</div>
      <VisibilitySettings />
    </Card>
    <HistoryBlock compact />
    <EditPreview field={editing} onClose={() => setEditing(undefined)} />
  </div>
}

function TimelineLayout() {
  const [editing, setEditing] = useState<Field>()
  return <div className="layout-content timeline-layout">
    <Card className="preview-card registration-card" title={<SectionTitle eyebrow="报名信息" title="报名资料" hint="以提交快照为主，变更点靠字段右侧标记" />}>
      <div className="registration-hero"><div className="avatar">张</div><div><strong>张同学</strong><span>健康管理师（三级） · 北京市</span></div><Tag color="green">资料已提交</Tag></div>
      <div className="field-grid">{registration.map(field => <FieldRow key={field.label} field={field} onEdit={setEditing} />)}</div>
    </Card>
    <Card className="preview-card exam-card" title={<SectionTitle eyebrow="考试信息" title="考试与证书" hint="结果按时间线推进，附件和链接统一呈现" />}>
      <div className="exam-timeline">{[['报名确认','2026-03-28','已完成'],['理论考试','2026-04-18','已预约'],['技能考试','待安排','待处理'],['证书发放','待评定','待处理']].map(([title, time, state], index) => <div className={`timeline-step ${index < 2 ? 'done' : ''}`} key={title}><span className="timeline-line" /><span className="timeline-node">{index + 1}</span><div><strong>{title}</strong><small>{time} · {state}</small></div></div>)}</div>
      <Divider />
      <div className="field-grid">{exam.slice(0, 6).map(field => <FieldRow key={field.label} field={field} onEdit={setEditing} />)}</div>
      <details className="timeline-more"><summary>展开证书字段（4）</summary><div className="field-grid">{exam.slice(6).map(field => <FieldRow key={field.label} field={field} onEdit={setEditing} />)}</div></details>
      <VisibilitySettings />
    </Card>
    <HistoryBlock />
    <EditPreview field={editing} onClose={() => setEditing(undefined)} />
  </div>
}

function SectionTitle({ eyebrow, title, hint }: { eyebrow: string; title: string; hint: string }) {
  return <div className="section-title"><small>{eyebrow}</small><strong>{title}</strong><span>{hint}</span></div>
}

function Preview() {
  const [layout, setLayout] = useState<LayoutKey>('cards')
  const content = layout === 'cards' ? <CardsLayout /> : layout === 'compact' ? <CompactLayout /> : <TimelineLayout />
  return <App><main className="preview-shell">
    <header className="preview-header"><div><Typography.Title level={3}>考试与报名信息布局预览</Typography.Title><Typography.Text type="secondary">三种方案都将报名信息置于上方，考试信息置于下方；点击字段右侧铅笔查看逐字段维护交互。</Typography.Text></div><Tag color="processing">预览数据</Tag></header>
    <nav className="layout-switcher" aria-label="布局方案"><span>选择布局</span>{([['cards','方案 A · 分组卡片'],['compact','方案 B · 紧凑摘要'],['timeline','方案 C · 进度时间线']] as [LayoutKey,string][]).map(([key, label]) => <Button key={key} type={layout === key ? 'primary' : 'default'} onClick={() => setLayout(key)}>{label}</Button>)}</nav>
    <div className="design-note"><strong>{layout === 'cards' ? '推荐：信息层级最清晰' : layout === 'compact' ? '适合高频查看：页面更紧凑' : '适合过程管理：考试阶段更直观'}</strong><span>修改历史始终保留，版本号不作为主标题展示。</span></div>
    {content}
  </main></App>
}

createRoot(document.getElementById('root')!).render(<BrowserRouter><Preview /></BrowserRouter>)
