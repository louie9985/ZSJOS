import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { SalesOrder } from '../services/api'
import SalesOrderDetailCards from './SalesOrderDetailCards'
import SalesOrderCoursePicker from './SalesOrderCoursePicker'

function order(overrides: Partial<SalesOrder> = {}): SalesOrder {
  return {
    id: 100,
    orderNo: 'OD202608190001',
    personId: 10,
    orderType: 'first_purchase',
    status: 'pending_approval',
    submitterUserId: 20,
    buyerName: '测试客户',
    studentName: '测试客户',
    studentNature: 'adult',
    provinceCode: '440000',
    provinceName: '广东省',
    cityCode: '440800',
    cityName: '湛江市',
    servicePeriod: 'one_year',
    studentSource: 'lead',
    totalAmount: 100,
    customerPaidAt: Date.parse('2026-08-19T10:00:00+08:00'),
    feeMode: 'full',
    paymentMethod: 'wechat',
    items: [],
    paymentVouchers: [],
    approvalRoundNo: 1,
    approvalRoundStatus: 'pending',
    version: 0,
    currentApprovalRoundId: 200,
    approvalRoundVersion: 0,
    submittedAt: Date.parse('2026-08-19T10:00:00+08:00'),
    ...overrides
  }
}

describe('SalesOrderDetailCards Lead profile', () => {
  it('retains course names and specs after removal from the active catalog', () => {
    const html = renderToStaticMarkup(<SalesOrderCoursePicker catalog={{ categoryTree: [], spus: [], skus: [] }} value="old::old-sku"
      historicalItems={[{ id: 1, productRef: 'old', skuRef: 'old-sku', productName: '历史课程名称', skuName: '历史班次', actualAmount: 100 } as SalesOrder['items'][number]]} />)
    expect(html).toContain('历史课程名称')
    expect(html).toContain('历史班次')
    expect(html).not.toContain('old-sku')
  })
  it('renders recorded labels without resolving current dictionaries and marks missing history', () => {
    const html = renderToStaticMarkup(<SalesOrderDetailCards mode="mine" order={order({
      paymentMethodLabelSnapshot: '已删除的历史支付方式',
      historyMissingFields: { leadProfile: 'history_not_recorded' }
    })} />)
    expect(html).toContain('已删除的历史支付方式')
    expect(html).toContain('历史未记录')
    expect(html).not.toContain('wechat')
    expect(html).not.toContain('标签加载')
  })
  it('keeps the SKU custom name alongside named specifications', () => {
    const html = renderToStaticMarkup(<SalesOrderDetailCards mode="mine" order={order({ items: [{
      id: 1, productName: '考试课程', skuName: '周末强化方案', actualAmount: 100,
      specs: [{ attrKey: 'level', attrName: '考试等级', value: '2', label: '二级' }]
    } as SalesOrder['items'][number]] })} />)
    expect(html).toContain('考试课程')
    expect(html).toContain('周末强化方案')
    expect(html).toContain('考试等级：二级')
  })
  it('renders approval centers as workflow nodes with nested supervisor sign-off', () => {
    const html = renderToStaticMarkup(<SalesOrderDetailCards mode="approval-done" order={order({
      taskDefinitionKey: 'financeReview',
      registrationApproval: { status: 'approved', reviewerUserName: '报名审核员', endTime: 1 },
      financeApproval: { status: 'pending', reviewerUserName: '财务审核员', createTime: 2 },
      financeSupervisorConfirmation: {
        id: 3, status: 'pending', requesterUserId: 20, requesterUserName: '销售专员', requestReason: '需要主管确认'
      }
    })}/>)

    expect(html).toContain('审批流程')
    expect(html).toContain('报名履约中心')
    expect(html).toContain('财务结算中心')
    expect(html).toContain('负责人主管会签')
    expect(html).toContain('当前节点')
    expect(html).toContain('ant-timeline')
    expect(html).toContain('sales-order-approval-sidebar')
    expect(html).not.toContain('审核中心')
  })

  it('separates the dense business canvas from status and available actions', () => {
    const html = renderToStaticMarkup(<SalesOrderDetailCards mode="mine" order={order({
      status: 'revision_required',
      decisionReason: '付款凭证不清晰',
      canRevise: true
    })} onRevise={() => undefined}/>)

    expect(html).toContain('sales-order-detail-layout')
    expect(html).toContain('sales-order-detail-main')
    expect(html).toContain('sales-order-approval-sidebar')
    expect(html).toContain('sales-order-approval-actions')
    expect(html).toContain('订单已驳回，等待补正')
    expect(html).toContain('付款凭证不清晰')
    expect(html).toContain('修改并重新提交')
  })

  it('renders the authoritative business profile and copy controls', () => {
    const html = renderToStaticMarkup(<SalesOrderDetailCards mode="approval-done" order={order({
      leadId: 1,
      leadProfile: {
        leadNo: 'KZ202608191041490002',
        submittedName: '自动测试客户',
        submittedMobile: '19926231001',
        submittedWechatId: 'wx-test',
        sourceType: 'internal_new_media',
        sourceLabel: '新媒体提交',
        sourceUserName: '新媒体专员',
        ownerUserName: '销售专员2',
        sourceChannel: 'information_flow',
        leadCategory: 'high_intent',
        dispatchMode: 'auto',
        provinceName: '广东省',
        cityName: '湛江市'
      }
    })}/>)

    expect(html).toContain('客户档案')
    expect(html).toContain('KZ202608191041490002')
    expect(html).toContain('19926231001')
    expect(html).toContain('wx-test')
    expect(html).toContain('新媒体提交')
    expect(html).toContain('自动分配')
    expect(html.match(/title="复制"/g)).toHaveLength(2)
  })

  it('does not invent a Lead profile for an unlinked repurchase', () => {
    const html = renderToStaticMarkup(<SalesOrderDetailCards mode="mine" order={order({
      orderType: 'repurchase',
      leadId: undefined,
      leadProfile: undefined
    })}/>)

    expect(html).not.toContain('客户档案')
    expect(html).not.toContain('客资编号')
  })
})

it('shows education conversion identity from the order snapshot', () => {
  const html = renderToStaticMarkup(<SalesOrderDetailCards mode="mine" order={order({
    formalOwnerIdentity: 'education', formalOwnerIdentityLabel: '教务',
    leadProfile: { submittedName: '测试学员', leadNo: 'KZ-test', sourceLabel: '教务自拓录', ownerUserName: '测试负责人',
      ownerIdentity: 'education', ownerIdentityLabel: '教务' } as SalesOrder['leadProfile']
  })} />)
  expect(html).toContain('成交归属身份')
  expect(html).toContain('教务自拓录')
  expect(html).toContain('教务')
  expect(html).not.toContain('所属销售')
})
