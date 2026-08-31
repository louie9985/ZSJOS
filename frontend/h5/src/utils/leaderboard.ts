import type { LeaderboardData, LeaderboardMember, LeaderboardValueUnit } from '@/api/leaderboard'

export type LeaderboardTone = 'idle' | 'champion' | 'tie' | 'chase' | 'unranked'

export interface LeaderboardChaseText {
  primary: string
  secondary: string
  tone: LeaderboardTone
}

export interface LeaderboardGroup {
  rank: number
  members: LeaderboardMember[]
  totalTied: number
}

function isZeroGap(displayValue?: string, value?: number | null) {
  return displayValue === '0' || displayValue === '¥0' || displayValue === '¥0.00' || displayValue === '0条' || value === 0
}

function numericDiff(a: number | undefined, b: number | undefined) {
  if (a == null || b == null) return 0
  return a - b
}

export function formatLeaderboardValue(value: number | undefined | null, unit: LeaderboardValueUnit = 'money') {
  if (value == null) return '--'
  return unit === 'count' ? `${Math.round(value)} 条` : `¥${value.toFixed(2)}`
}

export function formatLeaderboardChase(data?: LeaderboardData | null): LeaderboardChaseText {
  if (!data) return { primary: '冲榜中', secondary: '榜单加载中', tone: 'idle' }

  const mine = data.myRank
  if (!mine) {
    return { primary: '冲榜中', secondary: '提交有效客资后可参与榜单', tone: 'unranked' }
  }

  if (mine.rank === 1) {
    return {
      primary: '卫冕中 · 第 1 名',
      secondary: data.previousGap?.displayValue ? `领先第 2 名 ${data.previousGap.displayValue}` : '继续保持领先',
      tone: 'champion'
    }
  }

  if (data.previousGap && isZeroGap(data.previousGap.displayValue, data.previousGap.value)) {
    return {
      primary: `与上一名并列 · 第 ${mine.rank} 名`,
      secondary: data.top10Gap?.targetReached ? '已进入前 10' : `距 Top 10 ${data.top10Gap?.displayValue || '--'}`,
      tone: 'tie'
    }
  }

  const previous = data.previousGap?.displayValue || '--'
  if (mine.rank <= 10) {
    return { primary: `再 ${previous} 超越上一名`, secondary: '已进入前 10 · 冲击榜首', tone: 'chase' }
  }

  return {
    primary: `再 ${previous} 超越上一名`,
    secondary: data.top10Gap?.targetReached ? '已进入前 10' : `距 Top 10 ${data.top10Gap?.displayValue || '--'}`,
    tone: 'chase'
  }
}

export function leaderboardRowGapText(
  current: LeaderboardMember,
  previous: LeaderboardMember | undefined,
  unit: LeaderboardValueUnit = 'money'
) {
  if (!previous || current.rank === 1) return '榜首'
  const diff = numericDiff(previous.value, current.value)
  if (diff <= 0.0001) return '与上一名并列'
  return `再 ${formatLeaderboardValue(diff, unit)} 超越上一名`
}

export function computeMaxStepGap(list: LeaderboardMember[]) {
  return list.reduce((max, current, index) => {
    if (index === 0) return max
    return Math.max(max, numericDiff(list[index - 1].value, current.value))
  }, 0)
}

export function leaderboardRowGapPercent(current: LeaderboardMember, previous: LeaderboardMember | undefined, maxGap: number) {
  if (!previous) return 100
  const diff = numericDiff(previous.value, current.value)
  if (diff <= 0.0001) return 100
  if (!maxGap) return 30
  return Math.round(Math.max(8, Math.min(100, 100 - (diff / maxGap) * 92)))
}

export function hasTopTie(top3: LeaderboardMember[] | undefined) {
  if (!top3 || top3.length < 2) return false
  return new Set(top3.map(item => item.rank)).size < top3.length
}

export function groupTop3ByRank(top3: LeaderboardMember[] | undefined, list: LeaderboardMember[] | undefined): LeaderboardGroup[] {
  if (!top3?.length) return []
  const groups: LeaderboardGroup[] = []

  top3.forEach((member) => {
    const group = groups.find(item => item.rank === member.rank)
    if (group) group.members.push(member)
    else groups.push({ rank: member.rank, members: [member], totalTied: 0 })
  })

  groups.forEach((group) => {
    const visibleTied = list?.filter(member => member.rank === group.rank).length || 0
    group.totalTied = Math.max(group.members.length, visibleTied)
  })

  return groups.sort((a, b) => a.rank - b.rank)
}

export function leaderboardMemberInitial(name: string | undefined) {
  return (name || '*').trim().charAt(0) || '*'
}
