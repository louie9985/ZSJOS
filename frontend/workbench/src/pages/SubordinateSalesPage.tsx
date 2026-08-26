import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popover,
  Select,
  Skeleton,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from "antd";
import type { ColumnsType, TableRowSelection } from "antd/es/table/interface";
import {
  ArrowLeftOutlined,
  PoweroffOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import EmployeeAvatar from "../components/EmployeeAvatar";
import EmployeeSelect from "../components/EmployeeSelect";
import {
  api,
  type AssignmentUser,
  type AdvancedFilterGroup,
  type BusinessTaskBucket,
  type DictData,
  type ManagedLead,
  type SubordinateBatchResult,
  type SubordinateSales,
  type SubordinateTask,
} from "../services/api";
import { AdvancedFilterToolbar } from "../components/AdvancedFilter";
import LeadDetail from "../components/LeadDetail";
import { DICT_TYPE, LEAD_STATUS_LABELS } from "../constants";
import { dictionaryDisplayLabel } from "../services/leadManagement";
import { formatTimestamp } from "../services/time";
import {
  formatCurrency,
  appendSubordinateSalesRows,
  receiveStatusLabel,
  summarizeBatchResult,
  todayStatusLabel,
} from "../services/subordinateSales";

const PAGE_SIZE = 20;
type ReasonAction =
  | { type: "account"; sales: SubordinateSales; value: boolean }
  | { type: "dispatch"; sales: SubordinateSales; value: boolean };
type BatchAction = "transfer" | "restore" | "recycle" | "claimPool" | "publicSea";

function Metrics({ sales }: { sales: SubordinateSales }) {
  const metrics = [
    ["今日待跟进", sales.todayPendingCount],
    ["首跟超时", sales.firstFollowTimeoutCount],
    ["挂起客资", sales.suspendedLeadCount],
    ["有效客资", sales.validLeadCount],
    ["成交客资", sales.convertedLeadCount],
    ["成交订单", sales.effectiveOrderCount],
  ] as const;
  return (
    <div className="subordinate-metrics">
      {metrics.map(([label, value]) => (
        <div key={label} className="subordinate-metric-card">
          <Statistic title={label} value={value} />
        </div>
      ))}
      <div className="subordinate-metric-card">
        <Statistic
          title="成交金额"
          value={sales.effectiveOrderAmount}
          formatter={() => formatCurrency(sales.effectiveOrderAmount)}
        />
      </div>
    </div>
  );
}

function CategoryCounts({ sales }: { sales: SubordinateSales }) {
  return (
    <Popover
      title="客资分类"
      content={
        <List
          size="small"
          dataSource={sales.categoryCounts}
          renderItem={(item) => (
            <List.Item>
              <span>{item.label}</span>
              <Typography.Text strong>{item.count}</Typography.Text>
            </List.Item>
          )}
        />
      }
    >
      <Button type="link" size="small">
        {sales.categoryCounts.reduce((sum, item) => sum + item.count, 0)} 条
      </Button>
    </Popover>
  );
}

function BatchResultModal({
  result,
  open,
  onClose,
}: {
  result?: SubordinateBatchResult;
  open: boolean;
  onClose: () => void;
}) {
  return (
    <Modal
      open={open}
      title={result ? summarizeBatchResult(result) : "批量结果"}
      footer={<Button onClick={onClose}>关闭</Button>}
      onCancel={onClose}
    >
      <List
        size="small"
        dataSource={result?.items || []}
        renderItem={(item) => (
          <List.Item>
            <Space>
              <Tag color={item.success ? "success" : "error"}>
                {item.success ? "成功" : "失败"}
              </Tag>
              <span>{item.leadNo || '编号不可用'}</span>
              <Typography.Text type={item.success ? undefined : "danger"}>
                {item.message}
              </Typography.Text>
            </Space>
          </List.Item>
        )}
      />
    </Modal>
  );
}

function ManagedLeadDetail({ leadId, onBack }: { leadId: number; onBack: () => void }) {
  const [lead, setLead] = useState<ManagedLead>()
  const [categories, setCategories] = useState<DictData[]>([])
  const [channels, setChannels] = useState<DictData[]>([])
  const [categoryError, setCategoryError] = useState(false)
  const [channelError, setChannelError] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError(''); setCategoryError(false); setChannelError(false)
    const results = await Promise.allSettled([
      api.managedLead(leadId),
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)
    ])
    if (results[0].status === 'fulfilled') setLead(results[0].value)
    else {
      setLead(undefined)
      setError(results[0].reason instanceof Error ? results[0].reason.message : '客资详情加载失败')
    }
    if (results[1].status === 'fulfilled') setCategories(results[1].value)
    else { setCategories([]); setCategoryError(true) }
    if (results[2].status === 'fulfilled') setChannels(results[2].value)
    else { setChannels([]); setChannelError(true) }
    setLoading(false)
  }, [leadId])
  useEffect(() => { void load() }, [load])

  return <div className="subordinate-lead-detail">
    <div className="subordinate-detail-heading">
      <Button icon={<ArrowLeftOutlined/>} onClick={onBack}>返回销售详情</Button>
      <div><Typography.Title level={4}>{lead?.submittedName || '客资详情'}</Typography.Title><Typography.Text type="secondary">{lead?.leadNo}</Typography.Text></div>
    </div>
    {loading ? <Skeleton active paragraph={{ rows: 10 }}/>
      : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
        : lead ? <LeadDetail lead={lead} categories={categories}
          categoryLabel={value => dictionaryDisplayLabel(categories, value, categoryError)}
          channelLabel={value => dictionaryDisplayLabel(channels, value, channelError)}
          mode="manager-readonly" autoExpandFollowUp={false} onDirtyChange={() => undefined}
          onChanged={() => void load()}/>
          : <Empty description="客资详情不可用"/>}
  </div>
}

function SalesDetail({
  sales,
  permissions,
  onBack,
  onChanged,
  onReasonAction,
}: {
  sales: SubordinateSales;
  permissions: string[];
  onBack: () => void;
  onChanged: () => void;
  onReasonAction: (action: ReasonAction) => void;
}) {
  const [tab, setTab] = useState("overview");
  const [leads, setLeads] = useState<ManagedLead[]>([]);
  const [leadTotal, setLeadTotal] = useState(0);
  const [leadPage, setLeadPage] = useState(1);
  const [leadKeyword, setLeadKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [tasks, setTasks] = useState<SubordinateTask[]>([]);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskBucket, setTaskBucket] = useState<BusinessTaskBucket>("today");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchType, setBatchType] = useState<BatchAction>("transfer");
  const batchIdempotencyKey = useRef<string>();
  const [candidates, setCandidates] = useState<AssignmentUser[]>([]);
  const [batchSaving, setBatchSaving] = useState(false);
  const [batchResult, setBatchResult] = useState<SubordinateBatchResult>();
  const [resultOpen, setResultOpen] = useState(false);
  const [detailLeadId, setDetailLeadId] = useState<number>();
  const [form] = Form.useForm();

  const loadLeads = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const page = await api.subordinateSalesLeads(sales.userId, {
        pageNo: leadPage,
        pageSize: PAGE_SIZE,
        keyword: leadKeyword || undefined,
        advancedFilter,
      });
      setLeads(page.list);
      setLeadTotal(page.total);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "客资加载失败");
    } finally {
      setLoading(false);
    }
  }, [advancedFilter, leadKeyword, leadPage, sales.userId]);
  const loadTasks = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const page = await api.subordinateSalesTasks(sales.userId, {
        pageNo: 1,
        pageSize: 100,
        bucket: taskBucket,
      });
      setTasks(page.list);
      setTaskTotal(page.total);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "待办加载失败");
    } finally {
      setLoading(false);
    }
  }, [sales.userId, taskBucket]);
  useEffect(() => {
    setTab("overview");
    setSelected([]);
    setLeadPage(1);
    setError("");
  }, [sales?.userId]);
  useEffect(() => {
    if (tab === "leads") void loadLeads();
  }, [loadLeads, tab]);
  useEffect(() => {
    if (tab === "tasks") void loadTasks();
  }, [loadTasks, tab]);

  const openBatch = async (type: BatchAction) => {
    if (!selected.length) {
      message.warning("请先选择客资");
      return;
    }
    setBatchType(type);
    batchIdempotencyKey.current = crypto.randomUUID();
    form.resetFields();
    setBatchOpen(true);
    if (type !== "transfer" && type !== "publicSea") return;
    try {
      setCandidates(await api.subordinateTransferCandidates());
    } catch (loadError) {
      message.error(
        loadError instanceof Error ? loadError.message : "销售候选加载失败",
      );
    }
  };
  const submitBatch = async () => {
    const values = await form.validateFields();
    setBatchSaving(true);
    try {
      const ids = selected.map(Number);
      const action = ({ transfer: 'transfer', restore: 'restore', recycle: 'recycle',
        claimPool: 'release-claim-pool', publicSea: 'release-public-sea' } as const)[batchType]
      const result = await api.batchSupervisorLeadAction(action, ids, {
        reason: values.reason.trim(),
        targetUserId: values.targetUserId,
        collaboratorUserId: values.collaboratorUserId,
        idempotencyKey: batchIdempotencyKey.current ||= crypto.randomUUID(),
      });
      setBatchResult(result);
      setResultOpen(true);
      setBatchOpen(false);
      setSelected([]);
      batchIdempotencyKey.current = undefined;
      await loadLeads();
      onChanged();
    } catch (saveError) {
      message.error(
        saveError instanceof Error ? saveError.message : "批量操作失败",
      );
    } finally {
      setBatchSaving(false);
    }
  };
  const leadColumns: ColumnsType<ManagedLead> = [
    {
      title: "姓名",
      dataIndex: "submittedName",
      fixed: "left",
      width: 120,
      render: (value, row) => (
        <Button type="link" onClick={() => setDetailLeadId(row.id)}>
          {value}
        </Button>
      ),
    },
    {
      title: "手机号",
      dataIndex: "submittedMobile",
      width: 130,
      render: (value) => value || "-",
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      render: (value) => <Tag>{LEAD_STATUS_LABELS[value] || value}</Tag>,
    },
    {
      title: "分类",
      dataIndex: "leadCategory",
      width: 120,
      render: (value) => value || "未配置",
    },
    {
      title: "来源",
      dataIndex: "sourceChannel",
      width: 120,
      render: (value) => value || "-",
    },
    {
      title: "提交时间",
      dataIndex: "submittedAt",
      width: 170,
      render: (value) => formatTimestamp(value),
    },
  ];
  const taskColumns: ColumnsType<SubordinateTask> = [
    {
      title: "客资",
      dataIndex: "leadName",
      render: (value, row) => `${row.leadNo} · ${value || '未命名客资'}`,
    },
    { title: "任务类型", dataIndex: "taskType", width: 180 },
    {
      title: "截止时间",
      dataIndex: "dueAt",
      width: 180,
      render: (value) => formatTimestamp(value, "未排期"),
    },
    {
      title: "状态",
      width: 90,
      render: (_, row) =>
        row.overdue ? (
          <Tag color="error">逾期</Tag>
        ) : (
          <Tag color="processing">待处理</Tag>
        ),
    },
  ];
  const rowSelection: TableRowSelection<ManagedLead> = {
    selectedRowKeys: selected,
    onChange: setSelected,
    preserveSelectedRowKeys: true,
  };
  if (detailLeadId)
    return (
      <ManagedLeadDetail
        leadId={detailLeadId}
        onBack={() => setDetailLeadId(undefined)}
      />
    );
  return (
    <div className="subordinate-sales-detail">
      <div className="subordinate-detail-hero">
        <Button
          className="subordinate-mobile-back"
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
        >
          返回列表
        </Button>
        <EmployeeAvatar avatar={sales.avatar} name={sales.name} size={44} />
        <div className="subordinate-detail-title">
          <Typography.Title level={4}>{sales.name}</Typography.Title>
          <Typography.Text type="secondary">
            {sales.username} · {sales.mobile || "未填写手机号"}
          </Typography.Text>
        </div>
        <Space className="subordinate-detail-controls" wrap>
          <span>账号</span>
          <Switch
            checkedChildren="启用"
            unCheckedChildren="停用"
            checked={sales.accountStatus === 0}
            disabled={
              !permissions.includes("zsjos:subordinate-sales:account-status")
            }
            onChange={(value) =>
              onReasonAction({ type: "account", sales, value })
            }
          />
          <span>接单</span>
          <Switch
            checkedChildren="开启"
            unCheckedChildren="关闭"
            checked={sales.accepting}
            disabled={
              !permissions.includes("zsjos:subordinate-sales:dispatch-mode")
            }
            onChange={(value) =>
              onReasonAction({ type: "dispatch", sales, value })
            }
          />
        </Space>
      </div>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: "overview",
            label: "概览",
            children: (
              <div className="subordinate-overview-grid">
                <div className="subordinate-overview-main">
                  <section className="subordinate-overview-card">
                    <div className="subordinate-overview-card-head">基本信息</div>
                    <div className="subordinate-profile-fields">
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">手机号</span>
                        <span className={`subordinate-field-value${sales.mobile ? '' : ' subordinate-field-empty'}`}>{sales.mobile || '-'}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">账号状态</span>
                        <span className="subordinate-field-value">
                          <Tag color={sales.accountStatus === 0 ? 'success' : 'default'} style={{ margin: 0 }}>
                            {sales.accountStatus === 0 ? '启用' : '停用'}
                          </Tag>
                        </span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">页面状态</span>
                        <span className="subordinate-field-value">{sales.presence === 'online' ? '在线' : '离线'}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">接单状态</span>
                        <span className="subordinate-field-value">{sales.accepting ? '开启' : '关闭'}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">新客资</span>
                        <span className="subordinate-field-value">{receiveStatusLabel(sales)}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">新手池</span>
                        <span className="subordinate-field-value subordinate-field-empty">暂未开放</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">今日跟进状态</span>
                        <span className="subordinate-field-value">{todayStatusLabel(sales.todayFollowUpStatus)}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">客资分类</span>
                        <span className="subordinate-field-value"><CategoryCounts sales={sales}/></span>
                      </div>
                    </div>
                  </section>
                </div>
                <aside className="subordinate-overview-aside">
                  <section className="subordinate-overview-card">
                    <div className="subordinate-overview-card-head">业绩指标</div>
                    <Metrics sales={sales} />
                  </section>
                </aside>
              </div>
            ),
          },
          {
            key: "leads",
            label: "名下客资",
            children: (
              <>
                <AdvancedFilterToolbar scene="lead" placeholder="搜索姓名 / 手机号 / 微信号" keyword={leadKeyword} value={advancedFilter} onKeyword={setLeadKeyword} onChange={setAdvancedFilter}/>
                <div className="subordinate-batch-bar">
                  <Typography.Text>已选 {selected.length} 条</Typography.Text>
                  <Space wrap>
                    <Button
                      disabled={
                        !selected.length ||
                        !permissions.includes(
                          "zsjos:subordinate-sales:lead-transfer",
                        )
                      }
                      onClick={() => void openBatch("transfer")}
                    >
                      转派
                    </Button>
                    <Button disabled={!selected.length || !permissions.includes("zsjos:subordinate-sales:lead-restore")}
                      onClick={() => void openBatch("restore")}>恢复</Button>
                    <Button disabled={!selected.length || !permissions.includes("zsjos:subordinate-sales:lead-recycle")}
                      danger onClick={() => void openBatch("recycle")}>回收</Button>
                    <Button disabled={!selected.length || !permissions.includes("zsjos:subordinate-sales:lead-release-claim-pool")}
                      danger onClick={() => void openBatch("claimPool")}>释放至抢单池</Button>
                    <Button
                      disabled={
                        !selected.length ||
                        !permissions.includes(
                          "zsjos:subordinate-sales:lead-release-public-sea",
                        )
                      }
                      danger
                      onClick={() => void openBatch("publicSea")}
                    >
                      释放至公海池
                    </Button>
                  </Space>
                </div>
                {error && (
                  <Alert
                    type="error"
                    showIcon
                    message={error}
                    action={
                      <Button size="small" onClick={() => void loadLeads()}>
                        重试
                      </Button>
                    }
                  />
                )}
                <Table
                  rowKey="id"
                  loading={loading}
                  rowSelection={rowSelection}
                  columns={leadColumns}
                  dataSource={leads}
                  scroll={{ x: 820 }}
                  pagination={{
                    current: leadPage,
                    pageSize: PAGE_SIZE,
                    total: leadTotal,
                    onChange: setLeadPage,
                  }}
                />
              </>
            ),
          },
          {
            key: "tasks",
            label: "待跟进任务",
            children: (
              <>
                <Select
                  value={taskBucket}
                  onChange={setTaskBucket}
                  style={{ width: 160, marginBottom: 12 }}
                  options={[
                    { value: "overdue", label: "逾期" },
                    { value: "today", label: "今日" },
                    { value: "future", label: "未来" },
                    { value: "unscheduled", label: "未排期" },
                  ]}
                />
                {error && (
                  <Alert
                    type="error"
                    showIcon
                    message={error}
                    action={
                      <Button size="small" onClick={() => void loadTasks()}>
                        重试
                      </Button>
                    }
                  />
                )}
                <Table
                  rowKey="id"
                  loading={loading}
                  columns={taskColumns}
                  dataSource={tasks}
                  pagination={false}
                  locale={{ emptyText: <Empty description="暂无待跟进任务" /> }}
                />
                {taskTotal > tasks.length && (
                  <Typography.Text type="secondary">
                    共 {taskTotal} 条，仅显示前 {tasks.length} 条
                  </Typography.Text>
                )}
              </>
            ),
          },
        ]}
      />
      <Modal
        open={batchOpen}
        title={({ transfer: "批量转派客资", restore: "批量恢复客资", recycle: "批量回收客资",
          claimPool: "批量释放至抢单池", publicSea: "批量释放至公海池" })[batchType]}
        confirmLoading={batchSaving}
        onOk={() => void submitBatch()}
        onCancel={() => setBatchOpen(false)}
      >
        <Form form={form} layout="vertical">
          {batchType === "transfer" ? (
            <Form.Item
              name="targetUserId"
              label="目标销售"
              rules={[{ required: true, message: "请选择目标销售" }]}
            >
              <EmployeeSelect
                showSearch
                optionFilterProp="label"
                users={candidates}
              />
            </Form.Item>
          ) : batchType === "publicSea" ? (
            <Form.Item name="collaboratorUserId" label="公海跟进销售（可不填）">
              <EmployeeSelect
                allowClear
                showSearch
                optionFilterProp="label"
                users={candidates}
              />
            </Form.Item>
          ) : null}
          <Form.Item
            name="reason"
            label="操作原因"
            rules={[
              { required: true, whitespace: true, message: "请填写操作原因" },
              { max: 500 },
            ]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <BatchResultModal
        open={resultOpen}
        result={batchResult}
        onClose={() => setResultOpen(false)}
      />
    </div>
  );
}

export default function SubordinateSalesPage({
  permissions,
}: {
  permissions: string[];
}) {
  const [rows, setRows] = useState<SubordinateSales[]>([]);
  const [total, setTotal] = useState(0);
  const [loadedPage, setLoadedPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>();
  const [presence, setPresence] = useState<string>();
  const [accountStatus, setAccountStatus] = useState<number>();
  const [accepting, setAccepting] = useState<boolean>();
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [loadMoreError, setLoadMoreError] = useState("");
  const [selectedSales, setSelectedSales] = useState<SubordinateSales>();
  const [reasonAction, setReasonAction] = useState<ReasonAction>();
  const [reasonSaving, setReasonSaving] = useState(false);
  const [pausingAll, setPausingAll] = useState(false);
  const [reasonForm] = Form.useForm();
  const listRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const requestVersionRef = useRef(0);
  const loadingMoreRef = useRef(false);

  const requestPage = useCallback((pageNo: number) => api.subordinateSalesPage({
    pageNo,
    pageSize: PAGE_SIZE,
    keyword: keyword.trim() || undefined,
    presence,
    accountStatus,
    accepting,
    advancedFilter,
  }), [accepting, accountStatus, advancedFilter, keyword, presence]);

  const loadFirstPage = useCallback(async (preserveSelection = false) => {
    const requestVersion = ++requestVersionRef.current;
    setLoading(true);
    setError("");
    setLoadMoreError("");
    try {
      const result = await requestPage(1);
      if (requestVersion !== requestVersionRef.current) return;
      setRows(result.list);
      setTotal(result.total);
      setLoadedPage(1);
      setSelectedSales(current => {
        if (!preserveSelection || !current) return preserveSelection ? current : undefined;
        return result.list.find(row => row.userId === current.userId) || result.list[0];
      });
    } catch (loadError) {
      if (requestVersion !== requestVersionRef.current) return;
      setError(
        loadError instanceof Error ? loadError.message : "下属销售加载失败",
      );
    } finally {
      if (requestVersion === requestVersionRef.current) setLoading(false);
    }
  }, [requestPage]);

  useEffect(() => {
    setRows([]);
    setTotal(0);
    setLoadedPage(0);
    setSelectedSales(undefined);
    listRef.current?.scrollTo({ top: 0 });
    void loadFirstPage();
  }, [loadFirstPage]);

  const loadMore = useCallback(async () => {
    if (loading || loadingMoreRef.current || rows.length >= total) return;
    loadingMoreRef.current = true;
    setLoadingMore(true);
    setLoadMoreError("");
    const requestVersion = requestVersionRef.current;
    const nextPage = loadedPage + 1;
    try {
      const result = await requestPage(nextPage);
      if (requestVersion !== requestVersionRef.current) return;
      setRows(current => appendSubordinateSalesRows(current, result.list));
      setTotal(result.total);
      setLoadedPage(nextPage);
    } catch (loadError) {
      if (requestVersion !== requestVersionRef.current) return;
      setLoadMoreError(loadError instanceof Error ? loadError.message : "更多下属销售加载失败");
    } finally {
      loadingMoreRef.current = false;
      if (requestVersion === requestVersionRef.current) setLoadingMore(false);
    }
  }, [loadedPage, loading, requestPage, rows.length, total]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    const root = listRef.current;
    if (!sentinel || !root || loading || loadMoreError || rows.length >= total) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadMore();
    }, { root, rootMargin: "240px 0px" });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [loadMore, loadMoreError, loading, rows.length, total]);
  const submitReasonAction = async () => {
    if (!reasonAction) return;
    const values = await reasonForm.validateFields();
    setReasonSaving(true);
    try {
      if (reasonAction.type === "account")
        await api.updateSubordinateAccountStatus(
          reasonAction.sales.userId,
          reasonAction.value ? 0 : 1,
          values.reason.trim(),
        );
      else
        await api.updateSubordinateDispatchMode(
          reasonAction.sales.userId,
          reasonAction.value,
          values.reason.trim(),
        );
      message.success("操作成功");
      setReasonAction(undefined);
      await loadFirstPage(true);
    } catch (saveError) {
      message.error(
        saveError instanceof Error ? saveError.message : "操作失败",
      );
    } finally {
      setReasonSaving(false);
    }
  };
  useEffect(() => {
    if (!selectedSales) return;
    const current = rows.find((row) => row.userId === selectedSales.userId);
    if (current) setSelectedSales(current);
  }, [rows]);
  const openReasonAction = (action: ReasonAction) => {
    reasonForm.resetFields();
    setReasonAction(action);
  };
  const confirmPauseAll = () => {
    Modal.confirm({
      title: "确认一键下班",
      content: "将把当前主管管理范围内的全部销售（包括停用账号）设置为暂停接单。现有客资、账号状态和页面在线状态不会改变。",
      okText: "全部暂停接单",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        setPausingAll(true);
        try {
          const result = await api.pauseAllSubordinateDispatch();
          message.success(`共 ${result.totalCount} 人，已暂停 ${result.changedCount} 人，原已暂停 ${result.alreadyPausedCount} 人`);
          await loadFirstPage(true);
        } catch (pauseError) {
          message.error(pauseError instanceof Error ? pauseError.message : "一键下班失败");
          throw pauseError;
        } finally {
          setPausingAll(false);
        }
      },
    });
  };
  return (
    <section className="workspace-page subordinate-sales-page">
      <div className="subordinate-toolbar">
        <Space wrap>
          <AdvancedFilterToolbar
            scene="subordinate_sales"
            placeholder="搜索姓名、账号或手机号"
            keyword={keyword}
            value={advancedFilter}
            onKeyword={(value) => {
              setKeyword(value);
            }}
            onChange={(value) => {
              setAdvancedFilter(value);
            }}
          />
          <Select
            allowClear
            placeholder="账号状态"
            value={accountStatus}
            onChange={(value) => {
              setAccountStatus(value);
            }}
            style={{ width: 130 }}
            options={[
              { value: 0, label: "启用" },
              { value: 1, label: "停用" },
            ]}
          />
          <Select
            allowClear
            placeholder="页面状态"
            value={presence}
            onChange={(value) => {
              setPresence(value);
            }}
            style={{ width: 130 }}
            options={[
              { value: "online", label: "在线" },
              { value: "offline", label: "离线" },
            ]}
          />
          <Select
            allowClear
            placeholder="接单状态"
            value={accepting}
            onChange={(value) => {
              setAccepting(value);
            }}
            style={{ width: 130 }}
            options={[
              { value: true, label: "开启" },
              { value: false, label: "关闭" },
            ]}
          />
        </Space>
        {permissions.includes("zsjos:subordinate-sales:pause-all") && (
          <Button danger icon={<PoweroffOutlined />} loading={pausingAll} onClick={confirmPauseAll}>
            一键下班
          </Button>
        )}
      </div>
      {error && (
        <Alert
          className="subordinate-page-error"
          type="error"
          showIcon
          message={error}
          action={
            <Button size="small" onClick={() => void loadFirstPage()}>
              重试
            </Button>
          }
        />
      )}
      <div
        className={`subordinate-inbox-layout ${selectedSales ? "show-detail" : "show-list"}`}
      >
        <aside className="subordinate-sales-list-pane">
          <div className="subordinate-sales-list" ref={listRef}>
            {loading && !rows.length ? (
              <Skeleton active />
            ) : rows.length ? (
              <>
              {rows.map((row) => (
                <button
                  type="button"
                  key={row.userId}
                  className={`subordinate-sales-item ${selectedSales?.userId === row.userId ? "active" : ""}`}
                  onClick={() => setSelectedSales(row)}
                >
                  <div className="subordinate-sales-item-title">
                    <EmployeeAvatar avatar={row.avatar} name={row.name} size={28} />
                    <strong>{row.name}</strong>
                    <Tag
                      color={row.accountStatus === 0 ? "success" : "default"}
                    >
                      {row.accountStatus === 0 ? "启用" : "停用"}
                    </Tag>
                  </div>
                  <div className="subordinate-sales-item-account">
                    {row.username} · {row.mobile || "未填写手机号"}
                  </div>
                  <div className="subordinate-sales-item-status">
                    <Tag
                      color={row.presence === "online" ? "success" : "default"}
                    >
                      {row.presence === "online" ? "在线" : "离线"}
                    </Tag>
                    <Tag color={row.accepting ? "processing" : "default"}>
                      {row.accepting ? "接单开启" : "接单关闭"}
                    </Tag>
                    <Tag color={row.canReceiveNewLeads ? "success" : "default"}>
                      {receiveStatusLabel(row)}
                    </Tag>
                  </div>
                  <div className="subordinate-sales-item-summary">
                    <span>
                      今日待跟进 <b>{row.todayPendingCount}</b>
                    </span>
                    <span>{todayStatusLabel(row.todayFollowUpStatus)}</span>
                  </div>
                  <div className="subordinate-sales-item-summary">
                    <span>有效客资 {row.validLeadCount}</span>
                    <span>
                      成交 {row.convertedLeadCount} /{" "}
                      {formatCurrency(row.effectiveOrderAmount)}
                    </span>
                  </div>
                </button>
              ))}
              <div ref={sentinelRef} className="subordinate-sales-load-sentinel" aria-hidden="true" />
              {loadingMore && <div className="subordinate-sales-load-state"><Button type="text" size="small" loading>正在加载更多</Button></div>}
              {loadMoreError && <Alert className="subordinate-sales-load-error" type="error" showIcon message={loadMoreError}
                action={<Button size="small" onClick={() => void loadMore()}>重试</Button>} />}
              {!loadingMore && !loadMoreError && rows.length >= total && (
                <div className="subordinate-sales-load-state">已加载全部 {total} 人</div>
              )}
              </>
            ) : (
              <Empty image={<TeamOutlined />} description="暂无下属销售" />
            )}
          </div>
        </aside>
        <main className="subordinate-sales-detail-pane">
          {selectedSales ? (
            <SalesDetail
              sales={selectedSales}
              permissions={permissions}
              onBack={() => setSelectedSales(undefined)}
              onChanged={() => void loadFirstPage(true)}
              onReasonAction={openReasonAction}
            />
          ) : (
            <Empty
              image={<TeamOutlined />}
              description="从左侧选择销售查看详情"
            />
          )}
        </main>
      </div>
      <Modal
        open={Boolean(reasonAction)}
        title={
          reasonAction?.type === "account" ? "修改账号状态" : "修改接单状态"
        }
        confirmLoading={reasonSaving}
        onOk={() => void submitReasonAction()}
        onCancel={() => setReasonAction(undefined)}
      >
        <Form form={reasonForm} layout="vertical">
          <Form.Item
            name="reason"
            label="操作原因"
            rules={[
              { required: true, whitespace: true, message: "请填写操作原因" },
              { max: 500 },
            ]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}
