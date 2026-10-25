import { describe, expect, it } from "vitest";
import { attendanceWorkSchedule, DEFAULT_WORK_SCHEDULE } from "./departmentSchedules";
import { DayReview, EmployeeReview } from "./models";
import { dayHasManualReviewReminder, dayHasReviewIssue, employeeReviewIssueCount } from "./reviewIssues";
import { SOURCE_SUMMARY_REVIEW_MESSAGE } from "./sourceSummaryReview";

describe("review issue display rules", () => {
  it("does not treat blank manual changes to normal statuses as review issues", () => {
    const day = makeDay({
      systemMorning: "",
      systemAfternoon: "",
      finalMorning: "出勤",
      finalAfternoon: "事假",
    });

    expect(dayHasReviewIssue(day)).toBe(false);
  });

  it("does not show blank statuses as review issues even when system issues exist", () => {
    const day = makeDay({
      issues: [{ level: "warning", employee: "张三", day: 1, message: "无法识别每日明细", rawValue: "未知明细" }],
    });

    expect(dayHasReviewIssue(day)).toBe(false);
  });

  it("shows system issues when the system status is abnormal", () => {
    const day = makeDay({
      systemMorning: "异常",
      finalMorning: "异常",
      issues: [{ level: "warning", employee: "张三", day: 1, message: "打卡异常", rawValue: "wifi异常-08:20" }],
    });

    expect(dayHasReviewIssue(day)).toBe(true);
  });

  it("does not keep resolved system abnormalities as review issues", () => {
    const day = makeDay({
      systemMorning: "异常",
      systemAfternoon: "出勤",
      finalMorning: "出勤",
      finalAfternoon: "出勤",
      issues: [{ level: "warning", employee: "张三", day: 1, message: "打卡异常", rawValue: "wifi异常-08:20" }],
    });

    expect(dayHasReviewIssue(day)).toBe(false);
  });

  it("shows manual abnormal status changes as review issues", () => {
    const day = makeDay({
      systemMorning: "出勤",
      systemAfternoon: "出勤",
      finalMorning: "异常",
      finalAfternoon: "出勤",
    });

    expect(dayHasReviewIssue(day)).toBe(true);
  });

  it("tracks source-backed duration reminders separately from review issues", () => {
    const day = makeDay({
      systemMorning: "出勤",
      finalMorning: "调休",
      issues: [{ level: "info", employee: "张三", day: 1, message: SOURCE_SUMMARY_REVIEW_MESSAGE, rawValue: "" }],
    });

    expect(dayHasReviewIssue(day)).toBe(false);
    expect(dayHasManualReviewReminder(day)).toBe(true);
  });

  it("tracks any user-modified day as a manual review reminder", () => {
    const day = makeDay({
      systemMorning: "出勤",
      finalMorning: "迟到",
      modified: true,
    });

    expect(dayHasManualReviewReminder(day)).toBe(true);
    expect(dayHasReviewIssue(day)).toBe(false);
  });

  it("counts employee review issues with the same display rule", () => {
    const employee = makeEmployee([
      makeDay({ finalMorning: "出勤" }),
      makeDay({
        systemMorning: "出勤",
        finalMorning: "调休",
        issues: [{ level: "info", employee: "李四", day: 2, message: SOURCE_SUMMARY_REVIEW_MESSAGE, rawValue: "" }],
      }),
      makeDay({ systemMorning: "出勤", finalMorning: "异常" }),
      makeDay({
        systemMorning: "异常",
        finalMorning: "出勤",
        issues: [{ level: "warning", employee: "李四", day: 3, message: "打卡异常", rawValue: "wifi异常-08:20" }],
      }),
    ]);

    expect(employeeReviewIssueCount(employee)).toBe(1);
  });
});

function makeDay(patch: Partial<DayReview> = {}): DayReview {
  return {
    day: 1,
    rawDetail: "",
    systemMorning: "",
    systemAfternoon: "",
    finalMorning: "",
    finalAfternoon: "",
    morningNote: "",
    afternoonNote: "",
    issues: [],
    modified: false,
    ...patch,
  };
}

function makeEmployee(days: DayReview[]): EmployeeReview {
  return {
    key: "employee",
    sourceName: "员工",
    displayName: "员工",
    sourceDepartment: "",
    displayDepartment: "",
    departmentSchedule: {
      department: "默认班次",
      source: "default",
      mode: "default",
      schedule: attendanceWorkSchedule(DEFAULT_WORK_SCHEDULE),
    },
    entryDate: "",
    enabled: true,
    confirmed: false,
    sourceIndex: 1,
    sourceSummaries: {},
    days,
    summary: {
      shouldAttendanceDays: null,
      actualAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
      missingCardCount: null,
      lateText: null,
      latePunishment: null,
      earlyText: null,
      absenceDays: null,
      personalLeaveHours: null,
      sickLeaveDays: null,
      injuryLeaveDays: null,
      funeralLeaveDays: null,
      marriageLeaveDays: null,
      maternityLeaveDays: null,
      compLeaveHours: null,
      annualLeaveHours: null,
      overtimeHours: null,
      overtimeToSalary: "",
      remark: "",
    },
    manualSummaryOverrides: {},
    issues: [],
    notes: "",
  };
}
