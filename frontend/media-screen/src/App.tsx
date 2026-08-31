import { History, Palette, Trophy, X } from 'lucide-react';
import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import { adaptHistory, adaptMaintenance, adaptStats, type HistoryModel, type MediaScreenModel, type ModuleState } from './adapter.ts';
import { getMaintenanceStatus, getMediaScreenHistory, getMediaScreenStats, MediaScreenApiError } from './api.ts';
import { applyDevelopmentMock, isDevelopmentMockEnabled } from './mock.ts';

type RefreshState = 'loading' | 'ready' | 'error';
type MaintenanceState = { enabled: boolean; reason?: string; checkedAt?: string | null };
type SortKey = 'today' | 'week' | 'monthTotal' | 'monthEffective';

type RankingPreference = {
  sortKey: SortKey;
};

type Metrics = {
  today: number;
  week: number;
  monthTotal: number;
  monthEffective: number;
};

type Member = {
  id: string;
  name: string;
  isDisabled?: boolean;
  partTimers?: PartTimeCompanionDetail[];
} & Metrics;

type PartTimeCompanionDetail = {
  id: string;
  actorId: string;
  name: string;
} & Metrics;

type Department = {
  id: string;
  name: string;
  subtitle: string;
  metrics: Metrics;
  members: Member[];
  unavailableReason?: string;
};

type TodayStar = {
  name: string;
  deptName: string;
  today: number;
  yesterday: number;
  rankToday: number;
  rankYesterday: number | null;
};

type YesterdayChampion = {
  name: string;
  deptName: string;
  count: number;
};

type StatsData = {
  updatedAt: string;
  refreshIntervalSeconds: number;
  partTimeIncluded: boolean;
  summary: Metrics;
  departments: Department[];
  partTimeCompanionDepartment?: Department;
  todayStar: TodayStar | null;
  yesterdayChampion: YesterdayChampion | null;
  trend: { today: number[]; yesterday: number[]; stepMinutes: number };
  series: { submitted: number[]; valid: number[] };
  moduleStates: {
    today: ModuleState;
    week: ModuleState;
    monthTotal: ModuleState;
    monthEffective: ModuleState;
    trend: ModuleState;
    departments: ModuleState;
    yesterdayChampion: ModuleState;
    partTimers: ModuleState;
  };
};

type HistoryData = {
  date: string;
  frozen: boolean;
  partTimeIncluded?: boolean;
  summary: Metrics;
  departments: Department[];
  partTimeCompanionDepartment?: Department;
};

const DEFAULT_REFRESH_SECONDS = 5;
const FLASH_MS = 1300;
const TREND_STEP_MIN = 10;
const INCLUDE_PART_TIMERS_PARAM = 'includePartTimers';
const INCLUDE_PART_TIMERS_TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);
const INCLUDE_PART_TIMERS_FALSE_VALUES = new Set(['0', 'false', 'no', 'off']);
const RANKING_STORE_KEY = 'media-screen-ranking';
const SORT_KEYS: SortKey[] = ['today', 'week', 'monthTotal', 'monthEffective'];
const DEFAULT_RANKING: RankingPreference = { sortKey: 'today' };
const MEMBER_SORT_OPTIONS: Array<{ key: SortKey; label: string }> = [
  { key: 'today', label: '今日客资' },
  { key: 'week', label: '本周客资' },
  { key: 'monthTotal', label: '本月总客资' },
  { key: 'monthEffective', label: '本月有效' },
];

function formatNumber(value: number | null) {
  return value === null ? '--' : new Intl.NumberFormat('zh-CN').format(value);
}

function unavailableDepartment(id: string, name: string, reason: string): Department {
  return {
    id,
    name,
    subtitle: reason,
    metrics: { today: 0, week: 0, monthTotal: 0, monthEffective: 0 },
    members: [],
    unavailableReason: reason,
  };
}

function toStatsData(model: MediaScreenModel, includePartTimers: boolean): StatsData {
  const summary = model.summary.value || { today: 0, week: 0, monthTotal: 0, monthEffective: 0 };
  const trend = model.trend.value || { today: [], yesterday: [], stepMinutes: TREND_STEP_MIN };
  const series = model.series.value || { submitted: [], valid: [] };
  const departments =
    model.departments.value && model.departments.value.length > 0
      ? model.departments.value
      : [
          unavailableDepartment(
            'media-departments-unavailable',
            '新媒体部门',
            model.departments.reason || '当前没有已配置的新媒体部门数据',
          ),
        ];
  const companion =
    model.partTimeCompanionDepartment.value ||
    unavailableDepartment(
      'part_time_companion',
      '兼职陪跑',
      model.partTimeCompanionDepartment.reason || '当前暂无兼职陪跑数据',
    );
  return {
    updatedAt: model.generatedAt || '',
    refreshIntervalSeconds: model.refreshIntervalSeconds,
    partTimeIncluded: includePartTimers,
    summary,
    departments,
    partTimeCompanionDepartment: companion,
    todayStar: model.todayStar.value,
    yesterdayChampion: model.yesterdayChampion.value,
    trend,
    series,
    moduleStates: {
      today: model.summary.state,
      week: model.summary.state,
      monthTotal: model.summary.state,
      monthEffective: model.summary.state,
      trend: model.trend.state,
      departments: model.departments.state,
      yesterdayChampion: model.yesterdayChampion.state,
      partTimers: model.partTimeCompanionDepartment.state,
    },
  };
}

function toHistoryData(model: HistoryModel, includePartTimers: boolean): HistoryData {
  const byDepartment = new Map<string, Member[]>();
  model.mainRanking.forEach((row, index) => {
    const members = byDepartment.get(row.departmentName) || [];
    members.push({
      id: `history-${index}-${row.name}`,
      name: row.name,
      today: row.today || 0,
      week: row.week || 0,
      monthTotal: row.monthTotal || 0,
      monthEffective: row.monthEffective || 0,
    });
    byDepartment.set(row.departmentName, members);
  });
  const departments = [...byDepartment.entries()].map(([name, members], index) => ({
    id: `history-dept-${index}`,
    name,
    subtitle: '',
    metrics: {
      today: members.reduce((sum, member) => sum + member.today, 0),
      week: members.reduce((sum, member) => sum + member.week, 0),
      monthTotal: members.reduce((sum, member) => sum + member.monthTotal, 0),
      monthEffective: members.reduce((sum, member) => sum + member.monthEffective, 0),
    },
    members,
  }));
  const companionMembers = model.partTimerRanking.map((row, index) => ({
    id: `history-part-${index}-${row.name}`,
    name: row.name,
    today: row.today || 0,
    week: row.week || 0,
    monthTotal: row.monthTotal || 0,
    monthEffective: row.monthEffective || 0,
  }));
  return {
    date: model.snapshotDate || '',
    frozen: model.available,
    partTimeIncluded: includePartTimers,
    summary: {
      today: model.summary.today || 0,
      week: model.summary.week || 0,
      monthTotal: model.summary.monthTotal ?? model.totalLeads ?? 0,
      monthEffective: model.summary.monthEffective || 0,
    },
    departments,
    partTimeCompanionDepartment: includePartTimers
      ? {
          id: 'part_time_companion',
          name: '兼职陪跑',
          subtitle: '',
          metrics: { today: 0, week: 0, monthTotal: 0, monthEffective: 0 },
          members: companionMembers,
        }
      : undefined,
  };
}

function parseIncludePartTimers(value: string | null) {
  if (value === null) {
    return true;
  }
  const normalized = value.trim().toLowerCase();
  if (INCLUDE_PART_TIMERS_FALSE_VALUES.has(normalized)) {
    return false;
  }
  if (INCLUDE_PART_TIMERS_TRUE_VALUES.has(normalized)) {
    return true;
  }
  return true;
}

function loadIncludePartTimers() {
  return parseIncludePartTimers(new URLSearchParams(window.location.search).get(INCLUDE_PART_TIMERS_PARAM));
}

function syncIncludePartTimersUrl(includePartTimers: boolean) {
  const url = new URL(window.location.href);
  url.searchParams.set(INCLUDE_PART_TIMERS_PARAM, includePartTimers ? '1' : '0');
  window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
}

function loadRankingPreference(): RankingPreference {
  try {
    const parsed = JSON.parse(localStorage.getItem(RANKING_STORE_KEY) || '{}') as Partial<RankingPreference>;
    if (SORT_KEYS.includes(parsed.sortKey as SortKey)) {
      return { sortKey: parsed.sortKey as SortKey };
    }
  } catch {
    // 存储不可用或数据损坏时回到默认排行。
  }
  return DEFAULT_RANKING;
}

function formatClock(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function timeOnly(value?: string) {
  if (!value) {
    return '--:--:--';
  }
  const parts = value.split(' ');
  return parts.length > 1 ? parts[1] : value;
}

function pad2(value: number) {
  return String(value).padStart(2, '0');
}

function sortedMembers(department: Department, sortKey: SortKey) {
  return [...department.members].sort(
    (a, b) => b[sortKey] - a[sortKey] || a.name.localeCompare(b.name),
  );
}

function competitionRankMap<T extends { id: string; name: string } & Metrics>(rows: T[], sortKey: SortKey) {
  const ranks = new Map<string, number | null>();
  const sorted = [...rows].sort(
    (a, b) => b[sortKey] - a[sortKey] || a.name.localeCompare(b.name) || a.id.localeCompare(b.id),
  );
  let previousValue: number | null = null;
  let currentRank: number | null = null;

  sorted.forEach((row, index) => {
    const value = row[sortKey];
    if (value <= 0) {
      ranks.set(row.id, null);
      return;
    }
    if (value !== previousValue) {
      currentRank = index + 1;
      previousValue = value;
    }
    ranks.set(row.id, currentRank);
  });

  return ranks;
}

function memberNumClass(value: number, variant: 'today' | 'effective' | 'plain', flashing: boolean) {
  return ['member-num', variant === 'plain' ? '' : variant, value === 0 ? 'zero' : '', flashing ? 'is-bump' : '']
    .filter(Boolean)
    .join(' ');
}

/* ============ 数字变化检测：把每个数值格拍平成 key->value，比对上一帧 ============ */
function buildValueMap(data: StatsData): Map<string, number> {
  const map = new Map<string, number>();
  map.set('kpi:today', data.summary.today);
  map.set('kpi:week', data.summary.week);
  map.set('kpi:monthTotal', data.summary.monthTotal);
  map.set('kpi:monthEffective', data.summary.monthEffective);
  const departments = data.partTimeCompanionDepartment ? [...data.departments, data.partTimeCompanionDepartment] : data.departments;
  for (const dept of departments) {
    map.set(`dept:${dept.id}:today`, dept.metrics.today);
    map.set(`dept:${dept.id}:week`, dept.metrics.week);
    map.set(`dept:${dept.id}:monthTotal`, dept.metrics.monthTotal);
    for (const member of dept.members) {
      map.set(`m:${dept.id}:${member.id}:today`, member.today);
      map.set(`m:${dept.id}:${member.id}:week`, member.week);
      map.set(`m:${dept.id}:${member.id}:monthTotal`, member.monthTotal);
    }
  }
  return map;
}

/* ============ 当日累计走势图（数据来自后端，横轴 0 点 → 当前）============ */
function buildHourTicks(pointCount: number, stepMinutes: number) {
  const spanMin = Math.max(1, (pointCount - 1) * stepMinutes);
  const nowHour = Math.floor(spanMin / 60);
  const labelStep = nowHour <= 8 ? 2 : nowHour <= 16 ? 3 : 4;
  const ticks: Array<{ x: number; label: string | null }> = [];
  for (let h = 0; h <= nowHour; h += 1) {
    const x = (h * 60) / spanMin * 100;
    ticks.push({ x, label: h % labelStep === 0 && x < 92 ? `${h}:00` : null });
  }
  return ticks;
}

function TrendChart({
  points,
  ghost,
  nowLabel,
  peakBump,
  stepMinutes,
  emptyText,
}: {
  points: number[];
  ghost: number[];
  nowLabel: string;
  peakBump: boolean;
  stepMinutes: number;
  emptyText: string;
}) {
  const width = 100;
  const height = 100;
  if (points.length < 2) {
    return <div className="trend-empty">{emptyText}</div>;
  }
  const max = Math.max(...points, ...ghost, 1);
  const project = (arr: number[]) =>
    arr.map((value, index) => {
      const x = (index / (arr.length - 1)) * width;
      const y = height - (value / max) * (height - 10) - 5;
      return [x, y] as const;
    });
  const todayCoords = project(points);
  const ghostCoords = project(ghost.length === points.length ? ghost : points.map(() => 0));
  const toPath = (coords: ReadonlyArray<readonly [number, number]>) =>
    coords.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`).join(' ');
  const todayLine = toPath(todayCoords);
  const ghostLine = toPath(ghostCoords);
  const areaPath = `${todayLine} L${width},${height} L0,${height} Z`;
  const [cursorX, cursorY] = todayCoords[todayCoords.length - 1];
  const ticks = buildHourTicks(points.length, stepMinutes);

  return (
    <div className="trend-wrap">
      <svg className="trend-svg" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
            <stop className="trend-fill-top" offset="0%" />
            <stop className="trend-fill-bottom" offset="100%" />
          </linearGradient>
        </defs>
        {ticks.map((tick, i) => (
          <line key={i} className="trend-grid-v" x1={tick.x} y1="0" x2={tick.x} y2={height} vectorEffect="non-scaling-stroke" />
        ))}
        {ghost.length === points.length && ghost.length > 0 && (
          <path
            className="trend-ghost"
            d={ghostLine}
            fill="none"
            stroke="#6b7180"
            strokeWidth={1.2}
            strokeDasharray="3 3"
            vectorEffect="non-scaling-stroke"
          />
        )}
        <path d={areaPath} fill="url(#trendFill)" />
        <path
          className="trend-line"
          d={todayLine}
          fill="none"
          strokeWidth={1.7}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
        <circle className={`trend-cursor ${peakBump ? 'is-bump' : ''}`} cx={cursorX} cy={cursorY} r={2.6} />
      </svg>
      <div className="trend-axis" aria-hidden="true">
        {ticks.map((tick, i) =>
          tick.label ? (
            <span className="trend-tick" key={i} style={{ left: `${tick.x}%` }}>
              {tick.label}
            </span>
          ) : null,
        )}
        <span className="trend-tick trend-tick-now" style={{ left: '100%' }}>
          {nowLabel}
        </span>
      </div>
    </div>
  );
}

function MiniSpark({ points, tone }: { points: number[]; tone: 'slate' | 'green' }) {
  const width = 100;
  const height = 34;
  if (points.length < 2) {
    return null;
  }
  const max = Math.max(...points);
  const min = Math.min(...points);
  const range = max - min || 1;
  const stepX = width / (points.length - 1);
  const coords = points.map((value, index) => {
    const x = index * stepX;
    const y = height - ((value - min) / range) * (height - 8) - 4;
    return [x, y] as const;
  });
  const line = coords.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`).join(' ');
  const area = `${line} L${width},${height} L0,${height} Z`;
  return (
    <svg className={`mini-spark mini-${tone}`} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
      <path className="mini-area" d={area} />
      <path className="mini-line" d={line} fill="none" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

function TodayStarCard({
  star,
  yesterdayChampion,
}: {
  star: TodayStar | null;
  yesterdayChampion: YesterdayChampion | null;
}) {
  const delta = star ? star.today - star.yesterday : 0;
  const deltaText = delta > 0 ? `↑${delta}` : delta < 0 ? `↓${Math.abs(delta)}` : '持平';
  const deltaClass = delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat';
  const champGap = star && yesterdayChampion ? star.today - yesterdayChampion.count : null;
  const chaseClass = champGap === null ? 'tie' : champGap > 0 ? 'ahead' : champGap < 0 ? 'behind' : 'tie';

  return (
    <div className="star-card">
      <div className="star-head">
        <Trophy size={18} strokeWidth={2.4} />
        <span>今日之星</span>
      </div>

      <div className="star-versus">
        <div className="star-col is-today">
          <div className="star-col-tag">今日冠军</div>
          {star ? (
            <>
              <div className="star-col-name">{star.name}</div>
              <div className="star-col-dept">{star.deptName}</div>
              <div className="star-col-count">
                {formatNumber(star.today)}
                <span className="star-col-unit">条</span>
                <span className="star-col-asof">截至现在</span>
              </div>
              <div className="star-col-sub">
                <span className={`star-sub-delta ${deltaClass}`}>较昨日 {deltaText}</span>
                {star.rankYesterday ? (
                  <span className="star-sub-rank">
                    第{star.rankYesterday}→{star.rankToday}
                  </span>
                ) : null}
              </div>
            </>
          ) : (
            <div className="star-col-placeholder">今日暂无提交</div>
          )}
        </div>

        <div className="star-col is-yesterday">
          <div className="star-col-tag">昨日冠军</div>
          {yesterdayChampion ? (
            <>
              <div className="star-col-name">{yesterdayChampion.name}</div>
              <div className="star-col-dept">{yesterdayChampion.deptName}</div>
              <div className="star-col-count">
                {formatNumber(yesterdayChampion.count)}
                <span className="star-col-unit">条</span>
              </div>
              <div className="star-col-sub">
                <span className="star-sub-rank">当日第1</span>
              </div>
            </>
          ) : (
            <div className="star-col-placeholder">暂无昨日数据</div>
          )}
        </div>
      </div>

      {champGap !== null && (
        <div className={`star-compare-bar ${chaseClass}`}>
          {champGap < 0 && (
            <>
              还差 <strong>{-champGap}</strong> 条超越昨日冠军
            </>
          )}
          {champGap === 0 && <>已追平昨日冠军</>}
          {champGap > 0 && (
            <>
              已超越昨日冠军 <strong>+{champGap}</strong> 条 🎉
            </>
          )}
        </div>
      )}
    </div>
  );
}

function DepartmentCard({
  department,
  index,
  flashKeys,
  ranking,
  onSort,
}: {
  department: Department;
  index: number;
  flashKeys: Set<string>;
  ranking: RankingPreference;
  onSort: (key: SortKey) => void;
}) {
  const [openMemberId, setOpenMemberId] = useState<string | null>(null);
  const isCompanion = department.id === 'part_time_companion';
  const isUnavailable = Boolean(department.unavailableReason);
  const memberTitle = isCompanion ? '员工陪跑排行' : '成员排行';
  const emptyText = department.unavailableReason || (isCompanion ? '当前暂无有兼职员工' : '本部门暂无在职成员数据');
  const memberRanks = competitionRankMap(department.members, ranking.sortKey);

  return (
    <article className={`dept-card dept-${index + 1}`}>
      <div className="dept-header">
        <div className="dept-tag" />
        <div className="dept-name">{department.name}</div>
        <div className="dept-subname">{department.subtitle}</div>
      </div>

      <div className="dept-metrics">
        <div className="dept-metric today">
          <span className="dept-metric-label">今日客资</span>
          <span className={`dept-metric-value ${flashKeys.has(`dept:${department.id}:today`) ? 'is-bump' : ''}`}>
            {isUnavailable ? '--' : formatNumber(department.metrics.today)}
          </span>
        </div>
        <div className="dept-metric">
          <span className="dept-metric-label">本周客资</span>
          <span className={`dept-metric-value ${flashKeys.has(`dept:${department.id}:week`) ? 'is-bump' : ''}`}>
            {isUnavailable ? '--' : formatNumber(department.metrics.week)}
          </span>
        </div>
        <div className="dept-metric">
          <span className="dept-metric-label">本月总客资</span>
          <span className={`dept-metric-value ${flashKeys.has(`dept:${department.id}:monthTotal`) ? 'is-bump' : ''}`}>
            {isUnavailable ? '--' : formatNumber(department.metrics.monthTotal)}
          </span>
        </div>
        <div className="dept-metric effective">
          <span className="dept-metric-label">本月有效客资</span>
          <span className="dept-metric-value">{isUnavailable ? '--' : formatNumber(department.metrics.monthEffective)}</span>
        </div>
      </div>

      <div className="member-section">
        <div className="member-section-title">{memberTitle}</div>
        <div className="member-table">
          <div className="member-row header">
            <span>姓名</span>
            {MEMBER_SORT_OPTIONS.map((option) => {
              const active = ranking.sortKey === option.key;
              return (
                <button
                  className={`member-num member-sort ${active ? 'active' : ''}`}
                  type="button"
                  aria-label={`按${option.label}从高到低排行`}
                  aria-pressed={active}
                  disabled={isUnavailable}
                  onClick={() => onSort(option.key)}
                  key={option.key}
                >
                  {option.label}
                </button>
              );
            })}
          </div>

          {department.members.length === 0 ? (
            <div className="member-empty">{emptyText}</div>
          ) : (
            sortedMembers(department, ranking.sortKey).map((member) => {
              const rank = memberRanks.get(member.id) ?? null;
              const todayFlash = flashKeys.has(`m:${department.id}:${member.id}:today`);
              const rowContent = (
                <>
                  <span className="member-name">
                    <span className={`rank-num ${rank ? `rank-${rank}` : 'rank-none'}`}>{rank ?? '—'}</span>
                    {member.name}
                    {member.isDisabled && <span className="disabled-tag">停用</span>}
                  </span>
                  <span className={memberNumClass(member.today, 'today', todayFlash)}>{formatNumber(member.today)}</span>
                  <span className={memberNumClass(member.week, 'plain', flashKeys.has(`m:${department.id}:${member.id}:week`))}>
                    {formatNumber(member.week)}
                  </span>
                  <span className={memberNumClass(member.monthTotal, 'plain', flashKeys.has(`m:${department.id}:${member.id}:monthTotal`))}>
                    {formatNumber(member.monthTotal)}
                  </span>
                  <span className={memberNumClass(member.monthEffective, 'effective', false)}>
                    {formatNumber(member.monthEffective)}
                  </span>
                </>
              );
              if (!isCompanion) {
                return (
                  <div className={`member-row ${todayFlash ? 'is-row-bump' : ''}`} key={member.id}>
                    {rowContent}
                  </div>
                );
              }
              return (
                <div className="member-row-shell" key={member.id}>
                  <button
                    className={`member-row member-row-button ${todayFlash ? 'is-row-bump' : ''}`}
                    type="button"
                    onClick={() => setOpenMemberId((current) => (current === member.id ? null : member.id))}
                  >
                    {rowContent}
                  </button>
                  {openMemberId === member.id && <PartTimeDetailsPopover member={member} />}
                </div>
              );
            })
          )}
        </div>
      </div>
    </article>
  );
}

function PartTimeDetailsPopover({ member }: { member: Member }) {
  const rows = member.partTimers ?? [];
  return (
    <div className="parttime-popover" role="dialog" aria-label={`${member.name}名下兼职提交明细`}>
      <div className="parttime-popover-head">
        <span>名下兼职明细</span>
        <strong>{member.name}</strong>
      </div>
      <div className="parttime-detail-grid parttime-detail-header">
        <span>兼职</span>
        <span>今日</span>
        <span>本周</span>
        <span>月总</span>
        <span>有效</span>
      </div>
      {rows.length === 0 ? (
        <div className="parttime-popover-empty">暂无兼职提交</div>
      ) : (
        rows.map((item) => (
          <div className="parttime-detail-grid" key={`${item.id}-${item.actorId}`}>
            <span className="parttime-detail-name">{item.name}</span>
            <span className="today">{formatNumber(item.today)}</span>
            <span>{formatNumber(item.week)}</span>
            <span>{formatNumber(item.monthTotal)}</span>
            <span className="effective">{formatNumber(item.monthEffective)}</span>
          </div>
        ))
      )}
      <div className="parttime-detail-grid parttime-detail-total">
        <span>合计</span>
        <span className="today">{formatNumber(member.today)}</span>
        <span>{formatNumber(member.week)}</span>
        <span>{formatNumber(member.monthTotal)}</span>
        <span className="effective">{formatNumber(member.monthEffective)}</span>
      </div>
    </div>
  );
}

/* ============ 历史页：读后端定格快照，汇总成一张可排序全员表，供人事查数/颁奖 ============ */
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function dateKey(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

type HistoryRow = {
  id: string;
  name: string;
  deptName: string;
  today: number;
  week: number;
  monthTotal: number;
  monthEffective: number;
};

function flattenHistoryRows(data: HistoryData | null): HistoryRow[] {
  if (!data) {
    return [];
  }
  const rows: HistoryRow[] = [];
  for (const dept of data.departments) {
    for (const member of dept.members) {
      rows.push({
        id: `${dept.id}-${member.id}`,
        name: member.name,
        deptName: dept.name,
        today: member.today,
        week: member.week,
        monthTotal: member.monthTotal,
        monthEffective: member.monthEffective,
      });
    }
  }
  return rows;
}

/* ============ 自定义深色日历（替换原生 date 选择器，跟随主题+亮度）============ */
const CAL_WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日'];
const CAL_MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function DarkDatePicker({ value, max, onChange }: { value: Date; max: Date; onChange: (d: Date) => void }) {
  const [open, setOpen] = useState(false);
  const [viewYear, setViewYear] = useState(value.getFullYear());
  const [viewMonth, setViewMonth] = useState(value.getMonth());
  const [picking, setPicking] = useState(false); // true 时显示年/月快速跳

  const openPanel = () => {
    setViewYear(value.getFullYear());
    setViewMonth(value.getMonth());
    setPicking(false);
    setOpen(true);
  };

  const stepMonth = (delta: number) => {
    const next = new Date(viewYear, viewMonth + delta, 1);
    setViewYear(next.getFullYear());
    setViewMonth(next.getMonth());
  };

  const firstOffset = (new Date(viewYear, viewMonth, 1).getDay() + 6) % 7; // 周一为首
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const cells: Array<number | null> = [
    ...Array(firstOffset).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  const pick = (day: number) => {
    const d = new Date(viewYear, viewMonth, day);
    if (d > max) {
      return;
    }
    onChange(d);
    setOpen(false);
  };

  return (
    <div className="dark-date">
      <button className="dark-date-trigger" type="button" onClick={() => (open ? setOpen(false) : openPanel())}>
        📅 {dateKey(value)}
      </button>
      {open && (
        <>
          <div className="dark-date-backdrop" onClick={() => setOpen(false)} />
          <div className="dark-date-pop" role="dialog" aria-label="选择日期">
            {picking ? (
              <div className="dark-date-quick">
                <div className="dark-date-yearrow">
                  <button type="button" onClick={() => setViewYear((y) => y - 1)}>
                    ‹
                  </button>
                  <span>{viewYear} 年</span>
                  <button type="button" onClick={() => setViewYear((y) => Math.min(max.getFullYear(), y + 1))}>
                    ›
                  </button>
                </div>
                <div className="dark-date-months">
                  {CAL_MONTHS.map((label, index) => {
                    const disabled = new Date(viewYear, index, 1) > new Date(max.getFullYear(), max.getMonth(), 1);
                    return (
                      <button
                        key={label}
                        type="button"
                        className={`dark-date-month ${index === viewMonth ? 'active' : ''}`}
                        disabled={disabled}
                        onClick={() => {
                          setViewMonth(index);
                          setPicking(false);
                        }}
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>
              </div>
            ) : (
              <>
                <div className="dark-date-head">
                  <button type="button" onClick={() => stepMonth(-1)}>
                    ‹
                  </button>
                  <button type="button" className="dark-date-title" onClick={() => setPicking(true)}>
                    {viewYear}年{viewMonth + 1}月
                  </button>
                  <button
                    type="button"
                    onClick={() => stepMonth(1)}
                    disabled={new Date(viewYear, viewMonth + 1, 1) > new Date(max.getFullYear(), max.getMonth(), 1)}
                  >
                    ›
                  </button>
                </div>
                <div className="dark-date-grid dark-date-weekhead">
                  {CAL_WEEKDAYS.map((w) => (
                    <span className="dark-date-wk" key={w}>
                      {w}
                    </span>
                  ))}
                </div>
                <div className="dark-date-grid">
                  {cells.map((day, index) => {
                    if (day === null) {
                      return <span key={`e-${index}`} />;
                    }
                    const cellDate = new Date(viewYear, viewMonth, day);
                    const disabled = cellDate > max;
                    const selectedDay = sameDay(cellDate, value);
                    return (
                      <button
                        key={day}
                        type="button"
                        className={`dark-date-day ${selectedDay ? 'active' : ''}`}
                        disabled={disabled}
                        onClick={() => pick(day)}
                      >
                        {day}
                      </button>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function HistoryOverlay({ includePartTimers, onClose }: { includePartTimers: boolean; onClose: () => void }) {
  const today = useState(() => new Date())[0];
  // 最大可查日期 = 昨天（今天还没定格）；不再限制最早日期，任意历史日期都能查。
  const yesterday = useState(() => {
    const d = new Date(today);
    d.setDate(d.getDate() - 1);
    return d;
  })[0];

  const [selected, setSelected] = useState<Date>(() => yesterday);
  const [data, setData] = useState<HistoryData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);
  const [sortKey, setSortKey] = useState<SortKey>('today');
  const [sortDir, setSortDir] = useState<'desc' | 'asc'>('desc');
  const [grouped, setGrouped] = useState(false);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    getMediaScreenHistory(dateKey(selected), includePartTimers)
      .then((payload) => {
        if (!ignore) {
          setData(toHistoryData(adaptHistory(payload), includePartTimers));
          setLoading(false);
        }
      })
      .catch((reason: unknown) => {
        if (!ignore) {
          setData(null);
          setError(reason instanceof Error ? reason.message : '历史快照查询失败');
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [includePartTimers, retryVersion, selected]);

  const canNext = selected < yesterday;

  const shift = (days: number) => {
    setSelected((prev) => {
      const next = new Date(prev);
      next.setDate(next.getDate() + days);
      if (next > yesterday) {
        return prev;
      }
      return next;
    });
  };

  // 点指标列：全员平铺按该指标排（退出分组）
  const sortByMetric = (key: SortKey) => {
    setGrouped(false);
    if (key === sortKey) {
      setSortDir((dir) => (dir === 'desc' ? 'asc' : 'desc'));
    } else {
      setSortKey(key);
      setSortDir('desc');
    }
  };

  const weekday = WEEKDAYS[selected.getDay()];
  const frozen = data?.frozen ?? false;
  const rows = flattenHistoryRows(data);
  const dir = sortDir === 'desc' ? 1 : -1;
  const sortedRows = [...rows].sort((a, b) => dir * (b[sortKey] - a[sortKey]) || a.name.localeCompare(b.name));
  const rowRanks = competitionRankMap(rows, sortKey);
  // 分组模式：部门按"当前指标合计"排名，部门内成员按同指标排名（名次部门内重排）
  const groups = (data?.departments ?? [])
    .map((dept) => {
      const ranks = competitionRankMap(dept.members, sortKey);
      return {
        id: dept.id,
        name: dept.name,
        metrics: dept.metrics,
        members: [...dept.members].sort((a, b) => dir * (b[sortKey] - a[sortKey]) || a.name.localeCompare(b.name)),
        ranks,
      };
    })
    .sort((a, b) => dir * (b.metrics[sortKey] - a.metrics[sortKey]) || a.name.localeCompare(b.name));
  const companionDepartment = includePartTimers ? data?.partTimeCompanionDepartment : undefined;
  const companionRows = companionDepartment
    ? [...companionDepartment.members].sort((a, b) => dir * (b[sortKey] - a[sortKey]) || a.name.localeCompare(b.name))
    : [];
  const companionRanks = competitionRankMap(companionDepartment?.members ?? [], sortKey);
  const hasCompanionRows = companionRows.length > 0;
  const hasRows = frozen && rows.length > 0;
  const summaryCountText = includePartTimers
    ? `${rows.length} 人 + 陪跑员工 ${companionRows.length} 人`
    : `${rows.length} 人`;

  const sortIndicator = (key: SortKey) => (sortKey === key ? (sortDir === 'desc' ? ' ↓' : ' ↑') : '');

  return (
    <div className="history-overlay" role="dialog" aria-modal="true" aria-label="历史成绩">
      <div className="history-head">
        <div className="history-title-block">
          <History size={22} strokeWidth={2.2} />
          <div className="history-title">历史成绩</div>
          <div className="history-date">
            {dateKey(selected)}（{weekday}）
          </div>
          {frozen && <div className="history-frozen-tag">已定格</div>}
        </div>
        <div className="history-actions">
          <DarkDatePicker value={selected} max={yesterday} onChange={(d) => setSelected(d)} />
          <button className="history-nav" type="button" onClick={() => shift(-1)}>
            ← 前一天
          </button>
          <button className="history-nav" type="button" onClick={() => shift(1)} disabled={!canNext}>
            后一天 →
          </button>
          <button className="history-close" type="button" onClick={onClose} aria-label="关闭">
            <X size={20} strokeWidth={2.4} />
          </button>
        </div>
      </div>

      {loading ? (
        <div className="history-empty">加载中…</div>
      ) : error ? (
        <div className="history-empty history-error-state">
          <strong>历史快照加载失败</strong>
          <span>{error}</span>
          <button className="history-nav" type="button" onClick={() => setRetryVersion((value) => value + 1)}>
            重新请求
          </button>
        </div>
      ) : !hasRows ? (
        <div className="history-empty">该日期暂无历史快照</div>
      ) : (
        <>
          <section className="history-summary-strip" aria-label="全员合计">
            <div className="history-summary-copy">
              <div className="history-summary-title">{includePartTimers ? '全员合计（含陪跑）' : '全员合计'}</div>
              <div className="history-summary-sub">{summaryCountText}</div>
            </div>
            <div className="history-summary-metrics">
              <span>当日 {formatNumber(data!.summary.today)}</span>
              <span>本周 {formatNumber(data!.summary.week)}</span>
              <span>本月 {formatNumber(data!.summary.monthTotal)}</span>
              <span>有效 {formatNumber(data!.summary.monthEffective)}</span>
            </div>
          </section>

          <div className={`history-board-grid ${includePartTimers ? '' : 'single'}`}>
            <section className="history-board-panel history-main-panel" aria-label="主榜">
              <div className="history-board-head">
                <div>
                  <div className="history-board-title">主榜</div>
                  <div className="history-board-sub">主管/员工本人归属，不计兼职陪跑</div>
                </div>
              </div>
              <div className="history-panel-scroll">
                <table className="history-table history-main-table">
                  <thead>
                    <tr>
                      <th className="col-rank">排名</th>
                      <th className="col-name">姓名</th>
                      <th className={`col-dept sortable-dept ${grouped ? 'is-grouped' : ''}`} onClick={() => setGrouped((g) => !g)}>
                        部门{grouped ? ' ▦' : ' ⤵'}
                      </th>
                      <th className="col-num sortable" onClick={() => sortByMetric('today')}>
                        当日{sortIndicator('today')}
                      </th>
                      <th className="col-num sortable" onClick={() => sortByMetric('week')}>
                        本周{sortIndicator('week')}
                      </th>
                      <th className="col-num sortable" onClick={() => sortByMetric('monthTotal')}>
                        本月{sortIndicator('monthTotal')}
                      </th>
                      <th className="col-num sortable" onClick={() => sortByMetric('monthEffective')}>
                        有效{sortIndicator('monthEffective')}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {grouped
                      ? groups.map((group) => (
                          <Fragment key={group.id}>
                            <tr className="history-group-row">
                              <td className="col-rank" />
                              <td className="col-name" colSpan={2}>
                                {group.name}
                              </td>
                              <td className="col-num today">{formatNumber(group.metrics.today)}</td>
                              <td className="col-num">{formatNumber(group.metrics.week)}</td>
                              <td className="col-num">{formatNumber(group.metrics.monthTotal)}</td>
                              <td className="col-num effective">{formatNumber(group.metrics.monthEffective)}</td>
                            </tr>
                            {group.members.map((member) => {
                              const rank = group.ranks.get(member.id) ?? null;
                              return (
                                <tr key={`${group.id}-${member.id}`}>
                                  <td className="col-rank">
                                    <span className={`history-rank ${rank ? `rank-${rank}` : 'rank-none'}`}>{rank ?? '—'}</span>
                                  </td>
                                  <td className="col-name">{member.name}</td>
                                  <td className="col-dept">{group.name}</td>
                                  <td className={`col-num today ${member.today === 0 ? 'zero' : ''}`}>{formatNumber(member.today)}</td>
                                  <td className={`col-num ${member.week === 0 ? 'zero' : ''}`}>{formatNumber(member.week)}</td>
                                  <td className={`col-num ${member.monthTotal === 0 ? 'zero' : ''}`}>
                                    {formatNumber(member.monthTotal)}
                                  </td>
                                  <td className={`col-num effective ${member.monthEffective === 0 ? 'zero' : ''}`}>
                                    {formatNumber(member.monthEffective)}
                                  </td>
                                </tr>
                              );
                            })}
                          </Fragment>
                        ))
                      : sortedRows.map((row) => {
                          const rank = rowRanks.get(row.id) ?? null;
                          return (
                            <tr key={row.id}>
                              <td className="col-rank">
                                <span className={`history-rank ${rank ? `rank-${rank}` : 'rank-none'}`}>{rank ?? '—'}</span>
                              </td>
                              <td className="col-name">{row.name}</td>
                              <td className="col-dept">{row.deptName}</td>
                              <td className={`col-num today ${row.today === 0 ? 'zero' : ''}`}>{formatNumber(row.today)}</td>
                              <td className={`col-num ${row.week === 0 ? 'zero' : ''}`}>{formatNumber(row.week)}</td>
                              <td className={`col-num ${row.monthTotal === 0 ? 'zero' : ''}`}>{formatNumber(row.monthTotal)}</td>
                              <td className={`col-num effective ${row.monthEffective === 0 ? 'zero' : ''}`}>
                                {formatNumber(row.monthEffective)}
                              </td>
                            </tr>
                          );
                        })}
                  </tbody>
                </table>
              </div>
            </section>

            {includePartTimers && (
              <section className="history-board-panel history-companion-panel" aria-label="兼职陪跑客资排行">
                <div className="history-board-head">
                  <div>
                    <div className="history-board-title">兼职陪跑客资排行</div>
                    <div className="history-board-sub">员工名下兼职提交合计，不含员工本人归属客资</div>
                  </div>
                </div>
                <div className="history-panel-scroll">
                  {hasCompanionRows ? (
                    <table className="history-table history-companion-table">
                      <thead>
                        <tr>
                          <th className="col-rank">排名</th>
                          <th className="col-name">新媒体员工</th>
                          <th className="col-num sortable" onClick={() => sortByMetric('today')}>
                            当日{sortIndicator('today')}
                          </th>
                          <th className="col-num sortable" onClick={() => sortByMetric('week')}>
                            本周{sortIndicator('week')}
                          </th>
                          <th className="col-num sortable" onClick={() => sortByMetric('monthTotal')}>
                            本月{sortIndicator('monthTotal')}
                          </th>
                          <th className="col-num sortable" onClick={() => sortByMetric('monthEffective')}>
                            有效{sortIndicator('monthEffective')}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {companionRows.map((row) => {
                          const rank = companionRanks.get(row.id) ?? null;
                          return (
                            <tr key={`companion-${row.id}`}>
                              <td className="col-rank">
                                <span className={`history-rank ${rank ? `rank-${rank}` : 'rank-none'}`}>{rank ?? '—'}</span>
                              </td>
                              <td className="col-name">{row.name}</td>
                              <td className={`col-num today ${row.today === 0 ? 'zero' : ''}`}>{formatNumber(row.today)}</td>
                              <td className={`col-num ${row.week === 0 ? 'zero' : ''}`}>{formatNumber(row.week)}</td>
                              <td className={`col-num ${row.monthTotal === 0 ? 'zero' : ''}`}>{formatNumber(row.monthTotal)}</td>
                              <td className={`col-num effective ${row.monthEffective === 0 ? 'zero' : ''}`}>
                                {formatNumber(row.monthEffective)}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  ) : (
                    <div className="history-panel-empty">暂无兼职陪跑定格数据</div>
                  )}
                </div>
              </section>
            )}
          </div>
        </>
      )}
    </div>
  );
}

/* ============ 配色主题：4 套 + 底色亮度，存 localStorage 记住 ============ */
type ThemeId = 'A' | 'B' | 'C' | 'D';

const THEMES: Array<{ id: ThemeId; label: string; swatch: string }> = [
  { id: 'A', label: '暖石墨', swatch: '#ffc861' },
  { id: 'B', label: '午夜蓝', swatch: '#3fd2ff' },
  { id: 'C', label: '深空紫', swatch: '#2fd6c0' },
  { id: 'D', label: '墨绿', swatch: '#e7c873' },
];

// 每套配色的底色区间：[最暗(默认), 最亮档]，亮度滑块在两端之间插值。
const BG_RANGE: Record<ThemeId, [string, string]> = {
  A: ['#0e0d12', '#2a2733'],
  B: ['#0a0f1c', '#2f4168'],
  C: ['#100b1a', '#2c2444'],
  D: ['#0b1110', '#243430'],
};

const THEME_STORE_KEY = 'mediaScreenTheme';
const DEFAULT_THEME: ThemeId = 'B';

function hexToRgb(hex: string): [number, number, number] {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)) as [number, number, number];
}

function mixBg(theme: ThemeId, lift: number): string {
  const [dark, light] = BG_RANGE[theme];
  const dr = hexToRgb(dark);
  const lr = hexToRgb(light);
  const mixed = dr.map((v, i) => Math.round(v + (lr[i] - v) * lift));
  return `#${mixed.map((x) => x.toString(16).padStart(2, '0')).join('')}`;
}

function loadThemePref(): { theme: ThemeId; lift: number } {
  try {
    const raw = localStorage.getItem(THEME_STORE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as { theme?: ThemeId; lift?: number };
      const theme = parsed.theme && BG_RANGE[parsed.theme] ? parsed.theme : DEFAULT_THEME;
      const lift = typeof parsed.lift === 'number' ? Math.min(1, Math.max(0, parsed.lift)) : 0;
      return { theme, lift };
    }
  } catch {
    // 读不到就用默认
  }
  return { theme: DEFAULT_THEME, lift: 0 };
}

function applyTheme(theme: ThemeId, lift: number) {
  const root = document.documentElement;
  root.setAttribute('data-theme', theme);
  root.style.setProperty('--bg', mixBg(theme, lift));
}

function ThemePanel({
  theme,
  lift,
  onTheme,
  onLift,
  onClose,
}: {
  theme: ThemeId;
  lift: number;
  onTheme: (id: ThemeId) => void;
  onLift: (value: number) => void;
  onClose: () => void;
}) {
  return (
    <>
      <div className="theme-panel-backdrop" onClick={onClose} />
      <div className="theme-panel" role="dialog" aria-label="配色设置">
        <div className="theme-panel-title">配色设置</div>

        <div className="theme-panel-section">主题</div>
        <div className="theme-swatches">
          {THEMES.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`theme-swatch ${item.id === theme ? 'active' : ''}`}
              onClick={() => onTheme(item.id)}
            >
              <span className="theme-swatch-dot" style={{ background: item.swatch }} />
              <span className="theme-swatch-label">{item.label}</span>
            </button>
          ))}
        </div>

        <div className="theme-panel-section">
          底色亮度
          <span className="theme-lift-val">{lift === 0 ? '最暗' : lift >= 0.99 ? '最亮' : `${Math.round(lift * 100)}%`}</span>
        </div>
        <input
          className="theme-lift-slider"
          type="range"
          min={0}
          max={100}
          value={Math.round(lift * 100)}
          onChange={(event) => onLift(Number(event.target.value) / 100)}
        />
      </div>
    </>
  );
}

export default function App() {
  const [maintenance, setMaintenance] = useState<MaintenanceState | null>(null);
  const [refreshState, setRefreshState] = useState<RefreshState>('loading');
  const [refreshError, setRefreshError] = useState<Error | null>(null);
  const [currentTime, setCurrentTime] = useState(() => formatClock(new Date()));
  const [data, setData] = useState<StatsData | null>(null);
  const [flashKeys, setFlashKeys] = useState<Set<string>>(() => new Set());
  const [historyOpen, setHistoryOpen] = useState(false);
  const [themeOpen, setThemeOpen] = useState(false);
  const [themePref, setThemePref] = useState(() => loadThemePref());
  const [includePartTimers, setIncludePartTimers] = useState(() => loadIncludePartTimers());
  const [ranking, setRanking] = useState<RankingPreference>(() => loadRankingPreference());
  const mockEnabled = isDevelopmentMockEnabled();

  useEffect(() => {
    let stopped = false;
    const refresh = () => {
      getMaintenanceStatus()
        .then((response) => {
          if (!stopped) setMaintenance(adaptMaintenance(response));
        })
        .catch(() => undefined);
    };
    refresh();
    const timer = window.setInterval(refresh, 15_000);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, []);

  // 应用主题 + 亮度并记住
  useEffect(() => {
    applyTheme(themePref.theme, themePref.lift);
    try {
      localStorage.setItem(THEME_STORE_KEY, JSON.stringify(themePref));
    } catch {
      // 存不了就算了，不影响展示
    }
  }, [themePref]);

  const prevValuesRef = useRef<Map<string, number> | null>(null);
  const refreshSecondsRef = useRef(DEFAULT_REFRESH_SECONDS);

  useEffect(() => {
    syncIncludePartTimersUrl(includePartTimers);
  }, [includePartTimers]);

  useEffect(() => {
    try {
      localStorage.setItem(RANKING_STORE_KEY, JSON.stringify(ranking));
    } catch {
      // 存不了就保持当前会话排序，不影响大屏展示。
    }
  }, [ranking]);

  const sortMembersBy = (key: SortKey) => {
    setRanking({ sortKey: key });
  };

  const togglePartTimers = () => {
    prevValuesRef.current = null;
    setFlashKeys(new Set());
    setIncludePartTimers((value) => !value);
  };

  const applyData = useCallback((payload: StatsData) => {
    setData(payload);
    if (payload.refreshIntervalSeconds) {
      refreshSecondsRef.current = payload.refreshIntervalSeconds;
    }
    const current = buildValueMap(payload);
    const prev = prevValuesRef.current;
    if (prev) {
      const changed: string[] = [];
      current.forEach((value, key) => {
        const before = prev.get(key);
        if (before !== undefined && before !== value) {
          changed.push(key);
        }
      });
      if (changed.length > 0) {
        setFlashKeys((existing) => {
          const next = new Set(existing);
          changed.forEach((key) => next.add(key));
          return next;
        });
        changed.forEach((key) => {
          window.setTimeout(() => {
            setFlashKeys((existing) => {
              const next = new Set(existing);
              next.delete(key);
              return next;
            });
          }, FLASH_MS);
        });
      }
    }
    prevValuesRef.current = current;
  }, []);

  // 实时轮询：拉真实聚合，按返回的 refreshIntervalSeconds 间隔刷新
  useEffect(() => {
    let ignore = false;
    let timer = 0;
    setRefreshState('loading');

    const loop = () => {
      getMediaScreenStats(includePartTimers)
        .then((payload) => {
          if (ignore) {
            return;
          }
          const model = applyDevelopmentMock(adaptStats(payload), mockEnabled, includePartTimers);
          applyData(toStatsData(model, includePartTimers));
          setRefreshState('ready');
          setRefreshError(null);
        })
        .catch((reason: unknown) => {
          if (!ignore) {
            setRefreshState('error');
            setRefreshError(reason instanceof Error ? reason : new Error('媒体大屏请求失败'));
          }
        })
        .finally(() => {
          if (!ignore) {
            timer = window.setTimeout(loop, refreshSecondsRef.current * 1000);
          }
        });
    };

    loop();
    return () => {
      ignore = true;
      window.clearTimeout(timer);
    };
  }, [applyData, includePartTimers, mockEnabled]);

  // 时钟
  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(formatClock(new Date())), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const todayBump = flashKeys.has('kpi:today');
  const nowLabel = data ? timeOnly(data.updatedAt) : '--:--';
  const departmentsForCards = data
    ? data.partTimeCompanionDepartment
      ? [...data.departments, data.partTimeCompanionDepartment]
      : data.departments
    : [];

  return (
    <main className="screen-shell">
      {maintenance?.enabled && (
        <div className="maintenance-screen" role="status">
          <div className="maintenance-screen-title">系统维护中</div>
          <div className="maintenance-screen-detail">
            {maintenance.reason || '业务数据已暂停更新，请稍后再查看'}
          </div>
        </div>
      )}
      <section className="status-bar">
        <div className="title">新媒体部门客资数量大屏</div>
        <div className="status-right">
          <div>
            <span className="status-item-label">当前时间</span>
            <span className="clock">{currentTime}</span>
          </div>
          <div>
            <span className="status-item-label">最后更新</span>
            <span className="clock">{timeOnly(data?.updatedAt)}</span>
          </div>
          <div className={`live-indicator ${refreshState === 'error' ? 'is-error' : ''}`}>
            <span className="live-dot" />
            <span>{refreshState === 'error' ? '重试中' : '实时刷新中'}</span>
          </div>
          {mockEnabled && <div className="demo-indicator">演示数据</div>}
          <button
            className={`parttime-toggle ${includePartTimers ? 'active' : ''}`}
            type="button"
            aria-pressed={includePartTimers}
            aria-label={`计入兼职陪跑：${includePartTimers ? '开' : '关'}`}
            onClick={togglePartTimers}
          >
            <span className={`parttime-toggle-state ${includePartTimers ? 'dim' : 'current off'}`}>关</span>
            <span className={`parttime-toggle-state ${includePartTimers ? 'current on' : 'dim'}`}>开</span>
            <span className="parttime-toggle-text">计入兼职陪跑</span>
          </button>
          <button className="history-entry" type="button" onClick={() => setHistoryOpen(true)}>
            <History size={16} strokeWidth={2.4} />
            <span>历史</span>
          </button>
          <div className="theme-entry-wrap">
            <button className="history-entry" type="button" onClick={() => setThemeOpen((v) => !v)}>
              <Palette size={16} strokeWidth={2.4} />
              <span>配色</span>
            </button>
            {themeOpen && (
              <ThemePanel
                theme={themePref.theme}
                lift={themePref.lift}
                onTheme={(id) => setThemePref((prev) => ({ ...prev, theme: id }))}
                onLift={(value) => setThemePref((prev) => ({ ...prev, lift: value }))}
                onClose={() => setThemeOpen(false)}
              />
            )}
          </div>
        </div>
      </section>

      {!data ? (
        <div className="screen-loading">
          {refreshState === 'error' ? (
            <div className="screen-error-detail">
              <strong>{refreshError instanceof MediaScreenApiError && refreshError.kind === 'forbidden' ? '无权访问大屏' : refreshError instanceof MediaScreenApiError && refreshError.kind === 'unavailable' ? '大屏服务未开启' : '暂时无法获取数据'}</strong>
              <span>{refreshError?.message || '连接后端失败，自动重试中…'}</span>
            </div>
          ) : (
            '加载中…'
          )}
        </div>
      ) : (
        <>
          <section className="summary-row" aria-label="全部门汇总与今日之星">
            <div className="today-hero">
              <div className="today-hero-top">
                <span className="today-hero-label">今日客资数</span>
                <div className="today-hero-legend">
                  <span className="legend-today">— 今日</span>
                  <span className="legend-yesterday">┄ 昨日同时段</span>
                </div>
              </div>
              <div className={`today-hero-value ${todayBump ? 'is-bump' : ''}`}>
                {data.moduleStates.today === 'unsupported' ? '--' : formatNumber(data.summary.today)}
                {data.moduleStates.today === 'mock' && <small className="demo-tag">演示</small>}
              </div>
              <div className="today-hero-chart">
                <TrendChart
                  points={data.trend.today}
                  ghost={data.trend.yesterday}
                  nowLabel={nowLabel}
                  peakBump={todayBump}
                  stepMinutes={data.trend.stepMinutes}
                  emptyText={data.moduleStates.trend === 'unsupported' ? '当日走势待后端补齐' : '今日暂无走势'}
                />
              </div>
            </div>

            <div className="stat-stack">
              <div className="stat-bar">
                <div className="stat-bar-main">
                  <span className="stat-bar-label">本周客资</span>
                  <span className={`stat-bar-value ${flashKeys.has('kpi:week') ? 'is-bump' : ''}`}>
                    {data.moduleStates.week === 'unsupported' ? '--' : formatNumber(data.summary.week)}
                    {data.moduleStates.week === 'mock' && <small className="demo-tag">演示</small>}
                  </span>
                </div>
                <div className="stat-bar-spark">
                  <MiniSpark points={data.series.submitted.slice(-7)} tone="slate" />
                </div>
              </div>
              <div className="stat-bar">
                <div className="stat-bar-main">
                  <span className="stat-bar-label">本月总客资</span>
                  <span className={`stat-bar-value ${flashKeys.has('kpi:monthTotal') ? 'is-bump' : ''}`}>
                    {data.moduleStates.monthTotal === 'unsupported' ? '--' : formatNumber(data.summary.monthTotal)}
                    {data.moduleStates.monthTotal === 'mock' && <small className="demo-tag">演示</small>}
                  </span>
                </div>
                <div className="stat-bar-spark">
                  <MiniSpark points={data.series.submitted} tone="slate" />
                </div>
              </div>
              <div className="stat-bar effective">
                <div className="stat-bar-main">
                  <span className="stat-bar-label">本月有效客资</span>
                  <span className="stat-bar-value">
                    {data.moduleStates.monthEffective === 'unsupported' ? '--' : formatNumber(data.summary.monthEffective)}
                    {data.moduleStates.monthEffective === 'mock' && <small className="demo-tag">演示</small>}
                  </span>
                </div>
                <div className="stat-bar-spark">
                  <MiniSpark points={data.series.valid} tone="green" />
                </div>
              </div>
            </div>

            <TodayStarCard star={data.todayStar} yesterdayChampion={data.yesterdayChampion} />
          </section>

          <section className="dept-row" aria-label="部门成员明细">
            {departmentsForCards.map((department, index) => (
              <DepartmentCard
                department={department}
                index={index}
                flashKeys={flashKeys}
                ranking={ranking}
                onSort={sortMembersBy}
                key={department.id}
              />
            ))}
          </section>
        </>
      )}

      {historyOpen && <HistoryOverlay includePartTimers={includePartTimers} onClose={() => setHistoryOpen(false)} />}
    </main>
  );
}
