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

V143 adds `zsjos:subordinate-partner:query` as an administrator-configured employee read permission and
`zsjos:partner:assign-owner` for Partner ownership maintenance. The latter is initially granted only to
`system_administrator`; no ordinary role receives subordinate Partner visibility automatically.

V150 retires those two permissions and consolidates Partner access under `zsjos:partner:query` and
`zsjos:partner:manage`. Query is live-ownership scoped; manage is tenant-wide and includes all supported
account and ownership commands. Only roles that held the former create, state-update and assignment
permissions together are upgraded to manage. V150 also assigns the independent
`zsjos:lead:claim-pool:query` page to enabled `sales_manager` roles without granting `zsjos:lead:claim`.

V146 inherits media-account capabilities by existing permission relationships rather than role names:
holders of `zsjos:media-account:query` receive `zsjos:media-calendar:query`, holders of account edit
receive `zsjos:media-account:maintenance`, and holders of account `query-all` receive
`zsjos:media-calendar:query-all`. Runtime object scope still limits maintenance and ordinary calendar
queries to the assigned director/operator unless the independent all-account permission is present.

## 全部角色目标

| # | 角色编码 | ZSJOS 目标 |
|---:|---|---|
| 1 | `center_head` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 2 | `dept_manager` | 直属部门范围能力；对象权限仍要求本人是对应业务责任人 |
| 3 | `content_director` | `/zsjos/media-students`、第三方账号/内容/定位操作（仅本人服务关系或账号责任对象） |
| 4 | `new_media_operator` | `/zsjos/media-students`、第三方账号、内容生产、拍剪核对和账号定位复核；学员范围仅限本人负责账号、内容、定位或任务关联对象 |
| 5 | `filming_editor` | 查询、接受/拒绝指定拍剪单、制作、提交、查看公共池和抢单；候选资格由用户关系与 `zsjos:production-ticket:accept` 权限交集决定 |
| 6 | `sales_manager` | 通过 `query-owned` 查看本人及当前所管部门、子部门销售负责的客资，并具备跟进记录、流转记录只读能力及下属销售一键暂停接单能力；不得持有 `query-all` |
| 7 | `sales_specialist` | 通过 `query-owned` 查看本人负责的客资，保留接单/抢单、跟进判定、自拓、建单、本人订单、公海申请和本人工作计划能力；不得持有 `query-all` |
| 8 | `enrollment_manager` | V071 精确 2 项订单查询/履约审批权限；无提现、返现或资金导出 |
| 9 | `enrollment_specialist` | V071 精确 2 项订单查询/履约审批权限；无提现、返现或资金导出 |
| 10 | `finance_manager` | V071 精确 11 项完整财务权限 |
| 11 | `finance_specialist` | 与财务主管完全相同的 V071 精确 11 项 |
| 12 | `study_planner` | `/zsjos/my-students`、学习规划师联系/交付操作、专用学员复购和本人订单；不授予通用订单录入或外部历史客户复购，仅本人负责的服务关系 |
| 13 | `academic_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 14 | `delivery_manager` | 直属交付部门结业审批；对象责任关系由服务端动态解析 |
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
- V073 后 `study_planner` 使用 `zsjos:student:query-my`。V113 后 `content_director` 与 `new_media_operator` 共用 `zsjos:media-student:query-my` 和 `/zsjos/media-students`，但服务端分别按服务关系、账号责任关系和本人任务收敛数据范围。第三方账号、内容生产和账号定位的独立页面菜单退役，原稳定按钮权限移到学员菜单下。所有菜单按钮授权都不替代对象权限、数据范围和状态校验。
- V086 增加 `zsjos:lead-detail:follow-up-read`、`appeal-read`、`complaint-read`、`order-read` 四个独立只读权限。迁移只按原有效可见权限做兼容授权，后续由 System 角色权限管理分别配置；任何一个标签权限都不能替代 Lead 对象关系校验。
- V071 只改变菜单元数据和角色菜单关系，不改变真实账号、用户角色关系、BPM 或业务数据；应用现有数据库需要单独确认。
- V131 修复 V128 结构已存在但动作菜单关系缺失的环境：`content_director` 获得资料预审、采访、指派运营及已确认的定位动作；`new_media_operator` 仅保留定位查询、确认和退回，旧创建、编辑、可行性、签名、提交、试运行确认及归档授权被定向停用。

V073 grants `system_administrator` only the registration-checklist query/update/publish permissions and grants `study_planner` My Students. The retired new-media Student Operations permissions and graduation initiation buttons are no longer part of the baseline. V120 restores the shared `/zsjos/media-students` page grant for enabled `new_media_operator` roles after V103's historical director-only cleanup. Registration public-pool permissions remain intentionally separate; role names and departments never imply them.

V083 grants `content_director` only My Students. Runtime route candidates use the stable content-director post code and the persisted department subtree; this menu grant does not change candidate eligibility or public-pool access.
