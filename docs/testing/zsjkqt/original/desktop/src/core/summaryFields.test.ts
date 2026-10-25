import { describe, expect, it } from "vitest";
import { SUMMARY_FIELD_DEFINITIONS } from "./summaryFields";

describe("SUMMARY_FIELD_DEFINITIONS", () => {
  it("matches the exported template summary columns", () => {
    expect(SUMMARY_FIELD_DEFINITIONS.map((field) => field.label)).toEqual([
      "应出勤日",
      "实出勤日",
      "计薪日",
      "入职日期",
      "缺卡",
      "迟到",
      "迟到是否惩罚",
      "早退",
      "旷工",
      "事假/小时",
      "病假/天",
      "工伤/天",
      "丧假/天",
      "婚假/天",
      "产假/天",
      "调休/小时",
      "年假/小时",
      "加班/小时",
      "加班未调休换算成工资",
      "备注",
    ]);
  });

  it("keeps count-style fields numeric and description fields editable as text", () => {
    const fieldsByKey = Object.fromEntries(SUMMARY_FIELD_DEFINITIONS.map((field) => [field.key, field]));

    expect(fieldsByKey.shouldAttendanceDays.valueType).toBe("number");
    expect(fieldsByKey.actualAttendanceDays.valueType).toBe("number");
    expect(fieldsByKey.entryDate.valueType).toBe("text");
    expect(fieldsByKey.lateText.valueType).toBe("text");
    expect(fieldsByKey.latePunishment.valueType).toBe("text");
    expect(fieldsByKey.overtimeToSalary.valueType).toBe("text");
    expect(fieldsByKey.remark.valueType).toBe("text");
  });
});
