import { describe, expect, it } from "vitest";
import { statusStyleClass, statusStyleKey } from "./statusStyles";

describe("status style mapping", () => {
  it("matches the Excel status style groups", () => {
    expect(statusStyleKey("")).toBe("blank");
    expect(statusStyleKey("出勤")).toBe("normal");
    expect(statusStyleKey("休息")).toBe("normal");
    expect(statusStyleKey("带薪")).toBe("normal");
    expect(statusStyleKey("迟到")).toBe("late");
    expect(statusStyleKey("缺卡")).toBe("missing-card");
    expect(statusStyleKey("事假")).toBe("personal-leave");
    expect(statusStyleKey("早退")).toBe("early-leave");
    expect(statusStyleKey("异常")).toBe("abnormal");
    expect(statusStyleKey("旷工")).toBe("absence");
    expect(statusStyleKey("调休")).toBe("normal");
    expect(statusStyleKey("病假")).toBe("benefit-leave");
    expect(statusStyleKey("工伤")).toBe("benefit-leave");
    expect(statusStyleKey("丧假")).toBe("benefit-leave");
    expect(statusStyleKey("婚假")).toBe("benefit-leave");
    expect(statusStyleKey("产假")).toBe("benefit-leave");
    expect(statusStyleKey("年假")).toBe("benefit-leave");
    expect(statusStyleKey("加班")).toBe("normal");
  });

  it("builds stable CSS class names", () => {
    expect(statusStyleClass("迟到")).toBe("status-late");
    expect(statusStyleClass("年假")).toBe("status-benefit-leave");
    expect(statusStyleClass("病假")).toBe("status-benefit-leave");
    expect(statusStyleClass("调休")).toBe("status-normal");
  });
});
