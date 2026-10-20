// UTF-8. Synthetic browser fixture; no live business responses.
import '../src/styles/index.css'
import '../src/styles/pages/class-statistics-preview.css'
import { App, Avatar, Button, Card, Divider, Input, Segmented, Select, Space, Tag, Typography } from 'antd'
import { AppstoreOutlined, CalendarOutlined, EditOutlined, FilterOutlined, GroupOutlined, ReloadOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons'
import BusinessTable from '../src/components/BusinessTable'
import { createRoot } from 'react-dom/client'
import { useMemo, useState, type ReactNode } from 'react'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'

type ClassRow = {
  id: number; classNo: string; className: string; homeroom: string; department: string; project: string
  exam: string; status: '服务中' | '已结课'; services: number; tone: 'blue' | 'purple' | 'gold' | 'green'
}

const classes: ClassRow[] = [
  { id: 1, classNo: 'BJ202610180001', className: '健康管理师秋季一班', homeroom: '林晓芸', department: '教务一组', project: '健康管理师', exam: '2026-10-26', status: '服务中', services: 39, tone: 'blue' },
  { id: 2, classNo: 'BJ202610170004', className: '公共营养师冲刺班', homeroom: '周思远', department: '教务一组', project: '公共营养师', exam: '2026-11-02', status: '服务中', services: 31, tone: 'purple' },
  { id: 3, classNo: 'BJ202610160006', className: '心理咨询师周末班', homeroom: '陈安然', department: '教务二组', project: '心理咨询师', exam: '2026-11-09', status: '服务中', services: 25, tone: 'gold' },
  { id: 4, classNo: 'BJ202610120003', className: '健康管理师提高班', homeroom: '赵子涵', department: '教务二组', project: '健康管理师', exam: '2026-11-16', status: '服务中', services: 21, tone: 'green' },
  { id: 5, classNo: 'BJ202609280011', className: '健康管理师夏季二班', homeroom: '林晓芸', department: '教务一组', project: '健康管理师', exam: '2026-09-28', status: '已结课', services: 36, tone: 'blue' },
  { id: 6, classNo: 'BJ202609260008', className: '公共营养师基础班', homeroom: '周思远', department: '教务一组', project: '公共营养师', exam: '2026-09-26', status: '已结课', services: 29, tone: 'purple' },
  { id: 7, classNo: 'BJ202609220002', className: '心理咨询师入门班', homeroom: '陈安然', department: '教务二组', project: '心理咨询师', exam: '2026-09-22', status: '已结课', services: 24, tone: 'gold' },
  { id: 8, classNo: 'BJ202609180005', className: '健康管理师周末班', homeroom: '赵子涵', department: '教务二组', project: '健康管理师', exam: '2026-09-18', status: '已结课', services: 18, tone: 'green' },
]

const avatarClass = (tone: ClassRow['tone']) => `class-management-avatar class-management-avatar-${tone}`

function ClassCard({ row }: { row: ClassRow }) {
  return <Card className="class-management-card" hoverable>
    <div className="class-management-card-head"><div className="class-management-title"><Typography.Text strong ellipsis={{ tooltip: row.className }}>{row.className}</Typography.Text><Typography.Text type="secondary">{row.classNo}</Typography.Text></div><Tag color={row.status === '服务中' ? 'green' : 'default'}>{row.status}</Tag></div>
    <Space wrap className="class-management-project"><Tag>{row.project}</Tag><Typography.Text type="secondary"><CalendarOutlined /> {row.exam}</Typography.Text></Space>
    <Divider />
    <div className="class-management-meta"><span>班主任</span><strong><Avatar size="small" className={avatarClass(row.tone)}>{row.homeroom.slice(0, 1)}</Avatar>{row.homeroom}</strong></div>
    <div className="class-management-meta"><span>所属部门</span><strong>{row.department}</strong></div>
    <div className="class-management-card-foot"><Typography.Text type="secondary">{row.services} 条课程服务</Typography.Text><Button type="link" icon={<EditOutlined />}>查看班级</Button></div>
  </Card>
}

function CardView({ rows }: { rows: ClassRow[] }) { return <div className="class-management-grid">{rows.map(row => <ClassCard key={row.id} row={row} />)}</div> }

function TableView({ rows }: { rows: ClassRow[] }) {
  return <BusinessTable<ClassRow> tableKey="class-management-preview" mode="compact" search={false} dataSource={rows} pagination={false} rowKey="id" columns={[
    { title: '班级编号', dataIndex: 'classNo', width: 170 },
    { title: '班级名称', dataIndex: 'className', width: 220, render: (value: string) => <Typography.Link>{value}</Typography.Link> },
    { title: '状态', dataIndex: 'status', width: 100, render: (value: ClassRow['status']) => <Tag color={value === '服务中' ? 'green' : 'default'}>{value}</Tag> },
    { title: '项目', dataIndex: 'project', width: 150 },
    { title: '班主任', dataIndex: 'homeroom', width: 130 },
    { title: '考期', dataIndex: 'exam', width: 130 },
    { title: '课程服务', dataIndex: 'services', width: 110, align: 'right' },
    { title: '操作', key: 'action', width: 120, render: () => <Button type="link">查看班级</Button> },
  ]} />
}

function GroupedView({ rows }: { rows: ClassRow[] }) {
  const groups = useMemo(() => rows.reduce<Record<string, ClassRow[]>>((result, row) => { (result[row.homeroom] ||= []).push(row); return result }, {}), [rows])
  return <div className="class-management-groups">{Object.entries(groups).map(([name, group]) => <section className="class-management-group" key={name}><div className="class-management-group-head"><Space><Avatar className={avatarClass(group[0].tone)}>{name.slice(0, 1)}</Avatar><div><Typography.Text strong>{name}</Typography.Text><Typography.Text type="secondary">{group[0].department}</Typography.Text></div></Space><Tag>{group.length} 个班级</Tag></div><div className="class-management-group-grid">{group.map(row => <ClassCard key={row.id} row={row} />)}</div></section>)}</div>
}

function ViewOption({ icon, label }: { icon: ReactNode; label: string }) { return <Space size={6}><span>{icon}</span>{label}</Space> }

function Preview() {
  const [view, setView] = useState('cards')
  const [status, setStatus] = useState('服务中')
  const [keyword, setKeyword] = useState('')
  const [project, setProject] = useState('全部项目')
  const [homeroom, setHomeroom] = useState('全部班主任')
  const rows = classes.filter(row => (!status || row.status === status) && (project === '全部项目' || row.project === project) && (homeroom === '全部班主任' || row.homeroom === homeroom) && (!keyword.trim() || `${row.className}${row.classNo}${row.homeroom}`.includes(keyword.trim())))
  const viewContent = view === 'cards' ? <CardView rows={rows} /> : view === 'table' ? <TableView rows={rows} /> : <GroupedView rows={rows} />
  return <main className="class-management-preview"><div className="class-management-topbar"><div><Typography.Title level={3}>班级管理</Typography.Title><Typography.Text type="secondary">查看和管理当前权限范围内的全部班级</Typography.Text></div><Space><Button icon={<ReloadOutlined />}>刷新</Button><Button type="primary">创建班级</Button></Space></div><Card className="class-management-filter" bordered={false}><div className="class-management-filter-main"><Input allowClear prefix={<SearchOutlined />} value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="搜索班级名称、编号或班主任" /><Select value={project} onChange={setProject} options={[{ value: '全部项目', label: '全部项目' }, { value: '健康管理师', label: '健康管理师' }, { value: '公共营养师', label: '公共营养师' }, { value: '心理咨询师', label: '心理咨询师' }]} /><Select value={homeroom} onChange={setHomeroom} options={[{ value: '全部班主任', label: '全部班主任' }, { value: '林晓芸', label: '林晓芸' }, { value: '周思远', label: '周思远' }, { value: '陈安然', label: '陈安然' }, { value: '赵子涵', label: '赵子涵' }]} /><Button icon={<FilterOutlined />}>更多筛选</Button></div><div className="class-management-filter-foot"><Segmented value={status} onChange={value => setStatus(String(value))} options={[{ label: '服务中', value: '服务中' }, { label: '已结课', value: '已结课' }, { label: '全部', value: '' }]} /><Typography.Text type="secondary">共 {rows.length} 个班级</Typography.Text></div></Card><div className="class-management-toolbar"><Space><Typography.Text strong>班级列表</Typography.Text><Tag color="blue">预览样例</Tag></Space><Segmented value={view} onChange={value => setView(String(value))} options={[{ label: <ViewOption icon={<AppstoreOutlined />} label="卡片" />, value: 'cards' }, { label: <ViewOption icon={<TableOutlined />} label="表格" />, value: 'table' }, { label: <ViewOption icon={<GroupOutlined />} label="按班主任" />, value: 'grouped' }]} /></div>{rows.length ? viewContent : <Card className="class-management-empty"><Typography.Text type="secondary">暂无符合条件的班级</Typography.Text></Card>}<Typography.Paragraph className="class-management-note" type="secondary">三种视图只改变班级列表的呈现方式，筛选条件、班级操作和数据权限保持一致。</Typography.Paragraph></main>
}

createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><Preview /></App></ThemeProvider></BrowserRouter>)
