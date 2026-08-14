import { describe, expect, it } from 'vitest'
import type { ManagedLead } from './api'
import { applyInvalidRemarkTemplate, canJudgeLeadQualification, defaultInboxStage, dictionaryDisplayLabel, hasNextLeadInboxPage, invalidReasonSnapshotLabel, isLeadInboxUnauthorized, leadPendingTaskAlert, mergeUniqueLeads, protocolDisplayLabel, resolvedDisplayLabel, snapshotDisplayLabel, sumStatusCounts, tryStartLeadPageRequest } from './leadManagement'

const lead = (id: number, name: string): ManagedLead => ({
  id,
  leadNo: `KZ20260814000000${String(id).padStart(4, '0')}`,
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
    expect(sumStatusCounts({ submitted: 3, won: 2 })).toBe(5)
  })

  it('distinguishes dictionary labels, missing configuration, and loading failures', () => {
    const options = [{ value: 'douyin', label: '抖音' }]

    expect(dictionaryDisplayLabel(options, 'douyin')).toBe('抖音')
    expect(dictionaryDisplayLabel(options, 'legacy')).toBe('标签未配置')
    expect(dictionaryDisplayLabel([], 'douyin', true)).toBe('标签加载失败')
    expect(dictionaryDisplayLabel(options)).toBe('-')
  })

  it('uses server-resolved labels without exposing raw keys as labels', () => {
    expect(resolvedDisplayLabel('抖音', 'douyin')).toBe('抖音')
    expect(resolvedDisplayLabel(undefined, 'douyin')).toBe('标签未配置')
    expect(resolvedDisplayLabel(undefined, undefined)).toBe('-')
  })

  it('does not expose unknown protocol keys as user-facing statuses', () => {
    expect(protocolDisplayLabel({ owned: '已归属' }, 'owned', '未知分配状态')).toBe('已归属')
    expect(protocolDisplayLabel({ owned: '已归属' }, 'new_state', '未知分配状态')).toBe('未知分配状态')
    expect(protocolDisplayLabel({}, undefined, '未知分配状态')).toBe('-')
  })

  it('does not expose protocol keys stored in historical label snapshots', () => {
    expect(snapshotDisplayLabel('电话', 'phone')).toBe('电话')
    expect(snapshotDisplayLabel('phone', 'phone')).toBe('标签未配置')
    expect(invalidReasonSnapshotLabel('duplicate_lead')).toBe('标签未配置')
    expect(invalidReasonSnapshotLabel('客户已重复提交')).toBe('客户已重复提交')
  })

  it('prevents duplicate requests for the same filter version and page', () => {
    const activeRequests = new Set<string>()

    expect(tryStartLeadPageRequest(activeRequests, 2, 3)).toBe('2:3')
    expect(tryStartLeadPageRequest(activeRequests, 2, 3)).toBeUndefined()
    expect(tryStartLeadPageRequest(activeRequests, 3, 3)).toBe('3:3')
  })

  it('distinguishes unauthorized inbox failures from retryable load failures', () => {
    expect(isLeadInboxUnauthorized('请求失败（403）')).toBe(true)
    expect(isLeadInboxUnauthorized('当前账号无权查看客资')).toBe(true)
    expect(isLeadInboxUnauthorized('客资列表加载超时')).toBe(false)
  })

  it('uses the backend page boundary instead of the deduplicated item count', () => {
    expect(hasNextLeadInboxPage(1, 20, 41)).toBe(true)
    expect(hasNextLeadInboxPage(2, 20, 41)).toBe(true)
    expect(hasNextLeadInboxPage(3, 20, 41)).toBe(false)
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

  it('shows the first follow-up task before qualification starts', () => {
    expect(leadPendingTaskAlert({
      handlingStage: 'first_follow_pending', operationalStatus: 'active',
      currentAssignmentFirstFollowUpDeadlineAt: 1786669200000
    })).toEqual({ message: '待完成首次跟进', deadline: 1786669200000 })
  })

  it('shows qualification only when its deadline exists', () => {
    expect(leadPendingTaskAlert({
      handlingStage: 'qualification_pending', operationalStatus: 'active',
      qualificationDeadlineAt: 1786928400000
    })).toEqual({ message: '待完成有效性判定', deadline: 1786928400000 })
    expect(leadPendingTaskAlert({
      handlingStage: 'qualification_pending', operationalStatus: 'active'
    })).toBeUndefined()
  })

  it('does not show a pending task while the lead is suspended', () => {
    expect(leadPendingTaskAlert({
      handlingStage: 'qualification_pending', operationalStatus: 'suspended',
      qualificationDeadlineAt: 1786928400000
    })).toBeUndefined()
  })

  it('replaces the current invalid remark with the selected independent template', () => {
    expect(applyInvalidRemarkTemplate('销售已填写的旧内容', '客户明确表示无需求')).toBe('客户明确表示无需求')
  })
})
