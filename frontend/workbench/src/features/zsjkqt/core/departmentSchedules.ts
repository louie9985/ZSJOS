import { ATTENDANCE_CONFIG, DayHalf, parseClockMinutes } from "./attendanceConfig";
import type { EmployeeReview, PersonnelProfiles, SourceEmployee } from "./models";

export type DepartmentScheduleMode = "default" | "custom";
export type DepartmentScheduleSource = "display" | "source" | "default";

export interface WorkHalfSchedule {
  start: string;
  end: string;
}

export interface WorkSchedule {
  morning: WorkHalfSchedule;
  afternoon: WorkHalfSchedule;
}

export interface DepartmentScheduleOverride {
  mode: DepartmentScheduleMode;
  morning?: WorkHalfSchedule;
  afternoon?: WorkHalfSchedule;
}

export interface DepartmentScheduleSettings {
  default: WorkSchedule;
  departments: Record<string, DepartmentScheduleOverride>;
}

export interface AttendanceWorkSchedule {
  standardWorkHours: number;
  workHalves: Record<DayHalf, WorkHalfSchedule & { hours: number }>;
}

export interface ResolvedDepartmentSchedule {
  department: string;
  source: DepartmentScheduleSource;
  mode: DepartmentScheduleMode;
  schedule: AttendanceWorkSchedule;
}

export interface DepartmentScheduleValidationIssue {
  path: string;
  message: string;
}

export const DEFAULT_WORK_SCHEDULE: WorkSchedule = {
  morning: {
    start: ATTENDANCE_CONFIG.workHalves.morning.start,
    end: ATTENDANCE_CONFIG.workHalves.morning.end,
  },
  afternoon: {
    start: ATTENDANCE_CONFIG.workHalves.afternoon.start,
    end: ATTENDANCE_CONFIG.workHalves.afternoon.end,
  },
};

export const DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS: DepartmentScheduleSettings = {
  default: DEFAULT_WORK_SCHEDULE,
  departments: {},
};

export function normalizeDepartmentScheduleSettings(value: unknown): DepartmentScheduleSettings {
  if (!isObject(value)) return cloneDepartmentScheduleSettings(DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS);
  const defaultSchedule = normalizeWorkSchedule(value.default, DEFAULT_WORK_SCHEDULE);
  const departments: Record<string, DepartmentScheduleOverride> = {};
  if (isObject(value.departments)) {
    for (const [rawDepartment, rawOverride] of Object.entries(value.departments)) {
      const department = rawDepartment.trim();
      if (!department || !isObject(rawOverride)) continue;
      const mode = rawOverride.mode === "custom" ? "custom" : "default";
      departments[department] =
        mode === "custom"
          ? {
              mode,
              morning: normalizeWorkHalf(rawOverride.morning, defaultSchedule.morning),
              afternoon: normalizeWorkHalf(rawOverride.afternoon, defaultSchedule.afternoon),
            }
          : { mode: "default" };
    }
  }
  return { default: defaultSchedule, departments };
}

export function cloneDepartmentScheduleSettings(settings: DepartmentScheduleSettings): DepartmentScheduleSettings {
  return {
    default: cloneWorkSchedule(settings.default),
    departments: Object.fromEntries(
      Object.entries(settings.departments).map(([department, override]) => [
        department,
        override.mode === "custom"
          ? {
              mode: "custom",
              morning: override.morning ? { ...override.morning } : undefined,
              afternoon: override.afternoon ? { ...override.afternoon } : undefined,
            }
          : { mode: "default" },
      ]),
    ),
  };
}

export function ensureDepartments(settings: DepartmentScheduleSettings, departments: string[]): DepartmentScheduleSettings {
  const next = cloneDepartmentScheduleSettings(settings);
  for (const department of departments.map((item) => item.trim()).filter(Boolean)) {
    if (!next.departments[department]) next.departments[department] = { mode: "default" };
  }
  return next;
}

export function sourceDepartment(raw: string): string {
  if (!raw) return "";
  const leaves = raw
    .split(";")
    .map((path) => path.split("/").pop()?.trim() || "")
    .filter(Boolean);
  return leaves[0] || "";
}

export function knownDepartmentsFromMonth(employees: SourceEmployee[], profiles: PersonnelProfiles): string[] {
  const departments = new Set<string>();
  for (const employee of employees) {
    const key = employee.account.trim() || employee.name.trim();
    const displayDepartment = stringValue(profiles[key]?.department) || sourceDepartment(employee.department);
    if (displayDepartment) departments.add(displayDepartment);
    const sourceLeaf = sourceDepartment(employee.department);
    if (sourceLeaf) departments.add(sourceLeaf);
  }
  return [...departments].sort((left, right) => left.localeCompare(right, "zh-CN"));
}

export function knownDepartmentsFromProfiles(profiles: PersonnelProfiles): string[] {
  const departments = new Set<string>();
  for (const profile of Object.values(profiles)) {
    const department = stringValue(profile.department);
    if (department) departments.add(department);
  }
  return [...departments].sort((left, right) => left.localeCompare(right, "zh-CN"));
}

export function resolveDepartmentSchedule(
  displayDepartment: string,
  rawSourceDepartment: string,
  settings: DepartmentScheduleSettings,
): ResolvedDepartmentSchedule {
  const candidates: Array<{ department: string; source: DepartmentScheduleSource }> = [
    { department: displayDepartment.trim(), source: "display" },
    { department: rawSourceDepartment.trim(), source: "source" },
    { department: sourceDepartment(rawSourceDepartment), source: "source" },
  ];
  for (const candidate of candidates) {
    if (!candidate.department) continue;
    const override = settings.departments[candidate.department];
    if (override?.mode === "custom") {
      return {
        department: candidate.department,
        source: candidate.source,
        mode: override.mode,
        schedule: attendanceWorkSchedule(scheduleForOverride(settings.default, override)),
      };
    }
  }
  for (const candidate of candidates) {
    if (!candidate.department) continue;
    const override = settings.departments[candidate.department];
    if (override) {
      return {
        department: candidate.department,
        source: candidate.source,
        mode: override.mode,
        schedule: attendanceWorkSchedule(scheduleForOverride(settings.default, override)),
      };
    }
  }
  return {
    department: displayDepartment.trim() || sourceDepartment(rawSourceDepartment) || rawSourceDepartment.trim() || "默认班次",
    source: "default",
    mode: "default",
    schedule: attendanceWorkSchedule(settings.default),
  };
}

export function updateEmployeeDepartmentSchedule(
  employee: EmployeeReview,
  settings: DepartmentScheduleSettings,
): EmployeeReview {
  return {
    ...employee,
    departmentSchedule: resolveDepartmentSchedule(employee.displayDepartment, employee.sourceDepartment, settings),
  };
}

export function attendanceWorkSchedule(schedule: WorkSchedule): AttendanceWorkSchedule {
  const morningHours = hoursBetween(schedule.morning);
  const afternoonHours = hoursBetween(schedule.afternoon);
  return {
    standardWorkHours: roundHours(morningHours + afternoonHours),
    workHalves: {
      morning: { ...schedule.morning, hours: morningHours },
      afternoon: { ...schedule.afternoon, hours: afternoonHours },
    },
  };
}

export function workStartMinutes(half: DayHalf, schedule: AttendanceWorkSchedule): number {
  return parseClockMinutes(schedule.workHalves[half].start) ?? 0;
}

export function workEndMinutes(half: DayHalf, schedule: AttendanceWorkSchedule): number {
  return parseClockMinutes(schedule.workHalves[half].end) ?? 0;
}

export function workHalfForPunchMinute(minutes: number, schedule: AttendanceWorkSchedule): DayHalf {
  return minutes >= workStartMinutes("afternoon", schedule) ? "afternoon" : "morning";
}

export function overlapsWorkHalf(start: number, end: number, half: DayHalf, schedule: AttendanceWorkSchedule): boolean {
  return start < workEndMinutes(half, schedule) && end > workStartMinutes(half, schedule);
}

export function workHalfForRange(start: number, end: number, hours: number, schedule: AttendanceWorkSchedule): DayHalf | "full-day" | "" {
  if (hours >= schedule.standardWorkHours) return "full-day";
  const morning = overlapsWorkHalf(start, end, "morning", schedule);
  const afternoon = overlapsWorkHalf(start, end, "afternoon", schedule);
  if (morning && afternoon) return "full-day";
  return morning ? "morning" : afternoon ? "afternoon" : "";
}

export function validateDepartmentScheduleSettings(settings: DepartmentScheduleSettings): DepartmentScheduleValidationIssue[] {
  const issues = validateWorkSchedule(settings.default, "default");
  for (const [department, override] of Object.entries(settings.departments)) {
    if (override.mode !== "custom") continue;
    issues.push(...validateWorkSchedule(scheduleForOverride(settings.default, override), `departments.${department}`));
  }
  return issues;
}

export function departmentScheduleStatus(
  department: string,
  settings: DepartmentScheduleSettings,
): "使用默认" | "单独设置" | "新部门，已套默认" {
  const override = settings.departments[department];
  if (!override) return "新部门，已套默认";
  return override.mode === "custom" ? "单独设置" : "使用默认";
}

export function scheduleForOverride(defaultSchedule: WorkSchedule, override?: DepartmentScheduleOverride): WorkSchedule {
  if (!override || override.mode === "default") return cloneWorkSchedule(defaultSchedule);
  return {
    morning: { ...(override.morning || defaultSchedule.morning) },
    afternoon: { ...(override.afternoon || defaultSchedule.afternoon) },
  };
}

export function formatScheduleText(schedule: AttendanceWorkSchedule): string {
  return `上午 ${schedule.workHalves.morning.start}-${schedule.workHalves.morning.end}（${formatHours(schedule.workHalves.morning.hours)} 小时），下午 ${schedule.workHalves.afternoon.start}-${schedule.workHalves.afternoon.end}（${formatHours(schedule.workHalves.afternoon.hours)} 小时）`;
}

export function formatHours(value: number): string {
  return value.toFixed(2);
}

function validateWorkSchedule(schedule: WorkSchedule, path: string): DepartmentScheduleValidationIssue[] {
  const issues: DepartmentScheduleValidationIssue[] = [];
  const morningStart = parseClockMinutes(schedule.morning.start);
  const morningEnd = parseClockMinutes(schedule.morning.end);
  const afternoonStart = parseClockMinutes(schedule.afternoon.start);
  const afternoonEnd = parseClockMinutes(schedule.afternoon.end);

  if (morningStart == null || morningEnd == null) issues.push({ path: `${path}.morning`, message: "上午时间格式不合法" });
  if (afternoonStart == null || afternoonEnd == null) issues.push({ path: `${path}.afternoon`, message: "下午时间格式不合法" });
  if (morningStart != null && morningEnd != null && morningStart >= morningEnd) {
    issues.push({ path: `${path}.morning`, message: "上午开始时间必须早于结束时间" });
  }
  if (afternoonStart != null && afternoonEnd != null && afternoonStart >= afternoonEnd) {
    issues.push({ path: `${path}.afternoon`, message: "下午开始时间必须早于结束时间" });
  }
  if (morningEnd != null && afternoonStart != null && morningEnd > afternoonStart) {
    issues.push({ path, message: "上午和下午不能重叠" });
  }
  return issues;
}

function normalizeWorkSchedule(value: unknown, fallback: WorkSchedule): WorkSchedule {
  if (!isObject(value)) return cloneWorkSchedule(fallback);
  return {
    morning: normalizeWorkHalf(value.morning, fallback.morning),
    afternoon: normalizeWorkHalf(value.afternoon, fallback.afternoon),
  };
}

function normalizeWorkHalf(value: unknown, fallback: WorkHalfSchedule): WorkHalfSchedule {
  if (!isObject(value)) return { ...fallback };
  return {
    start: timeValue(value.start) || fallback.start,
    end: timeValue(value.end) || fallback.end,
  };
}

function cloneWorkSchedule(schedule: WorkSchedule): WorkSchedule {
  return {
    morning: { ...schedule.morning },
    afternoon: { ...schedule.afternoon },
  };
}

function hoursBetween(schedule: WorkHalfSchedule): number {
  const start = parseClockMinutes(schedule.start);
  const end = parseClockMinutes(schedule.end);
  if (start == null || end == null || end <= start) return 0;
  return roundHours((end - start) / 60);
}

function roundHours(value: number): number {
  return Number(value.toFixed(2));
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function timeValue(value: unknown): string {
  if (typeof value !== "string") return "";
  return parseClockMinutes(value) == null ? "" : value.trim();
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
