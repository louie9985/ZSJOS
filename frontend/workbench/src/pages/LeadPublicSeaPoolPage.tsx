import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Segmented,
  Space,
  Skeleton,
  Tag,
  Typography,
  message,
} from "antd";
import { ExportOutlined, MessageOutlined, UserSwitchOutlined } from "@ant-design/icons";
import {
  api,
  type LeadPublicSeaPoolItem,
  type LeadPublicSeaPoolStatus,
  type LeadInboxFilterProfile,
  type AdvancedFilterGroup,
  type ManagedLead,
} from "../services/api";
import LeadDetail from "../components/LeadDetail";
import type { ToolbarAction } from "../components/OverflowToolbar";
import { NameAvatar } from "../components/LeadDetailOverview";
import { formatTimestamp } from "../services/time";
import { AdvancedFilterToolbar } from "../components/AdvancedFilter";
import { isLeadInboxUnauthorized } from "../services/leadManagement";

import BusinessTable from "../components/BusinessTable";
import DateTimeText from "../components/DateTimeText";
import ResizableDetailDrawer from "../components/ResizableDetailDrawer";
import { useInboxTableLayout } from "../services/inboxLayout";
import { useTheme } from "../components/Theme/ThemeContext";
import { INBOX_LAYOUT_OPTIONS } from "../constants";
import type { ProColumns } from "@ant-design/pro-components";

const statusLabel: Record<LeadPublicSeaPoolStatus, string> = {
  waiting_assignment: "待指派",
  assigned: "协同跟进中",
  deal_pending: "成交审批中",
};

export default function LeadPublicSeaPoolPage() {
  const { isDesktop, useTableLayout } = useInboxTableLayout();
  const { inboxLayoutMode, setInboxLayoutMode } = useTheme();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [pageSize, setPageSize] = useState(20);
  const pageRef = useRef(1);
  const [items, setItems] = useState<LeadPublicSeaPoolItem[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [filterProfile, setFilterProfile] = useState<LeadInboxFilterProfile>({
    groups: [],
  });
  const [relationScope, setRelationScope] = useState<"owned" | "following">();
  const [inboxStage, setInboxStage] = useState<string>();
  const [keyword, setKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<LeadPublicSeaPoolItem>();
  const [detail, setDetail] = useState<ManagedLead>();
  const [detailError, setDetailError] = useState("");
  const [assignOpen, setAssignOpen] = useState(false);
  const [exitOpen, setExitOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferReason, setTransferReason] = useState("");
  const [candidates, setCandidates] = useState<
    Array<{ id: number; nickname: string }>
  >([]);
  const [candidateId, setCandidateId] = useState<number>();
  const [exitReason, setExitReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const selectedCycleRef = useRef<number | undefined>(undefined);
  const listRequestRef = useRef(0);
  const detailRequestRef = useRef(0);
  const publicSeaPoolGroup = useMemo(
    () =>
      filterProfile.groups.find((group) => group.key === "all") ||
      filterProfile.groups[0],
    [filterProfile.groups],
  );
  const publicSeaPoolOptions = useMemo(
    () => publicSeaPoolGroup?.sections.flatMap((section) => section.options) || [],
    [publicSeaPoolGroup],
  );

  const loadDetail = useCallback(async (item?: LeadPublicSeaPoolItem) => {
    const requestId = ++detailRequestRef.current;
    setDetail(undefined);
    setDetailError("");
    if (!item) return;
    try {
      const nextDetail = await api.managedLead(item.leadId);
      if (requestId === detailRequestRef.current) setDetail(nextDetail);
    } catch (detailError) {
      if (requestId === detailRequestRef.current) {
        setDetail(undefined);
        setDetailError(detailError instanceof Error ? detailError.message : "客资详情加载失败");
      }
    }
  }, []);

  const load = useCallback(
    async (overrides?: { keyword?: string; pageNo?: number; append?: boolean }) => {
      const requestId = ++listRequestRef.current;
      const nextKeyword = overrides?.keyword ?? keyword;
      const nextPageNo = overrides?.pageNo ?? 1;
      setLoading(true);
      setError("");
      try {
        const [page, nextCounts, nextProfile] = await Promise.all([
          api.publicSeaPoolPage({
            pageNo: nextPageNo,
            pageSize,
            keyword: nextKeyword || undefined,
            inboxGroup: publicSeaPoolGroup?.key || "all",
            inboxStage: inboxStage || "all",
            relationScope,
            advancedFilter,
          }),
          api.publicSeaPoolCounts(),
          api.publicSeaPoolFilterProfile(),
        ]);
        if (requestId !== listRequestRef.current) return;
        setItems(current => overrides?.append
          ? [...current, ...(page.list || []).filter(row => !current.some(item => item.cycleId === row.cycleId))]
          : page.list || []);
        setTotal(page.total || 0);
        setPageNo(nextPageNo);
        pageRef.current = nextPageNo;
        setCounts(nextCounts);
        setFilterProfile(nextProfile);
        if (!overrides?.append) {
          if (useTableLayout && nextPageNo > 1 && !page.list.length && page.total > 0) {
            void load({ pageNo: Math.max(1, Math.ceil(page.total / pageSize)) });
            return;
          }
          const nextSelected = page.list.find((item) => item.cycleId === selectedCycleRef.current) || page.list[0];
          setSelected(nextSelected);
          selectedCycleRef.current = nextSelected?.cycleId;
          await loadDetail(nextSelected);
        }
      } catch (loadError) {
        if (requestId === listRequestRef.current) {
          if (isLeadInboxUnauthorized(loadError instanceof Error ? loadError.message : "")) {
            setItems([]);
            setSelected(undefined);
            setDetail(undefined);
            setDrawerOpen(false);
            ++detailRequestRef.current;
          }
          setError(
            loadError instanceof Error ? loadError.message : "公海池加载失败",
          );
        }
      } finally {
        if (requestId === listRequestRef.current) setLoading(false);
      }
    },
    [pageSize, useTableLayout, advancedFilter, publicSeaPoolGroup?.key, inboxStage, keyword, loadDetail, relationScope],
  );

  useEffect(() => {
    void load();
  }, [load]);
  const selectItem = async (item: LeadPublicSeaPoolItem) => {
    setDrawerOpen(true);
    setSelected(item);
    selectedCycleRef.current = item.cycleId;
    await loadDetail(item);
  };
  const openAssign = async (item = selected) => {
    if (!item) return;
    void selectItem(item);
    try {
      setCandidates(await api.publicSeaPoolCandidates(item.cycleId));
      setCandidateId(item.collaboratorUserId);
      setAssignOpen(true);
    } catch (candidateError) {
      message.error(
        candidateError instanceof Error
          ? candidateError.message
          : "候选销售加载失败",
      );
    }
  };
  const assign = async () => {
    if (!selected || !candidateId) {
      message.warning("请选择协同销售");
      return;
    }
    setSaving(true);
    try {
      await api.assignPublicSeaPool(selected.cycleId, candidateId);
      message.success("协同销售已更新");
      setAssignOpen(false);
      await load({ pageNo: useTableLayout ? pageRef.current : 1 });
    } catch (assignError) {
      message.error(
        assignError instanceof Error ? assignError.message : "协同销售更新失败",
      );
    } finally {
      setSaving(false);
    }
  };
  const exit = async () => {
    if (!selected || !exitReason.trim()) {
      message.warning("请填写退出原因");
      return;
    }
    setSaving(true);
    try {
      await api.exitPublicSeaPool(selected.cycleId, exitReason.trim());
      message.success("商机已退出公海池");
      setExitOpen(false);
      setExitReason("");
      await load({ pageNo: useTableLayout ? pageRef.current : 1 });
    } catch (exitError) {
      message.error(
        exitError instanceof Error ? exitError.message : "退出公海池失败",
      );
    } finally {
      setSaving(false);
    }
  };

  const requestTransfer = async () => {
    if (!selected || !transferReason.trim()) return;
    setSaving(true);
    try {
      await api.requestPublicSeaPoolTransfer(selected.cycleId, transferReason.trim());
      message.success("正式转派申请已提交主管审批");
      setTransferOpen(false);
      setTransferReason("");
      await load({ pageNo: useTableLayout ? pageRef.current : 1 });
    } catch (transferError) {
      message.error(transferError instanceof Error ? transferError.message : "转派申请失败");
    } finally {
      setSaving(false);
    }
  };

  const detailContent = (<>
          {detailError ? <Alert type={isLeadInboxUnauthorized(detailError) ? "warning" : "error"} showIcon
              message={isLeadInboxUnauthorized(detailError) ? "无权查看该客资" : "客资详情加载失败"}
              description={detailError}
              action={!isLeadInboxUnauthorized(detailError) ? <Button size="small" onClick={() => void loadDetail(selected)}>重试</Button> : undefined}/>
            : !selected ? <Empty description="选择一条客资查看详情" />
            : !detail ? <Skeleton active paragraph={{ rows: 10 }} />
            : <><div className="public-sea-pool-business-bar"><div><Typography.Text strong>公海协作</Typography.Text><Typography.Text type="secondary">A：{selected.originalOwnerUserName || `#${selected.originalOwnerUserId}`} · B：{selected.collaboratorUserName || "待指派"} · 到期 {formatTimestamp(selected.dueAt)}</Typography.Text></div></div>
              <LeadDetail lead={detail} categories={[]} categoryLabel={value => value || "未记录"} channelLabel={value => value || "未记录"}
                mode="owner" autoExpandFollowUp={false} onDirtyChange={() => undefined} onChanged={() => void load({ pageNo: useTableLayout ? pageRef.current : 1 })}
                contextToolbarActions={[
                  selected.availableActions.includes("ASSIGN") && { key: "public-sea-pool-assign", icon: <UserSwitchOutlined/>, label: selected.collaboratorUserId ? "换派B" : "指派B", onClick: () => void openAssign() },
                  selected.availableActions.includes("EXIT") && { key: "public-sea-pool-exit", icon: <ExportOutlined/>, label: "退出公海", danger: true, onClick: () => setExitOpen(true) },
                  selected.availableActions.includes("REQUEST_TRANSFER") && { key: "public-sea-pool-transfer-request", icon: <MessageOutlined/>, label: "申请转给我", onClick: () => setTransferOpen(true) },
                ].filter(Boolean) as ToolbarAction[]}/></>}</>);
  const columns: ProColumns<LeadPublicSeaPoolItem>[] = [
    { title: "客资编号", dataIndex: "leadNo", width: 180, render: (_, row) => <Button type="link" onClick={() => void selectItem(row)}>{row.leadNo || "未记录"}</Button> },
    { title: "姓名", dataIndex: "submittedName", width: 140 },
    { title: "手机号", dataIndex: "submittedMobile", width: 150 },
    { title: "微信号", dataIndex: "submittedWechatId", width: 160 },
    { title: "公海状态", dataIndex: "status", width: 130, render: (_, row) => <Tag>{statusLabel[row.status] || row.status}</Tag> },
    { title: "原负责人 A", dataIndex: "originalOwnerUserName", width: 140 },
    { title: "协同销售 B", dataIndex: "collaboratorUserName", width: 140, render: (_, row) => row.collaboratorUserName || "待指派" },
    { title: "归属部门", dataIndex: "frozenDeptName", width: 160 },
    { title: "客资分类（记录值）", dataIndex: "leadCategory", width: 160 },
    { title: "来源渠道（记录值）", dataIndex: "sourceChannel", width: 160 },
    { title: "成交订单状态", dataIndex: "activeSalesOrderStatus", width: 150, render: (_, row) => row.activeSalesOrderStatus === "pending_approval" ? "审批中" : row.activeSalesOrderStatus === "revision_required" ? "待修订" : "-" },
    { title: "公海轮次", dataIndex: "cycleNo", width: 100 },
    ...([['ownershipStartedAt', '归属开始时间'], ['enteredAt', '进入公海时间'], ['assignedAt', '协同指派时间'], ['dueAt', '到期时间'], ['lastFollowUpAt', '最近跟进时间'], ['nextFollowUpAt', '下次跟进时间']] as const).map(([key, title]) => ({ title, dataIndex: key, width: 180, render: (_: unknown, row: LeadPublicSeaPoolItem) => <DateTimeText value={row[key]}/> })),
    { title: "操作", key: "action", valueType: "option", width: 330, render: (_, row) => <Space>
      <Button type="link" onClick={() => void selectItem(row)}>查看</Button>
      {row.availableActions.includes("ASSIGN") && <Button type="link" onClick={() => void openAssign(row)}>{row.collaboratorUserId ? "换派B" : "指派B"}</Button>}
      {row.availableActions.includes("EXIT") && <Button type="link" danger onClick={() => { void selectItem(row); setExitReason(""); setExitOpen(true); }}>退出公海</Button>}
      {row.availableActions.includes("REQUEST_TRANSFER") && <Button type="link" onClick={() => { void selectItem(row); setTransferReason(""); setTransferOpen(true); }}>申请转给我</Button>}
    </Space> },
  ];

  return (
    <section className="workspace-page public-sea-pool-page">
      <header className="lead-simple-status-shell" role="group" aria-label="公海池常驻筛选">
        <button type="button" className={!inboxStage && !relationScope ? "active" : ""}
          aria-pressed={!inboxStage && !relationScope}
          onClick={() => { setPageNo(1); setInboxStage(undefined); setRelationScope(undefined) }}>
          全部 {counts.all || 0}
        </button>
        <button type="button" className={relationScope === "owned" ? "active" : ""}
          aria-pressed={relationScope === "owned"}
          onClick={() => { setPageNo(1); setInboxStage(undefined); setRelationScope("owned") }}>
          归属我的
        </button>
        <button type="button" className={relationScope === "following" ? "active" : ""}
          aria-pressed={relationScope === "following"}
          onClick={() => { setPageNo(1); setInboxStage(undefined); setRelationScope("following") }}>
          我跟进的
        </button>
        {publicSeaPoolOptions.filter(option => option.key !== "all").map(option => (
          <button type="button" key={option.key} className={inboxStage === option.key ? "active" : ""}
            aria-pressed={inboxStage === option.key}
            onClick={() => { setPageNo(1); setRelationScope(undefined); setInboxStage(option.key) }}>
            {option.label}
          </button>
        ))}
      </header>
      <div className="public-sea-pool-toolbar">
        <Space wrap>
        {isDesktop && <Segmented aria-label="公海池视图" options={INBOX_LAYOUT_OPTIONS} value={inboxLayoutMode} onChange={value => { setDrawerOpen(false); setInboxLayoutMode(value); }}/>}
        <Button onClick={() => void load({ pageNo: useTableLayout ? pageNo : 1 })}>刷新</Button>
        <Button onClick={() => { setKeyword(""); setAdvancedFilter(undefined); setInboxStage(undefined); setRelationScope(undefined); }}>重置筛选</Button>
        </Space>
        <AdvancedFilterToolbar scene="lead" pageKey="lead_public_sea_pool" placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={(value) => { setKeyword(value); setPageNo(1) }} onChange={setAdvancedFilter}/>
      </div>
      {error && !useTableLayout && (
        <Alert
          type={isLeadInboxUnauthorized(error) ? "warning" : "error"}
          showIcon
          message={isLeadInboxUnauthorized(error) ? "无权查看公海池" : "公海池加载失败"}
          description={error}
          action={!isLeadInboxUnauthorized(error) ? (
            <Button size="small" onClick={() => void load()}>
              重试
            </Button>
          ) : undefined}
        />
      )}
      {useTableLayout ? <BusinessTable<LeadPublicSeaPoolItem>
        tableKey="lead-public-sea-pool" rowKey="cycleId" columns={columns}
        dataSource={items} loading={loading} error={error} unauthorized={isLeadInboxUnauthorized(error)}
        onReload={() => void load({ pageNo })}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, onChange: (page, size) => { if (size !== pageSize) setPageSize(size); else void load({ pageNo: page }); } }}
      /> : <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar"><Typography.Text type="secondary">共 {total} 条</Typography.Text></div>
          <div className="lead-inbox-scroll">
            {loading && items.length === 0 ? <Skeleton active paragraph={{ rows: 8 }} /> : items.length === 0
              ? <Empty description="暂无公海商机" />
              : items.map(item => <button type="button" key={item.cycleId}
                  className={`lead-inbox-item${selected?.cycleId === item.cycleId ? " active" : ""}`}
                  onClick={() => void selectItem(item)}>
                  <div className="lead-inbox-item-main"><NameAvatar name={item.submittedName} seed={item.leadNo} size={36}/>
                    <div className="lead-inbox-item-copy"><div className="lead-inbox-item-title"><strong>{item.submittedName}</strong><Tag>{statusLabel[item.status]}</Tag></div>
                      <span>{item.leadNo}</span><span>{item.submittedMobile || "无手机号"} · {item.submittedWechatId || "无微信号"}</span>
                      <span>A：{item.originalOwnerUserName || `#${item.originalOwnerUserId}`} · B：{item.collaboratorUserName || "待指派"}</span>
                      <span>进入 {formatTimestamp(item.enteredAt)} · 到期 {formatTimestamp(item.dueAt)}</span>
                    </div>
                  </div>
                </button>)}
            {items.length < total && <div className="public-sea-pool-load-more"><Button loading={loading} onClick={() => void load({ pageNo: pageNo + 1, append: true })}>加载更多</Button></div>}
          </div>
        </aside>
        <main className="lead-inbox-detail-pane">
          {detailContent}
        </main>
      </div>}
      <ResizableDetailDrawer title={selected?.submittedName || "客资详情"} open={drawerOpen && (useTableLayout || !isDesktop)} onClose={() => setDrawerOpen(false)} desktopResizable={isDesktop} width={isDesktop ? undefined : "100%"}>
        {detailContent}
      </ResizableDetailDrawer>
      <Modal
        title={selected?.collaboratorUserId ? "更换协同销售B" : "指派协同销售B"}
        open={assignOpen}
        confirmLoading={saving}
        onOk={() => void assign()}
        onCancel={() => setAssignOpen(false)}
      >
        <Form.Item label="协同销售" required><Select
          style={{ width: "100%" }}
          value={candidateId}
          onChange={setCandidateId}
          placeholder="选择同部门启用销售"
          options={candidates.map((item) => ({
            value: item.id,
            label: item.nickname,
          }))}
        /></Form.Item>
      </Modal>
      <Modal
        title="退出公海池"
        open={exitOpen}
        confirmLoading={saving}
        onOk={() => void exit()}
        onCancel={() => setExitOpen(false)}
        okButtonProps={{ danger: true }}
      >
        <Form.Item label="退出原因" required><Input.TextArea
          rows={4}
          maxLength={500}
          showCount
          value={exitReason}
          onChange={(event) => setExitReason(event.target.value)}
          placeholder="填写退出原因；退出后A重新获得独占推进权，并从当前时间重新计时"
        /></Form.Item>
      </Modal>
      <Modal
        title="申请正式转派给我"
        open={transferOpen}
        confirmLoading={saving}
        onOk={() => void requestTransfer()}
        onCancel={() => setTransferOpen(false)}
      >
        <Form.Item label="申请原因" required><Input.TextArea
          rows={4}
          maxLength={500}
          showCount
          value={transferReason}
          onChange={(event) => setTransferReason(event.target.value)}
          placeholder="填写申请原因；审批通过后正式归属和业绩归属将转给你，并退出公海池"
        /></Form.Item>
      </Modal>
    </section>
  );
}
