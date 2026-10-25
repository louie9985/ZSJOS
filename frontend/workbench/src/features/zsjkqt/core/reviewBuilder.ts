import { classifyDay, reclassifyDaysForSchedule } from "./dayClassifier";
import {
  AttendanceMonth,
  AttendanceProject,
  DayReview,
  EmployeeReview,
  ExportedAttendanceReport,
  ExportedReportEmployee,
  PAID_REST_MARK,
  PersonnelProfiles,
  REST_MARK,
  SourceEmployee,
  SummaryReview,
  ValidationIssue,
} from "./models";
import {
  DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS,
  DepartmentScheduleSettings,
  resolveDepartmentSchedule,
  sourceDepartment,
} from "./departmentSchedules";
import { recoverMonthlyParameters } from "./recoveredMonthlyParameters";
import { recalculateSummary } from "./summary";
import { normalizeProjectRowKeys, sourceEmployeeRowKey } from './employeeIdentity';

export interface BuildReviewOptions {
  restDays: number[];
  paidRestDays: number[];
  shouldAttendanceDays: number | null;
  payrollDays: number | null;
}

export function buildAttendanceProject(
  month: AttendanceMonth,
  options: BuildReviewOptions,
  personnelProfiles: PersonnelProfiles = {},
  departmentSchedules: DepartmentScheduleSettings = DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS,
): AttendanceProject {
  const now = new Date().toISOString();
  const employees = month.employees.map((employee, index) =>
    buildEmployeeReview(month, employee, index + 1, options, personnelProfiles, departmentSchedules),
  );
  return {
    id: `${month.sourceFileHash || month.sourceFileName || "manual"}-${month.year}-${month.month}`,
    sourceKind: "wechat-monthly",
    sourceFileHash: month.sourceFileHash || "",
    sourceFileName: month.sourceFileName || "",
    year: month.year,
    month: month.month,
    restDays: options.restDays,
    paidRestDays: options.paidRestDays,
    shouldAttendanceDays: options.shouldAttendanceDays,
    payrollDays: options.payrollDays,
    employees,
    createdAt: now,
    updatedAt: now,
  };
}

export function buildProjectFromExportedReport(
  report: ExportedAttendanceReport,
  departmentSchedules: DepartmentScheduleSettings = DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS,
): AttendanceProject {
  const now = new Date().toISOString();
  const recoveredParameters = recoverMonthlyParameters(report);
  return {
    id: `${report.sourceFileHash || report.sourceFileName || "exported-report"}-${report.year}-${report.month}`,
    sourceKind: "exported-report",
    sourceFileHash: report.sourceFileHash || "",
    sourceFileName: report.sourceFileName || "",
    year: report.year,
    month: report.month,
    restDays: recoveredParameters.restDays,
    paidRestDays: recoveredParameters.paidRestDays,
    shouldAttendanceDays: recoveredParameters.shouldAttendanceDays,
    payrollDays: recoveredParameters.payrollDays,
    employees: report.employees.map((employee) => buildEmployeeReviewFromExportedReport(report, employee, departmentSchedules)),
    createdAt: now,
    updatedAt: now,
  };
}

export function applyReimportMonthlyParameters(
  project: AttendanceProject,
  options: BuildReviewOptions,
): AttendanceProject {
  return {
    ...project,
    restDays: options.restDays,
    paidRestDays: options.paidRestDays,
    shouldAttendanceDays: options.shouldAttendanceDays,
    payrollDays: options.payrollDays,
    updatedAt: new Date().toISOString(),
    employees: project.employees.map((employee) => ({
      ...employee,
      summary: {
        ...employee.summary,
        shouldAttendanceDays: options.shouldAttendanceDays,
        payrollDays: options.payrollDays,
      },
      manualSummaryOverrides: {
        ...employee.manualSummaryOverrides,
        shouldAttendanceDays: true,
        payrollDays: true,
      },
    })),
  };
}

function buildEmployeeReviewFromExportedReport(
  report: ExportedAttendanceReport,
  employee: ExportedReportEmployee,
  departmentSchedules: DepartmentScheduleSettings,
): EmployeeReview {
  const displayName = employee.name.trim();
  const displayDepartment = employee.department.trim();
  const sourceIndex = employee.sourceIndex;
  const departmentSchedule = resolveDepartmentSchedule(displayDepartment, displayDepartment, departmentSchedules);
  const daysInMonth = new Date(report.year, report.month, 0).getDate();
  const importedDays = new Map(employee.days.map((day) => [day.day, day]));
  const days: DayReview[] = [];

  for (let day = 1; day <= daysInMonth; day += 1) {
    const importedDay = importedDays.get(day);
    const morning = importedDay?.morning || "";
    const afternoon = importedDay?.afternoon || "";
    days.push({
      day,
      rawDetail: "",
      systemMorning: morning,
      systemAfternoon: afternoon,
      finalMorning: morning,
      finalAfternoon: afternoon,
      morningNote: "",
      afternoonNote: "",
      issues: [],
      modified: false,
    });
  }

  return {
    key: `${displayName}-${displayDepartment}-${sourceIndex}`,
    sourceName: displayName,
    displayName,
    sourceDepartment: displayDepartment,
    displayDepartment,
    departmentSchedule,
    entryDate: stringValue(employee.summary.entryDate),
    enabled: true,
    confirmed: false,
    sourceIndex,
    sourceSummaries: exportedSummarySourceValues(employee.summary),
    days,
    summary: { ...employee.summary },
    manualSummaryOverrides: {},
    issues: [],
    notes: "",
  };
}

function exportedSummarySourceValues(summary: SummaryReview): Record<string, unknown> {
  return {
    ...summary,
    "事假(小时)": summary.personalLeaveHours,
    "调休假(小时)": summary.compLeaveHours,
    "年假(小时)": summary.annualLeaveHours,
    "加班时长(小时)": summary.overtimeHours,
  };
}

export function mergeRebuiltProjectWithReviewState(
  rebuiltProject: AttendanceProject,
  previousProject: AttendanceProject | null,
  departmentSchedules: DepartmentScheduleSettings = DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS,
): AttendanceProject {
  if (!previousProject) return rebuiltProject;
  previousProject = normalizeProjectRowKeys(previousProject);

  const parametersChanged = projectMonthlyParametersChanged(rebuiltProject, previousProject);
  const previousByKey = new Map(previousProject.employees.map((employee) => [employee.key, employee]));

  return {
    ...rebuiltProject,
    createdAt: previousProject.createdAt,
    employees: rebuiltProject.employees.map((rebuiltEmployee) => {
      const previousEmployee = previousByKey.get(rebuiltEmployee.key);
      if (!previousEmployee || !isSameSourceEmployee(rebuiltEmployee, previousEmployee)) return rebuiltEmployee;
      return mergeRebuiltEmployeeWithReviewState(rebuiltEmployee, previousEmployee, {
        departmentSchedules,
        forceReview: parametersChanged,
        shouldAttendanceDays: rebuiltProject.shouldAttendanceDays,
        payrollDays: rebuiltProject.payrollDays,
      });
    }),
  };
}

export function projectMonthlyParametersChanged(left: AttendanceProject, right: AttendanceProject): boolean {
  return (
    !sameDays(left.restDays, right.restDays) ||
    !sameDays(left.paidRestDays, right.paidRestDays) ||
    left.shouldAttendanceDays !== right.shouldAttendanceDays ||
    left.payrollDays !== right.payrollDays
  );
}

function mergeRebuiltEmployeeWithReviewState(
  rebuiltEmployee: EmployeeReview,
  previousEmployee: EmployeeReview,
  options: {
    departmentSchedules: DepartmentScheduleSettings;
    forceReview: boolean;
    shouldAttendanceDays: number | null;
    payrollDays: number | null;
  },
): EmployeeReview {
  const displayName = previousEmployee.displayName;
  const displayDepartment = previousEmployee.displayDepartment;
  const entryDate = previousEmployee.entryDate;
  const departmentSchedule = resolveDepartmentSchedule(displayDepartment, rebuiltEmployee.sourceDepartment, options.departmentSchedules);
  const days = reclassifyDaysForSchedule(
    mergeRebuiltDaysWithReviewState(rebuiltEmployee.days, previousEmployee.days),
    departmentSchedule.schedule,
    displayName,
  );
  const recalculatedSummary = recalculateSummary({
    sourceSummaries: rebuiltEmployee.sourceSummaries || {},
    days,
    shouldAttendanceDays: options.shouldAttendanceDays,
    payrollDays: options.payrollDays,
    entryDate,
    workSchedule: departmentSchedule.schedule,
  });
  const manualSummaryOverrides = {
    ...previousEmployee.manualSummaryOverrides,
    shouldAttendanceDays: false,
    payrollDays: false,
  };
  const summary = mergeSummaryWithManualOverrides(recalculatedSummary, previousEmployee.summary, manualSummaryOverrides);

  return {
    ...rebuiltEmployee,
    displayName,
    displayDepartment,
    departmentSchedule,
    entryDate,
    enabled: previousEmployee.enabled,
    confirmed: options.forceReview ? false : previousEmployee.confirmed,
    days,
    summary,
    manualSummaryOverrides,
    notes: previousEmployee.notes,
  };
}

function mergeRebuiltDaysWithReviewState(rebuiltDays: DayReview[], previousDays: DayReview[]): DayReview[] {
  const previousByDay = new Map(previousDays.map((day) => [day.day, day]));
  return rebuiltDays.map((rebuiltDay) => {
    const previousDay = previousByDay.get(rebuiltDay.day);
    if (!previousDay?.modified) return rebuiltDay;
    return {
      ...rebuiltDay,
      finalMorning: previousDay.finalMorning === previousDay.systemMorning ? rebuiltDay.finalMorning : previousDay.finalMorning,
      finalAfternoon: previousDay.finalAfternoon === previousDay.systemAfternoon ? rebuiltDay.finalAfternoon : previousDay.finalAfternoon,
      morningNote: previousDay.morningNote,
      afternoonNote: previousDay.afternoonNote,
      modified: true,
    };
  });
}

function mergeSummaryWithManualOverrides(
  recalculated: SummaryReview,
  previous: SummaryReview,
  manualOverrides: Partial<Record<keyof SummaryReview, boolean>>,
): SummaryReview {
  const next = { ...recalculated };
  for (const key of Object.keys(manualOverrides) as Array<keyof SummaryReview>) {
    if (manualOverrides[key]) next[key] = previous[key] as never;
  }
  return next;
}

function sameDays(left: number[], right: number[]): boolean {
  if (left.length !== right.length) return false;
  const sortedLeft = [...left].sort((a, b) => a - b);
  const sortedRight = [...right].sort((a, b) => a - b);
  return sortedLeft.every((day, index) => day === sortedRight[index]);
}

function isSameSourceEmployee(left: EmployeeReview, right: EmployeeReview): boolean {
  return left.sourceIndex === right.sourceIndex || left.sourceName === right.sourceName;
}

export function buildEmployeeReview(
  month: AttendanceMonth,
  employee: SourceEmployee,
  sourceIndex: number,
  options: BuildReviewOptions,
  personnelProfiles: PersonnelProfiles = {},
  departmentSchedules: DepartmentScheduleSettings = DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS,
): EmployeeReview {
  const profileKey = employee.account.trim() || employee.name.trim();
  const key = sourceEmployeeRowKey(profileKey, sourceIndex);
  const profile = personnelProfiles[profileKey] || {};
  const entryDate = stringValue(profile.entry_date);
  const displayName = stringValue(profile.name) || employee.name;
  const displayDepartment = stringValue(profile.department) || sourceDepartment(employee.department);
  const departmentSchedule = resolveDepartmentSchedule(displayDepartment, employee.department, departmentSchedules);
  const enabled = typeof profile.enabled === "boolean" ? profile.enabled : true;
  const issues: ValidationIssue[] = [];
  const days: DayReview[] = [];
  const daysInMonth = new Date(month.year, month.month, 0).getDate();

  for (let day = 1; day <= daysInMonth; day += 1) {
    const rawDetail = employee.dayDetails[day] || "";
    const restStatus = options.paidRestDays.includes(day) ? PAID_REST_MARK : options.restDays.includes(day) ? REST_MARK : "";
    const { result, issues: dayIssues } = classifyDay(rawDetail, employee.name, day, restStatus, departmentSchedule.schedule);
    issues.push(...dayIssues);
    days.push({
      day,
      rawDetail,
      systemMorning: result.morning,
      systemAfternoon: result.afternoon,
      finalMorning: result.morning,
      finalAfternoon: result.afternoon,
      attendanceMorning: result.attendanceMorning,
      attendanceAfternoon: result.attendanceAfternoon,
      workMorning: result.workMorning,
      workAfternoon: result.workAfternoon,
      morningNote: "",
      afternoonNote: "",
      issues: dayIssues,
      modified: false,
    });
  }

  const summary: SummaryReview = recalculateSummary({
    sourceSummaries: employee.summaries,
    days,
    shouldAttendanceDays: options.shouldAttendanceDays,
    payrollDays: options.payrollDays,
    entryDate,
    workSchedule: departmentSchedule.schedule,
  });

  return {
    key,
    profileKey,
    sourceName: employee.name,
    displayName,
    sourceDepartment: employee.department,
    displayDepartment,
    departmentSchedule,
    entryDate,
    enabled,
    confirmed: false,
    sourceIndex,
    sourceSummaries: employee.summaries,
    days,
    summary,
    manualSummaryOverrides: {},
    issues,
    notes: "",
  };
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
