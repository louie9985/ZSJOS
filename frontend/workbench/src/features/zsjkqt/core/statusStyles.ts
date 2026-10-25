import { AttendanceStatus } from "./models";

export type StatusStyleKey =
  | "blank"
  | "normal"
  | "late"
  | "missing-card"
  | "personal-leave"
  | "benefit-leave"
  | "early-leave"
  | "abnormal"
  | "absence";

export function statusStyleKey(value: AttendanceStatus | string): StatusStyleKey {
  if (!value) return "blank";
  if (value === "出勤" || value === "休息" || value === "带薪") return "normal";
  if (value === "迟到") return "late";
  if (value === "缺卡") return "missing-card";
  if (value === "事假") return "personal-leave";
  if (value === "病假" || value === "工伤" || value === "丧假" || value === "婚假" || value === "产假" || value === "年假") return "benefit-leave";
  if (value === "早退") return "early-leave";
  if (value === "异常") return "abnormal";
  if (value === "旷工") return "absence";
  return "normal";
}

export function statusStyleClass(value: AttendanceStatus | string): string {
  return `status-${statusStyleKey(value)}`;
}
