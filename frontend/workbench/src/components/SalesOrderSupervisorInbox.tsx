import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  Alert,
  Avatar,
  Button,
  Drawer,
  Empty,
  Input,
  Modal,
  Segmented,
  Skeleton,
  Space,
  Tag,
  Typography,
  message,
} from "antd";
import {
  CheckOutlined,
  CloseOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import {
  api,
  type AdvancedFilterGroup,
  type SalesOrder,
  type SalesOrderSupervisorInboxItem,
} from "../services/api";
import SalesOrderDetailCards, {
  SALES_ORDER_TASK_LABELS,
} from "./SalesOrderDetailCards";
import { formatTimestamp } from "../services/time";
import { useSubmissionGuard } from "../services/submissionGuard";
import { AdvancedFilterToolbar } from "./AdvancedFilter";

const STATUS_LABELS = {
  pending: "待审批",
  confirmed: "已通过",
  rejected: "已驳回",
  cancelled: "已取消",
} as const;
const STATUS_COLORS = {
  pending: "gold",
  confirmed: "green",
  rejected: "red",
  cancelled: "default",
} as const;

export default function SalesOrderSupervisorInbox({
  scopeControl,
  requestedConfirmationId,
  requestedOrderId,
}: {
  scopeControl?: ReactNode;
  requestedConfirmationId?: number;
  requestedOrderId?: number;
}) {
  const [handled, setHandled] = useState(false),
    [keyword, setKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [items, setItems] = useState<SalesOrderSupervisorInboxItem[]>([]),
    [selectedId, setSelectedId] = useState<number>();
  const [detail, setDetail] = useState<SalesOrder>(),
    [loading, setLoading] = useState(false),
    [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState(""),
    [detailError, setDetailError] = useState(""),
    [drawerOpen, setDrawerOpen] = useState(false);
  const [cursor, setCursor] = useState<string>(),
    [hasMore, setHasMore] = useState(true),
    [loadingMore, setLoadingMore] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null),
    sentinelRef = useRef<HTMLDivElement>(null);
  const listGeneration = useRef(0),
    detailGeneration = useRef(0),
    targetGeneration = useRef(0),
    inflightPages = useRef(new Set<string>()),
    targetConfirmation = useRef<SalesOrderSupervisorInboxItem | undefined>(undefined);
  const [decision, setDecision] = useState<"confirm" | "reject">(),
    [reason, setReason] = useState("");
  const { submitting, run, resetIntent } = useSubmissionGuard();
  const load = useCallback(
    async (append = false) => {
      const requestKey = `${append ? cursor || "none" : "first"}:${handled}:${keyword.trim()}:${JSON.stringify(advancedFilter)}`;
      if (inflightPages.current.has(requestKey)) return;
      inflightPages.current.add(requestKey);
      const generation = append ? listGeneration.current : ++listGeneration.current;
      if (append) setLoadingMore(true);
      else setLoading(true);
      setError("");
      try {
        const result = await api.salesOrderSupervisorCursor({
          cursor: append ? cursor : undefined,
          limit: 20,
          handled,
          keyword: keyword.trim() || undefined,
          advancedFilter,
        });
        if (generation !== listGeneration.current) return;
        const pinnedTarget = targetConfirmation.current;
        const replacement = pinnedTarget
          ? [pinnedTarget, ...result.list.filter((item) => item.id !== pinnedTarget.id)]
          : result.list;
        setItems((current) =>
          append
            ? [
                ...current,
                ...result.list.filter(
                  (item) =>
                    !current.some((existing) => existing.id === item.id),
                ),
              ]
            : replacement,
        );
        setCursor(result.nextCursor);
        setHasMore(result.hasMore);
        if (!append)
          setSelectedId((current) =>
            pinnedTarget ? pinnedTarget.id : current && replacement.some((item) => item.id === current)
              ? current
              : replacement[0]?.id,
          );
      } catch (loadError) {
        if (generation === listGeneration.current) setError(
          loadError instanceof Error
            ? loadError.message
            : "主管确认列表加载失败",
        );
      } finally {
        inflightPages.current.delete(requestKey);
        if (generation === listGeneration.current) {
          setLoading(false);
          setLoadingMore(false);
        }
      }
    },
    [advancedFilter, cursor, handled, keyword],
  );
  useEffect(() => {
    void load();
  }, [advancedFilter, handled, keyword]);
  useEffect(() => {
    if (!requestedConfirmationId) return;
    targetConfirmation.current = undefined;
    const generation = ++targetGeneration.current;
    void api
      .salesOrderSupervisorConfirmation(requestedConfirmationId)
      .then((item) => {
        if (generation !== targetGeneration.current) return;
        targetConfirmation.current = item;
        setItems((current) => [
          item,
          ...current.filter((existing) => existing.id !== item.id),
        ]);
        setSelectedId(item.id);
      })
      .catch((loadError) =>
        generation === targetGeneration.current && setError(
          loadError instanceof Error
            ? loadError.message
            : "主管确认任务定位失败",
        ),
      );
  }, [requestedConfirmationId]);
  useEffect(() => {
    if (!requestedOrderId || requestedConfirmationId) return;
    const match = items.find((item) => item.orderId === requestedOrderId);
    if (match) setSelectedId(match.id);
  }, [items, requestedConfirmationId, requestedOrderId]);
  useEffect(() => {
    const root = scrollRef.current,
      node = sentinelRef.current;
    if (!root || !node || !hasMore || loading || loadingMore || !cursor) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) void load(true);
      },
      { root, rootMargin: "160px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [cursor, hasMore, load, loading, loadingMore]);
  const selected = useMemo(
    () => items.find((item) => item.id === selectedId),
    [items, selectedId],
  );
  useEffect(() => {
    if (!selected) {
      setDetail(undefined);
      return;
    }
    setDetailLoading(true);
    setDetailError("");
    const generation = ++detailGeneration.current;
    void api
      .salesOrder(selected.orderId)
      .then((order) => {
        if (generation === detailGeneration.current) setDetail(order);
      })
      .catch((loadError) => {
        if (generation !== detailGeneration.current) return;
        setDetail(undefined);
        setDetailError(
          loadError instanceof Error ? loadError.message : "订单详情加载失败",
        );
      })
      .finally(() => {
        if (generation === detailGeneration.current) setDetailLoading(false);
      });
  }, [selected]);
  const submit = async () => {
    if (!selected || !decision || !reason.trim()) {
      message.warning("请填写主管意见");
      return;
    }
    const action = decision;
    await run(async ({ complete, idempotencyKey }) => {
      await api.decideSalesOrderSupervisor(selected.orderId, action, {
        confirmationId: selected.id,
        taskId: selected.taskId,
        reason: reason.trim(),
        approvalRoundId: selected.approvalRoundId,
        orderVersion: selected.orderVersion,
        roundVersion: selected.roundVersion,
        confirmationVersion: selected.version,
        idempotencyKey,
      });
      complete();
      setDecision(undefined);
      setReason("");
      message.success(
        action === "confirm"
          ? "主管审批已通过"
          : "主管审批已驳回，订单退回销售补正",
      );
      await load();
    }).catch((saveError) =>
      message.error(
        saveError instanceof Error ? saveError.message : "主管审批提交失败",
      ),
    );
  };
  const detailContent = detailLoading ? (
    <Skeleton active paragraph={{ rows: 10 }} />
  ) : detailError ? (
    <Alert type="error" showIcon message={detailError} />
  ) : selected && detail ? (
    <div className="sales-order-supervisor-detail">
      <Alert
        type={
          selected.status === "pending"
            ? "info"
            : selected.status === "rejected"
              ? "error"
              : "success"
        }
        showIcon
        message={`${selected.requesterUserName || "审批人"}申请主管审批`}
        description={
          <Space direction="vertical" size={2}>
            <span>{selected.requestReason}</span>
            <Typography.Text type="secondary">
              {formatTimestamp(selected.requestedAt)}
            </Typography.Text>
            {selected.decisionReason && (
              <span>主管意见：{selected.decisionReason}</span>
            )}
          </Space>
        }
      />
      {selected.status === "pending" && (
        <Space wrap className="sales-order-supervisor-actions">
          <Button
            type="primary"
            icon={<CheckOutlined />}
            onClick={() => {
              resetIntent();
              setReason("");
              setDecision("confirm");
            }}
          >
            通过
          </Button>
          <Button
            danger
            icon={<CloseOutlined />}
            onClick={() => {
              resetIntent();
              setReason("");
              setDecision("reject");
            }}
          >
            驳回
          </Button>
        </Space>
      )}
      <SalesOrderDetailCards order={detail} mode="approval-done" />
    </div>
  ) : (
    <Empty description="选择一条主管审批" />
  );
  return (
    <section className="workspace-page business-inbox-page sales-order-supervisor-page">
      <header className="business-inbox-scope-bar">
        <div className="business-inbox-scope-row">
          {scopeControl}
          <Segmented
            value={handled ? "done" : "todo"}
            onChange={(value) => setHandled(value === "done")}
            options={[
              { label: "待处理", value: "todo" },
              { label: "已处理", value: "done" },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </div>
      </header>
      <div className="business-inbox-layout">
        <aside className="business-inbox-list-pane">
          <div className="business-inbox-toolbar">
            <AdvancedFilterToolbar
              scene="order"
              placeholder="搜索订单号 / 学员姓名 / 手机号"
              keyword={keyword}
              value={advancedFilter}
              onKeyword={setKeyword}
              onChange={setAdvancedFilter}
            />
          </div>
          {error && (
            <Alert
              className="business-inbox-error"
              type="error"
              showIcon
              message={error}
              action={
                <Button size="small" onClick={() => void load()}>
                  重试
                </Button>
              }
            />
          )}
          <div
            ref={scrollRef}
            className="business-inbox-scroll"
            onScroll={(event) => {
              const node = event.currentTarget;
              if (
                !loading &&
                !loadingMore &&
                hasMore &&
                cursor &&
                node.scrollHeight - node.scrollTop - node.clientHeight < 80
              )
                void load(true);
            }}
          >
            {loading && !items.length ? (
              <Skeleton active paragraph={{ rows: 6 }} />
            ) : !items.length && !error ? (
              <Empty description="暂无主管审批" />
            ) : (
              items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={
                    item.id === selectedId
                      ? "business-inbox-item active"
                      : "business-inbox-item"
                  }
                  onClick={() => {
                    setSelectedId(item.id);
                    if (window.matchMedia("(max-width: 768px)").matches)
                      setDrawerOpen(true);
                  }}
                >
                  <div className="business-inbox-item-main">
                    <Avatar>{item.studentName.slice(0, 1)}</Avatar>
                    <div className="business-inbox-item-copy">
                      <div className="business-inbox-item-title">
                        <strong>{item.studentName}</strong>
                        <Tag color={STATUS_COLORS[item.status]}>
                          {STATUS_LABELS[item.status]}
                        </Tag>
                      </div>
                      <span>{item.orderNo}</span>
                      <span>
                        {SALES_ORDER_TASK_LABELS[item.taskDefinitionKey]}
                      </span>
                    </div>
                  </div>
                  <div className="business-inbox-item-meta">
                    <span>
                      {item.requesterUserName || "审批人"} ·{" "}
                      {formatTimestamp(item.requestedAt)}
                    </span>
                  </div>
                </button>
              ))
            )}
            {items.length > 0 && (
              <div ref={sentinelRef} className="business-inbox-list-state">
                <Typography.Text type="secondary">
                  {loadingMore
                    ? "加载中…"
                    : hasMore
                      ? "继续下滑加载"
                      : "已加载全部主管审批"}
                </Typography.Text>
              </div>
            )}
          </div>
        </aside>
        <main className="business-inbox-detail-pane">{detailContent}</main>
      </div>
      <Drawer
        className="business-inbox-mobile-drawer sales-order-mobile-drawer"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title="主管审批详情"
        width="100%"
      >
        {detailContent}
      </Drawer>
      <Modal
        title={decision === "confirm" ? "通过主管审批" : "驳回并退回销售"}
        open={Boolean(decision)}
        onCancel={() => {
          setDecision(undefined);
          setReason("");
          resetIntent();
        }}
        onOk={() => void submit()}
        confirmLoading={submitting}
        okText={decision === "confirm" ? "通过" : "驳回"}
        okButtonProps={{ danger: decision === "reject" }}
      >
        <Typography.Text strong>主管意见</Typography.Text>
        <Input.TextArea
          rows={5}
          maxLength={1000}
          showCount
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="填写主管意见（必填）"
        />
      </Modal>
    </section>
  );
}
