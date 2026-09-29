import { Alert, Button, Card, Empty, Input, InputNumber, Popconfirm, Select, Space, Spin, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import { mediaLeadApi, type DeptOption, type MediaOrg, type Target } from '../services/mediaLeadAnalysis'
import BusinessTable from '../components/BusinessTable'
import MediaLeadTargetHistory from './performance/MediaLeadTargetHistory'

const month = () => `${new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }).slice(0, 7)}-01`
function TargetHowTo() {
  return <Alert type="info" showIcon title="操作说明" description="先在“新媒体中心范围”选择中心并保存，System 组织树中的下级部门会自动纳入。选错中心可在下方表格点击“取消中心设置”，再选择正确的中心。然后给每位成员填写本月目标和调整原因；部门和中心默认自动汇总，也可单独输入覆盖值。修订记录只读显示每次保存前后的目标。" />
}

export default function MediaLeadTargetPage({ permissions }: { permissions: string[] }) {
  const [rows, setRows] = useState<Target[]>([]), [draft, setDraft] = useState<Record<string, number | null>>({}), [reason, setReason] = useState(''), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [orgs, setOrgs] = useState<MediaOrg[]>([]), [departments, setDepartments] = useState<DeptOption[]>([])
  const [orgDeptId, setOrgDeptId] = useState<number>()
  const editable = permissions.includes('zsjos:media-lead-target:update')
  const configurable = permissions.includes('zsjos:media-lead-target:configure')
  const load = async () => { setLoading(true); setError(''); try { const [targets, organizations, options] = await Promise.all([mediaLeadApi.targets(month()), mediaLeadApi.organizations(), configurable ? mediaLeadApi.departments() : Promise.resolve([])]); setRows(targets); setOrgs(organizations); setDepartments(options) } catch (e) { setError(e instanceof Error ? e.message : '加载失败，请重试') } finally { setLoading(false) } }
  useEffect(() => { if (permissions.includes('zsjos:media-lead-target:query')) void load() }, [])
  if (!permissions.includes('zsjos:media-lead-target:query')) return <Alert type="warning" showIcon title="暂无客资引流人数指标设置权限" />
  const save = async () => { if (!reason.trim()) { setError('请填写调整原因'); return } const items = rows.filter(x => draft[`${x.scopeType}:${x.scopeId}`] !== undefined).map(x => { const value = draft[`${x.scopeType}:${x.scopeId}`]; return { scopeType: x.scopeType, scopeId: x.scopeId, periodStart: month(), targetCount: value == null ? undefined : value, version: x.version, reason, restoreAutomatic: value === null } }); try { await mediaLeadApi.saveTargets(items); setDraft({}); setReason(''); message.success('指标已保存'); void load() } catch (e) { setError(e instanceof Error ? e.message : '保存失败，请重试') } }
  const saveOrg = async () => {
    if (orgDeptId == null) return
    const current = orgs.find(x => x.kind === 'CENTER' && x.deptId === orgDeptId)
    try { await mediaLeadApi.saveOrganization({ deptId: orgDeptId, centerId: orgDeptId, kind: 'CENTER', version: current?.version }); message.success('新媒体中心已保存'); await load() }
    catch (e) { setError(e instanceof Error ? e.message : '组织配置失败，请重试') }
  }
  const unsetOrg = async (org: MediaOrg) => {
    if (org.version == null) return
    setError('')
    try {
      await mediaLeadApi.unsetOrganization({ deptId: org.deptId, version: org.version })
      if (orgDeptId === org.deptId) setOrgDeptId(undefined)
      message.success('已取消中心设置')
      await load()
    } catch (e) { setError(e instanceof Error ? e.message : '取消中心设置失败，请重试') }
  }
  return <div className="performance-page"><Space direction="vertical" size="large" style={{ width: '100%' }}><div className="performance-heading"><Typography.Title level={3} style={{ margin: 0 }}>客资引流人数指标设置</Typography.Title><Button onClick={load}>刷新</Button></div>{error && <Alert type="error" showIcon title={error} action={<Button size="small" onClick={load}>重试</Button>} />}{loading ? <Card><Spin /></Card> : <><Card title="本月引流人数目标"><Typography.Paragraph type="secondary">部门和中心来自 System 组织树；部门汇总成员目标，中心汇总其下级部门目标；人工设置可覆盖汇总值。</Typography.Paragraph>{rows.length === 0 ? <Empty description="当前数据范围没有新媒体运营、编导或已配置组织" /> : <BusinessTable tableKey="media-lead-targets" mode="compact" columnMode="native" rowKey={x => `${x.scopeType}:${x.scopeId}`} scroll={{ x: 740 }} pagination={{ pageSize: 20 }} dataSource={rows} columns={[{ title: '对象类型', dataIndex: 'scopeType', render: (v: string) => ({ USER: '成员', DEPT: '部门', CENTER: '中心' })[v as 'USER' | 'DEPT' | 'CENTER'] ?? v }, { title: '对象', dataIndex: 'name' }, { title: '本月有效客资目标', render: (_: unknown, row: Target) => editable ? <InputNumber min={0} precision={0} value={draft[`${row.scopeType}:${row.scopeId}`] === null ? undefined : draft[`${row.scopeType}:${row.scopeId}`] ?? row.targetCount ?? undefined} placeholder="自动汇总" onChange={v => setDraft(old => ({ ...old, [`${row.scopeType}:${row.scopeId}`]: v ?? 0 }))} /> : row.targetCount ?? '未设置' }, { title: '来源', render: (_: unknown, row: Target) => draft[`${row.scopeType}:${row.scopeId}`] === null ? '待恢复自动汇总' : row.manual ? '人工设置' : row.targetCount == null ? '未设置' : '自动汇总' }, { title: '操作', render: (_: unknown, row: Target) => <Space size="small"><MediaLeadTargetHistory target={row} />{editable && row.scopeType !== 'USER' && row.manual ? <Button size="small" onClick={() => setDraft(old => ({ ...old, [`${row.scopeType}:${row.scopeId}`]: null }))}>恢复自动汇总</Button> : null}</Space> }]} />}{editable && <Space direction="vertical" style={{ width: '100%', marginTop: 16 }}><Input.TextArea value={reason} onChange={e => setReason(e.target.value)} placeholder="调整原因（必填）" maxLength={500} /><Button type="primary" disabled={!Object.keys(draft).length} onClick={save}>保存 {Object.keys(draft).length} 项</Button></Space>}</Card>{configurable && <Card title="新媒体中心范围"><Typography.Paragraph type="secondary">选择 System 中心部门，其下级部门自动纳入；选错后可在表格中取消中心设置。</Typography.Paragraph><Space wrap><Select showSearch optionFilterProp="label" placeholder="选择新媒体中心" style={{ width: 220 }} value={orgDeptId} onChange={setOrgDeptId} options={departments.map(x => ({ label: x.name, value: x.id }))} /><Button type="primary" disabled={orgDeptId == null} onClick={saveOrg}>保存中心</Button></Space><BusinessTable tableKey="media-lead-orgs" mode="compact" columnMode="native" rowKey={x => x.kind + ":" + x.deptId} scroll={{ x: 600 }} pagination={false} dataSource={orgs} columns={[{ title: '组织', dataIndex: 'name' }, { title: '类型', dataIndex: 'kind', render: (v: string) => v === 'CENTER' ? '中心' : '部门' }, { title: '所属中心', dataIndex: 'centerName' }, { title: '操作', render: (_: unknown, org: MediaOrg) => org.kind === 'CENTER' && org.version != null ? <Popconfirm title={'取消“' + org.name + '”的中心设置？'} description="只撤销中心标记，客资和目标不会删除。" okText="取消设置" cancelText="返回" onConfirm={() => void unsetOrg(org)}><Button type="link" size="small">取消中心设置</Button></Popconfirm> : null }]} /></Card>}</>}<TargetHowTo /></Space></div>
}
