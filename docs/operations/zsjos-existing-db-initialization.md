# 已有数据库配置补齐

`script/sql/mysql/sync-existing-server-config.sql` 用于已有 ZSJOS 数据库的非破坏性补齐。

## SQL 执行范围

脚本只执行以下操作：

- 补齐 Core、ZSJOS、BPM 字典类型和仓库已审核的具体字典条目；
- 按稳定的 `system_role.code` 与 `system_menu.permission` 补齐 V071 声明的角色授权；
- 已存在的字典、角色、菜单和授权关系不更新、不删除、不禁用。

脚本不会写入用户、业务实例、流程实例、任务、通知、上传文件或其他环境数据，也不会直接操作 Flowable 内部表。`zsjos_lead_category` 和 `zsjos_lead_source_channel` 只创建类型，不预置业务选项。

从仓库根目录执行：

```text
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/sync-existing-server-config.sql
```

执行前应完成备份和只读检查；脚本可重复执行，重复执行不会产生新的有效字典条目或角色菜单关系。

## BPM 流程资产

BPM 定义由 `yudao-module-bpm` 和 Flowable 所有，不能通过 SQL 复制 `ACT_*` 运行时/历史表。按以下步骤恢复：

1. 在仓库根目录运行 `python script/bpm/validate_manifest.py`。
2. 打开管理端“审批管理 -> 流程模型”。
3. 对 `script/bpm/manifest.json` 中每个 `recommended=true` 的资产，使用其 `path` 对应文件创建/导入模型；Simple 资产使用“导入模型”，BPMN 资产在流程设计器中使用“打开文件”。
4. 选择当前租户中已启用的 BPM 分类，审核候选人变量和任务 Key 后发布并启用。
5. 记录 Process Key、资产版本、SHA-256、Flowable 定义 ID/版本、部署时间和操作人。不要复制旧环境的模型管理员 ID。

当前清单包含 10 个推荐资产，涵盖客资申诉、客资流转、订单双中心会签、提现、学员联系延期、反馈需求审批、新媒体流程和 EAM 资产流转。已有流程实例和历史版本不应被删除或覆盖。

## 验证

SQL 执行后检查：

```sql
SELECT COUNT(*) FROM system_dict_type WHERE deleted=0;
SELECT COUNT(*) FROM system_dict_data WHERE deleted=0;
SELECT COUNT(*) FROM system_role_menu WHERE deleted=0;
SELECT code, COUNT(*) FROM system_role WHERE deleted=0 GROUP BY code;
```

BPM 验证以管理端已发布且启用的 Process Key、任务 Key、候选人配置和清单 SHA-256 为准；不要以 `ACT_RE_*` 的环境生成 ID 作为跨环境一致性依据。
