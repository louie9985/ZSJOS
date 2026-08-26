import { ArrowLeftOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Input, List, Pagination, Skeleton, Space, Tag, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import LeadDetail from '../components/LeadDetail'
import { DICT_TYPE } from '../constants'
import { api, type DictData, type ManagedLead, type SubordinatePartner } from '../services/api'
import { dictionaryDisplayLabel } from '../services/leadManagement'

const statusLabel = { enabled: '启用', disabled: '停用', converted: '已转员工' }

export default function SubordinatePartnerPage() {
  const [partners, setPartners] = useState<SubordinatePartner[]>([])
  const [selected, setSelected] = useState<SubordinatePartner>()
  const [leads, setLeads] = useState<ManagedLead[]>([])
  const [detail, setDetail] = useState<ManagedLead>()
  const [keyword, setKeyword] = useState('')
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [partnerRefresh, setPartnerRefresh] = useState(0)
  const [partnerPage, setPartnerPage] = useState(1), [partnerTotal, setPartnerTotal] = useState(0)
  const [leadPage, setLeadPage] = useState(1), [leadTotal, setLeadTotal] = useState(0)
  const [loading, setLoading] = useState(false), [leadLoading, setLeadLoading] = useState(false)
  const [error, setError] = useState(''), [leadError, setLeadError] = useState('')
  const [detailId, setDetailId] = useState<number>()
  const [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState('')
  const [categories, setCategories] = useState<DictData[]>([]), [channels, setChannels] = useState<DictData[]>([])
  const partnerRequestRef = useRef(0), leadRequestRef = useRef(0), detailRequestRef = useRef(0)

  const loadPartners = useCallback(async () => {
    const requestId = ++partnerRequestRef.current
    setLoading(true); setError('')
    try {
      const result = await api.subordinatePartners({ pageNo: partnerPage, pageSize: 20, keyword: appliedKeyword || undefined })
      if (requestId !== partnerRequestRef.current) return
      setPartners(result.list); setPartnerTotal(result.total)
      setSelected(current => current && result.list.some(item => item.id === current.id) ? current : undefined)
    } catch (e) {
      if (requestId !== partnerRequestRef.current) return
      setPartners([]); setPartnerTotal(0); setError(e instanceof Error ? e.message : '下属兼职加载失败')
    } finally {
      if (requestId === partnerRequestRef.current) setLoading(false)
    }
  }, [appliedKeyword, partnerPage, partnerRefresh])

  const loadLeads = useCallback(async () => {
    if (!selected) return
    const requestId = ++leadRequestRef.current
    setLeadLoading(true); setLeadError('')
    try {
      const result = await api.subordinatePartnerLeads(selected.id, { pageNo: leadPage, pageSize: 20 })
      if (requestId !== leadRequestRef.current) return
      setLeads(result.list); setLeadTotal(result.total)
    } catch (e) {
      if (requestId !== leadRequestRef.current) return
      setLeads([]); setLeadTotal(0); setLeadError(e instanceof Error ? e.message : '兼职客资加载失败')
    } finally {
      if (requestId === leadRequestRef.current) setLeadLoading(false)
    }
  }, [leadPage, selected])

  useEffect(() => { void loadPartners() }, [loadPartners])
  useEffect(() => {
    ++leadRequestRef.current; ++detailRequestRef.current
    setLeads([]); setLeadTotal(0); setLeadError(''); setLeadLoading(false)
    setDetail(undefined); setDetailId(undefined); setDetailError(''); setDetailLoading(false)
  }, [selected?.id])
  useEffect(() => { void loadLeads() }, [loadLeads])
  useEffect(() => { void Promise.allSettled([api.dictDataByType(DICT_TYPE.LEAD_CATEGORY).then(setCategories), api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL).then(setChannels)]) }, [])
  useEffect(() => () => {
    ++partnerRequestRef.current; ++leadRequestRef.current; ++detailRequestRef.current
  }, [])

  const openLead = async (id: number) => {
    const requestId = ++detailRequestRef.current
    setDetailId(id); setDetail(undefined); setDetailLoading(true); setDetailError('')
    try {
      const result = await api.subordinatePartnerLead(id)
      if (requestId === detailRequestRef.current) setDetail(result)
    } catch (e) {
      if (requestId === detailRequestRef.current) setDetailError(e instanceof Error ? e.message : '客资详情加载失败')
    } finally {
      if (requestId === detailRequestRef.current) setDetailLoading(false)
    }
  }

  const closeDetail = () => {
    ++detailRequestRef.current
    setDetailId(undefined); setDetail(undefined); setDetailError(''); setDetailLoading(false)
  }

  const submitSearch = () => {
    setPartnerPage(1)
    setAppliedKeyword(keyword.trim())
    setPartnerRefresh(value => value + 1)
  }

  if (detailId !== undefined) return <section className="workspace-page subordinate-partner-page">
    <Space align="start"><Button icon={<ArrowLeftOutlined/>} onClick={closeDetail}>返回兼职客资</Button>{detail && <div><Typography.Title level={4}>{detail.submittedName}</Typography.Title><Typography.Text type="secondary">{detail.leadNo}</Typography.Text></div>}</Space>
    {detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => void openLead(detailId)}>重试</Button>}/>
      : detailLoading || !detail ? <Skeleton active/>
      : <LeadDetail lead={detail} categories={categories}
      categoryLabel={value => dictionaryDisplayLabel(categories, value, false)}
      channelLabel={value => dictionaryDisplayLabel(channels, value, false)}
      mode="manager-readonly" autoExpandFollowUp={false} onDirtyChange={() => undefined}
      onChanged={() => void openLead(detail.id)}/>}
  </section>

  return <section className="workspace-page subordinate-partner-page">
    <div className="subordinate-partner-layout">
      <aside className="subordinate-partner-list">
        <Space.Compact block><Input value={keyword} allowClear placeholder="搜索兼职姓名或编号" onChange={e => setKeyword(e.target.value)} onPressEnter={submitSearch}/><Button icon={<SearchOutlined/>} onClick={submitSearch}/></Space.Compact>
        {error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadPartners()}>重试</Button>}/>
          : loading ? <Skeleton active/> : partners.length ? <><List dataSource={partners} renderItem={item => <List.Item className={selected?.id === item.id ? 'is-selected' : ''} onClick={() => { setSelected(item); setLeadPage(1) }}>
            <List.Item.Meta title={<Space>{item.name}<Tag>{statusLabel[item.status]}</Tag></Space>} description={`${item.partnerNo}${item.mobile ? ` · ${item.mobile}` : ''}`}/>
          </List.Item>}/><Pagination simple current={partnerPage} pageSize={20} total={partnerTotal} onChange={setPartnerPage}/></> : <Empty description="暂无下属兼职"/>}
      </aside>
      <main className="subordinate-partner-leads">
        {!selected ? <Empty description="请选择兼职查看其全部客资"/> : <>
          <Space><div><Typography.Title level={4}>{selected.name}的客资</Typography.Title><Typography.Text type="secondary">提交时归属以每条客资保存的快照为准</Typography.Text></div><Button icon={<ReloadOutlined/>} onClick={() => void loadLeads()}/></Space>
          {leadError ? <Alert type="error" showIcon message={leadError} action={<Button size="small" onClick={() => void loadLeads()}>重试</Button>}/>
            : leadLoading ? <Skeleton active/> : leads.length ? <><List dataSource={leads} renderItem={lead => <List.Item actions={[<Button key="detail" type="link" onClick={() => void openLead(lead.id)}>查看</Button>]}>
              <List.Item.Meta title={<Space><span>{lead.submittedName}</span><Typography.Text code>{lead.leadNo}</Typography.Text></Space>} description={`提交时归属：${lead.partnerOwnerNameSnapshot || '未记录'} · ${lead.ownerUserName || '待分配销售'}`}/>
            </List.Item>}/><Pagination current={leadPage} pageSize={20} total={leadTotal} showSizeChanger={false} onChange={setLeadPage}/></> : <Empty description="该兼职暂无客资"/>}
        </>}
      </main>
    </div>
  </section>
}
