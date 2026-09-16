# 自增计数器与悬空文件引用的修复

日期：2026-09-16（接 `handoff/backup-incremental-merge-20260916.md`）
脚本：
- `script/sql/merge/repair_dangling_infra_file_20260916.py`
- 计数器修复 SQL（见下）

## 一、AUTO_INCREMENT 全面失配（已修）

### 现象

增量合并用显式 id 插入后，目标表的自增计数器**没有前移**。全库扫描发现
**94 张表**的 `AUTO_INCREMENT <= MAX(id)` —— 不修的话下一次普通写入就会撞主键。

### 更麻烦的地方

`information_schema.tables` 的统计对这个库是**缓存的**，`SHOW TABLE STATUS`
同样不可信。合并后它仍然报 `Rows=4`、`update_time` 停在 2026-09-05，
`auto_increment` 也是旧值 —— 照它查会得出"没问题"的错误结论。

**必须** `SET SESSION information_schema_stats_expiry=0` 之后读，才是当前值。

### 修法

对 452 张有 `id` 的表逐张比对 `SHOW TABLE STATUS` 的 `Auto_increment` 与
`SELECT MAX(id)`，凡 `AUTO_INCREMENT <= MAX(id)` 的置为 `MAX(id)+1`。
共修 93 张（脚本输出见 `/tmp/zsjos-merge/ai_fix_final.sql`）。

两个"看起来异常、实际健康"的表，**故意不动**：

| 表 | 情况 | 结论 |
|---|---|---|
| `zsjos_business_audit_log` | `MAX(id)` = 5.5e13 | 低段是导入的 64.7 万行（id 1..647354），运行期由 `INSERT` 带 NULL id 走 InnoDB 自增，计数器一路正确跟到 5.5e13 |
| `zsjos_media_screen_daily_snapshot` | `MAX(id)` = 3,225,687 | 同上，`MediaScreenDailySnapshotMapper.insertIgnore` 用 `INSERT IGNORE` 不带 id |

这两个表的 `gap` 看着吓人，是因为**两段 id 空间**（导入段 + 运行段），
计数器始终高于 `MAX(id)`，没有风险。**不要**把它们"修"下去——
那会把计数器降到运行段之下，反而制造碰撞。

**踩坑：** 通过 `docker exec ... sh -c "..."` 传含反引号的 SQL 会被外层 shell 吃掉，
报 `Unknown command '\'`。要么 `docker cp` 进去再 `mysql < file`，要么用单引号包整条。

### 验证

452 张表全部满足 `AUTO_INCREMENT > MAX(id)`，`TOTAL SUSPECT: 0`。

## 二、悬空文件引用的成因（已修）

### 原来有多少

合并后第一轮只发现 16 条（都在 `zsjos_lead_attachment`）。用 `infra_file_id` /
`file_id` 列 + 所有内嵌 `infraFileId` 的 JSON 列全库扫描后，实际是 **47 处引用、
覆盖 47 个不同的 infra_file id**，分布在：

| 引用位置 | 处数 | 备注 |
|---|---:|---|
| `zsjos_lead_attachment.infra_file_id` | 16 | |
| `zsjos_lead_follow_up_image.infra_file_id` | 5 | |
| `zsjos_order.payment_voucher_refs`（JSON） | 19 | |
| `zsjos_business_event.evidence_refs`（JSON） | 8 | 事件本身是本次新导入的 |
| `zsjos_lead_appeal.evidence_refs` / `decision_evidence_refs`（JSON） | 8 | 3 条申诉 × 两列 |
| `zsjos_opportunity_follow_up_image` | 1 | |
| `zsjos_registration_item_attachment` | 1 | |
| `zsjos_lead.invalid_evidence_refs`（JSON） | 1 | |

### 成因：不是导入漏了，是**源头数据库在这两次备份之间把行删了**

对照两份备份：

| 备份 | `infra_file` 行数 | 覆盖的 id |
|---|---:|---|
| `ruoyi-vue-pro-after-legacy-20260915-184129.sql`（09-15 18:41） | 105 | **2260–2364，连续无缺号** |
| `ruoyi-vue-pro_20260916_193916.sql`（09-16 19:39） | 2,242 | 1–2242，**2260 以上整段消失** |

09-15 那份里 2260–2364 是完整的 105 行；到 09-16 这份时，同一批 id
**一个都不剩**。而且 id 段整体下移了 —— 说明源端不是"删了几行"，
而是把 `infra_file` 清空后用新的 id 序列重新灌了一批。
引用它们的业务行（客资附件、申诉证据、订单凭证）没跟着清，
就留下了指向旧 id 的悬空引用。

换句话说：**引用没错，是 infra_file 被重建过。** 这也解释了为什么
`zsjos_kz_backup_20260916`（KZ 清理快照）里根本没有 `infra_file` —— 那个快照
只存业务表。

### 修法：从 09-15 的备份回填这 47 行记录

对象存储没丢——47 个对象里 **47 个在 COS 上 HEAD 200 可访问**
（回填后逐个验过）。所以只要把数据库记录补回来，链路就通了。

- 行内容取自 09-15 备份（该批行的权威快照），**不是构造的**
- `config_id` 一律改写为目标库启用中的配置（1）
- 全部 `deleted=b'0'`（备份原值）
- 幂等：`INSERT ... SELECT ... WHERE NOT EXISTS`，重跑无副作用

`infra_file` 2,242 → **2,289** 行（+47）。

### 验证

- 列式引用扫描 12 张表：**全 0**
- JSON 引用扫描 25 个列：**全 0**
- `infra_file` 里 `config_id` 全为 1（目标有效），无 `deleted=1`
- 抽查中文文件名（`【哲风壁纸】冬季场景-卡通.png`、`企业微信截图_…`）无乱码
- 47 个对象 COS `HEAD` 全 200

## 三、其他顺带核对过、确认没问题的

- `zsjos_lead_attachment → lead`、`order → lead`、`order_item → order`、
  `cashback → lead`、`follow_up_record → lead`、`lead → person`、
  `partner_account → partner`：**全部 0 悬空**
- 幂等键重复（事件/协助/投诉/工单/回复/lead_no/order_no）：**全 0**
- `zsjos_lead.id=8` 的 `invalid_evidence_refs` 指向 2268 —— 属于上面那批，已回填

## 四、遗留（未处理，需要你定）

1. **`infra_file` 有 2 组重复 `path`**：
   - `zsjos/lead/admin/20260904/image.png` → id 2 与 8
   - `zsjos/lead/admin/20260905/大健康+AI训练营.jpeg` → id 12 与 13
   都是 09-04/09-05 测试期重复上传留下的，各自被不同业务行引用，
   合并它们要动引用方，本轮没碰。
2. **id 2261 在 COS 上是 403**（`【哲风壁纸】冬季场景-卡通.png` 于
   `zsjos/lead/20260808/`）。记录已回填，但该对象当前不可匿名读取 ——
   要么对象权限本来就是私有，要么该 key 被单独设过 ACL。其余 46 个都正常。
3. 首次合并时**没发现**后 31 处悬空，是因为当时只查了
   `zsjos_lead_attachment` 一张表的 `infra_file_id` 列。教训：**查悬空要连
   JSON 列一起扫**，`*_refs` / `*_ids_json` 这类列名里都藏着文件 id。

## 备份

| 内容 | 位置 | sha256 |
|---|---|---|
| 计数器修复前 | `backups/zsjos-before-ai-fix-20260916-212056.sql` | `ecb43938…f120ee44` |
| 第一轮回填前 | `backups/zsjos-before-dangling-repair-20260916-212925.sql` | `5ae5c965…559e0a165` |
| 第二轮回填前 | `backups/zsjos-before-json-repair-20260916-213258.sql` | `35de503e…ea8c97aa` |

后端全程未停服，修复后 `systemctl is-active zsjos-backend.service` = active，
`/actuator/health` = 200。
