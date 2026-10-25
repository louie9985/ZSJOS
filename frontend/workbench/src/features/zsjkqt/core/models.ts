export const REST_MARK = "休息";
export const NORMAL_MARK = "出勤";
export const PAID_REST_MARK = "带薪";

export const ATTENDANCE_STATUSES = [
  "",
  NORMAL_MARK,
  REST_MARK,
  PAID_REST_MARK,
  "迟到",
  "早退",
  "缺卡",
  "异常",
  "加班",
  "旷工",
  "事假",
  "病假",
  "年假",
  "调休",
  "婚假",
  "产假",
  "陪产假",
  "产检假",
  "丧假",
  "工伤",
] as const;

export type AttendanceStatus = (typeof ATTENDANCE_STATUSES)[number];

export interface ValidationIssue {
  level: "info" | "warning" | "error";
  employee: string;
  day: number | null;
  message: string;
  rawValue: string;
  resolved?: boolean;
}

export interface DayResult {
  morning: AttendanceStatus;
  afternoon: AttendanceStatus;
  attendanceMorning?: AttendanceStatus;
  attendanceAfternoon?: AttendanceStatus;
  /** Work status before leave and overtime display overrides. */
  workMorning?: AttendanceStatus;
  workAfternoon?: AttendanceStatus;
  notes: string[];
}

export interface SourceEmployee {
  name: string;
  account: string;
  rule: string;
  department: string;
  position: string;
  jobNo: string;
  summaries: Record<string, unknown>;
  dayDetails: Record<number, string>;
}

export interface PersonnelProfile {
  name?: string;
  department?: string;
  entry_date?: string;
  enabled?: boolean;
}

export type PersonnelProfiles = Record<string, PersonnelProfile>;

export interface AttendanceMonth {
  year: number;
  month: number;
  startDate: string | null;
  endDate: string | null;
  sourcePath?: string;
  sourceFileName?: string;
  sourceFileHash?: string;
  employees: SourceEmployee[];
}

export interface DayReview {
  day: number;
  rawDetail: string;
  systemMorning: AttendanceStatus;
  systemAfternoon: AttendanceStatus;
  finalMorning: AttendanceStatus;
  finalAfternoon: AttendanceStatus;
  attendanceMorning?: AttendanceStatus;
  attendanceAfternoon?: AttendanceStatus;
  workMorning?: AttendanceStatus;
  workAfternoon?: AttendanceStatus;
  morningNote: string;
  afternoonNote: string;
  issues: ValidationIssue[];
  modified: boolean;
}

export interface SummaryReview {
  shouldAttendanceDays: number | null;
  actualAttendanceDays: number | null;
  payrollDays: number | null;
  entryDate: string;
  missingCardCount: number | null;
  lateText: string | null;
  latePunishment: string | null;
  earlyText: string | null;
  absenceDays: number | null;
  personalLeaveHours: number | null;
  sickLeaveDays: number | null;
  injuryLeaveDays: number | null;
  funeralLeaveDays: number | null;
  marriageLeaveDays: number | null;
  maternityLeaveDays: number | null;
  compLeaveHours: number | null;
  annualLeaveHours: number | null;
  overtimeHours: number | null;
  overtimeToSalary: string;
  remark: string;
}

export interface EmployeeReview {
  key: string;
  /** Personnel configuration remains keyed by the original account/name, independently of the row key. */
  profileKey?: string;
  sourceName: string;
  displayName: string;
  sourceDepartment: string;
  displayDepartment: string;
  departmentSchedule: import("./departmentSchedules").ResolvedDepartmentSchedule;
  entryDate: string;
  enabled: boolean;
  confirmed: boolean;
  sourceIndex: number;
  sourceSummaries?: Record<string, unknown>;
  days: DayReview[];
  summary: SummaryReview;
  manualSummaryOverrides: Partial<Record<keyof SummaryReview, boolean>>;
  issues: ValidationIssue[];
  notes: string;
}

export interface AttendanceProject {
  id: string;
  sourceKind: "wechat-monthly" | "exported-report";
  sourceFileHash: string;
  sourceFileName: string;
  year: number;
  month: number;
  restDays: number[];
  paidRestDays: number[];
  shouldAttendanceDays: number | null;
  payrollDays: number | null;
  employees: EmployeeReview[];
  createdAt: string;
  updatedAt: string;
}

export interface ExportIssueRow {
  index: number;
  employee: string;
  department: string;
  day: number | "";
  issueType: string;
  rawDetail: string;
  systemResult: string;
  beforeValue: string;
  afterValue: string;
  note: string;
  confirmedAt: string;
}

export interface ExportedReportDay {
  day: number;
  morning: AttendanceStatus;
  afternoon: AttendanceStatus;
}

export interface ExportedReportEmployee {
  name: string;
  department: string;
  sourceIndex: number;
  days: ExportedReportDay[];
  summary: SummaryReview;
}

export interface RecoveredDayCandidate {
  day: number;
  confidence: "high" | "medium";
  matchedEmployees: number;
  totalEmployees: number;
}

export interface RecoveredMonthlyParameters {
  source: "metadata" | "inferred" | "none";
  shouldAttendanceDays: number | null;
  payrollDays: number | null;
  restDays: number[];
  paidRestDays: number[];
  restDayCandidates: RecoveredDayCandidate[];
  paidRestDayCandidates: RecoveredDayCandidate[];
  warnings: string[];
}

export interface ExportedAttendanceReport {
  year: number;
  month: number;
  sourceFileName?: string;
  sourceFileHash?: string;
  employees: ExportedReportEmployee[];
  recoveredParameters?: RecoveredMonthlyParameters;
}
