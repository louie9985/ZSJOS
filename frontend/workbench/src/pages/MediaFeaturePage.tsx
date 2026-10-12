import ResourceLink, { LinkedText, resourceTarget } from '../components/ResourceLink';
import ResourceLinkInput from '../components/ResourceLinkInput';
import { ticketSnapshotText } from '../services/productionTicketPresentation';
import AttachmentCard from '../components/AttachmentCard';
import { TICKET_CHANGED, notifyTicketChanged } from '../services/productionTicketEvents';
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
  | "content"
  | "tickets"
  | "positioning";
type Row = (
  | MediaContent
  | ProductionTicket
  | PositioningCard
) & { id: number; version: number; availableActions: string[] };
const labels: Record<string, string> = {
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
  content: "内容生产",
  tickets: "拍剪工单",
  positioning: "账号定位",
};
const actionLabels: Record<string, string> = {
  COMPLETE_TOPIC: "完成选题",
  SUBMIT_PRODUCTION: "提交制作",
  SUBMIT_ACCEPTANCE: "提交验收",
  APPROVE_CONTENT: "验收通过",
  REJECT_CONTENT: "验收退回",
  START_CONTENT_REVISION: "开始修改",
  RESUBMIT_PRODUCTION: "重新提交",
  CLAIM_TICKET: "抢单",
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
  content: 'contentId',
  tickets: 'ticketId',
  positioning: 'positioningCardId',
};

async function loadRows(
  feature: MediaFeature,
  pageNo: number,
  keyword?: string,
): Promise<{ list: Row[]; total: number }> {
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
      SUBMIT_TICKET: () => api.productionTicket.submit(row.id, { version: row.version, videoSentToOperator: true }),
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
  const [params, setParams] = useSearchParams();
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
  const [contentClasses, setContentClasses] = useState<DictData[]>([]);
  const [createForm] = Form.useForm<Record<string, unknown>>();
  const createPermission =
    feature === "content"
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
      api.dictDataByType("zsjos_content_class"),
    ]);
    const [a, u, s, c] = results;
    if (a.status === "fulfilled") setAccounts(a.value.list);
    if (u.status === "fulfilled") setUsers(u.value);
    if (s.status === "fulfilled")
      setStudents(
        s.value.list.map((x) => ({ personId: x.personId, name: x.name })),
      );
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
  const doAction = async (action: string) => {
    if (!selected) return;
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
      feature === "content"
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
          {canCreate && feature !== "tickets" && (
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
                          onClick={() => {
                            if (feature === "positioning" && action === "START_POSITIONING_REVISION") {
                              Modal.confirm({
                                title: "确认修改定位卡？", width: 520,
                                content: "确认后，当前定位卡将进入草稿状态。修改后需重新提交运营审核、学员确认并补齐确认凭证。已有历史版本和账号已应用版本保留。是否继续修改？",
                                okText: "确认修改", cancelText: "取消", autoFocusButton: "cancel",
                                mask: { closable: false }, keyboard: false,
                                onOk: () => doAction(action),
                              });
                              return;
                            }
                            void doAction(action);
                          }}
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
        contentClasses={contentClasses}
        onDeadlineChange={setCreateDeadlineAt}
        onCancel={() => setCreateOpen(false)}
        onSubmit={() => void submitCreate()}
      />
    </section>
  );
}

function requestActionReason(title: string, required = true): Promise<string | undefined> {
  return new Promise((resolve) => {
    let value = "";
    Modal.confirm({
      title,
      content: (
        <Input.TextArea
          autoFocus
          maxLength={500}
          placeholder={required ? "请输入驳回理由" : "请输入通过说明（可选）"}
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
        if (required && !reason) {
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

export function ContentPage({ permissions = [] }: { permissions?: string[] }) {
  return <MediaFeaturePage feature="content" permissions={permissions} />;
}
export { default as ProductionTicketsPage } from './ProductionTicketsPage';

export function ProductionTicketDetail({ ticket }: { ticket: ProductionTicket }) {
  const context = ticket.dispatchContext;
  const fields = ticket.formFields || [];
  const values = ticket.formValues || {};
  const accounts = ticket.accounts?.length ? ticket.accounts : context?.accounts || [];
  const attachedIds = new Set(fields.filter(field => field.type === 'attachment').flatMap(field => Array.isArray(values[field.key]) ? (values[field.key] as unknown[]).map(Number) : []));
  const attachment = (file: NonNullable<ProductionTicket['requestAttachments']>[number]) => <AttachmentCard key={file.id} name={file.name} load={async () => {
    const current = await api.productionTicket.get(ticket.id);
    const resource = current.requestAttachments?.find(item => item.id === file.id);
    if (!resource) throw new Error('附件不存在或不可访问');
    return resource;
  }} />;
  return <div className="media-ticket-detail">
    <section className="media-feature-card"><Typography.Title level={5}>制作需求</Typography.Title>
      <p>提交人：{ticket.submitterName || '未记录'}</p>
      <div className="production-ticket-request-fields">{fields.map(field => {
        const text = formatTicketValue(values[field.key]);
        const wide = field.type === 'attachment' || text.length > 120 || /https?:\/\//.test(text);
        return <div key={field.key} className={wide ? 'wide' : ''}><span>{field.label}</span><div>{field.type === 'attachment' ? (ticket.requestAttachments || []).filter(file => Array.isArray(values[field.key]) && (values[field.key] as unknown[]).map(Number).includes(file.id)).map(attachment) : <LinkedText text={text} resource />}</div></div>;
      })}</div>
      <div className="media-ticket-operator-remark"><Typography.Text strong>运营备注</Typography.Text><Typography.Paragraph>{context?.operatorRemark || '未填写'}</Typography.Paragraph></div>
      {(ticket.requestAttachments || []).filter(file => !attachedIds.has(file.id)).length > 0 && <div><Typography.Text strong>补充附件</Typography.Text>{(ticket.requestAttachments || []).filter(file => !attachedIds.has(file.id)).map(attachment)}</div>}
    </section>
    <section className="media-feature-card"><Typography.Title level={5}>关联账号</Typography.Title>{accounts.length ? <div className="production-ticket-accounts">{accounts.map((account, index) => <div key={account.accountId || index} className="media-ticket-account-content">{account.coverUrl && <img src={account.coverUrl} alt="账号封面" />}<div><Typography.Text strong>{account.accountName || account.accountNo || '未命名账号'}</Typography.Text><p>{account.accountNo}</p>{account.homepageUrl ? <ResourceLink href={account.homepageUrl} variant="resource" /> : <span>未填写主页链接</span>}</div></div>)}</div> : <p>未关联账号</p>}</section>
    {(context?.completionRemark || context?.completionUrl) && <section className="media-feature-card"><Typography.Title level={5}>成品交付</Typography.Title><div className="production-ticket-request-fields"><div className="wide"><span>成品链接</span>{context.completionUrl ? <ResourceLink href={context.completionUrl} variant="resource" /> : '未填写'}</div><div className="wide"><span>提交备注</span><div>{context.completionRemark || '未填写'}</div></div></div></section>}
  </div>;
}

function formatTicketValue(value: unknown) {
  return ticketSnapshotText(value);
}

function ProductionTicketSnapshot({ ticket }: { ticket?: ProductionTicket }) {
  return ticket ? <ProductionTicketDetail ticket={ticket} /> : null;
}

export function ProductionTicketAssignmentHost({ permissions = [] }: { permissions?: string[] }) {
  const enabled = hasPermission(permissions, 'zsjos:production-ticket:accept');
  const [pending, setPending] = useState<ProductionTicket[]>([]), [rejecting, setRejecting] = useState(false), [reason, setReason] = useState(''), [saving, setSaving] = useState(false);
  const load = useCallback(async () => { if (!enabled) return; try { setPending(await api.productionTicket.pendingAssignments()); } catch { setPending([]); } }, [enabled]);
  useEffect(() => { void load(); if (!enabled) return; const timer = window.setInterval(() => void load(), 30000); return () => window.clearInterval(timer); }, [enabled, load]);
  const ticket = pending[0];
  const finish = async (accept: boolean) => { if (!ticket) return; if (!accept && (!rejecting || !reason.trim())) { setRejecting(true); if (rejecting) message.error('请填写拒接原因'); return; } try { setSaving(true); if (accept) await api.productionTicket.accept(ticket.id, ticket.version); else await api.productionTicket.rejectAssignment(ticket.id, ticket.version, reason.trim()); notifyTicketChanged(); message.success(accept ? '已接单' : '已拒接，工单已进入公共池'); setRejecting(false); setReason(''); await load(); } catch (cause) { message.error(errorText(cause)); } finally { setSaving(false); } };
  return <Modal width="min(960px, calc(100vw - 32px))" title="拍剪指定派单" open={Boolean(ticket)} closable={false} maskClosable={false} keyboard={false} footer={null}><div className="media-ticket-detail-grid"><ProductionTicketSnapshot ticket={ticket}/><section className="media-ticket-action-bar"><Typography.Text strong>操作区</Typography.Text><Space wrap><Button danger loading={saving} disabled={!hasPermission(permissions, 'zsjos:production-ticket:reject-assignment')} onClick={() => void finish(false)}>{rejecting ? '确认拒接' : '拒绝接单'}</Button><Button type="primary" loading={saving} onClick={() => void finish(true)}>接单</Button></Space>{rejecting && <Input.TextArea autoFocus rows={3} maxLength={500} showCount value={reason} onChange={event => setReason(event.target.value)} placeholder="请填写拒接原因"/>}</section></div></Modal>;
}
export function PositioningPage({
  permissions = [],
}: {
  permissions?: string[];
}) {
  return <MediaFeaturePage feature="positioning" permissions={permissions} />;
}
