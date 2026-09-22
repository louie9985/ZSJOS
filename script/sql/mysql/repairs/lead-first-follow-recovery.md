# 迁移客资首跟与判定修复

此操作适用于本地受控开发数据库，生产/共享测试执行须另行授权。不是 fresh bootstrap 或编号升级迁移，不修改已应用文件及其 checksum。

## 问题与修改

- 代码：原实现只有首跟任务从 pending 更新成功才设置首跟字段；迁移缺任务导致跟进保存成功而首跟永远不完成。改为在现有 Lead 行锁内按当前周期/本人首条记录确认事实，任务完成为后续同步，不作为条件。原状态投影还用判定期限推断首跟，现改为读取首跟事实，保留主管跳过新首跟的例外。
- `zsjos_lead`：恢复缺失的 `current_assignment_first_follow_up_at`；不填造 `ownership_started_at`。完全缺失的判定轮次填写 `qualification_round_no`、`qualification_started_at`、`qualification_deadline_at`、`qualification_rule_snapshot`。
- `zsjos_lead_follow_up_record`：本周期最早本人记录的 `first_in_assignment=1`。记录内容、发生时间不改；其他周期和其他人的记录不当作证据。
- `zsjos_business_task`：同步完成已有 pending 首跟任务；缺失首跟任务按已知事实补 completed（不编造历史 due_at）。无首跟证据且整轮缺失时，按修复时刻补 pending 首跟任务，填主表首跟截止时间。新判定任务与轮次、负责人、期限一致。
- `zsjos_business_event`：追加本次真实修复时刻的 `lead_qualification_started` 事件，refs 标明修复来源，不伪称历史动作。
- `zsjos_performance_attribution`：新判定任务按修复时当前 System 用户/部门及配置组织映射保存 QUALIFICATION 快照，缺失的历史归属时间仍为空。

## 已确认口径

缺失判定轮次从修复时刻按当前启用规则重新计时；现有完整轮次不重置。当前规则判定为 4320 分钟（72 小时）、首跟为 30 分钟。脚本动态读取规则和版本，不把这些数值硬编码为业务规则。

## 前置与执行顺序

需要现有 Lead/跟进/任务/事件表、当前租户 default 启用跟进规则、当前 performance attribution/org 表及 System 用户/部门来源。不删除数据，不改变角色菜单、权限、责任人、主状态、历史归属时间或跟进计数。

1. 明确数据库和租户，预查缺失范围、规则、部分残缺轮次；备份目标 Lead 及其跟进/任务/事件/绩效记录。
2. UTF-8 客户端连接，设置 `@repair_tenant` 和北京时间 DATETIME `@repair_at`，开始事务。
3. 执行 [修复 SQL](lead_first_follow_recovery.sql)。先运行并 ROLLBACK；核对首跟事实、任务/事件/绩效关联、期限、未涉及记录、中文 HEX。
4. 同一前置状态正式执行并 COMMIT；重新执行应为 0/0/0，不延长期限。

```sql
SET NAMES utf8mb4;
SET @repair_tenant = 1;
SET @repair_at = CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00');
START TRANSACTION;
SOURCE script/sql/mysql/repairs/lead_first_follow_recovery.sql;
-- 核查结果后在同一会话选择 COMMIT 或 ROLLBACK。
ROLLBACK;
```

脚本在派生证据前锁定范围内 Lead，沿用应用的 Lead-first 锁顺序。仅自动处理 active submitted+owned、已有归属周期的客资；判定只处理 round=0/NULL 且开始、截止、快照、任务全缺失的情况。部分残缺或逻辑删除任务占用相同幂等键需另行审计，不覆盖。首跟完成事实无需伪造旧规则。脚本不自动提交，不应通过会继续忽略 SQL 错误的客户端执行。

## 验证记录：2026-09-23

- local，租户 1；先事务预演并回滚，再正式执行：恢复首跟事实 118 条，补待处理首跟任务 1 条，新建判定轮次/任务/事件/绩效快照 119 条。
- 修复时刻 2026-09-23 00:48:27，判定期限 2026-09-26 00:48:27；重复执行 0/0/0。
- 73 个已有判定轮次的首跟/判定时间、轮次、规则快照逐行对比不变；目标首跟与原记录、任务截止和负责人核对无差异。
- 当前归属周期已有本人跟进但首跟为空的 active owned 客资剩余 0，缺判定截止时间剩余 0。
- `LD202609170026` 首跟恢复为 2026-09-18 17:57:49；`LD202609200031` 原判定截止 2026-09-23 14:23:22 保持不变。
- 中文标题通过 UTF-8 查询及 HEX 检查：`有效性判定：` = `E69C89E69588E680A7E588A4E5AE9AEFBC9A`。
- 备份位于执行机器 `C:/Users/EDY/AppData/Local/Temp/zsjos-lead-first-recovery-20260923/`，不提交其业务数据到仓库。

## 回滚限制

提交前可完整回滚业务写入。提交后不能整体覆盖备份：必须先确认目标在修复后没有跟进、判定、派单等新业务动作，按 before-image 恢复原字段，逐项识别本次新增记录再制定补偿；删除新增任务/事件需单独授权。不要直接重放备份 INSERT，因为主键仍存在。

代码需后端重新加载生效；数据修复不替代部署代码。没有真实销售账号接口验收证据时，不声称已验证运行中页面的权限按钮。
