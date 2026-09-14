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
  canViewHistory: boolean;
  canSubmitPositioning?: boolean;
};
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
export type DiagnosisRequest = Omit<ProfilePatch, "changes"> & {
  cycle: number;
  templateType: "diagnosis_7d" | "diagnosis_14d" | "diagnosis_28d";
  currentStage: string; accountStatus: string; cooperationLevel: string; cooperationEvidence: string;
  primaryProblem: string; primaryProblemEvidence: string; secondaryProblem: string; secondaryProblemEvidence: string;
  conclusion: string; improvementMeasures: string; observedData: string; reposition: boolean;
};
const base = (id: number) => `/zsjos/media-account/${id}/profile`;
export const accountProfileApi = {
  submitPositioning: async (id: number, data: ProfilePatch) =>
    unwrap<number>(await http.post(`${base(id)}/positioning/submit`, data)),
  positioningVersions: async (id: number, pageNo: number) =>
    unwrap<PageResult<ProfileEntry>>(
      await http.get(`${base(id)}/positioning/versions`, {
        params: { pageNo, pageSize: 10 },
      }),
    ),
  get: async (id: number) => unwrap<AccountProfile>(await http.get(base(id))),
  patch: async (id: number, data: ProfilePatch) =>
    unwrap<number>(await http.put(base(id), data)),
  history: async (id: number, pageNo: number) =>
    unwrap<PageResult<ProfileEntry>>(
      await http.get(`${base(id)}/history`, {
        params: { pageNo, pageSize: 10 },
      }),
    ),
  append: async (
    id: number,
    data: Omit<ProfilePatch, "changes"> & {
      fieldKey: string;
      content: string;
      fileIds: number[];
    },
  ) => unwrap<number>(await http.post(`${base(id)}/records`, data)),
  diagnosis: async (id: number, data: DiagnosisRequest) =>
    unwrap<number>(await http.post(`${base(id)}/diagnosis`, data)),
  upload: async (id: number, fieldKey: string, file: File) => {
    const data = new FormData();
    data.append("fieldKey", fieldKey);
    data.append("file", file);
    return unwrap<ProfileFile>(await http.post(`${base(id)}/files`, data));
  },
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
  "content_format",
  "student_commitments",
  "company_commitments",
  "delivery_goals",
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
