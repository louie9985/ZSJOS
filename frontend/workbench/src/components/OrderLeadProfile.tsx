import { useState } from 'react'
import { Space, Tag } from 'antd'
import { CopyOutlined } from '@ant-design/icons'
import type { SalesOrder } from '../services/api'
import { formatTimestamp } from '../services/time'
import { orderPartnerOwnerText, orderSourcePersonText } from '../services/salesOrder'
import DetailFieldGrid from './DetailFieldGrid'
import SalesOrderInformationTable from './SalesOrderInformationTable'

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }
  return <button type="button" className={`lead-field-copy-btn ${copied ? 'copied' : ''}`} onClick={copy} title="复制">
    <CopyOutlined/>
  </button>
}

export default function OrderLeadProfile({ leadProfile, formalOwnerIdentityLabel, table = true }: {
  leadProfile: NonNullable<SalesOrder['leadProfile']>; formalOwnerIdentityLabel?: string; table?: boolean
}) {
  const Fields = table ? SalesOrderInformationTable : DetailFieldGrid
  const sourceDispatchTag = leadProfile?.sourceType === 'internal_new_media' && leadProfile.dispatchMode === 'auto'
    ? { label: '自动分配', color: 'blue' as const }
    : leadProfile?.sourceType === 'internal_new_media' && leadProfile.dispatchMode === 'specified'
      ? { label: '指定派单', color: 'orange' as const }
      : undefined
  return (
        <Fields items={[
          { key: 'name', label: '姓名', value: leadProfile.submittedName },
          { key: 'leadNo', label: '客资编号', value: leadProfile.leadNo },
          { key: 'mobile', label: '手机号', value: leadProfile.submittedMobile ? <Space>{leadProfile.submittedMobile}<CopyButton value={leadProfile.submittedMobile}/></Space> : '未填写' },
          { key: 'wechat', label: '微信号', value: leadProfile.submittedWechatId ? <Space>{leadProfile.submittedWechatId}<CopyButton value={leadProfile.submittedWechatId}/></Space> : '未填写' },
          { key: 'leadSubmittedAt', label: '客资提交时间', value: leadProfile.submittedAt ? formatTimestamp(leadProfile.submittedAt) : '历史未记录' },
          { key: 'identity', label: '成交归属身份', value: formalOwnerIdentityLabel || '未记录' },
          { key: 'source', label: '业务来源', value: <Space wrap>{leadProfile.sourceLabel || '来源未配置'}{sourceDispatchTag && <Tag color={sourceDispatchTag.color}>{sourceDispatchTag.label}</Tag>}</Space> },
          { key: 'sourcePerson', label: '来源人员', value: orderSourcePersonText(leadProfile.sourceType, leadProfile.sourceUserName, leadProfile.selfSourcedWithoutProvider) },
          ...(leadProfile.sourceType === 'partner' ? [{ key: 'partnerOwner', label: '兼职归属新媒体员工', value: orderPartnerOwnerText(leadProfile.sourceType, leadProfile.partnerOwnerSnapshot), span: 2 as const }] : []),
          { key: 'owner', label: '负责人', value: `${leadProfile.ownerUserName || '历史未记录'}${leadProfile.ownerIdentity ? ` · ${leadProfile.ownerIdentityLabel}` : ''}` },
          { key: 'category', label: '分类', value: leadProfile.leadCategoryLabelSnapshot || '历史未记录' },
          { key: 'channel', label: '渠道', value: leadProfile.sourceChannelLabelSnapshot || '历史未记录' },
          { key: 'region', label: '地区', value: [leadProfile.provinceName, leadProfile.cityName].filter(Boolean).join(' / ') || '-' }
        ]}/>
  )
}
