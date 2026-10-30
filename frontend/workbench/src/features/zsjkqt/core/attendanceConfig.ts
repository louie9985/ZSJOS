import type { AttendanceStatus } from "./models";

export type DayHalf = "morning" | "afternoon";
export type RangeHalf = DayHalf | "full-day" | "";

export const ATTENDANCE_CONFIG = {
  standardWorkHours: 8,
  actualAttendanceFractionDigits: 2,
  workHalves: {
    morning: { start: "08:30", end: "12:00", hours: 3.5 },
    afternoon: { start: "13:30", end: "18:00", hours: 4.5 },
  },
  actualAttendanceStatuses: ["出勤", "迟到", "早退", "病假", "工伤", "丧假", "婚假", "产假", "产检假", "调休", "年假"] as AttendanceStatus[],
  overtime: {
    displayOverridesWorkStatus: true,
    countAsActualAttendanceWhenBaseWorked: true,
    countRestDayOvertimeAsActualAttendance: false,
  },
} as const;

export function workHoursForHalf(half: DayHalf): number {
  return ATTENDANCE_CONFIG.workHalves[half].hours;
}

export function workStartMinutes(half: DayHalf): number {
  return parseClockMinutes(ATTENDANCE_CONFIG.workHalves[half].start) ?? 0;
}

export function workEndMinutes(half: DayHalf): number {
  return parseClockMinutes(ATTENDANCE_CONFIG.workHalves[half].end) ?? 0;
}

export function parseClockMinutes(value: string | undefined): number | null {
  if (!value) return null;
  const match = value.match(/(\d{1,2})[:：](\d{2})/);
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

export function workHalfForPunchMinute(minutes: number): DayHalf {
  return minutes >= workStartMinutes("afternoon") ? "afternoon" : "morning";
}

export function workHalfForRange(startMinutes: number, endMinutes: number, hours: number): RangeHalf {
  if (hours >= ATTENDANCE_CONFIG.standardWorkHours) return "full-day";

  const coversMorning = overlapsWorkHalf(startMinutes, endMinutes, "morning");
  const coversAfternoon = overlapsWorkHalf(startMinutes, endMinutes, "afternoon");

  if (coversMorning && coversAfternoon) return "full-day";
  if (coversMorning) return "morning";
  if (coversAfternoon) return "afternoon";
  return "";
}

function overlapsWorkHalf(startMinutes: number, endMinutes: number, half: DayHalf): boolean {
  return startMinutes < workEndMinutes(half) && endMinutes > workStartMinutes(half);
}
