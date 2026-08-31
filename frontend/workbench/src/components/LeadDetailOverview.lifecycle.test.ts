import { describe, expect, it } from 'vitest'
import { buildLeadFlowEvents, pipelineStepIndex, studentProfileIdentity } from './LeadDetailOverview'
import type { ManagedLead, MyStudent } from '../services/api'

const lead = (overrides: Partial<ManagedLead> = {}): ManagedLead => ({
  id: 1,
  leadNo: 'L202608180001',
  personId: 2,
  submittedName: '测试客户',
  sourceType: 'employee',
  status: 'valid',
  assignmentStatus: 'owned',
  handlingStage: 'following',
  qualificationStatus: 'valid',
  followUpStatus: 'following',
  operationalStatus: 'active',
  submittedAt: 100,
  createTime: 100,
  updateTime: 100,
  relationTypes: [],
  qualifiedAt: 200,
  ...overrides,
})

describe('Lead lifecycle presentation', () => {
  it('keeps qualification, deal entry, and conversion as separate events', () => {
    const events = buildLeadFlowEvents(lead({ salesOrderSubmittedAt: 300, convertedAt: 400 }))

    expect(events.map((event) => event.label)).toContain('判定有效')
    expect(events.map((event) => event.label)).toContain('录入成交')
    expect(events.map((event) => event.label)).toContain('成交转化')
    expect(events.find((event) => event.label === '录入成交')?.time).toBe(300)
    expect(events.find((event) => event.label === '成交转化')?.time).toBe(400)
  })

  it('advances the pipeline only after order entry and then effective conversion', () => {
    expect(pipelineStepIndex(lead())).toBe(2)
    expect(pipelineStepIndex(lead({ salesOrderSubmittedAt: 300, activeSalesOrderStatus: 'pending_approval' }))).toBe(3)
    expect(pipelineStepIndex(lead({ salesOrderSubmittedAt: 300, activeSalesOrderStatus: 'revision_required' }))).toBe(3)
    expect(pipelineStepIndex(lead({ salesOrderSubmittedAt: 300, status: 'terminated' }))).toBe(2)
    expect(pipelineStepIndex(lead({ convertedAt: 400, status: 'won', followUpStatus: 'won' }))).toBe(4)
  })
})

describe('student profile identity', () => {
  const student: MyStudent = {
    personId: 14,
    personNo: 'S202608250014',
    name: '学员姓名',
    mobile: '13800000000',
    wechatId: 'student-wechat',
    services: []
  }

  it('uses Person contact fields while retaining a real Lead number', () => {
    expect(studentProfileIdentity(lead(), student)).toEqual({
      name: '学员姓名',
      mobile: '13800000000',
      wechatId: 'student-wechat',
      numberLabel: '客资编号',
      number: 'L202608180001'
    })
  })

  it('uses the Person number when the selected service has no Lead', () => {
    expect(studentProfileIdentity(undefined, student)).toMatchObject({
      name: '学员姓名',
      numberLabel: '学员编号',
      number: 'S202608250014'
    })
  })
})
