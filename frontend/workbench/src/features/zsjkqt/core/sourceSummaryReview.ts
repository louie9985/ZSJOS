import { AttendanceStatus, DayReview, ValidationIssue } from "./models";

export const SOURCE_SUMMARY_REVIEW_MESSAGE = "人工修改，汇总数据待核对";

const SOURCE_SUMMARY_STATUSES = new Set<AttendanceStatus>(["事假", "调休", "年假", "加班"]);

export function refreshSourceSummaryReviewIssue(day: DayReview, employee: string): DayReview {
  const issues = day.issues.filter((issue) => !isSourceSummaryReviewIssue(issue));
  if (!needsSourceSummaryReview(day)) return { ...day, issues };

  return appendSourceSummaryReviewIssue({ ...day, issues }, employee);
}

export function appendSourceSummaryReviewIssue(day: DayReview, employee: string): DayReview {
  const issues = day.issues.filter((issue) => !isSourceSummaryReviewIssue(issue));
  return {
    ...day,
    issues: [
      ...issues,
      {
        level: "info",
        employee,
        day: day.day,
        message: SOURCE_SUMMARY_REVIEW_MESSAGE,
        rawValue: day.rawDetail,
      },
    ],
  };
}

export function isSourceSummaryReviewIssue(issue: ValidationIssue): boolean {
  return issue.message === SOURCE_SUMMARY_REVIEW_MESSAGE;
}

function needsSourceSummaryReview(day: DayReview): boolean {
  if (day.systemMorning === day.finalMorning && day.systemAfternoon === day.finalAfternoon) return false;
  return [day.systemMorning, day.systemAfternoon, day.finalMorning, day.finalAfternoon].some((status) =>
    SOURCE_SUMMARY_STATUSES.has(status),
  );
}
