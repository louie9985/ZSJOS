# 已有数据库配置补齐

`script/sql/mysql/sync-existing-server-config.sql` 用于已有中世健数据库的配置补齐，不再重建角色权限。

## SQL 执行范围

脚本补齐已有审核范围内的字典类型/条目，并通过 V071 维护菜单元数据。角色菜单关系由管理员在 System 角色管理中配置；脚本不再创建授权备份、自动授予、继承、回补或撤销权限。

脚本仍包含业务字典同步，执行前须按仓库规则确认具体数据范围。脚本不写入用户、用户角色绑定、业务实例、流程实例、任务、通知或上传文件，不直接操作 Flowable 内部表。既有授权备份保留作恢复记录，不能当作当前授权来源。

从仓库根目录执行：

```text
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/sync-existing-server-config.sql
```

命令必须在仓库根目录执行，或把 `script/sql/mysql/` 一并放到 MySQL 客户端可读取的路径；脚本中的 `SOURCE` 路径由客户端解析。MySQL 客户端必须使用 `utf8mb4`。

执行前完成备份和只读检查。字典按类型和值补齐，菜单按既有 V071 条件更新；重复执行不会改变角色菜单关系。不提供自动回滚，配置恢复须按实际变更审核。

## BPM 流程资产

BPM 定义由 `yudao-module-bpm` 和 Flowable 所有，不能通过 SQL 复制 `ACT_*` 运行时/历史表。按以下步骤恢复：

1. 在仓库根目录运行 `python script/bpm/validate_manifest.py`。
2. 打开管理端“审批管理 -> 流程模型”。
3. 对 `script/bpm/manifest.json` 中每个 `recommended=true` 的资产，使用其 `path` 对应文件创建/导入模型；SIMPLE 资产使用“导入模型”。已存在同一 Process Key 的模型时打开并更新为 SIMPLE 后重新发布，不重复导入；历史 BPMN 资产仅供旧实例兼容。
4. 选择当前租户中已启用的 BPM 分类，审核候选人变量和任务 Key 后发布并启用。
5. 记录 Process Key、资产版本、SHA-256、Flowable 定义 ID/版本、部署时间和操作人。不要复制旧环境的模型管理员 ID。

当前清单包含 14 个推荐 SIMPLE 资产，其中爆款账号/内容拆解的两份资产要求支持内嵌表单导入的 BPM 后端，并在导入后配置当前租户审核角色才可发布。其他资产涵盖客资申诉、客资流转、订单双中心会签、提现、学员联系延期、反馈需求审批、新媒体流程、班级调班和 EAM 资产流转。已有流程实例和历史版本不应被删除或覆盖。退款审批 `zsjos_payment_refund_approval` 尚缺权威审批人配置，不在可发布资产内。

## 验证

SQL 执行后检查：

```sql
SELECT COUNT(*) FROM system_dict_type WHERE deleted=0;
SELECT COUNT(*) FROM system_dict_data WHERE deleted=0;
SELECT COUNT(*) FROM system_role_menu WHERE deleted=0;
SELECT code, COUNT(*) FROM system_role WHERE deleted=0 GROUP BY code;
```

BPM 验证以管理端已发布且启用的 Process Key、任务 Key、候选人配置和清单 SHA-256 为准；不要以 `ACT_RE_*` 的环境生成 ID 作为跨环境一致性依据。
