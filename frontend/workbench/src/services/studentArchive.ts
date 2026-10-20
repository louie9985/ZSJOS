import { api, http, unwrap } from "./api";
import type { StudentInfoDetail, StudentInfoImage } from "./studentInfo";
export interface StudentArchive {
  personId: number;
  serviceRelationId: number;
  registrationCaseId?: number;
  orderItemId?: number;
  productName?: string;
  revision: number;
  canMaintain: boolean;
  information: StudentInfoDetail;
  history: {
    revision: number;
    operatorUserId: number;
    operatorName?: string;
    modifiedAt: string;
    reason: string;
  }[];
}
export interface ArchiveEdit {
  revision: number;
  values: Record<string, unknown>;
  options: Record<string, { value: string; label: string }[]>;
  images: StudentInfoImage[];
}
const base = (id: number) => `/zsjos/student/service/${id}/archive`;
export const studentArchiveApi = {
  detail: async (id: number, sensitive = false) =>
    unwrap<StudentArchive>(
      await http.get(base(id) + (sensitive ? "/sensitive" : "")),
    ),
  edit: async (id: number) =>
    unwrap<ArchiveEdit>(await http.get(base(id) + "/edit")),
  save: async (
    id: number,
    revision: number,
    changes: Record<string, unknown>,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<number>(
      await http.post(base(id), { revision, changes, reason, idempotencyKey }),
    ),
  areas: () => api.areaTree(),
  export: async (id: number) => {
    const r = await http.get(base(id) + "/export", { responseType: "blob" });
    if (r.data.type.includes("json")) throw Error("导出失败或没有权限");
    const url = URL.createObjectURL(r.data);
    const a = document.createElement("a");
    a.href = url;
    a.download = "学员档案.xlsx";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  upload: async (id: number, fieldKey: string, side: string, file: File) => {
    const body = new FormData();
    body.append("fieldKey", fieldKey);
    body.append("side", side);
    body.append("file", file);
    return unwrap<StudentInfoImage>(
      await http.post(base(id) + "/images", body),
    );
  },
  image: async (id: number, image: StudentInfoImage) => {
    const r = await http.get(base(id) + "/images/" + image.id, {
      responseType: "blob",
    });
    if (r.data.type.includes("json")) throw Error("图片读取失败或没有权限");
    return URL.createObjectURL(new Blob([r.data], { type: image.contentType }));
  },
};
