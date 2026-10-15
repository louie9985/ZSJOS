# ZSJOS 全角色目标权限矩阵

## 新媒体学员全量读取（V270）

`zsjos:media-student:query-my` 继续控制页面入口；`zsjos:media-student:query-all`
扩大为当前租户的新媒体学员范围，包含历史服务和草稿。System 的既有租户管理员
全量读取能力同样适用，普通角色须由管理员手动配置按钮权限。该读取范围不授予
访谈保存、定位卡提交或旧版沟通记录新增权限；关联资产仍执行各自权限检查。

## Role-menu assignment policy (2026-09-17)

Migrations, bootstrap seeds, standalone deployment SQL and their generators no longer
write `system_role_menu`: no default grants, role-name mappings, inherited grants,
ancestor repair, reassignment or automatic revocation. System role management owns
these assignments. Menu/button definitions and backend permission checks remain.
A fresh database starts without role-menu rows; assign ordinary roles explicitly.
Existing grants are neither reset nor restored by this source cleanup.

Historical grant descriptions below record earlier behavior, not current executable
migration guarantees. Versions for retired authorization-only migrations remain as
ledger placeholders so ordering and dependencies stay stable (including V251/V252).

This user-approved historical source cleanup changes file-byte checksums. Already
installed environments must retain their recorded checksums and use a separately
reviewed rollout; do not replay the entire chain or run checksum reconciliation just
to bypass drift protection. This change does not authorize database grant changes.
Rollback of source does not roll back grants previously written by an old release.
Read-only grant audits are optional administrator reports, not fresh-install defaults.


## 考期查看与管理

“日历 → 考期日历”保留独立可勾选的查看（73612，`zsjos:exam-calendar:query`）与管理（73611，`zsjos:exam-calendar:manage`）叶子。
查看用户读取已发布考期的历史规格快照；管理用户才可获取产品规格选项并创建、编辑草稿、发布、撤销。不要求产品配置权限，不按角色名授权。

## 客资销售反馈权限（V182）

新增 `zsjos:lead:submitter-feedback:read`（查看）与
`zsjos:lead:submitter-feedback:create`（发送），由管理员配置。销售应同时配置两项；
员工提交人只配置查看。权限不能替代当前负责人或提交方关系，不自动授权主管/管理员
读取所有反馈。Partner 使用独立账号/归属验证，不消费 ADMIN 权限列表。
迁移只创建菜单配置，不自动修改角色或真实账号授权。

本矩阵覆盖 34 个稳定角色编码的评审记录；其中 `part_time_partner` 已于 V247 退役（兼职改用独立账号体系），
当前有效 System 角色为 33 个。授权只按 `system_role.code` 和 `system_menu.permission` 处理；菜单 ID、显示名、部门名和岗位名都不是授权依据。BPM 任务候选人与 ZSJOS 功能权限分别校验，个人站内消息按登录身份和消息所有权提供，不复制 ZSJOS 菜单权限。

## V071 精确权限集

| 角色编码 | 精确 ZSJOS 权限 |
|---|---|
| `part_time_partner` | （V247 已退役该 System 角色）其权限现由兼职端常量 `PORTAL_PERMISSIONS` 提供：`zsjos:partner:self-query`, `zsjos:lead:submit`, `zsjos:lead:query-submitted`, `zsjos:lead:submitter-supplement`, `zsjos:lead:urge`, `zsjos:lead-complaint:create`, `zsjos:lead:appeal:create`, `zsjos:cashback:my-query`, `zsjos:withdrawal:my-query`, `zsjos:withdrawal:apply` |
| `finance_manager` | `zsjos:sales-order:query`, `zsjos:sales-order:review`, `zsjos:cashback:finance-query`, `zsjos:withdrawal:finance-query`, `zsjos:withdrawal:review`, `zsjos:withdrawal:payout`, `zsjos:export:query`, `zsjos:export:order`, `zsjos:export:finance-order`, `zsjos:export:cashback`, `zsjos:export:withdrawal` |
| `finance_specialist` | 与 `finance_manager` 完全相同的 11 项；不得拥有 `zsjos:export:lead` |
| `enrollment_manager` | `zsjos:sales-order:query`, `zsjos:sales-order:review` |
| `enrollment_specialist` | `zsjos:sales-order:query`, `zsjos:sales-order:review` |
| `quality_manager` | `zsjos:lead:appeal:query`, `zsjos:lead:appeal:review-quality` |
| `quality_specialist` | `zsjos:lead:appeal:query`, `zsjos:lead:appeal:review-quality` |
| `boss` | `zsjos:lead:appeal:query`, `zsjos:lead:appeal:review-chairman` |

`system_administrator` 的精确 allowlist 原由 V071 声明（业务审计和只读借视图、导出任务查询与客资导出、客资全局管理/派单/重复复核/筛选/跟进规则、人员与兼职主体、产品和 SKU、用户关系、通知规则，以及 `zsjos:withdrawal:admin-query`），并曾明确禁止 `zsjos:sales-order:review`、`zsjos:cashback:finance-query`、`zsjos:withdrawal:finance-query/review/payout` 和订单/财务订单/返现/提现导出。

**V252 起该约束被取代**：产品口径（2026-09-16）为「**管理员拥有所有菜单权限**」。`system_administrator` 现在持有全部 2245 个启用菜单（与 `super_admin` 可见范围一致），上述财务禁止条款不再适用，`verify-role-menu-coverage.sql` 的对应 violation 条款已移除。新增菜单的授权规则简化为：**通用菜单 → `normal_user`；全量菜单 → `system_administrator` / `super_admin`；业务角色按职责单独特评**。

V143 adds `zsjos:subordinate-partner:query` as an administrator-configured employee read permission and
`zsjos:partner:assign-owner` for Partner ownership maintenance. The latter is initially granted only to
`system_administrator`; no ordinary role receives subordinate Partner visibility automatically.

V150 retires those two permissions and consolidates Partner access under `zsjos:partner:query` and
`zsjos:partner:manage`. Query is live-ownership scoped; manage is tenant-wide and includes all supported
account and ownership commands. Only roles that held the former create, state-update and assignment
permissions together are upgraded to manage. V150 also assigns the independent
`zsjos:lead:claim-pool:query` page to enabled `sales_manager` roles without granting `zsjos:lead:claim`.

V185 turns the Partner page into a permission-free route container and moves its former query identity to
the `查看兼职` button. `zsjos:partner:query` keeps the existing department/relationship-expanded read scope;
`zsjos:partner:manage` becomes strict self-only read scope; `zsjos:partner:manage-all` receives the former
tenant-wide management semantics. Existing V150 management grants retain full capability through the stable
menu ID `79920`; V185 grants no role the new self-only permission automatically. Permissions are additive,
so a strict self-only role must not also receive `zsjos:partner:query`.

V146 inherits media-account capabilities by existing permission relationships rather than role names:
holders of `zsjos:media-account:query` receive `zsjos:media-calendar:query`, holders of account edit
receive `zsjos:media-account:maintenance`, and holders of account `query-all` receive
`zsjos:media-calendar:query-all`. Runtime object scope still limits maintenance and ordinary calendar
queries to the assigned director/operator unless the independent all-account permission is present.

V186 retires V161's shared `/calendar/all` page and does not copy its grants to the new personal calendar.
Account-calendar `query-managed` and `query-all` are independently administrator-configured; the former consumes
only bounded System department scope and the latter grants all accounts. The approved System tenant-read-all
capability also grants current-tenant account reads without granting maintenance operations. The personal-calendar page
and create/update/delete buttons are added to eligible tenant packages without automatic role grants.

V187 adds `zsjos:exam-calendar:query` for page access and `zsjos:exam-calendar:manage` for every mutation.
The page is a child of Calendar menu 73600, resolving to `/calendar/exam-calendar`.
The role tree exposes sibling button leaves 73612 (query) and 73611 (manage) under page 73610.
Read-only roles select the query leaf; managers select both. The parent page retains the query route contract.
It initially grants query to every enabled internal role whose tenant package exposes the page, and grants manage
to enabled `exam_manager` and `exam_specialist` roles. These are initial server-owned role-menu assignments only;
backend and Workbench runtime behavior checks permission identifiers and never role names.

## 全部角色目标

普通客资提交权限 `zsjos:lead:submit` 由 `center_head`、`dept_manager`、`content_director` 和
`new_media_operator` 角色持有；销售自拓权限
`zsjos:lead:self-sourced:create` 仅由 `sales_specialist` 持有。角色菜单关系按所有租户中的启用角色同步，
账号岗位和部门资格仍由后端业务校验。

| # | 角色编码 | ZSJOS 目标 |
|---:|---|---|
| 1 | `center_head` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 2 | `dept_manager` | 直属部门范围能力；对象权限仍要求本人是对应业务责任人 |
| 3 | `content_director` | `/zsjos/media-students`、第三方账号/内容/定位操作（仅本人服务关系或账号责任对象） |
| 4 | `new_media_operator` | `/zsjos/media-students`、第三方账号、内容生产、拍剪核对和账号定位复核；学员范围仅限本人负责账号、内容、定位或任务关联对象 |
| 5 | `filming_editor` | 查询、接受/拒绝指定拍剪单、制作、提交、查看公共池和抢单；候选资格由用户关系与 `zsjos:production-ticket:accept` 权限交集决定 |
| 6 | `sales_manager` | 通过 `query-owned` 查看本人及当前所管部门、子部门销售负责的客资，并具备跟进记录、流转记录只读能力及下属销售一键暂停接单能力；不得持有 `query-all` |
| 7 | `sales_specialist` | 通过 `query-owned` 查看本人负责的客资，保留接单/抢单、跟进判定、自拓、建单、本人订单、公海申请和本人工作计划能力；本人客资可按 `owner-transfer` 转派给启用销售，或按 `owner-release-public-sea` 释放至公海；不得持有 `query-all` |
| 8 | `enrollment_manager` | V071 精确 2 项订单查询/履约审批权限；无提现、返现或资金导出 |
| 9 | `enrollment_specialist` | V071 精确 2 项订单查询/履约审批权限；无提现、返现或资金导出 |
| 10 | `finance_manager` | V071 精确 11 项完整财务权限 |
| 11 | `finance_specialist` | 与财务主管完全相同的 V071 精确 11 项 |
| 12 | `study_planner` | `/zsjos/my-students`、学习规划师联系/交付操作、专用学员复购和本人订单；不授予通用订单录入或外部历史客户复购，仅本人负责的服务关系 |
| 13 | `academic_specialist` | 零 ZSJOS 菜单，当前没有已落地职责 |
| 14 | `delivery_manager` | 直属交付部门学员只读及主管直接调班；对象责任关系由服务端动态解析 |
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
| 29 | `system_administrator` | **V252 起为「管理员 = 全部启用菜单」**（2245 项，与 `super_admin` 可见范围一致）；取代 V071 allowlist 与财务禁止条款 |
| 30 | `application_developer` | 保持已有明确能力，不按岗位名称扩权 |
| 31 | `boss` | V071 精确 2 项申诉查询/最终裁决权限；每租户恰好一个有效角色 |
| 32 | `super_admin` | 保持框架超级管理员行为和全部 ZSJOS 管理能力；维护模式开关仅此角色 |
| 33 | `normal_user` | 保持已有明确能力，不按角色显示名扩权 |
| 34 | `part_time_partner` | **V247 已退役**：兼职使用独立账号体系不通过 System 角色授权；权限由 `PORTAL_PERMISSIONS` 常量提供 |

零菜单组实际为 18 个角色，不是 17 个（退役兼职角色后为 17 个）。未来教务、交付、考务、职业、教学、招聘、人力或行政模块落地时，必须按对应业务权限另行评审和前向迁移，不得从岗位名称自动生成授权。

**注**：上表的「零 ZSJOS 菜单」描述的是迁移当时的状态。V246 之后这些角色已获得工作台菜单
（首页、需求与反馈、我的资产、采购申请等基线页面，以及各自业务域的只读或操作页面）；
V248 之后客资详情的 5 个页签权限也已恢复。**V251 起通用工作台菜单（首页、日历、工单中心、
需求与反馈、学员账号交付、我的资产/采购申请等 45 项）统一由 `normal_user` 持有，
业务角色只保留岗位专属业务功能**——业务角色的最终可见范围由其全部角色授权的并集决定。
上表保留为各角色**业务职责**的评审记录，实际菜单授权以 `system_role_menu` 与
`verify-role-menu-coverage.sql` 为准。

## V246 全角色菜单覆盖配置

上表是各角色**在其业务模块落地时**的目标能力，V071 之后多数角色事实上没有工作台菜单。
V246（`script/sql/mysql/migrations/V246__role_menu_permission_coverage.sql`）把当前**已落地**的
工作台页面与按钮按职责配置到全部 34 个角色，并关闭了"只建菜单、不授角色"造成的覆盖缺口：

- 新增覆盖的菜单域：素材库/内容审核/内容生产（`80010`–`80042`、`602153`–`602155`）、礼品配置与采购
  （`8900`–`8911`）、支付主体与产品支付配置（`602200`–`602212`）、学员信息收集表配置与操作
  （`602136`–`602146`）、销售提交人反馈（`602133`/`602134`）、个人日历、考期/课程日历、
  H5 排行榜配置（`602151`/`602152`）、爆款账号/内容拆解（`80041`/`80042`）。
- 授权仍只按 `system_menu.permission` 解析，父目录按各角色分别补齐；**自带页面权限的父节点仅在该角色已
  持有该权限时才继承**，避免为一个按钮放开范围外的页面。
- 迁移是**纯增量**：不撤销任何既有授权，全部为 not-exists 保护的插入，可重复执行。
- V246 只覆盖此时已落地的菜单；上表中尚未落地的模块（教务、考务存档、招聘、人力流程等）仍按"另行评审"
  处理，不因本迁移而自动扩权。

`system_administrator` 在 V071 allowlist 之外补入新落地的配置页。

### 兼职端身份边界（V247）

**兼职不使用 System 用户体系。** 自 V072 起兼职具备完全独立的身份与鉴权链路：

| 维度 | 系统用户（Admin / Workbench） | 兼职（H5） |
|---|---|---|
| 账号表 | `system_user` | `zsjos_partner_account` |
| 认证接口 | `/admin-api/system/auth/login` | `/part-api/zsjos/auth/login` |
| API 前缀 | `/admin-api` → `UserTypeEnum.ADMIN(2)` | `/part-api` → `UserTypeEnum.PARTNER(3)` |
| 令牌主体 | `system_user.id` | `zsjos_partner_account.id` |
| 权限来源 | `system_role_menu` | `PartnerAuthServiceImpl.PORTAL_PERMISSIONS`（编译期常量） |

`TokenAuthenticationFilter` 按请求前缀解析出期望的 userType，并与令牌的 `userType` 比对，不一致即拒绝。
因此**兼职令牌无法访问 `/admin-api`，系统用户令牌也无法访问 `/part-api`**，双向隔离在框架层成立。

V063 引入的同名 System 角色 `part_time_partner` 从未被兼职端消费（它的权限来自常量集合），
长期作为影子角色出现在 System 角色权限管理里，容易误导配置。**V247 已将其实体退役**：
清空其全部菜单与用户关系后逻辑删除该角色，使 `system_role` 中不再存在兼职角色。
兼职端权限当前仍由 `PORTAL_PERMISSIONS` 决定，如需可配置化需另行评审。

### 管理员全量菜单（V252）

产品口径（2026-09-16）：**管理员拥有所有菜单权限**。V252
（`script/sql/mysql/migrations/V252__system_administrator_full_menu.sql`）给
`system_administrator` 授予全部 2245 个启用菜单，与 `super_admin` 的可见范围一致。

此前的问题是"按需 allowlist"完全靠人工判断：V071 之后新增的页面多数没补授，实测缺 2013 项；
更隐蔽的是它持有 `6741`/`79980`/`79990`（父节点是菜单 1「系统管理」）却不持有菜单 1，
前端建树丢弃这三个节点，**权限有、界面无**。

口径统一后，新增菜单的授权规则简化为三条：

| 菜单性质 | 授予角色 |
|---|---|
| 人人需要的通用工作台菜单 | `normal_user`（见 V251） |
| 全部菜单 | `system_administrator`、`super_admin` |
| 岗位专属业务功能 | 对应业务角色，单独特评 |

**代价（需产品知晓）**：全量授权与此前"禁止管理员持有财务复核与资金导出权限"的约束不可兼得。
V252 因此**取消了该约束**——`system_administrator` 现在持有 `zsjos:sales-order:review`、
`zsjos:cashback:finance-query`、`zsjos:withdrawal:finance-query/review/payout` 与五项
导出（客资/订单/财务订单/返现/提现）。若需保留职责分离，应改为"全量菜单 − 财务权限黑名单"，
届时需同步恢复 `verify-role-menu-coverage.sql` 的 violation 条款。

禁用菜单（`status=1`，如工作计划模块、框架自带的支付/公众号/商城/CRM/ERP/AI/IoT/MES/WMS 模块）
不授予，与 `super_admin` 的框架行为一致（`getPermissionInfo` 会 `filterDisableMenus`）。
这些停用模块下有 104 行"父停用、子启用"的历史数据，迁移不再为其补授父级；
`verify-role-menu-coverage.sql` 新增 4a) 检查单独列出（提示，非缺陷）。

### 悬空授权修复（V249 / V250）

V246 的覆盖扫描看不见一类缺陷：**按钮被授权、但其父目录未被授权**。系统只返回被直接授权的
菜单集合，前端建树时父节点缺失的子节点会被丢弃，因此这些按钮**权限有效但界面不可达**。

| 角色 | 悬空按钮 | 缺失父级 | 来源 |
|---|---|---|---|
| `dept_manager` | `1195`/`1197`/`1199`/`602117`、`2715`/`2716` | `1186` 流程管理、`1193` 流程模型、`2714` 流程分类 | V109 |
| `system_administrator` | `6786`-`6789`、`602131`/`602132` | `2144` 站内信管理 | V071 |

V249 按各角色**所需路径**精确补授（对照 `normal_user` 只持有 `1185 工作流程` + `1200 审批中心`
的既有惯例，不扩散到同级页面）。补授后 `dept_manager` 可进入流程模型/流程分类页面并使用 V109
已授予的按钮；`system_administrator` 可在消息中心看到业务通知规则与通知渠道。

V250 修复了另三行缺陷：`V179` 与一次手工修复把 `system_role_menu` 写成了 `tenant_id=0`，
而角色属于租户 1，导致 `system_administrator` 的通知渠道授权**实际不生效**。V250 把关系租户更正为
角色自身租户（保留原 creator 与时间线），并清理更正后产生的重复行。

`verify-role-menu-coverage.sql` 现同时校验悬空授权、跨租户授权与重复授权，三者预期均为 0 行。

### 通用菜单基线收敛（V251）

V246 把通用工作台菜单授给了**每一个**角色，结果是 tenant 1 的 33 个角色各自持有一组
完全相同的 45 个菜单（3 目录 + 10 页面 + 32 按钮：工作台首页、日历、工单中心、
需求与反馈、学员账号交付、我的资产/采购申请等），合计 1485 行重复授权。新增一个通用菜单
需要改 33 个角色，既是维护负担，也不符合"角色 = 岗位所需业务功能"的建模意图。

V251（`script/sql/mysql/migrations/V251__universal_menu_baseline.sql`）把这组菜单收敛到
**普通员工 `normal_user`** 一个角色：

- 授权是**并集语义**——用户可见菜单 = 其全部角色授权的并集
  （`PermissionServiceImpl.getRoleMenuListByRoleId`）。把通用菜单从业务角色移到 `normal_user`，
  只要账号同时持有 `normal_user`，最终可见菜单**完全不变**（V251 执行时逐账号比对并集，
  结果 0 个账号发生变化）。
- 选 `normal_user` 而非新建基础角色的原因：它已是既有的"普通员工"角色，V246 已授予其客资/
  学员/工单/资产/公告/HR 员工端/BPM/消息中心/素材浏览等**全部员工自助能力**，本身就是事实上的
  基础角色；tenant 1 的 42 个有角色账号中 41 个已持有它。
- 通用集不写死 ID，而是运行时取"tenant 1 全部启用角色的共同持有菜单"，并在撤销前校验
  **不存在"持有业务角色但未持有 normal_user"的账号**（`super_admin` 账号除外），
  存在则整批中止不生效。
- `super_admin` 的行**不撤销**（超管不受菜单授权限制，保留行以维持既有全量状态一致）。
- **必须回补祖先目录**：通用集含两个纯容器目录 `6735 工作台` 与 `73600 日历`，
  业务角色的业务页面（客资管理、订单管理、班级管理、素材库、考期日历…）都挂在其下。
  系统只返回直接授权的菜单，父节点缺失时前端建树会丢弃子节点——若不回补，
  这些页面会**权限仍在但界面不可达**，且并集比对看不出这种退化。V251 在撤销后自底向上
  补回"仍有被授权子孙"的祖先（迭代 5 层），并以悬空授权检查兜底。

收敛后通用集仍为 45 项，仅 `normal_user` 与 `super_admin` 持有；`normal_user` 共 103 项授权
（45 项通用基线 + 58 项既有的员工自助能力，含框架/BPM/HR 等非 `zsjos:` 命名空间），
全部业务角色的业务权限数量与并集可见范围不变。

### 存量待确认项

- 工作计划模块（`6900` 及 16 项权限）整块 `status=1`，为**有意待上线**状态，未纳入权限配置。
- `zsjos-db test-fresh` 的 145 项失败为既有测试缺陷，见下文。

### test-fresh 已按真实安装路径修复

`zsjos-db test-fresh` 原先只加载 `bootstrap.sql` + `V071` 就运行完整的 `verify/core.sql`，
导致所有「断言迁移产物」的检查必然失败——**145 项**（`handoff/main-delivery-task-closure.md`
与 `handoff/20260817-wecom-user-id.md` 已记录为长期已知）。

现已改为与 `migrate` 在空库上的行为一致：**应用基线 → 应用基线未登记的迁移 → 验证**。

这次修复暴露了一个此前测不出的**真实安装故障**：基线 DDL 已含
`zsjos_content_review_batch.student_person_id` 与 `account_ids_json`，但基线版本清单未登记 V225，
而 V225 是 15 个同类迁移中**唯一没有 `information_schema` 守卫**的，全新安装会直接
`ERROR 1060 Duplicate column name` 中止。V225 已按 V244 的写法补上守卫，并同步更新台账校验和。

V246 也不再让同一角色同时持有页面与同名按钮（`73610`+`73612`、`73630`+`73631`、
`73624`+`73629`、`6811`+`6849`）。真正由两个页面共享一个权限的
（`可接工单` 与 `我的工单` 共用 `zsjos:work-order:query`；两个爆款拆解页共用
`zsjos:material:create`）**仍然都授**，否则会有一个页面不可达。

失败数由 145 降至 16，且这 16 项**在线上开发库同样 FAIL**——全部是既有验证器漂移，无一是本次回归：

- **台账校验和口径已定**：权威口径为运行器写入的 `sha256(文件字节)`。117 个迁移自登记的
  `SHA2(文件名)`/字面量在下次应用时被运行器覆盖；基线以 `legacy`/`baseline` 种入的行保留
  种子值（种子 SQL 与若干断言以之为准）。`pending_migrations` 校验除种子行外的全部行，
  `record_migration` 一律写文件字节哈希，新增 `zsjos-db reconcile <env> [--apply]` 核对/纠正，
  `migrate` 在待办检查前自动执行（不含种子行）。相应的断言改为同时接受文件字节哈希与自登记值。
  开发库 194 条偏离行已按此口径纠正，种子行已还原为种子值。
- **V131/V150/V178**：违规授权由 `creator='1'`/`creator='39'` 在本次工作之前建立。例如 V178
  断言无人持有菜单 6820，而 `super_admin`、`new_media_operator`、`content_director` 都持有。
- 部分断言要求 `zsjos_schema_version` 中存在基线从未种入的版本行。

修复后的 `test-fresh` 已能执行真实安装路径，因此剩余 16 项是**可度量的**具体清单，
不再被 145 项噪声掩盖。

## V248 菜单 ID 复用事故与修复

V086 在 `6920`-`6923` 建立客资详情 4 个页签权限（父节点 `6770`），V091 在 `6924` 建立
`zsjos:lead-detail:flow-read`。V224 随后把 `6920`-`6927` 整段复用为「学员账号交付」，
其 `ON DUPLICATE KEY UPDATE` 覆盖了 `permission` 列：**5 个页签权限全部消失**，而 V086 遗留的
`system_role_menu` 仍指向被复用的 ID。结果是客资详情的跟进/申诉/投诉/订单/流转 5 个页签
对**所有角色**不可用，且因菜单行不存在而无法通过界面恢复。

V248 在空闲号段 `602300`-`602304` 重建这 5 个权限行，并按 **V086 自己声明的「源权限 → 详情权限」
继承表**恢复授权（不按角色名推断）：

| 详情权限 | 继承自 |
|---|---|
| `lead-detail:follow-up-read` | `lead-follow-up:query`、`subordinate-sales:query`、`student:query-my`、`lead:query-all` |
| `lead-detail:appeal-read` | `lead:appeal:create`、`lead:appeal:query`、`subordinate-sales:query`、`lead:query-all` |
| `lead-detail:complaint-read` | `lead-complaint:create`、`lead-complaint:handle`、`subordinate-sales:query`、`lead:query-all` |
| `lead-detail:order-read` | `sales-order:query`、`sales-order:create`、`subordinate-sales:query`、`student:query-my`、`lead:query-all` |
| `lead-detail:flow-read` | 仅 `sales_manager`（V091 原始归属）|

`normal_user` 与 `teaching_assistant` **显式排除**：它们在 V246 才获得 `zsjos:student:query-my`，
而该权限是 V086 继承表的源，纳入会凭空扩权。

V248 同时补建了后端自 V023/V024 起就在校验、但从未定义菜单行的三组权限：
`sales-order:query-own`、`sales-order:query-team`、`sales-order:refund-apply`、
`payment-refund:read/refresh/direct`、`student:exam-date-update`。
其中 `payment-refund:direct` 是资金出账动作，**仅 `finance_manager` 与 `super_admin`**。

### 防复发

`zsjos-db check` 新增菜单 ID 复用检查：后续迁移若用不同 permission 重新插入已被使用的
`system_menu.id`，检查直接失败。已应用到线上、无法回改的历史冲突（`6850`、V063 的兼职端段、
`6913`、`6920`-`6923`）在 `FROZEN_LEGACY_COLLISIONS` 中冻结，仅新增冲突会被拦截。

## 审计规则

- 每个租户的兼职角色必须恰好拥有上述 10 个有效权限，两个财务角色必须各自恰好拥有相同 11 项。
- 同一租户、角色和 permission 最多一个有效 `system_role_menu` 关系；权限按钮不得指向已删除父菜单。
- `system_administrator` 必须有提现只读和客资导出。**V252 起此前的「不得有常规财务审核、提现审核/打款或资金导出」约束被取消**：管理员改为持有全部启用菜单。
- V073 后 `study_planner` 使用 `zsjos:student:query-my`。V113 后 `content_director` 与 `new_media_operator` 共用 `zsjos:media-student:query-my` 和 `/zsjos/media-students`，但服务端分别按服务关系、账号责任关系和本人任务收敛数据范围。第三方账号、内容生产和账号定位的独立页面菜单退役，原稳定按钮权限移到学员菜单下。所有菜单按钮授权都不替代对象权限、数据范围和状态校验。
- V086 增加 `zsjos:lead-detail:follow-up-read`、`appeal-read`、`complaint-read`、`order-read` 四个独立只读权限。迁移只按原有效可见权限做兼容授权，后续由 System 角色权限管理分别配置；任何一个标签权限都不能替代 Lead 对象关系校验。
- V071 只改变菜单元数据和角色菜单关系，不改变真实账号、用户角色关系、BPM 或业务数据；应用现有数据库需要单独确认。
- V131 修复 V128 结构已存在但动作菜单关系缺失的环境：`content_director` 获得资料预审、采访、指派运营及已确认的定位动作；`new_media_operator` 仅保留定位查询、确认和退回，旧创建、编辑、可行性、签名、提交、试运行确认及归档授权被定向停用。

V073 grants `system_administrator` only the registration-checklist query/update/publish permissions and grants `study_planner` My Students. The retired new-media Student Operations permissions and graduation initiation buttons are no longer part of the baseline. V120 restores the shared `/zsjos/media-students` page grant for enabled `new_media_operator` roles after V103's historical director-only cleanup. Registration public-pool permissions remain intentionally separate; role names and departments never imply them.

V083 grants `content_director` only My Students. Runtime route candidates use the stable content-director post code and the persisted department subtree; this menu grant does not change candidate eligibility or public-pool access.

### Delivery class permissions (V188)

- `zsjos:delivery-class:query-managed`、`create`、`update`、`complete` 和 `direct-transfer` 独立配置；
  管理查询仍受部门 DataPermission 限制，主管查询权不自动授予任何写操作。
- `zsjos:delivery-class:query-my` 只开放本人担任班主任的班级；班主任候选还必须同时持有
  `zsjos:student:query-my`。`zsjos:class-transfer:create` 与 `query` 分别控制发起和查看本人申请。
- V188 初始授权从既有菜单关系继承，不在运行时代码检查角色名：拥有报名履约管理页 73001 的
  角色继承班级管理能力，拥有原“我的学员”页 73020 的角色继承“我的班级”和调班申请能力。
  73020 变为隐藏但可路由的学员详情深链，现有 `serviceRelationId` 操作契约保持不变。
