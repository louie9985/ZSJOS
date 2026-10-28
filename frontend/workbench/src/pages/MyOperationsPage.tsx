import { useEffect, useState } from 'react'
import { Alert, Button, Empty, Input, Skeleton, Space, Tabs, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, ReloadOutlined, TeamOutlined } from '@ant-design/icons'
import { myOperationsApi, type OperationsMember } from '../services/myOperations'
import LeadManagementPage from './LeadManagementPage'
import { api, type DictData } from '../services/api'
import { DICT_TYPE } from '../constants'

export default function MyOperationsPage({ permissions }: { permissions: string[] }) {
  const authorized = permissions.includes('zsjos:my-operations:query')
  const [members, setMembers] = useState<OperationsMember[]>([])
  const [selected, setSelected] = useState<OperationsMember>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [statuses, setStatuses] = useState<DictData[]>([])
  const [statusError, setStatusError] = useState('')
  const [statusLoading, setStatusLoading] = useState(true)
  useEffect(() => {
    let active = true
    setSelected(undefined)
    setMembers([])
    setError('')
    setLoading(authorized)
    setStatuses([])
    setStatusError('')
    setStatusLoading(authorized)
    if (!authorized) return
    api.dictDataByType(DICT_TYPE.COMMON_STATUS).then(rows => { if (active) setStatuses(rows) })
      .catch(() => { if (active) setStatusError('账号状态名称加载失败') })
      .finally(() => { if (active) setStatusLoading(false) })
    myOperationsApi.members().then(rows => { if (active) setMembers(rows) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '运营团队加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [authorized, reload])

  if (!authorized) return <Alert type="warning" title="暂无我的运营查看权限" />
  const accountStatusLabel = (status: number) => statuses.find(item => item.value === String(status))?.label
    || (statusLoading ? '状态加载中' : statusError ? '状态暂不可用' : '状态名称未配置')
  const visible = members.filter(row => (row.name || '').includes(keyword.trim()))
  return <section className="workspace-page subordinate-sales-page">
    <div className="subordinate-toolbar">
      <Space wrap>
        <Input.Search aria-label="搜索运营姓名" allowClear placeholder="搜索运营姓名" onSearch={value => { setKeyword(value); setSelected(undefined) }} />
        <Typography.Text type="secondary">共 {visible.length} 位运营</Typography.Text>
      </Space>
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setReload(value => value + 1)}>刷新团队</Button>
    </div>
    {error && <Alert className="subordinate-page-error" type="error" showIcon title={error}
      action={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />}
    {statusError && <Alert className="subordinate-page-error" type="warning" title={statusError}
      action={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />}
    <div className={`subordinate-inbox-layout ${selected ? 'show-detail' : 'show-list'}`}>
      <aside className="subordinate-sales-list-pane" aria-label="运营成员">
        <div className="subordinate-sales-list">
          {loading ? <Skeleton active /> : visible.map(row => <button type="button"
            className={`subordinate-sales-item ${selected?.userId === row.userId ? 'active' : ''}`}
            aria-pressed={selected?.userId === row.userId} key={row.userId} onClick={() => setSelected(row)}>
            <div className="subordinate-sales-item-title"><strong>{row.name || '姓名未配置'}</strong><Tag>{accountStatusLabel(row.accountStatus)}</Tag></div>
            <div className="subordinate-sales-item-summary"><span>名下客资</span><strong>{row.leadCount} 条</strong></div>
          </button>)}
          {!loading && !error && !visible.length && <Empty image={<TeamOutlined />}
            description={keyword ? '没有匹配的运营' : '暂无关联运营，请检查编导与运营关联配置'} />}
        </div>
      </aside>
      <main className="subordinate-sales-detail-pane" aria-label="运营详情">
        {selected ? <div className="subordinate-sales-detail" key={selected.userId}>
          <div className="subordinate-detail-heading">
            <Button className="subordinate-mobile-back" icon={<ArrowLeftOutlined />} onClick={() => setSelected(undefined)}>返回运营团队</Button>
            <div className="subordinate-detail-title"><Typography.Title level={4}>{selected.name || '姓名未配置'}</Typography.Title></div>
          </div>
          <Tabs defaultActiveKey="leads" items={[
            { key: 'overview', label: '员工概况', children: <Space direction="vertical">
              <Typography.Text>账号状态：{accountStatusLabel(selected.accountStatus)}</Typography.Text>
              <Typography.Text>名下客资：{selected.leadCount} 条</Typography.Text>
              <Typography.Text type="secondary">包含本人提交、销售或教务代录并归属于该运营的客资。</Typography.Text>
            </Space> },
            { key: 'leads', label: '名下客资', children: <LeadManagementPage permissions={permissions} operationsUserId={selected.userId} /> },
          ]} />
        </div> : <Empty image={<TeamOutlined />} description="请选择运营查看名下客资" />}
      </main>
    </div>
  </section>
}
