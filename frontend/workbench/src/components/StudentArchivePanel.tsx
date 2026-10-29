import { useEffect, useRef, useState } from "react";
import StudentArchiveExtraPanel from './StudentArchiveExtraPanel';
import { useStudentArchiveEditor } from './StudentArchiveEditGuard';
import {
  Alert,
  Button,
  Card,
  Cascader,
  Collapse,
  Empty,
  Input,
  Select,
  Space,
  Spin,
  Tag,
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
import AttachmentCard, { type AttachmentResource } from './AttachmentCard';
import { formatTimestamp } from '../services/time';
import "../styles/pages/student-archive.css";
import '../styles/components/student-info-panel.css';

const groupTitles: Record<string, string> = {
  basic: '基本信息', education: '学历与报考', career: '职业与学习目的', materials: '证明材料',
};

export default function StudentArchivePanel({
  relationId,
}: {
  relationId: number;
}) {
  return <StudentArchivePanelContent key={relationId} relationId={relationId} />;
}

function StudentArchivePanelContent({ relationId }: { relationId: number }) {
  const [data, setData] = useState<StudentArchive>(),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [editor, setEditor] = useState<ArchiveEdit>(),
    [editingField, setEditingField] = useState<string>();
  const [areas, setAreas] = useState<
      Awaited<ReturnType<typeof studentArchiveApi.areas>>
    >([]),
    [changed, setChanged] = useState<Record<string, unknown>>({}),
    [images, setImages] = useState<StudentInfoImage[]>([]);
  const sequence = useRef(0),
    key = useRef(""),
    pendingUploads = useRef(0);
  useStudentArchiveEditor(Boolean(editor) && Object.keys(changed).length > 0, Boolean(editor) && busy);
  const load = async () => {
    const n = ++sequence.current;
    setBusy(true);
    setError("");
    setData(undefined);
    try {
      let d = await studentArchiveApi.detail(relationId);
      if (n !== sequence.current) return;
      // Resolve disclosure from the server's permission projection before showing the record.
      if (d.information.status === 'SUBMITTED' && d.information.canReadSensitive) {
        d = await studentArchiveApi.detail(relationId, true);
      }
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
    setChanged({});
    setImages([]);
    pendingUploads.current = 0;
    void load();
    return () => {
      sequence.current++;
    };
  }, [relationId]);
  const edit = async (fieldKey: string) => {
    if (busy || editingField) return;
    const generation = sequence.current;
    setBusy(true);
    setError("");
    try {
      const [e, a] = await Promise.all([
        studentArchiveApi.edit(relationId),
        studentArchiveApi.areas(),
      ]);
      if (generation !== sequence.current) return;
      setEditor(e);
      setAreas(a);
      setChanged({});
      setImages(e.images);
      setEditingField(fieldKey);
      key.current = crypto.randomUUID();
    } catch (e) {
      if (generation === sequence.current) setError(studentInfoError(e));
    } finally {
      if (generation === sequence.current) setBusy(false);
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
  const attachment = (image: StudentInfoImage): AttachmentResource => {
    const generation = sequence.current;
    const metadata = { name: image.name, type: image.contentType, size: image.size };
    const loadContent = async (): Promise<AttachmentResource> => {
      if (generation !== sequence.current) throw new Error('档案已切换，请重新查看附件');
      const url = await studentArchiveApi.image(relationId, image);
      if (generation !== sequence.current) {
        URL.revokeObjectURL(url);
        throw new Error('档案已切换，请重新查看附件');
      }
      return { ...metadata, url, loadContent };
    };
    return { ...metadata, loadContent };
  };
  const save = async () => {
    if (!editor || busy || !Object.keys(changed).length) return;
    const generation = sequence.current;
    setBusy(true);
    setError("");
    try {
      await studentArchiveApi.save(
        relationId,
        editor.revision,
        changed,
        "",
        key.current,
      );
      if (generation !== sequence.current) return;
      setEditor(undefined);
      setEditingField(undefined);
      await load();
    } catch (e) {
      if (generation === sequence.current) setError(studentInfoError(e));
    } finally {
      if (generation === sequence.current) setBusy(false);
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
            <div className="archive-edit-attachment" key={i.id}>
              <AttachmentCard name={i.name} load={async () => attachment(i)} />
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
            </div>
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
                  const generation = sequence.current;
                  pendingUploads.current++;
                  setBusy(true);
                  setError("");
                  void studentArchiveApi
                    .upload(relationId, f.key, side, file)
                    .then((i) => {
                      if (generation !== sequence.current) return;
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
                    .catch((e) => {
                      if (generation === sequence.current) setError(studentInfoError(e));
                    })
                    .finally(() => {
                      if (generation === sequence.current) {
                        pendingUploads.current--;
                        setBusy(pendingUploads.current > 0);
                      }
                    });
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
          autoFocus
          aria-label={f.label}
          disabled={locked || busy}
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
          autoFocus
          aria-label={f.label}
          disabled={locked || busy}
          value={value as number[]}
          options={areas}
          fieldNames={{ value: "id", label: "name", children: "children" }}
          onChange={(v) => change(f.key, v || [])}
        />
      );
    const props = {
      "aria-label": f.label,
      autoFocus: true,
      disabled: locked || busy,
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
  const groups = new Map<string, StudentInfoField[]>();
  for (const field of [...(data?.information.fields || [])].sort((a, b) => a.sort - b.sort)) {
    groups.set(field.group, [...(groups.get(field.group) || []), field]);
  }
  return (
    <section className="lead-detail-tab-content student-archive-section">
      <Card className="student-archive-card" title={<div className="archive-card-title"><span><small>01 · 报名资料</small><strong>报名收集资料</strong><em>提交快照保留，字段可逐项维护</em></span></div>}>
      {data?.information.canExport && <Space wrap>
          <Button
            disabled={busy || !!editor}
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
      </Space>}
      {error && (
        <Alert
          type="error"
          title={error}
          action={!editor && <Button disabled={busy} onClick={() => void load()}>重试</Button>}
        />
      )}
      {busy && !data ? (
        <Spin />
      ) : data?.information.status !== "SUBMITTED" ? (
        !error && <Empty description="暂无已提交的收集资料" />
      ) : (
        <>
          <div className="student-info-panel archive-registration-content">
            <div className="student-info-layout">
              <div className="student-info-main">{[...groups].map(([group, fields]) => <section className="student-info-section" key={group}>
                <h4>{groupTitles[group] || '其他资料'}</h4>
                <dl className="student-info-fields">{fields.map(f => {
                  const value = data.information.values[f.key];
                  const isEditing = Boolean(editor && editingField === f.key);
                  const files = data.information.images.filter(image => image.fieldKey === f.key);
                  const wide = isEditing || f.type === 'images' || f.type === 'textarea' || (value?.length || 0) > 120;
                  return <div key={f.key} className={`student-info-field archive-registration-field${wide ? ' is-wide' : ''}`}>
                    <dt><span className="archive-description-label">{f.label}{data.canMaintain && !["age", "gender"].includes(f.key) && (!(f.sensitive || f.type === "images") || data.information.canReadSensitive) && <Button className="archive-field-edit" type="text" size="small" icon={<EditOutlined />} aria-label={`修改${f.label}`} disabled={busy || !!editor} onClick={() => void edit(f.key)} />}</span></dt>
                    <dd>{isEditing ? <div className="archive-inline-editor" role="group" aria-label={`编辑${f.label}`}>
                      {control(f)}
                      <Space className="archive-inline-actions" wrap>
                        <Button type="primary" size="small" loading={busy} disabled={busy || !Object.keys(changed).length} onClick={() => void save()}>保存</Button>
                        <Button size="small" disabled={busy} onClick={() => { setEditor(undefined); setEditingField(undefined); setChanged({}); setError(""); }}>取消</Button>
                      </Space>
                    </div> : f.type === 'images' ? files.length ? <>
                      <div className="student-info-attachments">{files.map(image => data.information.canReadSensitive
                        ? <AttachmentCard key={`${relationId}:${data.revision}:${image.id}`} name={image.name} load={async () => attachment(image)} />
                        : <span key={image.id}>{image.name}</span>)}</div>
                      {!data.information.canReadSensitive && <span className="archive-sensitive-hint">暂无材料附件查看权限</span>}
                    </> : <span className="archive-empty">未提交附件</span>
                      : value == null || value === '' ? <span className="archive-empty">未填写</span>
                        : f.type === 'dict' || f.type === 'area' ? <Tag color="cyan" className="student-info-option">{value}</Tag> : value}
                      {f.note && <div className="student-info-field-note">{f.note}</div>}
                    </dd>
                  </div>;
                })}</dl>
              </section>)}</div>
              <aside className="student-info-aside" aria-label="报名档案信息"><section className="student-info-section">
                <h4>档案信息</h4>
                <dl className="student-info-fields">
                  <div className="student-info-field"><dt>报名课程</dt><dd>{data.productName || '未记录'}</dd></div>
                  <div className="student-info-field"><dt>提交时间</dt><dd>{formatTimestamp(data.information.submittedAt)}<div className="student-info-field-note">北京时间</div></dd></div>
                  <div className="student-info-field"><dt>表单版本</dt><dd>{data.information.configVersion == null ? '未记录' : `V${data.information.configVersion}`}</dd></div>
                  <div className="student-info-field"><dt>维护版本</dt><dd>{data.revision}</dd></div>
                </dl>
                {!data.information.canReadSensitive && <p className="archive-sensitive-hint">当前账号无敏感信息查看权限，相关内容由服务端脱敏。</p>}
              </section></aside>
            </div>
          </div>
          <Collapse
            items={[
              {
                key: "history",
                label: "维护记录",
                children: data.history.length
                  ? data.history.map((h) => (
                      <p key={h.revision}>
                        第 {h.revision} 次修改 · {formatTimestamp(h.modifiedAt)} · {h.operatorName}
                        {h.reason && <> · {h.reason}</>}
                      </p>
                    ))
                  : "暂无维护记录",
              },
            ]}
          />
        </>
      )}
      </Card>
      <StudentArchiveExtraPanel key={relationId} relationId={relationId} />
    </section>
  );
}
