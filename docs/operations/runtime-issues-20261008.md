# 中仕建测试环境问题修复与待验记录

记录日期：2026-10-08。实施环境为 `local`，当前 `main` 工作区；不是测试环境部署记录。保留已有修改，未新增依赖、执行 SQL、改权限、重启共享服务、提交或发布。原报告日志次数不等于独立失败请求数。

## 逐项状态

| 问题 | 本次处理与证据 | 状态及关闭条件 |
| --- | --- | --- |
| 订单礼品提交、补正及 JSON 展示 | 统一快照解析/校验；提交编码与历史展示分离；保留旧字段；两端展示和 Workbench 补正适配；损坏历史阻止误清空 | 源码已修、本地验证通过，未部署；需核对运行包及真实订单流程 |
| 商品快照 null 标志 | 统一历史读取边界，缺失/null 转 false，其他错误类型拒绝；保留十进制精度；订单、财务、报名/学员消费者复用 | 源码已修、本地验证通过，未部署；实际列表/详情/审批/导出待验 |
| BPM 通知无 ADMIN 发起人 | DTO 使用 BpmStartSubjectDTO；ADMIN 可读姓名、PARTNER 保存姓名，否则“发起人信息不可用”；发起人查找失败不阻断有效处理人通知 | 源码已修、本地验证通过，未部署；实际渠道送达与重复投递待验 |
| 公告附件关联 | 保留已有复用/恢复实现，NoticeServiceImplTest 20 项、NoticeMapperTest 5 项通过 | 本地回归通过；正文、移除再添加、复制、跨租户和上传归属的部署包及真实接口验收未关闭 |
| 考期日期 | 复用已批准过期草稿改期实现；ExamScheduleServiceTest 19 项、前端日期测试 4 项通过；既有记录见下方链接 | 本地已修、部署待验；实际接口的上海时区边界及发布失败重试仍需部署验收 |
| 客资死锁 | 只读确认本地容器 MySQL 8.4.11、REPEATABLE-READ；无 LATEST DETECTED DEADLOCK | 证据不足待诊断，不能判定根因，不加索引或重试 |
| 上传 499 | 实际请求拦截器的浏览器检查确认公告/工单 FormData 为 600000ms，普通 JSON 为 30000ms，显式超时保留 | 覆盖性已验证；慢网络、大文件、并发、主动取消与网关/存储关联耗时未取得，不宣称 499 已修 |
| BPM 取消异常 | 真实 Flowable 8/H2 引擎验证并行任务取消、终止与重复取消；保留现有日志与生产分支 | 原始活动缺失异常、真实会签/驳回和业务回调完整验收仍待目标场景；未降低日志或吞异常 |
| 部门权限告警 | 部门管理查询仍受 system:dept:query 约束，无改权或放宽默认拒绝 | 缺具体请求、主体与有效权限，不能判定错误过滤或正常拒绝 |
| 集合校验弃用 | 24 个受影响文件改为元素级 @Valid；非法嵌套属性/附件校验测试通过 | 源码已修、本地验证通过，未部署 |
| 字典、版本、返现告警 | 保留字典历史快照、版本冲突及商品前置条件；返现源码仍拒绝未指定商品 | 缺原请求、历史记录和两类版本错误码对应证据；待诊断，不放宽校验 |
| 令牌、企微、定位链接 | 不修改生命周期、绑定或授权规则 | 缺过期/已消费/重复访问/未绑定/无权限的目标环境链路；提示及恢复入口待验 |
| 502、Broken pipe | 未发现足够证据将其归因到特定 ZSJOS 请求或服务 | 待原始日志的时间、路径、上游关联；未据此重启服务 |

“正常业务拒绝”只能用于已核对请求与业务条件的案例；本次没有将未复现告警一律归入该类。没有生产或测试环境项目被标记为“已修并验收”。

## 实现边界

礼品契约详见 [订单接口](../api/zsjos-sales-order.md#礼品与历史商品快照兼容2026-10-08)。历史快照只保留已有事实；未批量修复损坏历史。权限、租户及通知接收人归属边界未扩大。

BPM 任务通知保留事务完成回调，事务回滚不发送；发起主体用于表达来源与姓名，接收人仍为任务处理人 ADMIN。事件键仍为 `bpm.task.assigned:<taskId>`，不同任务使用不同键，重复同任务维持同键。没有加入业务重试或主动补发历史事件。通知系统既有 outbox/API/processor 回归通过；事件键与回调测试不等于真实渠道重复投递验收。

真实引擎测试曾使用绕过业务入口预处理的夹具直接移动并行任务，触发任务已删除异常。夹具已改为遵循实际 returnTask 的预先标记步骤，并调用生产 moveTaskToEnd。最终通过证明这些受控场景可用，不能据此将原报告全部活动缺失认定为正常幂等。

## 验证证据

本轮相关后端测试累计 **164 项，0 失败、0 错误、0 跳过**（以下两组最新 Surefire XML 汇总，重复类只计一次）。其中订单服务 70、礼品解析 5、商品历史 3、集合校验 1、公告 25、考期 19、BPM 转换/回调/真实引擎/事件身份 8、请求体 2、通知基础设施 31。

```powershell
mvn -f backend/pom.xml -pl yudao-module-zsjos -am '-Dtest=SalesOrderGiftSnapshotTest,HistoricalProductSnapshotTest,SalesOrderServiceImplTest,BpmTaskNotificationSubjectTest,BpmTaskAssignedNotificationTest,BpmCancellationEngineTest,BpmPartnerStartSubjectConvertTest,UnreadableRequestBodyTest,CollectionElementValidationTest,NoticeServiceImplTest,NoticeMapperTest,ExamScheduleServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -q
mvn -f backend/pom.xml -pl yudao-module-bpm -am '-Dtest=BpmTaskMessageIdentityTest,UnreadableRequestBodyTest,NotifyBusinessEventApiImplTest,NotifyBusinessEventProcessorTest,NotifyBusinessOutboxServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -q
```

Workbench 相关 **26 项**通过：orderGifts 4、SalesOrderEntryModal 7、SalesOrderDetailCards 9、examCalendarDates 4、directUpload 2；`npm run typecheck` 通过。Admin 礼品格式函数通过实际源码执行的契约测试；实际 Vue 页面通过隔离传输夹具浏览器验证。未用整个 Admin 项目的类型检查或生产构建作为本次证据。

浏览器 Chrome 在桌面 1440px、移动 390px 验证生产订单详情/录入组件及 Admin 礼品采购页：历史名称、已删除礼品保留、直接重提编码、显式清空、损坏阻断、两类历史存储与不展示 JSON。夹具不写业务库。源码入口为 `frontend/workbench/test/order-gifts.html`、`frontend/admin/test/runtime-gifts.html`；Admin 独立 Vite 配置为 `runtime-gifts.vite.ts`。本地截图保存在临时目录 `runtime-gifts-browser-5r2vh7kv` 和 `runtime-gifts-final-qzaf0k6e`，不作为部署验收。

## 死锁证据缺口与最小后续方案

本地容器不是目标测试库；应用本地配置指向其他数据库地址，不能把容器版本和索引当作目标事实。只读检查了任务相关租户/业务键、处理人/状态/到期时间、更新时间和幂等唯一索引；合成零 ID 的 UPDATE EXPLAIN 选择 `idx_tenant_assignee_status_due_v2`，range，估算 1 行，Using where/Using temporary。该计划不是实际失败请求计划，不支持新增索引结论。

下一步从目标库取得版本、隔离级别、精确表结构、实际绑定参数对应执行计划和完整 deadlock graph；按同一时间窗口串联派单、跟进、自拓及定时任务的事务 SQL 与锁获取顺序。定位相反锁顺序或扫描范围后，提出最小语句顺序/条件修改，并在目标 MySQL 版本进行并发复现与回归。任何 SQL、规则或重试变更另行形成具体方案；本次不预设修复。

## 部署与回滚待办

后端先发布兼容字段，再发布前端；执行前仍需针对部署目标、服务重启或共享库写入的授权。发布前检查构建版本和共存请求/响应，回滚包必须读取两类历史格式及本次对象数组新写入格式；不满足则暂停发布。

经授权后用代表性业务请求验收，再关联观察快照解析错误、礼品补正参数错误、BPM 通知失败、附件冲突和上传耗时。零日志但没有对应业务请求不能关闭问题。尚未完成：测试环境实际接口、真实 MySQL 并发、完整 BPM 会签/回调、通知真实投递、上传端到端压力及原始网关链路。

相关记录：[本次交付](../../handoff/local-runtime-issues-20261008.md)、[考期既有交付](../../handoff/local-exam-dates-20261008.md)、[上传限制](attachment-upload-limits.md)。
