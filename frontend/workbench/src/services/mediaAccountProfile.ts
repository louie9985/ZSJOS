import {
  http,
  unwrap,
  type MediaAccount,
  type MediaAccountField,
  type MediaAccountDetailSnapshot,
  type PageResult,
} from "./api";
import type { Timestamp } from "./time";
export type AccountFieldOwner = "AUTO" | "DIRECTOR" | "OPERATOR" | "UNASSIGNED";
export type ProfileField = MediaAccountField & {
  ownerType: AccountFieldOwner;
  group: string;
  requiredForComplete: boolean;
  sourceType: string;
  snapshotPolicy: string;
};
export type ProfileFile = {
  id: number;
  name: string;
  type: string;
  size: number;
  previewUrl?: string;
};
export type AccountProfile = {
  account: MediaAccount;
  config: { id: number; versionNo: number; fields: ProfileField[] };
  values: Record<string, unknown>;
  snapshots: MediaAccountDetailSnapshot[];
  editableFields: string[];
  missingFields: string[];
  missingByOwner: Record<string, number>;
  sourceNotes: Record<string, string>;
  files: Record<string, ProfileFile>;
  currentUserName?: string;
  studentName?: string;
  directorName?: string;
  operatorName?: string;
  latestRecords?: Record<string, ProfileEntry>;
  positioningRequirements?: Record<string, unknown>;
  diagnosisContext?: {anchorAt?: string; roundKey?: number; submissionId?: number; submissionNo?: number; syncedAt?: string};
  canViewHistory: boolean;
  canStartDiagnosis?: boolean;
  canSubmitDiagnosis?: boolean;
  diagnosisStarted?: boolean;
  partnerMetrics?: { sourceStatus: string; totalLeads?: number; monthLeads?: number; totalDeals?: number; monthDeals?: number; totalDealRate: number; monthDealRate: number; totalDealAmount: number; monthDealAmount: number };
};

export function formatAccountMetric(key: string, value: unknown): string | undefined {
  if (value == null || value === '') return undefined;
  if (!['total_leads', 'month_leads', 'total_conversion', 'month_conversion', 'total_amount', 'month_amount'].includes(key)) return undefined;
  const number = Number(value);
  if (!Number.isFinite(number)) return undefined;
  if (key.endsWith('_conversion')) return `${(number * 100).toFixed(2)}%`;
  if (key.endsWith('_amount')) return `¥${number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  return number.toLocaleString('zh-CN');
}
export type ProfileEntry = {
  id: number;
  kind: string;
  fieldKey?: string;
  title: string;
  content?: string;
  snapshots: MediaAccountDetailSnapshot[];
  files: ProfileFile[];
  operatedBy?: string;
  operatedAt: Timestamp;
  resultVersion: number;
  positioning?: {
    configVersionId: number;
    fields: ProfileField[];
    values: MediaAccountDetailSnapshot[];
  };
};
export type ProfilePatch = {
  version: number;
  configVersionId: number;
  idempotencyKey: string;
  changes: Record<string, unknown>;
};
type DiagnosisCommon = Omit<ProfilePatch, "changes"> & {
  previousEntryId?: number;
  taskId?: number;
  currentStage: string; accountStatus: string;
  primaryProblem: string; primaryProblemEvidence: string;
  conclusion: string; reposition: boolean;
};
export type DiagnosisRequest = DiagnosisCommon & ({
  templateType: "diagnosis_initial"; cycle: 0;
  secondaryProblem?: string; secondaryProblemEvidence?: string;
} | {
  templateType: "diagnosis_7d" | "diagnosis_14d" | "diagnosis_28d"; cycle: number;
  cooperationLevel: string; cooperationEvidence: string;
  secondaryProblem: string; secondaryProblemEvidence: string;
  improvementMeasures: string; observedData: string;
});

export type DiagnosisTodo = {taskId: number; accountId: number; studentPersonId: number; accountName?: string; title: string;
  templateType: DiagnosisRequest['templateType']; cycle: number; dueAt: Timestamp;
  payload: {requirementSnapshot?: Record<string, unknown>; source?: AccountProfile['diagnosisContext']}};
const diagnosisBase = '/zsjos/media-account/diagnosis';
export const diagnosisApi = {
  reminders: async () => unwrap<DiagnosisTodo[]>(await http.get(`${diagnosisBase}/reminders`)),
  tasks: async (accountId:number) => unwrap<DiagnosisTodo[]>(await http.get(`${diagnosisBase}/tasks`, {params:{accountId}})),
  acknowledge: async (taskIds:number[]) => unwrap<boolean>(await http.post(`${diagnosisBase}/acknowledge`, {taskIds})),
};
export const diagnosisTaskUrl = (task: Pick<DiagnosisTodo,'accountId'|'studentPersonId'|'taskId'>) =>
  `/zsjos/media-students?personId=${task.studentPersonId}&accountId=${task.accountId}&diagnosisTaskId=${task.taskId}`;
const base = (id: number) => `/zsjos/media-account/${id}/profile`;
export const accountProfileApi = {
  get: async (id: number) => unwrap<AccountProfile>(await http.get(base(id))),
  patch: async (id: number, data: ProfilePatch) => unwrap<number>(await http.put(base(id), data)),
  history: async (id: number, pageNo: number, filter: {fieldKey?: string; kind?: string; cycle?: number} = {}) => unwrap<PageResult<ProfileEntry>>(await http.get(`${base(id)}/history`, { params: { pageNo, pageSize: 10, ...filter } })),
  append: async (id: number, data: Omit<ProfilePatch, "changes"> & { fieldKey: string; content: string; fileIds: number[] }) => unwrap<number>(await http.post(`${base(id)}/records`, data)),
  diagnosis: async (id: number, data: DiagnosisRequest) => unwrap<number>(await http.post(`${base(id)}/diagnosis`, data)),
  upload: async (id: number, fieldKey: string, file: File) => { const data = new FormData(); data.append("fieldKey", fieldKey); data.append("file", file); return unwrap<ProfileFile>(await http.post(`${base(id)}/files`, data)); },
};
export const fieldEmpty = (value: unknown) =>
  value == null ||
  (typeof value === "string" && !value.trim()) ||
  (Array.isArray(value) && value.length === 0);
export const profileMissing = (
  fields: ProfileField[],
  values: Record<string, unknown>,
) =>
  fields.filter(
    (f) =>
      f.enabled &&
      f.requiredForComplete &&
      ["DIRECTOR", "OPERATOR"].includes(f.ownerType) &&
      !POSITIONING_SYNC_FIELDS.has(f.key) &&
      f.type !== "record" &&
      fieldEmpty(values[f.key]),
  );
export const POSITIONING_SYNC_FIELDS = new Set([
  "account_position",
  "professional_position",
  "content_format", "delivery_goals", "diagnosis_7d_requirement", "diagnosis_14d_requirement", "diagnosis_28d_requirement",
]);
export const profileChanges = (
  fields: ProfileField[],
  editable: string[],
  before: Record<string, unknown>,
  after: Record<string, unknown>,
) =>
  Object.fromEntries(
    fields
      .filter(
        (f) =>
          editable.includes(f.key) &&
          f.type !== "record" &&
          JSON.stringify(before[f.key] ?? null) !==
            JSON.stringify(after[f.key] ?? null),
      )
      .map((f) => [f.key, fieldEmpty(after[f.key]) ? null : after[f.key]]),
  );

// Storage groups are configuration metadata; the account sheet has three visual columns.
export const profileSection = (field: ProfileField) => {
  if (field.key === "cover") return "HOME";
  if (field.key === "positioning_history") return "POSITIONING";
  if (field.group === "PROFILE" || field.group === "METRICS") return "STATUS";
  return field.group;
};
