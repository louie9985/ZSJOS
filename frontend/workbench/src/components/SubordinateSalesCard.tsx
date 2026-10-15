import { Tag, Tooltip } from 'antd'
import EmployeeAvatar from './EmployeeAvatar'
import type { SubordinateSales } from '../services/api'
import { formatCurrency, receiveStatusLabel } from '../services/subordinateSales'

export default function SubordinateSalesCard({ sales, selected, onSelect }: {
  sales: SubordinateSales; selected: boolean; onSelect: () => void
}) {
  const remaining = sales.todayFollowUpRemainingCount
  const total = sales.todayFollowUpTotalCount
  const ready = remaining != null && total != null
  const metrics = [
    ['分配客资数', sales.todayAssignedCount, '今日派发给该销售的去重客资数'],
    ['漏接数', sales.todayMissedCount, '今日发生接单超时的去重客资数'],
    ['接收客资数', sales.todayReceivedCount, '今日接收或抢单成功的去重客资数'],
    ['判定有效客资数', sales.todayQualifiedCount, '今日由该销售判定有效的去重客资数'],
    ['跟单记录数', sales.todayFollowUpRecordCount, '今日提交的客资和机会跟进记录总数'],
    ['今日成交额', sales.todayOrderAmount == null ? undefined : formatCurrency(sales.todayOrderAmount), '今日生效且当前有效的订单金额'],
    ['今日待跟进未完成', remaining, '今日到期且仍待跟进的去重客资数'],
    ['剩余未判定', sales.pendingQualificationCount, '截至现在名下仍未判定的客资总数，不限制是否满 3 日'],
  ] as const
  return <button type="button" className={`subordinate-sales-item ${selected ? 'active' : ''}`}
    aria-pressed={selected} onClick={onSelect}>
    <div className="subordinate-sales-item-title">
      <EmployeeAvatar avatar={sales.avatar} name={sales.name} size={28} />
      <strong title={sales.name}>{sales.name}</strong>
      <Tooltip title="今日跟进：剩余待跟进客资数 / 今日待跟进客资总数">
        <Tag color={ready ? remaining === 0 ? 'success' : 'warning' : 'default'}>
          {ready ? remaining === 0 ? '已完成' : `${remaining} / ${total}` : '状态待更新'}
        </Tag>
      </Tooltip>
    </div>
    <div className="subordinate-sales-item-account">{sales.username} · {sales.mobile || '未填写手机号'}</div>
    <div className="subordinate-sales-item-status">
      <Tag color={sales.accountStatus === 0 ? 'success' : 'default'}>{sales.accountStatus === 0 ? '启用' : '停用'}</Tag>
      <Tag color={sales.presence === 'online' ? 'success' : 'default'}>{sales.presence === 'online' ? '在线' : '离线'}</Tag>
      <Tag color={sales.accepting ? 'processing' : 'default'}>{sales.accepting ? '接单开启' : '接单关闭'}</Tag>
      <Tag color={sales.canReceiveNewLeads ? 'success' : 'default'}>{receiveStatusLabel(sales)}</Tag>
    </div>
    <div className="subordinate-sales-daily-heading">今日状态</div>
    <div className="subordinate-sales-daily-grid">
      {metrics.map(([label, value, description]) => <Tooltip key={label} title={description}>
        <div className="subordinate-sales-daily-metric">
          <span>{label}</span><b className={(label === '今日待跟进未完成' || label === '剩余未判定') && typeof value === 'number' && value > 0 ? 'is-pending' : undefined}>{value ?? '—'}</b>
        </div>
      </Tooltip>)}
    </div>
    <div className="subordinate-sales-item-summary">
      <span title={`有效客资 ${sales.validLeadCount}`}>有效客资 {sales.validLeadCount}</span>
      <span title={`成交 ${sales.convertedLeadCount} / ${formatCurrency(sales.effectiveOrderAmount)}`}>成交 {sales.convertedLeadCount} / {formatCurrency(sales.effectiveOrderAmount)}</span>
    </div>
  </button>
}
