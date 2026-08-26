import { ReloadOutlined } from "@ant-design/icons";
import {
  Alert,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Select,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import type { FormInstance } from "antd/es/form";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ApiError,
  api,
  type DictData,
  type MediaAccount,
  type MediaContent,
  type PositioningCard,
  type ProductionTicket,
  type SimpleUser,
} from "../services/api";
import { hasPermission } from "../services/managementAccess";
import { formatTimestamp, type Timestamp } from "../services/time";

export type MediaFeature =
  | "accounts"
  | "content"
  | "tickets"
  | "positioning";
type Row = (
  | MediaAccount
  | MediaContent
  | ProductionTicket
  | PositioningCard
) & { id: number; version: number; availableActions: string[] };
const labels: Record<string, string> = {
  s0: "S0",
  s1: "S1",
  s2: "S2",
  s3: "S3",
  s4: "S4",
  s5: "S5",
  s6: "S6",
  active: "进行中",
  completed: "已完成",
  cancelled: "已取消",
  co_creating: "共创中",
  ip_review: "IP审核",
  operator_feasibility: "运营复核",
  student_confirm: "学员确认",
  trial_14d: "14天试跑",
  confirmed: "已确认",
  archived: "已归档",
  topic: "选题",
  script: "脚本",
  in_production: "制作中",
  acceptance: "待验收",
  published: "已发布",
  rejected: "已退回",
  revising: "修改中",
  pending_accept: "待接单",
  accepted: "已接单",
  submitted: "已提交",
  checking: "待核对",
  pending: "待处理",
  draft: "草稿",
  approved: "审核通过",
};
const titles: Record<MediaFeature, string> = {
  accounts: "第三方账号",
  content: "内容生产",
  tickets: "拍剪工单",
  positioning: "账号定位",
};
const actionLabels: Record<string, string> = {
  ADVANCE_STAGE: "推进阶段",
  ROLLBACK_STAGE: "回退阶段",
  BIND_STUDENT: "绑定学员",
  UNBIND_STUDENT: "解除绑定",
  EDIT_ACCOUNT: "编辑账号",
  RESCUE_ACCOUNT: "挽救处理",
  REQUEST_ACCOUNT_REBIND: "申请换绑",
  COMPLETE_TOPIC: "完成选题",
  SUBMIT_PRODUCTION: "提交制作",
  SUBMIT_ACCEPTANCE: "提交验收",
  APPROVE_CONTENT: "验收通过",
  REJECT_CONTENT: "验收退回",
  START_CONTENT_REVISION: "开始修改",
  RESUBMIT_PRODUCTION: "重新提交",
  ACCEPT_TICKET: "接单",
  START_TICKET: "开始制作",
  SUBMIT_TICKET: "提交成品",
  START_TICKET_CHECK: "开始核对",
  APPROVE_TICKET: "通过",
  REJECT_TICKET: "返工",
  REACCEPT_TICKET: "重新接单",
  SUBMIT_POSITIONING_REVIEW: "提交审核",
  APPROVE_POSITIONING_FEASIBILITY: "运营通过",
  REJECT_POSITIONING_FEASIBILITY: "运营退回",
  START_POSITIONING_REVISION: "修改定位卡",
  accept: "接收",
  reject: "退回",
  submit: "提交",
  approve: "审核通过",
  archive: "归档",
};
const actionText = (action: string) => actionLabels[action] || action;
const statusText = (status?: string) =>
  status ? labels[status] || status : "未记录";
const errorText = (error: unknown) =>
  error instanceof ApiError && error.code === 403
    ? "无权访问该页面"
    : error instanceof Error
      ? error.message
      : "数据加载失败，请重试";
const detailQuery: Partial<Record<MediaFeature, string>> = {
  accounts: 'accountId',
  content: 'contentId',
  tickets: 'ticketId',
  positioning: 'positioningCardId',
};

async function loadRows(
  feature: MediaFeature,
  pageNo: number,
  keyword?: string,
): Promise<{ list: Row[]; total: number }> {
  if (feature === "accounts")
    return api.mediaAccount.page({ pageNo, pageSize: 20, keyword }) as Promise<{
      list: Row[];
      total: number;
    }>;
  if (feature === "content")
    return api.mediaContent.page({ pageNo, pageSize: 20, keyword }) as Promise<{
      list: Row[];
      total: number;
    }>;
  if (feature === "tickets")
    return api.productionTicket.page({
      pageNo,
      pageSize: 20,
      keyword,
    }) as Promise<{ list: Row[]; total: number }>;
  if (feature === "positioning") {
    const result = await api.positioningCard.page({ pageNo, pageSize: 20 });
    return { list: result.list as Row[], total: result.total };
  }
  return { list: [], total: 0 };
}
async function loadDetail(feature: MediaFeature, preferredId: number) {
  if (feature === "accounts") return api.mediaAccount.get(preferredId);
  if (feature === "content") return api.mediaContent.get(preferredId);
  if (feature === "tickets") return api.productionTicket.get(preferredId);
  if (feature === "positioning") return api.positioningCard.get(preferredId);
  return undefined;
}

async function runAction(
  feature: MediaFeature,
  row: Row,
  action: string,
  reason?: string,
) {
  if (feature === "accounts") return undefined;
  if (feature === "content") {
    const f: Record<string, () => Promise<boolean>> = {
      COMPLETE_TOPIC: () => api.mediaContent.completeTopic(row.id, row.version),
      SUBMIT_PRODUCTION: () =>
        api.mediaContent.submitProduction(row.id, row.version),
      SUBMIT_ACCEPTANCE: () =>
        api.mediaContent.submitAcceptance(row.id, row.version),
      APPROVE_CONTENT: () =>
        api.mediaContent.approveAcceptance(row.id, row.version),
      REJECT_CONTENT: () =>
        api.mediaContent.rejectAcceptance(row.id, row.version, reason || ""),
      START_CONTENT_REVISION: () =>
        api.mediaContent.startRevision(row.id, row.version),
      RESUBMIT_PRODUCTION: () =>
        api.mediaContent.resubmitProduction(row.id, row.version),
    };
    return f[action]?.();
  }
  if (feature === "tickets") {
    const f: Record<string, () => Promise<boolean>> = {
      ACCEPT_TICKET: () => api.productionTicket.accept(row.id, row.version),
      START_TICKET: () =>
        api.productionTicket.startProduction(row.id, row.version),
      SUBMIT_TICKET: () => api.productionTicket.submit(row.id, row.version),
      START_TICKET_CHECK: () =>
        api.productionTicket.startCheck(row.id, row.version),
      APPROVE_TICKET: () => api.productionTicket.approve(row.id, row.version),
      REJECT_TICKET: () =>
        api.productionTicket.reject(row.id, row.version, reason || ""),
      REACCEPT_TICKET: () => api.productionTicket.reaccept(row.id, row.version),
    };
    return f[action]?.();
  }
  if (feature === "positioning") {
    const f: Record<string, () => Promise<boolean>> = {
      SUBMIT_POSITIONING_REVIEW: () =>
        api.positioningCard.submitReview(row.id, row.version),
      APPROVE_POSITIONING_FEASIBILITY: () =>
        api.positioningCard.operatorApprove(row.id, row.version),
      REJECT_POSITIONING_FEASIBILITY: () =>
        api.positioningCard.operatorReject(row.id, row.version, reason || ""),
      START_POSITIONING_REVISION: async () => {
        await api.positioningCard.startRevision(row.id, row.version);
        return true;
      },
    };
    return f[action]?.();
  }
  return undefined;
}

export default function MediaFeaturePage({
  feature,
  permissions = [],
}: {
  feature: MediaFeature;
  permissions?: string[];
}) {
  const [params] = useSearchParams();
  const requestedId = detailQuery[feature]
    ? Number(params.get(detailQuery[feature]!)) || undefined
    : undefined;
  const [rows, setRows] = useState<Row[]>([]);
  const [selected, setSelected] = useState<Row>();
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [createDeadlineAt, setCreateDeadlineAt] = useState<Timestamp>();
  const [accounts, setAccounts] = useState<MediaAccount[]>([]);
  const [users, setUsers] = useState<SimpleUser[]>([]);
  const [students, setStudents] = useState<
    Array<{ personId: number; name?: string }>
  >([]);
  const [platforms, setPlatforms] = useState<DictData[]>([]);
  const [contentClasses, setContentClasses] = useState<DictData[]>([]);
  const [createForm] = Form.useForm<Record<string, unknown>>();
  const [accountAction, setAccountAction] = useState<string>();
  const [accountActionLoading, setAccountActionLoading] = useState(false);
  const [accountActionForm] = Form.useForm<Record<string, unknown>>();
  const createPermission =
    feature === "accounts"
      ? "zsjos:media-account:create"
      : feature === "content"
        ? "zsjos:content:create"
        : feature === "tickets"
          ? "zsjos:production-ticket:create"
          : feature === "positioning"
            ? "zsjos:positioning-card:create"
            : "";
  const canCreate =
    Boolean(createPermission) && hasPermission(permissions, createPermission);
  const load = useCallback(
    async (targetPage = page, preferredId?: number) => {
      setLoading(true);
      setError("");
      setDetailError("");
      try {
        const result = await loadRows(
          feature,
          targetPage,
          keyword || undefined,
        );
        setRows(result.list);
        setTotal(result.total);
        setPage(targetPage);
        let next =
          result.list.find((row) => row.id === preferredId) || result.list[0];
        if (preferredId && next?.id !== preferredId)
          next = ((await loadDetail(feature, preferredId)) as Row) || next;
        setSelected(next);
      } catch (cause) {
        setRows([]);
        setSelected(undefined);
        setError(errorText(cause));
      } finally {
        setLoading(false);
      }
    },
    [feature, keyword, page],
  );
  useEffect(() => {
    void load(1, requestedId);
  }, [feature, keyword, requestedId]);
  const openDetail = async (row: Row) => {
    setSelected(row);
    setDetailLoading(true);
    try {
      const detail = await loadDetail(feature, row.id);
      if (detail) setSelected({ ...row, ...detail } as Row);
    } catch (cause) {
      setDetailError(errorText(cause));
    } finally {
      setDetailLoading(false);
    }
  };
  const openCreate = async () => {
    createForm.resetFields();
    setCreateDeadlineAt(undefined);
    setCreateOpen(true);
    const results = await Promise.allSettled([
      api.mediaAccount.page({ pageNo: 1, pageSize: 100 }),
      api.simpleUsers(),
      api.mediaStudents.page({ pageNo: 1, pageSize: 100 }),
      api.dictDataByType("zsjos_account_platform"),
      api.dictDataByType("zsjos_content_class"),
    ]);
    const [a, u, s, p, c] = results;
    if (a.status === "fulfilled") setAccounts(a.value.list);
    if (u.status === "fulfilled") setUsers(u.value);
    if (s.status === "fulfilled")
      setStudents(
        s.value.list.map((x) => ({ personId: x.personId, name: x.name })),
      );
    if (p.status === "fulfilled") setPlatforms(p.value);
    if (c.status === "fulfilled") setContentClasses(c.value);
    if (
      results.some(
        (x) =>
          x.status === "rejected" &&
          x.reason instanceof ApiError &&
          x.reason.code !== 403,
      )
    )
      message.error("部分候选数据加载失败，请重试");
  };
  const submitCreate = async () => {
    try {
      const values = await createForm.validateFields();
      if (feature === "tickets" && !createDeadlineAt) {
        message.error("请选择截止时间");
        return;
      }
      setCreateLoading(true);
      if (feature === "accounts")
        await api.mediaAccount.create({
          ...values,
          platformLabelSnapshot:
            platforms.find((x) => x.value === values.platformValue)?.label ||
            "",
        } as never);
      if (feature === "content")
        await api.mediaContent.create({
          ...values,
          contentClassLabelSnapshot:
            contentClasses.find((x) => x.value === values.contentClassValue)
              ?.label || "",
        } as never);
      if (feature === "tickets")
        await api.productionTicket.create({
          ...values,
          deadlineAt: createDeadlineAt,
        } as never);
      if (feature === "positioning")
        await api.positioningCard.create(values as never);
      message.success("创建成功");
      setCreateOpen(false);
      await load(1);
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields)
        message.error(errorText(cause));
    } finally {
      setCreateLoading(false);
    }
  };
  const openAccountAction = async (action: string) => {
    if (!selected || feature !== "accounts") return;
    accountActionForm.resetFields();
    const account = selected as MediaAccount;
    if (action === "EDIT_ACCOUNT")
      accountActionForm.setFieldsValue({
        nickname: account.nickname,
        platformAccountId: account.platformAccountId,
        leadDirection: account.leadDirection,
        directorUserId: account.directorUserId,
      });
    if (action === "RESCUE_ACCOUNT")
      accountActionForm.setFieldValue(
        "status",
        account.rescueStatus === "in_progress" ? "recovered" : "in_progress",
      );
    setAccountAction(action);
    if (!students.length || !users.length) {
      const results = await Promise.allSettled([
        api.mediaAccount.studentCandidates(),
        api.simpleUsers(),
      ]);
      if (results[0].status === "fulfilled")
        setStudents(results[0].value);
      if (results[1].status === "fulfilled") setUsers(results[1].value);
    }
  };
  const submitAccountAction = async () => {
    if (!selected || !accountAction) return;
    try {
      const values = await accountActionForm.validateFields();
      const account = selected as MediaAccount;
      setAccountActionLoading(true);
      if (accountAction === "ADVANCE_STAGE")
        await api.mediaAccount.advanceStage(
          account.id,
          String(values.toStage),
          account.version,
          String(values.basis),
        );
      else if (accountAction === "ROLLBACK_STAGE")
        await api.mediaAccount.rollbackStage(
          account.id,
          String(values.toStage),
          account.version,
          String(values.basis),
        );
      else if (accountAction === "BIND_STUDENT")
        await api.mediaAccount.bindStudent(
          account.id,
          Number(values.studentPersonId),
          String(values.reason || ""),
        );
      else if (accountAction === "UNBIND_STUDENT")
        await api.mediaAccount.unbindStudent(
          account.id,
          String(values.reason || ""),
        );
      else if (accountAction === "EDIT_ACCOUNT")
        await api.mediaAccount.update(account.id, {
          version: account.version,
          nickname: String(values.nickname),
          platformAccountId: values.platformAccountId as string | undefined,
          leadDirection: values.leadDirection as string | undefined,
          directorUserId: values.directorUserId as number | undefined,
          accountGradeValue: account.accountGradeValue,
          accountGradeLabelSnapshot: account.accountGradeLabelSnapshot,
          healthStatusValue: account.healthStatusValue,
          healthStatusLabelSnapshot: account.healthStatusLabelSnapshot,
          riskLevelValue: account.riskLevelValue,
          riskLevelLabelSnapshot: account.riskLevelLabelSnapshot,
          healthJson: account.healthJson,
        });
      else if (accountAction === "RESCUE_ACCOUNT")
        await api.mediaAccount.rescue(
          account.id,
          account.version,
          String(values.status),
        );
      else if (accountAction === "REQUEST_ACCOUNT_REBIND")
        await api.mediaAccount.requestRebind(
          account.id,
          Number(values.targetStudentId),
          account.version,
        );
      message.success(
        accountAction === "REQUEST_ACCOUNT_REBIND"
          ? "换绑审批已发起"
          : "操作成功",
      );
      setAccountAction(undefined);
      await load(page, account.id);
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields)
        message.error(
          cause instanceof ApiError && cause.code === 409
            ? "记录已被修改，请刷新后重试"
            : errorText(cause),
        );
    } finally {
      setAccountActionLoading(false);
    }
  };
  const doAction = async (action: string) => {
    if (!selected) return;
    if (feature === "accounts") {
      await openAccountAction(action);
      return;
    }
    let reason: string | undefined;
    if (
      action === "REJECT_CONTENT" ||
      action === "REJECT_TICKET" ||
      action === "reject" ||
      action === "request-arbitration" ||
      action.startsWith("arbitrate-")
    ) {
      reason = await requestActionReason(
        action === "REJECT_CONTENT" ? "填写验收退回原因" : "填写处理原因",
      );
      if (!reason) return;
    }
    try {
      const operation = await runAction(feature, selected, action, reason);
      if (operation === undefined) return;
      message.success("操作成功");
      await load(page);
    } catch (cause) {
      message.error(
        cause instanceof ApiError && cause.code === 409
          ? "记录已被修改，请刷新后重试"
          : errorText(cause),
      );
    }
  };
  const columns = useMemo(
    () =>
      feature === "accounts"
        ? [
            {
              label: "账号编号",
              value: (r: Row) => (r as MediaAccount).accountNo,
            },
            { label: "昵称", value: (r: Row) => (r as MediaAccount).nickname },
            {
              label: "平台",
              value: (r: Row) => (r as MediaAccount).platformLabelSnapshot,
            },
            {
              label: "阶段",
              value: (r: Row) => statusText((r as MediaAccount).sStage),
            },
          ]
        : feature === "content"
          ? [
              {
                label: "内容编号",
                value: (r: Row) => (r as MediaContent).contentNo,
              },
              { label: "标题", value: (r: Row) => (r as MediaContent).title },
              { label: "状态", value: (r: Row) => statusText(r.status) },
            ]
          : feature === "tickets"
            ? [
                {
                  label: "工单编号",
                  value: (r: Row) => (r as ProductionTicket).ticketNo,
                },
                { label: "状态", value: (r: Row) => statusText(r.status) },
                {
                  label: "截止时间",
                  value: (r: Row) =>
                    formatTimestamp(
                      (r as ProductionTicket).deadlineAt ||
                        (r as ProductionTicket).expectedDeliveredAt,
                    ),
                },
              ]
            : feature === "positioning"
              ? [
                  {
                    label: "定位编号",
                    value: (r: Row) => (r as PositioningCard).cardNo,
                  },
                  { label: "状态", value: (r: Row) => statusText(r.status) },
                ]
              : [],
    [feature],
  );
  return (
    <section className={`workspace-page media-${feature}-page`}>
      <header className="media-feature-heading">
        <Typography.Title level={4}>{titles[feature]}</Typography.Title>
        <Space>
          {canCreate && (
            <Button type="primary" onClick={() => void openCreate()}>
              新增
            </Button>
          )}
          <Tooltip title="刷新">
            <Button
              aria-label="刷新"
              icon={<ReloadOutlined />}
              onClick={() => void load(page)}
            />
          </Tooltip>
        </Space>
      </header>
      <div className="media-feature-inbox-layout">
        <aside className="media-feature-list-pane">
          <div className="media-feature-toolbar">
            <Input.Search
              allowClear
              value={search}
              placeholder="搜索编号、名称或关键词"
              onChange={(event) => setSearch(event.target.value)}
              onSearch={(value) => {
                setKeyword(value.trim());
                setPage(1);
              }}
            />
          </div>
          {error && (
            <Alert
              showIcon
              type="error"
              message={error}
              action={
                <Button size="small" onClick={() => void load(page)}>
                  重试
                </Button>
              }
            />
          )}
          <div className="media-feature-scroll">
            {loading && !rows.length ? (
              Array.from({ length: 5 }, (_, i) => (
                <div className="media-feature-skeleton" key={i}>
                  <Skeleton active paragraph={{ rows: 2 }} />
                </div>
              ))
            ) : !rows.length && !error ? (
              <Empty description="暂无记录" />
            ) : (
              rows.map((row) => (
                <button
                  type="button"
                  key={row.id}
                  className={`media-feature-item${selected?.id === row.id ? " active" : ""}`}
                  onClick={() => void openDetail(row)}
                >
                  <div>
                    <strong>{columns[0]?.value(row) || "未命名记录"}</strong>
                    <span>{columns[1]?.value(row) || "未记录"}</span>
                  </div>
                  <Tag>{statusText(row.status)}</Tag>
                </button>
              ))
            )}
          </div>
          {total > 20 && (
            <Pagination
              simple
              current={page}
              pageSize={20}
              total={total}
              onChange={(value) => void load(value)}
            />
          )}
        </aside>
        <main className="media-feature-detail-pane">
          {detailLoading ? (
            <Skeleton active paragraph={{ rows: 12 }} />
          ) : detailError ? (
            <Alert showIcon type="error" message={detailError} />
          ) : selected ? (
            <div className="media-feature-overview-grid">
              <div className="media-feature-overview-main">
                <section className="media-feature-card">
                  <div className="media-feature-detail-title">
                    <div>
                      <Typography.Title level={4}>
                        {columns[0]?.value(selected) || "业务记录"}
                      </Typography.Title>
                      <Typography.Text type="secondary">
                        {columns[1]?.label}:{" "}
                        {columns[1]?.value(selected) || "未记录"}
                      </Typography.Text>
                    </div>
                    <Tag>{statusText(selected.status)}</Tag>
                  </div>
                  <div className="media-feature-fields">
                    {columns.map((column) => (
                      <div key={column.label}>
                        <span>{column.label}</span>
                        <strong>{column.value(selected) || "-"}</strong>
                      </div>
                    ))}
                  </div>
                </section>
                <section className="media-feature-card">
                  <Typography.Title level={5}>当前处理</Typography.Title>
                  <Space wrap>
                    {selected.availableActions?.length ? (
                      selected.availableActions.map((action) => (
                        <Button
                          key={action}
                          type="primary"
                          size="small"
                          onClick={() => void doAction(action)}
                        >
                          {actionText(action)}
                        </Button>
                      ))
                    ) : (
                      <Typography.Text type="secondary">
                        当前没有可执行操作
                      </Typography.Text>
                    )}
                  </Space>
                </section>
              </div>
              <aside className="media-feature-overview-aside">
                <section className="media-feature-card">
                  <Typography.Title level={5}>记录信息</Typography.Title>
                  <div className="media-feature-meta">
                    <span>版本</span>
                    <strong>{selected.version}</strong>
                    <span>业务状态</span>
                    <strong>{statusText(selected.status)}</strong>
                    <span>可用操作</span>
                    <strong>{selected.availableActions?.length || 0} 项</strong>
                  </div>
                </section>
              </aside>
            </div>
          ) : (
            <Empty description="从左侧选择一条记录" />
          )}
        </main>
      </div>
      <CreateMediaModal
        feature={feature}
        open={createOpen}
        loading={createLoading}
        form={createForm}
        accounts={accounts}
        users={users}
        students={students}
        platforms={platforms}
        contentClasses={contentClasses}
        onDeadlineChange={setCreateDeadlineAt}
        onCancel={() => setCreateOpen(false)}
        onSubmit={() => void submitCreate()}
      />
      {feature === "accounts" && selected && (
        <AccountActionModal
          action={accountAction}
          account={selected as MediaAccount}
          loading={accountActionLoading}
          form={accountActionForm}
          users={users}
          students={students}
          onCancel={() => setAccountAction(undefined)}
          onSubmit={() => void submitAccountAction()}
        />
      )}
    </section>
  );
}

function AccountActionModal({
  action,
  account,
  loading,
  form,
  users,
  students,
  onCancel,
  onSubmit,
}: {
  action?: string;
  account: MediaAccount;
  loading: boolean;
  form: FormInstance<Record<string, unknown>>;
  users: SimpleUser[];
  students: Array<{ personId: number; name?: string }>;
  onCancel: () => void;
  onSubmit: () => void;
}) {
  const stages = ["s0", "s1", "s2", "s3", "s4", "s5", "s6"];
  const currentIndex = stages.indexOf(account.sStage);
  const stageOptions = stages
    .filter((_, index) =>
      action === "ADVANCE_STAGE" ? index > currentIndex : index < currentIndex,
    )
    .map((value) => ({ value, label: value.toUpperCase() }));
  const studentOptions = students
    .filter((item) => item.personId !== account.studentPersonId)
    .map((item) => ({
      value: item.personId,
      label: item.name || `学员 ${item.personId}`,
    }));
  const userOptions = users
    .filter((item) => item.status === undefined || item.status === 0)
    .map((item) => ({ value: item.id, label: item.nickname }));
  return (
    <Modal
      title={action ? actionText(action) : ""}
      open={Boolean(action)}
      confirmLoading={loading}
      okText="确认"
      cancelText="取消"
      onCancel={onCancel}
      onOk={onSubmit}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        {(action === "ADVANCE_STAGE" || action === "ROLLBACK_STAGE") && (
          <>
            <Form.Item
              name="toStage"
              label="目标阶段"
              rules={[{ required: true, message: "请选择目标阶段" }]}
            >
              <Select options={stageOptions} />
            </Form.Item>
            <Form.Item
              name="basis"
              label="判断依据"
              rules={[{ required: true, message: "请填写判断依据" }]}
            >
              <Input.TextArea rows={3} maxLength={500} />
            </Form.Item>
          </>
        )}
        {action === "BIND_STUDENT" && (
          <>
            <Form.Item
              name="studentPersonId"
              label="学员"
              rules={[{ required: true, message: "请选择学员" }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={studentOptions}
              />
            </Form.Item>
            <Form.Item name="reason" label="绑定原因">
              <Input.TextArea rows={3} maxLength={300} />
            </Form.Item>
          </>
        )}
        {action === "UNBIND_STUDENT" && (
          <Form.Item
            name="reason"
            label="解绑原因"
            rules={[{ required: true, message: "请填写解绑原因" }]}
          >
            <Input.TextArea rows={3} maxLength={300} />
          </Form.Item>
        )}
        {action === "EDIT_ACCOUNT" && (
          <>
            <Form.Item
              name="nickname"
              label="账号昵称"
              rules={[{ required: true, max: 100 }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="platformAccountId" label="平台账号标识">
              <Input />
            </Form.Item>
            <Form.Item name="leadDirection" label="内容方向">
              <Input />
            </Form.Item>
            <Form.Item name="directorUserId" label="编导">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                options={userOptions}
              />
            </Form.Item>
          </>
        )}
        {action === "RESCUE_ACCOUNT" && (
          <Form.Item
            name="status"
            label="挽救状态"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { value: "in_progress", label: "挽救中" },
                { value: "recovered", label: "已恢复" },
                { value: "failed", label: "挽救失败" },
              ]}
            />
          </Form.Item>
        )}
        {action === "REQUEST_ACCOUNT_REBIND" && (
          <Form.Item
            name="targetStudentId"
            label="换绑至学员"
            rules={[{ required: true, message: "请选择目标学员" }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={studentOptions}
            />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}

function requestActionReason(title: string): Promise<string | undefined> {
  return new Promise((resolve) => {
    let value = "";
    Modal.confirm({
      title,
      content: (
        <Input.TextArea
          autoFocus
          maxLength={500}
          placeholder="请输入具体原因"
          rows={4}
          onChange={(event) => {
            value = event.target.value;
          }}
        />
      ),
      okText: "确认退回",
      cancelText: "取消",
      onCancel: () => resolve(undefined),
      onOk: () => {
        const reason = value.trim();
        if (!reason) {
          message.error("请填写退回原因");
          return Promise.reject();
        }
        resolve(reason);
      },
    });
  });
}

function CreateMediaModal({
  feature,
  open,
  loading,
  form,
  accounts,
  users,
  students,
  platforms,
  contentClasses,
  onDeadlineChange,
  onCancel,
  onSubmit,
}: {
  feature: MediaFeature;
  open: boolean;
  loading: boolean;
  form: FormInstance<Record<string, unknown>>;
  accounts: MediaAccount[];
  users: SimpleUser[];
  students: Array<{ personId: number; name?: string }>;
  platforms: DictData[];
  contentClasses: DictData[];
  onDeadlineChange: (value?: Timestamp) => void;
  onCancel: () => void;
  onSubmit: () => void;
}) {
  const accountOptions = accounts.map((item) => ({
    value: item.id,
    label: `${item.accountNo} · ${item.nickname}`,
  }));
  const userOptions = users
    .filter((item) => item.status === undefined || item.status === 0)
    .map((item) => ({ value: item.id, label: item.nickname }));
  const studentOptions = students.map((item) => ({
    value: item.personId,
    label: item.name || `学员 ${item.personId}`,
  }));
  return (
    <Modal
      title={`新增${titles[feature]}`}
      open={open}
      confirmLoading={loading}
      onCancel={onCancel}
      onOk={onSubmit}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        {feature === "accounts" && (
          <>
            <Form.Item
              name="platformValue"
              label="平台"
              rules={[{ required: true }]}
            >
              <Select
                options={platforms.map((item) => ({
                  value: item.value,
                  label: item.label,
                }))}
                disabled={!platforms.length}
                placeholder={platforms.length ? "选择平台" : "暂无可用平台字典"}
              />
            </Form.Item>
            <Form.Item
              name="nickname"
              label="账号昵称"
              rules={[{ required: true, max: 100 }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="platformAccountId" label="平台账号标识">
              <Input />
            </Form.Item>
            <Form.Item name="studentPersonId" label="绑定学员（可选）">
              <Select allowClear options={studentOptions} />
            </Form.Item>
            <Form.Item name="directorUserId" label="编导（可选）">
              <Select allowClear options={userOptions} />
            </Form.Item>
          </>
        )}
        {feature === "content" && (
          <>
            <Form.Item
              name="accountId"
              label="关联账号"
              rules={[{ required: true }]}
            >
              <Select options={accountOptions} />
            </Form.Item>
            <Form.Item
              name="title"
              label="内容标题"
              rules={[{ required: true, max: 200 }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="topic" label="选题说明">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item
              name="contentClassValue"
              label="内容分类"
              rules={[{ required: true }]}
            >
              <Select
                options={contentClasses.map((item) => ({
                  value: item.value,
                  label: item.label,
                }))}
                disabled={!contentClasses.length}
                placeholder={
                  contentClasses.length
                    ? "选择内容分类"
                    : "暂无可用内容分类字典"
                }
              />
            </Form.Item>
          </>
        )}
        {feature === "tickets" && (
          <>
            <Form.Item
              name="accountId"
              label="关联账号"
              rules={[{ required: true }]}
            >
              <Select options={accountOptions} />
            </Form.Item>
            <Form.Item
              name="reviewerUserId"
              label="运营核对人"
              rules={[{ required: true }]}
            >
              <Select options={userOptions} />
            </Form.Item>
            <Form.Item
              name="assigneeFilmingEditorUserId"
              label="剪拍专员（可选）"
            >
              <Select allowClear options={userOptions} />
            </Form.Item>
            <Form.Item name="scriptText" label="脚本">
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item name="deadlineAt" label="截止时间">
              <DatePicker
                showTime
                onChange={(date) => onDeadlineChange(date?.valueOf())}
                style={{ width: "100%" }}
              />
            </Form.Item>
            <Form.Item name="maxRevisionCount" label="返工上限">
              <InputNumber min={0} max={20} style={{ width: "100%" }} />
            </Form.Item>
          </>
        )}
        {feature === "positioning" && (
          <>
            <Form.Item
              name="accountId"
              label="关联账号"
              rules={[{ required: true }]}
            >
              <Select options={accountOptions} />
            </Form.Item>
            <Form.Item name="studentPersonId" label="绑定学员（可选）">
              <Select allowClear options={studentOptions} />
            </Form.Item>
            <Form.Item name="professionalRisk" label="专业风险">
              <Select
                options={[
                  { value: true, label: "是" },
                  { value: false, label: "否" },
                ]}
              />
            </Form.Item>
            <Form.Item name="layer1Json" label="定位基础内容">
              <Input.TextArea rows={3} />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}

export function AccountsPage({ permissions = [] }: { permissions?: string[] }) {
  return <MediaFeaturePage feature="accounts" permissions={permissions} />;
}
export function ContentPage({ permissions = [] }: { permissions?: string[] }) {
  return <MediaFeaturePage feature="content" permissions={permissions} />;
}
export function ProductionTicketsPage({
  permissions = [],
}: {
  permissions?: string[];
}) {
  const [view, setView] = useState<'pending' | 'mine' | 'pool'>('mine');
  const [rows, setRows] = useState<ProductionTicket[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState<ProductionTicket>();
  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const result = view === 'pending' ? { list: await api.productionTicket.pendingAssignments(), total: 0 } : view === 'pool' ? await api.productionTicket.poolPage({ pageNo: 1, pageSize: 100 }) : await api.productionTicket.page({ pageNo: 1, pageSize: 100 });
      setRows(result.list);
    } catch (cause) { setRows([]); setError(errorText(cause)); } finally { setLoading(false); }
  }, [view]);
  useEffect(() => { void load(); }, [load]);
  const act = async (ticket: ProductionTicket, action: string) => {
    try {
      if (action === 'CLAIM_TICKET') { setPreview(ticket); return; }
      await runAction('tickets', ticket as Row, action);
      message.success('操作成功'); await load();
    } catch (cause) { message.error(errorText(cause)); }
  };
  return <section className="workspace-page media-tickets-page"><header className="media-feature-heading"><Typography.Title level={4}>拍剪工单</Typography.Title><Tooltip title="刷新"><Button icon={<ReloadOutlined/>} onClick={() => void load()}/></Tooltip></header><Tabs activeKey={view} onChange={key => setView(key as typeof view)} items={[{ key: 'pending', label: '待接单' }, { key: 'mine', label: '我的工单' }, { key: 'pool', label: '公共池', disabled: !hasPermission(permissions, 'zsjos:production-ticket:pool-query') && !hasPermission(permissions, 'zsjos:production-ticket:claim') }]} />{error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}<div className="media-ticket-list">{loading ? <Skeleton active paragraph={{ rows: 6 }}/> : rows.length ? rows.map(ticket => <div className="media-ticket-row" key={ticket.id}><div><strong>{ticket.ticketNo}</strong><span>{ticket.dispatchContext?.studentName || '学员未记录'} · {ticket.dispatchContext?.platformLabel || '平台未记录'} · {ticket.dispatchContext?.accountName || ticket.dispatchContext?.accountNo || `账号 ${ticket.accountId}`}</span></div><Space wrap><Tag>{statusText(ticket.status)}</Tag><Button onClick={() => setPreview(ticket)}>查看快照</Button>{ticket.availableActions.filter(action => action !== 'REJECT_TICKET_ASSIGNMENT').map(action => <Button type={action === 'CLAIM_TICKET' ? 'primary' : 'default'} key={action} onClick={() => void act(ticket, action)}>{actionText(action)}</Button>)}</Space></div>) : <Empty description={view === 'pool' ? '公共池暂无可抢工单' : view === 'pending' ? '暂无待接指定单' : '暂无工单'}/>}</div><Modal width="min(960px, calc(100vw - 32px))" title={preview ? `${preview.ticketNo} · 派单快照` : '派单快照'} open={Boolean(preview)} onCancel={() => setPreview(undefined)} okText={preview?.availableActions.includes('CLAIM_TICKET') ? '确认抢单' : '关闭'} onOk={async () => { if (preview?.availableActions.includes('CLAIM_TICKET')) { try { await api.productionTicket.claim(preview.id, preview.version); message.success('抢单成功'); setPreview(undefined); await load(); } catch (cause) { message.error(errorText(cause)); } } else setPreview(undefined); }}><ProductionTicketSnapshot ticket={preview}/></Modal></section>;
}

function ProductionTicketSnapshot({ ticket }: { ticket?: ProductionTicket }) {
  if (!ticket) return null;
  const context = ticket.dispatchContext;
  if (!context) return <Empty description="暂无派单快照" />;
  return <div className="media-ticket-snapshot"><Typography.Paragraph><strong>学员：</strong>{context.studentName || '未记录'}</Typography.Paragraph><Typography.Paragraph><strong>账号：</strong>{context.platformLabel || '平台未记录'} · {context.accountName || context.accountNo || ticket.accountId}</Typography.Paragraph>{context.accountFields?.map(field => <Typography.Paragraph key={field.key}><strong>{field.label}：</strong>{field.displayValue || String(field.value ?? '未记录')}</Typography.Paragraph>)}<Typography.Title level={5}>完整定位卡</Typography.Title><pre>{JSON.stringify(context.positioning || {}, null, 2)}</pre></div>;
}

export function ProductionTicketAssignmentHost({ permissions = [] }: { permissions?: string[] }) {
  const enabled = hasPermission(permissions, 'zsjos:production-ticket:accept');
  const [pending, setPending] = useState<ProductionTicket[]>([]), [rejecting, setRejecting] = useState(false), [reason, setReason] = useState(''), [saving, setSaving] = useState(false);
  const load = useCallback(async () => { if (!enabled) return; try { setPending(await api.productionTicket.pendingAssignments()); } catch { setPending([]); } }, [enabled]);
  useEffect(() => { void load(); if (!enabled) return; const timer = window.setInterval(() => void load(), 30000); return () => window.clearInterval(timer); }, [enabled, load]);
  const ticket = pending[0];
  const finish = async (accept: boolean) => { if (!ticket) return; if (!accept && (!rejecting || !reason.trim())) { setRejecting(true); if (rejecting) message.error('请填写拒接原因'); return; } try { setSaving(true); if (accept) await api.productionTicket.accept(ticket.id, ticket.version); else await api.productionTicket.rejectAssignment(ticket.id, ticket.version, reason.trim()); message.success(accept ? '已接单' : '已拒接，工单已进入公共池'); setRejecting(false); setReason(''); await load(); } catch (cause) { message.error(errorText(cause)); } finally { setSaving(false); } };
  return <Modal width="min(960px, calc(100vw - 32px))" title="拍剪指定派单" open={Boolean(ticket)} closable={false} maskClosable={false} keyboard={false} footer={<Space><Button danger loading={saving} disabled={!hasPermission(permissions, 'zsjos:production-ticket:reject-assignment')} onClick={() => void finish(false)}>{rejecting ? '确认拒接' : '拒绝接单'}</Button><Button type="primary" loading={saving} onClick={() => void finish(true)}>接单</Button></Space>}><ProductionTicketSnapshot ticket={ticket}/>{rejecting && <Input.TextArea autoFocus rows={3} maxLength={500} showCount value={reason} onChange={event => setReason(event.target.value)} placeholder="请填写拒接原因"/>}</Modal>;
}
export function PositioningPage({
  permissions = [],
}: {
  permissions?: string[];
}) {
  return <MediaFeaturePage feature="positioning" permissions={permissions} />;
}
