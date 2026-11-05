# 已通过复购订单关联客资（2026-11-05）

**后续身份纠错：** 下文保留当时执行记录。发现微信 `无` 导致的原客户误归并后，
已按用户最新要求拆分该组，`OD202610150855120003` 的当前关联及回填快照已更正为
`KZ202610130845270001`，覆盖下文原 `LD202607220004` 的选择；后者及其历史首购
已归回原客户。此前“同 Person”校验不能证明原始归并身份正确。实际修复和全部
127 组审计见[误归并修复与全量审计](false-person-merge-20261105.md)。

## 范围和已确认规则

本次仅处理环境标识为 `test` 的 `zsjos-mysql-1` / `zsjos` / 租户 1。
只纳入 39 条 `order_type=repurchase`、`status=effective`、当前审批轮次
`status=approved` 的订单。24 条已被新订单替代、4 条已终止、1 条待补正记录排除。

用户明确授权按同一客户档案匹配客资：优先录单人的当前所属客资；只有其他
用户客资时也可关联。唯一候选直接关联；多个候选按已成交 `won`、有效 `valid`
优先筛选，仍不唯一交用户裁定；无客资不处理。不按同名、联系方式或归并猜测
跨客户档案关联，不合并客户，不创建客资。

最终清单为新增关联 24 条：同录单人唯一 12 条、其他负责人唯一 8 条、状态
优先选出 3 条、用户裁定 1 条；已有正确关联的 5 条保留，档案下无客资的 10 条
跳过。新增关联中 10 条跨负责人，1 条唯一候选客资状态为 `invalid`，按用户
“唯一匹配直接关联”的决定保留该关联，不改变客资状态。

订单 `OD202610150855120003` 的两条候选都属于录单人且已成交。用户最后更正为
关联 `LD202607220004`，覆盖早先选择的 `KZ202610130845270001`。更正前未进行
数据库写入。最终固定清单在执行器的 `PAIRS` 中，完整行镜像仅存于私有证据目录。

本文前述关联操作的“快照保持缺失”结果由文末后续明确授权的快照回填补充覆盖；
两个步骤各自保留独立备份与验证，不把当前资料回填解释为历史证据恢复。

## 业务影响和边界

既有复购接口约定仅关联 Person。本次用户明确决定构成上述存量已通过订单的
定向例外；新订单运行逻辑、购买草稿、幂等和审批接口均未修改，不表示今后新复购
会自动关联客资。

仅修改 `zsjos_order.lead_id`、`version`、`updater`、`update_time`。录单人、正式
成交人、成交身份、订单类型、金额、付款、审批、历史归属快照保持原样，客资
归属和状态不变。不重启审批、不触发业务事件、不新增返现、不写权限配置。

关联使用现有对象权限：关联客资的当前负责人可满足通用订单详情的对象读取
条件，关联订单的有效服务参与关系也可满足客资读取条件；仍累计入口功能权限。
管理订单列表、本人列表、订单修改和终止权限仍由各自原规则控制。跨客户历史
身份范围不重写。24 条目标客资均无兼职关联、无活动公海周期，当前清单不会
新增兼职关联成交汇总或公海协作读取关系；这些前置条件若变化执行器拒绝应用。

销售/教务业绩仍按订单正式成交人及已冻结业绩归属统计，复购仍归非引流且不增加
首购转化人数。依赖当前客资关联的明细链接、关联筛选或服务客资查询会获得关联。
历史审批快照不补造当时的客资资料：Workbench 订单列表的 `leadNo`/来源投影及
Vue Admin 历史资料仍消费原 `leadProfile`，因此历史未记录项可能仍为空，不能把
本次数据库关联验收解释为历史资料已补全或已完成真实账号浏览器验收。

## 执行和验证

执行器：`script/sql/mysql/repairs/link_approved_repurchase_leads_20261105.py`。
复用现有 PyMySQL、Docker 连接器和私有序列化工具，无新增依赖，不进入初始化、
编号迁移或版本账本。MySQL 8.4.11，显式 `utf8mb4` 和北京时间。

执行顺序：只读冻结准确客户、候选、订单和受保护行；真实引擎连接级临时表演练；
实际事务内 NOWAIT 锁定并完整前像重验；写入 24 条；完整后像、受保护行及中文
UTF-8 HEX 检查；落盘提交前后像；提交；独立连接回查。原订单无数据库触发器，
已生效订单的两个活动订单生成列保持空。候选或任何冻结前置状态变化即拒绝。

```bash
python3 -B script/sql/mysql/repairs/link_approved_repurchase_leads_20261105.py prepare --evidence-dir /opt/zsjos-runtime/backups/repurchase-lead-link-20261105/prepared
python3 -B script/sql/mysql/repairs/link_approved_repurchase_leads_20261105.py apply --plan /opt/zsjos-runtime/backups/repurchase-lead-link-20261105/prepared/plan.json --evidence-dir /opt/zsjos-runtime/backups/repurchase-lead-link-20261105/applied
python3 -B script/sql/mysql/repairs/link_approved_repurchase_leads_20261105.py verify --plan /opt/zsjos-runtime/backups/repurchase-lead-link-20261105/prepared/plan.json --evidence-dir /opt/zsjos-runtime/backups/repurchase-lead-link-20261105/verified
```

目录 0700、文件 0600；使用新证据目录重复执行，不覆盖既有镜像。计划摘要
`5f5db45d05b186423f9f45957c8c23a05dc7fe966967e350c18f746f738779d1`。
演练已通过首次 24 条、重复 0 条、第 1/12/24 条写入故障后的全事务回滚、7 条
已应用后剩余 17 条恢复、冲突行拒绝且无部分写入、完整恢复和重新应用。

保护范围包括 925 条非目标订单、33 条候选客资、37 条客户、39 条审批轮次、
39 条业绩归属，以及相关订单命令、明细、履约、服务、返现和付款记录；完整
内容用于比较，敏感载荷不进入代码、日志或交付文档。

## 恢复限制

实际写入为单事务 InnoDB DML，提交前失败全部回滚；完整行镜像保存在上述私有
目录。重放只接受精确前像或后像，已完成记录零写入，不覆盖后续业务变化。
提交后恢复须另行确认当前状态无后续修改，仅补偿上述 24 条的关联及审计字段；
执行器的恢复能力仅用于连接级临时表演练，不提供未经审核的实际库恢复入口。
本次没有整库备份、数据库重建、DDL、服务操作或生产访问。

## 实际结果

已在上述 test 数据库提交 24 条定向关联。独立连接核验完整后像和中文 HEX
全部通过，1,249 条受保护记录完整比对一致；重复执行写入 0 条，独立只读
`verify` 再次通过。`applied/`、`repeated/`、`verified/` 保存相应结果和镜像。

最终 39 条已通过复购中 29 条有关联、10 条同档案下无客资。剩余“有同档案
客资却未关联”的订单数为 0，跨租户、已删除客资或跨 Person 错误关联数为 0。
用户更正的订单 `OD202610150855120003` 已准确关联 `LD202607220004`。
24 条已替代、4 条已终止、1 条待补正记录保持不变。

Python 语法、UTF-8、修正后的固定映射、文档链接和限定 diff 检查通过。
没有修改 Java、前端或运行配置，因此未运行应用构建；未使用真实账号做
浏览器/接口验收，结论限于真实数据库关系、完整保护记录及已检查的代码读取
边界，不宣称历史资料显示已补齐。

## 后续授权：缺失客资快照回填

用户在查询 `OD202610081849390016` 的“客资档案历史未记录”后明确要求回填
快照。此次将先前新增关联的 24 条订单当前已通过轮次中的空 `leadProfile`
填入当前关联客资资料；原有五条完整快照保留，无客资十条不处理。

来源为准确关联的同租户同 Person Lead 行、System 当前人员名称，以及 Lead
保存的分类/渠道标签快照。技术来源文案和身份标签对齐现有
`SalesOrderServiceImpl.buildLeadProfile` / `LeadConstants.ownerIdentityLabel`。
目标不含兼职来源，无跨档案推断、当前字典重解析、权限推断或旧业务编号回退。
14 条明确自拓且未关联提供人的客资按原有字段表达，10 条新媒体来源按原有
来源人员资料表达。未知字段保持空，不编造姓名、标签或录单时状态。

快照新增 `leadProfileBackfill`，包含修复键 `repurchase-profile-20261105`、
真实回填时间、来源说明、客资业务编号、来源行摘要及
`historicalAtSubmission=false`。原 `capturedAt` 和其他已有字段保持不变；
这些资料是本次明确修复时的资料，不代表订单最初提交时的客资状态。
现有解析器忽略额外 provenance 字段，实际客资展示继续使用 `leadProfile`。

执行器 `script/sql/mysql/repairs/backfill_repurchase_profiles_20261105.py`
复用原关联固定清单和已有准确前像审批轮次写入器。只修改
`zsjos_order_approval_round.order_snapshot` 的上述两个节点及版本、更新审计字段。
没有 Order/Lead、审批状态、金额、业绩、BPM、通知、权限、运行代码或迁移账本写入。
来源、目标和受保护行变化即拒绝；事务前后完整行比对，提交后独立连接重验。

私有根目录 `/opt/zsjos-runtime/backups/repurchase-profile-20261105/`，目录 0700、
文件 0600，`prepared/plan.json` 保留完整来源与轮次前后像。执行顺序：

```bash
python3 -B script/sql/mysql/repairs/backfill_repurchase_profiles_20261105.py prepare --evidence-dir /opt/zsjos-runtime/backups/repurchase-profile-20261105/prepared
python3 -B script/sql/mysql/repairs/backfill_repurchase_profiles_20261105.py apply --plan /opt/zsjos-runtime/backups/repurchase-profile-20261105/prepared/plan.json --evidence-dir /opt/zsjos-runtime/backups/repurchase-profile-20261105/applied
python3 -B script/sql/mysql/repairs/backfill_repurchase_profiles_20261105.py verify --plan /opt/zsjos-runtime/backups/repurchase-profile-20261105/prepared/plan.json --evidence-dir /opt/zsjos-runtime/backups/repurchase-profile-20261105/verified
```

固定计划摘要 `411757b8eafe82f532926146a51c4fb72532fc050467774d6b2339b4c40b0fec`。
MySQL 8.4.11 临时表通过首次 24、重放 0、写入第 1/12/24 条故障回滚、部分 7
加恢复 17、冲突拒绝、恢复/重新应用及中文 JSON 字段 HEX。当前发布包中实际
`SalesOrderSnapshot.read/apply` 对 24 条拟回填快照解码和响应投影通过，
`historyMissingFields.leadProfile` 均不再出现；额外来源信息不使快照解析失败。
独立 Java 检查器首次编译括号错误已修正并重跑通过，未影响数据库。

原审批轮次没有触发器。重复执行只接受准确前像或完成后像，不重复写入；
提交前异常全部回滚。提交后恢复需按私有前像另行审查当前后像及后续业务，
不覆盖后来变更；本执行入口不提供实际库自动恢复或删除。源码与文档不包含
姓名、联系方式或完整敏感快照，证据仅存私有目录。真实账号浏览器/HTTP 验收
尚未执行，解码/投影验证不能代替完整浏览器流程。

实际结果：24 条空客资快照已提交，完整后像及中文 HEX 独立回查通过，重复执行
写入 0 条，1,284 条受保护行（包括原有五条轮次及全部范围内订单、客户、客资、
人员源投影、业绩和履约相关记录）一致。来源保护摘要
`7b8a95bb93da12e7b242f76d07698eeba679c7c496de70d5d8e128cb6a6c8c6b`。
再次从实际数据库读取 24 条已提交快照，以当前发布包实际 Java 解码/投影验证
通过；29 条已关联复购的快照客资编号与当前关联全部一致。
`OD202610081849390016` 已包含 `LD202608230008` 的客资资料。
结果和镜像位于 `applied/`、`repeated/`、`verified/`，实际解码验证位于
`java-check/stored-result.txt`。页面需重新请求订单详情才能取得回填结果。
