import {
  ExportedAttendanceReport,
  ExportedReportEmployee,
  RecoveredDayCandidate,
  RecoveredMonthlyParameters,
  SummaryReview,
} from "./models";

export const EMPTY_RECOVERED_PARAMETERS: RecoveredMonthlyParameters = {
  source: "none",
  shouldAttendanceDays: null,
  payrollDays: null,
  restDays: [],
  paidRestDays: [],
  restDayCandidates: [],
  paidRestDayCandidates: [],
  warnings: [],
};

export function recoverMonthlyParameters(report: ExportedAttendanceReport): RecoveredMonthlyParameters {
  if (report.recoveredParameters) return normalizeRecoveredParameters(report.recoveredParameters);
  return inferMonthlyParameters(report);
}

export function inferMonthlyParameters(report: ExportedAttendanceReport): RecoveredMonthlyParameters {
  const warnings: string[] = [];
  const shouldAttendanceDays = commonSummaryNumber(report.employees, "shouldAttendanceDays");
  const payrollDays = commonSummaryNumber(report.employees, "payrollDays");

  if (shouldAttendanceDays.inconsistent) warnings.push("不同员工的应出勤日不一致，请手工确认。");
  if (payrollDays.inconsistent) warnings.push("不同员工的计薪日不一致，请手工确认。");

  const restDayCandidates = inferDayCandidates(report, "休息");
  const paidRestDayCandidates = inferDayCandidates(report, "带薪");
  const restCandidateDays = new Set(restDayCandidates.map((candidate) => candidate.day));
  const paidCandidateDays = new Set(paidRestDayCandidates.map((candidate) => candidate.day));
  for (const day of restCandidateDays) {
    if (paidCandidateDays.has(day)) warnings.push(`${day} 日同时符合休息日和带薪休息日候选，请手工确认。`);
  }

  return {
    source: report.employees.length > 0 ? "inferred" : "none",
    shouldAttendanceDays: shouldAttendanceDays.value,
    payrollDays: payrollDays.value,
    restDays: [],
    paidRestDays: [],
    restDayCandidates,
    paidRestDayCandidates,
    warnings,
  };
}

export function normalizeRecoveredParameters(value: RecoveredMonthlyParameters | undefined): RecoveredMonthlyParameters {
  if (!value) return EMPTY_RECOVERED_PARAMETERS;
  return {
    source: value.source || "none",
    shouldAttendanceDays: normalizeNumber(value.shouldAttendanceDays),
    payrollDays: normalizeNumber(value.payrollDays),
    restDays: normalizeDays(value.restDays),
    paidRestDays: normalizeDays(value.paidRestDays),
    restDayCandidates: normalizeCandidates(value.restDayCandidates),
    paidRestDayCandidates: normalizeCandidates(value.paidRestDayCandidates),
    warnings: Array.isArray(value.warnings) ? value.warnings.filter((item) => typeof item === "string") : [],
  };
}

function commonSummaryNumber(
  employees: ExportedReportEmployee[],
  key: keyof Pick<SummaryReview, "shouldAttendanceDays" | "payrollDays">,
): { value: number | null; inconsistent: boolean } {
  const values = new Set<number>();
  for (const employee of employees) {
    const value = normalizeNumber(employee.summary[key]);
    if (value !== null) values.add(value);
  }
  if (values.size === 0) return { value: null, inconsistent: false };
  if (values.size === 1) return { value: [...values][0], inconsistent: false };
  return { value: null, inconsistent: true };
}

function inferDayCandidates(report: ExportedAttendanceReport, status: "休息" | "带薪"): RecoveredDayCandidate[] {
  const daysInMonth = new Date(report.year, report.month, 0).getDate();
  const candidates: RecoveredDayCandidate[] = [];
  const totalEmployees = report.employees.length;
  if (totalEmployees === 0) return candidates;

  for (let day = 1; day <= daysInMonth; day += 1) {
    let matchedEmployees = 0;
    for (const employee of report.employees) {
      const review = employee.days.find((item) => item.day === day);
      if (review?.morning === status && review.afternoon === status) matchedEmployees += 1;
    }
    if (matchedEmployees === totalEmployees) {
      candidates.push({ day, confidence: "high", matchedEmployees, totalEmployees });
    } else if (matchedEmployees / totalEmployees >= 0.8) {
      candidates.push({ day, confidence: "medium", matchedEmployees, totalEmployees });
    }
  }
  return candidates;
}

function normalizeNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  return null;
}

function normalizeDays(value: unknown): number[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map((item) => normalizeNumber(item)).filter((item): item is number => item !== null))]
    .filter((day) => Number.isInteger(day) && day >= 1 && day <= 31)
    .sort((left, right) => left - right);
}

function normalizeCandidates(value: unknown): RecoveredDayCandidate[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (!item || typeof item !== "object") return null;
      const candidate = item as Partial<RecoveredDayCandidate>;
      const day = normalizeNumber(candidate.day);
      const matchedEmployees = normalizeNumber(candidate.matchedEmployees);
      const totalEmployees = normalizeNumber(candidate.totalEmployees);
      if (day === null || matchedEmployees === null || totalEmployees === null) return null;
      return {
        day,
        confidence: candidate.confidence === "high" ? "high" : "medium",
        matchedEmployees,
        totalEmployees,
      };
    })
    .filter((item): item is RecoveredDayCandidate => Boolean(item));
}
