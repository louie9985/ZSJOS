import ExcelJS from "exceljs";
import { AttendanceMonth, SourceEmployee, ValidationIssue } from "./models";

const SUMMARY_FIELDS = [
  "应出勤天数(天)",
  "实际出勤天数(天)",
  "缺卡次数(次)",
  "迟到次数(次)",
  "迟到时长(分钟)",
  "早退次数(次)",
  "早退时长(分钟)",
  "旷工次数(次)",
  "旷工时长(分钟)",
  "事假(小时)",
  "病假(小时)",
  "年假(小时)",
  "调休假(小时)",
  "调休(小时)",
  "婚假(天)",
  "产假(天)",
  "丧假(天)",
  "加班时长(小时)",
];

export async function parseBrowserSourceFile(file: File): Promise<{ attendance: AttendanceMonth; issues: ValidationIssue[] }> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(await file.arrayBuffer());
  const worksheet = workbook.worksheets[0];
  if (!worksheet) throw new Error("无法读取企业微信月报工作表");

  const { startDate, endDate, year, month } = parseDateRange(file.name, cellText(worksheet, 2, 1));
  if (!year || !month) throw new Error("无法从文件名或统计时间识别月份");

  const columns = buildColumnMap(worksheet);
  const dayColumns = buildDayColumns(worksheet);
  const daysInMonth = new Date(year, month, 0).getDate();
  const seenNames = new Set<string>();
  const issues: ValidationIssue[] = [];
  const employees: SourceEmployee[] = [];

  for (let row = 5; row <= worksheet.rowCount; row += 1) {
    const name = cellText(worksheet, row, columns.get("姓名"));
    if (!name) continue;

    if (seenNames.has(name)) {
      issues.push({ level: "warning", employee: name, day: null, message: "同名人员出现多条记录", rawValue: "" });
    }
    seenNames.add(name);

    const summaries: Record<string, unknown> = {};
    for (const field of SUMMARY_FIELDS) {
      const col = columns.get(field);
      if (col) summaries[field] = cellRaw(worksheet, row, col);
    }

    const dayDetails: Record<number, string> = {};
    for (const [day, col] of dayColumns.entries()) {
      if (day <= daysInMonth) dayDetails[day] = cellText(worksheet, row, col);
    }

    employees.push({
      name,
      account: cellText(worksheet, row, columns.get("账号")),
      rule: cellText(worksheet, row, columns.get("所属规则")),
      department: cellText(worksheet, row, columns.get("部门")),
      position: cellText(worksheet, row, columns.get("职务")),
      jobNo: cellText(worksheet, row, columns.get("工号")),
      summaries,
      dayDetails,
    });
  }

  return {
    attendance: {
      year,
      month,
      startDate,
      endDate,
      sourceFileName: file.name,
      sourceFileHash: `${file.name}-${file.size}-${file.lastModified}`,
      employees,
    },
    issues,
  };
}

function buildColumnMap(worksheet: ExcelJS.Worksheet): Map<string, number> {
  const columns = new Map<string, number>();
  for (let col = 1; col <= worksheet.columnCount; col += 1) {
    const group = cellText(worksheet, 3, col);
    if (group) columns.set(group, col);

    const header = cellText(worksheet, 4, col);
    if (header) columns.set(header, col);
  }
  return columns;
}

function buildDayColumns(worksheet: ExcelJS.Worksheet): Map<number, number> {
  const dayColumns = new Map<number, number>();
  for (let col = 1; col <= worksheet.columnCount; col += 1) {
    const header = cellText(worksheet, 4, col);
    const match = header.match(/^\s*(\d{1,2})\s*(?:\n|$)/);
    if (match) dayColumns.set(Number(match[1]), col);
  }
  return dayColumns;
}

function parseDateRange(filename: string, statCell: string): { startDate: string | null; endDate: string | null; year: number | null; month: number | null } {
  const filenameMatch = filename.match(/(20\d{2})(\d{2})(\d{2})-(20\d{2})(\d{2})(\d{2})/);
  if (filenameMatch) {
    return {
      startDate: `${filenameMatch[1]}-${filenameMatch[2]}-${filenameMatch[3]}`,
      endDate: `${filenameMatch[4]}-${filenameMatch[5]}-${filenameMatch[6]}`,
      year: Number(filenameMatch[1]),
      month: Number(filenameMatch[2]),
    };
  }

  const statMatch = statCell.match(/统计时间:(\d{2})-(\d{2})\s*[～~-]\s*(\d{2})-(\d{2})/);
  if (statMatch) {
    const year = new Date().getFullYear();
    return {
      startDate: `${year}-${statMatch[1]}-${statMatch[2]}`,
      endDate: `${year}-${statMatch[3]}-${statMatch[4]}`,
      year,
      month: Number(statMatch[1]),
    };
  }

  return { startDate: null, endDate: null, year: null, month: null };
}

function cellText(worksheet: ExcelJS.Worksheet, row: number, col?: number): string {
  if (!col) return "";
  const value = worksheet.getCell(row, col).value;
  if (value == null || value === "--") return "";
  if (typeof value === "object" && "text" in value) return String(value.text).trim();
  if (typeof value === "object" && "richText" in value) return value.richText.map((part) => part.text).join("").trim();
  return String(value).trim();
}

function cellRaw(worksheet: ExcelJS.Worksheet, row: number, col: number): unknown {
  const value = worksheet.getCell(row, col).value;
  return value == null || value === "--" ? null : value;
}
