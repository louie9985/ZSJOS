import {
  EnvironmentOutlined,
  PhoneOutlined,
  PictureOutlined,
  WechatOutlined
} from '@ant-design/icons'
import { Alert, App, Button, Card, Empty, Image, Skeleton, Spin, Tag, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type AdvancedFilterGroup, type PendingLead } from '../services/api'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import { mergeUniqueLeads, resolvedDisplayLabel, tryStartLeadPageRequest } from '../services/leadManagement'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 12

function isUnauthorized(message: string) {
  return message.includes('403') || message.includes('无权') || message.includes('权限')
}

function ClaimCard({ lead, canClaim, claiming, onClaim }: {
  lead: PendingLead
  canClaim: boolean
  claiming: boolean
  onClaim: (id: number) => void
}) {
  const region = [lead.provinceName, lead.cityName].filter(Boolean).join(' / ') || '-'
  return <Card className="claim-pool-card">
    <div className="claim-card-content">
      <div className="claim-card-header">
        <div className="claim-card-identity">
          <Typography.Title level={5}>{lead.maskedName || '未命名客户'}</Typography.Title>
          <Typography.Text type="secondary">{lead.leadNo} · {formatTimestamp(lead.submittedAt)}</Typography.Text>
        </div>
        <Tag color="blue">待抢单</Tag>
      </div>

      <div className="claim-card-contacts">
        <Typography.Text><PhoneOutlined /> <span>{lead.maskedMobile || '-'}</span></Typography.Text>
        <Typography.Text><WechatOutlined /> <span>{lead.maskedWechatId || '-'}</span></Typography.Text>
      </div>

      <div className="claim-card-region"><EnvironmentOutlined /> <span>{region}</span></div>

      <dl className="claim-card-fields">
        <div><dt>来源渠道</dt><dd>{resolvedDisplayLabel(lead.sourceChannelLabel, lead.sourceChannel)}</dd></div>
        <div><dt>客资分类</dt><dd>{resolvedDisplayLabel(lead.leadCategoryLabel, lead.leadCategory)}</dd></div>
      </dl>

      <section className="claim-card-section">
        <Typography.Text type="secondary">意向课程</Typography.Text>
        <div className="claim-card-tags claim-card-scroll-region">
          {lead.intendedProducts.length ? lead.intendedProducts.map((course, index) =>
            <Tag key={`${course}-${index}`} color={course === lead.primaryIntendedProduct ? 'blue' : undefined}>
              {course}{course === lead.primaryIntendedProduct ? ' · 主意向' : ''}
            </Tag>
          ) : <Typography.Text>-</Typography.Text>}
        </div>
      </section>

      <section className="claim-card-section">
        <Typography.Text type="secondary">备注</Typography.Text>
        <p className="claim-card-remark claim-card-scroll-region">{lead.remark || '-'}</p>
      </section>

      <section className="claim-card-section">
        <Typography.Text type="secondary"><PictureOutlined /> 附件图片</Typography.Text>
        {lead.attachmentUrls.length ? <Image.PreviewGroup items={lead.attachmentUrls}>
          <div className="claim-card-images claim-card-scroll-region">
            {lead.attachmentUrls.map((url, index) =>
              <Image key={`${url}-${index}`} src={url} alt={`客资附件 ${index + 1}`} loading="lazy" />
            )}
          </div>
        </Image.PreviewGroup> : <Typography.Text className="claim-card-no-image">暂无附件</Typography.Text>}
      </section>
    </div>

    {canClaim && <Button
        className="claim-card-action"
        type="primary"
        loading={claiming}
        onClick={() => onClaim(lead.id)}
      >抢单</Button>}
  </Card>
}

export default function LeadClaimPoolPage({ canClaim }: { canClaim: boolean }) {
  const { message } = App.useApp()
  const [items, setItems] = useState<PendingLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [claiming, setClaiming] = useState<number>()
  const requestVersion = useRef(0)
  const activeRequests = useRef(new Set<string>())
  const sentinelRef = useRef<HTMLDivElement>(null)

  const loadPage = useCallback(async (targetPage: number, replace: boolean, version: number) => {
    const requestKey = tryStartLeadPageRequest(activeRequests.current, version, targetPage)
    if (!requestKey) return
    setLoading(true)
    setError('')
    try {
      const result = await api.claimPoolPage({ pageNo: targetPage, pageSize: PAGE_SIZE, keyword: keyword || undefined, advancedFilter })
      if (version !== requestVersion.current) return
      setItems(current => replace ? result.list : mergeUniqueLeads(current, result.list))
      setTotal(result.total)
      setPageNo(targetPage)
    } catch (loadError) {
      if (version === requestVersion.current) {
        setError(loadError instanceof Error ? loadError.message : '抢单池加载失败')
      }
    } finally {
      activeRequests.current.delete(requestKey)
      if (version === requestVersion.current) setLoading(false)
    }
  }, [advancedFilter, keyword])

  const refresh = useCallback(() => {
    const version = ++requestVersion.current
    setPageNo(0)
    void loadPage(1, true, version)
  }, [loadPage])

  useEffect(() => { refresh() }, [refresh])

  const hasMore = items.length < total
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasMore || loading) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) {
        void loadPage(pageNo + 1, false, requestVersion.current)
      }
    }, { rootMargin: '240px 0px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, loadPage, loading, pageNo])

  const claim = useCallback(async (id: number) => {
    setClaiming(id)
    try {
      await api.claimLead(id)
      message.success('抢单成功')
    } catch (claimError) {
      message.error(claimError instanceof Error ? claimError.message : '客资已被其他销售抢走')
    } finally {
      setClaiming(undefined)
      refresh()
    }
  }, [message, refresh])

  const content = useMemo(() => {
    if (loading && !items.length) {
      return <div className="claim-pool-grid claim-pool-skeletons">
        {Array.from({ length: PAGE_SIZE }, (_, index) => <Card key={index}><Skeleton active paragraph={{ rows: 8 }} /></Card>)}
      </div>
    }
    if (!items.length && error) {
      return <div className="claim-pool-state"><Alert type="error" showIcon
        message={isUnauthorized(error) ? '无权查看抢单池' : '抢单池加载失败'} description={error}
        action={!isUnauthorized(error) ? <Button onClick={refresh}>重试</Button> : undefined}/></div>
    }
    if (!items.length) return <div className="claim-pool-state"><Empty description="当前没有可抢客资" /></div>
    return <>
      {error && <Alert className="claim-pool-inline-error" type="error" showIcon message={error}
        action={<Button size="small" onClick={() => void loadPage(pageNo + 1, false, requestVersion.current)}>重试</Button>}/>} 
      <div className="claim-pool-grid">
        {items.map(lead => <ClaimCard key={lead.id} lead={lead} canClaim={canClaim} claiming={claiming === lead.id} onClaim={claim}/>) }
      </div>
      <div ref={sentinelRef} className="claim-pool-sentinel">
        {loading ? <><Spin size="small" /> 加载中</> : hasMore ? '继续下滑加载' : `已加载全部 ${total} 条客资`}
      </div>
    </>
  }, [canClaim, claim, claiming, error, hasMore, items, loadPage, loading, pageNo, refresh, total])

  return <section className="workspace-page claim-pool-page">
    <div className="claim-pool-toolbar">
      <AdvancedFilterToolbar scene="lead" placeholder="搜索姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/>
    </div>
    <div className="claim-pool-container">{content}</div>
  </section>
}
