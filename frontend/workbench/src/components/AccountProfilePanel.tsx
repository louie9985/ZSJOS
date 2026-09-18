import { diagnosisApi, type DiagnosisTodo } from "../services/mediaAccountProfile";
import AccountDiagnosisForm from "./AccountDiagnosisForm";
import AccountReviewRecord from "./AccountReviewRecord";
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
import { memo, useCallback, useEffect, useMemo, useRef, useState, type ReactNode, type SetStateAction } from "react";
import { api, type DictData, type MediaStudentDetail } from "../services/api";
import {
  accountProfileApi,
  fieldEmpty,
  formatAccountMetric,
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
import AccountPositioningCard from './AccountPositioningCard';
import AccountProfileTextInput from './AccountProfileTextInput';
import ResourceLink from './ResourceLink';
const MemoAccountPositioningCard = memo(AccountPositioningCard);

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

export function diagnosisHistoryText(entry: ProfileEntry): string | undefined {
  if (entry.kind !== 'DIAGNOSIS' || !entry.content) return entry.content;
  try {
    const data = JSON.parse(entry.content) as Record<string, unknown>;
    const requirements = data.requirementSnapshot as Record<string,unknown> | undefined;
    const source = data.requirementSource as {submissionNo?:number} | undefined;
    const requirementText = requirements ? ['diagnosis_7d','diagnosis_14d','diagnosis_28d'].map((key,i) => `${[7,14,28][i]}天要求：${requirements[key] ?? '未填写'}`).join('\n') : '诊断要求：历史版本未留存';
    return `${source?.submissionNo ? `来源：定位卡第${source.submissionNo}次提交\n` : ''}${requirementText}\n` + ([['cycle', '第几轮诊断'], ['currentStageLabel', '阶段'], ['accountStatusLabel', '账号状态'],
      ['primaryProblemLabel', '主要瓶颈'], ['primaryProblemEvidence', '主要瓶颈证据'],
      ['cooperationLevelLabel', '配合等级'], ['cooperationEvidence', '配合等级证据'],
      ['secondaryProblemLabel', '次要瓶颈'], ['secondaryProblemEvidence', '次要瓶颈证据'],
      ['conclusion', '诊断结论'], ['improvementMeasures', '改进措施'], ['observedData', '重点观测数据'],
      ['reposition', '是否重新定位']] as const)
      .filter(([key]) => data[key] != null && data[key] !== '' && !(key === 'cycle' && entry.fieldKey === 'diagnosis_initial'))
      .map(([key, label]) => `${label}：${typeof data[key] === 'boolean' ? (data[key] ? '是' : '否') : String(data[key])}`).join('\n');
  } catch { return '诊断记录格式无法读取，请联系管理员'; }
}

export default function AccountProfilePanel({
  account,
  canQuery,
  canQueryPositioning = false,
  student,
  serviceRelationId,
  canReadInterview = false,
  initiallyEditing = false,
  diagnosisTaskId,
  onEditingFinished,
  onSaved,
  onMissingChange,
  deliveryActions,
}: {
  account?: Account;
  deliveryActions?: ReactNode;
  canQuery: boolean;
  canQueryPositioning?: boolean;
  student?: MediaStudentDetail["student"];
  serviceRelationId?: number;
  canReadInterview?: boolean;
  canMaintain: boolean;
  initiallyEditing?: boolean;
  diagnosisTaskId?: number;
  onEditingFinished?: () => void;
  onSaved: () => Promise<void>;
  onMissingChange?: (id: number, count: number) => void;
}) {
  const { message, modal } = App.useApp();
  const [positioningRefresh, setPositioningRefresh] = useState(0);
  const [positioningStatusTarget, setPositioningStatusTarget] = useState<HTMLDivElement | null>(null);
  const [profile, setProfile] = useState<AccountProfile>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [open, setOpen] = useState(false),
    [values, publishValues] = useState<Record<string, unknown>>({}),
    [files, setFiles] = useState<Record<string, ProfileFile>>({});
  const [diagnosisSeed, setDiagnosisSeed] = useState<Record<string, unknown>>({});
  const [diagnosisPrevious, setDiagnosisPrevious] = useState<number>();
  const [diagnosisSaving, setDiagnosisSaving] = useState(false);
  const diagnosisLock = useRef(false);
  const diagnosisRequest = useRef<{ fingerprint: string; key: string } | undefined>(undefined);
  const [diagnosisTask, setDiagnosisTask] = useState<DiagnosisTodo>();
  const [diagnosisTasks, setDiagnosisTasks] = useState<DiagnosisTodo[]>([]);
  const [diagnosisTasksLoading, setDiagnosisTasksLoading] = useState(false);
  const [diagnosisTasksError, setDiagnosisTasksError] = useState('');
  const openedDiagnosisTask = useRef<number | undefined>(undefined);
  const startupPromptedTask = useRef<number | undefined>(undefined);
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
  const latestValues = useRef<Record<string, unknown>>({});
  const setValues = useCallback((action: SetStateAction<Record<string, unknown>>) => {
    latestValues.current = typeof action === 'function' ? action(latestValues.current) : action;
    publishValues(latestValues.current);
  }, []);
  const updateText = useCallback((key: string, value: string) => {
    const previous = latestValues.current[key];
    latestValues.current = { ...latestValues.current, [key]: value };
    // Only completeness/dirty transitions need the rest of the sheet to update while typing.
    const original = profile?.values[key] ?? '';
    if (fieldEmpty(previous) !== fieldEmpty(value) || (previous === original) !== (value === original)) {
      publishValues(latestValues.current);
    }
  }, [profile?.values]);
  const flushText = useCallback(() => publishValues(latestValues.current), []);
  const refreshPositioning = useCallback(() => setPositioningRefresh(value => value + 1), []);
  const fields = useMemo(() => profile?.config.fields.filter((f) => f.enabled && f.group !== 'POSITIONING' && !f.key.startsWith('pc_') && !['positioning_history', 'positioning_snapshot'].includes(f.key)) || [], [profile?.config.fields]),
    editable = profile?.editableFields || [];
  const missing = profileMissing(fields, open ? values : profile?.values || {});
  const required = fields.filter(
    (f) =>
      f.requiredForComplete &&
      ["DIRECTOR", "OPERATOR"].includes(f.ownerType) &&
      !POSITIONING_SYNC_FIELDS.has(f.key) &&
      f.type !== "record",
  ).length;
  const currentChanges = () => profile
    ? profileChanges(fields, editable, profile.values, latestValues.current)
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
  const loadDiagnosisTasks = useCallback(async () => {
    if (!account || !profile?.canSubmitDiagnosis) return;
    const gen = generation.current;
    setDiagnosisTasksLoading(true);
    setDiagnosisTasksError('');
    try {
      const tasks = await diagnosisApi.tasks(account.id);
      if (gen === generation.current) setDiagnosisTasks(tasks);
    } catch (cause) {
      if (gen === generation.current) setDiagnosisTasksError(errorText(cause));
    } finally {
      if (gen === generation.current) setDiagnosisTasksLoading(false);
    }
  }, [account?.id, profile]);
  useEffect(() => {
    setDiagnosisTasks([]);
    setDiagnosisTasksError('');
    void loadDiagnosisTasks();
  }, [loadDiagnosisTasks]);
  useEffect(() => {
    // Applying a card changes both the account version and startup eligibility.
    if (positioningRefresh > 0) void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positioningRefresh]);
  const loadHistory = async (p = 1) => {
    if (!account || !profile?.canViewHistory) return;
    const gen = generation.current;
    setHistoryLoading(true);
    setHistoryError("");
    try {
      const r = await accountProfileApi.history(account.id, p, { kind: "PROFILE" });
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
    setDiagnosisOpen(false);
    diagnosisRequest.current = undefined;
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
        changes: currentChanges(),
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
    if (Object.keys(currentChanges()).length) {
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
        ? profile?.sourceNotes[f.key] || "等待来源数据"
        : f.type === "record"
          ? "按次追加，详见下方记录"
          : "待补充";
    return f.ownerType === "AUTO"
      ? formatAccountMetric(f.key, v) ?? profile?.snapshots.find(s => s.key === f.key)?.displayValue ?? String(v)
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
    editable.includes(f.key) && !f.key.startsWith("delivery_s") && (!["diagnosis_7d","diagnosis_14d","adjustment_28d"].includes(f.key) || profile?.canSubmitDiagnosis) ? (
      <Button
        size="small"
        disabled={
          saving || uploading || (open && Object.keys(currentChanges()).length > 0)
        }
        onClick={() => {
          if (["diagnosis_7d", "diagnosis_14d", "adjustment_28d"].includes(f.key)) {
            openDiagnosis(f.key === "adjustment_28d" ? "diagnosis_28d" : f.key as DiagnosisRequest["templateType"]);
            return;
          }
          setRecord(f);
          setContent("");
          setRecordFiles([]);
        }}
      >
        {["diagnosis_7d","diagnosis_14d","adjustment_28d"].includes(f.key) ? "填写诊断" : "填写记录"}
      </Button>
      ) : null;
  const openDiagnosis = async (type: DiagnosisRequest["templateType"], previous?: ProfileEntry, requestedTaskId?: number, anyTemplate = false) => {
    if (!profile) return;
    let seed: Record<string, unknown> = {};
    if (previous?.kind === "DIAGNOSIS" && previous.content) {
      try { seed = JSON.parse(previous.content) as Record<string, unknown>; } catch { message.error("记录格式无法读取"); return; }
    }
    const requestGeneration = generation.current;
    let task: DiagnosisTodo | undefined;
    if (!previous && type !== 'diagnosis_initial') {
      const accountId = account!.id;
      try {
        const tasks = await diagnosisApi.tasks(accountId);
        if (requestGeneration !== generation.current) return;
        setDiagnosisTasks(tasks);
        task = tasks.find(t => requestedTaskId ? t.taskId === requestedTaskId : anyTemplate || t.templateType === type);
        if (!task) { message.info('暂无可填写任务：本周期可能已完成或任务尚未生成，请刷新任务查看'); return; }
        seed.cycle = task.cycle; type = task.templateType;
      } catch (cause) { message.error(errorText(cause)); return; }
    }
    if (requestGeneration !== generation.current) return;
    setDiagnosisTask(task);
    setDiagnosisSeed(seed); setDiagnosisPrevious(previous?.id);
    setDiagnosisType(type); diagnosisRequest.current = undefined; setDiagnosisOpen(true);
    void loadDicts(profile);
  };
  const diagnosisDeadline = (fieldKey: string) => {
    if (!profile?.canSubmitDiagnosis) return <Typography.Paragraph type="secondary">{profile?.canStartDiagnosis ? '完成启动诊断后可查看周期任务及截止时间' : '周期任务暂不可用，请确认有效定位卡、启动诊断及账号维护权限'}</Typography.Paragraph>;
    if (diagnosisTasksLoading) return <Typography.Paragraph type="secondary">正在加载诊断截止时间…</Typography.Paragraph>;
    if (diagnosisTasksError) return <Alert type="error" showIcon message={`诊断任务加载失败：${diagnosisTasksError}`} action={<Button size="small" onClick={() => void loadDiagnosisTasks()}>重试</Button>} />;
    const task = diagnosisTasks.find(t => t.templateType === (fieldKey === 'adjustment_28d' ? 'diagnosis_28d' : fieldKey));
    return <Typography.Paragraph>
      {task ? <>第 {task.cycle} 轮 · <Typography.Text strong>截止时间：{formatTimestamp(task.dueAt)}（北京时间）</Typography.Text><br />{task.dueAt < Date.now() ? '已逾期，仍可补填' : '可提前填写，提交后完成本次任务'}</> : '暂无待填写任务，本周期可能已完成；下一周期开始后生成新任务'}
      {' '}<Button type="link" size="small" onClick={() => void loadDiagnosisTasks()}>刷新任务</Button>
    </Typography.Paragraph>;
  };
  useEffect(() => {
    if (!profile || !diagnosisTaskId || !account || openedDiagnosisTask.current === diagnosisTaskId) return;
    if (!profile.canSubmitDiagnosis) {
      if (startupPromptedTask.current !== diagnosisTaskId) {
        startupPromptedTask.current = diagnosisTaskId;
        message.info(profile.canStartDiagnosis ? '请先完成启动诊断，再填写本次跟进' : '请先应用有效定位卡并完成启动诊断，或确认账号维护权限');
        if (profile.canStartDiagnosis) void openDiagnosis('diagnosis_initial');
      }
      return;
    }
    openedDiagnosisTask.current = diagnosisTaskId;
    void openDiagnosis('diagnosis_7d', undefined, diagnosisTaskId);
  }, [profile, account?.id, diagnosisTaskId]);
  const submitDiagnosis = async (values: Record<string, unknown>) => {
    if (!profile || !account || diagnosisLock.current) return;
    const gen = generation.current;
    diagnosisLock.current = true; setDiagnosisSaving(true);
    try {
      const payload = { ...values, version: profile.account.version, configVersionId: profile.config.id,
        previousEntryId: diagnosisPrevious, taskId: diagnosisTask?.taskId, templateType: diagnosisType, cycle: diagnosisType === 'diagnosis_initial' ? 0 : Number(diagnosisPrevious != null ? diagnosisSeed.cycle : diagnosisTask?.cycle) };
      const fingerprint = JSON.stringify(payload);
      if (diagnosisRequest.current?.fingerprint !== fingerprint) diagnosisRequest.current = { fingerprint, key: crypto.randomUUID() };
      await accountProfileApi.diagnosis(account.id, { ...payload, idempotencyKey: diagnosisRequest.current.key } as DiagnosisRequest);
      if (gen !== generation.current) return;
      message.success("诊断已提交"); setDiagnosisOpen(false); await load(); await loadHistory(); await onSaved();
    } catch (cause) { message.error(errorText(cause)); }
    finally { diagnosisLock.current = false; setDiagnosisSaving(false); }
  };
  const control = (f: ProfileField) => {
    const allowed = editable.includes(f.key),
      value = values[f.key],
      disabled = !allowed || saving || uploading;
    const update = (v: unknown) =>
      setValues((current) => ({ ...current, [f.key]: v }));
    if (f.type === "record")
      return (
        <>
          {(["diagnosis_7d", "diagnosis_14d", "adjustment_28d"].includes(f.key)) && (
            <>{diagnosisDeadline(f.key)}<Typography.Paragraph type="secondary" style={{ whiteSpace: "pre-wrap" }}>
              定位卡诊断要求：{String(profile?.positioningRequirements?.[f.key === "adjustment_28d" ? "diagnosis_28d" : f.key] ?? "未填写诊断要求")}
              <br />{profile?.diagnosisContext?.submissionNo ? `来源：第${profile.diagnosisContext.submissionNo}次提交 · 更新 ${profile.diagnosisContext.syncedAt}` : '等待有效定位卡，计时待核实'}
            </Typography.Paragraph></>
          )}
          <AccountReviewRecord accountId={account!.id} fieldKey={f.key} latest={profile?.latestRecords?.[f.key]} canHistory={!!profile?.canViewHistory}
          text={diagnosisHistoryText} action={recordButton(f)} onRevise={profile?.canSubmitDiagnosis && ["diagnosis_7d","diagnosis_14d","adjustment_28d"].includes(f.key) ? entry => openDiagnosis(f.key === "adjustment_28d" ? "diagnosis_28d" : f.key as DiagnosisRequest["templateType"], entry) : undefined} />
        </>
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
    return (
      <AccountProfileTextInput
        key={`${account?.id}-${open}`}
        fieldKey={f.key} label={f.label} value={String(value ?? "")}
        multiline={f.type === 'textarea'} date={f.type === 'date'}
        onChange={updateText} onBlur={flushText}
        maxLength={["nickname", "uid"].includes(f.key) ? 255 : 2000}
        disabled={disabled}
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
  const reminder = missing.length ? (
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
  ) : (
    <div className="account-profile-complete" role="status">
      <Tag color="success">资料已齐全</Tag>
      <Button type="link" size="small" onClick={() => { setMissingOwner('DIRECTOR'); setShowMissing(true) }}>编导待补 0</Button>
      <Button type="link" size="small" onClick={() => { setMissingOwner('OPERATOR'); setShowMissing(true) }}>运营待补 0</Button>
    </div>
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
        {f.type === "record" ? control(f) : f.type === "image" && files[f.key] ? (
          <Image width={64} src={safeLink(files[f.key].previewUrl)} />
        ) : f.type === 'url' && !fieldEmpty(profile.values[f.key]) ? (
          <ResourceLink href={String(profile.values[f.key])} />
        ) : (['publish_rhythm', 'account_position', 'professional_position', 'stage', 'current_status', 'bottleneck', 'content_format'].includes(f.key) || f.type === 'select' || f.type === 'multi_select') && !fieldEmpty(profile.values[f.key]) ? (
          <span className="account-value-tags">{(f.type === 'multi_select' || ['account_position', 'professional_position', 'content_format'].includes(f.key)
            // The profile API joins historical multi-selection labels with this delimiter.
            ? display(f).split('、').map(value => value.trim()).filter(Boolean)
            : [display(f)]).map((label, index) => <Tag key={`${index}-${label}`} className="account-value-tag" color="blue">{label}</Tag>)}</span>
        ) : (
          <Typography.Text
            type={fieldEmpty(profile.values[f.key]) ? "secondary" : undefined}
            style={f.type === "textarea" ? { whiteSpace: "pre-wrap", overflowWrap: "anywhere" } : undefined}
          >
            {display(f)}
          </Typography.Text>
        )}

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
      <Modal title={diagnosisType === "diagnosis_initial" ? "启动诊断" : "周期诊断"} open={diagnosisOpen} onCancel={() => { if (!diagnosisSaving) setDiagnosisOpen(false); }} width={1080} style={{top:16,maxWidth:'calc(100vw - 32px)',paddingBottom:0}}
        styles={{container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column'},body:{minHeight:0,overflowY:'auto'},footer:{flexShrink:0}}}
        footer={<Button type="primary" htmlType="submit" form="account-diagnosis-form" loading={diagnosisSaving} disabled={!!dictError || dictLoading}>提交诊断</Button>} destroyOnHidden>
        {dictError && <Alert type="error" message={dictError} action={<Button onClick={() => void loadDicts(profile)}>重试字典</Button>} />}
        {diagnosisTask && <Alert type={diagnosisTask.dueAt < Date.now() ? 'warning' : 'info'} showIcon
          message={`截止时间：${formatTimestamp(diagnosisTask.dueAt)}（北京时间）`}
          description={diagnosisTask.dueAt < Date.now() ? '本次任务已逾期，仍可补填。' : '可以提前填写，提交后即完成本次诊断任务。'} />}
        {diagnosisType !== 'diagnosis_initial' && <Typography.Paragraph style={{whiteSpace:'pre-wrap'}}>
          本次诊断要求：{String((diagnosisPrevious ? (diagnosisSeed.requirementSnapshot as Record<string,unknown> | undefined) : diagnosisTask?.payload.requirementSnapshot)?.[diagnosisType] ?? '历史版本未留存')}
        </Typography.Paragraph>}
        <AccountDiagnosisForm key={`${diagnosisType}-${diagnosisPrevious ?? diagnosisTask?.taskId ?? 'new'}`} type={diagnosisType}
          seed={diagnosisSeed} revising={diagnosisPrevious != null} disabled={diagnosisSaving || dictLoading || !!dictError}
          dicts={dicts} onFinish={submitDiagnosis} />
      </Modal>
      {/* 主体自然滚动；右侧摘要栏由 CSS sticky 保持在视口内。 */}
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
          <section className="account-profile-positioning-status" aria-label="定位卡状态与操作">
            <Typography.Text strong>定位卡状态与操作</Typography.Text>
            <div ref={setPositioningStatusTarget} />
          </section>
          <section className="account-profile-sidebar-summary">
            <Typography.Text strong>账号状态</Typography.Text>
            <Tag color={profile.snapshots.find(s => s.key === "current_status")?.displayValue ? "processing" : "default"}>
              {profile.snapshots.find(s => s.key === "current_status")?.displayValue || "待完成启动诊断"}
            </Tag>
            <div className="account-profile-sidebar-actions">
              {editable.length > 0 && <Button icon={<EditOutlined />} onClick={() => openEditor()}>维护账号表</Button>}
              {profile.canStartDiagnosis && <Button onClick={() => openDiagnosis('diagnosis_initial')}>填写启动诊断</Button>}
              {profile.canSubmitDiagnosis && <Button onClick={() => openDiagnosis('diagnosis_7d', undefined, undefined, true)}>填写周期诊断</Button>}
            </div>
            {reminder}
            {missingList}
            <Progress percent={required ? Math.round(((required - missing.length) / required) * 100) : 100} size="small" />
          </section>
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
                {k === "POSITIONING" ? <MemoAccountPositioningCard hideHistory statusTarget={positioningStatusTarget} key={`${account.id}-${account.version}`} accountId={account.id} canQuery={canQueryPositioning} studentName={student?.name || profile.studentName} studentContact={student?.mobile} serviceRelationId={serviceRelationId} canReadInterview={canReadInterview} refresh={positioningRefresh} onApplied={refreshPositioning} /> : k === "STATUS" ? (
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
                  <>{fields.filter((f) => profileSection(f) === k && !f.key.startsWith("delivery_s")).map(renderRow)}{k === "REVIEW" && deliveryActions}</>
                )}
                {k === "REVIEW" && (
                  <>
                    {profile.canViewHistory && (
                      <details className="account-profile-history"><summary>查看资料变更历史</summary>
                        <Typography.Title level={5}>
                          资料变更历史
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
                              <Typography.Paragraph style={{ whiteSpace: "pre-wrap" }}>
                                {diagnosisHistoryText(e)}
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
                            description="暂无资料变更历史"
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
                      </details>
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
        width={1680}
        style={{ top: 16, maxWidth: 'calc(100vw - 24px)', paddingBottom: 0 }}
        styles={{ container: { maxHeight: 'calc(100dvh - 32px)', display: 'flex', flexDirection: 'column' }, body: { minHeight: 0, overflowY: 'auto' }, header: { flexShrink: 0 }, footer: { flexShrink: 0 } }}
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
        {dictError && (
          <Alert
            type="error"
            message={dictError}
            action={
              <Button onClick={() => void loadDicts(profile)}>重试字典</Button>
            }
          />
        )}
        {/* 第一列集中承载预览、归属、操作与待补提醒；定位卡通栏占第一行，
            状态与复盘并排占第二行。列位置由 CSS grid-template-areas 指定，
            不依赖 DOM 顺序 —— 曾经因此把复盘卡挤到空白列。 */}
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
            <div className="account-profile-editor-owner">
              <p>学员：{profile.studentName || "未记录"}</p>
              <p>{account.accountNo}</p>
              <p>编导：{profile.directorName || "未分配"}</p>
              <p>运营：{profile.operatorName || "未分配"}</p>
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
          </aside>
          <div className="account-profile-editor-main">
            {/* 三张卡片各是一列网格项，内部各自滚动。 */}
            <div className="account-profile-editor-columns">
              {groups.map(([k, name]) => {
                if (k === 'POSITIONING') return <section key={k} className="account-profile-section" data-profile-section={k}>
                  <div className="account-profile-section-heading"><Typography.Title level={5}>{name}</Typography.Title></div>
                  <div className="account-profile-section-body"><MemoAccountPositioningCard key={account.id} accountId={account.id} canQuery={canQueryPositioning} studentName={student?.name || profile.studentName} studentContact={student?.mobile} serviceRelationId={serviceRelationId} canReadInterview={canReadInterview} refresh={positioningRefresh} onApplied={refreshPositioning} /></div>
                </section>;
                const visible = fields.filter(
                  (f) =>
                    profileSection(f) === k && !f.key.startsWith("delivery_s") &&
                    (!onlyMissing || missing.some((m) => m.key === f.key)),
                );
                const controlCell = (f: ProfileField) => (
                  <div
                    key={f.key}
                    className={`account-profile-control owner-${f.ownerType.toLowerCase()}`}
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
                      {k === "STATUS" ? (
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
                        <>{visible.map(controlCell)}{k === "REVIEW" && deliveryActions}</>
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
