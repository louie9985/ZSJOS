import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const homeSource = readFileSync(path.join(projectRoot, 'src/pages/home/index.vue'), 'utf8')
const leaderboardSource = readFileSync(path.join(projectRoot, 'src/pages/leaderboard/index.vue'), 'utf8')
const leaderboardUtilsSource = readFileSync(path.join(projectRoot, 'src/utils/leaderboard.ts'), 'utf8')
const crownSource = readFileSync(path.join(projectRoot, 'src/components/LeaderboardCrown.vue'), 'utf8')

test('首页简榜第一名跟随主题且详细榜保留默认奖牌配色', () => {
  assert.match(homeSource, /<LeaderboardCrown[^>]*tone="theme"/)
  assert.match(homeSource, /\.leaderboard-compact-row\.rank-1[^}]*var\(--h5-primary\)/)
  assert.match(homeSource, /\.leaderboard-compact-row\.rank-1 \.leaderboard-compact-row__avatar \{[^}]*var\(--h5-primary\)/)
  assert.doesNotMatch(homeSource, /#f4c95d|#694c00/)

  assert.match(crownSource, /tone\?: 'medal' \| 'theme'/)
  assert.match(crownSource, /tone: 'medal'/)
  assert.match(crownSource, /\.leaderboard-crown--theme \{ color: var\(--h5-primary-dark\); \}/)
  assert.match(crownSource, /\.leaderboard-crown--theme::before \{[^}]*var\(--h5-primary\)/)

  assert.match(leaderboardSource, /<LeaderboardCrown :rank="item\.member\.rank" :position="item\.slot" \/>/)
  assert.doesNotMatch(leaderboardSource, /<LeaderboardCrown[^>]*tone="theme"/)
})

test('排行榜名称、周期标签和首页详情参数保持一致', () => {
  assert.match(leaderboardUtilsSource, /today: '日榜'/)
  assert.match(leaderboardUtilsSource, /week: '周榜'/)
  assert.match(leaderboardUtilsSource, /month: '月榜'/)
  assert.match(leaderboardUtilsSource, /total: '总榜'/)
  assert.doesNotMatch(leaderboardUtilsSource, /今日榜/)
  assert.match(leaderboardUtilsSource, /normalized\.endsWith\('榜'\)/)

  assert.match(homeSource, /formatLeaderboardTitle\(leaderboard\.value\?\.typeLabel \|\| configuredType\?\.label\)/)
  assert.match(homeSource, /leaderboard\.value\?\.period \|\| leaderboardConfig\.value\?\.defaultPeriod/)
  assert.match(homeSource, /query: \{ type: current\.type, period: current\.period \}/)
  assert.match(homeSource, /class="leaderboard-card-header__period"/)
  assert.doesNotMatch(homeSource, /本月预计收益排行/)

  assert.match(leaderboardSource, /requestedPeriod\(\) \|\| result\.defaultPeriod/)
  assert.match(leaderboardSource, /requestedType\(result\.enabledTypes\)/)
  assert.match(leaderboardSource, /<van-nav-bar :title="leaderboardTitle"/)
})
