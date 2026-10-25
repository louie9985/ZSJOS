import { SummaryReview } from "./models";

export type SummaryFieldValueType = "number" | "text";

export interface SummaryFieldDefinition {
  key: keyof SummaryReview;
  label: string;
  valueType: SummaryFieldValueType;
}

export const SUMMARY_FIELD_DEFINITIONS: SummaryFieldDefinition[] = [
  { key: "shouldAttendanceDays", label: "应出勤日", valueType: "number" },
  { key: "actualAttendanceDays", label: "实出勤日", valueType: "number" },
  { key: "payrollDays", label: "计薪日", valueType: "number" },
  { key: "entryDate", label: "入职日期", valueType: "text" },
  { key: "missingCardCount", label: "缺卡", valueType: "number" },
  { key: "lateText", label: "迟到", valueType: "text" },
  { key: "latePunishment", label: "迟到是否惩罚", valueType: "text" },
  { key: "earlyText", label: "早退", valueType: "text" },
  { key: "absenceDays", label: "旷工", valueType: "number" },
  { key: "personalLeaveHours", label: "事假/小时", valueType: "number" },
  { key: "sickLeaveDays", label: "病假/天", valueType: "number" },
  { key: "injuryLeaveDays", label: "工伤/天", valueType: "number" },
  { key: "funeralLeaveDays", label: "丧假/天", valueType: "number" },
  { key: "marriageLeaveDays", label: "婚假/天", valueType: "number" },
  { key: "maternityLeaveDays", label: "产假/天", valueType: "number" },
  { key: "compLeaveHours", label: "调休/小时", valueType: "number" },
  { key: "annualLeaveHours", label: "年假/小时", valueType: "number" },
  { key: "overtimeHours", label: "加班/小时", valueType: "number" },
  { key: "overtimeToSalary", label: "加班未调休换算成工资", valueType: "text" },
  { key: "remark", label: "备注", valueType: "text" },
];
