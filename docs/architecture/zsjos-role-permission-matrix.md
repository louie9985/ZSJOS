# ZSJOS 全角色目标权限矩阵

本矩阵覆盖当前 34 个稳定角色编码。授权只按 `system_role.code` 和 `system_menu.permission` 处理；菜单 ID、显示名、部门名和岗位名都不是授权依据。BPM 任务候选人与 ZSJOS 功能权限分别校验，个人站内消息按登录身份和消息所有权提供，不复制 ZSJOS 菜单权限。

## V071 精确权限集

| 角色编码 | 精确 ZSJOS 权限 |
|---|---|
| `part_time_partner` | `zsjos:partner:self-query`, `zsjos:lead:submit`, `zsjos:lead:query-submitted`, `zsjos:lead:submitter-supplement`, `zsjos:lead:urge`, `zsjos:lead-complaint:create`, `zsjos:lead:appeal:create`, `zsjos:cashback:my-query`, `zsjos:withdrawal:my-query`, `zsjos:withdrawal:apply` |
| `finance_manager` | `zsjos:sales-order:query`, `zsjos:sales-order:review`, `zsjos:cashback:finance-query`, `zsjos:withdrawal:finance-query`, `zsjos:withdrawal:review`, `zsjos:withdrawal:payout`, `zsjos:export:query`, `zsjos:export:order`, `zsjos:export:finance-order`, `zsjos:export:cashback`, `zsjos:export:withdrawal` |
| `finance_specialist` | 与 `finance_manager` 完全相同的 11 项；不得拥有 `zsjos:export:lead` |
| `enrollment_manager` | `zsjos:sales-order:query`, `zsjos:sales-order:review` |
| `enrollment_specialist` | `zsjos:sales-order:query`, `zsjos:sales-order:review` |
| `quality_manager` | `zsjos:lead:appeal:query`, `zsjos:lead:appeal:review-quality` |
| `quality_specialist` | `zsjos:lead:appeal:query`, `zsjos:lead:appeal:review-quality` |
| `boss` | `zsjos:lead:appeal:query`, `zsjos:lead:appeal:review-chairman` |

`system_administrator` 的精确 allowlist 由 V071 声明，分为：业务审计和只读借视图、导出任务查询与客资导出、客资全局管理/派单/重复复核/筛选/跟进规则、人员与兼职主体、产品和 SKU、用户关系、通知规则，以及 `zsjos:withdrawal:admin-query`。明确禁止 `zsjos:sales-order:review`、`zsjos:cashback:finance-query`、`zsjos:withdrawal:finance-query/review/payout` 和订单/财务订单/返现/提现导出。迁移文件是该长 allowlist 的可执行事实源。

## 全部角色目标

| # | 角色编码 | ZSJOS 目标 |
|---:|---|---|
| 1 | `center_head` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 2 | `dept_manager` | 保持已批准的提交方和组织范围能力，不按名称追加销售、财务或管理员权限 |
| 3 | `content_director` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 4 | `new_media_operator` | 通过 `query-submitted` 查看本人提交客资；部门主管关系可将同一权限范围扩到当前所管部门及子部门员工，不按角色名称扩权 |
| 5 | `filming_editor` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 6 | `sales_manager` | 通过 `query-owned` 查看本人及当前所管部门、子部门销售负责的客资，并具备跟进记录只读能力；不得持有 `query-all` |
| 7 | `sales_specialist` | 通过 `query-owned` 查看本人负责的客资，保留接单/抢单、跟进判定、自拓、建单、本人订单、公海申请和本人工作计划能力；不得持有 `query-all` |
| 8 | `enrollment_manager` | V071 精确 2 项订单查询/履约审批权限；无提现、返现或资金导出 |
| 9 | `enrollment_specialist` | V071 精确 2 项订单查询/履约审批权限；无提现、返现或资金导出 |
| 10 | `finance_manager` | V071 精确 11 项完整财务权限 |
| 11 | `finance_specialist` | 与财务主管完全相同的 V071 精确 11 项 |
| 12 | `study_planner` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 13 | `academic_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 14 | `delivery_manager` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 15 | `exam_manager` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 16 | `exam_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 17 | `career_planner` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 18 | `career_manager` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 19 | `ip_teacher` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 20 | `product_rd_head` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 21 | `teaching_assistant` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 22 | `quality_manager` | V071 精确 2 项申诉查询/质控复核权限 |
| 23 | `quality_specialist` | V071 精确 2 项申诉查询/质控复核权限 |
| 24 | `recruitment_manager` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 25 | `recruitment_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 26 | `hr_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 27 | `admin_manager` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 28 | `admin_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 29 | `system_administrator` | V071 管理员 allowlist；提现只读，不得审核或打款 |
| 30 | `application_developer` | 保持已有明确能力，不按岗位名称扩权 |
| 31 | `boss` | V071 精确 2 项申诉查询/最终裁决权限；每租户恰好一个有效角色 |
| 32 | `super_admin` | 保持框架超级管理员行为和全部 ZSJOS 管理能力；维护模式开关仅此角色 |
| 33 | `normal_user` | 保持已有明确能力，不按角色显示名扩权 |
| 34 | `part_time_partner` | V071 精确 10 项本人范围权限；明确禁止 `zsjos:lead:query` |

零菜单组实际为 18 个角色，不是 17 个。未来教务、交付、考务、职业、教学、招聘、人力或行政模块落地时，必须按对应业务权限另行评审和前向迁移，不得从岗位名称自动生成授权。

## 审计规则

- 每个租户的兼职角色必须恰好拥有上述 10 个有效权限，两个财务角色必须各自恰好拥有相同 11 项。
- 同一租户、角色和 permission 最多一个有效 `system_role_menu` 关系；权限按钮不得指向已删除父菜单。
- `system_administrator` 必须有提现只读和客资导出，且不得有常规财务审核、提现审核/打款或资金导出。
- V073 后 `study_planner` 仅新增 `zsjos:student:query-my`；其余原零菜单角色仍不得存在任何有效 `zsjos:%` 权限。
- V071 只改变菜单元数据和角色菜单关系，不改变真实账号、用户角色关系、BPM 或业务数据；应用现有数据库需要单独确认。

V073 grants `system_administrator` only the registration-checklist query/update/publish permissions and grants `study_planner` only My Students. Registration public-pool permissions are intentionally unassigned and must be configured by an administrator; role names and departments never imply them.
