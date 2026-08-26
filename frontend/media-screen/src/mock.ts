import type {
  MediaScreenModel,
  ModuleValue,
  ScreenDepartment,
  ScreenMetrics,
  ScreenRealtimeTrend,
  ScreenSeries,
  ScreenTodayStar,
  ScreenYesterdayChampion,
} from './adapter.ts';

const MOCK_DEPARTMENTS: ScreenDepartment[] = [
  {
    id: 'mock-media-1',
    name: '新媒体一部',
    subtitle: '演示数据',
    metrics: { today: 14, week: 68, monthTotal: 286, monthEffective: 112 },
    members: [
      { id: 'mock-media-1-a', name: '演示成员甲', today: 8, week: 38, monthTotal: 158, monthEffective: 64 },
      { id: 'mock-media-1-b', name: '演示成员乙', today: 6, week: 30, monthTotal: 128, monthEffective: 48 },
    ],
  },
  {
    id: 'mock-media-2',
    name: '新媒体二部',
    subtitle: '演示数据',
    metrics: { today: 11, week: 52, monthTotal: 224, monthEffective: 86 },
    members: [
      { id: 'mock-media-2-a', name: '演示成员丙', today: 7, week: 31, monthTotal: 132, monthEffective: 52 },
      { id: 'mock-media-2-b', name: '演示成员丁', today: 4, week: 21, monthTotal: 92, monthEffective: 34 },
    ],
  },
  {
    id: 'mock-media-3',
    name: '新媒体三部',
    subtitle: '演示数据',
    metrics: { today: 9, week: 45, monthTotal: 196, monthEffective: 73 },
    members: [
      { id: 'mock-media-3-a', name: '演示成员戊', today: 5, week: 25, monthTotal: 106, monthEffective: 41 },
      { id: 'mock-media-3-b', name: '演示成员己', today: 4, week: 20, monthTotal: 90, monthEffective: 32 },
    ],
  },
];

const MOCK_COMPANION: ScreenDepartment = {
  id: 'part_time_companion',
  name: '兼职陪跑',
  subtitle: '演示数据 · 有兼职员工',
  metrics: { today: 6, week: 27, monthTotal: 118, monthEffective: 42 },
  members: [
    {
      id: 'mock-companion-a',
      name: '演示成员甲',
      today: 4,
      week: 17,
      monthTotal: 72,
      monthEffective: 26,
      partTimers: [
        {
          id: 'mock-part-a',
          actorId: 'mock-part-a',
          name: '演示兼职甲',
          today: 4,
          week: 17,
          monthTotal: 72,
          monthEffective: 26,
        },
      ],
    },
    {
      id: 'mock-companion-b',
      name: '演示成员丙',
      today: 2,
      week: 10,
      monthTotal: 46,
      monthEffective: 16,
      partTimers: [],
    },
  ],
};

const MOCK_TREND: ScreenRealtimeTrend = {
  today: [0, 1, 3, 7, 12, 18, 25, 31, 34],
  yesterday: [0, 2, 4, 6, 10, 15, 21, 27, 30],
  stepMinutes: 120,
};
const MOCK_SERIES: ScreenSeries = {
  submitted: [18, 22, 19, 26, 31, 28, 34, 25, 29, 32, 27, 35, 30, 34],
  valid: [7, 9, 8, 11, 13, 12, 15, 10, 12, 14, 11, 15, 13, 16],
};
const MOCK_STAR: ScreenTodayStar = {
  name: '演示成员甲',
  deptName: '新媒体一部',
  today: 8,
  yesterday: 6,
  rankToday: 1,
  rankYesterday: 2,
};
const MOCK_YESTERDAY_CHAMPION: ScreenYesterdayChampion = {
  name: '演示成员丙',
  deptName: '新媒体二部',
  count: 9,
};

function fillUnsupported<T>(module: ModuleValue<T>, value: T): ModuleValue<T> {
  return module.state === 'unsupported' ? { state: 'mock', value } : module;
}

export function isDevelopmentMockEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_MEDIA_SCREEN_ENABLE_MOCK === 'true';
}

export function applyDevelopmentMock(
  model: MediaScreenModel,
  enabled: boolean,
  includePartTimers = true,
): MediaScreenModel {
  if (!enabled) return model;
  const departmentMetrics = MOCK_DEPARTMENTS.reduce<ScreenMetrics>(
    (total, department) => ({
      today: total.today + department.metrics.today,
      week: total.week + department.metrics.week,
      monthTotal: total.monthTotal + department.metrics.monthTotal,
      monthEffective: total.monthEffective + department.metrics.monthEffective,
    }),
    { today: 0, week: 0, monthTotal: 0, monthEffective: 0 },
  );
  const summary = includePartTimers
    ? {
        today: departmentMetrics.today + MOCK_COMPANION.metrics.today,
        week: departmentMetrics.week + MOCK_COMPANION.metrics.week,
        monthTotal: departmentMetrics.monthTotal + MOCK_COMPANION.metrics.monthTotal,
        monthEffective: departmentMetrics.monthEffective + MOCK_COMPANION.metrics.monthEffective,
      }
    : departmentMetrics;
  return {
    ...model,
    partTimeIncluded: model.partTimeIncluded ?? includePartTimers,
    summary: fillUnsupported(model.summary, summary),
    departments: fillUnsupported(model.departments, MOCK_DEPARTMENTS),
    partTimeCompanionDepartment: fillUnsupported(model.partTimeCompanionDepartment, MOCK_COMPANION),
    todayStar: fillUnsupported(model.todayStar, MOCK_STAR),
    yesterdayChampion: fillUnsupported(model.yesterdayChampion, MOCK_YESTERDAY_CHAMPION),
    trend: fillUnsupported(model.trend, MOCK_TREND),
    series: fillUnsupported(model.series, MOCK_SERIES),
  };
}
