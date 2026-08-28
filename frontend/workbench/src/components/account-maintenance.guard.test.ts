import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { canViewAccountHistory } from "./AccountMaintenancePanel";

describe("media account maintenance panel", () => {
  it("loads all four authoritative dictionaries and persists values rather than labels", () => {
    const source = readFileSync(
      "src/components/AccountMaintenancePanel.tsx",
      "utf8",
    );
    expect(source).toContain("MEDIA_ACCOUNT_CURRENT_STATUS");
    expect(source).toContain("MEDIA_ACCOUNT_STAGE");
    expect(source).toContain("MEDIA_ACCOUNT_PRIMARY_PROBLEM");
    expect(source).toContain("MEDIA_ACCOUNT_EXECUTION_MEASURE");
    expect(source).toContain("primaryProblemValues");
    expect(source).not.toContain("currentStatusLabelSnapshot: values");
  });

  it("exposes only immutable maintenance history without retired legacy-stage records", () => {
    const source = readFileSync(
      "src/components/AccountMaintenancePanel.tsx",
      "utf8",
    );
    const api = readFileSync("src/services/api.ts", "utf8");
    expect(source).toContain("maintenanceHistory");
    expect(source).not.toContain("legacyStageHistory");
    expect(source).not.toContain("原阶段记录");
    expect(api).not.toContain("MediaAccountLegacyStage");
    expect(api).not.toContain("legacy-stage-history");
    expect(source).not.toContain("恢复此版本");
    expect(source).not.toContain("删除版本");
  });

  it("loads history only when the server projects account-history access", () => {
    expect(
      canViewAccountHistory({
        availableActions: ["VIEW_ACCOUNT_HISTORY"],
      } as never),
    ).toBe(true);
    expect(
      canViewAccountHistory({
        availableActions: ["MAINTAIN_ACCOUNT"],
      } as never),
    ).toBe(false);
    expect(canViewAccountHistory(undefined)).toBe(false);

    const source = readFileSync(
      "src/components/AccountMaintenancePanel.tsx",
      "utf8",
    );
    expect(source).toContain("if (!account || !canViewHistory) return");
    expect(source).toContain("if (account && canViewHistory) void loadHistory");
    expect(source).toContain("{canViewHistory && (");
    expect(source).toContain('className="media-account-history-tabs"');
  });

  it("does not expose the retired ordered stage transition contract", () => {
    const page = readFileSync("src/pages/MediaFeaturePage.tsx", "utf8");
    const api = readFileSync("src/services/api.ts", "utf8");
    expect(page).not.toContain("推进阶段");
    expect(page).not.toContain("回退阶段");
    expect(page).not.toContain("判断依据");
    expect(page).not.toContain("advanceStage");
    expect(page).not.toContain("rollbackStage");
    expect(api).not.toContain("/advance-stage");
    expect(api).not.toContain("/rollback-stage");
    expect(api).toContain("maintain: async");
  });
});
