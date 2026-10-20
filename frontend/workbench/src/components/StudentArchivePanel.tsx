import { useEffect, useRef, useState } from "react";
import StudentArchiveExtraPanel from './StudentArchiveExtraPanel';
import {
  Alert,
  Button,
  Card,
  Cascader,
  Collapse,
  Descriptions,
  Empty,
  Form,
  Image,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Upload,
} from "antd";
import { EditOutlined } from "@ant-design/icons";
import {
  studentArchiveApi,
  type StudentArchive,
  type ArchiveEdit,
} from "../services/studentArchive";
import {
  studentInfoError,
  type StudentInfoField,
  type StudentInfoImage,
} from "../services/studentInfo";
import "../styles/pages/student-archive.css";

export default function StudentArchivePanel({
  relationId,
}: {
  relationId: number;
}) {
  const [data, setData] = useState<StudentArchive>(),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [editor, setEditor] = useState<ArchiveEdit>(),
    [reason, setReason] = useState(""),
    [editingField, setEditingField] = useState<string>();
  const [areas, setAreas] = useState<
      Awaited<ReturnType<typeof studentArchiveApi.areas>>
    >([]),
    [changed, setChanged] = useState<Record<string, unknown>>({}),
    [images, setImages] = useState<StudentInfoImage[]>([]),
    [urls, setUrls] = useState<Record<number, string>>({});
  const sequence = useRef(0),
    key = useRef("");
  const load = async (sensitive = false) => {
    const n = ++sequence.current;
    setBusy(true);
    setError("");
    try {
      const d = await studentArchiveApi.detail(relationId, sensitive);
      if (n === sequence.current) setData(d);
    } catch (e) {
      if (n === sequence.current) {
        setData(undefined);
        setError(studentInfoError(e));
      }
    } finally {
      if (n === sequence.current) setBusy(false);
    }
  };
  useEffect(() => {
    setData(undefined);
    setEditor(undefined);
    setEditingField(undefined);
    setUrls({});
    void load();
    return () => {
      sequence.current++;
    };
  }, [relationId]);
  useEffect(
    () => () => {
      Object.values(urls).forEach(URL.revokeObjectURL);
    },
    [urls],
  );
  const edit = async (fieldKey?: string) => {
    setBusy(true);
    setError("");
    try {
      const [e, a] = await Promise.all([
        studentArchiveApi.edit(relationId),
        studentArchiveApi.areas(),
      ]);
      setEditor(e);
      setAreas(a);
      setChanged({});
      setImages(e.images);
      setReason("");
      setEditingField(fieldKey);
      key.current = crypto.randomUUID();
    } catch (e) {
      setError(studentInfoError(e));
    } finally {
      setBusy(false);
    }
  };
  const change = (field: string, value: unknown) => {
    setChanged((c) => ({ ...c, [field]: value }));
    key.current = crypto.randomUUID();
  };
  const selectImages = (field: string, next: StudentInfoImage[]) => {
    setImages((all) => [...all.filter((i) => i.fieldKey !== field), ...next]);
    change(
      field,
      next.map((i) => i.id),
    );
  };
  const preview = async () => {
    setBusy(true);
    try {
      const entries = await Promise.all(
        data!.information.images.map(
          async (i) =>
            [i.id, await studentArchiveApi.image(relationId, i)] as const,
        ),
      );
      setUrls(Object.fromEntries(entries));
    } catch (e) {
      setError(studentInfoError(e));
    } finally {
      setBusy(false);
    }
  };
  const save = async () => {
    if (!editor || !reason.trim()) return;
    setBusy(true);
    setError("");
    try {
      await studentArchiveApi.save(
        relationId,
        editor.revision,
        changed,
        reason,
        key.current,
      );
      setEditor(undefined);
      setEditingField(undefined);
      await load();
    } catch (e) {
      setError(studentInfoError(e));
    } finally {
      setBusy(false);
    }
  };
  const control = (f: StudentInfoField) => {
    const value = Object.hasOwn(changed, f.key)
      ? changed[f.key]
      : editor?.values[f.key];
    const locked =
      ((f.sensitive || f.type === "images") &&
        !data?.information.canReadSensitive) ||
      ["age", "gender"].includes(f.key);
    if (f.type === "images") {
      const selected = images.filter((i) => i.fieldKey === f.key);
      return (
        <Space orientation="vertical">
          {selected.map((i) => (
            <Space key={i.id}>
              <span>
                {i.side === "front"
                  ? "身份证正面"
                  : i.side === "back"
                    ? "身份证反面"
                    : i.name}
              </span>
              <Button
                disabled={locked || busy}
                onClick={() =>
                  selectImages(
                    f.key,
                    selected.filter((x) => x.id !== i.id),
                  )
                }
              >
                移除
              </Button>
            </Space>
          ))}
          {(f.key === "identity_images" ? ["front", "back"] : [""]).map(
            (side) => (
              <Upload
                key={side}
                accept={(f.allowedExtensions || ["jpg", "jpeg", "png"]).map(e => "." + e).join(",")}
                multiple={!side}
                showUploadList={false}
                disabled={
                  locked ||
                  busy ||
                  (side
                    ? selected.some((i) => i.side === side)
                    : selected.length >= f.maxImages!)
                }
                beforeUpload={(file) => {
                  if (
                    !(f.allowedExtensions || ["jpg", "jpeg", "png"]).includes(file.name.split(".").pop()?.toLowerCase() || "") ||
                    file.size > f.maxSizeMb! * 1024 * 1024
                  ) {
                    setError("请选择符合字段格式和大小要求的附件");
                    return false;
                  }
                  setBusy(true);
                  void studentArchiveApi
                    .upload(relationId, f.key, side, file)
                    .then((i) => {
                      setImages((all) => {
                        const next = [...all, i];
                        setChanged((c) => ({
                          ...c,
                          [f.key]: next
                            .filter((x) => x.fieldKey === f.key)
                            .map((x) => x.id),
                        }));
                        return next;
                      });
                      key.current = crypto.randomUUID();
                    })
                    .catch((e) => setError(studentInfoError(e)))
                    .finally(() => setBusy(false));
                  return false;
                }}
              >
                <Button>
                  {side === "front"
                    ? "上传正面"
                    : side === "back"
                      ? "上传反面"
                      : "上传附件"}
                </Button>
              </Upload>
            ),
          )}
        </Space>
      );
    }
    if (f.type === "dict")
      return (
        <Select
          allowClear
          disabled={locked}
          value={value as string}
          options={(editor?.options[f.key] || []).map((o) => ({
            value: o.value,
            label: o.label,
          }))}
          onChange={(v) => change(f.key, v ?? "")}
        />
      );
    if (f.type === "area")
      return (
        <Cascader
          disabled={locked}
          value={value as number[]}
          options={areas}
          fieldNames={{ value: "id", label: "name", children: "children" }}
          onChange={(v) => change(f.key, v || [])}
        />
      );
    const props = {
      disabled: locked,
      value: typeof value === "string" ? value : "",
      onChange: (
        e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
      ) => change(f.key, e.target.value),
    };
    return f.type === "textarea" ? (
      <Input.TextArea {...props} rows={3} />
    ) : (
      <Input {...props} />
    );
  };
  return (
    <section className="lead-detail-tab-content student-archive-section">
      <Card className="student-archive-card" title={<div className="archive-card-title"><span><small>01 · 报名资料</small><strong>报名收集资料</strong><em>提交快照保留，字段可逐项维护</em></span><Button size="small" loading={busy} onClick={() => void load()}>刷新</Button></div>}>
      <Space wrap>
        {data?.information.canExport && (
          <Button
            disabled={busy}
            onClick={() => {
              setBusy(true);
              void studentArchiveApi
                .export(relationId)
                .catch((e) => setError(studentInfoError(e)))
                .finally(() => setBusy(false));
            }}
          >
            导出学员档案
          </Button>
        )}
        {data?.information.status === "SUBMITTED" &&
          data.information.canReadSensitive && (
            <>
              <Button disabled={busy} onClick={() => void load(true)}>
                查看完整敏感信息
              </Button>
              <Button disabled={busy} onClick={() => void preview()}>
                查看材料附件
              </Button>
            </>
          )}
      </Space>
      {error && (
        <Alert
          type="error"
          title={error}
          action={<Button onClick={() => void load()}>重试</Button>}
        />
      )}
      {busy && !editor ? (
        <Spin />
      ) : data?.information.status !== "SUBMITTED" ? (
        <Empty description="暂无已提交的收集资料" />
      ) : (
        <>
          <p>{data.productName} · 本次报名档案</p>
          <Descriptions
            column={{ xs: 1, sm: 2 }}
            items={data.information.fields.map((f) => ({
              key: f.key,
              label: <span className="archive-description-label">{f.label}{data.canMaintain && <Button className="archive-field-edit" type="text" size="small" icon={<EditOutlined />} aria-label={`修改${f.label}`} onClick={() => void edit(f.key)} />}</span>,
              children:
                f.type === "images" ? (
                  <Image.PreviewGroup>
                    {data.information.images
                      .filter((i) => i.fieldKey === f.key)
                      .map((i) =>
                        urls[i.id] ? (
                          <span key={i.id}>{i.contentType.startsWith("image/") ? <Image src={urls[i.id]} width={80} /> : <a href={urls[i.id]} download={i.name}>{i.name}</a>}</span>
                        ) : (
                          <span key={i.id}>已上传附件 </span>
                        ),
                      )}
                  </Image.PreviewGroup>
                ) : (
                  data.information.values[f.key] || "—"
                ),
            }))}
          />
          <Collapse
            items={[
              {
                key: "history",
                label: "维护记录",
                children: data.history.length
                  ? data.history.map((h) => (
                      <p key={h.revision}>
                        第 {h.revision} 次修改 · {h.modifiedAt} · {h.operatorName} ·{" "}
                        {h.reason}
                      </p>
                    ))
                  : "暂无维护记录",
              },
            ]}
          />
        </>
      )}
      <Modal
        open={!!editor}
        title={`修改${data?.information.fields.find(f => f.key === editingField)?.label || "报名信息"}`}
        width={760}
        onCancel={() => {
          if (!busy) { setEditor(undefined); setEditingField(undefined); }
        }}
        onOk={() => void save()}
        confirmLoading={busy}
        okButtonProps={{
          disabled: !reason.trim() || !Object.keys(changed).length,
        }}
        destroyOnHidden
      >
        <p>
          原始提交保留，保存后生成新档案版本。未修改的字典和地区保留原标签。
        </p>
        <Form layout="vertical">
          {data?.information.fields.filter((f) => !editingField || f.key === editingField).map((f) => (
            <Form.Item key={f.key} label={f.label} required={f.required}>
              {control(f)}
            </Form.Item>
          ))}
          <Form.Item label="维护原因" required>
            <Input.TextArea
              value={reason}
              maxLength={500}
              onChange={(e) => {
                setReason(e.target.value);
                key.current = crypto.randomUUID();
              }}
            />
          </Form.Item>
        </Form>
        {error && <Alert type="error" title={error} />}
      </Modal>
      </Card>
      <StudentArchiveExtraPanel key={relationId} relationId={relationId} />
    </section>
  );
}
