import { useEffect, useState } from 'react'
import { Alert, Button, Checkbox, DatePicker, Empty, Input, Select, Skeleton, Tag } from 'antd'
import { FilterOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { api, type DictData, type SimpleUser } from '../services/api'
import type { ContentReviewBatch, ContentReviewPageParams } from '../services/materialApi'
import { reviewAccounts } from '../services/contentReviewQuery'
import DateTimeText from './DateTimeText'

export type ReviewFilters = Pick<ContentReviewPageParams, 'operatorUserId' | 'directorUserId' | 'platformValue' | 'submittedFrom' | 'submittedTo'> & { stage?: string }
export default function ContentReviewInbox({ rows, selectedId, loading, error, keyword, category, filters, mine, canSeeAll, total,
  statusText, onSearch, onFilters, onMine, onReset, onSelect, onRetry }: {
  rows: ContentReviewBatch[]; selectedId?: number; loading: boolean; error: string; keyword: string; category: string
  filters: ReviewFilters; mine: boolean; canSeeAll: boolean; total: number; statusText: Record<string, string>
  onSearch: (value: string) => void; onFilters: (value: ReviewFilters) => void; onMine: (value: boolean) => void
  onReset: () => void; onSelect: (id: number) => void; onRetry: () => void
}) {
  const [search, setSearch] = useState(keyword)
  const [expanded, setExpanded] = useState(false)
  const [users, setUsers] = useState<SimpleUser[]>([])
  const [platforms, setPlatforms] = useState<DictData[]>([])
  const [optionsLoading, setOptionsLoading] = useState(false)
  const [optionsError, setOptionsError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => setSearch(keyword), [keyword])
  useEffect(() => {
    if (!expanded) return
    let disposed = false
    setOptionsLoading(true); setOptionsError('')
    Promise.all([api.simpleUsers(), api.dictDataByType('zsjos_account_platform')])
      .then(([people, dictionary]) => { if (!disposed) { setUsers(people); setPlatforms(dictionary) } })
      .catch(cause => { if (!disposed) setOptionsError(cause instanceof Error ? cause.message : '筛选选项加载失败') })
      .finally(() => { if (!disposed) setOptionsLoading(false) })
    return () => { disposed = true }
  }, [expanded, retry])
  const count = Number(Boolean(filters.operatorUserId)) + Number(Boolean(filters.directorUserId))
    + Number(Boolean(filters.platformValue)) + Number(Boolean(filters.submittedFrom || filters.submittedTo))
    + Number(Boolean(filters.stage && category === 'PENDING')) + Number(mine)
  return <>
    <div className="content-review-inbox-toolbar">
      <div className="content-review-inbox-caption"><strong>内容收件箱</strong><span>{total} 个批次</span></div>
      <div className="content-review-inbox-search"><Input.Search aria-label="搜索内容审核" allowClear value={search}
        onChange={event => { setSearch(event.target.value); if (!event.target.value) onSearch('') }}
        onSearch={value => onSearch(value.trim())} placeholder="学员、账号、标题、正文…" />
        <Button aria-label="筛选内容审核" icon={<FilterOutlined />} type={expanded || count ? 'primary' : 'default'} ghost={Boolean(expanded || count)} onClick={() => setExpanded(value => !value)}>{count || ''}</Button>
      </div>
      {(keyword || count > 0) && <Button size="small" type="link" onClick={() => { setSearch(''); onReset() }}>重置搜索与筛选</Button>}
      {expanded && <div className="content-review-inbox-filters">
        {optionsError && <Alert type="error" title={optionsError} action={<Button onClick={() => setRetry(value => value + 1)}>重试</Button>} />}
        <label>责任运营<Select aria-label="责任运营筛选" showSearch optionFilterProp="label" allowClear placeholder="全部运营" loading={optionsLoading} disabled={Boolean(optionsError)} value={filters.operatorUserId} options={users.map(user => ({ value: user.id, label: user.nickname }))} onChange={value => onFilters({ ...filters, operatorUserId: value })} /></label>
        <label>责任编导<Select aria-label="责任编导筛选" showSearch optionFilterProp="label" allowClear placeholder="全部编导" loading={optionsLoading} disabled={Boolean(optionsError)} value={filters.directorUserId} options={users.map(user => ({ value: user.id, label: user.nickname }))} onChange={value => onFilters({ ...filters, directorUserId: value })} /></label>
        <label>平台<Select aria-label="平台筛选" allowClear placeholder="全部平台" loading={optionsLoading} disabled={Boolean(optionsError)} value={filters.platformValue} options={platforms.map(item => ({ value: item.value, label: item.label }))} onChange={value => onFilters({ ...filters, platformValue: value })} /></label>
        {category === 'PENDING' && <label>审批阶段<Select allowClear placeholder="全部阶段" value={filters.stage} options={[{ value: 'DIRECTOR_REVIEW', label: '编导审核' }, { value: 'FINAL_REVIEW', label: '终审' }]} onChange={value => onFilters({ ...filters, stage: value })} /></label>}
        <label className="content-review-filter-dates">提交时间<DatePicker.RangePicker value={filters.submittedFrom && filters.submittedTo ? [dayjs(filters.submittedFrom), dayjs(filters.submittedTo)] : null} onChange={(_, values) => onFilters({ ...filters, submittedFrom: values[0] || undefined, submittedTo: values[1] || undefined })} /></label>
        {canSeeAll && <Checkbox checked={mine} onChange={event => onMine(event.target.checked)}>我是批次运营</Checkbox>}
      </div>}
    </div>
    <div className="content-review-scroll">{error ? <Alert type="error" title={error} action={<Button onClick={onRetry}>重试</Button>} /> : loading ? <Skeleton active /> : rows.length ? rows.map(batch =>
      <button type="button" key={batch.id} className={`content-review-list-item${batch.id === selectedId ? ' active' : ''}`} onClick={() => onSelect(batch.id)}>
        <div className="content-review-inbox-title"><strong>{batch.studentName || '未记录学员姓名'}</strong><Tag color={['DIRECTOR_REVIEW', 'FINAL_REVIEW'].includes(batch.status) ? 'processing' : batch.status === 'NEED_MODIFY' ? 'warning' : undefined}>{statusText[batch.status] || batch.status}</Tag></div>
        {reviewAccounts(batch).map((account, index) => <div className="content-review-inbox-account" key={account.accountId ?? index}>
          <div>{account.accountName || '未记录账号名称'} <span>{account.platformLabel}</span></div>
          <div className="content-review-inbox-owners"><span>责任运营：{account.operatorName || '未记录'}{account.operatorNameResolved && <small>（现用姓名）</small>}</span><span>责任编导：{account.directorName || '未记录'}{account.directorNameResolved && <small>（现用姓名）</small>}</span></div>
        </div>)}
        <div className="content-review-inbox-footer"><Tag color="blue">{batch.items.length} 条内容</Tag><DateTimeText value={batch.submittedAt} /></div>
      </button>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的审核批次" />}</div>
  </>
}
