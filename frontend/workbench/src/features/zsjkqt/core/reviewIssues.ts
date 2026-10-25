import { DayReview, EmployeeReview } from "./models";
import { isSourceSummaryReviewIssue } from "./sourceSummaryReview";

export function dayHasReviewIssue(day: DayReview): boolean {
  if (!dayHasAbnormalStatus(day)) return false;
  return day.issues.some((issue) => !isSourceSummaryReviewIssue(issue)) || dayChanged(day);
}

export function dayHasManualReviewReminder(day: DayReview): boolean {
  return day.modified || day.issues.some(isSourceSummaryReviewIssue);
}

export function employeeReviewIssueCount(employee: EmployeeReview): number {
  return employee.days.filter(dayHasReviewIssue).length;
}

function dayChanged(day: DayReview): boolean {
  return (
    day.systemMorning !== day.finalMorning ||
    day.systemAfternoon !== day.finalAfternoon ||
    Boolean(day.morningNote) ||
    Boolean(day.afternoonNote)
  );
}

function dayHasAbnormalStatus(day: DayReview): boolean {
  return [day.finalMorning, day.finalAfternoon].includes("异常");
}
