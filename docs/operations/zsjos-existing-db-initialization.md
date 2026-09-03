# 已有数据库配置补齐

`script/sql/mysql/sync-existing-server-config.sql` 用于已有 ZSJOS 数据库的非破坏性补齐和角色权限重建。

## SQL 执行范围

脚本只执行以下操作：

- 补齐 Core、ZSJOS、BPM 字典类型和仓库已审核的具体字典条目；
- 先把租户 `1、121、122` 的原始 `system_role_menu` 行复制到
  `zsjos_role_menu_backup_20260904`（按行 ID 幂等）；
- 按 V071 精确白名单重建兼职、财务、申诉、老板和系统管理员的 ZSJOS 授权；
- 普通用户重建为通用能力白名单，岗位角色只保留其已审核的业务权限，并清理已确认的越权授权；
- 旧关系只做 `deleted=1` 逻辑删除，不删除用户、角色、菜单、字典或业务数据。

脚本不会写入用户、用户角色绑定、业务实例、流程实例、任务、通知、上传文件或其他环境数据，也不会直接操作 Flowable 内部表。脚本会按当前已审核快照补齐 `zsjos_lead_category`（5 条）和 `zsjos_lead_source_channel`（5 条），按类型和值幂等执行，不覆盖管理员已有修改。备份表是本地恢复依据，不应跨环境直接复制。

从仓库根目录执行：

```text
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/sync-existing-server-config.sql
```

命令必须在仓库根目录执行，或把 `script/sql/mysql/` 一并放到 MySQL 客户端可读取的路径；脚本中的 `SOURCE` 路径由客户端解析。MySQL 客户端必须使用 `utf8mb4`。

执行前应完成备份和只读检查。脚本可重复执行：旧关系保持逻辑删除、canonical permission 保证同一租户/角色/权限最多一条有效关系。恢复限制为同一数据库中按备份行 ID 的人工审核前向操作，脚本不提供自动回滚。

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
