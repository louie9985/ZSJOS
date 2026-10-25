import ExcelJS from "exceljs";
import {
  ATTENDANCE_STATUSES,
  AttendanceStatus,
  ExportedAttendanceReport,
  ExportedReportDay,
  ExportedReportEmployee,
  SummaryReview,
} from "./models";
import { inferMonthlyParameters, normalizeRecoveredParameters } from "./recoveredMonthlyParameters";
import { SUMMARY_FIELD_DEFINITIONS } from "./summaryFields";

const LEADING_HEADERS = ["序号", "部门", "姓名", "考勤"];
const METADATA_SHEET_NAME = "_attendance_metadata";

export async function parseExportedReportFile(file: File): Promise<ExportedAttendanceReport> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(await file.arrayBuffer());
  const worksheet = workbook.worksheets[0];
  if (!worksheet) throw exportedReportFormatError();

  const { year, month } = parseSheetMonth(worksheet.name);
  const daysInMonth = new Date(year, month, 0).getDate();
  validateHeaders(worksheet, daysInMonth);

  const employees: ExportedReportEmployee[] = [];
  for (let row = 2; row <= worksheet.rowCount; row += 1) {
    const name = cellText(worksheet, row, 3);
    const department = cellText(worksheet, row, 2);
    if (!name && !department) continue;
    if (!name || !department) throw new Error(`正式考勤汇总表第 ${row} 行缺少姓名或部门。`);

    employees.push({
      name,
      department,
      sourceIndex: employees.length + 1,
      days: parseDays(worksheet, row, daysInMonth),
      summary: parseSummary(worksheet, row, daysInMonth),
    });
  }

  if (employees.length === 0) throw exportedReportFormatError();

  const report: ExportedAttendanceReport = {
    year,
    month,
    sourceFileName: file.name,
    sourceFileHash: `${file.name}-${file.size}-${file.lastModified}`,
    employees,
    recoveredParameters: parseMetadata(workbook, year, month, employees),
  };
  if (!report.recoveredParameters) report.recoveredParameters = inferMonthlyParameters(report);
  return report;
}

function validateHeaders(worksheet: ExcelJS.Worksheet, daysInMonth: number): void {
  for (const [index, header] of LEADING_HEADERS.entries()) {
    if (cellText(worksheet, 1, index + 1) !== header) throw exportedReportFormatError();
  }

  for (let day = 1; day <= daysInMonth; day += 1) {
    const header = cellText(worksheet, 1, 4 + day);
    if (!header.match(new RegExp(`^\\d{1,2}月${day}日星期[一二三四五六日]$`))) throw exportedReportFormatError();
  }

  const summaryStart = 5 + daysInMonth;
  for (const [index, field] of SUMMARY_FIELD_DEFINITIONS.entries()) {
    if (cellText(worksheet, 1, summaryStart + index) !== field.label) throw exportedReportFormatError();
  }
}

function parseDays(worksheet: ExcelJS.Worksheet, row: number, daysInMonth: number): ExportedReportDay[] {
  const days: ExportedReportDay[] = [];
  for (let day = 1; day <= daysInMonth; day += 1) {
    const { morning, afternoon } = parseStatusPair(cellText(worksheet, row, 4 + day), row, day);
    days.push({ day, morning, afternoon });
  }
  return days;
}

function parseSummary(worksheet: ExcelJS.Worksheet, row: number, daysInMonth: number): SummaryReview {
  const summary = {} as SummaryReview;
  const summaryStart = 5 + daysInMonth;
  for (const [index, field] of SUMMARY_FIELD_DEFINITIONS.entries()) {
    const value = worksheet.getCell(row, summaryStart + index).value;
    summary[field.key] = (field.valueType === "number" ? numericValue(value) : textValue(value)) as never;
  }
  return summary;
}

function parseStatusPair(text: string, row: number, day: number): { morning: AttendanceStatus; afternoon: AttendanceStatus } {
  const match = text.match(/上午[:：]\s*([^\r\n]+)\s*[\r\n]+\s*下午[:：]\s*([^\r\n]+)/);
  if (!match) throw new Error(`正式考勤汇总表第 ${row} 行 ${day} 日状态格式不正确。`);
  return {
    morning: normalizeStatus(match[1], row, day, "上午"),
    afternoon: normalizeStatus(match[2], row, day, "下午"),
  };
}

function normalizeStatus(value: string, row: number, day: number, half: string): AttendanceStatus {
  const status = value.trim();
  const normalized = status === "--" ? "" : status === "带薪假" ? "带薪" : status;
  if ((ATTENDANCE_STATUSES as readonly string[]).includes(normalized)) return normalized as AttendanceStatus;
  throw new Error(`正式考勤汇总表第 ${row} 行 ${day} 日${half}状态不支持：${status}`);
}

function parseSheetMonth(name: string): { year: number; month: number } {
  const match = name.match(/^(20\d{2})年(\d{1,2})月考勤$/);
  if (!match) throw exportedReportFormatError();
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (!Number.isInteger(month) || month < 1 || month > 12) throw exportedReportFormatError();
  return { year, month };
}

function numericValue(value: ExcelJS.CellValue): number | null {
  if (value == null || value === "" || value === "--") return null;
  if (typeof value === "number") return value;
  if (typeof value === "object" && "result" in value) return numericValue(value.result as ExcelJS.CellValue);
  const number = Number(textValue(value));
  return Number.isFinite(number) ? number : null;
}

function textValue(value: ExcelJS.CellValue): string {
  if (value == null || value === "--") return "";
  if (typeof value === "object" && "text" in value) return String(value.text).trim();
  if (typeof value === "object" && "richText" in value) return value.richText.map((part) => part.text).join("").trim();
  if (typeof value === "object" && "result" in value) return textValue(value.result as ExcelJS.CellValue);
  return String(value).trim();
}

function cellText(worksheet: ExcelJS.Worksheet, row: number, col: number): string {
  return textValue(worksheet.getCell(row, col).value);
}

function exportedReportFormatError(): Error {
  return new Error("请选择由中世健考勤通导出的正式考勤汇总表。");
}

function parseMetadata(
  workbook: ExcelJS.Workbook,
  year: number,
  month: number,
  employees: ExportedReportEmployee[],
): ExportedAttendanceReport["recoveredParameters"] {
  const worksheet = workbook.getWorksheet(METADATA_SHEET_NAME);
  if (!worksheet) return undefined;
  const values = new Map<string, string>();
  for (let row = 1; row <= worksheet.rowCount; row += 1) {
    const key = cellText(worksheet, row, 1);
    if (!key) continue;
    values.set(key, cellText(worksheet, row, 2));
  }
  if (values.get("appName") !== "zhongshijian-kaoqintong" || values.get("schemaVersion") !== "1") {
    return {
      ...inferMonthlyParameters({ year, month, employees }),
      warnings: ["隐藏元数据格式不正确，已改用汇总表推断。", ...inferMonthlyParameters({ year, month, employees }).warnings],
    };
  }
  if (Number(values.get("year")) !== year || Number(values.get("month")) !== month) {
    return {
      ...inferMonthlyParameters({ year, month, employees }),
      warnings: ["隐藏元数据年月与工作表不一致，已改用汇总表推断。", ...inferMonthlyParameters({ year, month, employees }).warnings],
    };
  }
  return normalizeRecoveredParameters({
    source: "metadata",
    shouldAttendanceDays: numericMetadata(values.get("shouldAttendanceDays")),
    payrollDays: numericMetadata(values.get("payrollDays")),
    restDays: dayListMetadata(values.get("restDays")),
    paidRestDays: dayListMetadata(values.get("paidRestDays")),
    restDayCandidates: [],
    paidRestDayCandidates: [],
    warnings: [],
  });
}

function numericMetadata(value: string | undefined): number | null {
  if (!value) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function dayListMetadata(value: string | undefined): number[] {
  if (!value) return [];
  return value
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item >= 1 && item <= 31);
}
