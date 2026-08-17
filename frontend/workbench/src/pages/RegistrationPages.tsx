import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Descriptions,
  Empty,
  Input,
  Pagination,
  Select,
  Skeleton,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { useLocation } from "react-router-dom";
import { NameAvatar } from "../components/LeadDetailOverview";
import {
  api,
  type MyStudent,
  type RegistrationCase,
  type RegistrationChecklistConfig,
} from "../services/api";
import { formatTimestamp } from "../services/time";

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
    value.items!.every((item) => Boolean(item.checked));
  if (!allChecked)
    return {
      ...value,
      completable: false,
      completionBlockCode: "checklist_incomplete",
      completionBlockReason: "请先完成全部报名履约清单项",
    };
  if (!value.studyPlannerUserId)
    return {
      ...value,
      completable: false,
      completionBlockCode: "planner_required",
      completionBlockReason: "请先选择学习规划师",
    };
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
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [selected, setSelected] = useState<RegistrationCase>();
  const [error, setError] = useState("");
  const [detailError, setDetailError] = useState("");
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [plannerSaving, setPlannerSaving] = useState(false);
  const [savingItemIds, setSavingItemIds] = useState<Set<number>>(new Set());
  const [planners, setPlanners] = useState<
    Array<{ id: number; nickname: string }>
  >([]);

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
      setDetailLoading(true);
      setDetailError("");
      try {
        applyCase(await api.registrationCase(id));
      } catch (requestError) {
        setDetailError(errorMessage(requestError));
      } finally {
        setDetailLoading(false);
      }
    },
    [applyCase],
  );
  const load = useCallback(
    async (targetPage = pageNo, preferredId?: number) => {
      setLoading(true);
      setError("");
      try {
        const [page, candidates] = await Promise.all([
          api.registrationPoolPage({
            pageNo: targetPage,
            pageSize: PAGE_SIZE,
            keyword: keyword || undefined,
          }),
          api.registrationPlannerCandidates(),
        ]);
        setRows(page.list);
        setTotal(page.total);
        setPageNo(targetPage);
        setPlanners(candidates);
        const targetId = preferredId ?? selected?.id ?? page.list[0]?.id;
        if (targetId) await loadCase(targetId);
        else setSelected(undefined);
      } catch (requestError) {
        setError(errorMessage(requestError));
      } finally {
        setLoading(false);
      }
    },
    [keyword, loadCase, pageNo, selected?.id],
  );

  useEffect(() => {
    void load(1, routeState?.registrationCaseId);
  }, [keyword]);
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
  const updatePlanner = async (id: number) => {
    if (!selected) return;
    const previous = selected;
    const planner = planners.find((item) => item.id === id);
    applyCase(
      withCompletionState({
        ...selected,
        status: "processing",
        statusLabel: "处理中",
        studyPlannerUserId: id,
        studyPlannerUserName: planner?.nickname,
        items: selected.items?.map((item) =>
          item.itemType === "study_planner" ? { ...item, checked: true } : item,
        ),
      }),
    );
    setPlannerSaving(true);
    try {
      applyCase(
        await api.updateRegistrationPlanner(previous.id, {
          studyPlannerUserId: id,
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
      setPlannerSaving(false);
    }
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
      <Descriptions
        column={{ xs: 1, sm: 2 }}
        items={[
          { key: "order", label: "订单号", children: selected.orderNo || "-" },
          {
            key: "orderStatus",
            label: "订单状态",
            children: <Tag>{selected.orderStatusLabel || "未知状态"}</Tag>,
          },
          { key: "lead", label: "客资编号", children: selected.leadNo || "-" },
          {
            key: "mobile",
            label: "学员手机号",
            children: selected.studentMobile || "-",
          },
        ]}
      />
      {selected.completionBlockReason && (
        <Alert
          className="registration-block-alert"
          type="warning"
          showIcon
          title={selected.completionBlockReason}
        />
      )}
      <section className="registration-checklist-card">
        <Typography.Title level={5}>履约清单</Typography.Title>
        <div className="registration-checklist">
          {selected.items?.map((item) =>
            item.itemType === "study_planner" ? (
              <div className="registration-checklist-row" key={item.id}>
                <div className="registration-checklist-copy">
                  <strong>{item.title}</strong>
                  <span>{selected.studyPlannerUserName || "尚未选择"}</span>
                </div>
                <Select
                  value={selected.studyPlannerUserId}
                  placeholder="选择学习规划师"
                  loading={plannerSaving}
                  disabled={plannerSaving || selected.status === "completed"}
                  options={planners.map((planner) => ({
                    value: planner.id,
                    label: planner.nickname,
                  }))}
                  onChange={(value) => void updatePlanner(value)}
                />
              </div>
            ) : (
              <div className="registration-checklist-row" key={item.id}>
                <Checkbox
                  checked={item.checked}
                  disabled={
                    savingItemIds.has(item.id) ||
                    selected.status === "completed"
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
      </header>
      <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar">
            <Space.Compact>
              <Input.Search
                value={searchText}
                onChange={(event) => setSearchText(event.target.value)}
                onSearch={(value) => {
                  setKeyword(value.trim());
                  setPageNo(1);
                }}
                allowClear
                placeholder="搜索订单号、姓名或手机号"
              />
              <Button
                icon={<ReloadOutlined />}
                aria-label="刷新"
                onClick={() => void load(pageNo)}
              />
            </Space.Compact>
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

export function MyStudentsPage() {
  const [rows, setRows] = useState<MyStudent[]>([]),
    [selected, setSelected] = useState<MyStudent>();
  const [searchText, setSearchText] = useState(""),
    [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1),
    [total, setTotal] = useState(0);
  const [error, setError] = useState(""),
    [detailError, setDetailError] = useState("");
  const [loading, setLoading] = useState(false),
    [detailLoading, setDetailLoading] = useState(false);
  const loadStudent = useCallback(async (personId: number) => {
    setDetailLoading(true);
    setDetailError("");
    try {
      setSelected(await api.myStudent(personId));
    } catch (requestError) {
      setDetailError(errorMessage(requestError));
    } finally {
      setDetailLoading(false);
    }
  }, []);
  const load = useCallback(
    async (targetPage = pageNo) => {
      setLoading(true);
      setError("");
      try {
        const page = await api.myStudents({
          pageNo: targetPage,
          pageSize: PAGE_SIZE,
          keyword: keyword || undefined,
        });
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
        setError(errorMessage(requestError));
      } finally {
        setLoading(false);
      }
    },
    [keyword, loadStudent, pageNo, selected],
  );
  useEffect(() => {
    void load(1);
  }, [keyword]);
  const detailContent = detailLoading ? (
    <Skeleton active paragraph={{ rows: 10 }} />
  ) : detailError ? (
    <LoadState
      error={detailError}
      retry={() => selected?.personId && void loadStudent(selected.personId)}
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
      <Descriptions
        column={{ xs: 1, sm: 2 }}
        items={[
          { key: "mobile", label: "手机号", children: selected.mobile || "-" },
          {
            key: "wechat",
            label: "微信号",
            children: selected.wechatId || "-",
          },
        ]}
      />
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
      </header>
      <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar">
            <Space.Compact>
              <Input.Search
                value={searchText}
                onChange={(event) => setSearchText(event.target.value)}
                onSearch={(value) => {
                  setKeyword(value.trim());
                  setPageNo(1);
                }}
                allowClear
                placeholder="搜索姓名、手机号或客资编号"
              />
              <Button
                icon={<ReloadOutlined />}
                aria-label="刷新"
                onClick={() => void load(pageNo)}
              />
            </Space.Compact>
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

export function RegistrationChecklistConfigPage() {
  const [config, setConfig] = useState<RegistrationChecklistConfig>(),
    [error, setError] = useState("");
  const [loading, setLoading] = useState(false),
    [saving, setSaving] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setConfig(await api.registrationChecklistConfig());
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
  const updateTitle = (id: number, title: string) =>
    setConfig((value) =>
      value?.draft
        ? {
            ...value,
            draft: {
              ...value.draft,
              items: value.draft.items.map((item) =>
                item.id === id ? { ...item, title } : item,
              ),
            },
          }
        : value,
    );
  const save = async () => {
    if (!config?.draft) return;
    setSaving(true);
    try {
      await api.saveRegistrationChecklistDraft({
        templateVersion: config.templateVersion,
        idempotencyKey: key(),
        items: config.draft.items,
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
          {draft.items.map((item) => (
            <div className="registration-config-item" key={item.id}>
              <Input
                value={item.title}
                disabled={item.itemType === "study_planner"}
                onChange={(event) => updateTitle(item.id, event.target.value)}
                addonAfter={
                  item.itemType === "study_planner" ? "系统固定项" : undefined
                }
              />
            </div>
          ))}
        </div>
      ) : (
        <Empty description="暂无草稿，请复制已发布版本" />
      )}
    </section>
  );
}
