import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Radio,
  Select,
  Skeleton,
  Space,
  Spin,
  Switch,
  Tag,
  Tabs,
  Typography,
  Upload,
  message,
} from "antd";
import { CheckOutlined, DeleteOutlined, DownOutlined, EditOutlined, PhoneOutlined, PlusOutlined, ReloadOutlined, UploadOutlined, UpOutlined, UserAddOutlined } from "@ant-design/icons";
import { useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { NameAvatar } from "../components/LeadDetailOverview";
import LeadDetail from "../components/LeadDetail";
import StudentDetail from "../components/StudentDetail";
import SalesOrderEntryModal from "../components/SalesOrderEntryModal";
import { hasPermission } from "../services/managementAccess";
import OverflowToolbar, { type ToolbarAction } from "../components/OverflowToolbar";
import {
  api,
  type DictData,
  type ManagedLead,
  type MyStudent,
  type RegistrationCase,
  type RegistrationChecklistConfig,
  type RegistrationRoute,
  type SimpleDept,
  type StudyPlanner,
} from "../services/api";
import { formatTimestamp } from "../services/time";
import DetailFieldGrid from "../components/DetailFieldGrid";
import { AdvancedFilterToolbar } from "../components/AdvancedFilter";
import type { AdvancedFilterGroup } from "../services/api";
import { DICT_TYPE } from "../constants";
import { dictionaryDisplayLabel } from "../services/leadManagement";
import RegistrationAttachmentPreview from "../components/RegistrationAttachmentPreview";

const PAGE_SIZE = 20;
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "请求失败，请重试";
const key = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const serviceStatusLabel = (status: string) =>
  ({ active: "服务中", completed: "已完成", cancelled: "已取消" })[status] ||
  "未知状态";
const studentContactTypeLabel = (type?: string) =>
  ({ student_first_contact: "首联", student_study_plan: "制定学习计划", student_contact: "普通跟进", student_delivery_stage: "交付记录" })[type || ""] || type || "联系记录";
const studentDeliveryStageLabel = (stage?: string) =>
  ({ first_contact: "首联", study_plan: "制定学习计划", supervision: "督学", exam_confirmation: "考试确认", exam_preparation: "考前准备", post_exam: "考后跟进", result: "成绩跟进", certificate: "证书办理" })[stage || ""] || "历史交付记录";

function LoadState({
  error,
  retry,
  warning = false,
}: {
  error: string;
  retry: () => void;
  warning?: boolean;
}) {
  return (
    <Alert
      type={warning ? "warning" : "error"}
      showIcon
      title={error}
      action={
        <Button size="small" icon={<ReloadOutlined />} onClick={retry}>
          重试
        </Button>
      }
    />
  );
}

function withCompletionState(value: RegistrationCase): RegistrationCase {
  if (value.orderStatus !== "effective") return value;
  const allChecked =
    Boolean(value.items?.length) &&
    value.items!.filter((item) => item.itemType === "checkbox").every((item) => Boolean(item.checked));
  if (!allChecked)
    return {
      ...value,
      completable: false,
      completionBlockCode: "checklist_incomplete",
      completionBlockReason: "请先完成全部报名履约清单项",
    };
  if (value.items?.some((item) => item.itemType === "attachment" && item.attachmentRequired && !item.attachments?.length))
    return {
      ...value,
      completable: false,
      completionBlockCode: "attachment_required",
      completionBlockReason: "请先上传所有必传附件",
    };
  const selectedRoutes = value.routes?.filter((route) => route.selected) || [];
  if (!selectedRoutes.length) return { ...value, completable: false, completionBlockCode: "route_required", completionBlockReason: "请至少选择一个学员流转部门" };
  if (selectedRoutes.some((route) => !route.assigneeUserId)) return { ...value, completable: false, completionBlockCode: "route_assignee_invalid", completionBlockReason: "请为已选流转部门配置有效负责人" };
  return {
    ...value,
    completable: true,
    completionBlockCode: undefined,
    completionBlockReason: undefined,
  };
}

export function RegistrationPoolPage() {
  const location = useLocation();
  const routeState = location.state as { registrationCaseId?: number } | null;
  const [rows, setRows] = useState<RegistrationCase[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [selected, setSelected] = useState<RegistrationCase>();
  const [error, setError] = useState("");
  const [detailError, setDetailError] = useState("");
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [routeSaving, setRouteSaving] = useState(false);
  const [attachmentSavingIds, setAttachmentSavingIds] = useState<Set<number>>(new Set());
  const [savingItemIds, setSavingItemIds] = useState<Set<number>>(new Set());
  const [routeCandidates, setRouteCandidates] = useState<Record<number, Array<{ id: number; nickname: string }>>>({});
  const listGeneration = useRef(0);
  const detailGeneration = useRef(0);
  const inflightLists = useRef(new Set<string>());
  const applyCase = useCallback((next: RegistrationCase) => {
    setSelected(next);
    setRows((current) =>
      current.map((row) =>
        row.id === next.id ? { ...row, ...next, items: undefined } : row,
      ),
    );
  }, []);
  const loadCase = useCallback(
    async (id: number) => {
      const generation = ++detailGeneration.current;
      setDetailLoading(true);
      setDetailError("");
      try {
        const next = await api.registrationCase(id);
        if (generation === detailGeneration.current) applyCase(next);
      } catch (requestError) {
        if (generation === detailGeneration.current) setDetailError(errorMessage(requestError));
      } finally {
        if (generation === detailGeneration.current) setDetailLoading(false);
      }
    },
    [applyCase],
  );
  const load = useCallback(
    async (targetPage = pageNo, preferredId?: number) => {
      const requestKey = `${targetPage}:${preferredId || "current"}:${keyword}:${JSON.stringify(advancedFilter)}`;
      if (inflightLists.current.has(requestKey)) return;
      inflightLists.current.add(requestKey);
      const generation = ++listGeneration.current;
      setLoading(true);
      setError("");
      try {
        const page = await api.registrationPoolPage({
            pageNo: targetPage,
            pageSize: PAGE_SIZE,
            keyword: keyword || undefined,
            advancedFilter,
          });
        if (generation !== listGeneration.current) return;
        setRows(page.list);
        setTotal(page.total);
        setPageNo(targetPage);
        const targetId = preferredId ?? selected?.id ?? page.list[0]?.id;
        if (targetId) await loadCase(targetId);
        else setSelected(undefined);
      } catch (requestError) {
        if (generation === listGeneration.current) setError(errorMessage(requestError));
      } finally {
        inflightLists.current.delete(requestKey);
        if (generation === listGeneration.current) setLoading(false);
      }
    },
    [advancedFilter, keyword, loadCase, pageNo, selected?.id],
  );

  useEffect(() => {
    void load(1, routeState?.registrationCaseId);
  }, [advancedFilter, keyword]);
  useEffect(() => {
    const refresh = (event: Event) => {
      const caseId = Number(
        (event as CustomEvent<{ registrationCaseId?: number }>).detail
          ?.registrationCaseId,
      );
      message.info("有新的报名履约任务");
      void load(1, Number.isFinite(caseId) ? caseId : undefined);
    };
    window.addEventListener("zsjos-registration-task-created", refresh);
    return () =>
      window.removeEventListener("zsjos-registration-task-created", refresh);
  }, [load]);

  const updateItem = async (
    item: NonNullable<RegistrationCase["items"]>[number],
    checked: boolean,
  ) => {
    if (!selected) return;
    const previous = selected;
    applyCase(
      withCompletionState({
        ...selected,
        status: "processing",
        statusLabel: "处理中",
        items: selected.items?.map((current) =>
          current.id === item.id ? { ...current, checked } : current,
        ),
      }),
    );
    setSavingItemIds((current) => new Set(current).add(item.id));
    try {
      applyCase(
        await api.updateRegistrationItem(previous.id, item.id, {
          checked,
          version: previous.version,
          idempotencyKey: key(),
        }),
      );
    } catch (requestError) {
      applyCase(previous);
      const text = errorMessage(requestError);
      message.error(text);
      if (text.includes("其他人员修改")) await loadCase(previous.id);
    } finally {
      setSavingItemIds((current) => {
        const next = new Set(current);
        next.delete(item.id);
        return next;
      });
    }
  };
  const loadRouteCandidates = async (route: RegistrationRoute) => {
    if (!selected || routeCandidates[route.id]) return;
    try {
      const candidates = await api.registrationRouteCandidates(selected.id, route.id);
      setRouteCandidates((current) => ({ ...current, [route.id]: candidates }));
    } catch (requestError) {
      message.error(errorMessage(requestError));
    }
  };
  const saveRoutes = async (routes: RegistrationRoute[], previous: RegistrationCase) => {
    setRouteSaving(true);
    applyCase(withCompletionState({ ...previous, status: "processing", statusLabel: "处理中", routes }));
    try {
      applyCase(await api.updateRegistrationRoutes(previous.id, {
        version: previous.version, idempotencyKey: key(),
        routes: routes.map((route) => ({ routeId: route.id, selected: route.selected, assigneeUserId: route.assigneeUserId })),
      }));
    } catch (requestError) {
      applyCase(previous);
      const text = errorMessage(requestError); message.error(text);
      if (text.includes("其他人员修改")) await loadCase(previous.id);
    } finally { setRouteSaving(false); }
  };
  const toggleRoute = async (route: RegistrationRoute, checked: boolean) => {
    if (!selected?.routes) return;
    const previous = selected;
    const routes = selected.routes.map((item) => item.id === route.id
      ? { ...item, selected: checked, assigneeUserId: checked ? item.assigneeUserId : undefined, assigneeUserName: checked ? item.assigneeUserName : undefined }
      : item);
    if (checked) {
      applyCase(withCompletionState({ ...selected, routes }));
      await loadRouteCandidates(route);
      return;
    }
    await saveRoutes(routes, previous);
  };
  const assignRoute = async (route: RegistrationRoute, assigneeUserId: number) => {
    if (!selected?.routes) return;
    const candidate = routeCandidates[route.id]?.find((item) => item.id === assigneeUserId);
    await saveRoutes(selected.routes.map((item) => item.id === route.id
      ? { ...item, selected: true, assigneeUserId, assigneeUserName: candidate?.nickname }
      : item), selected);
  };
  const uploadAttachment = async (item: NonNullable<RegistrationCase["items"]>[number], file: File) => {
    if (!selected) return;
    if (file.size > 20 * 1024 * 1024 || !/\.(jpe?g|png|webp|pdf|docx?|xlsx?)$/i.test(file.name)) {
      message.error("仅支持 JPG、PNG、WebP、PDF、Word、Excel，单个文件不超过 20 MB"); return;
    }
    setAttachmentSavingIds((current) => new Set(current).add(item.id));
    try {
      const uploaded = await api.uploadRegistrationAttachment(selected.id, item.id, file, selected.version);
      applyCase(withCompletionState({ ...selected, version: uploaded.version, items: selected.items?.map((current) => current.id === item.id
        ? { ...current, checked: true, attachments: [...(current.attachments || []), { ...uploaded, uploadedByUserId: 0 }] }
        : current) }));
    } catch (requestError) { message.error(errorMessage(requestError)); }
    finally { setAttachmentSavingIds((current) => { const next = new Set(current); next.delete(item.id); return next; }); }
  };
  const deleteAttachment = async (itemId: number, attachmentId: number) => {
    if (!selected) return;
    setAttachmentSavingIds((current) => new Set(current).add(itemId));
    try { applyCase(await api.deleteRegistrationAttachment(selected.id, itemId, attachmentId, { version: selected.version, idempotencyKey: key() })); }
    catch (requestError) { message.error(errorMessage(requestError)); }
    finally { setAttachmentSavingIds((current) => { const next = new Set(current); next.delete(itemId); return next; }); }
  };
  const complete = async () => {
    if (!selected) return;
    if (!selected.completable) {
      message.warning(selected.completionBlockReason || "当前任务暂时不能完成");
      return;
    }
    setCompleting(true);
    try {
      await api.completeRegistration(selected.id, {
        version: selected.version,
        idempotencyKey: key(),
      });
      message.success("报名履约已完成");
      await load(pageNo);
    } catch (requestError) {
      message.error(errorMessage(requestError));
    } finally {
      setCompleting(false);
    }
  };

  const detailContent = detailLoading ? (
    <Skeleton active paragraph={{ rows: 10 }} />
  ) : detailError ? (
    <LoadState
      error={detailError}
      retry={() => selected?.id && void loadCase(selected.id)}
    />
  ) : selected ? (
    <div className="registration-detail">
      <div className="registration-detail-hero">
        <div>
          <Typography.Title level={4}>
            {selected.studentName || "未填写学员姓名"}
          </Typography.Title>
          <Typography.Text type="secondary">
            {selected.orderNo || `订单 ${selected.orderId}`}
          </Typography.Text>
        </div>
        <Tag
          color={
            selected.status === "completed"
              ? "success"
              : selected.status === "cancelled"
                ? "default"
                : "processing"
          }
        >
          {selected.statusLabel || "未知状态"}
        </Tag>
      </div>
      <section className="registration-summary-card">
        <DetailFieldGrid items={[
          { key: "order", label: "订单号", value: selected.orderNo },
          { key: "orderStatus", label: "订单状态", value: <Tag>{selected.orderStatusLabel || "未知状态"}</Tag> },
          { key: "lead", label: "客资编号", value: selected.leadNo },
          { key: "mobile", label: "学员手机号", value: selected.studentMobile },
        ]}/>
      </section>
      {selected.completionBlockReason && (
        <Alert
          className="registration-block-alert"
          type="warning"
          showIcon
          title={selected.completionBlockReason}
        />
      )}
      <section className="registration-checklist-card">
        <Typography.Title level={5}>学员流转</Typography.Title>
        <div className="registration-checklist">
          {selected.routes?.map((route) => (
            <div className="registration-checklist-row registration-route-row" key={route.id}>
              <Checkbox
                checked={route.selected}
                disabled={routeSaving || ["completed", "cancelled"].includes(selected.status)}
                onChange={(event) => void toggleRoute(route, event.target.checked)}
              >
                {route.departmentName}
              </Checkbox>
              {route.selected ? (
                <Select
                  value={route.assigneeUserId}
                  placeholder={`选择${route.assigneeTypeLabel}`}
                  loading={routeSaving || !routeCandidates[route.id]}
                  disabled={routeSaving || ["completed", "cancelled"].includes(selected.status)}
                  options={(routeCandidates[route.id] || (route.assigneeUserId ? [{ id: route.assigneeUserId, nickname: route.assigneeUserName || "已分配负责人" }] : [])).map((candidate) => ({ value: candidate.id, label: candidate.nickname }))}
                  onOpenChange={(open) => open && void loadRouteCandidates(route)}
                  onChange={(value) => void assignRoute(route, value)}
                />
              ) : <Typography.Text type="secondary">未选择</Typography.Text>}
            </div>
          ))}
        </div>
      </section>
      <section className="registration-checklist-card">
        <Typography.Title level={5}>履约清单</Typography.Title>
        <div className="registration-checklist">
          {selected.items?.map((item) =>
            item.itemType === "study_planner" ? (
              <div className="registration-checklist-row" key={item.id}>
                <div className="registration-checklist-copy">
                  <strong>{item.title}</strong>
                  <span>由“学员流转”中的学生服务部门负责人自动满足</span>
                </div>
                <Tag>{selected.studyPlannerUserName || "未选择学生服务部门"}</Tag>
              </div>
            ) : item.itemType === "attachment" ? (
              <div className="registration-checklist-row registration-attachment-row" key={item.id}>
                <div className="registration-checklist-copy">
                  <strong>{item.title}{item.attachmentRequired ? "（必传）" : "（选传）"}</strong>
                  <span>最多 9 个文件，单个不超过 20 MB</span>
                  <div className="registration-attachment-list">
                    {item.attachments?.map((attachment) => (
                      <div className="registration-attachment-entry" key={attachment.id}>
                        <RegistrationAttachmentPreview attachment={attachment} />
                        {! ["completed", "cancelled"].includes(selected.status) && (
                          <Button type="text" danger size="small" icon={<DeleteOutlined />} aria-label={`删除 ${attachment.originalName}`}
                            onClick={() => void deleteAttachment(item.id, attachment.id)} />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
                <Upload showUploadList={false} accept=".jpg,.jpeg,.png,.webp,.pdf,.doc,.docx,.xls,.xlsx"
                  disabled={attachmentSavingIds.has(item.id) || (item.attachments?.length || 0) >= 9 || ["completed", "cancelled"].includes(selected.status)}
                  beforeUpload={(file) => { void uploadAttachment(item, file); return Upload.LIST_IGNORE; }}>
                  <Button icon={<UploadOutlined />} loading={attachmentSavingIds.has(item.id)}>上传附件</Button>
                </Upload>
              </div>
            ) : (
              <div className="registration-checklist-row" key={item.id}>
                <Checkbox
                  checked={item.checked}
                  disabled={
                    savingItemIds.has(item.id) ||
                    ["completed", "cancelled"].includes(selected.status)
                  }
                  onChange={(event) =>
                    void updateItem(item, event.target.checked)
                  }
                >
                  {item.title}
                </Checkbox>
                <span>
                  {savingItemIds.has(item.id)
                    ? "保存中"
                    : item.checkedByUserName
                      ? `${item.checkedByUserName} · ${formatTimestamp(item.checkedAt)}`
                      : "待完成"}
                </span>
              </div>
            ),
          )}
        </div>
      </section>
      <div className="registration-detail-actions">
        {selected.status !== "completed" && selected.status !== "cancelled" ? <Button
          type="primary"
          disabled={!selected.completable || completing}
          loading={completing}
          onClick={() => void complete()}
        >完成报名履约</Button> : null}
      </div>
    </div>
  ) : (
    <Empty description="从左侧选择一条报名履约任务" />
  );

  return (
    <section className="workspace-page registration-page">
      <header className="registration-filter-shell">
        <div>
          <Typography.Title level={4}>报名履约公共池</Typography.Title>
          <Typography.Text type="secondary">
            协作完成成交后的学员入学事项
          </Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void load(1)}>刷新</Button>
      </header>
      <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar">
            <AdvancedFilterToolbar
              scene="registration"
              placeholder="搜索订单号、客资编号、姓名或手机号"
              keyword={keyword}
              value={advancedFilter}
              onKeyword={(value) => {
                setSelected(undefined);
                setPageNo(1);
                setKeyword(value);
              }}
              onChange={(value) => {
                setSelected(undefined);
                setPageNo(1);
                setAdvancedFilter(value);
              }}
            />
          </div>
          {error && <LoadState error={error} retry={() => void load(pageNo)} />}
          <div className="lead-inbox-scroll">
            {loading && !rows.length ? (
              Array.from({ length: 5 }, (_, index) => (
                <div className="lead-inbox-item" key={index}>
                  <Skeleton active avatar paragraph={{ rows: 2 }} />
                </div>
              ))
            ) : !rows.length && !error ? (
              <Empty description="当前筛选下暂无报名履约任务" />
            ) : (
              rows.map((row) => (
                <button
                  type="button"
                  key={row.id}
                  className={`lead-inbox-item${selected?.id === row.id ? " active" : ""}`}
                  onClick={() => void loadCase(row.id)}
                >
                  <div className="lead-inbox-item-main">
                    <NameAvatar name={row.studentName || "学员"} size={36} />
                    <div className="lead-inbox-item-copy">
                      <div className="lead-inbox-item-title">
                        <strong>{row.studentName || "未填写姓名"}</strong>
                        <Tag>{row.statusLabel || "未知状态"}</Tag>
                      </div>
                      <span>{row.orderNo || `订单 ${row.orderId}`}</span>
                      <span>{row.leadNo || "暂无客资编号"}</span>
                    </div>
                  </div>
                  <div className="lead-inbox-item-meta">
                    <Badge
                      status={
                        row.orderStatus === "effective"
                          ? "success"
                          : "processing"
                      }
                    />
                    <span>
                      {row.orderStatusLabel || "未知状态"} ·{" "}
                      {formatTimestamp(row.registrationApprovedAt)}
                    </span>
                  </div>
                </button>
              ))
            )}
          </div>
          {total > PAGE_SIZE && (
            <Pagination
              className="registration-pagination"
              simple
              current={pageNo}
              pageSize={PAGE_SIZE}
              total={total}
              onChange={(value) => void load(value)}
            />
          )}
        </aside>
        <main className="lead-inbox-detail-pane">{detailContent}</main>
      </div>
    </section>
  );
}

export function MyStudentsPage({ permissions = [] }: { permissions?: string[] }) {
  const location = useLocation();
  const taskTarget = location.state as { personId?: number; serviceRelationId?: number; openContactTask?: boolean; taskId?: number; taskType?: string } | null;
  const requestedPersonId = Number(taskTarget?.personId) || undefined;
  const requestedServiceId = Number(taskTarget?.serviceRelationId) || undefined;
  const [rows, setRows] = useState<MyStudent[]>([]),
    [selected, setSelected] = useState<MyStudent>();
  const [leadDetail, setLeadDetail] = useState<ManagedLead>();
  const [selectedServiceId, setSelectedServiceId] = useState<number>();
  const [repurchaseOpen, setRepurchaseOpen] = useState(false);
  const [studentContactContext, setStudentContactContext] = useState<import("../services/api").StudentContactContext>();
  const [studentContactRecords, setStudentContactRecords] = useState<import("../services/api").StudentContactRecord[]>([]);
  const [categories, setCategories] = useState<DictData[]>([]);
  const [channels, setChannels] = useState<DictData[]>([]);
  const [categoryError, setCategoryError] = useState(false);
  const [channelError, setChannelError] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [serviceStatus, setServiceStatus] = useState<'active' | 'paused' | 'completed'>();
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [pageNo, setPageNo] = useState(1),
    [total, setTotal] = useState(0);
  const [error, setError] = useState(""),
    [detailError, setDetailError] = useState("");
  const [loading, setLoading] = useState(false),
    [detailLoading, setDetailLoading] = useState(false);
  const listGeneration = useRef(0);
  const forcedListSequence = useRef(0);
  const detailGeneration = useRef(0);
  const dictionaryGeneration = useRef(0);
  const inflightLists = useRef(new Set<string>());
  const resetSelection = () => {
    detailGeneration.current += 1;
    setRows([]); setSelected(undefined); setSelectedServiceId(undefined); setLeadDetail(undefined);
    setStudentContactContext(undefined); setStudentContactRecords([]); setDetailError("");
  };
  const loadStudent = useCallback(async (personId: number, preferredServiceId?: number) => {
    const generation = ++detailGeneration.current;
    setDetailLoading(true);
    setDetailError("");
    try {
      const student = await api.myStudent(personId);
      if (generation !== detailGeneration.current) return;
      setSelected(student);
      const service = student.services.find(item => item.serviceRelationId === (preferredServiceId || selectedServiceId)) || student.services[0];
      setSelectedServiceId(service?.serviceRelationId);
      const leadId = service?.leadId;
      const [lead, context, records] = await Promise.all([
        leadId ? api.managedLead(leadId) : Promise.resolve(undefined),
        service ? api.studentContactContext(service.serviceRelationId) : Promise.resolve(undefined),
        service ? api.studentContactRecords(service.serviceRelationId, 1, 100) : Promise.resolve({ list: [], total: 0 }),
      ]);
      if (generation === detailGeneration.current) {
        setLeadDetail(lead);
        setStudentContactContext(context);
        setStudentContactRecords(records.list);
      }
    } catch (requestError) {
      if (generation !== detailGeneration.current) return;
      setLeadDetail(undefined);
      setStudentContactContext(undefined);
      setStudentContactRecords([]);
      setDetailError(errorMessage(requestError));
    } finally {
      if (generation === detailGeneration.current) setDetailLoading(false);
    }
  }, [selectedServiceId]);
  const targetNavigationKey = location.key;
  useEffect(() => {
    const targetKey = requestedPersonId ? `person:${requestedPersonId}`
      : requestedServiceId ? `service:${requestedServiceId}` : "";
    if (!targetKey) return;
    if (requestedPersonId) {
      void loadStudent(requestedPersonId);
      return;
    }
    void api.myStudentByService(requestedServiceId!).then(student => {
      setSelectedServiceId(requestedServiceId);
      return loadStudent(student.personId, requestedServiceId);
    }).catch(requestError => setDetailError(errorMessage(requestError)));
  }, [loadStudent, requestedPersonId, requestedServiceId, targetNavigationKey]);
  const loadDictionaries = useCallback(async () => {
    const generation = ++dictionaryGeneration.current;
    const [categoryResult, channelResult] = await Promise.allSettled([
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL),
    ]);
    if (generation !== dictionaryGeneration.current) return;
    if (categoryResult.status === "fulfilled") { setCategories(categoryResult.value); setCategoryError(false); }
    else setCategoryError(true);
    if (channelResult.status === "fulfilled") { setChannels(channelResult.value); setChannelError(false); }
    else setChannelError(true);
  }, []);
  useEffect(() => { void loadDictionaries(); }, [loadDictionaries]);
  const load = useCallback(
    async (targetPage = pageNo, options: { force?: boolean; reloadDetail?: boolean } = {}) => {
      const baseRequestKey = `${targetPage}:${keyword}:${serviceStatus || ''}:${JSON.stringify(advancedFilter)}`;
      if (!options.force && inflightLists.current.has(baseRequestKey)) return;
      const requestKey = options.force
        ? `${baseRequestKey}:force:${++forcedListSequence.current}`
        : baseRequestKey;
      inflightLists.current.add(requestKey);
      const generation = ++listGeneration.current;
      setLoading(true);
      setError("");
      try {
        const page = await api.myStudents({
          pageNo: targetPage,
          pageSize: PAGE_SIZE,
          keyword: keyword || undefined,
          serviceStatus,
          advancedFilter,
        });
        if (generation !== listGeneration.current) return;
        setRows(page.list);
        setTotal(page.total);
        setPageNo(targetPage);
        if (options.reloadDetail !== false && !requestedPersonId) {
          const target = selected && page.list.some((item) => item.personId === selected.personId)
            ? selected.personId
            : page.list[0]?.personId;
          if (target) await loadStudent(target);
          else setSelected(undefined);
        }
      } catch (requestError) {
        if (generation === listGeneration.current) setError(errorMessage(requestError));
      } finally {
        inflightLists.current.delete(requestKey);
        if (generation === listGeneration.current) setLoading(false);
      }
    },
    [advancedFilter, keyword, loadStudent, pageNo, requestedPersonId, selected, serviceStatus],
  );
  useEffect(() => {
    void load(1);
  }, [advancedFilter, keyword, serviceStatus]);
  const selectedService = selected?.services.find(item => item.serviceRelationId === selectedServiceId) || selected?.services[0];
  const refreshCurrentStudent = useCallback(async () => {
    if (!selected) {
      await load(pageNo, { force: true });
      return;
    }
    await Promise.all([
      load(pageNo, { force: true, reloadDetail: false }),
      loadStudent(selected.personId, selectedService?.serviceRelationId),
    ]);
  }, [load, loadStudent, pageNo, selected, selectedService?.serviceRelationId]);
  const canStudentRepurchase = Boolean(selectedService && hasPermission(permissions, 'zsjos:sales-order:student-repurchase')
    && selectedService.owner && selectedService.acceptanceStatus === 'accepted'
    && ['active', 'paused', 'completed'].includes(selectedService.status));
  const selectService = async (relationId: number) => {
    const service = selected?.services.find(item => item.serviceRelationId === relationId);
    if (!service) return;
    setSelectedServiceId(relationId);
    setDetailLoading(true);
    setDetailError("");
    try {
      const [lead, context, records] = await Promise.all([
        service.leadId ? api.managedLead(service.leadId) : Promise.resolve(undefined),
        api.studentContactContext(service.serviceRelationId),
        api.studentContactRecords(service.serviceRelationId, 1, 100),
      ]);
      setLeadDetail(lead); setStudentContactContext(context); setStudentContactRecords(records.list);
    }
    catch (requestError) { setLeadDetail(undefined); setStudentContactContext(undefined); setStudentContactRecords([]); setDetailError(errorMessage(requestError)); }
    finally { setDetailLoading(false); }
  };
  const detailContent = detailLoading ? (
    <Skeleton active paragraph={{ rows: 10 }} />
  ) : detailError ? (
    <LoadState
      error={detailError}
      retry={() => {
        const personId = selected?.personId || requestedPersonId;
        if (personId) void loadStudent(personId);
      }}
    />
  ) : selected && selectedService && studentContactContext ? (
    <StudentPlannerOperations
      key={selectedService.serviceRelationId}
      student={selected}
      service={selectedService}
      context={studentContactContext}
      openTaskId={taskTarget?.openContactTask ? taskTarget.taskId : undefined}
      openTaskType={taskTarget?.openContactTask ? taskTarget.taskType : undefined}
      onRefresh={refreshCurrentStudent}
    >
      {(studentToolbarActions) => leadDetail ? <LeadDetail
      lead={{
        ...leadDetail,
        submittedName: selected.name ?? leadDetail.submittedName,
        submittedMobile: selected.mobile ?? leadDetail.submittedMobile,
        submittedWechatId: selected.wechatId ?? leadDetail.submittedWechatId,
      }}
      categories={categories}
      categoryLabel={(value) => dictionaryDisplayLabel(categories, value, categoryError)}
      channelLabel={(value) => dictionaryDisplayLabel(channels, value, channelError)}
      mode="student-readonly"
      autoExpandFollowUp={false}
      onDirtyChange={() => undefined}
      onChanged={() => void refreshCurrentStudent()}
      studentToolbarActions={canStudentRepurchase
         ? [...studentToolbarActions, { key: 'student-repurchase', icon: <PlusOutlined />, label: '录入复购', onClick: () => setRepurchaseOpen(true) }]
         : studentToolbarActions}
      contextHeader={selectedService ? <div style={{ marginBottom: 16 }}><Typography.Text strong>当前课程服务</Typography.Text><Select style={{ width: '100%', marginTop: 8 }} value={selectedService.serviceRelationId} onChange={value => void selectService(value)} options={selected.services.map(service => ({ value: service.serviceRelationId, label: `${service.courseName || service.skuName || '课程服务'} · ${service.orderNo || service.orderId}` }))}/></div> : undefined}
      studentContext={{ service: selectedService, contactContext: studentContactContext, contactRecords: studentContactRecords }}
      extraTabs={[
        { key: 'student-service', label: '课程服务', children: <section className="registration-summary-card"><DetailFieldGrid items={[{ key: 'course', label: '课程', value: selectedService.courseName || selectedService.skuName }, { key: 'sku', label: '具体方案', value: selectedService.skuName }, { key: 'category', label: '分类', value: selectedService.categoryPath?.join(' / ') }, { key: 'order', label: '订单号', value: selectedService.orderNo }, { key: 'status', label: '服务状态', value: serviceStatusLabel(selectedService.status) }, { key: 'director', label: '编导', value: selectedService.contentDirectorUserName || '未分配' }, { key: 'career', label: '职业规划师', value: selectedService.careerPlannerUserName || '未分配' }]} />{selectedService.attributeValues?.length ? <Space wrap style={{ marginTop: 12 }}>{selectedService.attributeValues.map(value => <Tag key={value}>{value}</Tag>)}</Space> : null}</section> },
        { key: 'student-contact', label: '联系记录', forceRender: true, children: <StudentContactDetail service={selectedService} /> }
      ]}
    /> : <StudentDetail
      student={selected}
      service={selectedService}
      contactContext={studentContactContext}
      contactRecords={studentContactRecords}
      toolbar={<OverflowToolbar actions={canStudentRepurchase
        ? [...studentToolbarActions, { key: 'student-repurchase', icon: <PlusOutlined />, label: '录入复购', onClick: () => setRepurchaseOpen(true) }]
        : studentToolbarActions} />}
      contextHeader={<div style={{ marginBottom: 16 }}><Typography.Text strong>当前课程服务</Typography.Text><Select style={{ width: '100%', marginTop: 8 }} value={selectedService.serviceRelationId} onChange={value => void selectService(value)} options={selected.services.map(service => ({ value: service.serviceRelationId, label: `${service.courseName || service.skuName || '课程服务'} · ${service.orderNo || service.orderId}` }))}/></div>}
      extraTabs={[
        { key: 'student-service', label: '课程服务', children: <section className="registration-summary-card"><DetailFieldGrid items={[{ key: 'course', label: '课程', value: selectedService.courseName || selectedService.skuName }, { key: 'sku', label: '具体方案', value: selectedService.skuName }, { key: 'category', label: '分类', value: selectedService.categoryPath?.join(' / ') }, { key: 'order', label: '订单号', value: selectedService.orderNo }, { key: 'status', label: '服务状态', value: serviceStatusLabel(selectedService.status) }, { key: 'director', label: '编导', value: selectedService.contentDirectorUserName || '未分配' }, { key: 'career', label: '职业规划师', value: selectedService.careerPlannerUserName || '未分配' }]} />{selectedService.attributeValues?.length ? <Space wrap style={{ marginTop: 12 }}>{selectedService.attributeValues.map(value => <Tag key={value}>{value}</Tag>)}</Space> : null}</section> },
        { key: 'student-contact', label: '联系记录', forceRender: true, children: <StudentContactDetail service={selectedService} /> }
      ]}
    />}
    </StudentPlannerOperations>
  ) : selected ? (
    <div className="registration-detail">
      <div className="registration-detail-hero">
        <div>
          <Typography.Title level={4}>
            {selected.name || "未填写姓名"}
          </Typography.Title>
          <Typography.Text type="secondary">
            {selected.leadNo || "暂无客资编号"}
          </Typography.Text>
        </div>
      </div>
      <section className="registration-summary-card">
        <DetailFieldGrid items={[
          { key: "mobile", label: "手机号", value: selected.mobile },
          { key: "wechat", label: "微信号", value: selected.wechatId },
        ]}/>
      </section>
      <section className="registration-checklist-card">
        <Typography.Title level={5}>课程权益</Typography.Title>
        <div className="registration-service-list">
          {selected.services.map((service) => (
            <div
              className="registration-service-item"
              key={service.serviceRelationId}
            >
              <div className="registration-service-copy">
                <strong>
                  {service.courseName || service.skuName || "课程服务"}
                </strong>
                {service.skuName && service.skuName !== service.courseName && (
                  <span>{service.skuName}</span>
                )}
                <span>
                  {service.categoryPath?.length
                    ? service.categoryPath.join(" / ")
                    : "课程分类暂未记录"}
                </span>
                {Boolean(service.attributeValues?.length) && (
                  <Space size={[4, 4]} wrap>
                    {service.attributeValues!.map((value) => (
                      <Tag key={value} variant="filled">
                        {value}
                      </Tag>
                    ))}
                  </Space>
                )}
                <span>{service.orderNo || `订单 ${service.orderId}`}</span>
              </div>
              <Tag color={service.status === "active" ? "success" : undefined}>
                {serviceStatusLabel(service.status)}
              </Tag>
            </div>
          ))}
        </div>
      </section>
    </div>
  ) : (
    <Empty description="从左侧选择一名学员" />
  );
  return (
    <section className="workspace-page registration-page">
      <header className="registration-filter-shell">
        <div>
          <Typography.Title level={4}>我的学员</Typography.Title>
          <Typography.Text type="secondary">
            查看当前负责的学员及课程权益
          </Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => { void refreshCurrentStudent(); void loadDictionaries(); }}>刷新</Button>
      </header>
      <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar">
            <Select allowClear value={serviceStatus} placeholder="全部服务状态" style={{ width: '100%', marginBottom: 8 }}
              onChange={value => { resetSelection(); setPageNo(1); setServiceStatus(value); }}
              options={[{ value: 'active', label: '服务中' }, { value: 'paused', label: '已暂停' }, { value: 'completed', label: '已结业' }]}/>
            <AdvancedFilterToolbar
              scene="student"
              placeholder="搜索姓名、手机号或客资编号"
              keyword={keyword}
              value={advancedFilter}
              onKeyword={(value) => {
                resetSelection();
                setPageNo(1);
                setKeyword(value);
              }}
              onChange={(value) => {
                resetSelection();
                setPageNo(1);
                setAdvancedFilter(value);
              }}
            />
          </div>
          {error && <LoadState error={error} retry={() => void load(pageNo)} />}
          <div className="lead-inbox-scroll">
            {loading && !rows.length ? (
              Array.from({ length: 5 }, (_, index) => (
                <div className="lead-inbox-item" key={index}>
                  <Skeleton active avatar paragraph={{ rows: 2 }} />
                </div>
              ))
            ) : !rows.length && !error ? (
              <Empty description="当前筛选下暂无学员" />
            ) : (
              rows.map((row) => (
                <button
                  type="button"
                  key={row.personId}
                  className={`lead-inbox-item${selected?.personId === row.personId ? " active" : ""}`}
                  onClick={() => void loadStudent(row.personId)}
                >
                  <div className="lead-inbox-item-main">
                    <NameAvatar name={row.name || "学员"} size={36} />
                    <div className="lead-inbox-item-copy">
                      <div className="lead-inbox-item-title">
                        <strong>{row.name || "未填写姓名"}</strong>
                        <Tag color="success">学员</Tag>
                      </div>
                      <span>{row.leadNo || "暂无客资编号"}</span>
                      <span>
                        {row.mobile || "无手机号"} ·{" "}
                        {row.wechatId || "无微信号"}
                      </span>
                    </div>
                  </div>
                </button>
              ))
            )}
          </div>
          {total > PAGE_SIZE && (
            <Pagination
              className="registration-pagination"
              simple
              current={pageNo}
              pageSize={PAGE_SIZE}
              total={total}
              onChange={(value) => void load(value)}
            />
          )}
        </aside>
        <main className="lead-inbox-detail-pane">{detailContent}</main>
      </div>
      {repurchaseOpen && selected && <SalesOrderEntryModal
        lead={{ id: selected.personId, submittedName: selected.name || '', submittedMobile: selected.mobile, submittedWechatId: selected.wechatId,
          provinceCode: leadDetail?.provinceCode, provinceName: leadDetail?.provinceName, cityCode: leadDetail?.cityCode, cityName: leadDetail?.cityName }}
        repurchase studentRepurchase open={repurchaseOpen}
        onClose={() => setRepurchaseOpen(false)} onSubmitted={async () => { setRepurchaseOpen(false); await refreshCurrentStudent(); }}
      />}
    </section>
  );
}

function StudentContactForm({
  relationId,
  context,
  onDone,
}: { relationId: number; context: import("../services/api").StudentContactContext; onDone: () => void | Promise<void> }) {
  const [form] = Form.useForm();
  const [reasons, setReasons] = useState<DictData[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [attachmentUploads, setAttachmentUploads] = useState<Array<{ uid: string; name: string; status: "uploading" | "done" | "error"; url?: string; fileId?: number }>>([]);
  const [extensionReasons, setExtensionReasons] = useState<DictData[]>([]);
  const taskType = context.currentTask?.type;
  const [fieldDicts, setFieldDicts] = useState<Record<string, DictData[]>>({});
  useEffect(() => { void Promise.all([api.dictDataByType(DICT_TYPE.STUDENT_CONTACT_UNSUCCESSFUL_REASON), api.dictDataByType("zsjos_student_contact_extension_reason")]).then(([a,b]) => { setReasons(a); setExtensionReasons(b); }).catch(() => { setReasons([]); setExtensionReasons([]); }); }, []);
  useEffect(() => {
    const fields = context.formFields || [];
    const enumTypes = new Set(["dict", "select", "multi_select", "radio", "checkbox_group"]);
    const types = [...new Set(fields.filter(field => enumTypes.has(field.type) && field.dictType).map(field => field.dictType as string))];
    if (!types.length) return;
    void Promise.all(types.map(async type => [type, await api.dictDataByType(type)] as const))
      .then(rows => setFieldDicts(Object.fromEntries(rows)))
      .catch(() => setFieldDicts({}));
  }, [context.formFields]);
  if (!context.currentTask) return <Alert type="info" showIcon title="当前没有待处理联系任务" />;
  const submit = async (values: Record<string, unknown>) => {
    if (attachmentUploads.some(item => item.status !== "done" || item.fileId == null)) {
      message.warning("请等待附件上传完成，或移除上传失败的附件");
      return;
    }
    setSubmitting(true);
    try {
      const nextContactAt = typeof values.nextContactAt === "object" && values.nextContactAt !== null
        ? Number((values.nextContactAt as { valueOf: () => number }).valueOf())
        : new Date(String(values.nextContactAt)).getTime();
      const next = new Date(nextContactAt);
      const successful = values.successful === true;
      const nextTaskType = taskType === "student_first_contact" ? (successful ? "student_study_plan" : "student_first_contact") : taskType === "student_study_plan" ? (successful ? "student_contact" : "student_study_plan") : "student_contact";
      const timeout = nextTaskType === "student_first_contact" ? context.firstContactTimeoutMinutes : nextTaskType === "student_study_plan" ? context.studyPlanTimeoutMinutes : 0;
      const extensionRequired = Boolean(timeout && next.getTime() > Date.now() + timeout * 60000);
      const attachmentFileIds = attachmentUploads.map(item => item.fileId as number);
      const configuredKeys = new Set((context.formFields || []).map(field => field.key));
      const data = Object.fromEntries(Object.entries(values).filter(([field]) => configuredKeys.has(field)));
      const payload = { ...values, data, nextContactAt, taskId: context.currentTask!.id, idempotencyKey: key(), attachmentFileIds, extensionAttachmentFileIds: extensionRequired ? attachmentFileIds : [], extensionReasonValue: extensionRequired ? values.extensionReasonValue : undefined, extensionDescription: extensionRequired ? values.extensionDescription : undefined };
      if (taskType === "student_first_contact") await api.studentFirstContact(relationId, payload);
      else if (taskType === "student_study_plan") await api.studentStudyPlan(relationId, payload);
      else await api.studentContact(relationId, payload);
      message.success("任务已提交"); form.resetFields(); setAttachmentUploads([]); await onDone();
    } catch (error) { message.error(errorMessage(error)); }
    finally { setSubmitting(false); }
  };
  return <Form form={form} layout="vertical" onFinish={submit}>
    <Form.Item name="successful" label="是否成功联系上学员" rules={[{ required: true }]}><Radio.Group options={[{ label: "是", value: true }, { label: "否", value: false }]} /></Form.Item>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue("successful") === false ? <Form.Item name="unsuccessfulReasonValue" label="未联系原因" rules={[{ required: true }]}><Select options={reasons.map(row => ({ label: row.label, value: row.value }))} placeholder={reasons.length ? "选择管理员配置的原因" : "暂无可用原因，请联系管理员"} /></Form.Item> : null}</Form.Item>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => taskType === "student_first_contact" && getFieldValue("successful") === true ? <Form.Item name="completedChecklistKeys" label="首联任务清单" rules={[{ required: true, type: "array", min: 1 }]}><Checkbox.Group options={context.firstContactChecklist.map(item => ({ label: item.title, value: item.key }))} /></Form.Item> : null}</Form.Item>
    {(context.formFields || []).map(field => {
      const rules = field.required ? [{ required: true, message: `请填写${field.title}` }] : [];
      const dictOptions = field.dictType ? (fieldDicts[field.dictType] || []).map(item => ({ label: item.label, value: item.value })) : [];
      let control: ReactNode = <Input />;
      if (field.type === "textarea") control = <Input.TextArea rows={3} />;
      else if (field.type === "number") control = <Input type="number" />;
      else if (field.type === "date") control = <DatePicker style={{ width: "100%" }} />;
      else if (field.type === "datetime") control = <DatePicker showTime style={{ width: "100%" }} />;
      else if (field.type === "radio") control = <Radio.Group options={dictOptions} />;
      else if (field.type === "checkbox") control = <Checkbox />;
      else if (field.type === "checkbox_group") control = <Checkbox.Group options={dictOptions} />;
      else if (["dict", "select", "multi_select"].includes(field.type)) control = <Select options={dictOptions} loading={Boolean(field.dictType && !fieldDicts[field.dictType])} placeholder={dictOptions.length ? "请选择" : "暂无可用字典项，请重试"} mode={field.multiple || field.type === "multi_select" ? "multiple" : undefined} />;
      return <Form.Item key={field.key} name={field.key} label={field.title} rules={rules} extra={field.description}>{control}</Form.Item>;
    })}
    {context.quickNotes.length > 0 && <Form.Item label="快捷备注"><Space wrap>{context.quickNotes.map(note => <Button key={note} size="small" onClick={() => { const current = String(form.getFieldValue("remark") || "").trimEnd(); form.setFieldValue("remark", current ? `${current}\n${note}` : note); }}>{note}</Button>)}</Space></Form.Item>}
    <Form.Item name="remark" label="备注" rules={[{ required: true }]}><Input.TextArea rows={4} maxLength={2000} showCount /></Form.Item>
    <Form.Item name="nextContactAt" label="下次联系时间" rules={[{ required: true }]}><DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: "100%" }} /></Form.Item>
    <Space wrap className="student-contact-time-shortcuts">{[1, 2, 3, 5, 7, 14, 30].map(days => <Button key={days} size="small" onClick={() => form.setFieldValue("nextContactAt", dayjs().add(days, "day"))}>+{days} 天</Button>)}</Space>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => { const value = getFieldValue("nextContactAt"); const successful = getFieldValue("successful") === true; const nextTaskType = taskType === "student_first_contact" ? (successful ? "student_study_plan" : "student_first_contact") : taskType === "student_study_plan" ? (successful ? "student_contact" : "student_study_plan") : "student_contact"; const timeout = nextTaskType === "student_first_contact" ? context.firstContactTimeoutMinutes : nextTaskType === "student_study_plan" ? context.studyPlanTimeoutMinutes : 0; const extended = Boolean(value && timeout && new Date(String(value)).getTime() > Date.now() + timeout * 60000); return extended ? <><Alert type="warning" showIcon title={`超过允许时限（${timeout} 分钟），将发起延期审批`} /><Form.Item name="extensionReasonValue" label="延期原因" rules={[{ required: true }]}><Select options={extensionReasons.map(row => ({ label: row.label, value: row.value }))} /></Form.Item><Form.Item name="extensionDescription" label="延期说明" rules={[{ required: true }]}><Input.TextArea rows={3} maxLength={1000} /></Form.Item></> : null; }}</Form.Item>
    <Space wrap><Upload multiple fileList={attachmentUploads} beforeUpload={async file => { setAttachmentUploads(items => [...items.filter(item => item.uid !== file.uid), { uid: file.uid, name: file.name, status: "uploading" }]); try { const uploaded = await api.studentContactUpload(relationId, file); setAttachmentUploads(items => items.map(item => item.uid === file.uid ? { ...item, status: "done", url: uploaded.url, fileId: uploaded.fileId } : item)); message.success(`${file.name}已上传`); } catch (error) { setAttachmentUploads(items => items.map(item => item.uid === file.uid ? { ...item, status: "error" } : item)); message.error(errorMessage(error)); } return false; }} onRemove={file => { setAttachmentUploads(items => items.filter(item => item.uid !== file.uid)); return true; }}><Button icon={<UploadOutlined />}>添加附件</Button></Upload>{attachmentUploads.length > 0 && <Tag>{attachmentUploads.length} 个附件</Tag>}</Space>
    <Space><Button type="primary" htmlType="submit" loading={submitting} disabled={attachmentUploads.some(item => item.status !== "done")}>提交{taskType === "student_first_contact" ? "首联" : taskType === "student_study_plan" ? "学习计划" : "普通跟进"}</Button></Space>
  </Form>;
}

function StudentPlannerOperations({ student, service, context, openTaskId, openTaskType, onRefresh, children }: {
  student: MyStudent;
  service: MyStudent["services"][number];
  context: import("../services/api").StudentContactContext;
  openTaskId?: number;
  openTaskType?: string;
  onRefresh: () => Promise<void>;
  children: (actions: ToolbarAction[]) => ReactNode;
}) {
  const [contactOpen, setContactOpen] = useState(false);
  const [basicInfoOpen, setBasicInfoOpen] = useState(false);
  const [basicInfoSaving, setBasicInfoSaving] = useState(false);
  const [basicInfoForm] = Form.useForm();
  const [assignmentType, setAssignmentType] = useState<"content_director" | "career_planner">();
  const [candidates, setCandidates] = useState<StudyPlanner[]>([]);
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [candidateError, setCandidateError] = useState("");
  const [candidateUserId, setCandidateUserId] = useState<number>();
  const [correctionReason, setCorrectionReason] = useState("");
  const [assignmentSaving, setAssignmentSaving] = useState(false);
  const [deliveryOpen, setDeliveryOpen] = useState(false);
  const [deliverySaving, setDeliverySaving] = useState(false);
  const [deliveryForm] = Form.useForm();
  const [examDateForm] = Form.useForm();
  const [examDateOpen, setExamDateOpen] = useState(false);
  const [examDateSaving, setExamDateSaving] = useState(false);
  const deliveryIdempotencyKey = useRef<string | undefined>(undefined);
  const available = context.availableActions || [];
  const stageAction = available.find(action => ["FIRST_CONTACT", "STUDY_PLAN", "FOLLOW_UP"].includes(action));
  const stageLabel = stageAction === "FIRST_CONTACT" ? "首联" : stageAction === "STUDY_PLAN" ? "制定学习计划" : "普通跟进";
  const taskOpened = useRef(false);
  useEffect(() => {
    if (taskOpened.current || !openTaskId || !context.currentTask) return;
    taskOpened.current = true;
    if (context.currentTask.id === openTaskId
        && (!openTaskType || context.currentTask.type === openTaskType) && stageAction) {
      setContactOpen(true);
    } else {
      message.warning("该待办已变化，请按当前学员任务状态处理");
    }
  }, [context.currentTask, openTaskId, openTaskType, stageAction]);

  const accept = () => Modal.confirm({
    title: "确认接收学员",
    content: "接收后将立即生成首联任务。",
    okText: "接收",
    cancelText: "取消",
    onOk: async () => {
      try {
        await api.studentAccept(service.serviceRelationId, context.version, key());
        message.success("已接收，首联任务已生成");
        await onRefresh();
      } catch (error) {
        message.error(errorMessage(error));
        throw error;
      }
    },
  });
  const openBasicInfo = () => {
    basicInfoForm.setFieldsValue({ name: student.name, mobile: student.mobile, wechatId: student.wechatId, reason: "" });
    setBasicInfoOpen(true);
  };
  const saveBasicInfo = async (values: { name: string; mobile?: string; wechatId?: string; reason: string }) => {
    if (!values.mobile?.trim() && !values.wechatId?.trim()) {
      message.warning("手机号和微信号至少填写一个");
      return;
    }
    setBasicInfoSaving(true);
    try {
      await api.studentUpdateBasicInfo(service.serviceRelationId, {
        name: values.name.trim(), mobile: values.mobile?.trim() || undefined,
        wechatId: values.wechatId?.trim() || undefined, reason: values.reason.trim(),
      });
      message.success("学员基础信息已更新");
      setBasicInfoOpen(false);
      await onRefresh();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBasicInfoSaving(false);
    }
  };
  const loadCandidates = async (type: "content_director" | "career_planner") => {
    setCandidateLoading(true); setCandidateError("");
    try { setCandidates(await api.studentCollaboratorCandidates(service.serviceRelationId, type)); }
    catch (error) { setCandidates([]); setCandidateError(errorMessage(error)); }
    finally { setCandidateLoading(false); }
  };
  const openAssignment = (type: "content_director" | "career_planner") => {
    setAssignmentType(type);
    setCandidateUserId(type === "content_director" ? service.contentDirectorUserId : service.careerPlannerUserId);
    setCorrectionReason("");
    void loadCandidates(type);
  };
  const assignedUserId = assignmentType === "content_director" ? context.contentDirectorUserId : context.careerPlannerUserId;
  const assign = async () => {
    if (!assignmentType || !candidateUserId) { message.warning("请选择协作者"); return; }
    if (assignedUserId && !correctionReason.trim()) { message.warning("纠正分配时请填写原因"); return; }
    setAssignmentSaving(true);
    try {
      await api.studentAssignCollaborator(service.serviceRelationId, {
        collaboratorType: assignmentType, userId: candidateUserId, version: context.version,
        idempotencyKey: key(), correctionReason: assignedUserId ? correctionReason.trim() : undefined,
      });
      message.success("协作者已分配"); setAssignmentType(undefined); await onRefresh();
    } catch (error) { message.error(errorMessage(error)); }
    finally { setAssignmentSaving(false); }
  };
  const currentDeliveryStage = context.deliveryStages?.find(item => item.current);
  const saveExamDate = async (values: { examDate: unknown }) => {
    if (!values.examDate) return;
    setExamDateSaving(true);
    try {
      const timestamp = Number((values.examDate as { valueOf: () => number }).valueOf());
      const date = new Date(timestamp);
      const iso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
      await api.studentExamDate(service.serviceRelationId, { examDate: iso, version: context.version, idempotencyKey: key() });
      message.success("考试时间已更新"); setExamDateOpen(false); await onRefresh();
    } catch (error) { message.error(errorMessage(error)); }
    finally { setExamDateSaving(false); }
  };
  const submitDeliveryStage = async (values: { remark: string; data: string }) => {
    if (!currentDeliveryStage) return;
    let parsed: Record<string, unknown> = {};
    try {
      const value: unknown = values.data?.trim() ? JSON.parse(values.data) : {};
      if (value === null || typeof value !== "object" || Array.isArray(value)) {
        message.warning("阶段事实必须是 JSON 对象"); return;
      }
      parsed = value as Record<string, unknown>;
    }
    catch { message.warning("阶段事实必须是合法 JSON"); return; }
    const requiredFacts: Record<string, Record<string, "boolean" | "string">> = {
      exam_confirmation: { examIntention: "string", examDate: "string" },
      exam_preparation: { examNoticeSent: "boolean", admissionTicketNoticeSent: "boolean" },
      post_exam: { examFeedback: "string" }, result: { result: "string" },
      certificate: { certificateNotice: "string", mailingInfo: "string" },
    };
    const schema = requiredFacts[currentDeliveryStage.code] || {};
    if (Object.entries(schema).some(([field, type]) => typeof parsed[field] !== type
        || type === "string" && !String(parsed[field]).trim())) {
      message.warning("阶段事实缺少必填字段或字段类型不正确"); return;
    }
    setDeliverySaving(true);
    try {
      const idempotencyKey = deliveryIdempotencyKey.current ||= key();
      await api.studentDeliveryStage(service.serviceRelationId, { stage: currentDeliveryStage.code, successful: true, remark: values.remark.trim(), data: parsed, idempotencyKey });
      message.success("交付阶段已完成"); setDeliveryOpen(false); deliveryIdempotencyKey.current = undefined; deliveryForm.resetFields(); await onRefresh();
    } catch (error) { message.error(errorMessage(error)); }
    finally { setDeliverySaving(false); }
  };

  const toolbarActions: ToolbarAction[] = [
    available.includes("ACCEPT") && { key: "student-accept", icon: <CheckOutlined />, label: "接收", onClick: accept },
    stageAction && { key: `student-${stageAction.toLowerCase()}`, icon: <PhoneOutlined />, label: stageLabel, onClick: () => setContactOpen(true) },
    available.includes("EDIT_BASIC_INFO") && { key: "student-edit-basic-info", icon: <EditOutlined />, label: "修改信息", onClick: openBasicInfo },
    available.includes("ASSIGN_CONTENT_DIRECTOR") && { key: "student-assign-director", icon: <UserAddOutlined />, label: "分配编导", onClick: () => openAssignment("content_director") },
    available.includes("ASSIGN_CAREER_PLANNER") && { key: "student-assign-career", icon: <UserAddOutlined />, label: "分配职业规划师", onClick: () => openAssignment("career_planner") },
    available.includes("UPDATE_EXAM_DATE") && { key: "student-exam-date", icon: <EditOutlined />, label: "修改考试时间", onClick: () => { examDateForm.setFieldsValue({ examDate: context.examDate ? dayjs(context.examDate) : undefined }); setExamDateOpen(true); } },
    available.includes("EXAM_NOTICE_DONE") && { key: "student-exam-notice-done", icon: <CheckOutlined />, label: "已考前通知", onClick: () => { setDeliveryOpen(true); deliveryIdempotencyKey.current = key(); deliveryForm.setFieldsValue({ data: "{}" }); } },
    available.includes("POST_EXAM_DONE") && { key: "student-post-exam-done", icon: <CheckOutlined />, label: "已考后回访", onClick: () => { setDeliveryOpen(true); deliveryIdempotencyKey.current = key(); deliveryForm.setFieldsValue({ data: "{}" }); } },
    (available.includes("COMPLETE_STAGE") || available.includes("END_SERVICE")) && currentDeliveryStage
      && !["first_contact", "study_plan", "supervision"].includes(currentDeliveryStage.code)
      && { key: "student-delivery-stage", icon: <CheckOutlined />, label: currentDeliveryStage.label, onClick: () => {
        const templates: Record<string, Record<string, unknown>> = {
          supervision: {}, exam_confirmation: { examIntention: "", examDate: "" },
          exam_preparation: { examNoticeSent: true, admissionTicketNoticeSent: true },
          post_exam: { examFeedback: "" }, result: { result: "" },
          certificate: { certificateNotice: "", mailingInfo: "" },
        };
        deliveryIdempotencyKey.current = key();
        deliveryForm.setFieldsValue({ data: JSON.stringify(templates[currentDeliveryStage.code] || {}, null, 2) });
        setDeliveryOpen(true);
      } },
  ].filter(Boolean) as ToolbarAction[];

  return <>
    {children(toolbarActions)}
    <Modal title={stageLabel} open={contactOpen} footer={null} destroyOnHidden onCancel={() => setContactOpen(false)}>
      <StudentContactForm relationId={service.serviceRelationId} context={context} onDone={async () => { setContactOpen(false); await onRefresh(); }} />
    </Modal>
    <Modal title="修改学员信息" open={basicInfoOpen} confirmLoading={basicInfoSaving} okText="保存" onCancel={() => setBasicInfoOpen(false)} onOk={() => basicInfoForm.submit()} destroyOnHidden>
      <Form form={basicInfoForm} layout="vertical" onFinish={saveBasicInfo}>
        <Form.Item name="name" label="姓名" rules={[{ required: true, whitespace: true, max: 100 }]}><Input /></Form.Item>
        <Form.Item name="mobile" label="手机号" rules={[{ pattern: /^1[3-9]\d{9}$/, message: "请输入正确的手机号" }]}><Input maxLength={32} /></Form.Item>
        <Form.Item name="wechatId" label="微信号" rules={[{ max: 64 }]}><Input /></Form.Item>
        <Form.Item name="reason" label="修改原因" rules={[{ required: true, whitespace: true, max: 500 }]}><Input.TextArea rows={3} showCount maxLength={500} /></Form.Item>
      </Form>
    </Modal>
    <Modal title="修改考试时间" open={examDateOpen} confirmLoading={examDateSaving} okText="保存" onCancel={() => setExamDateOpen(false)} onOk={() => void examDateForm.submit()} destroyOnHidden>
      <Form form={examDateForm} layout="vertical" onFinish={saveExamDate}>
        <Form.Item name="examDate" label="考试时间" rules={[{ required: true, message: "请选择考试日期" }]}><DatePicker style={{ width: "100%" }} /></Form.Item>
      </Form>
    </Modal>
    <Modal title={assignmentType === "content_director" ? "分配编导" : "分配职业规划师"} open={Boolean(assignmentType)} confirmLoading={assignmentSaving} okText="确认分配" onCancel={() => setAssignmentType(undefined)} onOk={() => void assign()} destroyOnHidden>
      <Space direction="vertical" size="middle" style={{ width: "100%" }}>
        {candidateError && <Alert type="error" showIcon title={candidateError} action={<Button size="small" onClick={() => assignmentType && void loadCandidates(assignmentType)}>重试</Button>} />}
        <Select showSearch optionFilterProp="label" loading={candidateLoading} disabled={Boolean(candidateError)} value={candidateUserId} onChange={setCandidateUserId} placeholder="选择协作者" options={candidates.map(item => ({ value: item.id, label: item.nickname }))} style={{ width: "100%" }} />
        {assignedUserId && <>
          <Alert type="info" showIcon title={`当前${assignmentType === "content_director" ? "编导" : "职业规划师"}：${assignmentType === "content_director" ? service.contentDirectorUserName || "已分配（姓名未返回）" : service.careerPlannerUserName || "已分配（姓名未返回）"}`} />
          <Form.Item label="修改原因" required style={{ marginBottom: 0 }}><Input.TextArea value={correctionReason} onChange={event => setCorrectionReason(event.target.value)} rows={3} maxLength={500} showCount /></Form.Item>
        </>}
      </Space>
    </Modal>
    <Modal title={currentDeliveryStage?.label || "完成交付阶段"} open={deliveryOpen} confirmLoading={deliverySaving} okText="完成阶段" onCancel={() => { setDeliveryOpen(false); deliveryIdempotencyKey.current = undefined; }} onOk={() => void deliveryForm.submit()} destroyOnHidden>
      <Form form={deliveryForm} layout="vertical" onFinish={submitDeliveryStage} initialValues={{ data: "{}" }}>
        <Alert type="info" showIcon title="完成后将推进到下一阶段，阶段事实会作为历史快照保留。" />
        <Form.Item name="remark" label="本次处理说明" rules={[{ required: true, whitespace: true, max: 2000 }]}><Input.TextArea rows={4} showCount maxLength={2000} /></Form.Item>
        <Form.Item name="data" label="阶段事实（JSON）" rules={[{ required: true }]}><Input.TextArea rows={7} placeholder='例如：{"examDate":"2026-12-20","examIntention":"参加"}' /></Form.Item>
      </Form>
    </Modal>
  </>;
}

function StudentContactDetail({ service }: { service: MyStudent["services"][number] }) {
  const [context, setContext] = useState<import("../services/api").StudentContactContext>();
  const [records, setRecords] = useState<import("../services/api").StudentContactRecord[]>([]);
  const [recordPage, setRecordPage] = useState(1);
  const [recordTotal, setRecordTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const load = useCallback(async (page = 1) => { setLoading(true); try { const [next, history] = await Promise.all([api.studentContactContext(service.serviceRelationId), api.studentContactRecords(service.serviceRelationId, page, PAGE_SIZE)]); setContext(next); setRecords(history.list); setRecordPage(page); setRecordTotal(history.total); } catch (error) { message.error(errorMessage(error)); } finally { setLoading(false); } }, [service.serviceRelationId]);
  useEffect(() => { void load(1); }, [load]);
  if (loading || !context) return <Skeleton active paragraph={{ rows: 10 }} />;
  const history = records.length ? <Space direction="vertical" className="student-contact-history" style={{ width: "100%" }}>{records.map(row => {
    const checklistTitles = row.completedChecklistKeys.map(itemKey => context.firstContactChecklist.find(item => item.key === itemKey)?.title || itemKey);
    return <section className="registration-summary-card student-contact-record" key={row.id}>
      <div className="student-contact-record-header">
        <Space wrap size={[8, 4]}>
          <Tag color="blue">{studentContactTypeLabel(row.contactType)}</Tag>
          {row.deliveryStage && <Tag>{studentDeliveryStageLabel(row.deliveryStage)}</Tag>}
          <Tag color={row.successful ? "success" : "warning"}>{row.successful ? "已联系" : "未联系成功"}</Tag>
        </Space>
        <Typography.Text type="secondary">联系时间：{formatTimestamp(new Date(row.submittedAt).getTime())}</Typography.Text>
      </div>
      <div className="student-contact-record-meta">
        <Typography.Text type="secondary">操作人：{row.operatorUserName || "未记录"}</Typography.Text>
        {row.nextContactAt && <Typography.Text type="secondary">下次联系：{formatTimestamp(new Date(row.nextContactAt).getTime())}</Typography.Text>}
        {!row.successful && row.unsuccessfulReasonLabel && <Typography.Text type="secondary">原因：{row.unsuccessfulReasonLabel}</Typography.Text>}
      </div>
      <Typography.Paragraph className="student-contact-record-remark">{row.remark || "未填写备注"}</Typography.Paragraph>
      {(checklistTitles.length > 0 || row.attachmentFileIds.length > 0) && <div className="student-contact-record-footer">
        {checklistTitles.length > 0 && <Typography.Text type="secondary">已完成清单：{checklistTitles.join("、")}</Typography.Text>}
        {row.attachmentFileIds.length > 0 && <Typography.Text type="secondary">附件：{row.attachmentFileIds.length} 个</Typography.Text>}
      </div>}
    </section>;
  })}{recordTotal > PAGE_SIZE && <Pagination current={recordPage} pageSize={PAGE_SIZE} total={recordTotal} showSizeChanger={false} onChange={page => void load(page)} />}</Space> : <Empty description="暂无联系记录" />;
  const taskLabel = context.currentTask?.type === "student_first_contact" ? "首联" : context.currentTask?.type === "student_study_plan" ? "制定学习计划" : context.currentTask?.type === "student_contact" ? "普通跟进" : "暂无待办";
  return <Space direction="vertical" size="middle" style={{ width: "100%" }}>
    <Alert type={context.currentTask?.overdue ? "warning" : "info"} showIcon title={`当前任务：${taskLabel}`} description={context.currentTask?.dueAt ? `截止时间：${formatTimestamp(new Date(context.currentTask.dueAt).getTime())}` : undefined} />
    {history}
  </Space>;
}

export function StudentContactConfigPage() {
  const [config, setConfig] = useState<import("../services/api").StudentContactConfig>(); const [saving, setSaving] = useState(false);
  const load = useCallback(async () => { try { setConfig(await api.studentContactConfig()); } catch (error) { message.error(errorMessage(error)); } }, []);
  useEffect(() => { void load(); }, [load]);
  const draft = config?.draft;
  const update = (patch: Record<string, unknown>) => setConfig(value => value?.draft ? { ...value, draft: { ...value.draft, ...patch } } : value);
  const persistDraft = async (value: NonNullable<import("../services/api").StudentContactConfig["draft"]>) => { if (value.firstContactTimeoutMinutes < 5 || value.studyPlanTimeoutMinutes < 5 || !value.checklist.some(item => item.enabled) || value.checklist.some(item => !item.title.trim()) || value.quickNotes.some(note => !note.trim())) throw new Error("请检查时间限制、快捷备注和首联任务清单"); await api.saveStudentContactConfigDraft({ id: value.id, version: value.version, idempotencyKey: key(), firstContactTimeoutMinutes: value.firstContactTimeoutMinutes, studyPlanTimeoutMinutes: value.studyPlanTimeoutMinutes, checklist: value.checklist, quickNotes: value.quickNotes.map(note => note.trim()), collaboratorTabs: value.collaboratorTabs }); };
  const save = async () => { if (!draft) return; setSaving(true); try { await persistDraft(draft); message.success("草稿已保存"); await load(); } catch (error) { message.error(errorMessage(error)); } finally { setSaving(false); } };
  const publish = async () => { if (!draft) return; setSaving(true); try { await persistDraft(draft); const refreshed = await api.studentContactConfig(); const current = refreshed.draft; if (!current) throw new Error("草稿状态已变化，请刷新后重试"); await api.publishStudentContactConfig(current.id, current.version, key()); message.success("配置已发布"); await load(); } catch (error) { message.error(errorMessage(error)); } finally { setSaving(false); } };
  const copy = async () => { const published = config?.published; if (!published) return; setSaving(true); try { await api.copyStudentContactConfigDraft(published.id, published.version, key()); await load(); message.success("已创建草稿"); } catch (error) { message.error(errorMessage(error)); } finally { setSaving(false); } };
  const updateQuickNote = (index: number, value: string) => update({ quickNotes: draft!.quickNotes.map((note, noteIndex) => noteIndex === index ? value : note) });
  const moveQuickNote = (index: number, offset: number) => { const target = index + offset; if (target < 0 || target >= draft!.quickNotes.length) return; const notes = [...draft!.quickNotes]; [notes[index], notes[target]] = [notes[target], notes[index]]; update({ quickNotes: notes }); };
  return <section className="workspace-page registration-config-page"><div className="page-heading"><div><Typography.Title level={4}>学员联系配置</Typography.Title><Typography.Text type="secondary">发布后仅新建联系任务使用新版本</Typography.Text></div><Space><Button loading={saving} disabled={Boolean(draft) || !config?.published} onClick={() => void copy()}>复制已发布版本</Button><Button type="primary" loading={saving} disabled={!draft} onClick={() => void save()}>保存草稿</Button>{draft && <Button loading={saving} onClick={() => void publish()}>保存并发布</Button>}</Space></div>{draft ? <Form layout="vertical"><Space wrap><Form.Item label="首次联系最大间隔（分钟）"><Input type="number" min={5} max={10080} value={draft.firstContactTimeoutMinutes} onChange={event => update({ firstContactTimeoutMinutes: Number(event.target.value) })} /></Form.Item><Form.Item label="制定学习计划最大间隔（分钟）"><Input type="number" min={5} max={43200} value={draft.studyPlanTimeoutMinutes} onChange={event => update({ studyPlanTimeoutMinutes: Number(event.target.value) })} /></Form.Item></Space><div className="student-contact-config-section"><Typography.Title level={5}>快捷备注</Typography.Title>{draft.quickNotes.map((note, index) => <Space key={`${index}-${note}`} style={{ display: "flex", marginBottom: 8 }}><Input value={note} maxLength={200} onChange={event => updateQuickNote(index, event.target.value)} /><Button icon={<UpOutlined />} aria-label="上移" disabled={index === 0} onClick={() => moveQuickNote(index, -1)} /><Button icon={<DownOutlined />} aria-label="下移" disabled={index === draft.quickNotes.length - 1} onClick={() => moveQuickNote(index, 1)} /><Button danger icon={<DeleteOutlined />} aria-label="删除" onClick={() => update({ quickNotes: draft.quickNotes.filter((_, noteIndex) => noteIndex !== index) })} /></Space>)}<Button icon={<PlusOutlined />} onClick={() => update({ quickNotes: [...draft.quickNotes, ""] })}>新增快捷备注</Button></div><Typography.Title level={5}>首联任务清单</Typography.Title>{draft.checklist.map((item, index) => <Space key={item.key} style={{ display: "flex", marginBottom: 8 }}><Input value={item.title} onChange={event => update({ checklist: draft.checklist.map((row, rowIndex) => rowIndex === index ? { ...row, title: event.target.value } : row) })} /><Switch checked={item.enabled !== false} onChange={checked => update({ checklist: draft.checklist.map((row, rowIndex) => rowIndex === index ? { ...row, enabled: checked } : row) })} /></Space>)}</Form> : <Empty description="暂无草稿，请先复制已发布版本" />}</section>;
}

export function StudentContactExceptionsPage() {
  const [rows, setRows] = useState<import("../services/api").StudentContactExtension[]>([]); const [pageNo, setPageNo] = useState(1); const [total, setTotal] = useState(0); const [loading, setLoading] = useState(false);
  const load = useCallback(async (page = 1) => { setLoading(true); try { const result = await api.studentContactExtensions(page, PAGE_SIZE); setRows(result.list); setTotal(result.total); setPageNo(page); } catch (error) { message.error(errorMessage(error)); } finally { setLoading(false); } }, []);
  useEffect(() => { void load(1); }, [load]);
  return <section className="workspace-page registration-page"><div className="page-heading"><Typography.Title level={4}>异常情况处理</Typography.Title></div><Spin spinning={loading}>{rows.length ? <Space direction="vertical" style={{ width: "100%" }}>{rows.map(row => <section className="registration-summary-card" key={row.id}><Space wrap><Tag>{row.status}</Tag><span>{row.reasonLabel || row.reasonValue}</span><span>{row.requestedDueAt}</span><span>{row.processInstanceId || "BPM 流程待生成"}</span></Space><Typography.Paragraph>{row.description}</Typography.Paragraph></section>)}{total > PAGE_SIZE && <Pagination current={pageNo} pageSize={PAGE_SIZE} total={total} showSizeChanger={false} onChange={page => void load(page)} />}</Space> : <Empty description="暂无延期申请记录" />}</Spin></section>;
}

export function RegistrationChecklistConfigPage() {
  const [config, setConfig] = useState<RegistrationChecklistConfig>(),
    [error, setError] = useState("");
  const [loading, setLoading] = useState(false),
    [saving, setSaving] = useState(false);
  const [departments, setDepartments] = useState<SimpleDept[]>([]);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [nextConfig, nextDepartments] = await Promise.all([
        api.registrationChecklistConfig(), api.simpleDepartments(),
      ]);
      setConfig(nextConfig);
      setDepartments(nextDepartments);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  const draft = config?.draft;
  const updateItems = (update: (items: NonNullable<RegistrationChecklistConfig["draft"]>["items"]) => NonNullable<RegistrationChecklistConfig["draft"]>["items"]) =>
    setConfig((value) => value?.draft ? { ...value, draft: { ...value.draft, items: update(value.draft.items) } } : value);
  const updateRoutes = (update: (routes: NonNullable<RegistrationChecklistConfig["draft"]>["routeOptions"]) => NonNullable<RegistrationChecklistConfig["draft"]>["routeOptions"]) =>
    setConfig((value) => value?.draft ? { ...value, draft: { ...value.draft, routeOptions: update(value.draft.routeOptions) } } : value);
  const move = <T,>(rows: T[], index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= rows.length) return rows;
    const next = [...rows];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  };
  const addItem = () => updateItems((items) => [...items, {
    id: -Date.now(), itemKey: `custom_${crypto.randomUUID().replaceAll("-", "")}`,
    itemType: "checkbox", title: "新清单项", sort: (items.length + 1) * 10,
    enabled: true, systemRequired: false, attachmentRequired: false,
  }]);
  const addRoute = () => {
    const department = departments[0];
    if (!department) { message.warning("系统暂无可用部门"); return; }
    updateRoutes((routes) => [...routes, {
      id: -Date.now(), optionKey: `custom_${crypto.randomUUID().replaceAll("-", "")}`,
      departmentId: department.id, departmentName: department.name, assigneeType: "study_planner",
      assigneeTypeLabel: "学习规划师", sort: (routes.length + 1) * 10,
      enabled: true, systemRequired: false,
    }]);
  };
  const save = async () => {
    if (!config?.draft) return;
    setSaving(true);
    try {
      await api.saveRegistrationChecklistDraft({
        templateVersion: config.templateVersion,
        idempotencyKey: key(),
        items: config.draft.items.map((item, index) => ({ ...item, id: item.id > 0 ? item.id : undefined, sort: (index + 1) * 10 })),
        routeOptions: config.draft.routeOptions.map((route, index) => ({ ...route, id: route.id > 0 ? route.id : undefined, sort: (index + 1) * 10 })),
      });
      message.success("草稿已保存");
      await load();
    } catch (requestError) {
      message.error(errorMessage(requestError));
    } finally {
      setSaving(false);
    }
  };
  const copy = async () => {
    if (!config) return;
    try {
      await api.copyRegistrationChecklistDraft(config.templateVersion);
      await load();
    } catch (requestError) {
      message.error(errorMessage(requestError));
    }
  };
  const publish = async () => {
    if (!config) return;
    try {
      await api.publishRegistrationChecklist(config.templateVersion);
      message.success("模板已发布");
      await load();
    } catch (requestError) {
      message.error(errorMessage(requestError));
    }
  };
  return (
    <section className="workspace-page registration-config-page">
      <div className="page-heading">
        <div>
          <Typography.Title level={4}>履约清单配置</Typography.Title>
          <Typography.Text type="secondary">
            发布后仅新建报名任务使用新版本
          </Typography.Text>
        </div>
        <Space>
          <Button onClick={() => void copy()} disabled={Boolean(draft)}>
            复制已发布版本
          </Button>
          <Button
            type="primary"
            onClick={() => void save()}
            disabled={!draft}
            loading={saving}
          >
            保存草稿
          </Button>
          <Button onClick={() => void publish()} disabled={!draft}>
            发布
          </Button>
        </Space>
      </div>
      {error && <LoadState error={error} retry={() => void load()} />}{" "}
      {loading ? (
        <Spin />
      ) : draft ? (
        <div className="registration-config-list">
          <div className="registration-config-section-heading">
            <Typography.Title level={5}>任务清单</Typography.Title>
            <Button icon={<PlusOutlined />} onClick={addItem}>新增清单项</Button>
          </div>
          {draft.items.map((item, index) => (
            <div className="registration-config-item" key={item.id}>
              <Input
                value={item.title}
                disabled={item.itemType === "study_planner"}
                onChange={(event) => updateItems((items) => items.map((current) => current.id === item.id ? { ...current, title: event.target.value } : current))}
              />
              <Select value={item.itemType} disabled={item.systemRequired} options={[
                { value: "checkbox", label: "人工确认" },
                { value: "attachment", label: "上传附件" },
                ...(item.systemRequired ? [{ value: "study_planner", label: "系统固定项" }] : []),
              ]} onChange={(itemType) => updateItems((items) => items.map((current) => current.id === item.id
                ? { ...current, itemType, attachmentRequired: itemType === "attachment" ? current.attachmentRequired : false } : current))} />
              {item.itemType === "attachment" && <Checkbox checked={item.attachmentRequired}
                onChange={(event) => updateItems((items) => items.map((current) => current.id === item.id
                  ? { ...current, attachmentRequired: event.target.checked } : current))}>必传</Checkbox>}
              <Switch checked={item.enabled} disabled={item.systemRequired}
                onChange={(enabled) => updateItems((items) => items.map((current) => current.id === item.id ? { ...current, enabled } : current))} />
              <Space size={4}>
                <Button type="text" icon={<UpOutlined />} aria-label="上移" disabled={index === 0}
                  onClick={() => updateItems((items) => move(items, index, -1))} />
                <Button type="text" icon={<DownOutlined />} aria-label="下移" disabled={index === draft.items.length - 1}
                  onClick={() => updateItems((items) => move(items, index, 1))} />
                <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除" disabled={item.systemRequired}
                  onClick={() => updateItems((items) => items.filter((current) => current.id !== item.id))} />
              </Space>
            </div>
          ))}
          <div className="registration-config-section-heading">
            <Typography.Title level={5}>学员流转部门</Typography.Title>
            <Button icon={<PlusOutlined />} onClick={addRoute}>新增流转部门</Button>
          </div>
          {draft.routeOptions.map((route, index) => (
            <div className="registration-config-item registration-config-route" key={route.id}>
              <Select showSearch optionFilterProp="label" value={route.departmentId}
                options={departments.map((department) => ({ value: department.id, label: department.name }))}
                onChange={(departmentId) => updateRoutes((routes) => routes.map((current) => current.id === route.id
                  ? { ...current, departmentId, departmentName: departments.find((item) => item.id === departmentId)?.name || current.departmentName } : current))} />
              <Select value={route.assigneeType} options={[
                { value: "study_planner", label: "学习规划师" }, { value: "content_director", label: "编导" },
              ]} onChange={(assigneeType) => updateRoutes((routes) => routes.map((current) => current.id === route.id
                ? { ...current, assigneeType, assigneeTypeLabel: assigneeType === "study_planner" ? "学习规划师" : "编导" } : current))} />
              <Switch checked={route.enabled} onChange={(enabled) => updateRoutes((routes) => routes.map((current) => current.id === route.id ? { ...current, enabled } : current))} />
              <Space size={4}>
                <Button type="text" icon={<UpOutlined />} aria-label="上移" disabled={index === 0}
                  onClick={() => updateRoutes((routes) => move(routes, index, -1))} />
                <Button type="text" icon={<DownOutlined />} aria-label="下移" disabled={index === draft.routeOptions.length - 1}
                  onClick={() => updateRoutes((routes) => move(routes, index, 1))} />
                <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除"
                  onClick={() => updateRoutes((routes) => routes.filter((current) => current.id !== route.id))} />
              </Space>
            </div>
          ))}
        </div>
      ) : (
        <Empty description="暂无草稿，请复制已发布版本" />
      )}
    </section>
  );
}
