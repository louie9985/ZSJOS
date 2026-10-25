import { describe, expect, it } from "vitest";
import { attendanceWorkSchedule } from "./departmentSchedules";
import { DayReview } from "./models";
import { recalculateSummary } from "./summary";

function day(patch: Partial<DayReview>): DayReview {
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

describe("recalculateSummary", () => {
  const departmentSchedule = attendanceWorkSchedule({
    morning: { start: "09:00", end: "12:00" },
    afternoon: { start: "13:00", end: "18:00" },
  });

  function summarizeDays(days: DayReview[]) {
    return recalculateSummary({ sourceSummaries: {}, days, shouldAttendanceDays: null, payrollDays: null, entryDate: "", workSchedule: departmentSchedule });
  }

  it("allocates cross-half comp leave by work overlap without inventing remaining attendance", () => {
    const input = day({ rawDetail: "调休假4.0小时（9/22 09:00 - 9/22 14:00）", systemMorning: "调休", systemAfternoon: "调休", finalMorning: "调休", finalAfternoon: "调休" });
    expect(summarizeDays([{ ...input, finalAfternoon: "缺卡" }]).actualAttendanceDays).toBe(0.38);
    expect(summarizeDays([{ ...input, finalMorning: "缺卡" }]).actualAttendanceDays).toBe(0.13);
    expect(summarizeDays([input]).actualAttendanceDays).toBe(0.5);
  });

  it("does not double-count duplicate or overlapping comp leave intervals", () => {
    const input = day({ rawDetail: "调休假4.0小时（9/22 09:00 - 9/22 14:00）\n调休假4.0小时（9/22 09:00 - 9/22 14:00）\n调休假2.0小时（9/22 13:00 - 9/22 15:00）", systemMorning: "调休", systemAfternoon: "调休", finalMorning: "调休", finalAfternoon: "调休" });
    expect(summarizeDays([input]).actualAttendanceDays).toBe(0.63);
  });

  it("does not count overtime alone or use overnight overtime to repair missing punches", () => {
    expect(summarizeDays([day({ rawDetail: "加班13.0小时（9/14 20:00 - 9/15 09:00）", systemAfternoon: "加班", finalAfternoon: "加班" })]).actualAttendanceDays).toBeNull();
    const input = day({ rawDetail: "缺卡(18:00); 加班13.0小时（9/14 20:00 - 9/15 09:00）", systemMorning: "出勤", systemAfternoon: "加班", finalMorning: "出勤", finalAfternoon: "加班", attendanceAfternoon: "缺卡" });
    expect(summarizeDays([input])).toMatchObject({ actualAttendanceDays: 0.38, missingCardCount: 1 });
    expect(summarizeDays([{ ...input, finalAfternoon: "出勤", modified: true }])).toMatchObject({ actualAttendanceDays: 1, missingCardCount: null });
  });

  it("unions overlapping personal leave before subtracting work hours", () => {
    const input = day({ rawDetail: "事假2.0小时（9/22 13:00 - 9/22 15:00）\n事假2.0小时（9/22 14:00 - 9/22 16:00）", systemMorning: "出勤", finalMorning: "出勤", systemAfternoon: "事假", finalAfternoon: "事假", attendanceAfternoon: "出勤" });
    expect(summarizeDays([input]).actualAttendanceDays).toBe(0.63);
  });

  it("counts full-day comp leave once with or without a normal work basis", () => {
    const input = day({ rawDetail: "调休假8.0小时（9/24 09:00 - 9/24 18:00）", systemMorning: "调休", finalMorning: "调休", systemAfternoon: "调休", finalAfternoon: "调休" });
    expect(summarizeDays([input]).actualAttendanceDays).toBe(1);
    expect(summarizeDays([{ ...input, workMorning: "出勤", workAfternoon: "出勤" }]).actualAttendanceDays).toBe(1);
  });
  it("uses final daily statuses for missing card and keeps personal leave hours from source", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 1 },
      days: [day({ rawDetail: "事假4.0小时", finalMorning: "缺卡", finalAfternoon: "事假" })],
      shouldAttendanceDays: 21,
      payrollDays: 23,
      entryDate: "",
    });

    expect(summary.missingCardCount).toBe(1);
    expect(summary.personalLeaveHours).toBe(1);
  });

  it("keeps source-backed hour fields independent from final daily statuses", () => {
    const summary = recalculateSummary({
      sourceSummaries: {
        "事假(小时)": 1,
        "调休假(小时)": 2,
        "年假(小时)": 3,
        "加班时长(小时)": 4,
      },
      days: [day({ finalMorning: "调休", finalAfternoon: "加班" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.personalLeaveHours).toBe(1);
    expect(summary.compLeaveHours).toBe(2);
    expect(summary.annualLeaveHours).toBe(3);
    expect(summary.overtimeHours).toBe(4);
  });

  it("reads comp leave hours directly from source summary aliases", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "调休(小时)": 6 },
      days: [day({ finalMorning: "出勤", finalAfternoon: "调休" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.compLeaveHours).toBe(6);
  });

  it("recalculates actual attendance from final daily statuses", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "实际出勤天数(天)": 99 },
      days: [
        day({ finalMorning: "出勤", finalAfternoon: "出勤" }),
        day({ finalMorning: "迟到", finalAfternoon: "早退" }),
        day({ finalMorning: "缺卡", finalAfternoon: "事假" }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(2);
  });

  it("deducts early-leave minutes from actual attendance", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "迟到90分钟-10:00; 早退60分钟-17:00",
          finalMorning: "迟到",
          finalAfternoon: "早退",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(0.88);
    expect(summary.lateText).toBe("90分钟");
    expect(summary.earlyText).toBe("60分钟");
  });

  it("counts normal attendance with approved outing as a full attendance day", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "正常- 08:24; 18:36 \n 外出3.0小时（5/22 13:30 - 5/22 16:30）",
          finalMorning: "出勤",
          finalAfternoon: "出勤",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(1);
  });

  it("counts configured leave statuses as actual attendance", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({ finalMorning: "病假", finalAfternoon: "工伤" }),
        day({ finalMorning: "丧假", finalAfternoon: "婚假" }),
        day({ finalMorning: "产假", finalAfternoon: "年假" }),
        day({ finalMorning: "调休", finalAfternoon: "事假" }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(3.44);
  });

  it("counts comp leave over normal attendance by the underlying attendance basis", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "正常- 09:16; 22:50 \n 调休假0.5小时（6/16 09:00 - 6/16 09:30） \n 加班2.5小时（6/16 20:00 - 6/16 22:30）",
          finalMorning: "调休",
          finalAfternoon: "加班",
          systemMorning: "调休",
          systemAfternoon: "加班",
          attendanceMorning: "出勤",
          attendanceAfternoon: "出勤",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(1);
  });

  it("does not let normal attendance override personal leave for actual attendance", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "正常(请假)- 08:20; \n 事假6.0小时（6/25 12:00 - 6/25 18:00）",
          systemMorning: "出勤",
          systemAfternoon: "事假",
          finalMorning: "出勤",
          finalAfternoon: "事假",
          attendanceAfternoon: "出勤",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(0.44);
  });

  it("deducts a cross-half personal leave by its exact work-time overlap", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 5.5 },
      days: [
        day({
          rawDetail: "事假5.5小时（8/16 11：00 - 8/16 18：00）",
          systemMorning: "事假",
          systemAfternoon: "事假",
          finalMorning: "事假",
          finalAfternoon: "事假",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(0.31);
    expect(summary.personalLeaveHours).toBe(5.5);
  });

  it("counts changed comp leave as actual attendance by original parsed duration", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "事假3.0小时（5/7 14:00 - 5/7 17:00）",
          systemAfternoon: "事假",
          finalMorning: "出勤",
          finalAfternoon: "调休",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(0.81);
  });

  it("falls back to configured half hours for actual attendance when duration cannot be parsed", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "调休", finalMorning: "出勤", finalAfternoon: "调休" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(1);
  });

  it("rounds configured afternoon attendance with morning personal leave to two decimals", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "正常- 21:33 \n 迟到302分钟-13:32; \n 事假3.5小时（5/18 08:30 - 5/18 12:00）",
          finalMorning: "事假",
          finalAfternoon: "迟到",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(0.56);
    expect(summary.lateText).toBe("2分钟");
  });

  it("moves changed morning personal leave to comp leave and counts a full actual attendance day", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 3.5 },
      days: [
        day({
          rawDetail: "正常- 21:33 \n 迟到302分钟-13:32; \n 事假3.5小时（5/18 08:30 - 5/18 12:00）",
          systemMorning: "事假",
          systemAfternoon: "迟到",
          finalMorning: "调休",
          finalAfternoon: "迟到",
          modified: true,
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(1);
    expect(summary.personalLeaveHours).toBeNull();
    expect(summary.compLeaveHours).toBe(3.5);
    expect(summary.lateText).toBe("2分钟");
  });

  it("uses covered base attendance when overtime is the displayed afternoon status", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "正常- 08:59; 次日00:16 \n 加班6.0小时（5/22 18:00 - 5/23 00:00）",
          systemMorning: "出勤",
          systemAfternoon: "加班",
          finalMorning: "出勤",
          finalAfternoon: "加班",
          attendanceAfternoon: "出勤",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(1);
  });

  it("does not count overtime as attendance when it covered a leave half", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail:
            "正常- 次日01:04 \n 迟到113分钟-10:58; \n 事假1.7小时（5/26 09:00 - 5/26 10:40） \n 事假1.9小时（5/26 13:00 - 5/26 14:50） \n 加班7.0小时（5/26 18:00 - 5/27 01:00）",
          systemMorning: "事假",
          systemAfternoon: "加班",
          finalMorning: "事假",
          finalAfternoon: "加班",
          attendanceAfternoon: "事假",
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBeNull();
  });

  it("moves visible and overtime-covered personal leave to comp leave when changed to comp leave", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 3.6 },
      days: [
        day({
          rawDetail:
            "正常- 次日01:04 \n 迟到113分钟-10:58; \n 事假1.7小时（5/26 09:00 - 5/26 10:40） \n 事假1.9小时（5/26 13:00 - 5/26 14:50） \n 加班7.0小时（5/26 18:00 - 5/27 01:00）",
          systemMorning: "事假",
          systemAfternoon: "加班",
          finalMorning: "调休",
          finalAfternoon: "调休",
          attendanceAfternoon: "事假",
          modified: true,
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.actualAttendanceDays).toBe(0.45);
    expect(summary.personalLeaveHours).toBeNull();
    expect(summary.compLeaveHours).toBe(3.6);
  });

  it("does not move leave hours when a non-leave status is changed to comp leave", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({
          rawDetail: "正常- 08:30; 18:00",
          systemMorning: "出勤",
          systemAfternoon: "出勤",
          finalMorning: "调休",
          finalAfternoon: "出勤",
          modified: true,
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.personalLeaveHours).toBeNull();
    expect(summary.compLeaveHours).toBeNull();
  });

  it("moves parsed personal leave hours to comp leave after user changes the status", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 3, "调休假(小时)": 2 },
      days: [
        day({
          rawDetail: "事假3.0小时（5/7 14:00 - 5/7 17:00）",
          systemMorning: "出勤",
          systemAfternoon: "事假",
          finalMorning: "出勤",
          finalAfternoon: "调休",
          modified: true,
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.personalLeaveHours).toBeNull();
    expect(summary.compLeaveHours).toBe(5);
  });

  it("falls back to 3.5 morning hours and 4.5 afternoon hours when changed leave hours cannot be parsed", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 8 },
      days: [
        day({
          rawDetail: "事假",
          systemMorning: "事假",
          systemAfternoon: "事假",
          finalMorning: "调休",
          finalAfternoon: "调休",
          modified: true,
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.personalLeaveHours).toBeNull();
    expect(summary.compLeaveHours).toBe(8);
  });

  it("uses department half hours when changed leave hours cannot be parsed", () => {
    const summary = recalculateSummary({
      sourceSummaries: { "事假(小时)": 7 },
      days: [
        day({
          rawDetail: "事假",
          systemMorning: "事假",
          systemAfternoon: "事假",
          finalMorning: "调休",
          finalAfternoon: "调休",
          modified: true,
        }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
      workSchedule: attendanceWorkSchedule({
        morning: { start: "09:00", end: "12:00" },
        afternoon: { start: "13:30", end: "17:30" },
      }),
    });

    expect(summary.personalLeaveHours).toBeNull();
    expect(summary.compLeaveHours).toBe(7);
  });

  it("uses final daily statuses for absence", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "旷工480分钟;", finalMorning: "旷工", finalAfternoon: "旷工" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.absenceDays).toBe(1);
  });

  it("calculates explicit absence minutes once against department standard hours", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "旷工480分钟;", finalMorning: "旷工", finalAfternoon: "旷工" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
      workSchedule: attendanceWorkSchedule({
        morning: { start: "11:00", end: "12:00" },
        afternoon: { start: "13:30", end: "21:30" },
      }),
    });

    expect(summary.absenceDays).toBe(0.89);
  });

  it("calculates absence days from parsed duration before falling back to half-day defaults", () => {
    const parsed = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "旷工60分钟;", finalMorning: "旷工" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });
    const fallback = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "旷工", finalAfternoon: "旷工" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(parsed.absenceDays).toBe(0.13);
    expect(fallback.absenceDays).toBe(0.56);
  });

  it("uses department standard hours when falling back absence days", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "旷工", finalMorning: "旷工" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
      workSchedule: attendanceWorkSchedule({
        morning: { start: "09:00", end: "12:00" },
        afternoon: { start: "13:30", end: "17:30" },
      }),
    });

    expect(summary.absenceDays).toBe(0.43);
  });

  it("calculates leave day summaries from parsed duration", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "病假2.0小时（5/8 09:00 - 5/8 11:00）", finalMorning: "病假" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.sickLeaveDays).toBe(0.25);
  });

  it("does not count raw absence after the final calendar status removes it", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [day({ rawDetail: "旷工480分钟;", finalMorning: "休息", finalAfternoon: "休息" })],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.absenceDays).toBeNull();
  });

  it("keeps late punishment blank under threshold", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({ rawDetail: "迟到5分钟-08:35", finalMorning: "迟到" }),
        day({ rawDetail: "迟到10分钟-08:40", finalMorning: "迟到" }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.lateText).toBe("15分钟");
    expect(summary.latePunishment).toBeNull();
  });

  it("marks late punishment by count", () => {
    const summary = recalculateSummary({
      sourceSummaries: {},
      days: [
        day({ rawDetail: "迟到1分钟", finalMorning: "迟到" }),
        day({ rawDetail: "迟到1分钟", finalMorning: "迟到" }),
        day({ rawDetail: "迟到1分钟", finalMorning: "迟到" }),
      ],
      shouldAttendanceDays: null,
      payrollDays: null,
      entryDate: "",
    });

    expect(summary.latePunishment).toBe("是：迟到次数3次≥3次");
  });
});
