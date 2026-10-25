import { describe, expect, it } from "vitest";
import {
  attendanceWorkSchedule,
  DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS,
  ensureDepartments,
  knownDepartmentsFromMonth,
  knownDepartmentsFromProfiles,
  normalizeDepartmentScheduleSettings,
  resolveDepartmentSchedule,
  validateDepartmentScheduleSettings,
} from "./departmentSchedules";

describe("department schedules", () => {
  it("calculates default half hours from configured times", () => {
    const schedule = attendanceWorkSchedule(DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS.default);

    expect(schedule.workHalves.morning.hours).toBe(3.5);
    expect(schedule.workHalves.afternoon.hours).toBe(4.5);
    expect(schedule.standardWorkHours).toBe(8);
  });

  it("uses display department before source department", () => {
    const settings = normalizeDepartmentScheduleSettings({
      default: DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS.default,
      departments: {
        "AI应用部": {
          mode: "custom",
          morning: { start: "09:00", end: "12:00" },
          afternoon: { start: "13:30", end: "18:30" },
        },
        "源表部门": {
          mode: "custom",
          morning: { start: "08:00", end: "12:00" },
          afternoon: { start: "13:00", end: "18:00" },
        },
      },
    });

    const resolved = resolveDepartmentSchedule("AI应用部", "公司/源表部门", settings);

    expect(resolved.department).toBe("AI应用部");
    expect(resolved.source).toBe("display");
    expect(resolved.schedule.workHalves.morning.hours).toBe(3);
    expect(resolved.schedule.workHalves.afternoon.hours).toBe(5);
  });

  it("falls back to source department when display department has no setting", () => {
    const settings = normalizeDepartmentScheduleSettings({
      default: DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS.default,
      departments: {
        "源表部门": {
          mode: "custom",
          morning: { start: "08:00", end: "12:00" },
          afternoon: { start: "13:00", end: "18:00" },
        },
      },
    });

    const resolved = resolveDepartmentSchedule("未配置部门", "公司/源表部门", settings);

    expect(resolved.department).toBe("源表部门");
    expect(resolved.source).toBe("source");
    expect(resolved.schedule.standardWorkHours).toBe(9);
  });

  it("prefers source custom schedules over display department defaults", () => {
    const settings = normalizeDepartmentScheduleSettings({
      default: DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS.default,
      departments: {
        Sales: { mode: "default" },
        "Company/Sales": {
          mode: "custom",
          morning: { start: "08:00", end: "12:00" },
          afternoon: { start: "13:00", end: "18:00" },
        },
      },
    });

    const resolved = resolveDepartmentSchedule("Sales", "Company/Sales", settings);

    expect(resolved.department).toBe("Company/Sales");
    expect(resolved.source).toBe("source");
    expect(resolved.schedule.standardWorkHours).toBe(9);
  });

  it("does not auto-add raw source department paths to editable department lists", () => {
    const departments = knownDepartmentsFromMonth(
      [
        {
          name: "Alice",
          account: "alice",
          rule: "",
          department: "Company/Sales",
          position: "",
          jobNo: "",
          summaries: {},
          dayDetails: {},
        },
      ],
      {},
    );

    expect(departments).toEqual(["Sales"]);
  });

  it("reads known departments from personnel profiles", () => {
    const departments = knownDepartmentsFromProfiles({
      alice: { department: "销售部" },
      bob: { department: "销售部" },
      empty: { department: " " },
      carol: { department: "教务部" },
    });

    expect(departments).toEqual(["教务部", "销售部"]);
  });

  it("ensures new departments inherit the default schedule", () => {
    const settings = ensureDepartments(DEFAULT_DEPARTMENT_SCHEDULE_SETTINGS, ["教务部"]);

    expect(settings.departments["教务部"]).toEqual({ mode: "default" });
  });

  it("validates overlapping halves", () => {
    const settings = normalizeDepartmentScheduleSettings({
      default: {
        morning: { start: "08:30", end: "13:30" },
        afternoon: { start: "13:00", end: "18:00" },
      },
      departments: {},
    });

    expect(validateDepartmentScheduleSettings(settings)[0].message).toBe("上午和下午不能重叠");
  });
});
