import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Empty,
  Form,
  Input,
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
import { DeleteOutlined, DownOutlined, PlusOutlined, ReloadOutlined, UploadOutlined, UpOutlined } from "@ant-design/icons";
import { useLocation } from "react-router-dom";
import { NameAvatar } from "../components/LeadDetailOverview";
import LeadDetail from "../components/LeadDetail";
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

const PAGE_SIZE = 20;
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "请求失败，请重试";
const key = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const serviceStatusLabel = (status: string) =>
  ({ active: "服务中", completed: "已完成", cancelled: "已取消" })[status] ||
  "未知状态";

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
                      <span key={attachment.id}>
                        <a href={attachment.fileUrl} target="_blank" rel="noreferrer">{attachment.originalName}</a>
                        {! ["completed", "cancelled"].includes(selected.status) && (
                          <Button type="text" danger size="small" icon={<DeleteOutlined />} aria-label={`删除 ${attachment.originalName}`}
                            onClick={() => void deleteAttachment(item.id, attachment.id)} />
                        )}
                      </span>
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
        <Button
          type="primary"
          disabled={!selected.completable}
          loading={completing}
          onClick={() => void complete()}
        >
          完成报名履约
        </Button>
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

function LegacyMyStudentsPage() {
  const [rows, setRows] = useState<MyStudent[]>([]),
    [selected, setSelected] = useState<MyStudent>();
  const [leadDetail, setLeadDetail] = useState<ManagedLead>();
  const [categories, setCategories] = useState<DictData[]>([]);
  const [channels, setChannels] = useState<DictData[]>([]);
  const [categoryError, setCategoryError] = useState(false);
  const [channelError, setChannelError] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [pageNo, setPageNo] = useState(1),
    [total, setTotal] = useState(0);
  const [error, setError] = useState(""),
    [detailError, setDetailError] = useState("");
  const [loading, setLoading] = useState(false),
    [detailLoading, setDetailLoading] = useState(false);
  const listGeneration = useRef(0);
  const detailGeneration = useRef(0);
  const dictionaryGeneration = useRef(0);
  const inflightLists = useRef(new Set<string>());
  const loadStudent = useCallback(async (personId: number) => {
    const generation = ++detailGeneration.current;
    setDetailLoading(true);
    setDetailError("");
    try {
      const student = await api.myStudent(personId);
      if (generation !== detailGeneration.current) return;
      setSelected(student);
      const lead = student.leadId ? await api.managedLead(student.leadId) : undefined;
      if (generation === detailGeneration.current) setLeadDetail(lead);
    } catch (requestError) {
      if (generation !== detailGeneration.current) return;
      setLeadDetail(undefined);
      setDetailError(errorMessage(requestError));
    } finally {
      if (generation === detailGeneration.current) setDetailLoading(false);
    }
  }, []);
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
    async (targetPage = pageNo) => {
      const requestKey = `${targetPage}:${keyword}:${JSON.stringify(advancedFilter)}`;
      if (inflightLists.current.has(requestKey)) return;
      inflightLists.current.add(requestKey);
      const generation = ++listGeneration.current;
      setLoading(true);
      setError("");
      try {
        const page = await api.myStudents({
          pageNo: targetPage,
          pageSize: PAGE_SIZE,
          keyword: keyword || undefined,
          advancedFilter,
        });
        if (generation !== listGeneration.current) return;
        setRows(page.list);
        setTotal(page.total);
        setPageNo(targetPage);
        const target =
          selected &&
          page.list.some((item) => item.personId === selected.personId)
            ? selected.personId
            : page.list[0]?.personId;
        if (target) await loadStudent(target);
        else setSelected(undefined);
      } catch (requestError) {
        if (generation === listGeneration.current) setError(errorMessage(requestError));
      } finally {
        inflightLists.current.delete(requestKey);
        if (generation === listGeneration.current) setLoading(false);
      }
    },
    [advancedFilter, keyword, loadStudent, pageNo, selected],
  );
  useEffect(() => {
    void load(1);
  }, [advancedFilter, keyword]);
  const detailContent = detailLoading ? (
    <Skeleton active paragraph={{ rows: 10 }} />
  ) : detailError ? (
    <LoadState
      error={detailError}
      retry={() => selected?.personId && void loadStudent(selected.personId)}
    />
  ) : selected && leadDetail ? (
    <LeadDetail
      lead={leadDetail}
      categories={categories}
      categoryLabel={(value) => dictionaryDisplayLabel(categories, value, categoryError)}
      channelLabel={(value) => dictionaryDisplayLabel(channels, value, channelError)}
      mode="student-readonly"
      autoExpandFollowUp={false}
      onDirtyChange={() => undefined}
      onChanged={() => void loadStudent(selected.personId)}
    />
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
        <Button icon={<ReloadOutlined />} onClick={() => { void load(1); void loadDictionaries(); }}>刷新</Button>
      </header>
      <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar">
            <AdvancedFilterToolbar
              scene="student"
              placeholder="搜索姓名、手机号或客资编号"
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
    </section>
  );
}

function StudentContactForm({
  relationId,
  context,
  onDone,
}: { relationId: number; context: import("../services/api").StudentContactContext; onDone: () => void }) {
  const [form] = Form.useForm();
  const [reasons, setReasons] = useState<DictData[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [attachmentFileIds, setAttachmentFileIds] = useState<number[]>([]);
  const [extensionReasons, setExtensionReasons] = useState<DictData[]>([]);
  const taskType = context.currentTask?.type;
  useEffect(() => { void Promise.all([api.dictDataByType(DICT_TYPE.STUDENT_CONTACT_UNSUCCESSFUL_REASON), api.dictDataByType("zsjos_student_contact_extension_reason")]).then(([a,b]) => { setReasons(a); setExtensionReasons(b); }).catch(() => { setReasons([]); setExtensionReasons([]); }); }, []);
  if (!context.currentTask) return <Alert type="info" showIcon title="当前没有待处理联系任务" />;
  const submit = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const next = new Date(String(values.nextContactAt));
      const timeout = taskType === "student_first_contact" ? context.firstContactTimeoutMinutes : taskType === "student_study_plan" ? context.studyPlanTimeoutMinutes : 0;
      const extensionRequired = Boolean(timeout && next.getTime() > Date.now() + timeout * 60000);
      const payload = { ...values, taskId: context.currentTask!.id, idempotencyKey: key(), attachmentFileIds, extensionAttachmentFileIds: [], extensionReasonValue: extensionRequired ? values.extensionReasonValue : undefined, extensionDescription: extensionRequired ? values.extensionDescription : undefined };
      if (taskType === "student_first_contact") await api.studentFirstContact(relationId, payload);
      else if (taskType === "student_study_plan") await api.studentStudyPlan(relationId, payload);
      else await api.studentContact(relationId, payload);
      message.success("任务已提交"); form.resetFields(); onDone();
    } catch (error) { message.error(errorMessage(error)); }
    finally { setSubmitting(false); }
  };
  return <Form form={form} layout="vertical" onFinish={submit}>
    <Form.Item name="successful" label="是否成功联系上学员" rules={[{ required: true }]}><Radio.Group options={[{ label: "是", value: true }, { label: "否", value: false }]} /></Form.Item>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue("successful") === false ? <Form.Item name="unsuccessfulReasonValue" label="未联系原因" rules={[{ required: true }]}><Select options={reasons.map(row => ({ label: row.label, value: row.value }))} placeholder={reasons.length ? "选择管理员配置的原因" : "暂无可用原因，请联系管理员"} /></Form.Item> : null}</Form.Item>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => taskType === "student_first_contact" && getFieldValue("successful") === true ? <Form.Item name="completedChecklistKeys" label="首联任务清单" rules={[{ required: true, type: "array", min: 1 }]}><Checkbox.Group options={context.firstContactChecklist.map(item => ({ label: item.title, value: item.key }))} /></Form.Item> : null}</Form.Item>
    {context.quickNotes.length > 0 && <Form.Item label="快捷备注"><Space wrap>{context.quickNotes.map(note => <Button key={note} size="small" onClick={() => form.setFieldValue("remark", `${form.getFieldValue("remark") || ""}${note}`)}>{note}</Button>)}</Space></Form.Item>}
    <Form.Item name="remark" label="备注" rules={[{ required: true }]}><Input.TextArea rows={4} maxLength={2000} showCount /></Form.Item>
    <Form.Item name="nextContactAt" label="下次联系时间" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => { const value = getFieldValue("nextContactAt"); const timeout = taskType === "student_first_contact" ? context.firstContactTimeoutMinutes : taskType === "student_study_plan" ? context.studyPlanTimeoutMinutes : 0; const extended = Boolean(value && timeout && new Date(String(value)).getTime() > Date.now() + timeout * 60000); return extended ? <><Alert type="warning" showIcon title={`超过允许时限（${timeout} 分钟），将发起延期审批`} /><Form.Item name="extensionReasonValue" label="延期原因" rules={[{ required: true }]}><Select options={extensionReasons.map(row => ({ label: row.label, value: row.value }))} /></Form.Item><Form.Item name="extensionDescription" label="延期说明" rules={[{ required: true }]}><Input.TextArea rows={3} maxLength={1000} /></Form.Item></> : null; }}</Form.Item>
    <Space wrap><Upload multiple beforeUpload={async file => { try { const uploaded = await api.studentContactUpload(file); setAttachmentFileIds(ids => [...ids, uploaded.fileId]); message.success(`${file.name}已上传`); } catch (error) { message.error(errorMessage(error)); } return false; }} showUploadList><Button icon={<UploadOutlined />}>添加附件</Button></Upload>{attachmentFileIds.length > 0 && <Tag>{attachmentFileIds.length} 个附件</Tag>}</Space>
    <Space><Button type="primary" htmlType="submit" loading={submitting}>提交{taskType === "student_first_contact" ? "首次联系" : taskType === "student_study_plan" ? "学习计划" : "联系记录"}</Button></Space>
  </Form>;
}

function StudentContactDetail({ student, service, onRefresh }: { student: MyStudent; service: MyStudent["services"][number]; onRefresh: () => void }) {
  const [context, setContext] = useState<import("../services/api").StudentContactContext>();
  const [records, setRecords] = useState<import("../services/api").StudentContactRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [candidates, setCandidates] = useState<Record<string, StudyPlanner[]>>({});
  const load = useCallback(async () => { setLoading(true); try { const [next, history] = await Promise.all([api.studentContactContext(service.serviceRelationId), api.studentContactRecords(service.serviceRelationId)]); setContext(next); setRecords(history); } catch (error) { message.error(errorMessage(error)); } finally { setLoading(false); } }, [service.serviceRelationId]);
  useEffect(() => { void load(); }, [load]);
  if (loading || !context) return <Skeleton active paragraph={{ rows: 10 }} />;
  const assign = async (type: "content_director" | "career_planner") => { const list = await api.studentCollaboratorCandidates(service.serviceRelationId, type); setCandidates(value => ({ ...value, [type]: list })); };
  const doAssign = async (type: "content_director" | "career_planner", userId: number) => { try { await api.studentAssignCollaborator(service.serviceRelationId, { collaboratorType: type, userId, version: context.version, idempotencyKey: key() }); message.success("协作者已分配"); await load(); onRefresh(); } catch (error) { message.error(errorMessage(error)); } };
  const accept = async () => { try { await api.studentAccept(service.serviceRelationId, context.version); message.success("已确认接收"); await load(); onRefresh(); } catch (error) { message.error(errorMessage(error)); } };
  const overview = <Space direction="vertical" style={{ width: "100%" }} size="large"><section className="registration-summary-card"><DetailFieldGrid items={[{ key: "mobile", label: "手机号", value: student.mobile }, { key: "wechat", label: "微信号", value: student.wechatId }, { key: "course", label: "课程", value: service.courseName || service.skuName }, { key: "order", label: "订单号", value: service.orderNo }]} /><Space wrap><Tag color={service.acceptanceStatus === "accepted" ? "success" : "warning"}>{service.acceptanceStatus === "accepted" ? "已接收" : "待接收"}</Tag>{service.owner && service.acceptanceStatus !== "accepted" && <Button type="primary" onClick={() => void accept()}>确认接收</Button>}</Space></section><section className="registration-summary-card"><Typography.Title level={5}>可选协作者</Typography.Title><Space wrap><Button disabled={service.acceptanceStatus !== "accepted" || Boolean(service.contentDirectorUserId)} onClick={() => void assign("content_director")}>分配编导</Button><Button disabled={service.acceptanceStatus !== "accepted" || Boolean(service.careerPlannerUserId)} onClick={() => void assign("career_planner")}>分配职业规划师</Button></Space>{(["content_director", "career_planner"] as const).map(type => <div key={type}>{(candidates[type] || []).map(item => <Button key={item.id} size="small" onClick={() => void doAssign(type, item.id)}>{item.nickname}</Button>)}</div>)}</section></Space>;
  const contactForm = <StudentContactForm relationId={service.serviceRelationId} context={context} onDone={async () => { await load(); onRefresh(); }} />;
  const history = records.length ? <Space direction="vertical" style={{ width: "100%" }}>{records.map(row => <section className="registration-summary-card" key={row.id}><Space wrap><Tag>{row.contactType}</Tag><Tag color={row.successful ? "success" : "warning"}>{row.successful ? "已联系" : row.unsuccessfulReasonLabel || "未联系"}</Tag><span>{row.nextContactAt}</span></Space><Typography.Paragraph>{row.remark}</Typography.Paragraph></section>)}</Space> : <Empty description="暂无联系记录" />;
  const tabs = [{ key: "overview", label: "概览", children: overview }, { key: "first-contact", label: "首次联系", children: context.currentTask?.type === "student_first_contact" ? contactForm : <Alert type="info" title="当前任务不是首次联系" /> }, { key: "study-plan", label: "学习计划", children: context.currentTask?.type === "student_study_plan" ? contactForm : <Alert type="info" title="当前任务不是制定学习计划" /> }, { key: "contacts", label: "联系记录", children: <Space direction="vertical" style={{ width: "100%" }}>{context.currentTask?.type === "student_contact" && contactForm}{history}</Space> }].filter(tab => context.visibleTabs?.includes(tab.key));
  return <div className="registration-detail"><div className="registration-detail-hero"><div><Typography.Title level={4}>{student.name || "未填写姓名"}</Typography.Title><Typography.Text type="secondary">{student.leadNo || service.orderNo || "暂无客资编号"}</Typography.Text></div></div><Tabs items={tabs} defaultActiveKey="overview" /></div>;
}

export function MyStudentsPage() {
  const [rows, setRows] = useState<MyStudent[]>([]); const [selected, setSelected] = useState<MyStudent>(); const [serviceId, setServiceId] = useState<number>(); const [loading, setLoading] = useState(false); const [error, setError] = useState(""); const [keyword, setKeyword] = useState("");
  const load = useCallback(async () => { setLoading(true); try { const page = await api.myStudents({ pageNo: 1, pageSize: PAGE_SIZE, keyword: keyword || undefined }); setRows(page.list); const first = selected && page.list.find(row => row.personId === selected.personId) || page.list[0]; setSelected(first); setServiceId(value => value && first?.services.some(service => service.serviceRelationId === value) ? value : first?.services[0]?.serviceRelationId); } catch (requestError) { setError(errorMessage(requestError)); } finally { setLoading(false); } }, [keyword, selected]);
  useEffect(() => { void load(); }, [keyword]);
  const currentService = selected?.services.find(service => service.serviceRelationId === serviceId);
  return <section className="workspace-page registration-page"><header className="registration-filter-shell"><div><Typography.Title level={4}>我的学员</Typography.Title><Typography.Text type="secondary">按服务项目管理接收、首联、学习计划与联系记录</Typography.Text></div><Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button></header><div className="lead-inbox-layout"><aside className="lead-inbox-list-pane"><Input.Search value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="搜索姓名、手机号或客资编号" allowClear />{error ? <LoadState error={error} retry={() => void load()} /> : loading ? <Skeleton active paragraph={{ rows: 8 }} /> : rows.length ? rows.map(row => <button type="button" key={row.personId} className={`lead-inbox-item${selected?.personId === row.personId ? " active" : ""}`} onClick={() => { setSelected(row); setServiceId(row.services[0]?.serviceRelationId); }}><div className="lead-inbox-item-main"><NameAvatar name={row.name || "学员"} size={36} /><div className="lead-inbox-item-copy"><strong>{row.name || "未填写姓名"}</strong><span>{row.leadNo || "暂无客资编号"}</span><span>{row.services.length} 个服务项目</span></div></div></button>) : <Empty description="当前筛选下暂无学员" />}</aside><main className="lead-inbox-detail-pane">{selected && currentService ? <><Select value={serviceId} onChange={setServiceId} style={{ width: "100%", marginBottom: 12 }} options={selected.services.map(service => ({ label: `${service.courseName || service.skuName || "课程服务"} · ${service.orderNo || service.orderId}`, value: service.serviceRelationId }))} /><StudentContactDetail student={selected} service={currentService} onRefresh={() => void load()} /></> : <Empty description="从左侧选择一名学员" />}</main></div></section>;
}

export function StudentContactConfigPage() {
  const [config, setConfig] = useState<import("../services/api").StudentContactConfig>(); const [saving, setSaving] = useState(false);
  const load = useCallback(async () => { try { setConfig(await api.studentContactConfig()); } catch (error) { message.error(errorMessage(error)); } }, []);
  useEffect(() => { void load(); }, [load]);
  const draft = config?.draft;
  const update = (patch: Record<string, unknown>) => setConfig(value => value?.draft ? { ...value, draft: { ...value.draft, ...patch } } : value);
  const save = async () => { if (!draft) return; setSaving(true); try { await api.saveStudentContactConfigDraft({ id: draft.id, version: draft.version, firstContactTimeoutMinutes: draft.firstContactTimeoutMinutes, studyPlanTimeoutMinutes: draft.studyPlanTimeoutMinutes, checklist: draft.checklist, quickNotes: draft.quickNotes, collaboratorTabs: draft.collaboratorTabs }); message.success("草稿已保存"); await load(); } catch (error) { message.error(errorMessage(error)); } finally { setSaving(false); } };
  return <section className="workspace-page registration-config-page"><div className="page-heading"><div><Typography.Title level={4}>学员联系配置</Typography.Title><Typography.Text type="secondary">发布后仅新建联系任务使用新版本</Typography.Text></div><Space><Button onClick={async () => { await api.copyStudentContactConfigDraft(); await load(); }}>复制已发布版本</Button><Button type="primary" loading={saving} disabled={!draft} onClick={() => void save()}>保存草稿</Button>{draft && <Button onClick={async () => { await api.publishStudentContactConfig(draft.id, draft.version); message.success("配置已发布"); await load(); }}>发布配置</Button>}</Space></div>{draft ? <Form layout="vertical"><Space wrap><Form.Item label="首次联系最大间隔（分钟）"><Input type="number" value={draft.firstContactTimeoutMinutes} onChange={event => update({ firstContactTimeoutMinutes: Number(event.target.value) })} /></Form.Item><Form.Item label="制定学习计划最大间隔（分钟）"><Input type="number" value={draft.studyPlanTimeoutMinutes} onChange={event => update({ studyPlanTimeoutMinutes: Number(event.target.value) })} /></Form.Item></Space><Typography.Title level={5}>首联任务清单</Typography.Title>{draft.checklist.map((item, index) => <Space key={item.key} style={{ display: "flex", marginBottom: 8 }}><Input value={item.title} onChange={event => update({ checklist: draft.checklist.map((row, rowIndex) => rowIndex === index ? { ...row, title: event.target.value } : row) })} /><Switch checked={item.enabled !== false} onChange={checked => update({ checklist: draft.checklist.map((row, rowIndex) => rowIndex === index ? { ...row, enabled: checked } : row) })} /></Space>)}</Form> : <Empty description="暂无草稿，请先复制已发布版本" />}</section>;
}

export function StudentContactExceptionsPage() {
  const [rows, setRows] = useState<import("../services/api").StudentContactExtension[]>([]); useEffect(() => { void api.studentContactExtensions().then(setRows).catch(error => message.error(errorMessage(error))); }, []);
  return <section className="workspace-page registration-page"><div className="page-heading"><Typography.Title level={4}>异常情况处理</Typography.Title></div>{rows.length ? rows.map(row => <section className="registration-summary-card" key={row.id}><Space wrap><Tag>{row.status}</Tag><span>{row.reasonLabel || row.reasonValue}</span><span>{row.requestedDueAt}</span><span>{row.processInstanceId || "BPM 流程待生成"}</span></Space><Typography.Paragraph>{row.description}</Typography.Paragraph></section>) : <Empty description="暂无延期申请记录" />}</section>;
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
