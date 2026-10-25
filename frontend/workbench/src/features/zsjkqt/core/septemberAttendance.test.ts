import { describe, expect, it } from "vitest";
import fixture from "./fixtures/september2026.json";
import { attendanceWorkSchedule, type DepartmentScheduleSettings } from "./departmentSchedules";
import { buildEmployeeReview } from "./reviewBuilder";
import { recalculateSummary } from "./summary";
import type { AttendanceMonth, DayReview, SourceEmployee } from "./models";

const schedule = { morning: { start: "09:00", end: "12:00" }, afternoon: { start: "13:00", end: "18:00" } };
const settings: DepartmentScheduleSettings = { default: schedule, departments: {} };
const source: SourceEmployee = fixture.employee;
const month: AttendanceMonth = { year: 2026, month: 9, startDate: "2026-09-01", endDate: "2026-09-30", employees: [source] };
const options = { restDays: [5, 12, 13, 19, 26, 27], paidRestDays: [25], shouldAttendanceDays: 23, payrollDays: 24 };

function review() {
  return buildEmployeeReview(month, source, 0, options, {}, settings);
}

function summary(days: DayReview[]) {
  return recalculateSummary({ sourceSummaries: source.summaries, days, ...options, entryDate: "", workSchedule: attendanceWorkSchedule(schedule) });
}

describe("September 2026 source workbook regression", () => {
  it.each([21, 22])("counts day %i cross-half comp leave with normal attendance as one day", (number) => {
    const item = review().days.find((day) => day.day === number)!;
    expect(summary([item]).actualAttendanceDays).toBe(1);
  });

  it.each([
    [false, false, 22, 2],
    [false, true, 22.38, 1],
    [true, false, 22.63, 1],
    [true, true, 23, null],
  ] as const)("reconciles the whole month with day 14 fixed=%s and day 15 fixed=%s", (fix14, fix15, expected, missing) => {
    const days = review().days.map((day) => {
      if (day.day === 14 && fix14) return { ...day, finalAfternoon: "出勤" as const, modified: true };
      if (day.day === 15 && fix15) return { ...day, finalMorning: "出勤" as const, modified: true };
      return day;
    });
    const result = summary(days);
    expect(result.actualAttendanceDays).toBe(expected);
    expect(result.missingCardCount).toBe(missing);
    expect(result.compLeaveHours).toBe(30.5);
    expect(result.overtimeHours).toBe(27.5);
    expect(result.shouldAttendanceDays).toBe(23);
    expect(result.payrollDays).toBe(24);
  });
});
