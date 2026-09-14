import { describe, expect, it } from 'vitest'
import { accountIdFromTab, buildMediaAccountTabLabels, mediaAccountTabKey, normalizeMediaStudentTab, resolveMediaStudentAccountId } from './MediaStudentsPage'
import type { MediaStudentDetail } from '../services/api'

const account = (id: number, nickname?: string, platformLabel?: string, accountNo = `NO-${id}`) => ({
  id, nickname, platformLabel, accountNo, primaryProblems: [], version: 1, availableActions: [], detailSnapshots: [], taskLine: [],
}) as MediaStudentDetail['accounts'][number]

describe('media student account tabs', () => {
  it('accepts only overview and concrete account tab keys', () => {
    expect(normalizeMediaStudentTab('account-12')).toBe('account-12')
    expect(normalizeMediaStudentTab('accounts')).toBe('overview')
    expect(normalizeMediaStudentTab('content')).toBe('overview')
    expect(normalizeMediaStudentTab(null)).toBe('overview')
    expect(mediaAccountTabKey(12)).toBe('account-12')
    expect(accountIdFromTab('account-12')).toBe(12)
    expect(accountIdFromTab('account-invalid')).toBeUndefined()
  })

  it('uses nicknames and disambiguates duplicate or missing names', () => {
    const labels = buildMediaAccountTabLabels([
      account(1, '健康小站', '抖音', 'DY-1'),
      account(2, '健康小站', '小红书', 'XHS-2'),
      account(3, undefined, '视频号', 'WX-3'),
    ])
    expect(labels.get(1)).toBe('健康小站 · 抖音')
    expect(labels.get(2)).toBe('健康小站 · 小红书')
    expect(labels.get(3)).toBe('未命名账号 · WX-3')
  })

  it('resolves account, content, positioning and legacy tab deep links', () => {
    const accounts = [{ id: 10 }, { id: 20 }]
    const contents = [{ id: 100, accountId: 20 }]
    const positioningRows = [{ id: 200, accountId: 10 }]
    expect(resolveMediaStudentAccountId(accounts, contents, positioningRows, { tab: 'account-20' })).toBe(20)
    expect(resolveMediaStudentAccountId(accounts, contents, positioningRows, { accountId: 10, tab: 'account-20' })).toBe(10)
    expect(resolveMediaStudentAccountId(accounts, contents, positioningRows, { contentId: 100 })).toBe(20)
    expect(resolveMediaStudentAccountId(accounts, contents, positioningRows, { positioningCardId: 200 })).toBe(10)
    expect(resolveMediaStudentAccountId(accounts, contents, positioningRows, { tab: 'account-99' })).toBeUndefined()
  })

  it('uses the account number when duplicate nicknames share a platform', () => {
    const labels = buildMediaAccountTabLabels([
      account(1, '健康小站', '抖音', 'DY-1'),
      account(2, '健康小站', '抖音', 'DY-2'),
    ])
    expect(labels.get(1)).toBe('健康小站 · 抖音 · DY-1')
    expect(labels.get(2)).toBe('健康小站 · 抖音 · DY-2')
  })

  it('falls back to the internal tab discriminator when all display fields collide', () => {
    const labels = buildMediaAccountTabLabels([
      account(1, undefined, '抖音', 'DY-SAME'),
      account(2, undefined, '抖音', 'DY-SAME'),
    ])
    expect(labels.get(1)).toBe('未命名账号 · DY-SAME · #1')
    expect(labels.get(2)).toBe('未命名账号 · DY-SAME · #2')
  })
})
