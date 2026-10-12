# 成交订单历史修复（V305）

本文件为中世健已确认数据修复的交付入口。仅租户 1；当前业务状态及本次确认的逐单人员映射是依据，旧库用于恢复缺失资料，不回退当前订单状态。不得重放 BPM、通知、返现或服务开通。

## 范围

本地预检基准为 392 笔生效订单，排除 creator 为 seed-liudandan、seed-students、seed-llyilin、seed-batch4 的 107 笔批量录入订单。不是按金额零过滤。

逐单 38 笔人员映射和 8 条客资映射保存在 `order_history_repair_model.py`，来自用户逐单确认，不能根据昵称扩展。3 笔离职销售订单 OR202606250003、OR202606260004、OR202606260005 不补人员及业绩。没有可靠证据的身份、审批人、SKU 和规格保持缺失。

写入：8 条客资、1 条现有机会、280 条订单、49 条现有轮次、37 条商品明细；插入 271 个历史导入轮次、2 条商品明细、38 条业绩归属。37 条明细更新包括 3 条用户批准的重复导入记录逻辑删除和 34 条快照恢复。实际执行以冻结计划和目标原值校验为准，不能用本地行号在其他环境盲目更新。

271 个新轮次均为本系统第 1 轮，process_definition_key=legacy-finance-review，process_instance_id=NULL，status=approved。旧 revision_no 仅留档。有真实旧库通过记录时使用其 reviewed_at（按目标 DATETIME 秒精度截断）；否则完成时间为空，不用 effective_at 冒充审批时间。既有轮次仅 OR202609200030 修正 rejected 为 approved。

307 份补录快照使用修正后的当前订单／客资并记录本次 capturedAt；208 笔原始字典标签取源备份，99 笔原始空值不编造标签。11 份 V1 使用原快照投影到 V2。两笔渠道原值为“其他”的快照补标签。完整礼品和正常 V2 不重建。旧人员证据不改旧员工编号；用户确认的正确人员写入新快照，原证据与 before-image 保留。

38 笔新增业绩记录 org_source=current；529 使用部门 1021／中心 1020，其他指定账号部门 1051／中心空。接收批次必须属于正确负责人且发生在提交前。现有 current、frozen 以及 OD202609282047360014 的空来源标记保持原样。

## 文件、依赖和证据

- 迁移：`script/sql/mysql/migrations/V305__order_history_repair.sql`，前置两张台账均为 V304。
- 执行器：`script/sql/mysql/tools/order_history_repair.py`，使用既有 PyMySQL；不新增依赖。
- 原始证据：parttimecrm-cst+0800-20260920-222103.sql.gz，SHA256 为 `bb7f6234c81647ea750ad854622478830e9b90f538a4ed02e2e53a4db60c95db`。直接解析原始备份，不执行其 SQL、不依赖生产有 ptml。
- 私有计划包含源记录、目标完整前后镜像、依赖和摘要，仅放 Git 忽略的 `backups/mysql/order-history-v305-*` 或受控私有目录，不提交、发布或在日志打印。

当前版证据包绑定目标行完整原值和内部关联 ID。相同数据的环境可直接核验；ID 或数据不同的生产环境必须先针对该环境只读 prepare，不允许绕过原值检查。源匹配使用唯一业务订单号、客资业务编号、旧商品编号及金额；生产需安全传输原始备份或已审核的对应证据材料，不能依赖旧库联机查询。

## 本地执行

```powershell
python script/sql/mysql/tools/order_history_repair.py preflight
python script/sql/mysql/tools/order_history_repair.py prepare --plan backups/mysql/order-history-v305-YYYYMMDD/plan.json
python script/sql/mysql/tools/test_order_history_repair.py --plan backups/mysql/order-history-v305-YYYYMMDD/plan.json
python script/sql/mysql/tools/order_history_repair.py apply --plan <私有计划路径> --expected-sha256 <prepare输出的摘要>
python script/sql/mysql/tools/order_history_repair.py verify --plan <私有计划路径> --expected-sha256 <同一摘要>
python script/sql/mysql/tools/order_history_repair.py recovery-check --plan <私有计划路径> --expected-sha256 <同一摘要>
```

preflight 对数据库只读；prepare 仅增加私有文件；verify／recovery-check 只读核对完整后镜像和两张版本表。apply 才进行数据库写入，默认目标为本地 Docker ruoyi-vue-pro，凭据在进程内部读取。其他目标使用 `--host --port --database --user --password-file`，密码不得放命令行。

对有目标订单的数据库，直接运行 SQL 的普通迁移入口会报“requires reviewed private evidence”，且不会登记成功。必须通过执行器完成 V305，之后正常迁移 runner 可继续。无目标订单的空环境可以直接执行 SQL并重复执行。禁止 mysql --force、跳过校验或手填成功版本。

## 事务、重放和回退

执行器持有数据库级命名锁，先完成备份列 LONGTEXT 扩容和辅助例程安装，再进入 SERIALIZABLE 事务。DDL 隐式提交，失败可能留下扩容及例程，但不会留下业务成功标记。扩容不删除数据，不通过缩回 VARCHAR 撤销。

事务内校验依赖和所有前镜像、插入目标不存在性、重复商品引用；保存完整前后镜像；按客资／机会、商品、轮次、订单、业绩顺序写入；使用 AUTO_INCREMENT 并解析关联；核对后镜像和 UTF-8 HEX；最后调用同事务台账登记。任何异常回滚全部业务修改和版本记录。

重复执行复用首次计划摘要及分配的主键，不再创建新轮次和业绩。部分完成恢复只接受整行等于首次 before 或 after；插入记录必须完整存在且与留档一致。缺少已插入行、列级混合状态、后续业务编辑、依赖改变、其他计划摘要均拒绝自动覆盖。

回退仅可从完整 after-image 状态启动；恢复旧订单关联后才移除本次插入的行。测试包含事务内回退演练并验证 before-image，随后 ROLLBACK，保留修复结果。CLI 不提供未经授权的真实删除式回退；正式撤回需针对当时引用和并发状态另行确认，保留证据、备份和版本台账。

## 验收与剩余项

隔离 MySQL 副本检查初次、重复、私有证据缺失拒绝、事务中途失败、原值冲突、部分恢复、回退、跨租户保护、锁冲突和空环境。全库摘要比较排除预期六张业务表、备份和版本台账，其他表必须不变。测试库及私有全库备份保留，不自动删除。

`OrderHistoryRepairContractTest` 通过环境变量 ZSJOS_ORDER_REPAIR_PLAN 读取私有计划，调用实际 SalesOrderSnapshot、LeadProductSnapshot、SalesOrderGiftSnapshot 和 JsonUtils；不输出业务内容。核对 320 份轮次快照和 36 份非删除商品快照的解析、金额、日期往返和未知 SKU 边界。

现场验收另需核对列表和详情读同一轮次、38 人员映射、三重复明细汇总、两缺失明细、两渠道标签、正常 V2／礼品不变，并在 Admin／Workbench 现有页面验证。数据库和 Java 证据不能替代已登录页面验收。

### 2026-10-10 本地执行结果

最终私有计划 `backups/mysql/order-history-v305-20261010/plan-final.json`，SHA256：`3622738b3c15ed7c2c5a7c355ad8c16c5daabde0703a1272377394174e799970`。本地执行 686 项行操作，立即重放 0 项；两张版本台账均登记 V305。备份共 1062 行，最长 old_value 为 76500 字符，扩容有效且未截断。普通 preflight/prepare 面向未修复目标；修复后使用同一计划执行 verify/recovery-check，不重新生成计划覆盖证据。

隔离最终副本 `v305_20261010154454` 通过首次、前置失败、事务中途失败、第二台账写入失败、直接 SQL 缺证据拒绝、重复、无关表及无关行保留、完整后镜像、带版本恢复、回退演练、跨租户拒绝、锁冲突、空目标及重复执行。故障注入只发生在隔离副本。

Java 最终 13 项测试成功，包含私有计划全部 320 个轮次快照、36 个非删除商品快照的实际读取器检查。仓库迁移结构检查通过。本地业务验收确认 392 个有效订单均为 V2，当前／最新轮次一致且 approved，38 笔人员和新增归属、8 条客资正确，全部明细合计等于订单金额，107 笔批量零金额订单保持不变。既有六张业务表的所有非目标行与私有前镜像一致。

`zsjos_cashback`、`zsjos_service_relation`、`ACT_HI_PROCINST`、`ACT_RU_TASK`、`zsjos_business_event`、`system_role_menu` 本地执行前后校验值一致。OR202609200030 的当前轮次已为 approved，订单保持 effective。

真实页面验收未完成：工作台 localhost:5174 停在登录页，无登录态；Admin localhost:80 使用页面已有保存信息登录后返回“账号密码不正确”。没有猜测密码、重置账号或改变权限。现有两端消费字段已做源码核对，不能替代登录后的实际验收。生产未执行、服务未重启、代码未提交或推送。

额外只读验收入口：

```powershell
python script/sql/mysql/tools/order_history_repair_verify.py --plan <私有计划> --before <local-before-rows.json> --output <新的私有验收文件>
```

保留的已知缺失：3 笔离职销售人员／归属、没有原始证据的标签和审批姓名、OD202609282047360014 的 org_source。生产执行、重启或发布均不包含在本地实施授权内。


## 2026-10-11 客户端重复执行修订

V305 已完成时，直接 SQL 可使用数据库中原私有计划回执、操作计数、逐行证据以及 V306～V309 的完整后续证据链核验完成并零写入返回。首次业务修复仍必须用私有执行器；不会根据版本号直接跳过，也不会自动重写旧计划摘要。最终登记过程独立检查前置条件，客户端继续执行不能把失败的准备阶段登记为成功。

仅面向本地未发布开发脚本；保留原台账、备份、业务数据和部署校验值审查边界。此节更新前文对“重复执行一律检查整行”的约定，实际写入恢复约束不变。生成方式、执行顺序与隔离验证见 [数据库迁移操作](database-migrations.md#replaying-v296v310-from-a-gui-client)。
