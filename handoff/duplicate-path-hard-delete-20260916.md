# 重复 path 文件的硬删除

日期：2026-09-16（接 `handoff/auto-increment-and-dangling-file-repair-20260916.md`）

用户指示：第一项**完全删除**，第二项**完整删除**（连带引用一起删），
COS 对象不动（只删数据库行）。全部为**物理 DELETE**，非软删。

## 第一项：孤立记录

`infra_file` 里 id `2 / 8 / 12 / 13` 四行——两组同 key 重复上传、**全库零引用**
（17 个 `*_file_id` 列 + 198 个 JSON 列，按 id 与按 `infraFileId`/`fileId` 两种路径都扫过）。
之前只做了软删，本轮改为物理 DELETE。

```
DELETE FROM infra_file WHERE id IN (2, 8, 12, 13);   -- 4 rows
```

## 第二项：5 组同 key 记录，连带引用一起删

COS 桶**无版本控制**，同 key 后传覆盖。5 组共 11 行，每行都被引用，
按 key 归组后引用方分两类：

| 组 | id | 引用方 |
|---|---|---|
| `lead/20260811/【哲风壁纸】…png` | 2277, 2279 | 申诉 13 + 事件 14/15 |
| `lead/20260811/logo.png` | 2281, 2283, 2285 | 申诉 14/15 + 事件 16/18 + 附件 6 |
| `lead/20260811/ScreenShot…539.png` | 2282, 2284 | 事件 17/19 |
| `lead/20260813/logo.png` | 2291, 2295 | 附件 7/11 |
| `sales-order-voucher/20260823/image.png` | 2325, 2327 | 订单 16/17/22 / 订单 19 |

**执行前提示过的风险（用户已确认接受）**：`2325` 挂的 3 笔订单（16/17/22）
是 `历史客户复购1` 的真实订单（各 999.00），其中 `OD202608261931150019` 处于
`pending_approval`。曾建议改为"把 2325 改指到 2327（同一 COS key、字节相同）"，
可零损失保留凭证；用户选择直接连带删除。

### 实际删除的行

| 表 | 行数 | 说明 |
|---|---:|---|
| `infra_file` | 11 | 5 组的全部 11 行 |
| `zsjos_order` | 4 | id 16 / 17 / 19 / 22 |
| `zsjos_order_item` | 4 | |
| `zsjos_order_approval_round` | 4 | |
| `zsjos_order_command` | 4 | |
| `zsjos_service_relation` | 1 | id 5（order 19） |
| `zsjos_lead_appeal` | 3 | id 13 / 14 / 15（客资 8 的申诉） |
| `zsjos_business_event` | 6 | id 14–19（客资 8 的申诉事件） |
| `zsjos_lead_attachment` | 3 | id 6 / 7 / 11（客资 10/13/14） |
| **合计** | **40** | |

**没动的**：`zsjos_person` 46（`历史客户复购1`，订单删了但人档保留）、
客资 8/10/13/14 本身、COS 对象、相邻订单 18/20/21/23/24。

### 快照（可回滚）

- 库 `zsjos_order_cleanup_backup_20260916`：11 张表，逐表快照上述所有行
- 文件 `backups/zsjos-order-cleanup-snapshot-20260916.sql`（84 KB）
- 删除前全量 `backups/zsjos-before-hard-delete-20260916-223755.sql`
  （sha256 `21822b5759cb21898c7623b72506cf91511bd6005150569aba9f2e7ff066c384`）

## 执行后验证

| 检查 | 结果 |
|---|---|
| 列式悬空引用（12 张表） | 0 |
| JSON 悬空引用（25 个列） | 0 |
| 有效行重复 `path` | 0 |
| `AUTO_INCREMENT <= MAX(id)` | 0 |
| `order_item → order` / `service_relation → order` / `appeal → lead` | 0 悬空 |
| 相邻订单 18/20/21/23/24 | 未受影响 |
| 后端 `zsjos-backend.service` / `/actuator/health` | active / 200 |

行数变化：`infra_file` 2285 → 2274，`zsjos_order` 390 → 386，
`zsjos_lead_attachment` 2002 → 1999，`zsjos_lead_appeal` 8 → 5，
`zsjos_business_event` 430 → 424。

## 遗留

- ~~`zsjos_person` 46 孤立人档~~ —— 已按用户指示删除，见下节。
- COS 上 `sales-order-voucher/20260823/image.png` 这个 key 现在没有任何
  数据库行引用它了（对象本身保留）。
- `V255` 与 `verify-bootstrap.sql` 仍未提交（按要求不推送、不提交）。

---

# 追加：孤立人档删除

删完订单后 `zsjos_person` 46（`历史客户复购1`）成了孤立人档。用户指示删除。

**删除前全库扫描 31 个 person 引用列**，除一条 `zsjos_person_contact_claim`
（`contact_value='lishikehufugou1'`，claim 是归属登记，随人档一起走）外全部为 0。

| 表 | 行 |
|---|---:|
| `zsjos_person_contact_claim` | 1（id 55） |
| `zsjos_person` | 1（id 46） |

快照：`zsjos_order_cleanup_backup_20260916.zsjos_person_46` /
`zsjos_person_contact_claim_46`，并已重导
`backups/zsjos-order-cleanup-snapshot-20260916.sql`。

## 顺带发现（未处理）

`zsjos_order.id=24`（`OR202606040001`，terminated，creator=`migration`）
的 `person_id=0`。"人档编号 0" 不存在，属于**哨兵值**，全库仅此一例
（其它表的 `person_id=0` 均为 0 条）——不是本次删除造成的，未动。

## person 侧最终验证

| 检查 | 结果 |
|---|---|
| 31 个 person 引用列扫描 | 全 0 |
| `lead → person` 悬空 | 0 |
| `service_relation → person` 悬空 | 0 |
| `order → person` 悬空 | 1（`person_id=0` 哨兵，非真实悬空） |
| 后端 | active，`/actuator/health` 200 |

