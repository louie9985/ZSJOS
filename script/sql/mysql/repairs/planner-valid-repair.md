# 学习规划师已首跟客资统一判有效（UTF-8）

本文件记录 2026-09-23 用户明确批准的一次性运维修复，不改变正常接口的分类校验或状态机，不加入 bootstrap 或编号迁移。

## 范围和业务例外

目标是环境标识 `test`、数据库 `zsjos`、租户 1，System 学习规划师岗位与角色均确认的五位在职用户当前负责、当前归属周期已完成本人首跟的 64 条客资：63 条 `submitted + owned` 和业务编号 `LD202609200016` 的 1 条 `invalid + owned`。实际内部主键及完整原始值冻结在仓库外受限备份中；不是每次动态扫描后扩张范围。

用户确认全部改为有效，包含推翻上述无效判定。原待判定中的 8 条及原无效中的 1 条，共 9 条缺失分类原样保留，不凭空补分类，不更改字典。系统运维操作没有可验证的 ADMIN 操作人，`qualified_by_user_id` 与业务事件操作人留空，审计明确记系统运维修复，不冒充负责人。其他已有效、已成交、未首跟客资不改。另经用户单独确认，LD202609150049 原有已生效首次订单必须保留：该客资校正为 won（对外判定有效），新建 won 机会并补齐原订单 opportunity_id。订单金额、审批、生效时间不变。

## 依赖、执行和影响

执行器为 [repair_planner_valid.py](../tools/repair_planner_valid.py)，使用已安装的 PyMySQL、Docker 和当前 MySQL secret，不新增依赖、不输出凭据。必须从原始前置状态执行 `prepare`，冻结备份后执行 `apply`；后者先完整事务演练并回滚，再重新校验目标前置状态后提交；其他客资同期发生业务变化不扩大本次范围。目标客资或其关联记录已变化时拒绝执行，须重新核查，不能覆盖旧备份。

```bash
python3 script/sql/mysql/tools/repair_planner_valid.py prepare --backup-dir /opt/zsjos-runtime/backups/planner-valid-20260923
python3 script/sql/mysql/tools/repair_planner_valid.py apply --backup-dir /opt/zsjos-runtime/backups/planner-valid-20260923 --reconcile-effective-order
python3 script/sql/mysql/tools/repair_planner_valid.py verify --backup-dir /opt/zsjos-runtime/backups/planner-valid-20260923
```

Lead 行锁先于关联写入。拒绝首跟证据缺失、非预期来源、未经确认的订单/申诉关联、机会冲突、非预期任务或绩效结果。受影响表均使用事务：

- `zsjos_lead`：63 条状态 valid，1 条有生效订单的状态 won，记录本次真实判定/兼容转化时间、有效说明、活动时间和版本，清空当前无效信息；负责人、首跟事实、分类与快照、判定轮次/期限保留。
- `zsjos_opportunity`：新增 62 个 `initial_conversion/open` 机会和 1 个 `initial_conversion/won` 机会，将原无效客资的 `lost` 机会恢复 `open`；意向产品摘要按已有选择生成，不建立订单。
- `zsjos_business_task`：完成 63 个 pending 判定任务；原无效客资的判定任务已经 completed，保留其原完成记录。
- `zsjos_performance_attribution`：63 个已有当前轮次 QUALIFICATION 结果改为 valid，完成时间记录本次修复时间，组织/人员归属快照不改。KZ202609221710270021 原始快照缺失，按现有服务语义保留缺失，不补造历史。
- `zsjos_order`：仅上述 1 条订单补机会关联并推进 version/更新时间，保留订单其余字段。
- `zsjos_business_event`、`zsjos_business_audit_log`：分别追加 64 条真实系统修复记录，保留全部既有事件及无效判定历史。新记录含修复来源、前后状态、幂等键，不伪造员工提交。

所有目标的权威 `provider_owner_type` 均不是 partner，按现有返现服务不生成返现。直接运维修复不调用通知发送链路，不补发历史消息。不修改权限、初始化脚本、schema/version 记录、其他租户或业务数据。

## 验证、重复执行和恢复

演练和正式执行均验证：64 条判定有效（63 valid + 1 won）、64 个唯一机会（63 open + 1 won）、64 个完成判定任务、63 个已有绩效结果为 valid；新增事件/审计数量和关联；原有行逐列仅允许声明的改动；跟进、产品、申诉原样保留；订单仅允许已确认那 1 条的机会关联、版本和更新元数据变化；中文有效说明通过 UTF-8 读取及 HEX 比较。审计表超过百万行，因此只读取本次独有 execution_key 前缀，不扫描/备份无关审计内容。其他受检表保存租户内 before/after 镜像，文件权限由 0700 目录和 0077 umask 保护。

修复函数在同一事务再次执行必须 0 变更，且前后数据完全相同。CLI `apply` 提交后再次执行会因原始前置状态已变化而拒绝，绝不重新覆盖后续业务；只读 `verify` 可以重复执行。正式提交后使用新连接对冻结目标及其关联精确回读，并保存受限 before/after 与摘要证据。

提交前可完整 ROLLBACK，演练产生的自增间隙不重置。提交后不能整表恢复或按标记批量删除；必须核对后续业务变化，对原有行使用 before/after 版本守卫做补偿，新机会存在下游引用时不得直接撤销，审计/事件保留并追加补偿事实。恢复或删除仍需另行明确授权。
