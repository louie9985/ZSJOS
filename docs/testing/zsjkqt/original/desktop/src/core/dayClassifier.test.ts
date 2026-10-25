import { describe, expect, it } from "vitest";
import { classifyDay, reclassifyDaysForSchedule } from "./dayClassifier";
import { attendanceWorkSchedule } from "./departmentSchedules";
import type { DayReview } from "./models";

describe("classifyDay", () => {
  const departmentSchedule = attendanceWorkSchedule({
    morning: { start: "09:00", end: "12:00" },
    afternoon: { start: "13:00", end: "18:00" },
  });

  it("preserves normal work through cross-half comp leave and an overtime overlay", () => {
    const { result } = classifyDay("正常- 13:46; 21:30\n调休假4.0小时（9/21 09:00 - 9/21 14:00）\n加班1.5小时（9/21 20:00 - 9/21 21:30）", "", 21, false, departmentSchedule);
    expect(result).toMatchObject({ morning: "调休", afternoon: "加班", workMorning: "出勤", workAfternoon: "出勤", attendanceAfternoon: "调休" });
  });

  it("uses department boundaries for leave and late punches", () => {
    const { result } = classifyDay("正常- 18:30\n迟到10分钟-13:10;\n调休假0.5小时（9/2 13:00 - 9/2 13:30）", "", 2, false, departmentSchedule);
    expect(result.morning).toBe("出勤");
    expect(result.afternoon).toBe("调休");
    expect(result.workAfternoon).toBe("迟到");
  });

  it("does not turn leave outside department work hours into full-day leave", () => {
    const { result } = classifyDay("正常- 09:00;18:00\n调休假1.0小时（9/2 12:00 - 9/2 13:00）", "", 2, false, departmentSchedule);
    expect([result.morning, result.afternoon]).toEqual(["出勤", "出勤"]);
  });

  it("reclassifies untouched halves after schedule changes while preserving edits and notes", () => {
    const rawDetail = "正常- 18:30\n迟到10分钟-13:10;";
    const { result } = classifyDay(rawDetail);
    const original: DayReview = {
      day: 2, rawDetail, systemMorning: result.morning, systemAfternoon: result.afternoon,
      finalMorning: result.morning, finalAfternoon: "事假", morningNote: "上午备注", afternoonNote: "人工确认",
      issues: [], modified: true,
    };
    const [next] = reclassifyDaysForSchedule([original], departmentSchedule, "测试员工");
    expect(next).toMatchObject({ systemMorning: "出勤", systemAfternoon: "迟到", finalMorning: "出勤", finalAfternoon: "事假", morningNote: "上午备注", afternoonNote: "人工确认", modified: true });
  });
  it("maps source rest text to selected normal rest status", () => {
    const { result, issues } = classifyDay("正常（休息）", "", null, true);
    expect(result.morning).toBe("休息");
    expect(result.afternoon).toBe("休息");
    expect(issues).toEqual([]);
  });

  it("maps source rest text to selected paid rest status", () => {
    const { result, issues } = classifyDay("正常（休息）", "", null, "带薪");
    expect(result.morning).toBe("带薪");
    expect(result.afternoon).toBe("带薪");
    expect(issues).toEqual([]);
  });

  it("maps source rest text to normal rest status when the day is not selected", () => {
    const { result, issues } = classifyDay("正常（休息）");
    expect(result.morning).toBe("休息");
    expect(result.afternoon).toBe("休息");
    expect(issues).toEqual([]);
  });

  it("keeps a selected rest day without source data blank", () => {
    const { result } = classifyDay("", "", null, true);
    expect(result.morning).toBe("");
    expect(result.afternoon).toBe("");
  });

  it("treats source dash placeholders as missing source data", () => {
    const { result, issues } = classifyDay("--", "", null, true);
    expect(result.morning).toBe("");
    expect(result.afternoon).toBe("");
    expect(issues).toEqual([]);
  });

  it("keeps a selected paid rest day without source data blank", () => {
    const { result } = classifyDay("", "", null, "带薪");
    expect(result.morning).toBe("");
    expect(result.afternoon).toBe("");
  });

  it("lets selected rest day override source attendance", () => {
    const { result } = classifyDay("正常- 08:21; 18:03", "", null, "休息");
    expect(result.morning).toBe("休息");
    expect(result.afternoon).toBe("休息");
  });

  it("lets selected paid rest day override absence and missing card", () => {
    const absence = classifyDay("旷工480分钟;", "", null, "带薪").result;
    const missingCard = classifyDay("缺卡(08:30)", "", null, "带薪").result;

    expect(absence.morning).toBe("带薪");
    expect(absence.afternoon).toBe("带薪");
    expect(missingCard.morning).toBe("带薪");
    expect(missingCard.afternoon).toBe("带薪");
  });

  it("lets selected rest day override leave and late states", () => {
    const leave = classifyDay("正常(请假)- 08:21; 14:00 \n 事假4.0小时（5/7 14:00 - 5/7 18:00）", "", null, "休息").result;
    const late = classifyDay("正常- 08:31; 18:03\n迟到1分钟-08:31;", "", null, "休息").result;

    expect(leave.morning).toBe("休息");
    expect(leave.afternoon).toBe("休息");
    expect(late.morning).toBe("休息");
    expect(late.afternoon).toBe("休息");
  });

  it("keeps overtime above selected rest day", () => {
    const { result } = classifyDay("正常- 08:23; 20:42 \n 加班2.5小时（5/7 18:00 - 5/7 20:30）", "", null, "休息");
    expect(result.morning).toBe("休息");
    expect(result.afternoon).toBe("加班");
  });

  it("keeps abnormal status above selected rest day", () => {
    const { result, issues } = classifyDay("正常- 08:20; 18:00 \n wifi异常-08:20;", "", null, "带薪");
    expect(result.morning).toBe("异常");
    expect(result.afternoon).toBe("带薪");
    expect(issues).toHaveLength(1);
  });

  it("maps normal attendance to both halves", () => {
    const { result } = classifyDay("正常- 08:21; 18:03");
    expect(result.morning).toBe("出勤");
    expect(result.afternoon).toBe("出勤");
  });

  it("splits late and early leave into morning and afternoon", () => {
    const { result } = classifyDay("正常- 08:31; 15:22\n迟到1分钟-08:31; 早退158分钟-15:22;");
    expect(result.morning).toBe("迟到");
    expect(result.afternoon).toBe("早退");
  });

  it("uses configured work periods to split morning leave and afternoon late arrival", () => {
    const { result } = classifyDay("正常- 21:33 \n 迟到302分钟-13:32; \n 事假3.5小时（5/18 08:30 - 5/18 12:00）");

    expect(result.morning).toBe("事假");
    expect(result.afternoon).toBe("迟到");
  });

  it("recognizes full-width colons in cross-half leave times", () => {
    const { result } = classifyDay("事假5.5小时（8/16 11：00 - 8/16 18：00）");

    expect(result.morning).toBe("事假");
    expect(result.afternoon).toBe("事假");
  });

  it("maps injury leave aliases to the configured injury status", () => {
    const { result } = classifyDay("工伤假3.5小时（5/20 08:30 - 5/20 12:00）");

    expect(result.morning).toBe("工伤");
    expect(result.afternoon).toBe("");
  });

  it("keeps the base afternoon attendance when overtime covers the displayed status", () => {
    const { result } = classifyDay("正常- 08:59; 次日00:16 \n 加班6.0小时（5/22 18:00 - 5/23 00:00）");

    expect(result.morning).toBe("出勤");
    expect(result.afternoon).toBe("加班");
    expect(result.attendanceAfternoon).toBe("出勤");
  });

  it("keeps multi-segment leave as the overtime attendance basis before display override", () => {
    const { result } = classifyDay(
      "正常- 次日01:04 \n 迟到113分钟-10:58; \n 事假1.7小时（5/26 09:00 - 5/26 10:40） \n 事假1.9小时（5/26 13:00 - 5/26 14:50） \n 加班7.0小时（5/26 18:00 - 5/27 01:00）",
    );

    expect(result.morning).toBe("事假");
    expect(result.afternoon).toBe("加班");
    expect(result.attendanceAfternoon).toBe("事假");
  });

  it("keeps normal attendance as the basis when short comp leave overlays a normal day", () => {
    const { result } = classifyDay(
      "正常- 09:16; 22:50 \n 调休假0.5小时（6/16 09:00 - 6/16 09:30） \n 加班2.5小时（6/16 20:00 - 6/16 22:30）",
    );

    expect(result.morning).toBe("调休");
    expect(result.attendanceMorning).toBe("出勤");
    expect(result.afternoon).toBe("加班");
    expect(result.attendanceAfternoon).toBe("出勤");
  });

  it("maps pure absence to both halves", () => {
    const { result, issues } = classifyDay("旷工480分钟;");
    expect(result.morning).toBe("旷工");
    expect(result.afternoon).toBe("旷工");
    expect(issues).toEqual([]);
  });

  it("marks unrecognized daily details as abnormal", () => {
    const { result, issues } = classifyDay("未知明细");
    expect(result.morning).toBe("异常");
    expect(result.afternoon).toBe("异常");
    expect(issues).toHaveLength(1);
  });

  it("marks unclear missing-card time as abnormal", () => {
    const { result, issues } = classifyDay("缺卡(未知时间)");
    expect(result.morning).toBe("异常");
    expect(result.afternoon).toBe("异常");
    expect(issues).toHaveLength(1);
  });

  it("lets outing override wifi anomaly and early leave", () => {
    const { result, issues } = classifyDay("正常(外出)- 07:30 \n 早退199分钟,wifi异常-14:41; \n 外出7.0小时（5/9 07:30 - 5/9 14:30）");
    expect(result.morning).toBe("出勤");
    expect(result.afternoon).toBe("出勤");
    expect(issues).toEqual([]);
  });

  it("turns unapproved wifi anomaly into manual-confirmation issue", () => {
    const { result, issues } = classifyDay("正常- 08:20; 18:00 \n wifi异常-08:20;");
    expect(result.morning).toBe("异常");
    expect(result.afternoon).toBe("出勤");
    expect(issues).toHaveLength(1);
  });
});
