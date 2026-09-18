import { describe, expect, it } from "vitest";
import {
  fieldEmpty,
  formatAccountMetric,
  profileChanges,
  profileMissing,
  type ProfileField,
} from "./mediaAccountProfile";
const field = (
  key: string,
  ownerType: ProfileField["ownerType"],
  type: ProfileField["type"] = "text",
): ProfileField => ({
  key,
  label: key,
  ownerType,
  type,
  group: "PROFILE",
  enabled: true,
  required: false,
  requiredForComplete: true,
  sourceType: "MANUAL",
  snapshotPolicy: "ON_SELECTION",
  searchable: false,
  sort: 1,
});
describe("account profile responsibilities", () => {
  it('formats live partner totals without treating zero as missing', () => {
    expect(formatAccountMetric('total_leads', 0)).toBe('0');
    expect(formatAccountMetric('month_conversion', 0.25)).toBe('25.00%');
    expect(formatAccountMetric('total_amount', '1200.5')).toBe('¥1,200.50');
    expect(formatAccountMetric('month_amount', null)).toBeUndefined();
  });
  it("counts configured missing manual fields even before the first snapshot", () => {
    expect(
      profileMissing(
        [
          field("goal", "DIRECTOR"),
          field("name", "OPERATOR"),
          field("status", "AUTO"),
          field("cover", "UNASSIGNED"),
          field("review", "DIRECTOR", "record"),
        ],
        {},
      ).map((f) => f.key),
    ).toEqual(["goal", "name"]);
  });
  it("only submits changed editable fields, including an explicit clear", () => {
    expect(
      profileChanges(
        [
          field("goal", "DIRECTOR"),
          field("name", "OPERATOR"),
          field("status", "AUTO"),
        ],
        ["goal"],
        { goal: "before", name: "keep" },
        { goal: "", name: "forged", status: "forged" },
      ),
    ).toEqual({ goal: null });
    expect(
      profileChanges(
        [field("name", "OPERATOR")],
        ["name"],
        { name: "same" },
        { name: "same" },
      ),
    ).toEqual({});
  });
  it("false and zero are complete and disabled fields are excluded", () => {
    expect(fieldEmpty(false)).toBe(false);
    expect(fieldEmpty(0)).toBe(false);
    expect(
      profileMissing(
        [
          { ...field("hidden", "DIRECTOR"), enabled: false },
          field("goal", "DIRECTOR"),
        ],
        { goal: "complete" },
      ),
    ).toEqual([]);
  });
});
