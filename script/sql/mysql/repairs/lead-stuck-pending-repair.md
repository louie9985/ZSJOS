# 客资派单卡死修复

此操作适用于本地受控开发数据库，生产/共享测试执行须另行授权。不是 fresh bootstrap 或编号升级迁移，不修改已应用文件及其 checksum。

## 问题与修改

**故障现象**：7 条客资卡在 `pending_acceptance`，既未被接单也未超时回收，销售端表现为「没分配给我、抢单池也没有、却在总客资里躺着」。

**根因**：`LeadAssignmentTimeoutScheduler` 每 5 秒执行 `processExpired()` 回收到期未接单的客资。该方法是 `@Transactional`，执行到写派单历史时调用 `PerformanceSnapshotService.activity(...)` 落绩效快照。`zsjos_performance_attribution` 的 `creator`/`updater` 为 `NOT NULL DEFAULT ''`，但 MyBatis-Plus 会因 `@TableField(fill = FieldFill.INSERT)` 把该列强制写进 INSERT 并显式绑定 `NULL`（覆盖 DDL 默认值）。定时任务线程没有 SecurityContext，`DefaultDBFieldHandler` 只在有登录用户时回填，于是 `creator` 为 `NULL` → 违反 NOT NULL → 异常冒泡 → **整个 `processExpired()` 事务回滚**，状态改动连同历史一起被撤销。

每次重试都是「改状态 → 写历史 → 崩 → 全部退回」，因此客资状态从未真正变化。9/26 单日报错 15270 次。

**连带影响**：`processExpired()` 与 `processUnassignedRetries()` 原在 scheduler 的同一个 try 块内，前半段抛异常导致**未分配重试在故障期间完全未执行**。

**代码侧修复**（须先于本脚本部署）：
1. `DefaultDBFieldHandler`：无登录上下文时回填 `creator`/`updater` 为系统操作人，单点覆盖全部 16 张 `creator NOT NULL` 表。
2. 绩效埋点改为事务提交后触发，埋点失败不再回滚派单主流程。
3. `LeadAssignmentTimeoutScheduler` 拆分为两个独立 try-catch。

**数据侧修复**（本脚本）：故障期间超时任务未能写入的 `timeout` 历史予以补录，客资状态释放回 `unassigned`，悬空的「待接客资」待办关闭。

## 已确认口径

- **不重新派单**。脚本只把客资释放为 `unassigned`；部署修复后的 scheduler 在 5 秒内按现有规则自动接管派单。这也是**代码修复必须先行**的原因——否则脚本执行后 scheduler 会再次因同一 bug 崩溃，客资二次卡死。
- **`occurred_at` 用原始 `pending_expires_at`，不用 `@repair_at`**。超时发生的时间是派单到期那一刻，历史时间轴应反映事实，而非修复时刻。
- **只处理 `dispatch_mode <> 'specified'`**。指定派单的 offer 不设过期，不属于超时回收范围。原故障数据中全部为 `auto`，该条件用于防止脚本被误用于其他场景。
- **不删除数据、不改权限、不重建归属、不臆造历史规则、不重置资质轮次**。
- 回填 `creator`/`updater` 写脚本标识 `'lead-stuck-pending-recovery'`，不写用户 ID。
- 重跑幂等：已有同期 `timeout` 记录的客资不再补录，已是 `unassigned` 的客资不再入选。

## 前置与执行顺序

需要现有 Lead/派单历史/待办任务表，且**修复后的代码已部署并确认 scheduler 不再报 `Column 'creator' cannot be null`**。

1. 确认代码已部署。备份目标客资及其派单历史、待办任务记录。
2. UTF-8 客户端连接，设置 `@repair_tenant` 和北京时间 DATETIME `@repair_at`，开启事务。
3. 执行 [修复 SQL](lead_stuck_pending_repair.sql)。**先运行并 ROLLBACK**，核对脚本末尾的验证查询：每条客资应为 `assignment_status='unassigned'`、`pending_assignee_user_id=NULL`、`timeout_rows=1`、`open_tasks=0`。
4. 同一前置状态正式执行并 COMMIT。
5. 重跑一次脚本确认幂等（临时表为空、无新增记录）。
6. 观察 5 秒内 scheduler 是否将客资重新派出，日志中确认不再出现 `Column 'creator' cannot be null`。

```sql
SET NAMES utf8mb4;
SET @repair_tenant = 1;
SET @repair_at = CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00');
```

## 本次执行记录

2026-09-27 在本地库以 `zsjos_repair_dryrun` 影子库完成 dry-run：命中 7 条客资（6544、6546、6548、6551、6556、6570、6610），验证查询全部符合预期；提交后重跑为 no-op，`timeout` 历史总数保持 7 条无重复；未设置 `@repair_tenant` 时前置断言按预期中止脚本。
