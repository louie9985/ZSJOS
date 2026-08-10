import { describe, expect, it } from 'vitest'
import type { ManagedLead } from './api'
import { applyInvalidRemarkTemplate, canJudgeLeadQualification, defaultInboxStage, mergeUniqueLeads, sumStatusCounts, tryStartLeadPageRequest } from './leadManagement'

const lead = (id: number, name: string): ManagedLead => ({
  id,
  personId: id,
  submittedName: name,
  sourceType: 'internal_new_media',
  status: 'submitted',
  assignmentStatus: 'owned',
  handlingStage: 'first_follow_pending',
  qualificationStatus: 'pending',
  followUpStatus: 'first_follow_pending',
  operationalStatus: 'active',
  submittedAt: 1786154400000,
  createTime: 1786154400000,
  updateTime: 1786154400000,
  relationTypes: ['owner']
})

describe('lead management paging helpers', () => {
  it('merges lazy-loaded pages without duplicate records', () => {
    expect(mergeUniqueLeads([lead(1, '旧名称')], [lead(1, '新名称'), lead(2, '客户二')]))
      .toEqual([lead(1, '新名称'), lead(2, '客户二')])
  })

  it('sums server status counts', () => {
    expect(sumStatusCounts({ submitted: 3, converted: 2 })).toBe(5)
  })

  it('prevents duplicate requests for the same filter version and page', () => {
    const activeRequests = new Set<string>()

    expect(tryStartLeadPageRequest(activeRequests, 2, 3)).toBe('2:3')
    expect(tryStartLeadPageRequest(activeRequests, 2, 3)).toBeUndefined()
    expect(tryStartLeadPageRequest(activeRequests, 3, 3)).toBe('3:3')
  })

  it('uses the first server-provided stage and falls back to all', () => {
    const groups = [{ key: 'valid', sections: [{ options: [{ key: 'all' }, { key: 'valid' }] }] }]

    expect(defaultInboxStage(groups, 'valid')).toBe('all')
    expect(defaultInboxStage(groups, 'closed')).toBe('all')
  })

  it('allows qualification only from the server-projected pending follow-up state', () => {
    const pending = { qualificationStatus: 'pending' as const, followUpStatus: 'following' as const, operationalStatus: 'active' as const }
    expect(canJudgeLeadQualification(pending, 'owner', true)).toBe(true)
    expect(canJudgeLeadQualification({ ...pending, followUpStatus: 'first_follow_pending' }, 'owner', true)).toBe(false)
    expect(canJudgeLeadQualification({ ...pending, operationalStatus: 'suspended' }, 'owner', true)).toBe(false)
    expect(canJudgeLeadQualification(pending, 'submitter', true)).toBe(false)
    expect(canJudgeLeadQualification(pending, 'owner', false)).toBe(false)
  })

  it('replaces the current invalid remark with the selected independent template', () => {
    expect(applyInvalidRemarkTemplate('销售已填写的旧内容', '客户明确表示无需求')).toBe('客户明确表示无需求')
  })
})
