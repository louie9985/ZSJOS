import { Button, Tag } from 'antd'
import type { ProColumns } from '@ant-design/pro-components'
import type { SalesOrder, SalesOrderListItem } from '../services/api'
import DateTimeText from './DateTimeText'
import {
  SALES_ORDER_STATUS_COLORS,
  SALES_ORDER_STATUS_LABELS,
  SALES_ORDER_TASK_LABELS
} from './SalesOrderDetailCards'

const ORDER_TYPE_LABELS: Record<string, string> = { first_purchase: '首购', repurchase: '复购' }
const TASK_STATUS_LABELS: Record<number, string> = {
  0: '待审批', 1: '审批中', 2: '已通过', 3: '已拒绝', 4: '已取消', 5: '已退回', 7: '通过中'
}
const SUPERVISOR_STATUS_LABELS: Record<string, string> = {
  pending: '等待确认', confirmed: '已确认', rejected: '已驳回', cancelled: '已取消'
}

const textColumn = (title: string, dataIndex: keyof SalesOrderListItem, width = 150): ProColumns<SalesOrderListItem> => ({
  title,
  dataIndex,
  width,
  ellipsis: true,
  render: value => value || '-'
})

export function buildSalesOrderTableColumns(onDetail: (item: SalesOrderListItem) => void): ProColumns<SalesOrderListItem>[] {
  return [
    { title: '订单号', dataIndex: 'orderNo', width: 180, fixed: 'left', ellipsis: true },
    { title: '订单类型', dataIndex: 'orderType', width: 110, render: value => ORDER_TYPE_LABELS[String(value)] || value || '-' },
    {
      title: '订单状态', dataIndex: 'status', width: 130,
      render: value => <Tag color={SALES_ORDER_STATUS_COLORS[value as SalesOrder['status']]}>{SALES_ORDER_STATUS_LABELS[value as SalesOrder['status']] || String(value)}</Tag>
    },
    { title: '审批轮次', dataIndex: 'approvalRoundNo', width: 100, render: value => value ? `第 ${value} 轮` : '-' },
    { title: '当前审批节点', dataIndex: 'taskDefinitionKey', width: 150, render: value => SALES_ORDER_TASK_LABELS[String(value)] || '-' },
    { title: '审批结果', dataIndex: 'taskStatus', width: 110, render: value => value == null ? '-' : TASK_STATUS_LABELS[Number(value)] || `状态 ${value}` },
    { title: '主管确认状态', dataIndex: 'supervisorConfirmationStatus', width: 130, render: value => value ? SUPERVISOR_STATUS_LABELS[String(value)] || value : '-' },
    textColumn('主管确认申请人', 'supervisorRequesterName', 150),
    textColumn('购买方', 'buyerName'),
    textColumn('学员姓名', 'studentName', 130),
    textColumn('学员性质', 'studentNatureLabelSnapshot', 130),
    textColumn('手机号', 'studentMobile', 140),
    textColumn('微信号', 'studentWechatId', 150),
    { title: '订单地区', width: 180, ellipsis: true, render: (_, item) => [item.provinceName, item.cityName].filter(Boolean).join(' / ') || '-' },
    textColumn('约定考试时间', 'agreedExamTime', 150),
    textColumn('班型', 'classType', 130),
    textColumn('服务周期', 'servicePeriodLabelSnapshot', 140),
    textColumn('学员来源', 'studentSourceLabelSnapshot', 140),
    textColumn('课程 / 产品', 'productSummary', 260),
    { title: '订单金额', dataIndex: 'totalAmount', width: 130, render: value => `¥${Number(value || 0).toFixed(2)}` },
    { title: '客户付款时间', dataIndex: 'customerPaidAt', width: 170, render: value => <DateTimeText value={value as SalesOrderListItem['customerPaidAt']}/> },
    textColumn('收费模式', 'feeModeLabelSnapshot', 140),
    textColumn('付款方式', 'paymentMethodLabelSnapshot', 140),
    textColumn('订单备注', 'remark', 220),
    textColumn('学员特殊要求', 'studentSpecialRequirements', 220),
    textColumn('资料寄送联系人', 'materialDeliveryContact', 180),
    textColumn('复购原因', 'repurchaseReason', 200),
    textColumn('终止原因', 'terminationReason', 200),
    textColumn('客资编号', 'leadNo', 160),
    textColumn('客资来源', 'leadSourceLabel', 140),
    textColumn('客资提交人', 'leadSourceUserName', 140),
    textColumn('所属销售', 'leadOwnerUserName', 140),
    textColumn('客资分类', 'leadCategoryLabelSnapshot', 140),
    textColumn('来源渠道', 'leadSourceChannelLabelSnapshot', 160),
    { title: '客资地区', width: 180, ellipsis: true, render: (_, item) => [item.leadProvinceName, item.leadCityName].filter(Boolean).join(' / ') || '-' },
    { title: '提交时间', dataIndex: 'submittedAt', width: 170, render: value => <DateTimeText value={value as SalesOrderListItem['submittedAt']}/> },
    { title: '订单生效时间', dataIndex: 'effectiveAt', width: 170, render: value => <DateTimeText value={value as SalesOrderListItem['effectiveAt']}/> },
    { title: '任务到达时间', dataIndex: 'taskCreateTime', width: 170, render: value => <DateTimeText value={value as SalesOrderListItem['taskCreateTime']}/> },
    { title: '任务完成时间', dataIndex: 'taskEndTime', width: 170, render: value => <DateTimeText value={value as SalesOrderListItem['taskEndTime']}/> },
    textColumn('审批意见', 'taskReason', 240),
    {
      title: '操作', key: 'action', width: 88, fixed: 'right', hideInSetting: true,
      render: (_, item) => <Button type="link" onClick={() => onDetail(item)}>详细</Button>
    }
  ]
}
