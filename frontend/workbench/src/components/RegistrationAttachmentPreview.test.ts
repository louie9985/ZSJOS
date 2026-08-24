import { describe, expect, it } from "vitest";
import { registrationAttachmentPreviewKind } from "./RegistrationAttachmentPreview";

describe("registration attachment preview", () => {
  it.each([
    ["image/png", "proof.bin", "image"],
    [undefined, "proof.webp", "image"],
    ["application/pdf", "proof.bin", "pdf"],
    [undefined, "proof.PDF", "pdf"],
    ["application/vnd.openxmlformats-officedocument.wordprocessingml.document", "proof.docx", "unsupported"],
    ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "proof.xlsx", "unsupported"],
  ])("classifies %s %s as %s", (contentType, originalName, expected) => {
    expect(registrationAttachmentPreviewKind({ contentType, originalName })).toBe(expected);
  });
});
