export type BackendRankItem = {
  name?: string | null;
  leadCount?: number | null;
  rank?: number | null;
  today?: number | null;
  week?: number | null;
  monthTotal?: number | null;
  monthEffective?: number | null;
};

export type BackendTrendItem = {
  date?: string | null;
  leadCount?: number | null;
};

export type BackendMetrics = {
  today?: number | null;
  week?: number | null;
  monthTotal?: number | null;
  monthEffective?: number | null;
};

export type BackendPartTimerDetail = BackendMetrics & {
  partnerId?: number | null;
  name?: string | null;
};

export type BackendMember = BackendMetrics & {
  userId?: number | null;
  name?: string | null;
  departmentName?: string | null;
  leadCount?: number | null;
  rank?: number | null;
  isDisabled?: boolean | null;
  partTimers?: BackendPartTimerDetail[] | null;
};

export type BackendDepartment = {
  departmentId?: number | null;
  name?: string | null;
  subtitle?: string | null;
  metrics?: BackendMetrics | null;
  members?: BackendMember[] | null;
};

export type BackendRealtimeTrend = {
  today?: number[] | null;
  yesterday?: number[] | null;
  stepMinutes?: number | null;
};

export type BackendTodayStar = BackendRankItem & {
  deptName?: string | null;
  yesterday?: number | null;
  rankToday?: number | null;
  rankYesterday?: number | null;
  includesPartTime?: boolean | null;
};

export type BackendYesterdayChampion = {
  name?: string | null;
  deptName?: string | null;
  count?: number | null;
  includesPartTime?: boolean | null;
};

export type BackendStats = {
  tenantId?: number;
  generatedAt?: string | number | null;
  updatedAt?: string | number | null;
  refreshIntervalSeconds?: number | null;
  partTimeIncluded?: boolean | null;
  totalLeads?: number | null;
  departmentRanking?: BackendRankItem[] | null;
  memberRanking?: BackendRankItem[] | null;
  summary?: BackendMetrics | null;
  departments?: BackendDepartment[] | null;
  partTimeCompanionDepartment?: BackendDepartment | null;
  todayStar?: BackendTodayStar | null;
  yesterdayChampion?: BackendYesterdayChampion | null;
  partTimer?: { enabled?: boolean | null; items?: BackendRankItem[] | null } | null;
  trend?: BackendRealtimeTrend | BackendTrendItem[] | null;
  series?: { submitted?: number[] | null; valid?: number[] | null } | null;
};

export type BackendHistory = BackendStats & {
  available?: boolean | null;
  snapshotDate?: string | null;
  source?: string | null;
  snapshotCreatedAt?: string | null;
  departments?: Array<BackendDepartment & {
    members?: Array<BackendMember & BackendRankItem> | null;
  }> | null;
  historySnapshot?: {
    available?: boolean | null;
    snapshotDate?: string | null;
    totalLeads?: number | null;
  } | null;
};

export type BackendMaintenance = {
  tenantId?: number;
  maintenanceEnabled?: boolean | null;
  checkedAt?: string | number | null;
};

type ApiEnvelope<T> = { code?: number; msg?: string; message?: string; data?: T | null };

export type ApiErrorKind =
  | 'configuration'
  | 'unauthorized'
  | 'forbidden'
  | 'unavailable'
  | 'network'
  | 'business'
  | 'invalid-response'
  | 'http';

export class MediaScreenApiError extends Error {
  readonly status: number;
  readonly code?: number;
  readonly kind: ApiErrorKind;

  constructor(message: string, kind: ApiErrorKind, status = 0, code?: number) {
    super(message);
    this.name = 'MediaScreenApiError';
    this.kind = kind;
    this.status = status;
    this.code = code;
  }
}

export type MediaScreenRuntimeConfig = { tenantId: string; apiBaseUrl: string; apiPrefix: string };

const DEFAULT_API_PREFIX = '/public-api/zsjos/media-screen';

export function readMediaScreenRuntimeConfig(): MediaScreenRuntimeConfig {
  const tenantId = (import.meta.env.VITE_MEDIA_SCREEN_TENANT_ID || '').trim();
  if (!/^\d+$/.test(tenantId) || Number(tenantId) <= 0) {
    throw new MediaScreenApiError(
      '缺少有效的 VITE_MEDIA_SCREEN_TENANT_ID，请由部署环境提供租户 ID',
      'configuration',
    );
  }
  return {
    tenantId,
    apiBaseUrl: (import.meta.env.VITE_MEDIA_SCREEN_API_BASE_URL || '').replace(/\/$/, ''),
    apiPrefix: (import.meta.env.VITE_MEDIA_SCREEN_API_PREFIX || DEFAULT_API_PREFIX).replace(/\/$/, ''),
  };
}

function errorMessage(status: number, body?: ApiEnvelope<unknown>) {
  const backendMessage = body?.msg || body?.message;
  if (backendMessage) return backendMessage;
  if (status === 401) return '公共大屏接口异常要求登录，请检查后端路由和网关配置';
  if (status === 403) return '当前 IP 或租户未加入媒体大屏白名单';
  if (status === 503) return '媒体大屏服务未开启或正在维护';
  return `请求失败（HTTP ${status}）`;
}

function errorKind(status: number): ApiErrorKind {
  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 503) return 'unavailable';
  return 'http';
}

async function request<T>(resource: string, params: Record<string, string>): Promise<T> {
  const config = readMediaScreenRuntimeConfig();
  const query = new URLSearchParams({ tenantId: config.tenantId, ...params });
  const url = `${config.apiBaseUrl}${config.apiPrefix}/${resource}?${query.toString()}`;
  let response: Response;
  try {
    response = await fetch(url, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'omit',
    });
  } catch {
    throw new MediaScreenApiError('无法连接媒体大屏后端', 'network');
  }

  let body: ApiEnvelope<T> | undefined;
  try {
    body = (await response.json()) as ApiEnvelope<T>;
  } catch {
    body = undefined;
  }
  if (!response.ok) {
    throw new MediaScreenApiError(
      errorMessage(response.status, body),
      errorKind(response.status),
      response.status,
      body?.code,
    );
  }
  if (!body) {
    throw new MediaScreenApiError('接口未返回 JSON 数据', 'invalid-response', response.status);
  }
  if (body.code !== undefined && body.code !== 0) {
    throw new MediaScreenApiError(
      body.msg || body.message || '媒体大屏接口返回业务错误',
      'business',
      response.status,
      body.code,
    );
  }
  if (body.data === undefined || body.data === null) {
    throw new MediaScreenApiError('接口未返回有效 data', 'invalid-response', response.status, body.code);
  }
  return body.data;
}

export function getMediaScreenStats(includePartTimers: boolean) {
  return request<BackendStats>('stats', { includePartTimers: includePartTimers ? '1' : '0' });
}

export function getMediaScreenHistory(date: string, includePartTimers: boolean) {
  return request<BackendHistory>('history', {
    date,
    includePartTimers: includePartTimers ? '1' : '0',
  });
}

export function getMaintenanceStatus() {
  return request<BackendMaintenance>('maintenance/status', {});
}
