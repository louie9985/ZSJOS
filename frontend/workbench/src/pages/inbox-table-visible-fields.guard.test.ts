import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(`src/${path}`, 'utf8')

describe('inbox table visible business fields', () => {
  it('keeps the shared order table comprehensive and excludes internal identifiers', () => {
    const columns = source('components/SalesOrderTableColumns.tsx')
    for (const field of [
      '订单类型', '学员性质', '手机号', '微信号', '订单地区', '服务周期', '学员来源',
      '课程 / 产品', '客户付款时间', '收费模式', '付款方式', '订单备注', '学员特殊要求',
      '资料寄送联系人', '客资编号', '客资来源', '客资提交人', '所属销售', '客资分类',
      '来源渠道', '客资地区', '审批意见'
    ]) expect(columns).toContain(field)
    for (const internalField of ['leadId', 'personId', 'taskId', 'supervisorConfirmationId']) {
      expect(columns).not.toContain(`dataIndex: '${internalField}'`)
    }

    expect(source('pages/MySalesOrderPage.tsx')).toContain('buildSalesOrderTableColumns')
    expect(source('pages/SalesOrderApprovalPage.tsx')).toContain('buildSalesOrderTableColumns')
  })

  it('covers the visible detail fields of the remaining table inboxes', () => {
    const expectations: Record<string, string[]> = {
      'pages/LeadAppealPage.tsx': ['申诉轮次', '申请人', '申诉理由', '原无效原因', '裁决意见', '处理时间'],
      'pages/BpmApprovalCenterPage.tsx': ['流程名称', '流程摘要', '当前处理人', '流程发起时间', '处理耗时', '审批意见'],
      'pages/AnnouncementCenterPage.tsx': ['正文', '高亮状态', '高亮截止时间', '阅读时间', '附件数量'],
      'pages/LeadDuplicateReviewPage.tsx': ['提交姓名', '手机号', '微信号', '重复标记', '命中规则', '候选对象', '复核意见'],
      'components/SalesOrderSupervisorInbox.tsx': ['ProTable', '申请原因', '主管意见', '申请时间', '处理时间']
    }
    for (const [path, labels] of Object.entries(expectations)) {
      const page = source(path)
      labels.forEach(label => expect(page, `${path}: ${label}`).toContain(label))
      expect(page).toContain('columnsState')
      expect(page).toContain('详细')
    }
  })
})
