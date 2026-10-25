import { AttendanceStatus, DayResult, DayReview, NORMAL_MARK, PAID_REST_MARK, REST_MARK, ValidationIssue } from "./models";
import { refreshSourceSummaryReviewIssue } from "./sourceSummaryReview";
import { ATTENDANCE_CONFIG, DayHalf, parseClockMinutes } from "./attendanceConfig";
import { attendanceWorkSchedule, DEFAULT_WORK_SCHEDULE, workHalfForPunchMinute, workHalfForRange, type AttendanceWorkSchedule } from "./departmentSchedules";

const LEAVE_TYPES = [
  "陪产假",
  "产检假",
  "调休假",
  "工伤假",
  "工伤",
  "事假",
  "病假",
  "年假",
  "调休",
  "婚假",
  "产假",
  "丧假",
] as const;

export function classifyDay(
  detail: unknown,
  employee = "",
  day: number | null = null,
  restStatus: AttendanceStatus | boolean = false,
  workSchedule: AttendanceWorkSchedule = attendanceWorkSchedule(DEFAULT_WORK_SCHEDULE),
): { result: DayResult; issues: ValidationIssue[] } {
  const text = clean(detail);
  const result: DayResult = { morning: "", afternoon: "", notes: [] };
  const issues: ValidationIssue[] = [];

  if (!text) return { result, issues };

  if (restStatus) {
    return classifySelectedRestDay(text, result, issues, employee, day, restMark(restStatus));
  }

  const hasUnapprovedAnomaly = hasAttendanceAnomaly(text) && !hasOuting(text);

  if (text.includes("正常（休息）") || text.includes("正常(休息)")) {
    const mark = restMark(restStatus);
    result.morning = mark;
    result.afternoon = mark;
    return { result, issues };
  }

  let recognized = false;

  if (text.includes("正常")) {
    result.morning = NORMAL_MARK;
    result.afternoon = NORMAL_MARK;
    recognized = true;
  }

  if (hasUnapprovedAnomaly) {
    recognized = true;
    issues.push(issue(employee, day, "打卡异常未关联外出/外勤申请，需人工确认", text));
  }

  const leaveType = findLeaveType(text);
  if (leaveType) recognized = true;

  if (text.includes("缺卡")) {
    recognized = true;
    for (const missingTime of text.matchAll(/缺卡\(([^)）]+)[)）]/g)) {
      const half = missingCardHalf(missingTime[1]);
      if (half === "morning") result.morning = "缺卡";
      else if (half === "afternoon") result.afternoon = "缺卡";
      else {
        result.morning = "异常";
        result.afternoon = "异常";
        issues.push(issue(employee, day, `无法判断缺卡时段：${missingTime[1]}`, text));
      }
    }
  }

  if (text.includes("迟到")) {
    recognized = true;
    applyLate(result, text, workSchedule);
  }

  if (text.includes("早退")) {
    recognized = true;
    result.afternoon = "早退";
  }

  if (hasOuting(text)) {
    recognized = true;
    applyOuting(result, text);
  }

  if (leaveType) {
    result.workMorning = result.morning;
    result.workAfternoon = result.afternoon;
    applyLeave(result, text, leaveType, workSchedule);
  }

  if (text.includes("加班")) {
    recognized = true;
    const beforeOvertime = { morning: result.morning, afternoon: result.afternoon };
    applyOvertime(result, text);
    applyOvertimeAttendanceBasis(result, beforeOvertime);
  }

  if (text.includes("旷工")) {
    recognized = true;
    if (![leaveType, text.includes("缺卡"), text.includes("迟到"), text.includes("早退"), text.includes("正常"), text.includes("加班")].some(Boolean)) {
      result.morning = "旷工";
      result.afternoon = "旷工";
    }
  }

  if (hasUnapprovedAnomaly) applyAttendanceAnomaly(result, text);

  if (!recognized) {
    result.morning = "异常";
    result.afternoon = "异常";
    issues.push(issue(employee, day, "无法识别每日明细", text));
  }

  return { result, issues };
}

export function reclassifyDaysForSchedule(days: DayReview[], schedule: AttendanceWorkSchedule, employee: string): DayReview[] {
  return days.map((day) => {
    const restStatus = [day.systemMorning, day.systemAfternoon].find((status) => status === REST_MARK || status === PAID_REST_MARK) || false;
    const { result, issues } = classifyDay(day.rawDetail, employee, day.day, restStatus, schedule);
    const next: DayReview = {
      ...day,
      systemMorning: result.morning,
      systemAfternoon: result.afternoon,
      finalMorning: day.finalMorning === day.systemMorning ? result.morning : day.finalMorning,
      finalAfternoon: day.finalAfternoon === day.systemAfternoon ? result.afternoon : day.finalAfternoon,
      attendanceMorning: result.attendanceMorning,
      attendanceAfternoon: result.attendanceAfternoon,
      workMorning: result.workMorning,
      workAfternoon: result.workAfternoon,
      issues,
    };
    return refreshSourceSummaryReviewIssue(next, employee);
  });
}

function classifySelectedRestDay(
  text: string,
  result: DayResult,
  issues: ValidationIssue[],
  employee: string,
  day: number | null,
  mark: AttendanceStatus,
): { result: DayResult; issues: ValidationIssue[] } {
  result.morning = mark;
  result.afternoon = mark;

  const hasUnapprovedAnomaly = hasAttendanceAnomaly(text) && !hasOuting(text);
  const leaveType = findLeaveType(text);
  const recognized = hasRestCoveredStatus(text, leaveType) || text.includes("加班") || hasUnapprovedAnomaly;

  if (text.includes("加班")) applyOvertime(result, text);

  if (hasUnapprovedAnomaly) {
    issues.push(issue(employee, day, "打卡异常未关联外出/外勤申请，需人工确认", text));
    applyAttendanceAnomaly(result, text);
  }

  if (!recognized) {
    result.morning = "异常";
    result.afternoon = "异常";
    issues.push(issue(employee, day, "无法识别每日明细", text));
  }

  return { result, issues };
}

function hasRestCoveredStatus(text: string, leaveType: AttendanceStatus | ""): boolean {
  return Boolean(
    leaveType ||
      text.includes("正常") ||
      text.includes("缺卡") ||
      text.includes("迟到") ||
      text.includes("早退") ||
      text.includes("旷工") ||
      hasOuting(text),
  );
}

function clean(value: unknown): string {
  if (value == null) return "";
  const text = String(value).replace(/\r\n/g, "\n").trim();
  return text === "--" ? "" : text;
}

function restMark(restStatus: AttendanceStatus | boolean): AttendanceStatus {
  if (restStatus === PAID_REST_MARK) return PAID_REST_MARK;
  return REST_MARK;
}

function findLeaveType(text: string): AttendanceStatus | "" {
  for (const leaveType of LEAVE_TYPES) {
    if (text.includes(leaveType)) {
      if (leaveType === "调休假") return "调休";
      if (leaveType === "工伤假") return "工伤";
      return leaveType;
    }
  }
  return "";
}

function hasAttendanceAnomaly(text: string): boolean {
  return text.toLowerCase().includes("wifi异常") || text.includes("地点异常");
}

function hasOuting(text: string): boolean {
  return text.includes("外出") || text.includes("外勤");
}

function applyAttendanceAnomaly(result: DayResult, text: string): void {
  const matches = [...text.matchAll(/(?:wifi异常|地点异常)-([^;；\n]+)/gi)];
  if (!matches.length) {
    result.morning = "异常";
    result.afternoon = "异常";
    return;
  }

  for (const match of matches) {
    const half = anomalyHalf(match[1]);
    if (half === "morning") result.morning = "异常";
    else if (half === "afternoon") result.afternoon = "异常";
    else {
      result.morning = "异常";
      result.afternoon = "异常";
    }
  }
}

function anomalyHalf(value: string): "morning" | "afternoon" | "" {
  if (value.includes("次日")) return "afternoon";
  const match = value.match(/(\d{1,2}):\d{2}/);
  if (!match) return "";
  return Number(match[1]) < 12 ? "morning" : "afternoon";
}

function applyLeave(result: DayResult, text: string, leaveType: AttendanceStatus, workSchedule: AttendanceWorkSchedule): void {
  const entries = extractLeaveEntries(text, leaveType);

  if (!entries.length) {
    applyLeaveToHalf(result, "morning", leaveType);
    applyLeaveToHalf(result, "afternoon", leaveType);
    return;
  }

  for (const entry of entries) {
    if (entry.hours >= workSchedule.standardWorkHours) {
      applyLeaveToHalf(result, "morning", leaveType);
      applyLeaveToHalf(result, "afternoon", leaveType);
      continue;
    }

    if (entry.startMinutes == null || entry.endMinutes == null) {
      applyLeaveToHalf(result, "morning", leaveType);
      applyLeaveToHalf(result, "afternoon", leaveType);
      continue;
    }

    const half = workHalfForRange(entry.startMinutes, entry.endMinutes, entry.hours, workSchedule);
    if (half === "morning") applyLeaveToHalf(result, "morning", leaveType);
    else if (half === "afternoon") applyLeaveToHalf(result, "afternoon", leaveType);
    else if (half === "full-day") {
      applyLeaveToHalf(result, "morning", leaveType);
      applyLeaveToHalf(result, "afternoon", leaveType);
    }
  }
}

function applyLeaveToHalf(result: DayResult, half: DayHalf, leaveType: AttendanceStatus): void {
  const currentStatus = half === "morning" ? result.morning : result.afternoon;
  if (currentStatus === NORMAL_MARK) {
    if (half === "morning") result.attendanceMorning = currentStatus;
    else result.attendanceAfternoon = currentStatus;
  }

  if (half === "morning") result.morning = leaveType;
  else result.afternoon = leaveType;
}

function applyLate(result: DayResult, text: string, workSchedule: AttendanceWorkSchedule): void {
  let matched = false;
  for (const match of text.matchAll(/迟到\s*\d+\s*分钟(?:-\s*(\d{1,2}[:：]\d{2}))?/g)) {
    matched = true;
    const minutes = parseClockMinutes(match[1]);
    if (minutes == null) {
      result.morning = "迟到";
      continue;
    }

    const half = workHalfForPunchMinute(minutes, workSchedule);
    if (half === "morning") result.morning = "迟到";
    else result.afternoon = "迟到";
  }

  if (!matched) result.morning = "迟到";
}

function extractLeaveEntries(
  text: string,
  label: string,
): Array<{ hours: number; startMinutes: number | null; endMinutes: number | null }> {
  const labelPattern = label === "工伤" ? "工伤(?:假)?" : label === "调休" ? "调休(?:假)?" : escapeRegExp(label);
  const pattern = new RegExp(`${labelPattern}\\s*([0-9]+(?:\\.[0-9]+)?)\\s*小时\\s*(?:[（(](.*?)[）)])?`, "g");
  return [...text.matchAll(pattern)].map((match) => {
    const times = [...(match[2] || "").matchAll(/\d{1,2}[:：]\d{2}/g)]
      .map((item) => parseClockMinutes(item[0]))
      .filter((item): item is number => item != null);

    return {
      hours: Number(match[1]),
      startMinutes: times[0] ?? null,
      endMinutes: times.length >= 2 ? times[times.length - 1] : null,
    };
  });
}

function applyOvertime(result: DayResult, text: string): void {
  for (const match of text.matchAll(/加班\s*([0-9]+(?:\.[0-9]+)?)\s*小时.*?[（(](.*?)[）)]/gs)) {
    const hours = Number(match[1]);
    const times = [...match[2].matchAll(/(\d{1,2}):\d{2}/g)].map((item) => Number(item[1]));
    if (times.length < 2) {
      if (hours >= 8) {
        result.morning = "加班";
        result.afternoon = "加班";
      }
      continue;
    }

    const [startHour, endHour] = [times[0], times[times.length - 1]];
    if (startHour < 12 && endHour > 13) {
      result.morning = "加班";
      result.afternoon = "加班";
    } else if (startHour < 12) result.morning = "加班";
    else result.afternoon = "加班";
  }
}

function applyOvertimeAttendanceBasis(
  result: DayResult,
  beforeOvertime: Record<DayHalf, AttendanceStatus>,
): void {
  if (!ATTENDANCE_CONFIG.overtime.countAsActualAttendanceWhenBaseWorked) return;

  if (result.morning === "加班") result.attendanceMorning = overtimeAttendanceBasis(beforeOvertime.morning);
  if (result.afternoon === "加班") result.attendanceAfternoon = overtimeAttendanceBasis(beforeOvertime.afternoon);
}

function overtimeAttendanceBasis(baseStatus: AttendanceStatus): AttendanceStatus {
  if (baseStatus) return baseStatus;
  return ATTENDANCE_CONFIG.overtime.countRestDayOvertimeAsActualAttendance ? "加班" : "";
}

function applyOuting(result: DayResult, text: string): void {
  for (const match of text.matchAll(/外[出勤]\s*([0-9]+(?:\.[0-9]+)?)\s*小时.*?[（(](.*?)[）)]/gs)) {
    const hours = Number(match[1]);
    const times = [...match[2].matchAll(/(\d{1,2}):\d{2}/g)].map((item) => Number(item[1]));
    if (hours >= 7) {
      result.morning = NORMAL_MARK;
      result.afternoon = NORMAL_MARK;
      return;
    }
    if (times.length < 2) continue;

    const [startHour, endHour] = [times[0], times[times.length - 1]];
    if (startHour < 12 && endHour > 13) {
      result.morning = NORMAL_MARK;
      result.afternoon = NORMAL_MARK;
    } else if (startHour < 12) result.morning = NORMAL_MARK;
    else result.afternoon = NORMAL_MARK;
  }
}

function missingCardHalf(value: string): "morning" | "afternoon" | "" {
  if (value.includes("次日")) return "afternoon";
  const match = value.match(/(\d{1,2}):\d{2}/);
  if (!match) return "";
  const hour = Number(match[1]);
  return hour < 6 || hour >= 12 ? "afternoon" : "morning";
}

function issue(employee: string, day: number | null, message: string, rawValue: string): ValidationIssue {
  return { level: "warning", employee, day, message, rawValue };
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
