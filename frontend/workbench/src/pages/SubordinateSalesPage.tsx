import SubordinateSalesCard from "../components/SubordinateSalesCard";
import SubordinateSalesStatusFilter from "../components/SubordinateSalesStatusFilter";
import BusinessTable from '../components/BusinessTable'
import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Button, Empty, Form, Input, InputNumber, List, Modal, Popover, Select, Skeleton, Space, Statistic, Switch, Tabs, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table/interface";
import {
  ArrowLeftOutlined,
  PoweroffOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import EmployeeAvatar from "../components/EmployeeAvatar";
import {
  api,
  type AdvancedFilterGroup,
  type BusinessTaskBucket,
  type SubordinateSales,
  type SubordinateTask,
} from "../services/api";
import { AdvancedFilterToolbar } from "../components/AdvancedFilter";
import LeadManagementPage from "./LeadManagementPage";
import { formatTimestamp } from "../services/time";
import {
  formatCurrency,
  subordinateTaskTypeLabel,
  appendSubordinateSalesRows,
  receiveStatusLabel,
  todayStatusLabel,
} from "../services/subordinateSales";

const PAGE_SIZE = 20;
type ReasonAction =
  | { type: "account"; sales: SubordinateSales; value: boolean }
  | { type: "dispatch"; sales: SubordinateSales; value: boolean };

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

function SalesDetail({
  sales,
  permissions,
  onBack,
  onChanged,
  onReasonAction,
  onLeadDirtyChange,
  canLeaveLeads,
}: {
  sales: SubordinateSales;
  permissions: string[];
  onBack: () => void;
  onChanged: () => void;
  onReasonAction: (action: ReasonAction) => void;
  onLeadDirtyChange: (dirty: boolean) => void;
  canLeaveLeads: () => boolean;
}) {
  const [tab, setTab] = useState("overview");
  const [tasks, setTasks] = useState<SubordinateTask[]>([]);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskBucket, setTaskBucket] = useState<BusinessTaskBucket>("today");
  const [taskLoading, setTaskLoading] = useState(false);
  const [taskError, setTaskError] = useState("");
  const taskRequest = useRef(0);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      taskRequest.current++;
    };
  }, []);
  const [newcomerForm] = Form.useForm<{ enabled: boolean; dailyQuota: number }>();
  const [newcomerOpen, setNewcomerOpen] = useState(false);
  const [newcomerSaving, setNewcomerSaving] = useState(false);
  const saveNewcomer = async () => {
    const values = await newcomerForm.validateFields().catch(() => undefined);
    if (!values) return;
    setNewcomerSaving(true);
    try {
      await api.updateSubordinateNewcomerPool(sales.userId, values.enabled, values.dailyQuota);
      message.success("新手池配置已保存");
      setNewcomerOpen(false);
      onChanged();
    } catch (saveError) {
      message.error(saveError instanceof Error ? saveError.message : "新手池配置失败");
    } finally {
      setNewcomerSaving(false);
    }
  };

  const loadTasks = useCallback(async () => {
    if (!mounted.current) return;
    const request = ++taskRequest.current;
    setTaskLoading(true);
    setTaskError("");
    setTasks([]);
    setTaskTotal(0);
    try {
      const page = await api.subordinateSalesTasks(sales.userId, {
        pageNo: 1,
        pageSize: 100,
        bucket: taskBucket,
      });
      if (request !== taskRequest.current) return;
      setTasks(page.list);
      setTaskTotal(page.total);
    } catch (loadError) {
      if (request !== taskRequest.current) return;
      setTaskError(loadError instanceof Error ? loadError.message : "待办加载失败");
    } finally {
      if (request === taskRequest.current) setTaskLoading(false);
    }
  }, [sales.userId, taskBucket]);
  useEffect(() => {
    if (tab === "tasks") void loadTasks();
    return () => { taskRequest.current++; };
  }, [loadTasks, tab]);

  const taskColumns: ColumnsType<SubordinateTask> = [
    {
      title: "客资",
      dataIndex: "leadName",
      render: (value, row) => `${row.leadNo} · ${value || '未命名客资'}`,
    },
    { title: "任务类型", dataIndex: "taskType", width: 180, render: (_, row) => subordinateTaskTypeLabel(row.taskType) },
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
          {<><span>接单</span>
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
          </>}
        </Space>
      </div>
      <Tabs
        activeKey={tab}
        onChange={value => {
          if (tab === "leads" && !canLeaveLeads()) return;
          taskRequest.current++;
          setTab(value);
        }}
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
                      {<><div className="subordinate-profile-row">
                        <span className="subordinate-field-label">接单状态</span>
                        <span className="subordinate-field-value">{sales.accepting ? '开启' : '关闭'}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">新客资</span>
                        <span className="subordinate-field-value">{receiveStatusLabel(sales)}</span>
                      </div>
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">新手池</span>
                        <span className="subordinate-field-value">
                          <Tag color={sales.newcomerPoolStatus === "active" ? "success" : "default"}>
                            {sales.newcomerPoolStatus === "active" ? "启用" : "未启用"}
                          </Tag>
                          {sales.newcomerDailyQuota > 0 && `今日已用 ${sales.newcomerAcceptedToday}/${sales.newcomerDailyQuota} 条，剩余 ${sales.newcomerRemainingToday} 条`}
                          {permissions.includes("zsjos:subordinate-sales:newcomer-pool") && (
                            <Button type="link" size="small" onClick={() => {
                              newcomerForm.setFieldsValue({
                                enabled: sales.newcomerPoolStatus === "active",
                                dailyQuota: sales.newcomerDailyQuota || 1,
                              });
                              setNewcomerOpen(true);
                            }}>配置</Button>
                          )}
                        </span>
                      </div>
                      </>}
                      <div className="subordinate-profile-row">
                        <span className="subordinate-field-label">部门 / 岗位</span>
                        <span>{[sales.departmentName, ...(sales.postNames ?? [])].filter(Boolean).join(' · ') || '—'}</span>
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
              tab === 'leads' ? <LeadManagementPage key={sales.userId} permissions={permissions} salesUserId={sales.userId} onScopeChanged={onChanged} onDirtyChange={onLeadDirtyChange} /> : null
            ),
          },
          {
            key: "tasks",
            label: "待跟进任务",
            children: (
              <>
                <Select
                  value={taskBucket}
                  onChange={value => { taskRequest.current++; setTaskBucket(value); }}
                  style={{ width: 160, marginBottom: 12 }}
                  options={[
                    { value: "overdue", label: "逾期" },
                    { value: "today", label: "今日" },
                    { value: "future", label: "未来" },
                    { value: "unscheduled", label: "未排期" },
                  ]}
                />
                {taskError && (
                  <Alert
                    type="error"
                    showIcon
                    message={taskError}
                    action={
                      <Button size="small" onClick={() => void loadTasks()}>
                        重试
                      </Button>
                    }
                  />
                )}
                <BusinessTable tableKey="subordinate-sales-page-2" columnMode="native"
                  rowKey="id"
                  loading={taskLoading}
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
      <Modal title="配置新手池" open={newcomerOpen} confirmLoading={newcomerSaving}
        onOk={() => void saveNewcomer()} onCancel={() => setNewcomerOpen(false)}>
        <Form form={newcomerForm} layout="vertical">
          <Form.Item name="enabled" label="启用新手池" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="dailyQuota" label="每日优先接单额度"
            rules={[{ required: true, type: "integer", min: 1, max: 2147483647, message: "请输入有效的正整数" }]}>
            <InputNumber min={1} max={2147483647} precision={0} style={{ width: "100%" }} />
          </Form.Item>
          <Typography.Text type="secondary">今日已接 {sales.newcomerAcceptedToday} 条；修改额度不会清除已用数量。</Typography.Text>
        </Form>
      </Modal>
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
  const [accountStatus, setAccountStatus] = useState<number | undefined>(0);
  const [accepting, setAccepting] = useState<boolean>();
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [loadMoreError, setLoadMoreError] = useState("");
  const [selectedSales, setSelectedSales] = useState<SubordinateSales>();
  const leadDirty = useRef(false);
  const onLeadDirtyChange = useCallback((dirty: boolean) => { leadDirty.current = dirty }, []);
  const canLeaveLeads = () => !leadDirty.current || window.confirm("当前客资表单尚未提交，切换将丢失已填写内容。确定继续吗？");
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
  const refreshSelectedSales = async () => {
    if (!selectedSales) return;
    const userId = selectedSales.userId;
    try {
      const updated = await api.subordinateSalesOverview(userId);
      setSelectedSales(current => current?.userId === userId ? updated : current);
      setRows(current => current.map(row => row.userId === userId ? updated : row));
    } catch (refreshError) {
      setError(refreshError instanceof Error ? refreshError.message : "员工详情刷新失败，请重试");
    }
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
          <SubordinateSalesStatusFilter value={accountStatus} onChange={value => { if (canLeaveLeads()) setAccountStatus(value) }} />
          <AdvancedFilterToolbar
            scene="subordinate_sales"
            pageKey="subordinate_sales"
            placeholder="搜索姓名、账号或手机号"
            keyword={keyword}
            value={advancedFilter}
            onKeyword={(value) => {
              if (canLeaveLeads()) setKeyword(value);
            }}
            onChange={(value) => {
              if (canLeaveLeads()) setAdvancedFilter(value);
            }}
          />
          <Select
            allowClear
            placeholder="页面状态"
            value={presence}
            onChange={(value) => {
              if (canLeaveLeads()) setPresence(value);
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
              if (canLeaveLeads()) setAccepting(value);
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
                <SubordinateSalesCard key={row.userId} sales={row}
                  selected={selectedSales?.userId === row.userId} onSelect={() => { if (selectedSales?.userId !== row.userId && canLeaveLeads()) setSelectedSales(row) }} />
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
              key={selectedSales.userId}
              sales={selectedSales}
              permissions={permissions}
              onBack={() => { if (canLeaveLeads()) setSelectedSales(undefined) }}
              onLeadDirtyChange={onLeadDirtyChange}
              canLeaveLeads={canLeaveLeads}
              onChanged={() => void refreshSelectedSales()}
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
