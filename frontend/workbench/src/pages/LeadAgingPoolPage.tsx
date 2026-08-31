import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
  Space,
  Tag,
  Typography,
  message,
} from "antd";
import { UserSwitchOutlined } from "@ant-design/icons";
import {
  api,
  type LeadAgingPoolItem,
  type LeadAgingPoolStatus,
  type LeadInboxFilterProfile,
  type AdvancedFilterGroup,
  type ManagedLead,
} from "../services/api";
import LeadDetail from "../components/LeadDetail";
import { NameAvatar } from "../components/LeadDetailOverview";
import { formatTimestamp } from "../services/time";
import { AdvancedFilterToolbar, filterCount } from "../components/AdvancedFilter";
import { isLeadInboxUnauthorized } from "../services/leadManagement";

const statusLabel: Record<LeadAgingPoolStatus, string> = {
  waiting_assignment: "待指派",
  assigned: "协同跟进中",
  deal_pending: "成交审批中",
};

export default function LeadAgingPoolPage() {
  const [items, setItems] = useState<LeadAgingPoolItem[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [filterProfile, setFilterProfile] = useState<LeadInboxFilterProfile>({
    groups: [],
  });
  const [inboxStage, setInboxStage] = useState<string>();
  const [keyword, setKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<LeadAgingPoolItem>();
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
  const agingPoolGroup = useMemo(
    () =>
      filterProfile.groups.find((group) => group.key === "all") ||
      filterProfile.groups[0],
    [filterProfile.groups],
  );
  const agingPoolOptions = useMemo(
    () => agingPoolGroup?.sections.flatMap((section) => section.options) || [],
    [agingPoolGroup],
  );

  const loadDetail = useCallback(async (item?: LeadAgingPoolItem) => {
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
          api.agingPoolPage({
            pageNo: nextPageNo,
            pageSize: 20,
            keyword: nextKeyword || undefined,
            inboxGroup: agingPoolGroup?.key || "all",
            inboxStage: inboxStage || "all",
            advancedFilter,
          }),
          api.agingPoolCounts(),
          api.agingPoolFilterProfile(),
        ]);
        if (requestId !== listRequestRef.current) return;
        setItems(current => overrides?.append
          ? [...current, ...(page.list || []).filter(row => !current.some(item => item.cycleId === row.cycleId))]
          : page.list || []);
        setTotal(page.total || 0);
        setPageNo(nextPageNo);
        setCounts(nextCounts);
        setFilterProfile(nextProfile);
        if (!overrides?.append) {
          const nextSelected = page.list.find((item) => item.cycleId === selectedCycleRef.current) || page.list[0];
          setSelected(nextSelected);
          selectedCycleRef.current = nextSelected?.cycleId;
          await loadDetail(nextSelected);
        }
      } catch (loadError) {
        if (requestId === listRequestRef.current) {
          setError(
            loadError instanceof Error ? loadError.message : "公海池加载失败",
          );
        }
      } finally {
        if (requestId === listRequestRef.current) setLoading(false);
      }
    },
    [advancedFilter, agingPoolGroup?.key, inboxStage, keyword, loadDetail],
  );

  useEffect(() => {
    void load();
  }, [load]);
  const selectItem = async (item: LeadAgingPoolItem) => {
    setSelected(item);
    selectedCycleRef.current = item.cycleId;
    await loadDetail(item);
  };
  const openAssign = async () => {
    if (!selected) return;
    try {
      setCandidates(await api.agingPoolCandidates(selected.cycleId));
      setCandidateId(selected.collaboratorUserId);
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
      await api.assignAgingPool(selected.cycleId, candidateId);
      message.success("协同销售已更新");
      setAssignOpen(false);
      await load();
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
      await api.exitAgingPool(selected.cycleId, exitReason.trim());
      message.success("商机已退出公海池");
      setExitOpen(false);
      setExitReason("");
      await load();
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
      await api.requestAgingPoolTransfer(selected.cycleId, transferReason.trim());
      message.success("正式转派申请已提交主管审批");
      setTransferOpen(false);
      setTransferReason("");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="workspace-page aging-pool-page">
      <div className="aging-pool-toolbar">
        {filterCount(advancedFilter) === 0 && <Space wrap>
          <Button
            type={!inboxStage ? "primary" : "default"}
            onClick={() => {
              setPageNo(1);
              setInboxStage(undefined);
            }}
          >
            全部 {counts.all || 0}
          </Button>
          {agingPoolOptions
            .filter((option) => option.key !== "all")
            .map((option) => {
              return (
                <Button
                  key={option.key}
                  type={inboxStage === option.key ? "primary" : "default"}
                  onClick={() => {
                    setPageNo(1);
                    setInboxStage(option.key);
                  }}
                >
                  {option.label}
                </Button>
              );
            })}
        </Space>}
        <Space>
          <AdvancedFilterToolbar scene="lead" pageKey="lead_aging_pool" placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={(value) => { setKeyword(value); setPageNo(1) }} onChange={setAdvancedFilter}/>
        </Space>
      </div>
      {error && (
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
      <div className="lead-inbox-layout">
        <aside className="lead-inbox-list-pane">
          <div className="lead-inbox-toolbar"><Typography.Text type="secondary">共 {total} 条</Typography.Text></div>
          <div className="lead-inbox-scroll">
            {loading && items.length === 0 ? <Skeleton active paragraph={{ rows: 8 }} /> : items.length === 0
              ? <Empty description="暂无公海商机" />
              : items.map(item => <button type="button" key={item.cycleId}
                  className={`lead-inbox-item${selected?.cycleId === item.cycleId ? " active" : ""}`}
                  onClick={() => void selectItem(item)}>
                  <div className="lead-inbox-item-main"><NameAvatar name={item.submittedName} size={36}/>
                    <div className="lead-inbox-item-copy"><div className="lead-inbox-item-title"><strong>{item.submittedName}</strong><Tag>{statusLabel[item.status]}</Tag></div>
                      <span>{item.leadNo}</span><span>{item.submittedMobile || "无手机号"} · {item.submittedWechatId || "无微信号"}</span>
                      <span>A：{item.originalOwnerUserName || `#${item.originalOwnerUserId}`} · B：{item.collaboratorUserName || "待指派"}</span>
                      <span>进入 {formatTimestamp(item.enteredAt)} · 到期 {formatTimestamp(item.dueAt)}</span>
                    </div>
                  </div>
                </button>)}
            {items.length < total && <div className="aging-pool-load-more"><Button loading={loading} onClick={() => void load({ pageNo: pageNo + 1, append: true })}>加载更多</Button></div>}
          </div>
        </aside>
        <main className="lead-inbox-detail-pane">
          {detailError ? <Alert type={isLeadInboxUnauthorized(detailError) ? "warning" : "error"} showIcon
              message={isLeadInboxUnauthorized(detailError) ? "无权查看该客资" : "客资详情加载失败"}
              description={detailError}
              action={!isLeadInboxUnauthorized(detailError) ? <Button size="small" onClick={() => void loadDetail(selected)}>重试</Button> : undefined}/>
            : !selected ? <Empty description="选择一条客资查看详情" />
            : !detail ? <Skeleton active paragraph={{ rows: 10 }} />
            : <><div className="aging-pool-business-bar"><div><Typography.Text strong>公海协作</Typography.Text><Typography.Text type="secondary">A：{selected.originalOwnerUserName || `#${selected.originalOwnerUserId}`} · B：{selected.collaboratorUserName || "待指派"} · 到期 {formatTimestamp(selected.dueAt)}</Typography.Text></div><Space wrap>
                {selected.availableActions.includes("ASSIGN") && <Button icon={<UserSwitchOutlined/>} onClick={() => void openAssign()}>{selected.collaboratorUserId ? "换派B" : "指派B"}</Button>}
                {selected.availableActions.includes("EXIT") && <Button danger onClick={() => setExitOpen(true)}>退出公海</Button>}
                {selected.availableActions.includes("REQUEST_TRANSFER") && <Button icon={<UserSwitchOutlined/>} onClick={() => setTransferOpen(true)}>申请转给我</Button>}
              </Space></div>
              <LeadDetail lead={detail} categories={[]} categoryLabel={value => value || "未记录"} channelLabel={value => value || "未记录"}
                mode="owner" autoExpandFollowUp={false} onDirtyChange={() => undefined} onChanged={() => void load()}/></>}
        </main>
      </div>
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
