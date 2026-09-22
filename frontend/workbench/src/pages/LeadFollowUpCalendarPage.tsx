import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Calendar, DatePicker, Empty, Modal, Pagination, Select, Space, Spin, Tag, Typography } from 'antd'
import { LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, type DictData, type ManagedLead } from '../services/api'
import { leadCalendarApi, type CalendarCard, type CalendarDay, type CalendarDirection, type CalendarSort } from '../services/leadCalendar'
import { DICT_TYPE } from '../constants'
import { formatTimestamp } from '../services/time'
import LeadDetail from '../components/LeadDetail'
import FollowUpModal from '../components/FollowUpModal'
import '../styles/pages/lead-follow-up-calendar.css'

const errorText = (error: unknown) => error instanceof Error ? error.message : '加载失败，请重试'
const allowed = (permissions: string[], permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)

export default function LeadFollowUpCalendarPage({ permissions }: { permissions: string[] }) {
  const { modal } = App.useApp()
  const canView = allowed(permissions, 'zsjos:lead-follow-up-calendar:query') && allowed(permissions, 'zsjos:lead:query')
  const [anchor, setAnchor] = useState(dayjs())
  const [days, setDays] = useState<CalendarDay[]>([])
  const [day, setDay] = useState<Dayjs>()
  const [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [cards, setCards] = useState<CalendarCard[]>([]), [total, setTotal] = useState(0)
  const [cardLoading, setCardLoading] = useState(false), [cardError, setCardError] = useState('')
  const [page, setPage] = useState(1), [sort, setSort] = useState<CalendarSort>('deadline')
  const [direction, setDirection] = useState<CalendarDirection>('asc')
  const [revision, setRevision] = useState(0)
  const [detail, setDetail] = useState<ManagedLead>(), [followUp, setFollowUp] = useState<ManagedLead>()
  const [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState('')
  const [detailId, setDetailId] = useState<number>(), [detailDirty, setDetailDirty] = useState(false)
  const [categories, setCategories] = useState<DictData[]>([]), [channels, setChannels] = useState<DictData[]>([])
  const detailSequence = useRef(0)
  const start = anchor.startOf('month').startOf('week').format('YYYY-MM-DD')
  const end = dayjs(start).add(42, 'day').format('YYYY-MM-DD')
  const selectedDate = day?.format('YYYY-MM-DD')

  useEffect(() => {
    if (!canView) return
    const controller = new AbortController()
    setLoading(true); setError(''); setDays([])
    leadCalendarApi.days(start, end, controller.signal).then(data => { if (!controller.signal.aborted) setDays(data) })
      .catch(cause => { if (!controller.signal.aborted) setError(errorText(cause)) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [start, end, revision, canView])

  useEffect(() => {
    if (!selectedDate || !canView) return
    const controller = new AbortController()
    setCardLoading(true); setCardError(''); setCards([])
    leadCalendarApi.cards({ start: selectedDate, end: dayjs(selectedDate).add(1, 'day').format('YYYY-MM-DD'), pageNo: page, pageSize: 24, sort, direction }, controller.signal)
      .then(data => {
        if (controller.signal.aborted) return
        if (!data.list.length && data.total > 0 && page > 1) { setPage(Math.ceil(data.total / 24)); return }
        setCards(data.list); setTotal(data.total)
      }).catch(cause => { if (!controller.signal.aborted) setCardError(errorText(cause)) })
      .finally(() => { if (!controller.signal.aborted) setCardLoading(false) })
    return () => controller.abort()
  }, [selectedDate, page, sort, direction, revision, canView])

  const loadDetail = useCallback(async (id: number) => {
    const sequence = ++detailSequence.current
    setDetailLoading(true); setDetailError(''); setDetail(undefined)
    try {
      const [lead, categoryRows, channelRows] = await Promise.all([api.managedLead(id), api.dictDataByType(DICT_TYPE.LEAD_CATEGORY), api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)])
      if (sequence !== detailSequence.current) return
      setDetail(lead); setCategories(categoryRows); setChannels(channelRows)
    } catch (cause) { if (sequence === detailSequence.current) setDetailError(errorText(cause)) }
    finally { if (sequence === detailSequence.current) setDetailLoading(false) }
  }, [])
  const refresh = () => { setRevision(value => value + 1); if (detailId) void loadDetail(detailId) }
  const closeDetail = () => {
    const close = () => { ++detailSequence.current; setDetailId(undefined); setDetail(undefined); setDetailDirty(false) }
    if (detailDirty) modal.confirm({ title: '关闭详情将丢失尚未提交的内容，确定关闭？', onOk: close })
    else close()
  }

  if (!canView) return <Alert type="warning" showIcon title="无权查看销售客资跟进日历，请联系管理员配置权限" />
  return <section className="workspace-page lead-calendar-page">
    <div className="page-heading"><div><Typography.Title level={4}>销售客资跟进日历</Typography.Title><Typography.Text type="secondary">我的待跟进客资 · 按跟进截止日期展示</Typography.Text></div><Button icon={<ReloadOutlined />} onClick={refresh}>刷新</Button></div>
    {error ? <Alert type="error" showIcon title={error} action={<Button onClick={refresh}>重试</Button>} /> : <Spin spinning={loading}>
      <Calendar value={anchor} mode="month" onPanelChange={setAnchor}
        headerRender={() => <div className="lead-calendar-month"><Space wrap>
          <Button aria-label="上个月" icon={<LeftOutlined />} onClick={() => setAnchor(value => value.subtract(1, 'month'))} />
          <DatePicker picker="month" value={anchor} allowClear={false} aria-label="选择月份" onChange={value => value && setAnchor(value)} />
          <Button aria-label="下个月" icon={<RightOutlined />} onClick={() => setAnchor(value => value.add(1, 'month'))} />
          <Button onClick={() => setAnchor(dayjs())}>今天</Button>
        </Space></div>}
        onSelect={(date, info) => { if (info.source === 'date' && !loading) { setDay(date); setPage(1); setCards([]); setTotal(0) } }}
        cellRender={(date, info) => {
          if (info.type !== 'date') return info.originNode
          const count = days.find(item => item.date === date.format('YYYY-MM-DD'))?.count
          return count ? <span className="lead-calendar-count" aria-label={`${date.format('YYYY-MM-DD')} 待跟进 ${count} 个客资`}>{count}</span> : null
        }} />
      {!loading && !days.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前日历范围暂无待跟进客资" />}
    </Spin>}
    <Modal title={`${day?.format('YYYY年M月D日')} · 待跟进客资`} open={Boolean(day)} onCancel={() => setDay(undefined)} footer={null}
      mask={{ closable: false }} width="min(1440px, calc(100vw - 32px))" destroyOnHidden className="lead-calendar-modal">
      <div className="lead-calendar-toolbar"><Typography.Text type="secondary">共 {total} 个客资</Typography.Text><Space wrap>
        <Select aria-label="客资排序字段" value={sort} options={[{ value: 'deadline', label: '跟进截止时间' }, { value: 'category', label: '客资分类等级' }]} onChange={value => { setSort(value); setPage(1) }} />
        <Select aria-label="排序方向" value={direction} options={[{ value: 'asc', label: sort === 'category' ? 'S级优先' : '最早优先' }, { value: 'desc', label: '倒序' }]} onChange={value => { setDirection(value); setPage(1) }} />
        <Button icon={<ReloadOutlined />} onClick={refresh}>刷新</Button>
      </Space></div>
      {cardError ? <Alert type="error" showIcon title={cardError} action={<Button onClick={refresh}>重试</Button>} /> : <Spin spinning={cardLoading}>
        <div className="lead-calendar-grid">{cards.map(card => {
          const { lead, lastFollowUp } = card
          const canFollow = allowed(permissions, 'zsjos:lead-follow-up:create') && lead.availableActions?.some(action => action.code === 'ADD_FOLLOW_UP' && action.enabled)
          return <article className="lead-calendar-card" key={lead.id}>
            <div className="lead-calendar-card-body"><Typography.Title level={5}>{lead.submittedName || '未填写姓名'}</Typography.Title>
              <div>手机号：<Typography.Text copyable={lead.submittedMobile ? { text: lead.submittedMobile } : false}>{lead.submittedMobile || '未填写'}</Typography.Text></div>
              <div>微信号：<Typography.Text copyable={lead.submittedWechatId ? { text: lead.submittedWechatId } : false}>{lead.submittedWechatId || '未填写'}</Typography.Text></div>
              <Space wrap><Tag>{lead.leadCategoryLabelSnapshot || '未记录分类'}</Tag><Tag color="blue">{lead.salesStageLabelSnapshot || '未记录销售阶段'}</Tag></Space>
              <Typography.Text type="secondary">跟进截止：{formatTimestamp(card.deadline)}</Typography.Text>
              <div className="lead-calendar-last"><strong>上次跟进</strong>{!card.canReadFollowUp ? <p>暂无跟进记录查看权限</p> : lastFollowUp ? <>
                <div>{formatTimestamp(lastFollowUp.occurredAt)} · {lastFollowUp.operatorName || '未记录跟进人'}</div>
                <div>{[lastFollowUp.methodLabel, lastFollowUp.resultLabel].filter(Boolean).join(' · ') || '未记录方式或结果'}</div>
                <p>{lastFollowUp.remark || '未填写备注'}</p>
              </> : <p>暂无跟进记录</p>}</div>
            </div>
            <div className="lead-calendar-actions"><Button type="text" onClick={() => { setDetailId(lead.id); void loadDetail(lead.id) }}>查看详情</Button><Button type="text" disabled={!canFollow} onClick={() => setFollowUp(lead)}>填写跟进记录</Button></div>
          </article>
        })}</div>
        {!cardLoading && !cards.length && <Empty description="当天暂无待跟进客资" />}
        {total > 24 && <Pagination current={page} pageSize={24} total={total} showSizeChanger={false} onChange={setPage} />}
      </Spin>}
    </Modal>
    <Modal title="客资详情" open={Boolean(detailId)} onCancel={closeDetail} footer={null} mask={{ closable: false }} width="min(1200px, calc(100vw - 32px))" destroyOnHidden>
      {detailLoading ? <Spin /> : detailError ? <Alert type="error" title={detailError} action={<Button onClick={() => detailId && void loadDetail(detailId)}>重试</Button>} /> : detail && <LeadDetail lead={detail} categories={categories}
        categoryLabel={value => categories.find(item => item.value === value)?.label || '未记录分类'} channelLabel={value => channels.find(item => item.value === value)?.label || '未记录渠道'}
        mode="owner" autoExpandFollowUp={false} onDirtyChange={setDetailDirty} onChanged={refresh} />}
    </Modal>
    {followUp && <FollowUpModal lead={followUp} open onClose={() => setFollowUp(undefined)} onSuccess={refresh} />}
  </section>
}
