# 备份增量合并（新库备份 -> zsjos）

日期：2026-09-16
源：`/opt/zsjos-runtime/backups/ruoyi-vue-pro_20260916_193916.sql`（436 MB，数据库名 `ruoyi-vue-pro`，511 张表）
目标：本机 `zsjos`
脚本：`script/sql/merge/merge_backup_incremental_20260916.py`

## 做法

备份先恢复到只读暂存库 `zsjos_merge_stage`（把 dump 里的 `` `ruoyi-vue-pro` `` 全量改写成 `zsjos_merge_stage`），
再在目标库克隆 `zsjos_merge_rehearsal` 上整轮演练，通过后才对生产库执行。

全流程只生成 SQL、由 mysql 执行；单个 `START TRANSACTION ... COMMIT`，
全部写入都是 `INSERT ... SELECT ... ON DUPLICATE KEY UPDATE id=id`（等价 insert-if-absent），
可在任何阶段重复执行而不产生新行、不改动已存在的同键行。

**不做的事**：不迁移旧申诉；不碰账户/角色/权限/菜单/字典；不写审计日志
（`zsjos_business_audit_log` 目标侧比备份多 2,509 行，是备份后仍持续写入的运行数据，按约定不合并）；
不触发审批、通知、待办；不覆盖目标库本批范围外的任何行。

## 关键判断：主键无需重映射

结构相同不等于主键一致，所以逐表核对过：

| 表 | 源/目标行数 | id 交集 | lead_no/order_no 交集 | 只在一侧 |
|---|---:|---:|---:|---:|
| `zsjos_lead` | 4,427 / 4,427 | 4,427 | 4,427 | 0 |
| `zsjos_order` | 390 / 390 | 390 | 390 | 0 |
| 其余本批表 | 交集行内容逐字段相同 | — | — | 0 |

两边 id 段不相交，新增行沿用备份原 id 即可，**没有发生 ID 重映射**，因此
JSON 内嵌的 `infraFileId` 与外键都无需翻译。

## 实际落库

| 表 | 新增 | 备注 |
|---|---:|---|
| `infra_file` | 2,223 | `legacy-crm/*` 全部遗留文件，`config_id` 一律改写为目标环境有效配置 |
| `zsjos_lead_attachment` | 1,986 | `creator=legacy-attachment-backfill`，id 14,646–16,631 |
| `zsjos_business_event` | 188 | 170 lead + 18 feedback，`creator=legacy-history-20260915` |
| `zsjos_feedback` | 8 | |
| `zsjos_work_order` | 8 | |
| `zsjos_feedback_reply` | 18 | |
| `zsjos_lead_submitter_assist_request` | 19 | |
| `zsjos_lead_complaint` | 2 | |
| **更新** `zsjos_lead.invalid_evidence_refs` | 52 行 | 按 `tenant_id`+`lead_no` 定位，只写该列 |
| **更新** `zsjos_order.payment_voucher_refs` | 213 行 | 旧 `/media/uploads/...` → 备份中的 COS 引用+元数据 |

## 文件引用闭环（逐条验证，0 悬空）

`infra_file` 本批 2,223 行 = 1,986 附件引用 ∪ 176 订单凭证引用 ∪ 61 条客资
`invalid_evidence_refs` 引用。三者合起来刚好覆盖全部，没有多余、没有缺失。

- 1,986 条附件的 `infra_file_id` 全部可解析；`file_url` 为 COS 地址、无过期内签名
- 其中 63 个文件（id 2180–2242）不被附件引用，但**必须一起迁**——它们被
  `invalid_evidence_refs` 引用，漏掉会让那 52 条客资的凭证指向不存在的文件
- 目标环境 `infra_file_config` 只有 id=1 且与备份同配置（JSON sha256 相同），沿用之
- 实测 COS 对象可直接访问：`HEAD` 200、Range 206，抽样 7 个全通

## 校验结果

- 关联完整性 15 项：唯二"命中"是**目标库本就存在**的 16 条悬空附件与 2 组重复文件路径（非本次引入）
- 幂等键全家桶重复检查：事件/协助/投诉/工单/回复/lead_no/order_no 全为 0
- 合并结果与源库逐字段字节级比对：`0` 差异（含中文，无乱码）
- 演练库（生产库克隆）与生产库结果一致

## 踩到的坑

**`AUTO_INCREMENT` 计数器不会因显式 id 插入而前移。** 合并后目标库
`zsjos_lead_attachment` 的计数器仍是 5、`infra_file` 仍是 20，而实际 max(id)
已到 16,631 / 2,242——继续写入会直接撞车。脚本里的自增兜底查询本身也不可靠：
`information_schema.tables` 的统计对该库是缓存的（`SHOW TABLE STATUS` 仍报 4 行、
update_time 停在 2026-09-05）。必须显式 `ALTER TABLE ... AUTO_INCREMENT = max(id)+1`，
并用 `SET SESSION information_schema_stats_expiry=0` 复核。已在生产库执行：

```
zsjos_lead_attachment 16632   infra_file 2243
zsjos_business_event   823     zsjos_work_order 78
zsjos_feedback          77     zsjos_feedback_reply 106
zsjos_lead_submitter_assist_request 54   zsjos_lead_complaint 7
```

（注意：`SHOW TABLE STATUS` / 默认 `information_schema` 读出来的仍是旧值，是缓存不是真相。）

## 回滚

- 合并前全量备份：`/opt/zsjos-runtime/backups/zsjos-before-incremental-merge-20260916-205832.sql`
  sha256 `ccef2e64615a754cf8f2fe765efa605b63ae36a7c13e3ba2a3e577d890171e82`
- 回滚即恢复该备份；或按本文件"实际落库"表逐表删除新增行、把两列改回。
- 暂存库 `zsjos_merge_stage` 与演练库 `zsjos_merge_rehearsal` 已按约定删除，
  需重放可用上面的源备份重建。

## 未了事项（非本次范围）

- 目标库 16 条附件指向不存在的 `infra_file` id（2261–2357 段，来自更早的批次），
  与 2 组重复 `infra_file.path`，本次未处理
- `zsjos_lead.id=8`（KZ 客资）的 `invalid_evidence_refs` 指向 id 2268，同样悬空
- `script/sql/mysql/migrations/V255__repair_media_screen_and_duplicate_accounts.sql`
  仍是未提交状态（已应用）
