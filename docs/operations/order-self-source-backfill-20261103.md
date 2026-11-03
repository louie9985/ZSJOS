# 中世健订单自拓来源展示回填（2026-11-03）

## 授权和实际结果

用户在审阅候选范围后明确要求修复，并排除此前单独列出的数据。本次仅操作环境标识
`test`、现有 `zsjos-mysql-1` 的 `zsjos` 库、租户 1；没有访问生产、发布或重启服务。

已修复 252 笔订单的 252 个审批轮次快照，涉及 192 条客资：

| 来源 | 订单/轮次 | 客资 |
| --- | ---: | ---: |
| 销售自拓 | 155 | 114 |
| 教务自拓 | 97 | 78 |

订单状态为 191 笔已生效、60 笔已被重提订单替代、1 笔待修改重提。旧单也在明确授权范围内。
回填后原候选条件的剩余数量为 0。缺失整份客资快照/轮次的 56 笔、来源类型不一致的
6 笔、已有姓名的 53 笔未关联提供方订单、2 笔关联提供方订单及原本已有标记的 1 笔均未修改。

## 依据与精确修改边界

旧 `SalesOrderServiceImpl.buildLeadProfile` 在自拓客资已明确记录未选提供方时主动
省略来源姓名，但未保存 `selfSourcedWithoutProvider`。新的展示契约需要显式布尔值。
本次不是从空姓名、当前负责人、岗位或当前用户名推断历史：

- 208 笔使用客资原始提交时冻结的 `source_provider_recorded=1` 与空提供方记录，
  同时校验原始提交幂等标识、时间、客资编号、订单快照来源类型及已有来源主体标识。
- 44 笔来自历史导入，另有完整 `__before__`、`__after__`、`__provenance__` 凭据：
  38 笔对应 `lead-source-confirmed-v309-r2`，6 笔对应 `confirmed-provider-v308`。
  它们是此前明确确认的本人自拓修复，不重新推断旧库含义。
- 其中 92 笔另有订单提交前的客资流转快照，明确记录自拓来源与空提供方，作为补充证据。
- 每笔都校验提供方身份、姓名和贡献人员不存在冲突；原快照中的来源类型和客资编号必须匹配。

唯一业务字段改动为 `zsjos_order_approval_round.order_snapshot` 内的
`leadProfile.selfSourcedWithoutProvider=true`（JSON 布尔值）。完整 JSON 的其余内容不变；
轮次 `version` 增加 1，`updater=order-self-source-20261103`，`update_time` 记录修复时间。
姓名字段仍为空，页面使用既有逻辑显示“销售本人自拓（未关联新媒体）”或
“教务本人自拓（未关联新媒体）”。没有修改来源姓名、客资归属、金额、业务状态、BPM 历史或权限。

## 工具、前置条件和顺序

工具：[backfill_order_self_source_20261103.py](../../script/sql/mysql/repairs/backfill_order_self_source_20261103.py)。
复用已有 PyMySQL 和 Docker 连接工具，无新增依赖或数据库结构。仅允许 `test`，并绑定
已审阅数据库的 server UUID、库名、容器及租户。需要已有来源冻结字段、订单 JSON 快照、
历史修复凭据和 MySQL 8 的 NOWAIT 行锁；实测引擎为 8.4.11。

这是单独授权的存量业务数据修复，不是初始化种子，不改已应用迁移、基线或版本账本。
`prepare` 固定清单并在连接级临时表中演练；`apply` 只消费同一已验证计划，不重新扩大候选范围。
以下命令记录本次执行顺序，不授权其他环境或新清单的写入：

```bash
python3 -B script/sql/mysql/repairs/backfill_order_self_source_20261103.py prepare \
  --evidence-dir /opt/zsjos-runtime/backups/order-self-source-20261103/review-1
python3 -B script/sql/mysql/repairs/backfill_order_self_source_20261103.py apply \
  --evidence-dir /opt/zsjos-runtime/backups/order-self-source-20261103/review-1
python3 -B script/sql/mysql/repairs/backfill_order_self_source_20261103.py verify \
  --evidence-dir /opt/zsjos-runtime/backups/order-self-source-20261103/review-1
```

上述目录已存在，不能重新 prepare 覆盖。已提交后 apply 拒绝再次写入，应使用只读 verify。
底层写入器对完整后镜像重复执行为零写入；前镜像、来源证据或其他轮次字段发生变化时拒绝执行。
所有目标在一个事务中修改，按订单、轮次、客资顺序加 NOWAIT 锁，遇到锁竞争立即中止，
不停止业务服务。没有删除数据，未制造新的审批轮次或重放业务流程。

## 私有备份与恢复限制

私有目录保存逐笔完整轮次前后镜像、订单保护镜像、冻结来源和历史凭据；目录权限 0700、
文件权限 0600，写入 fsync 后才允许提交，不将个人数据放进仓库或公开日志。
计划摘要 SHA-256：`103c841c992c191d801d36b6829e0d56c70323166313d813a2dfd61d5d895d72`。

- `plan.json`：固定目标、完整前后镜像与来源依据。
- `rehearsal.json`：计划及写入器摘要、受控 MySQL 验证结果。
- `precommit-protection.json`、`pending-commit.json`：保护范围摘要和提交前校验。
- `committed.json`：独立连接提交后复读结果。

提交前失败自动回滚；临时表验证已覆盖提交了部分目标后的恢复和完整前镜像恢复。
真实提交后的恢复需单独审阅、授权，并重新匹配全部后镜像，不能覆盖后来业务编辑，
也不能直接照抄演练时回退版本号的操作。CLI 不提供真实库自动回滚入口。
如果提交响应丢失，先运行只读 verify，结合 pending/committed 凭据判定状态；
不能删除证据、重复 prepare 或盲目重跑 apply。修复自身没有外部消息副作用。

## 验证证据

- 同引擎、真实轮次表结构的临时表验证：首次应用、重复零写入、第 7 笔故障回滚、
  7 笔已提交后的剩余 245 笔恢复、并发修改拒绝、252 笔恢复后重应用均通过。
- 真实事务中逐条完整后镜像匹配；925 行订单主表、446 行非目标审批轮次和 192 条关联
  客资完整镜像保持不变。事务内再次调用写入器为零写入。
- 独立连接及随后单独 verify 均通过 252 个完整后镜像、JSON BOOLEAN 和既有中文
  来源标签的 UTF-8 HEX 检查。原候选条件剩余 0，修复标识精确对应 252 行。
- Workbench 现有 `orderSourcePerson.test.tsx` 两项测试通过，覆盖展示及列表投影。
  首次默认配置加载受现有 root 所有 `.vite-temp` 目录权限阻挡；改用现有 Vite
  `--configLoader runner --no-cache` 后通过，未修改目录权限或安装依赖。
- Admin 实际 `OrderHistoryFacts.vue` 编译及服务端渲染检查通过销售、教务、缺失标记和
  false 标记四种情况。两端运行时源代码均未变更。
- Python 语法、定向 diff 和文档链接检查通过。未进行真实账号 HTTP 或浏览器验收；
  本次没有交互、样式、路由或构建改动，不将组件验证表述为真实浏览器验收。
