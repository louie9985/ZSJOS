import { describe, expect, it } from "vitest";
import { DayReview } from "./models";
import { refreshSourceSummaryReviewIssue, SOURCE_SUMMARY_REVIEW_MESSAGE } from "./sourceSummaryReview";

function day(patch: Partial<DayReview> = {}): DayReview {
  return {
    day: 1,
    rawDetail: "",
    systemMorning: "",
    systemAfternoon: "",
    finalMorning: "",
    finalAfternoon: "",
    morningNote: "",
    afternoonNote: "",
    issues: [],
    modified: false,
    ...patch,
  };
}

describe("source summary review reminders", () => {
  it("adds a manual review reminder when a source-backed status is changed", () => {
    const result = refreshSourceSummaryReviewIssue(day({ systemMorning: "出勤", finalMorning: "调休" }), "张三");

    expect(result.issues).toEqual([
      {
        level: "info",
        employee: "张三",
        day: 1,
        message: SOURCE_SUMMARY_REVIEW_MESSAGE,
        rawValue: "",
      },
    ]);
  });

  it("removes the reminder when the source-backed status change is reverted", () => {
    const result = refreshSourceSummaryReviewIssue(
      day({
        systemMorning: "出勤",
        finalMorning: "出勤",
        issues: [{ level: "info", employee: "张三", day: 1, message: SOURCE_SUMMARY_REVIEW_MESSAGE, rawValue: "" }],
      }),
      "张三",
    );

    expect(result.issues).toEqual([]);
  });
});
