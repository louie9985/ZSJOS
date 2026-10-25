import { describe, expect, it } from "vitest";
import { ExportedAttendanceReport } from "./models";
import { inferMonthlyParameters } from "./recoveredMonthlyParameters";

describe("inferMonthlyParameters", () => {
  it("recovers common summary days and suggests rest day candidates", () => {
    const report = exportedReport([
      { shouldAttendanceDays: 21, payrollDays: 23 },
      { shouldAttendanceDays: 21, payrollDays: 23 },
    ]);

    const recovered = inferMonthlyParameters(report);

    expect(recovered.source).toBe("inferred");
    expect(recovered.shouldAttendanceDays).toBe(21);
    expect(recovered.payrollDays).toBe(23);
    expect(recovered.restDays).toEqual([]);
    expect(recovered.paidRestDays).toEqual([]);
    expect(recovered.restDayCandidates[0]).toMatchObject({ day: 2, confidence: "high" });
    expect(recovered.paidRestDayCandidates[0]).toMatchObject({ day: 3, confidence: "high" });
    expect(recovered.warnings).toEqual([]);
  });

  it("warns when employee summary days are inconsistent", () => {
    const report = exportedReport([
      { shouldAttendanceDays: 21, payrollDays: 23 },
      { shouldAttendanceDays: 22, payrollDays: 23 },
    ]);

    const recovered = inferMonthlyParameters(report);

    expect(recovered.shouldAttendanceDays).toBeNull();
    expect(recovered.payrollDays).toBe(23);
    expect(recovered.warnings).toContain("不同员工的应出勤日不一致，请手工确认。");
  });
});

function exportedReport(
  summaries: Array<{ shouldAttendanceDays: number; payrollDays: number }>,
): ExportedAttendanceReport {
  return {
    year: 2026,
    month: 6,
    employees: summaries.map((summary, index) => ({
      name: `员工${index + 1}`,
      department: "教务部",
      sourceIndex: index + 1,
      days: [
        { day: 1, morning: "出勤", afternoon: "出勤" },
        { day: 2, morning: "休息", afternoon: "休息" },
        { day: 3, morning: "带薪", afternoon: "带薪" },
      ],
      summary: {
        shouldAttendanceDays: summary.shouldAttendanceDays,
        actualAttendanceDays: null,
        payrollDays: summary.payrollDays,
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
    })),
  };
}
