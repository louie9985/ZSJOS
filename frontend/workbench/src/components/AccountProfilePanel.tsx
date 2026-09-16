import {
  EditOutlined,
  FileImageOutlined,
  InfoCircleOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Checkbox,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Progress,
  Select,
  Skeleton,
  Space,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from "antd";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { api, type DictData, type MediaStudentDetail } from "../services/api";
import {
  accountProfileApi,
  fieldEmpty,
  POSITIONING_SYNC_FIELDS,
  profileChanges,
  profileMissing,
  profileSection,
  type AccountProfile,
  type ProfileEntry,
  type ProfileField,
  type ProfileFile,
  type DiagnosisRequest,
} from "../services/mediaAccountProfile";
import { formatTimestamp } from "../services/time";

type Account = MediaStudentDetail["accounts"][number];
const owners = {
  AUTO: "自动生成",
  DIRECTOR: "编导填写",
  OPERATOR: "运营填写",
  UNASSIGNED: "责任待配置",
};
const groups = [
  ["POSITIONING", "账号定位卡"],
  ["STATUS", "账号状态"],
  ["REVIEW", "账号复盘记录"],
] as const;
const errorText = (cause: unknown) =>
  cause instanceof Error ? cause.message : "请求失败，请重试";
export const canViewAccountHistory = (account?: Account) =>
  Boolean(account?.availableActions.includes("VIEW_ACCOUNT_HISTORY"));
const safeLink = (url?: string) => {
  try {
    return url && ["http:", "https:"].includes(new URL(url).protocol)
      ? url
      : undefined;
  } catch {
    return undefined;
  }
};

export default function AccountProfilePanel({
  account,
  canQuery,
  initiallyEditing = false,
  onEditingFinished,
  onSaved,
  onMissingChange,
  deliveryActions,
}: {
  account?: Account;
  deliveryActions?: ReactNode;
  canQuery: boolean;
  canMaintain: boolean;
  initiallyEditing?: boolean;
  onEditingFinished?: () => void;
  onSaved: () => Promise<void>;
  onMissingChange?: (id: number, count: number) => void;
}) {
  const { message, modal } = App.useApp();
  const [profile, setProfile] = useState<AccountProfile>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [open, setOpen] = useState(false),
    [values, setValues] = useState<Record<string, unknown>>({}),
    [files, setFiles] = useState<Record<string, ProfileFile>>({});
  const [diagnosisOpen, setDiagnosisOpen] = useState(false), [diagnosisType, setDiagnosisType] = useState<DiagnosisRequest["templateType"]>("diagnosis_7d");
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({}),
    [dictError, setDictError] = useState(""),
    [dictLoading, setDictLoading] = useState(false);
  const [saving, setSaving] = useState(false),
    [uploading, setUploading] = useState(false),
    [onlyMissing, setOnlyMissing] = useState(false),
    [showMissing, setShowMissing] = useState(false),
    [missingOwner, setMissingOwner] = useState("");
  const [history, setHistory] = useState<ProfileEntry[]>([]),
    [total, setTotal] = useState(0),
    [page, setPage] = useState(1),
    [historyError, setHistoryError] = useState(""),
    [historyLoading, setHistoryLoading] = useState(false);
  const [record, setRecord] = useState<ProfileField>(),
    [content, setContent] = useState(""),
    [recordFiles, setRecordFiles] = useState<ProfileFile[]>([]);
  const pending = useRef<{ fingerprint: string; key: string } | undefined>(
      undefined,
    ),
    generation = useRef(0),
    body = useRef<HTMLDivElement>(null);
  const fields = profile?.config.fields.filter((f) => f.enabled) || [],
    editable = profile?.editableFields || [];
  const missing = profileMissing(fields, open ? values : profile?.values || {});
  const required = fields.filter(
    (f) =>
      f.requiredForComplete &&
      ["DIRECTOR", "OPERATOR"].includes(f.ownerType) &&
      !POSITIONING_SYNC_FIELDS.has(f.key) &&
      f.type !== "record",
  ).length;
  const changes = profile
    ? profileChanges(fields, editable, profile.values, values)
    : {};
  const load = async () => {
    if (!account || !canQuery) return;
    const gen = generation.current;
    setLoading(true);
    setError("");
    try {
      const r = await accountProfileApi.get(account.id);
      if (gen === generation.current) {
        setProfile(r);
        setValues(r.values);
        setFiles(r.files);
      }
    } catch (cause) {
      if (gen === generation.current) setError(errorText(cause));
    } finally {
      if (gen === generation.current) setLoading(false);
    }
  };
  const loadHistory = async (p = 1) => {
    if (!account || !profile?.canViewHistory) return;
    const gen = generation.current;
    setHistoryLoading(true);
    setHistoryError("");
    try {
      const r = await accountProfileApi.history(account.id, p);
      if (gen === generation.current) {
        setHistory(r.list);
        setTotal(r.total);
        setPage(p);
      }
    } catch (cause) {
      if (gen === generation.current) setHistoryError(errorText(cause));
    } finally {
      if (gen === generation.current) setHistoryLoading(false);
    }
  };
  useEffect(() => {
    if (account && profile) onMissingChange?.(account.id, missing.length);
  }, [account?.id, Boolean(profile), missing.length, onMissingChange]);
  const loadDicts = async (p: AccountProfile) => {
    const gen = generation.current;
    setDictLoading(true);
    setDictError("");
    try {
      const types = [
        ...new Set(
          p.config.fields
            .filter((f) => f.enabled && f.dictType)
            .map((f) => f.dictType!),
        ),
        "zsjos_media_account_stage", "zsjos_media_account_current_status", "zsjos_media_account_primary_problem", "zsjos_media_account_cooperation_level",
      ];
      const rows = await Promise.all(
        types.map(
          async (type) => [type, await api.dictDataByType(type)] as const,
        ),
      );
      if (gen === generation.current) setDicts(Object.fromEntries(rows));
    } catch (cause) {
      if (gen === generation.current) setDictError(errorText(cause));
    } finally {
      if (gen === generation.current) setDictLoading(false);
    }
  };
  useEffect(() => {
    generation.current++;
    setProfile(undefined);
    setOpen(false);
    setRecord(undefined);
    setHistory([]);
    pending.current = undefined;
    void load();
    return () => {
      generation.current++;
    };
    // Identity changes invalidate in-flight requests and account-specific drafts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [account?.id, canQuery]);
  useEffect(() => {
    if (!profile) return;
    void loadHistory();
    if (initiallyEditing && profile.editableFields.length) {
      setOpen(true);
      void loadDicts(profile);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile?.account.id]);
  const openEditor = (focus?: string) => {
    if (!profile || !editable.length) return;
    if (!open) {
      setValues(profile.values);
      setFiles(profile.files);
    }
    setOnlyMissing(false);
    setOpen(true);
    void loadDicts(profile);
    if (focus)
      window.setTimeout(() => {
        const el = body.current?.querySelector(`[data-profile-key="${focus}"]`);
        el?.scrollIntoView({ block: "center" });
        el?.querySelector<HTMLElement>("input,textarea,button")?.focus();
      }, 100);
  };
  const key = (data: unknown) => {
    const fingerprint = JSON.stringify(data);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = { fingerprint, key: crypto.randomUUID() };
    return pending.current!.key;
  };
  const save = async (close: boolean) => {
    if (!profile || !account || uploading || saving) return false;
    setSaving(true);
    try {
      const data = {
        version: profile.account.version,
        configVersionId: profile.config.id,
        changes,
      };
      await accountProfileApi.patch(account.id, {
        ...data,
        idempotencyKey: key(data),
      });
      pending.current = undefined;
      message.success("账号资料已保存");
      if (close) setOpen(false);
      await load();
      await loadHistory();
      await onSaved();
      if (close) onEditingFinished?.();
      return true;
    } catch (cause) {
      message.error(errorText(cause));
      return false;
    } finally {
      setSaving(false);
    }
  };
  const close = () => {
    if (saving || uploading) return;
    if (Object.keys(changes).length) {
      const dialog = modal.confirm({
        title: "有尚未保存的账号资料",
        content: "保存后仍可继续补充缺失字段。",
        okText: "保存并返回",
        cancelText: "继续填写",
        onOk: async () => {
          if (!(await save(true))) throw new Error("保存未完成");
        },
        footer: (_, { OkBtn, CancelBtn }) => (
          <Space>
            <Button
              onClick={() => {
                dialog.destroy();
                setOpen(false);
                onEditingFinished?.();
              }}
            >
              放弃本次修改
            </Button>
            <CancelBtn />
            <OkBtn />
          </Space>
        ),
      });
      return;
    }
    setOpen(false);
    onEditingFinished?.();
  };
  const upload = async (field: ProfileField, file: File, toRecord = false) => {
    if (!account || uploading || saving) return;
    setUploading(true);
    try {
      const r = await accountProfileApi.upload(account.id, field.key, file);
      if (toRecord) setRecordFiles((current) => [...current, r]);
      else if (field.type === "attachment") {
        setFiles((current) => ({ ...current, [String(r.id)]: r }));
        setValues((current) => ({
          ...current,
          [field.key]: [
            ...(Array.isArray(current[field.key])
              ? (current[field.key] as number[])
              : []),
            r.id,
          ],
        }));
      } else {
        setFiles((current) => ({ ...current, [field.key]: r }));
        setValues((current) => ({ ...current, [field.key]: r.id }));
      }
    } catch (cause) {
      message.error(errorText(cause));
    } finally {
      setUploading(false);
    }
  };
  const append = async () => {
    if (!record || !profile || !account || saving || uploading) return;
    setSaving(true);
    try {
      const data = {
        version: profile.account.version,
        configVersionId: profile.config.id,
        fieldKey: record.key,
        content,
        fileIds: recordFiles.map((f) => f.id),
      };
      await accountProfileApi.append(account.id, {
        ...data,
        idempotencyKey: key(data),
      });
      pending.current = undefined;
      setRecord(undefined);
      message.success("记录已追加");
      await load();
      await loadHistory();
      await onSaved();
    } catch (cause) {
      message.error(errorText(cause));
    } finally {
      setSaving(false);
    }
  };
  const display = (f: ProfileField) => {
    const v = profile?.values[f.key];
    if (fieldEmpty(v))
      return f.ownerType === "AUTO"
        ? "等待来源数据"
        : f.type === "record"
          ? "按次追加，详见下方记录"
          : "待补充";
    return f.ownerType === "AUTO"
      ? String(v)
      : profile?.snapshots.find((s) => s.key === f.key)?.displayValue ||
          String(v);
  };
  const tag = (f: ProfileField) => (
    <Tag className={`account-owner owner-${f.ownerType.toLowerCase()}`}>
      {owners[f.ownerType]}
    </Tag>
  );
  const fileLink = (f: ProfileFile) =>
    safeLink(f.previewUrl) ? (
      <a
        href={safeLink(f.previewUrl)}
        target="_blank"
        rel="noreferrer"
        download={f.name}
      >
        {f.name} · 查看/下载
      </a>
    ) : (
      <Typography.Text type="secondary">{f.name} · 暂不可预览</Typography.Text>
    );
  const recordButton = (f: ProfileField) =>
    editable.includes(f.key) ? (
      <Button
        size="small"
        disabled={
          saving || uploading || (open && Object.keys(changes).length > 0)
        }
        onClick={() => {
          setRecord(f);
          setContent("");
          setRecordFiles([]);
        }}
      >
        填写记录
      </Button>
      ) : null;
  const submitDiagnosis = async (values: Record<string, unknown>) => {
    if (!profile || !account) return;
    try {
      await accountProfileApi.diagnosis(account.id, { version: profile.account.version, configVersionId: profile.config.id,
        idempotencyKey: `diagnosis-${account.id}-${diagnosisType}-${Date.now()}`, templateType: diagnosisType, cycle: Number(values.cycle ?? 1),
        ...(values as Omit<DiagnosisRequest, "version" | "configVersionId" | "idempotencyKey" | "templateType" | "cycle">) });
      message.success("诊断已提交"); setDiagnosisOpen(false); await load(); await loadHistory();
    } catch (cause) { message.error(errorText(cause)); }
  };
  const control = (f: ProfileField) => {
    const allowed = editable.includes(f.key),
      value = values[f.key],
      disabled = !allowed || saving || uploading;
    const update = (v: unknown) =>
      setValues((current) => ({ ...current, [f.key]: v }));
    if (f.type === "record")
      return (
        <Space wrap>
          <Typography.Text type="secondary">
            按次追加，先保存资料修改
          </Typography.Text>
          {recordButton(f)}
        </Space>
      );
    if (f.type === "materials")
      return <div className="account-profile-readonly">{display(f)}</div>;
    if (!allowed)
      return <div className="account-profile-readonly">{display(f)}</div>;
    if (f.type === "attachment")
      return (
        <Space direction="vertical">
          {(Array.isArray(value) ? (value as number[]) : []).map((id) => (
            <Space key={id}>
              {files[String(id)] ? (
                fileLink(files[String(id)])
              ) : (
                <Typography.Text>附件暂不可用</Typography.Text>
              )}
              <Button
                disabled={disabled}
                onClick={() =>
                  update((value as number[]).filter((item) => item !== id))
                }
              >
                移除
              </Button>
            </Space>
          ))}
          <Upload
            accept=".pdf,.png,.jpg,.jpeg,.webp"
            showUploadList={false}
            disabled={disabled || (Array.isArray(value) && value.length >= 20)}
            beforeUpload={(file) => {
              void upload(f, file);
              return false;
            }}
          >
            <Button loading={uploading}>上传附件</Button>
          </Upload>
          <Typography.Text type="secondary">
            PDF 或图片，单份不超过20MB，最多20份
          </Typography.Text>
        </Space>
      );
    if (f.type === "image")
      return (
        <Space direction="vertical">
          {/* 主页图只在左栏展示一次，字段控制区不再重复预览。 */}
          {f.key !== "cover" && files[f.key] && (
            <Image width={90} src={safeLink(files[f.key].previewUrl)} />
          )}
          <Space>
            <Upload
              accept="image/png,image/jpeg,image/webp"
              showUploadList={false}
              beforeUpload={(file) => {
                void upload(f, file);
                return false;
              }}
              disabled={disabled || !fieldEmpty(value)}
            >
              <Button icon={<UploadOutlined />} disabled={disabled}>
                上传图片
              </Button>
            </Upload>
            {!fieldEmpty(value) && (
              <Button
                disabled={disabled}
                onClick={() => {
                  update(null);
                  setFiles((current) => {
                    const next = { ...current };
                    delete next[f.key];
                    return next;
                  });
                }}
              >
                移除
              </Button>
            )}
          </Space>
        </Space>
      );
    if (f.type === "select" || f.type === "multi_select") {
      const options: Array<{
        label: string;
        value: string;
        disabled?: boolean;
      }> = (dicts[f.dictType || ""] || []).map((d) => ({
        label: d.label,
        value: d.value,
      }));
      const snapshot = profile?.snapshots.find((s) => s.key === f.key),
        original = profile?.values[f.key];
      if (snapshot && original != null)
        for (const v of Array.isArray(original) ? original : [original])
          if (!options.some((o) => o.value === v))
            options.push({
              value: String(v),
              label: `${snapshot.displayValue || v}（历史选择）`,
              disabled: true,
            });
      return (
        <Select
          aria-label={f.label}
          allowClear
          mode={f.type === "multi_select" ? "multiple" : undefined}
          value={value as string | string[] | undefined}
          options={options}
          onChange={update}
          disabled={disabled || dictLoading || Boolean(dictError)}
          placeholder={
            options.length ? "请选择，可留空" : "暂无字典选项，请联系管理员配置"
          }
        />
      );
    }
    if (f.type === "number")
      return (
        <InputNumber
          aria-label={f.label}
          value={value as number | undefined}
          onChange={update}
          disabled={disabled}
        />
      );
    if (f.type === "boolean")
      return (
        <Select
          aria-label={f.label}
          value={value as boolean | undefined}
          allowClear
          options={[
            { value: true, label: "是" },
            { value: false, label: "否" },
          ]}
          onChange={update}
          disabled={disabled}
        />
      );
    if (f.type === "textarea")
      return (
        <Input.TextArea
          aria-label={f.label}
          value={String(value ?? "")}
          onChange={(e) => update(e.target.value)}
          rows={3}
          maxLength={2000}
          showCount
          disabled={disabled}
        />
      );
    return (
      <Input
        aria-label={f.label}
        value={String(value ?? "")}
        type={f.type === "date" ? "date" : "text"}
        onChange={(e) => update(e.target.value)}
        maxLength={["nickname", "uid"].includes(f.key) ? 255 : 2000}
        disabled={disabled}
        placeholder="待补充，可留空"
      />
    );
  };
  if (!account) return <Empty description="请选择账号" />;
  if (!canQuery)
    return (
      <Alert
        type="warning"
        showIcon
        message="暂无账号档案查看权限，请联系管理员"
      />
    );
  if (loading && !profile) return <Skeleton active paragraph={{ rows: 8 }} />;
  if (error)
    return (
      <Alert
        type="error"
        showIcon
        message={error}
        action={<Button onClick={() => void load()}>重试</Button>}
      />
    );
  if (!profile) return <Empty description="账号资料未加载" />;
  const reminder = (
    <Alert
      className="account-profile-reminder"
      type={missing.length ? "warning" : "success"}
      showIcon
      message={
        missing.length
          ? `还有 ${missing.length} 项资料待补充`
          : "人工资料已补齐"
      }
      description={
        <Space wrap>
          <Button
            type="link"
            onClick={() => {
              setMissingOwner("DIRECTOR");
              setShowMissing(true);
            }}
          >
            编导待补 {missing.filter((f) => f.ownerType === "DIRECTOR").length}
          </Button>
          <Button
            type="link"
            onClick={() => {
              setMissingOwner("OPERATOR");
              setShowMissing(true);
            }}
          >
            运营待补 {missing.filter((f) => f.ownerType === "OPERATOR").length}
          </Button>
          <span>空值可保存，自动生成项和后续复盘不计入缺失</span>
        </Space>
      }
    />
  );
  const missingList = showMissing && (
    <div className="account-profile-missing">
      {missing
        .filter((f) => !missingOwner || f.ownerType === missingOwner)
        .map((f) => (
          <Button
            key={f.key}
            size="small"
            disabled={!editable.length}
            onClick={() => openEditor(f.key)}
          >
            {f.label} · {owners[f.ownerType]}
          </Button>
        ))}
    </div>
  );
  const renderRow = (f: ProfileField) => (
    <div
      className={`account-profile-row owner-${f.ownerType.toLowerCase()}`}
      key={f.key}
      data-profile-key={f.key}
    >
      <div>
        <Typography.Text>{f.label}</Typography.Text>
        {tag(f)}
      </div>
      <div>
        {f.type === "image" && files[f.key] ? (
          <Image width={64} src={safeLink(files[f.key].previewUrl)} />
        ) : (
          <Typography.Text
            type={fieldEmpty(profile.values[f.key]) ? "secondary" : undefined}
          >
            {display(f)}
          </Typography.Text>
        )}
        {f.type === "record" && recordButton(f)}
        {profile.sourceNotes[f.key] && (
          <Tooltip title={profile.sourceNotes[f.key]}>
            <InfoCircleOutlined aria-label={profile.sourceNotes[f.key]} />
          </Tooltip>
        )}
      </div>
    </div>
  );
  return (
    <section className="media-students-card account-profile">
      <div className="media-students-tab-heading">
        <div>
          <Typography.Title level={5}>
            {profile.account.nickname || "未命名账号"}
          </Typography.Title>
          <Typography.Text type="secondary">
            {profile.account.platformLabelSnapshot || "平台待填写"}
          </Typography.Text>
          <Tag>{String(profile.values.current_status || "状态等待来源")}</Tag>
        </div>
        <div className="account-profile-actions">
        {deliveryActions}
        {editable.length > 0 && (
          <Button icon={<EditOutlined />} onClick={() => openEditor()}>
            维护账号表
          </Button>
        )}
        {editable.length > 0 && <Button onClick={() => setDiagnosisOpen(true)}>填写周期诊断</Button>}
        </div>
      </div>
      <Modal title="周期诊断" open={diagnosisOpen} onCancel={() => setDiagnosisOpen(false)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={submitDiagnosis} initialValues={{ reposition: false }}>
          <Form.Item label="诊断模板" name="templateType"><Select value={diagnosisType} onChange={setDiagnosisType} options={[{value:'diagnosis_7d',label:'7天账号数据诊断'},{value:'diagnosis_14d',label:'14天验证指标诊断'},{value:'diagnosis_28d',label:'28天调整触发条件'}]} /></Form.Item>
          <Form.Item label="当前阶段" name="currentStage" rules={[{required:true}]}><Select options={(dicts.zsjos_media_account_stage || []).map(x => ({value:x.value,label:x.label}))} /></Form.Item>
          <Form.Item label="账号状态" name="accountStatus" rules={[{required:true}]}><Select options={(dicts.zsjos_media_account_current_status || []).map(x => ({value:x.value,label:x.label}))} /></Form.Item>
          <Form.Item label="学员配合等级" name="cooperationLevel" rules={[{required:true}]}><Select options={(dicts.zsjos_media_account_cooperation_level || []).map(x => ({value:x.value,label:x.label}))} /></Form.Item>
          {[["cooperationEvidence","配合等级证据"],["primaryProblemEvidence","主要瓶颈证据"],["secondaryProblemEvidence","次要瓶颈证据"],["conclusion","一句话诊断结论"],["improvementMeasures","改进措施"],["observedData","重点观测数据"]].map(([name,label]) => <Form.Item key={name} label={label} name={name} rules={[{required:true}]}><Input.TextArea rows={2}/></Form.Item>)}
          <Form.Item label="主要瓶颈" name="primaryProblem" rules={[{required:true}]}><Select options={(dicts.zsjos_media_account_primary_problem || []).map(x => ({value:x.value,label:x.label}))} /></Form.Item>
          <Form.Item label="次要瓶颈" name="secondaryProblem" rules={[{required:true}]}><Select options={(dicts.zsjos_media_account_primary_problem || []).map(x => ({value:x.value,label:x.label}))} /></Form.Item>
          <Form.Item label="是否重新定位" name="reposition" rules={[{required:true}]}><Select options={[{value:true,label:'是'},{value:false,label:'否'}]} /></Form.Item>
          <Button type="primary" htmlType="submit">提交诊断</Button>
        </Form>
      </Modal>
      <div className="account-profile-legend">
        {(["AUTO", "DIRECTOR", "OPERATOR"] as const).map((owner) => (
          <Tag
            key={owner}
            className={`account-owner owner-${owner.toLowerCase()}`}
          >
            {owners[owner]}
          </Tag>
        ))}
      </div>
      {reminder}
      {missingList}
      <Progress
        percent={
          required
            ? Math.round(((required - missing.length) / required) * 100)
            : 100
        }
        size="small"
      />
      {/* 定高版式：左列主页图固定，右侧三个板块各自滚动，整页不滚。 */}
      <div className="account-profile-grid">
        <aside className="account-profile-aside">
          <div className="account-profile-cover">
            {files.cover && safeLink(files.cover.previewUrl) ? (
              <Image src={safeLink(files.cover.previewUrl)} />
            ) : (
              <>
                <FileImageOutlined />
                <span>主页图尚未上传</span>
                <Tag>责任待配置</Tag>
              </>
            )}
          </div>
          <p>学员：{profile.studentName || "未记录"}</p>
          <p>编导：{profile.directorName || "未分配"}</p>
          <p>运营：{profile.operatorName || "未分配"}</p>
        </aside>
        <div className="account-profile-sections">
          {groups.map(([k, name]) => (
            <section
              key={k}
              className="account-profile-section"
              data-profile-section={k}
            >
              <div className="account-profile-section-heading">
                <Typography.Title level={5}>{name}</Typography.Title>
                {fields.some(
                  (f) => profileSection(f) === k && editable.includes(f.key),
                ) && (
                  <Button type="link" onClick={() => openEditor()}>
                    维护
                  </Button>
                )}
              </div>
              <div className="account-profile-section-body">
                {k === "STATUS" ? (
                  <div className="account-profile-status-columns">
                    <div data-profile-status="identity">
                      {fields
                        .filter(
                          (f) =>
                            profileSection(f) === "STATUS" &&
                            (f.group === "PROFILE" || f.key === "account_no"),
                        )
                        .map(renderRow)}
                    </div>
                    <div data-profile-status="progress">
                      {fields
                        .filter(
                          (f) =>
                            profileSection(f) === "STATUS" &&
                            f.group !== "PROFILE" &&
                            f.key !== "account_no",
                        )
                        .map(renderRow)}
                    </div>
                  </div>
                ) : (
                  fields.filter((f) => profileSection(f) === k).map(renderRow)
                )}
                {k === "REVIEW" && (
                  <>
                    {profile.canViewHistory && (
                      <section className="account-profile-history">
                        <Typography.Title level={5}>
                          记录时间线
                        </Typography.Title>
                        {historyError && (
                          <Alert
                            type="error"
                            message={historyError}
                            action={
                              <Button onClick={() => void loadHistory(page)}>
                                重试
                              </Button>
                            }
                          />
                        )}
                        {historyLoading ? (
                          <Skeleton active />
                        ) : history.length ? (
                          history.map((e) => (
                            <article key={e.id}>
                              <Space wrap>
                                <Typography.Text strong>
                                  {e.title}
                                </Typography.Text>
                                <Tag>版本 {e.resultVersion}</Tag>
                                <Typography.Text type="secondary">
                                  {e.operatedBy || "未知维护人"} ·{" "}
                                  {formatTimestamp(e.operatedAt)}
                                </Typography.Text>
                              </Space>
                              <Typography.Paragraph>
                                {e.content}
                              </Typography.Paragraph>
                              {e.snapshots.length > 0 && (
                                <details>
                                  <summary>查看保存时资料快照</summary>
                                  {e.snapshots.map((s) => (
                                    <p key={s.key}>
                                      {s.label}：{s.displayValue || "未记录"}
                                    </p>
                                  ))}
                                </details>
                              )}
                              <Space wrap>
                                {e.files.map((f) => (
                                  <span key={f.id}>{fileLink(f)}</span>
                                ))}
                              </Space>
                            </article>
                          ))
                        ) : (
                          <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="暂无维护或复盘记录"
                          />
                        )}
                        {total > 10 && (
                          <Pagination
                            current={page}
                            total={total}
                            pageSize={10}
                            onChange={(p) => void loadHistory(p)}
                          />
                        )}
                      </section>
                    )}
                  </>
                )}
              </div>
            </section>
          ))}
        </div>
      </div>
      <Modal
        title={`维护账号表 · ${profile.account.nickname || account.accountNo}`}
        width="min(1440px, calc(100vw - 24px))"
        open={open}
        onCancel={close}
        maskClosable={false}
        footer={
          <Space wrap>
            <Typography.Text type="secondary">
              可部分保存，缺失项持续提醒
            </Typography.Text>
            <Button onClick={close} disabled={saving || uploading}>
              返回
            </Button>
            <Button
              loading={saving}
              disabled={uploading || Boolean(dictError)}
              onClick={() => void save(false)}
            >
              保存
            </Button>
            <Button
              type="primary"
              loading={saving}
              disabled={uploading || Boolean(dictError)}
              onClick={() => void save(true)}
            >
              保存并返回
            </Button>
          </Space>
        }
      >
        <Typography.Paragraph type="secondary">
          当前维护人：{profile.currentUserName || "当前用户"} · 资料完整度{" "}
          {required
            ? Math.round(((required - missing.length) / required) * 100)
            : 100}
          %
        </Typography.Paragraph>
        {reminder}
        {missingList}
        {dictError && (
          <Alert
            type="error"
            message={dictError}
            action={
              <Button onClick={() => void loadDicts(profile)}>重试字典</Button>
            }
          />
        )}
        {/* 定高版式：与账号主页同构 —— 左栏主页图 + 右侧三列字段卡片，各列内部滚动。 */}
        <div className="account-profile-editor" ref={body}>
          <aside className="account-profile-editor-aside">
            <div className="account-profile-editor-cover" data-profile-key="cover">
              <div className="account-profile-cover">
                {files.cover && safeLink(files.cover.previewUrl) ? (
                  <Image src={safeLink(files.cover.previewUrl)} />
                ) : (
                  <>
                    <FileImageOutlined />
                    <span>主页图尚未上传</span>
                  </>
                )}
              </div>
              <div className="account-profile-editor-actions">
                {fields
                  .filter((f) => f.key === "cover")
                  .map((f) => (
                    <div key={f.key}>{control(f)}</div>
                  ))}
              </div>
              <div className="account-profile-legend">
                {fields
                  .filter((f) => f.key === "cover")
                  .map((f) => (
                    <span key={f.key}>{tag(f)}</span>
                  ))}
              </div>
            </div>
            <p>学员：{profile.studentName || "未记录"}</p>
            <p>{account.accountNo}</p>
            <p>编导：{profile.directorName || "未分配"}</p>
            <p>运营：{profile.operatorName || "未分配"}</p>
          </aside>
          <div className="account-profile-editor-main">
            <div className="account-profile-editor-toolbar">
              <Typography.Text type="secondary">
                无权限字段以只读展示；空值可保存，缺失项持续提醒
              </Typography.Text>
              <Checkbox
                checked={onlyMissing}
                onChange={(e) => setOnlyMissing(e.target.checked)}
              >
                仅看待补充
              </Checkbox>
            </div>
            {/* 与只读展示区同构：三组各一列卡片，卡片内部各自滚动。 */}
            <div className="account-profile-editor-columns">
              {groups.map(([k, name]) => {
                const visible = fields.filter(
                  (f) =>
                    profileSection(f) === k &&
                    (!onlyMissing || missing.some((m) => m.key === f.key)),
                );
                const controlCell = (f: ProfileField) => (
                  <div
                    key={f.key}
                    className={`account-profile-control owner-${f.ownerType.toLowerCase()} ${f.type === "textarea" || f.type === "record" ? "wide" : ""}`}
                    data-profile-key={f.key}
                  >
                    <div>
                      <Typography.Text strong>{f.label}</Typography.Text>
                      {tag(f)}
                      <Tooltip
                        title={
                          f.type === "record"
                            ? "按次追加；图片或 PDF 附件；不计入待补"
                            : f.type === "image"
                              ? "PNG / JPEG / WebP，最大 20 MB"
                              : f.dictType
                                ? "管理员维护字典选项，保留选择时标签快照"
                                : f.type === "url"
                                  ? "仅支持 http 或 https 地址，可留空"
                                  : "可留空；文本最多 2000 字；按字段责任限制编辑"
                        }
                      >
                        <InfoCircleOutlined aria-label={`${f.label}字段规则`} />
                      </Tooltip>
                    </div>
                    {control(f)}
                    {f.description && (
                      <Typography.Paragraph style={{ whiteSpace: "pre-wrap" }}>
                        {f.description}
                      </Typography.Paragraph>
                    )}
                    <Typography.Text type="secondary">
                      {f.ownerType === "AUTO"
                        ? profile.sourceNotes[f.key] || "系统生成，不可手动修改"
                        : !editable.includes(f.key)
                          ? "当前只读"
                          : f.type === "record"
                            ? "按次追加，不覆盖历史"
                            : fieldEmpty(values[f.key])
                              ? "待补充，不影响保存"
                              : "已填写"}
                    </Typography.Text>
                  </div>
                );
                return (
                  <section
                    key={k}
                    className="account-profile-section"
                    data-profile-section={k}
                  >
                    <div className="account-profile-section-heading">
                      <Typography.Title level={5}>{name}</Typography.Title>
                      <Typography.Text type="secondary">
                        待补{" "}
                        {missing.filter((f) => profileSection(f) === k).length}
                      </Typography.Text>
                    </div>
                    <div className="account-profile-section-body">
                      {k === "POSITIONING" ? (
                        <table className="account-positioning-table">
                          <thead>
                            <tr>
                              <th>定位卡项目</th>
                              <th>填写提示</th>
                              <th>计划交付内容确定</th>
                              <th>参考账号与爆款</th>
                            </tr>
                          </thead>
                          <tbody>
                            {visible
                              .filter((f) => !f.referenceFor)
                              .map((f) => (
                                <tr key={f.key} data-profile-key={f.key}>
                                  <th scope="row">
                                    {f.label}
                                    {tag(f)}
                                  </th>
                                  <td>{f.description || "—"}</td>
                                  <td>{control(f)}</td>
                                  <td>
                                    {fields
                                      .filter(
                                        (ref) => ref.referenceFor === f.key,
                                      )
                                      .map((ref) => (
                                        <div key={ref.key}>{control(ref)}</div>
                                      ))}
                                  </td>
                                </tr>
                              ))}
                          </tbody>
                        </table>
                      ) : k === "STATUS" ? (
                        <div className="account-profile-status-columns">
                          <div data-profile-status="identity">
                            {visible
                              .filter(
                                (f) =>
                                  f.group === "PROFILE" ||
                                  f.key === "account_no",
                              )
                              .map(controlCell)}
                          </div>
                          <div data-profile-status="progress">
                            {visible
                              .filter(
                                (f) =>
                                  f.group !== "PROFILE" &&
                                  f.key !== "account_no",
                              )
                              .map(controlCell)}
                          </div>
                        </div>
                      ) : (
                        visible.map(controlCell)
                      )}
                    </div>
                  </section>
                );
              })}
            </div>
          </div>
        </div>
      </Modal>
      <Modal
        title={record?.label}
        open={Boolean(record)}
        onCancel={() => {
          if (saving || uploading) return;
          if (content.trim() || recordFiles.length)
            modal.confirm({
              title: "放弃本次未保存记录？",
              onOk: () => setRecord(undefined),
            });
          else setRecord(undefined);
        }}
        onOk={() => void append()}
        confirmLoading={saving}
        okButtonProps={{
          disabled: uploading || (!content.trim() && !recordFiles.length),
        }}
        okText="追加记录"
        maskClosable={false}
      >
        <Input.TextArea
          aria-label="复盘记录内容"
          rows={6}
          maxLength={10000}
          showCount
          value={content}
          onChange={(e) => setContent(e.target.value)}
          disabled={saving}
        />
        <Upload
          accept="image/png,image/jpeg,image/webp,application/pdf"
          showUploadList={false}
          beforeUpload={(file) => {
            if (record) void upload(record, file, true);
            return false;
          }}
          disabled={saving || uploading || recordFiles.length >= 20}
        >
          <Button icon={<UploadOutlined />} loading={uploading}>
            上传附件
          </Button>
        </Upload>
        <Typography.Paragraph type="secondary">
          图片或 PDF，单个不超过 20 MB，最多 20 个。
        </Typography.Paragraph>
        {recordFiles.map((f) => (
          <div key={f.id}>
            {fileLink(f)}
            <Button
              type="link"
              disabled={saving}
              onClick={() =>
                setRecordFiles((current) =>
                  current.filter((x) => x.id !== f.id),
                )
              }
            >
              移除
            </Button>
          </div>
        ))}
      </Modal>
    </section>
  );
}
