# 中实建订单领取归属修复（2026-10-16）

用户在只读扫描确认后授权“同步一起调整”。本次执行环境为 test，数据库
`zsjos-mysql-1/zsjos`，MySQL 8.4.11，租户 1。不得将该授权用于生产环境。

## 范围及关系

执行源：[repair_order_receipts_20261016.py](../../script/sql/mysql/repairs/repair_order_receipts_20261016.py)。
脚本 TARGETS 固定列出 11 个 ORDER 归属记录及订单业务号，涉及 8 个客资、
8 笔有效订单和 3 笔 superseded 历史订单。原关联均为同客资的历史 `other` 事件。
仅更新 `zsjos_performance_attribution.assignment_id/received_at/updater/update_time`。
不删除记录，不修改订单状态、金额、人员归属、Lead 当前关联、组织快照、来源或服务关系。
不变更表结构、迁移版本、权限或 BPM，不发布代码或重启服务。

原数据与预期后像按完整行私密备份。新关联必须同时满足：真实领取事件类型、
同租户/同客资、接收人一致、订单提交前最新归属事件、当前最新归属事件、
唯一 ASSIGNMENT 事实及其真实接收时间一致。订单、Lead、历史事件和接收快照先锁定核验。
11 条写入同一 InnoDB 事务；任意前像或依赖变化均拒绝整批执行。

## 执行及恢复

前提为已有业务表及已核实的领取历史；先 prepare，再带方案 SHA256 执行 apply，最后 verify。
prepare 在同一 MySQL 引擎的临时表验证首次、重复、故障回滚、部分执行恢复、并发修改拒绝和恢复。
不新建持久业务表、不插入业务记录，因此没有删除或插入顺序；关联完整核验后才更新。
连接显式 utf8mb4、Asia/Shanghai，审计标记为 ASCII。

```bash
python3 -B script/sql/mysql/repairs/repair_order_receipts_20261016.py prepare --evidence /private/new-plan-directory
python3 -B script/sql/mysql/repairs/repair_order_receipts_20261016.py apply --evidence /private/new-plan-directory --expected-sha256 REVIEWED_SHA256
python3 -B script/sql/mysql/repairs/repair_order_receipts_20261016.py verify --evidence /private/new-plan-directory --expected-sha256 REVIEWED_SHA256
```

重复 apply 跳过完全匹配的后像；前像/后像混合时可继续剩余行。任何其他变化拒绝覆盖。
恢复使用同一命令的 restore 模式，要求全部目标仍等于原后像且依赖保持一致；
会恢复备份中的四个修改字段及原审计值。已发生后续业务编辑时不能强制回滚，应重新调查。
恢复真实数据库同样需要对应操作授权；本轮只在临时表演练恢复。

私密证据：`/opt/zsjos-runtime/backups/order-receipts-20261016-11-v1`，目录 0700、文件 0600。
plan.json SHA256：`b502817ed529bc59f4187e00f0fc9bcf10bde306de6dcf1fd5c713033721a14f`。
备份包含敏感行，不复制到仓库或交付报告。

## 验证及影响

- MySQL 临时表：初次 11、重复 0、第四条注入异常后全部回滚、部分 4 条提交后补齐 7 条、拒绝并发修改、精确恢复全部通过。
- 实库提交 11 条，独立提交后完整后像回读通过，再次 apply 为 0。
- 直接依赖的订单、Lead、历史事件、服务及同客资其他归属记录行数/摘要保持一致。
- 最终订单 ORDER 归属指向 `other` 的数量为 0；代表性中文快照通过 UTF-8 客户端及 HEX 核验。
- 当前部署版本的真实 MyBatis mapper + PerformanceConversion 重算：上月成交客资/分母分别为 3/214、1/138、18/419，对应修复前 2/214、0/138、17/418。
- 其余 5 笔有效订单因金额门槛或周期规则仍不新增成交；3 笔被替代订单仍排除。金额、60 日、来源和当前归属规则不变。

防复发源码在 PerformanceSnapshotService 的 ORDER 写入路径解析提交时的真实归属事件，
读取端保留严格冻结关联检查。17 项聚焦测试通过，覆盖时间、租户、逻辑删除、
无人归属、转派、同刻事件顺序、缺失历史、复购和成交口径。
常规 Maven 运行因既有 root 所有的 target 目录不可写失败；改用独立临时目录，
沿用现有依赖编译修改的 Java 源码并执行 JUnit。未进行完整 reactor 构建、发布或服务重启。
数据修复不依赖发布，防复发源码只有后续部署后才作用于新订单。浏览器及 HTTP 页面操作未验证。

已准备临时部署候选包 `/tmp/zsjos-order-receipt-check/yudao-server-order-receipt-candidate.jar`，
基于当前运行 release，仅替换经过上述编译/测试的两个 class；逐项比较确认外层只变更
ZSJOS 模块 jar，内层只变更 LeadAssignmentHistoryMapper 和 PerformanceSnapshotService。
SHA256 `3dd453cd9b2cd68e233e98b6e85bebb80afbf947463800238da8fd8f3dfb68b3`。
该包是临时审查候选，不是完整 Maven 构建结果；未发布，未切换运行版本，未验证启动。
如确认部署测试环境，需保留当前运行包用于回退，并在重启 `zsjos-backend.service` 后验证健康及统计接口。
