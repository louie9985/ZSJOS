# 客资三级申诉发布说明

申诉流程定义文件为 `script/bpm/zsjos_lead_appeal_review.bpmn20.xml`，流程 Key 固定为 `zsjos_lead_appeal_review`。BPMN 必须由管理员在 BPM 管理页面受控导入、发布，不启用全局自动部署。

执行顺序：

1. 导入并发布 BPMN，确认流程 Key 和多人或签（任一人通过或驳回即结束）配置正确。
2. 在受控环境依次执行增量迁移 `script/sql/mysql/migrations/V015__lead_three_level_appeal.sql` 和 `script/sql/mysql/migrations/V016__complete_lead_notify_templates.sql`，再运行 `script/sql/mysql/verify-bootstrap.sql`。
3. 为每个租户配置恰好一名启用的 `boss` 角色用户；迁移只在角色编码缺失时创建角色，不自动分配账号。

迁移只增加申诉字段、索引、状态字典、菜单和授权，不删除或重写业务数据。已存在申诉历史的环境中，幂等键列允许历史空值，MySQL 唯一索引仍允许多条空值；后续请求必须携带非空幂等键。若流程定义缺失，申诉提交事务整体回滚并返回可识别错误。回滚采用禁用菜单和流程定义、保留申诉与业务事件审计数据，不通过删列恢复。

申诉无时限、无自动升级、最多三轮；第二和第三轮均由提交人手动重新提交。
