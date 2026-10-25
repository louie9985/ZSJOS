import { readFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { parseBrowserSourceFile } from "./browserSourceParser";

describe("parseBrowserSourceFile", () => {
  it("parses the sample WeCom attendance workbook from a File object", async () => {
    const sourcePath = resolve("..", "上下班打卡_月报_20260501-20260531 (1).xlsx");
    const bytes = await readFile(sourcePath);
    const file = new File([bytes], basename(sourcePath), {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      lastModified: 0,
    });

    const { attendance } = await parseBrowserSourceFile(file);

    expect(attendance.year).toBe(2026);
    expect(attendance.month).toBe(5);
    expect(attendance.employees).toHaveLength(39);
  });
});
