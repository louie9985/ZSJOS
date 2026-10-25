import { AttendanceStatus, DayReview, SummaryReview } from "./models";
import {
  ATTENDANCE_CONFIG,
  DayHalf,
  parseClockMinutes,
} from "./attendanceConfig";
import { attendanceWorkSchedule, DEFAULT_WORK_SCHEDULE, overlapsWorkHalf, workEndMinutes, workHalfForPunchMinute, workHalfForRange, workStartMinutes, type AttendanceWorkSchedule } from "./departmentSchedules";

const PERSONAL_LEAVE_HOURS = "事假(小时)";
const COMP_LEAVE_HOURS = "调休假(小时)";
const COMP_LEAVE_HOUR_ALIASES = [COMP_LEAVE_HOURS, "调休(小时)"];
const ANNUAL_LEAVE_HOURS = "年假(小时)";
const OVERTIME_HOURS = "加班时长(小时)";
const ATTENDED_STATUSES = new Set<AttendanceStatus>(ATTENDANCE_CONFIG.actualAttendanceStatuses);
const STATUS_HOUR_LABELS: Partial<Record<AttendanceStatus, string[]>> = {
  出勤: ["外出", "外勤"],
  事假: ["事假"],
  病假: ["病假"],
  年假: ["年假"],
  调休: ["调休假", "调休"],
  婚假: ["婚假"],
  产假: ["产假", "陪产假", "产检假"],
  丧假: ["丧假"],
  工伤: ["工伤假", "工伤"],
};
const HOUR_LABELS = [...new Set(Object.values(STATUS_HOUR_LABELS).flat())].sort((a, b) => b.length - a.length);
const ADJUSTABLE_HOUR_STATUSES = new Set<AttendanceStatus>(["事假", "调休"]);
const DURATION_STATUSES = new Set<AttendanceStatus>(Object.keys(STATUS_HOUR_LABELS) as AttendanceStatus[]);
type LeaveHourDelta = {
  personalLeaveHours: number;
  compLeaveHours: number;
};
type HourEntry = {
  label: string;
  hours: number;
  half: DayHalf | "full-day" | "";
  startMinutes?: number;
  endMinutes?: number;
};

interface RecalculateSummaryInput {
  sourceSummaries: Record<string, unknown>;
  days: DayReview[];
  shouldAttendanceDays: number | null;
  payrollDays: number | null;
  entryDate: string;
  workSchedule?: AttendanceWorkSchedule;
}

export function recalculateSummary(input: RecalculateSummaryInput): SummaryReview {
  const workSchedule = input.workSchedule || attendanceWorkSchedule(DEFAULT_WORK_SCHEDULE);
  const stats = {
    missingCardCount: 0,
    lateCount: 0,
    lateMinutes: 0,
    earlyCount: 0,
    earlyMinutes: 0,
    absenceDays: 0,
    sickLeaveDays: 0,
    injuryLeaveDays: 0,
    funeralLeaveDays: 0,
    marriageLeaveDays: 0,
    maternityLeaveDays: 0,
  };

  for (const day of input.days) {
    addAdjustedExceptionStats(stats, day, workSchedule);
  }
  const adjustedLeaveHours = recalculateAdjustedLeaveHours(input, workSchedule);

  return {
    shouldAttendanceDays: input.shouldAttendanceDays,
    actualAttendanceDays: blankZero(recalculateActualAttendanceDays(input.days, workSchedule), ATTENDANCE_CONFIG.actualAttendanceFractionDigits),
    payrollDays: input.payrollDays,
    entryDate: input.entryDate,
    missingCardCount: blankZero(stats.missingCardCount),
    lateText: minutesText(stats.lateMinutes, stats.lateCount),
    latePunishment: latePunishment(stats.lateCount, stats.lateMinutes),
    earlyText: minutesText(stats.earlyMinutes, stats.earlyCount),
    absenceDays: blankZero(stats.absenceDays),
    personalLeaveHours: adjustedLeaveHours.personalLeaveHours,
    sickLeaveDays: blankZero(stats.sickLeaveDays),
    injuryLeaveDays: blankZero(stats.injuryLeaveDays),
    funeralLeaveDays: blankZero(stats.funeralLeaveDays),
    marriageLeaveDays: blankZero(stats.marriageLeaveDays),
    maternityLeaveDays: blankZero(stats.maternityLeaveDays),
    compLeaveHours: adjustedLeaveHours.compLeaveHours,
    annualLeaveHours: toNumber(input.sourceSummaries[ANNUAL_LEAVE_HOURS]),
    overtimeHours: toNumber(input.sourceSummaries[OVERTIME_HOURS]),
    overtimeToSalary: "",
    remark: "",
  };
}

function recalculateAdjustedLeaveHours(
  input: RecalculateSummaryInput,
  workSchedule: AttendanceWorkSchedule,
): Pick<SummaryReview, "personalLeaveHours" | "compLeaveHours"> {
  let personalLeaveHours = toNumber(input.sourceSummaries[PERSONAL_LEAVE_HOURS]) ?? 0;
  let compLeaveHours = toNumber(firstSourceSummary(input.sourceSummaries, COMP_LEAVE_HOUR_ALIASES)) ?? 0;

  for (const day of input.days) {
    const morningDelta = leaveHourDelta(day, "morning", day.systemMorning, day.finalMorning, workSchedule, day.attendanceMorning);
    const afternoonDelta = leaveHourDelta(day, "afternoon", day.systemAfternoon, day.finalAfternoon, workSchedule, day.attendanceAfternoon);
    personalLeaveHours += morningDelta.personalLeaveHours + afternoonDelta.personalLeaveHours;
    compLeaveHours += morningDelta.compLeaveHours + afternoonDelta.compLeaveHours;
  }

  return {
    personalLeaveHours: blankZero(Math.max(0, personalLeaveHours)),
    compLeaveHours: blankZero(Math.max(0, compLeaveHours)),
  };
}

function leaveHourDelta(
  day: DayReview,
  half: DayHalf,
  systemStatus: AttendanceStatus,
  finalStatus: AttendanceStatus,
  workSchedule: AttendanceWorkSchedule,
  attendanceStatus?: AttendanceStatus,
): LeaveHourDelta {
  const delta = { personalLeaveHours: 0, compLeaveHours: 0 };
  if (!day.modified) return delta;
  if (systemStatus === finalStatus) return delta;
  if (!isLeaveReplacement(systemStatus, finalStatus, attendanceStatus)) return delta;

  const hours = extractAdjustedLeaveHours(day.rawDetail, half, originalLeaveStatus(systemStatus, attendanceStatus), finalStatus, workSchedule);
  if (systemStatus === "事假") delta.personalLeaveHours -= hours;
  if (systemStatus === "调休") delta.compLeaveHours -= hours;
  if (attendanceStatus === "事假" && systemStatus !== "事假") delta.personalLeaveHours -= hours;
  if (attendanceStatus === "调休" && systemStatus !== "调休") delta.compLeaveHours -= hours;
  if (finalStatus === "事假") delta.personalLeaveHours += hours;
  if (finalStatus === "调休") delta.compLeaveHours += hours;
  return delta;
}

function isLeaveReplacement(
  systemStatus: AttendanceStatus,
  finalStatus: AttendanceStatus,
  attendanceStatus?: AttendanceStatus,
): boolean {
  if (!ADJUSTABLE_HOUR_STATUSES.has(finalStatus)) return false;
  return ADJUSTABLE_HOUR_STATUSES.has(systemStatus) || Boolean(attendanceStatus && ADJUSTABLE_HOUR_STATUSES.has(attendanceStatus));
}

function originalLeaveStatus(systemStatus: AttendanceStatus, attendanceStatus?: AttendanceStatus): AttendanceStatus {
  if (ADJUSTABLE_HOUR_STATUSES.has(systemStatus)) return systemStatus;
  if (attendanceStatus && ADJUSTABLE_HOUR_STATUSES.has(attendanceStatus)) return attendanceStatus;
  return systemStatus;
}

function extractAdjustedLeaveHours(
  text: string,
  half: DayHalf,
  systemStatus: AttendanceStatus,
  finalStatus: AttendanceStatus,
  workSchedule: AttendanceWorkSchedule,
): number {
  return extractHoursForStatuses(text, half, [systemStatus, finalStatus], workSchedule) ?? fallbackHoursForHalf(half, workSchedule);
}

function hourLabelsForStatuses(...statuses: AttendanceStatus[]): string[] {
  const labels = new Set<string>();
  for (const status of statuses) {
    for (const label of STATUS_HOUR_LABELS[status] || []) labels.add(label);
  }
  return [...labels];
}

function extractHoursForStatuses(
  text: string,
  half: DayHalf,
  statuses: AttendanceStatus[],
  workSchedule: AttendanceWorkSchedule,
): number | null {
  const labels = hourLabelsForStatuses(...statuses);
  const entries = [
    ...extractStatusHourEntries(text, workSchedule).filter((entry) => labels.includes(entry.label)),
    ...(statuses.includes("旷工") ? extractAbsenceHourEntries(text, workSchedule) : []),
  ];
  return hoursForHalf(entries, half, workSchedule);
}

function extractStatusHourEntries(text: string, workSchedule: AttendanceWorkSchedule): HourEntry[] {
  const labelPattern = HOUR_LABELS.map(escapeRegExp).join("|");
  const pattern = new RegExp(`(${labelPattern})\\s*([0-9]+(?:\\.[0-9]+)?)\\s*小时\\s*(?:[（(](.*?)[）)])?`, "g");
  return [...text.matchAll(pattern)].map((match) => {
    const hours = Number(match[2]);
    const times = clockMinutesInText(match[3] || "");
    return {
      label: match[1],
      hours,
      half: leaveHalfFromRange(match[3] || "", hours, workSchedule),
      startMinutes: times[0],
      endMinutes: times.length >= 2 ? times[times.length - 1] : undefined,
    };
  });
}

function extractAbsenceHourEntries(text: string, workSchedule: AttendanceWorkSchedule): HourEntry[] {
  const entries: HourEntry[] = [];
  for (const match of text.matchAll(/旷工\s*([0-9]+(?:\.[0-9]+)?)\s*分钟/g)) {
    const hours = Number(match[1]) / 60;
    entries.push({ label: "旷工", hours, half: hours >= workSchedule.standardWorkHours ? "full-day" : "" });
  }
  for (const match of text.matchAll(/旷工\s*([0-9]+(?:\.[0-9]+)?)\s*小时\s*(?:[（(](.*?)[）)])?/g)) {
    const hours = Number(match[1]);
    entries.push({ label: "旷工", hours, half: leaveHalfFromRange(match[2] || "", hours, workSchedule) });
  }
  return entries;
}

function hoursForHalf(entries: HourEntry[], half: DayHalf, workSchedule: AttendanceWorkSchedule): number | null {
  const timedEntries = entries.filter((entry) => entry.startMinutes != null && entry.endMinutes != null && entry.endMinutes > entry.startMinutes);
  if (timedEntries.length === entries.length && timedEntries.length > 0) {
    const relevantEntries = timedEntries.filter((entry) => entry.half === half || entry.half === "full-day");
    // Keep source rounding for a single half (e.g. 1.7 hours for 100 minutes).
    if (relevantEntries.length === 1 && relevantEntries[0].half === half) {
      return Math.min(relevantEntries[0].hours, fallbackHoursForHalf(half, workSchedule));
    }
    return coveredWorkMinutes(relevantEntries, half, workSchedule) / 60;
  }
  const halfHours = sumHours(entries.filter((entry) => entry.half === half));
  if (halfHours > 0) return Math.min(halfHours, fallbackHoursForHalf(half, workSchedule));

  const fullDayHours = sumHours(entries.filter((entry) => entry.half === "full-day"));
  if (fullDayHours > 0) return Math.min(fullDayHours, fallbackHoursForHalf(half, workSchedule));

  const untimedHours = sumHours(entries.filter((entry) => entry.half === ""));
  if (untimedHours > 0) return untimedHours >= workSchedule.standardWorkHours ? fallbackHoursForHalf(half, workSchedule) : untimedHours;

  return null;
}

function leaveHalfFromRange(range: string, hours: number, workSchedule: AttendanceWorkSchedule): DayHalf | "full-day" | "" {
  if (hours >= workSchedule.standardWorkHours) return "full-day";
  const times = clockMinutesInText(range);
  if (times.length < 2) return "";
  return workHalfForRange(times[0], times[times.length - 1], hours, workSchedule);
}

function fallbackHoursForHalf(half: DayHalf, workSchedule: AttendanceWorkSchedule): number {
  return workSchedule.workHalves[half].hours;
}

function recalculateActualAttendanceDays(days: DayReview[], workSchedule: AttendanceWorkSchedule): number {
  return days.reduce((total, day) => total + actualAttendanceForDay(day, workSchedule), 0);
}

function actualAttendanceForDay(day: DayReview, workSchedule: AttendanceWorkSchedule): number {
  const attendedHours =
    attendanceHoursForHalf(day, "morning", actualAttendanceStatusForHalf(day, "morning"), workSchedule) +
    attendanceHoursForHalf(day, "afternoon", actualAttendanceStatusForHalf(day, "afternoon"), workSchedule);
  return attendedHours / workSchedule.standardWorkHours;
}

function actualAttendanceStatusForHalf(day: DayReview, half: DayHalf): AttendanceStatus {
  const finalStatus = half === "morning" ? day.finalMorning : day.finalAfternoon;
  const systemStatus = half === "morning" ? day.systemMorning : day.systemAfternoon;
  const attendanceStatus = half === "morning" ? day.attendanceMorning : day.attendanceAfternoon;

  if (finalStatus === systemStatus && systemStatus === "加班" && attendanceStatus) return attendanceStatus;
  if (finalStatus === systemStatus && ATTENDED_STATUSES.has(finalStatus) && attendanceStatus && ATTENDED_STATUSES.has(attendanceStatus)) {
    return attendanceStatus;
  }
  return finalStatus;
}

function attendanceHoursForHalf(day: DayReview, half: DayHalf, status: AttendanceStatus, workSchedule: AttendanceWorkSchedule): number {
  if (status === "事假") return workedHoursAroundPersonalLeave(day, half, workSchedule);
  if (!ATTENDED_STATUSES.has(status)) return 0;
  const finalStatus = half === "morning" ? day.finalMorning : day.finalAfternoon;
  const systemStatus = half === "morning" ? day.systemMorning : day.systemAfternoon;
  const workStatus = half === "morning" ? day.workMorning : day.workAfternoon;
  // Paid leave and normal work cover the same scheduled half once, even when
  // overtime has replaced the leave label and its immediate attendance basis.
  if (finalStatus === systemStatus && DURATION_STATUSES.has(status) && workStatus === "出勤") {
    return fallbackHoursForHalf(half, workSchedule);
  }
  if (status === "早退") {
    const earlyHours = sumMinutes(day.rawDetail, "早退", half, workSchedule) / 60;
    return Math.max(0, fallbackHoursForHalf(half, workSchedule) - earlyHours);
  }
  return statusHoursForHalf(day, half, status, workSchedule);
}

function workedHoursAroundPersonalLeave(
  day: DayReview,
  half: DayHalf,
  workSchedule: AttendanceWorkSchedule,
): number {
  const labels = hourLabelsForStatuses(...durationCandidateStatuses(day, half, "事假"));
  const entries = extractStatusHourEntries(day.rawDetail, workSchedule).filter((entry) => labels.includes(entry.label));
  const timedEntries = entries.filter(
    (entry): entry is HourEntry & { startMinutes: number; endMinutes: number } =>
      entry.startMinutes != null && entry.endMinutes != null,
  );
  if (!timedEntries.length) return 0;

  const attendanceStatus = half === "morning" ? day.attendanceMorning : day.attendanceAfternoon;
  const hasAttendanceBasis = Boolean(attendanceStatus && ATTENDED_STATUSES.has(attendanceStatus));
  const hasCrossHalfEntry = timedEntries.some((entry) => {
    return (
      overlapsWorkHalf(entry.startMinutes, entry.endMinutes, "morning", workSchedule) &&
      overlapsWorkHalf(entry.startMinutes, entry.endMinutes, "afternoon", workSchedule)
    );
  });
  if (!hasAttendanceBasis && !hasCrossHalfEntry) return 0;

  const leaveMinutes = coveredWorkMinutes(timedEntries, half, workSchedule);
  return Math.max(0, fallbackHoursForHalf(half, workSchedule) - leaveMinutes / 60);
}

function coveredWorkMinutes(entries: HourEntry[], half: DayHalf, workSchedule: AttendanceWorkSchedule): number {
  const start = workStartMinutes(half, workSchedule);
  const end = workEndMinutes(half, workSchedule);
  const intervals = entries
    .filter((entry) => entry.startMinutes != null && entry.endMinutes != null)
    .map((entry) => [Math.max(start, entry.startMinutes!), Math.min(end, entry.endMinutes!)] as const)
    .filter(([from, to]) => to > from)
    .sort((left, right) => left[0] - right[0]);
  let minutes = 0;
  let coveredUntil = start;
  for (const [from, to] of intervals) {
    minutes += Math.max(0, to - Math.max(from, coveredUntil));
    coveredUntil = Math.max(coveredUntil, to);
  }
  return minutes;
}

function addAdjustedExceptionStats(stats: ReturnType<typeof createStatsShape>, day: DayReview, workSchedule: AttendanceWorkSchedule): void {
  addAbsenceStats(stats, day, workSchedule);

  for (const half of ["morning", "afternoon"] as DayHalf[]) {
    const value = half === "morning" ? day.finalMorning : day.finalAfternoon;
    if (actualAttendanceStatusForHalf(day, half) === "缺卡") stats.missingCardCount += 1;
    else if (value === "病假") stats.sickLeaveDays += statusHoursForHalf(day, half, value, workSchedule) / workSchedule.standardWorkHours;
    else if (value === "工伤") stats.injuryLeaveDays += statusHoursForHalf(day, half, value, workSchedule) / workSchedule.standardWorkHours;
    else if (value === "丧假") stats.funeralLeaveDays += statusHoursForHalf(day, half, value, workSchedule) / workSchedule.standardWorkHours;
    else if (value === "婚假") stats.marriageLeaveDays += statusHoursForHalf(day, half, value, workSchedule) / workSchedule.standardWorkHours;
    else if (value === "产假") stats.maternityLeaveDays += statusHoursForHalf(day, half, value, workSchedule) / workSchedule.standardWorkHours;
  }

  if (day.finalMorning === "迟到") {
    stats.lateCount += 1;
    stats.lateMinutes += sumMinutes(day.rawDetail, "迟到", "morning", workSchedule);
  }
  if (day.finalAfternoon === "迟到") {
    stats.lateCount += 1;
    stats.lateMinutes += sumMinutes(day.rawDetail, "迟到", "afternoon", workSchedule);
  }
  if (day.finalAfternoon === "早退") {
    stats.earlyCount += 1;
    stats.earlyMinutes += sumMinutes(day.rawDetail, "早退", "afternoon", workSchedule);
  }
}

function addAbsenceStats(stats: ReturnType<typeof createStatsShape>, day: DayReview, workSchedule: AttendanceWorkSchedule): void {
  const absenceHalves = (["morning", "afternoon"] as DayHalf[]).filter((half) => {
    return (half === "morning" ? day.finalMorning : day.finalAfternoon) === "旷工";
  });
  if (!absenceHalves.length) return;

  const parsedHours = sumHours(extractAbsenceHourEntries(day.rawDetail, workSchedule));
  if (parsedHours > 0) {
    stats.absenceDays += parsedHours / workSchedule.standardWorkHours;
    return;
  }

  for (const half of absenceHalves) {
    stats.absenceDays += fallbackHoursForHalf(half, workSchedule) / workSchedule.standardWorkHours;
  }
}

function createStatsShape() {
  return {
    missingCardCount: 0,
    lateCount: 0,
    lateMinutes: 0,
    earlyCount: 0,
    earlyMinutes: 0,
    absenceDays: 0,
    sickLeaveDays: 0,
    injuryLeaveDays: 0,
    funeralLeaveDays: 0,
    marriageLeaveDays: 0,
    maternityLeaveDays: 0,
  };
}

function sumMinutes(text: string, label: "迟到" | "早退", half: DayHalf, workSchedule: AttendanceWorkSchedule): number {
  return [...text.matchAll(new RegExp(`${label}\\s*(\\d+)\\s*分钟(?:-\\s*(\\d{1,2}[:：]\\d{2}))?`, "g"))].reduce((sum, match) => {
    const rawMinutes = Number(match[1]);
    const eventMinutes = parseClockMinutes(match[2]);
    if (eventMinutes == null) return sum + rawMinutes;

    const eventHalf = workHalfForPunchMinute(eventMinutes, workSchedule);
    if (eventHalf !== half) return sum;

    const adjustedMinutes =
      label === "迟到" ? eventMinutes - workStartMinutes(eventHalf, workSchedule) : workEndMinutes(eventHalf, workSchedule) - eventMinutes;
    return sum + Math.max(0, adjustedMinutes);
  }, 0);
}

function clockMinutesInText(value: string): number[] {
  return [...value.matchAll(/\d{1,2}[:：]\d{2}/g)]
    .map((item) => parseClockMinutes(item[0]))
    .filter((item): item is number => item != null);
}

function statusHoursForHalf(day: DayReview, half: DayHalf, status: AttendanceStatus, workSchedule: AttendanceWorkSchedule): number {
  if (status === "出勤") return fallbackHoursForHalf(half, workSchedule);

  const parsedHours = DURATION_STATUSES.has(status)
    ? extractHoursForStatuses(day.rawDetail, half, durationCandidateStatuses(day, half, status), workSchedule)
    : extractHoursForStatuses(day.rawDetail, half, [status], workSchedule);
  return parsedHours ?? fallbackHoursForHalf(half, workSchedule);
}

function durationCandidateStatuses(day: DayReview, half: DayHalf, status: AttendanceStatus): AttendanceStatus[] {
  const candidates = new Set<AttendanceStatus>([status]);
  const systemStatus = half === "morning" ? day.systemMorning : day.systemAfternoon;
  const attendanceStatus = half === "morning" ? day.attendanceMorning : day.attendanceAfternoon;

  if (DURATION_STATUSES.has(status)) {
    if (DURATION_STATUSES.has(systemStatus)) candidates.add(systemStatus);
    if (attendanceStatus && DURATION_STATUSES.has(attendanceStatus)) candidates.add(attendanceStatus);
  }

  return [...candidates];
}

function sumHours(entries: HourEntry[]): number {
  return entries.reduce((sum, entry) => sum + entry.hours, 0);
}

function minutesText(minutes: number, count: number): string | null {
  if (!minutes && !count) return null;
  if (minutes > 0) return `${formatNumber(minutes)}分钟`;
  return `${formatNumber(count)}次`;
}

function latePunishment(count: number, minutes: number): string | null {
  if (!count && !minutes) return null;
  if (count < 3 && minutes <= 15) return null;
  const reasons: string[] = [];
  if (count >= 3) reasons.push(`迟到次数${formatNumber(count)}次≥3次`);
  if (minutes > 15) reasons.push(`总时长${formatNumber(minutes)}分钟>15分钟`);
  return `是：${reasons.join("；")}`;
}

function toNumber(value: unknown): number | null {
  if (value == null || value === "" || value === "--") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function firstSourceSummary(sourceSummaries: Record<string, unknown>, fields: string[]): unknown {
  for (const field of fields) {
    const value = sourceSummaries[field];
    if (value != null && value !== "" && value !== "--") return value;
  }
  return null;
}

function blankZero(value: number, fractionDigits = 2): number | null {
  const rounded = Number.isInteger(value) ? value : Number(value.toFixed(fractionDigits));
  return rounded === 0 ? null : rounded;
}

function formatNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : String(value);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
