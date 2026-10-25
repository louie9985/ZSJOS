import { describe, expect, it } from "vitest";
import { AttendanceMonth, ExportedAttendanceReport, SourceEmployee } from "./models";
import { applyReimportMonthlyParameters, buildAttendanceProject, buildEmployeeReview, buildProjectFromExportedReport, mergeRebuiltProjectWithReviewState } from "./reviewBuilder";

const options = {
  restDays: [],
  paidRestDays: [],
  shouldAttendanceDays: 21,
  payrollDays: 23,
};

function month(employee: SourceEmployee): AttendanceMonth {
  return {
    year: 2026,
    month: 6,
    startDate: "2026-06-01",
    endDate: "2026-06-30",
    sourceFileName: "source.xlsx",
    sourceFileHash: "source-hash",
    employees: [employee],
  };
}

function employee(patch: Partial<SourceEmployee> = {}): SourceEmployee {
  return {
    name: "Source Name",
    account: "alice",
    rule: "",
    department: "Company/Sales",
    position: "",
    jobNo: "",
    summaries: {},
    dayDetails: {},
    ...patch,
  };
}

describe("buildEmployeeReview personnel profiles", () => {
  it("uses personnel profile defaults over source values", () => {
    const source = employee({ summaries: { "调休假(小时)": 2 } });
    const review = buildEmployeeReview(month(source), source, 1, options, {
      alice: {
        name: "Profile Name",
        department: "Operations",
        entry_date: "2026-05-20",
        enabled: false,
      },
    });

    expect(review.key).toBe("alice");
    expect(review.displayName).toBe("Profile Name");
    expect(review.displayDepartment).toBe("Operations");
    expect(review.entryDate).toBe("2026-05-20");
    expect(review.enabled).toBe(false);
    expect(review.summary.entryDate).toBe("2026-05-20");
    expect(review.sourceSummaries).toEqual({ "调休假(小时)": 2 });
    expect(review.summary.compLeaveHours).toBe(2);
  });

  it("falls back to source values and exports employees by default", () => {
    const source = employee({ account: "", name: "Fallback Name" });
    const review = buildEmployeeReview(month(source), source, 1, options, {
      "Fallback Name": {
        name: " ",
        department: "",
      },
    });

    expect(review.key).toBe("Fallback Name");
    expect(review.displayName).toBe("Fallback Name");
    expect(review.displayDepartment).toBe("Sales");
    expect(review.entryDate).toBe("");
    expect(review.enabled).toBe(true);
    expect(review.summary.entryDate).toBe("");
  });

  it("applies profiles when building the whole attendance project", () => {
    const source = employee();
    const project = buildAttendanceProject(month(source), options, {
      alice: {
        department: "AI Department",
        entry_date: "2026-06-01",
        enabled: true,
      },
    });

    expect(project.employees).toHaveLength(1);
    expect(project.employees[0].displayDepartment).toBe("AI Department");
    expect(project.employees[0].entryDate).toBe("2026-06-01");
    expect(project.employees[0].summary.entryDate).toBe("2026-06-01");
  });

  it("applies department schedules by display department", () => {
    const source = employee({
      department: "Company/Sales",
      dayDetails: {
        1: "事假",
      },
    });
    const review = buildEmployeeReview(
      month(source),
      source,
      1,
      options,
      {
        alice: {
          department: "AI应用部",
        },
      },
      {
        default: {
          morning: { start: "08:30", end: "12:00" },
          afternoon: { start: "13:30", end: "18:00" },
        },
        departments: {
          "AI应用部": {
            mode: "custom",
            morning: { start: "09:00", end: "12:00" },
            afternoon: { start: "13:30", end: "17:30" },
          },
        },
      },
    );

    expect(review.departmentSchedule.department).toBe("AI应用部");
    expect(review.departmentSchedule.schedule.standardWorkHours).toBe(7);
  });
});

describe("mergeRebuiltProjectWithReviewState", () => {
  it("updates untouched half-day statuses when rebuilding for a new department schedule", () => {
    const source = employee({ dayDetails: { 1: "正常- 18:30\n迟到10分钟-13:10;" } });
    const previous = buildAttendanceProject(month(source), options);
    previous.employees[0].days[0].finalAfternoon = "事假";
    previous.employees[0].days[0].modified = true;
    const settings = { default: { morning: { start: "09:00", end: "12:00" }, afternoon: { start: "13:00", end: "18:00" } }, departments: {} };
    const rebuilt = buildAttendanceProject(month(source), options, {}, settings);
    const merged = mergeRebuiltProjectWithReviewState(rebuilt, previous, settings);
    expect(merged.employees[0].days[0]).toMatchObject({
      systemMorning: "出勤", systemAfternoon: "迟到", finalMorning: "出勤", finalAfternoon: "事假", modified: true,
    });
  });

  it("keeps manual day changes while recalculating unmodified days with new parameters", () => {
    const source = employee({
      dayDetails: {
        1: "正常- 08:21; 18:03",
        2: "正常- 08:21; 18:03",
      },
    });
    const previousProject = buildAttendanceProject(month(source), options);
    previousProject.employees[0] = {
      ...previousProject.employees[0],
      confirmed: true,
      days: previousProject.employees[0].days.map((day) =>
        day.day === 1
          ? {
              ...day,
              finalMorning: "事假",
              finalAfternoon: "事假",
              modified: true,
            }
          : day,
      ),
    };

    const rebuiltProject = buildAttendanceProject(month(source), {
      ...options,
      restDays: [1, 2],
      payrollDays: 22,
    });
    const mergedProject = mergeRebuiltProjectWithReviewState(rebuiltProject, previousProject);
    const mergedEmployee = mergedProject.employees[0];

    expect(mergedEmployee.confirmed).toBe(false);
    expect(mergedEmployee.days[0].systemMorning).toBe("休息");
    expect(mergedEmployee.days[0].finalMorning).toBe("事假");
    expect(mergedEmployee.days[0].modified).toBe(true);
    expect(mergedEmployee.days[1].systemMorning).toBe("休息");
    expect(mergedEmployee.days[1].finalMorning).toBe("休息");
    expect(mergedEmployee.summary.payrollDays).toBe(22);
  });

  it("preserves employee edits and manual summary fields without overriding new monthly parameters", () => {
    const source = employee();
    const previousProject = buildAttendanceProject(month(source), options);
    previousProject.employees[0] = {
      ...previousProject.employees[0],
      displayName: "Edited Name",
      displayDepartment: "Edited Department",
      enabled: false,
      confirmed: true,
      entryDate: "2026-06-08",
      summary: {
        ...previousProject.employees[0].summary,
        payrollDays: 99,
        remark: "人工备注",
      },
      manualSummaryOverrides: {
        payrollDays: true,
        remark: true,
      },
      notes: "员工备注",
    };

    const rebuiltProject = buildAttendanceProject(month(source), {
      ...options,
      payrollDays: 22,
    });
    const mergedProject = mergeRebuiltProjectWithReviewState(rebuiltProject, previousProject);
    const mergedEmployee = mergedProject.employees[0];

    expect(mergedEmployee.displayName).toBe("Edited Name");
    expect(mergedEmployee.displayDepartment).toBe("Edited Department");
    expect(mergedEmployee.enabled).toBe(false);
    expect(mergedEmployee.entryDate).toBe("2026-06-08");
    expect(mergedEmployee.confirmed).toBe(false);
    expect(mergedEmployee.summary.payrollDays).toBe(22);
    expect(mergedEmployee.summary.remark).toBe("人工备注");
    expect(mergedEmployee.manualSummaryOverrides.payrollDays).toBe(false);
    expect(mergedEmployee.manualSummaryOverrides.remark).toBe(true);
    expect(mergedEmployee.notes).toBe("员工备注");
  });

  it("keeps confirmation when parameters do not change", () => {
    const source = employee();
    const previousProject = buildAttendanceProject(month(source), options);
    previousProject.employees[0] = {
      ...previousProject.employees[0],
      confirmed: true,
    };

    const rebuiltProject = buildAttendanceProject(month(source), options);
    const mergedProject = mergeRebuiltProjectWithReviewState(rebuiltProject, previousProject);

    expect(mergedProject.employees[0].confirmed).toBe(true);
  });
});

describe("buildProjectFromExportedReport", () => {
  it("keeps imported statuses and summaries without recalculating", () => {
    const report: ExportedAttendanceReport = {
      year: 2026,
      month: 6,
      sourceFileName: "2026年6月中世健学校考勤汇总表.xlsx",
      employees: [
        {
          name: "张三",
          department: "教务部",
          sourceIndex: 1,
          days: [{ day: 1, morning: "出勤", afternoon: "事假" }],
          summary: {
            shouldAttendanceDays: 21,
            actualAttendanceDays: 99,
            payrollDays: 23,
            entryDate: "2026-06-01",
            missingCardCount: null,
            lateText: null,
            latePunishment: null,
            earlyText: null,
            absenceDays: null,
            personalLeaveHours: 7,
            sickLeaveDays: null,
            injuryLeaveDays: null,
            funeralLeaveDays: null,
            marriageLeaveDays: null,
            maternityLeaveDays: null,
            compLeaveHours: null,
            annualLeaveHours: null,
            overtimeHours: null,
            overtimeToSalary: "",
            remark: "保留导入值",
          },
        },
      ],
    };

    const project = buildProjectFromExportedReport(report);
    const employee = project.employees[0];

    expect(project.sourceKind).toBe("exported-report");
    expect(project.shouldAttendanceDays).toBe(21);
    expect(project.payrollDays).toBe(23);
    expect(employee.confirmed).toBe(false);
    expect(employee.key).toBe("张三-教务部-1");
    expect(employee.days[0].systemMorning).toBe("出勤");
    expect(employee.days[0].finalAfternoon).toBe("事假");
    expect(employee.summary.actualAttendanceDays).toBe(99);
    expect(employee.summary.personalLeaveHours).toBe(7);
    expect(employee.sourceSummaries).toMatchObject({
      ...report.employees[0].summary,
      "事假(小时)": 7,
    });
    expect(employee.manualSummaryOverrides).toEqual({});
  });

  it("applies confirmed reimport monthly parameters to employee summary fields only", () => {
    const project = buildProjectFromExportedReport(exportedReportForReimport());

    const updated = applyReimportMonthlyParameters(project, {
      restDays: [7, 14],
      paidRestDays: [1],
      shouldAttendanceDays: 20,
      payrollDays: 22,
    });
    const employee = updated.employees[0];

    expect(updated.shouldAttendanceDays).toBe(20);
    expect(updated.payrollDays).toBe(22);
    expect(updated.restDays).toEqual([7, 14]);
    expect(updated.paidRestDays).toEqual([1]);
    expect(employee.summary.shouldAttendanceDays).toBe(20);
    expect(employee.summary.payrollDays).toBe(22);
    expect(employee.summary.actualAttendanceDays).toBe(99);
    expect(employee.summary.personalLeaveHours).toBe(7);
    expect(employee.manualSummaryOverrides.shouldAttendanceDays).toBe(true);
    expect(employee.manualSummaryOverrides.payrollDays).toBe(true);
  });
});

function exportedReportForReimport(): ExportedAttendanceReport {
  return {
    year: 2026,
    month: 6,
    sourceFileName: "2026年6月中世健学校考勤汇总表.xlsx",
    employees: [
      {
        name: "张三",
        department: "教务部",
        sourceIndex: 1,
        days: [{ day: 1, morning: "出勤", afternoon: "事假" }],
        summary: {
          shouldAttendanceDays: 21,
          actualAttendanceDays: 99,
          payrollDays: 23,
          entryDate: "2026-06-01",
          missingCardCount: null,
          lateText: null,
          latePunishment: null,
          earlyText: null,
          absenceDays: null,
          personalLeaveHours: 7,
          sickLeaveDays: null,
          injuryLeaveDays: null,
          funeralLeaveDays: null,
          marriageLeaveDays: null,
          maternityLeaveDays: null,
          compLeaveHours: null,
          annualLeaveHours: null,
          overtimeHours: null,
          overtimeToSalary: "",
          remark: "保留导入值",
        },
      },
    ],
  };
}
