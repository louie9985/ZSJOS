import ExcelJS from "exceljs";
import { describe, expect, it } from "vitest";
import { parseExportedReportFile } from "./exportedReportParser";
import { SUMMARY_FIELD_DEFINITIONS } from "./summaryFields";

describe("parseExportedReportFile", () => {
  it("parses a strict exported attendance report without recalculating values", async () => {
    const file = await exportedReportFile();

    const report = await parseExportedReportFile(file);

    expect(report.year).toBe(2026);
    expect(report.month).toBe(6);
    expect(report.employees).toHaveLength(1);
    expect(report.employees[0].name).toBe("张三");
    expect(report.employees[0].department).toBe("教务部");
    expect(report.employees[0].days[0]).toEqual({ day: 1, morning: "出勤", afternoon: "事假" });
    expect(report.employees[0].summary.actualAttendanceDays).toBe(99);
    expect(report.employees[0].summary.remark).toBe("原表备注");
    expect(report.recoveredParameters?.source).toBe("inferred");
    expect(report.recoveredParameters?.shouldAttendanceDays).toBe(21);
    expect(report.recoveredParameters?.payrollDays).toBe(23);
    expect(report.recoveredParameters?.restDayCandidates[0]).toMatchObject({ day: 2, confidence: "high" });
  });

  it("rejects old exported sheets without a year in the sheet name", async () => {
    const file = await exportedReportFile({ sheetName: "6月考勤" });

    await expect(parseExportedReportFile(file)).rejects.toThrow("请选择由中世健考勤通导出的正式考勤汇总表");
  });

  it("uses hidden metadata when present", async () => {
    const file = await exportedReportFile({ metadata: true });

    const report = await parseExportedReportFile(file);

    expect(report.recoveredParameters).toMatchObject({
      source: "metadata",
      shouldAttendanceDays: 20,
      payrollDays: 22,
      restDays: [6, 13],
      paidRestDays: [1],
    });
  });
});

async function exportedReportFile(options: { sheetName?: string; metadata?: boolean } = {}): Promise<File> {
  const workbook = new ExcelJS.Workbook();
  const worksheet = workbook.addWorksheet(options.sheetName || "2026年6月考勤");
  worksheet.getCell(1, 1).value = "序号";
  worksheet.getCell(1, 2).value = "部门";
  worksheet.getCell(1, 3).value = "姓名";
  worksheet.getCell(1, 4).value = "考勤";
  for (let day = 1; day <= 30; day += 1) {
    worksheet.getCell(1, 4 + day).value = `6月${day}日${weekdayName(2026, 6, day)}`;
    worksheet.getCell(2, 4 + day).value =
      day === 1 ? "上午：出勤\n下午：事假" : day === 2 ? "上午：休息\n下午：休息" : "上午：--\n下午：--";
  }

  const summaryStart = 35;
  for (const [index, field] of SUMMARY_FIELD_DEFINITIONS.entries()) {
    worksheet.getCell(1, summaryStart + index).value = field.label;
  }
  worksheet.getCell(2, 1).value = 1;
  worksheet.getCell(2, 2).value = "教务部";
  worksheet.getCell(2, 3).value = "张三";
  worksheet.getCell(2, summaryStart).value = 21;
  worksheet.getCell(2, summaryStart + 1).value = 99;
  worksheet.getCell(2, summaryStart + 2).value = 23;
  worksheet.getCell(2, summaryStart + 19).value = "原表备注";

  if (options.metadata) {
    const metadata = workbook.addWorksheet("_attendance_metadata");
    metadata.state = "veryHidden";
    const rows = [
      ["schemaVersion", "1"],
      ["appName", "zhongshijian-kaoqintong"],
      ["year", "2026"],
      ["month", "6"],
      ["shouldAttendanceDays", "20"],
      ["payrollDays", "22"],
      ["restDays", "6,13"],
      ["paidRestDays", "1"],
    ];
    rows.forEach(([key, value], index) => {
      metadata.getCell(index + 1, 1).value = key;
      metadata.getCell(index + 1, 2).value = value;
    });
  }

  const buffer = await workbook.xlsx.writeBuffer();
  return new File([buffer], "2026年6月中世健学校考勤汇总表.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    lastModified: 0,
  });
}

function weekdayName(year: number, month: number, day: number): string {
  return ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"][new Date(year, month - 1, day).getDay()];
}
