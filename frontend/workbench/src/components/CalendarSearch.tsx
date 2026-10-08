import { SearchOutlined } from '@ant-design/icons'
import { Alert, Button, DatePicker, Drawer, Empty, Input, List, Pagination, Select, Space, Spin, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useEffect, useRef, useState } from 'react'
import { ApiError, AUTH_EXPIRED_EVENT } from '../services/api'
import { STORAGE_KEYS, AUTH_STORAGE_KEYS } from '../constants'
import { IMPERSONATION_CHANGE_EVENT } from '../services/impersonation'
import { CalendarResultExpired, matchingExcerpt, type CalendarSearchQuery, type SearchPage, type SearchPresentation } from '../services/calendarSearch'
import '../styles/components/calendar-search.css'

type Props<T> = {
  scopeKey?: string; scopeLabel: string; initialKeyword?: string; disabled?: boolean
  search: (query: CalendarSearchQuery, signal: AbortSignal) => Promise<SearchPage<T>>
  present: (row: T) => SearchPresentation
  onLocate: (row: T, signal: AbortSignal) => Promise<void>
}

export default function CalendarSearch<T>({ scopeKey = '', scopeLabel, initialKeyword = '', disabled, search, present, onLocate }: Props<T>) {
  const [open, setOpen] = useState(false), [keyword, setKeyword] = useState(initialKeyword)
  const [range, setRange] = useState<[string, string]>()
  const [sort, setSort] = useState<CalendarSearchQuery['sort']>('nearest')
  const [query, setQuery] = useState<CalendarSearchQuery>()
  const [data, setData] = useState<SearchPage<T>>({ list: [], total: 0 })
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const [locating, setLocating] = useState<number>()
  const controller = useRef<AbortController | undefined>(undefined)
  const locationController = useRef<AbortController | undefined>(undefined)
  const current = useRef({ search, onLocate }); current.current = { search, onLocate }
  const attempted = useRef(false)

  useEffect(() => {
    const reset = () => {
      controller.current?.abort(); locationController.current?.abort(); attempted.current = false
      setOpen(false); setKeyword(''); setRange(undefined); setQuery(undefined); setData({ list: [], total: 0 })
      setLoading(false); setLocating(undefined); setError(''); setNotice(''); setSort('nearest')
    }
    const storage = (event: StorageEvent) => {
      const keys: string[] = [STORAGE_KEYS.TENANT_ID, STORAGE_KEYS.IMPERSONATION, ...Object.values(AUTH_STORAGE_KEYS).map(value => value.accessToken)]
      if (event.key === null || keys.includes(event.key)) reset()
    }
    window.addEventListener('storage', storage)
    window.addEventListener(AUTH_EXPIRED_EVENT, reset)
    window.addEventListener(IMPERSONATION_CHANGE_EVENT, reset)
    return () => { window.removeEventListener('storage', storage); window.removeEventListener(AUTH_EXPIRED_EVENT, reset); window.removeEventListener(IMPERSONATION_CHANGE_EVENT, reset) }
  }, [])

  useEffect(() => {
    controller.current?.abort(); locationController.current?.abort()
    attempted.current = false
    setQuery(undefined); setData({ list: [], total: 0 }); setKeyword(initialKeyword); setRange(undefined)
    setSort('nearest'); setError(''); setNotice(''); setLoading(false); setLocating(undefined)
    return () => { controller.current?.abort(); locationController.current?.abort() }
    // initialKeyword seeds a new context; ordinary typing in the source page must not reset completed searches.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey])

  const run = async (next: CalendarSearchQuery) => {
    controller.current?.abort(); locationController.current?.abort(); setLocating(undefined)
    const request = new AbortController(); controller.current = request
    attempted.current = true; setQuery(next); setLoading(true); setError(''); setData({ list: [], total: 0 })
    try {
      const result = await current.current.search(next, request.signal)
      if (!request.signal.aborted) setData(result)
    } catch (cause) {
      if (!request.signal.aborted) setError(cause instanceof ApiError && cause.code === 403 ? '无权搜索此日历，请联系管理员' : cause instanceof Error ? cause.message : '搜索失败，请重试')
    } finally { if (!request.signal.aborted) setLoading(false) }
  }
  const submit = (value: string) => {
    if (!value.trim()) { setNotice('请输入搜索关键词'); return }
    setNotice(''); void run({ keyword: value.trim(), rangeStart: range?.[0], rangeEnd: range?.[1], sort, pageNo: 1, pageSize: 20 })
  }
  const locate = async (row: T) => {
    locationController.current?.abort()
    const request = new AbortController(); locationController.current = request
    setLocating(present(row).id); setNotice('')
    try {
      await current.current.onLocate(row, request.signal)
      if (!request.signal.aborted) setOpen(false)
    } catch (cause) {
      if (!request.signal.aborted) {
        setNotice(cause instanceof Error ? cause.message : '定位失败，请重试')
        if (cause instanceof CalendarResultExpired && query) void run({ ...query, pageNo: 1 })
      }
    } finally { if (!request.signal.aborted) setLocating(undefined) }
  }
  return <>
    <Button icon={<SearchOutlined />} disabled={disabled} onClick={() => { if (!attempted.current) setKeyword(initialKeyword); setOpen(true) }}>跨月份搜索</Button>
    <Drawer title="跨月份搜索" open={open} onClose={() => { locationController.current?.abort(); setLocating(undefined); setOpen(false) }} className="calendar-search-drawer" width="min(560px, 100vw)">
      <Space orientation="vertical" className="calendar-search-content" size="middle">
        <Typography.Text type="secondary">搜索范围：{scopeLabel}；日期默认不限</Typography.Text>
        <Input.Search aria-label="日历搜索关键词" placeholder="输入关键词，按回车搜索" value={keyword} maxLength={100} allowClear enterButton="搜索"
          onChange={event => { setKeyword(event.target.value); if (!event.target.value) { controller.current?.abort(); locationController.current?.abort(); setQuery(undefined); setData({ list: [], total: 0 }); setError(''); setNotice(''); setLoading(false); setLocating(undefined) } }} onSearch={submit} />
        <div className="calendar-search-filters">
          <DatePicker.RangePicker value={range ? [dayjs(range[0]), dayjs(range[1])] : null} onChange={dates => setRange(dates?.[0] && dates[1] ? [dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')] : undefined)} />
          <Select aria-label="搜索结果排序" value={sort} onChange={value => { setSort(value); if (query) void run({ ...query, sort: value, pageNo: 1 }) }} options={[{ value: 'nearest', label: '距今天最近' }, { value: 'asc', label: '日期从早到晚' }, { value: 'desc', label: '日期从新到旧' }]} />
        </div>
        {query && <Typography.Text type="secondary">当前结果：“{query.keyword}” · {query.rangeStart ? `${query.rangeStart} 至 ${query.rangeEnd}` : '不限日期'}</Typography.Text>}
        {notice && <Alert type="warning" showIcon title={notice} />}
        {error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => query && void run(query)}>重试</Button>} /> : <Spin spinning={loading}>
          {!query ? <Empty description="输入关键词查找其他月份的安排" /> : !loading && !data.list.length ? <Empty description="没有符合当前条件的安排" /> : <List dataSource={data.list} renderItem={row => {
            const item = present(row)
            return <List.Item key={item.id} className="calendar-search-result">
              <div><Typography.Text strong>{item.title}</Typography.Text>{item.label && <Tag>{item.label}</Tag>}
                <div>{item.start}{item.end !== item.start && ` 至 ${item.end}`}</div>
                {item.summary && <Typography.Paragraph className="calendar-search-summary">{matchingExcerpt(item.summary, query?.keyword || '')}</Typography.Paragraph>}
              </div><Button loading={locating === item.id} disabled={locating !== undefined && locating !== item.id} onClick={() => void locate(row)}>定位到日历</Button>
            </List.Item>
          }} />}
        </Spin>}
        {query && data.total > 0 && !error && <Pagination current={query.pageNo} pageSize={20} total={data.total} showSizeChanger={false} showTotal={total => `共 ${total} 条`} onChange={pageNo => void run({ ...query, pageNo })} />}
      </Space>
    </Drawer>
  </>
}
