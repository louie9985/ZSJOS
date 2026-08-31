import type {
  BackendDepartment,
  BackendHistory,
  BackendMaintenance,
  BackendMember,
  BackendMetrics,
  BackendStats,
} from './api.ts';

export type ModuleState = 'real' | 'empty' | 'unsupported' | 'mock';
export type ModuleValue<T> = { state: ModuleState; value: T | null; reason?: string };
export type RankItem = { name: string; leadCount: number; rank: number };
export type ScreenMetrics = { today: number; week: number; monthTotal: number; monthEffective: number };
export type ScreenPartTimer = ScreenMetrics & { id: string; actorId: string; name: string };
export type ScreenMember = ScreenMetrics & {
  id: string;
  name: string;
  isDisabled?: boolean;
  partTimers?: ScreenPartTimer[];
};
export type ScreenDepartment = {
  id: string;
  name: string;
  subtitle: string;
  metrics: ScreenMetrics;
  members: ScreenMember[];
};
export type ScreenTodayStar = {
  name: string;
  deptName: string;
  today: number;
  yesterday: number;
  rankToday: number;
  rankYesterday: number | null;
  includesPartTime: boolean;
};
export type ScreenYesterdayChampion = { name: string; deptName: string; count: number; includesPartTime: boolean };
export type ScreenRealtimeTrend = { today: number[]; yesterday: number[]; stepMinutes: number };
export type ScreenSeries = { submitted: number[]; valid: number[] };
export type HistoryRankItem = RankItem & {
  departmentName: string;
  today: number | null;
  week: number | null;
  monthTotal: number | null;
  monthEffective: number | null;
};

export type MediaScreenModel = {
  generatedAt: string | null;
  refreshIntervalSeconds: number;
  partTimeIncluded: boolean | null;
  summary: ModuleValue<ScreenMetrics>;
  departments: ModuleValue<ScreenDepartment[]>;
  partTimeCompanionDepartment: ModuleValue<ScreenDepartment>;
  todayStar: ModuleValue<ScreenTodayStar>;
  yesterdayChampion: ModuleValue<ScreenYesterdayChampion>;
  trend: ModuleValue<ScreenRealtimeTrend>;
  series: ModuleValue<ScreenSeries>;
};

export type HistoryModel = {
  available: boolean;
  source: string | null;
  snapshotDate: string | null;
  snapshotCreatedAt: string | null;
  totalLeads: number | null;
  summary: {
    today: number | null;
    week: number | null;
    monthTotal: number | null;
    monthEffective: number | null;
  };
  mainRanking: HistoryRankItem[];
  partTimerRanking: HistoryRankItem[];
};

export type MaintenanceModel = { enabled: boolean; checkedAt: string | null };
const UNSUPPORTED_REASON = '后端第一版尚未提供';

function formatBackendDateTime(value: unknown): string | null {
  const date =
    typeof value === 'number' && Number.isFinite(value)
      ? new Date(value)
      : typeof value === 'string' && value.trim()
        ? new Date(value)
        : null;
  if (!date || Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
}

function finiteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function nonNegativeInteger(value: unknown): number | null {
  const number = finiteNumber(value);
  return number !== null && number >= 0 ? Math.trunc(number) : null;
}

function positiveInteger(value: unknown): number | null {
  const number = nonNegativeInteger(value);
  return number !== null && number > 0 ? number : null;
}

function adaptMetrics(raw: BackendMetrics | null | undefined): ScreenMetrics | null {
  const today = nonNegativeInteger(raw?.today);
  const week = nonNegativeInteger(raw?.week);
  const monthTotal = nonNegativeInteger(raw?.monthTotal);
  const monthEffective = nonNegativeInteger(raw?.monthEffective);
  return today === null || week === null || monthTotal === null || monthEffective === null
    ? null
    : { today, week, monthTotal, monthEffective };
}

function adaptMember(raw: BackendMember): ScreenMember | null {
  const name = typeof raw.name === 'string' ? raw.name.trim() : '';
  const metrics = adaptMetrics(raw);
  const userId = positiveInteger(raw.userId);
  if (!name || !metrics || userId === null) return null;
  const memberKey = `user-${userId}`;
  const partTimers = Array.isArray(raw.partTimers)
    ? raw.partTimers
        .map((item) => {
          const partTimerName = typeof item.name === 'string' ? item.name.trim() : '';
          const partTimerMetrics = adaptMetrics(item);
          const partnerId = positiveInteger(item.partnerId);
          if (!partTimerName || !partTimerMetrics || partnerId === null) return null;
          return {
            id: `partner-${partnerId}`,
            actorId: `partner-${partnerId}`,
            name: partTimerName,
            ...partTimerMetrics,
          };
        })
        .filter((item): item is ScreenPartTimer => item !== null)
    : undefined;
  return {
    id: memberKey,
    name,
    isDisabled: raw.isDisabled === true || undefined,
    partTimers,
    ...metrics,
  };
}

function adaptDepartment(raw: BackendDepartment, companion = false): ScreenDepartment | null {
  const name = typeof raw.name === 'string' ? raw.name.trim() : '';
  const metrics = adaptMetrics(raw.metrics);
  const departmentId = positiveInteger(raw.departmentId);
  if (!name || !metrics || !Array.isArray(raw.members) || (!companion && departmentId === null)) return null;
  const key = companion ? 'part_time_companion' : `department-${departmentId}`;
  const members = raw.members
    .map((member) => adaptMember(member))
    .filter((member): member is ScreenMember => member !== null);
  if (raw.members.length > 0 && members.length !== raw.members.length) return null;
  return {
    id: key,
    name,
    subtitle: typeof raw.subtitle === 'string' ? raw.subtitle.trim() : '',
    metrics,
    members,
  };
}

function adaptDepartments(raw: BackendDepartment[] | null | undefined): ModuleValue<ScreenDepartment[]> {
  if (!Array.isArray(raw)) return unsupported('响应中缺少新媒体部门及成员统计');
  const departments = raw
    .map((department) => adaptDepartment(department))
    .filter((department): department is ScreenDepartment => department !== null);
  if (raw.length > 0 && departments.length !== raw.length) {
    return unsupported('新媒体部门或成员缺少今日、本周、本月、本月有效字段');
  }
  return departments.length > 0 ? { state: 'real', value: departments } : { state: 'empty', value: [] };
}

function adaptCompanion(raw: BackendDepartment | null | undefined): ModuleValue<ScreenDepartment> {
  if (raw === null) return { state: 'empty', value: null };
  if (raw === undefined) return unsupported('响应中缺少真实兼职陪跑统计');
  const department = adaptDepartment(raw, true);
  return department
    ? { state: 'real', value: department }
    : unsupported('兼职陪跑成员缺少今日、本周、本月、本月有效字段');
}

function adaptNumberArray(raw: unknown): number[] | null {
  if (!Array.isArray(raw)) return null;
  const values = raw.map(nonNegativeInteger);
  return values.some((value) => value === null) ? null : (values as number[]);
}

function unsupported<T>(reason = UNSUPPORTED_REASON): ModuleValue<T> {
  return { state: 'unsupported', value: null, reason };
}

export function adaptStats(raw: BackendStats): MediaScreenModel {
  const summary = adaptMetrics(raw.summary);
  const todayStarName = typeof raw.todayStar?.name === 'string' ? raw.todayStar.name.trim() : '';
  const todayStarToday = nonNegativeInteger(raw.todayStar?.today);
  const todayStarRank = nonNegativeInteger(raw.todayStar?.rankToday);
  const todayStar =
    todayStarName && todayStarToday !== null && todayStarRank !== null && todayStarRank > 0
      ? {
          name: todayStarName,
          deptName: typeof raw.todayStar?.deptName === 'string' ? raw.todayStar.deptName.trim() : '',
          today: todayStarToday,
          yesterday: nonNegativeInteger(raw.todayStar?.yesterday) ?? 0,
          rankToday: todayStarRank,
          rankYesterday: nonNegativeInteger(raw.todayStar?.rankYesterday),
          includesPartTime: raw.todayStar?.includesPartTime === true,
        }
      : null;
  const championName = typeof raw.yesterdayChampion?.name === 'string' ? raw.yesterdayChampion.name.trim() : '';
  const championCount = nonNegativeInteger(raw.yesterdayChampion?.count);
  const trendToday = !Array.isArray(raw.trend) ? adaptNumberArray(raw.trend?.today) : null;
  const trendYesterday = !Array.isArray(raw.trend) ? adaptNumberArray(raw.trend?.yesterday) : null;
  const stepMinutes = !Array.isArray(raw.trend) ? nonNegativeInteger(raw.trend?.stepMinutes) : null;
  const submitted = adaptNumberArray(raw.series?.submitted);
  const valid = adaptNumberArray(raw.series?.valid);
  return {
    generatedAt: formatBackendDateTime(raw.updatedAt ?? raw.generatedAt),
    refreshIntervalSeconds: nonNegativeInteger(raw.refreshIntervalSeconds) || 5,
    partTimeIncluded: typeof raw.partTimeIncluded === 'boolean' ? raw.partTimeIncluded : null,
    summary: summary ? { state: 'real', value: summary } : unsupported('响应中缺少新媒体今日、周、月及有效汇总'),
    departments: adaptDepartments(raw.departments),
    partTimeCompanionDepartment: adaptCompanion(raw.partTimeCompanionDepartment),
    todayStar: todayStar
      ? { state: 'real', value: todayStar }
      : raw.todayStar === null
        ? { state: 'empty', value: null }
        : unsupported('响应中缺少按今日新媒体客资统计的今日之星'),
    yesterdayChampion:
      championName && championCount !== null
        ? {
            state: 'real',
            value: {
              name: championName,
              deptName:
                typeof raw.yesterdayChampion?.deptName === 'string' ? raw.yesterdayChampion.deptName.trim() : '',
              count: championCount,
              includesPartTime: raw.yesterdayChampion?.includesPartTime === true,
            },
          }
        : raw.yesterdayChampion === null
          ? { state: 'empty', value: null }
          : unsupported('响应中缺少昨日冠军'),
    trend:
      trendToday === null
        ? unsupported('响应中缺少今日 0 点至当前的累计走势')
        : trendToday.length === 0
          ? { state: 'empty', value: { today: [], yesterday: trendYesterday || [], stepMinutes: stepMinutes || 10 } }
          : { state: 'real', value: { today: trendToday, yesterday: trendYesterday || [], stepMinutes: stepMinutes || 10 } },
    series:
      submitted !== null && valid !== null
        ? { state: submitted.length || valid.length ? 'real' : 'empty', value: { submitted, valid } }
        : unsupported('响应中缺少近 14 日提交量与有效量序列'),
  };
}

export function adaptHistory(raw: BackendHistory): HistoryModel {
  const snapshot = raw.historySnapshot;
  const available = raw.available === true || snapshot?.available === true;
  if (!available) {
    return {
      available: false,
      source: typeof raw.source === 'string' ? raw.source : null,
      snapshotDate:
        (typeof raw.snapshotDate === 'string' && raw.snapshotDate) ||
        (typeof snapshot?.snapshotDate === 'string' && snapshot.snapshotDate) ||
        null,
      snapshotCreatedAt: null,
      totalLeads: null,
      summary: { today: null, week: null, monthTotal: null, monthEffective: null },
      mainRanking: [],
      partTimerRanking: [],
    };
  }
  const summary = {
    today: finiteNumber(raw.summary?.today),
    week: finiteNumber(raw.summary?.week),
    monthTotal: finiteNumber(raw.summary?.monthTotal),
    monthEffective: finiteNumber(raw.summary?.monthEffective),
  };
  const mainRanking = (raw.departments || []).flatMap((department) =>
    (department.members || []).map((member, index) => ({
      name: typeof member.name === 'string' && member.name.trim() ? member.name : '未知成员',
      departmentName:
        (typeof member.departmentName === 'string' && member.departmentName.trim()) ||
        (typeof department.name === 'string' && department.name.trim()) ||
        '未分配',
      leadCount: finiteNumber(member.leadCount) ?? finiteNumber(member.today) ?? 0,
      rank: finiteNumber(member.rank) ?? index + 1,
      today: finiteNumber(member.today),
      week: finiteNumber(member.week),
      monthTotal: finiteNumber(member.monthTotal),
      monthEffective: finiteNumber(member.monthEffective),
    })),
  );
  const partTimerRanking = (raw.partTimeCompanionDepartment?.members || [])
    .map((item, index): HistoryRankItem | null => {
      const name = typeof item.name === 'string' ? item.name.trim() : '';
      if (!name) return null;
      return {
        name,
        leadCount: finiteNumber(item.today) ?? 0,
        rank: index + 1,
        departmentName: '兼职陪跑',
        today: finiteNumber(item.today),
        week: finiteNumber(item.week),
        monthTotal: finiteNumber(item.monthTotal),
        monthEffective: finiteNumber(item.monthEffective),
      };
    })
    .filter((item): item is HistoryRankItem => item !== null);
  return {
    available: true,
    source: typeof raw.source === 'string' ? raw.source : null,
    snapshotDate:
      (typeof raw.snapshotDate === 'string' && raw.snapshotDate) ||
      (typeof snapshot?.snapshotDate === 'string' && snapshot.snapshotDate) ||
      null,
    snapshotCreatedAt: formatBackendDateTime(raw.snapshotCreatedAt),
    totalLeads: finiteNumber(snapshot?.totalLeads) ?? finiteNumber(raw.totalLeads),
    summary,
    mainRanking,
    partTimerRanking,
  };
}

export function adaptMaintenance(raw: BackendMaintenance): MaintenanceModel {
  return {
    enabled: raw.maintenanceEnabled === true,
    checkedAt: formatBackendDateTime(raw.checkedAt),
  };
}
