# 历史导入客资成交状态与销售机会补齐（V304）

## 业务范围与来源

仅租户 1；按生效首购订单的 `creator` 精确限定 `legacy-migration`、
`legacy-increment-20260917`、`legacy-increment-20260921`。客资原状态必须为
`valid` 或 `converted`。候选及其恢复清单以业务编号展示，内部主键只用于关联和备份。

2026-10-10 本地预检发现 40 条：26 条 valid、14 条 converted。26 个已有首购机会中，
25 个来自标记 `backfill-valid-missing-opportunity` 的历史补数，1 个来自实际判有效事件。
另外 14 条缺失机会。本次用户明确批准按最终状态补建，并为对应生效订单关联机会。
各环境按条件识别，不把 40/26/14 写成强制常量。

关联订单必须唯一、未删除、状态 effective、类型 first_purchase、有生效时间，且与客资
指向同一客户。已有的受批准订单生效决定保持不变；残留历史取消时间不单独推翻 effective。
多单、不同客户、待审批/待重提或 pending 轮次、活动公海、机会冲突均阻止全部业务写入。

## 修改与保留

- 客资：只改 status=won、version、updater、update_time。保留 qualified_at、converted_at、
  last_activity_at、负责人、所有快照、历史事件与跟进时间。
- 已有机会：open/following 且 won_at 为空时改 won，won_at 使用订单 effective_at，更新版本
  与修改审计。原负责人和跟进时间保留。机会已经 won 仅在本次修复留档吻合时作为恢复/重放接受。
- 缺失机会：type=initial_conversion、status=won、version=0。person/lead/owner 取当前客资，
  won_at 取真实订单生效时间；next_follow_up_at 取客资当前值；产品摘要按 V019 结构从既有
  意向产品快照生成，没有快照则空数组。creator=V304-legacy-deal-backfill，创建时间为实际迁移时间，
  不冒充历史创建。缺负责人/客户，已有逻辑删除机会或同客户其他活动机会时不擅自补建。
- 生效首购订单：仅补空 opportunity_id 及版本、修改审计；正确关联不变，其他关联拒绝。
  旧单、复购、审批轮次及其冻结快照均不补关联。
- 16 条本地待处理提醒全部晚于成交，必须保留。没有任务取消、再次判有效、审批/通知重放，
  不改变金额、财务、返现、业绩归属和已有审批历史。

## 执行入口与前置条件

在仓库根目录执行（Python 使用现有本地 Docker MySQL 配置，凭证不进入输出）：

```powershell
python script/sql/mysql/tools/legacy_lead_deal_reconcile.py preflight
python script/sql/mysql/tools/test_legacy_lead_deal_reconcile.py
python script/sql/mysql/tools/test_legacy_lead_deal_reconcile.py --suite clone
python script/sql/mysql/tools/legacy_lead_deal_reconcile.py apply-local
python script/sql/mysql/tools/legacy_lead_deal_reconcile.py recovery-check
```

`preflight` 和 `recovery-check` 仅执行 SELECT。`--suite clone` 只从开发库导出，迁移运行在
新建的隔离副本；测试库保留，不自动删除。`apply-local` 严格限定现有本地 ruoyi-vue-pro，
先预检、备份、再执行 SQL 并逐字段核对；输出只含业务编号、统计、摘要哈希和备份位置。
备份及核验报告存入 Git 忽略的 `backups/mysql/v304-*`，包含业务数据，不能发布或加入 Git。

SQL 文件：`script/sql/mysql/migrations/V304__legacy_lead_deal_state_reconcile.sql`。
须在 Core V303 双版本台账齐全之后运行；沿用现有迁移发现机制，不修改历史迁移或版本校验值。
远程环境需另行授权并使用其既有执行入口，设置 utf8mb4，必须遇错停止，禁止 mysql --force。
前置还包括现有相关业务表和 InnoDB 引擎。备份表缺失时按项目既有结构非破坏创建。
这是局部历史数据修复，不改基线、权限、字典或前后端接口；不要求应用重启。

数据库级命名锁覆盖例程安装、执行及移除。SQL 在 SERIALIZABLE 事务内读取、锁定并复核目标。
备份、插入、更新、后置检查和双台账登记同事务提交；异常 ROLLBACK/RESIGNAL。备份表和例程
DDL 不属于该事务，失败可能留下空备份表或辅助例程，不能宣称 DDL 被回滚；重新执行会安装例程。
失败客户端断开时释放命名锁，强制继续执行不属于支持的部署方式。

## 幂等、部分恢复与回退限制

固定修复标记为 `legacy-deal-state-v304`，记录在已有 `zsjos_data_repair_backup`：

- Lead `__target__`：原状态、原机会编号、客户、依据订单、生效时间和批次，作为恢复清单。
- 各表 `__before__` / `__after__`：本次涉及的字段、版本和修改审计，不覆盖首次原值。
- 新机会 `__inserted__`：新机会编号、来源客资、依据订单、创建标记和创建时间；无伪造 before。

即使 Lead 已经 won 或 V304 已登记，显式重放 SQL 仍按留档检查实际结构和状态。已有记录只接受
before 或 after 完整匹配；新机会只接受 after 匹配。恢复时复用首次 after 中的版本和时间，
不重复递增或另造新记录。原机会缺失、替换、重复备份、后续修改等情况失败，留待审查。
版本感知的常规 runner 可能跳过已登记版本，因此异常恢复必须显式执行经审查的同一份 SQL，
不可删除版本记录来强迫重跑，也不可跳过实际状态检查。

回退首先运行 `recovery-check`。任一 `matchesAfterImage=false` 都应停止，不能覆盖后续业务。
该命令只给出核验结果，不自动恢复、不自动删除新机会。核验结果不是未来时点的写授权；真正恢复
必须在同一事务中锁定原记录、重新比较 after 的版本及全部字段，再按 before 恢复已有行。
反向顺序为订单关联、已有机会、客资；每条更新保留 tenant_id=1 和修复清单约束。任一比较或行数
不符均回滚。不得把整表 before.sql 灌回开发库覆盖并行业务。

新增机会的完整回退必须单独审查后续订单、机会跟进等引用及其创建/版本标记，并明确授权删除影响。
尚未审查时保留新增机会，不执行会造成客资/机会/订单再次矛盾的部分回退。已经被正常业务使用的新
机会不能作为“临时数据”删除。保留修复备份和双台账；撤销版本和重新迁移属于另行审查的恢复步骤。

## 验证边界

专项测试覆盖三批次、已有/缺失机会、订单缺正式负责人、保持机会当前负责人、原产品中文快照、
成交后提醒、已正确关联、空候选、重复和并发运行；拒绝客户/负责人/时间缺失、不同订单关联、
多有效首购、机会流失/时间冲突/逻辑删除/同客户占用、活动审批、公海和前置版本缺失。
注入机会插入及第二版本台账失败，验证业务和备份全部回滚、失败不登记成功；解除故障后恢复成功。
带已登记版本的 Lead、机会、订单部分恢复及后续用户修改防护均在实际 MySQL 验证。

开发库隔离副本逐行逐字段比较全部 15 张涉及/受保护表：只有授权字段、新增机会、修复备份和
V304 版本行允许变化。已有中文快照通过 UTF-8 客户端和 HEX 核对。
本次不修改迁移顺序或基线，专项验证不冒充完整 fresh/upgrade 全链发布验收。

执行结果与真实页面验收见本工作流交付记录：
`handoff/local-legacy-lead-deal-reconcile-20261010.md`。


## 2026-10-11 客户端重复执行修订

V304 区分完成核验与恢复：完整修复证据和本迁移负责的状态／成交时间／机会关联一致时，保留后续版本、审计及无关字段并零写入返回。实际业务字段不一致时仍使用原完整 before/after 校验，不能借完成分支绕过部分恢复保护。

仅面向本地未发布开发脚本；保留原台账、备份、业务数据和部署校验值审查边界。此节更新前文对“重复执行一律检查整行”的约定，实际写入恢复约束不变。生成方式、执行顺序与隔离验证见 [数据库迁移操作](database-migrations.md#replaying-v296v310-from-a-gui-client)。
