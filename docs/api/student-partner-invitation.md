# 学员兼职邀请

## 绑定已有兼职账号

### Admin 学员详情

Admin「我的学员 → 学员详情」提供兼职账号区域，仅拥有 `zsjos:partner:manage-all` 时加载；保留现有学员可见范围。未绑定时可搜索、分页选择已有兼职并填写可选说明，核对双方身份后绑定；已绑定显示兼职编号、姓名和绑定时间，不提供解绑或换绑入口。状态错误可重试，不当作未绑定。切换学员清空旧状态并忽略过期响应。

`GET /admin-api/zsjos/partner-student-link/student?studentPersonId=...` 返回 `{bound, partnerNo?, partnerName?, startedAt?}`，时间遵循 epoch 毫秒契约；无有效关系时 `bound=false`。关联兼职缺失或租户不匹配返回既有 `1900014009`，不伪装成未绑定。仅返回展示摘要，不返回账号凭据。

状态查询与既有 `POST /admin-api/zsjos/partner-student-link/bind` 均要求 `zsjos:partner:manage-all` 及目标学员 `student/read` 对象权限。手工绑定通过单独的 `PartnerStudentManualLinkService` 进入现有事务绑定服务，参数和返回值不变；此校验同时适用于 Admin 和 Workbench。邀请码激活仍使用原有内部绑定流程，不要求注册者具有 ADMIN 学员权限。后端先上线，再发布 Admin；工作台原有客户端无需升级协议，但不可读学员的手工请求现在会被拒绝。无 SQL 或权限授予同步。

Admin 请求层新增可选 `preserveBusinessError`，本流程状态、候选和绑定请求显式开启，保留后端业务错误消息及 code；其他请求仍使用原默认行为，避免改变既有页面契约。绑定成功但状态刷新失败时显示状态重试，不提示再次提交绑定。

工作台学员总览工具栏提供「绑定已有兼职账号」，由服务端返回的 `zsjos:partner:manage-all` 权限控制，与现有绑定接口一致。可按既有兼职分页查询能力搜索并选择兼职，核对编号、姓名和手机号，填写可选绑定说明后确认。未授予该权限的用户不显示入口，不因编导身份自动授权。

复用 `GET /admin-api/zsjos/partner/page` 与 `POST /admin-api/zsjos/partner-student-link/bind`（查询参数 `partnerId`、`studentPersonId`、可选 `reason`）。当前学员作为固定绑定目标；双方已有有效绑定时由后端拒绝覆盖，页面保留错误提示，不自动解绑。分页查询目前不提供已绑定排除条件，候选是否可绑定以提交时后端校验为准。

成功后刷新学员详情及有权读取的邀请码状态，账号主页重新加载指标。绑定只建立身份关系，不创建新账号、不改变手机号或原运营归属，不限定历史客资必须发生在绑定之后。此入口不改变管理端、H5 或后端契约。

编导只能从“已接收学员”列表发起学员兼职邀请。服务端依据接收关系校验资格，不依据角色名称或前端可见性判断。

- `POST /admin-api/zsjos/partner-invitation/student/create`：创建学员场景邀请码，预填姓名和手机号且允许本次修改。
- 现有兼职注册入口使用邀请场景激活；激活事务同时创建兼职主体、登录账号和 `PartnerStudentLink`。

学员身份的权威关系是“兼职主体 ↔ 学员信息主体”。兼职运营归属是独立关系，由邀请表单明确选定的运营建立，不能仅凭学员服务分配关系推断。原始学员姓名、手机号、发起编导和分配上下文作为审计快照保存；本次修改后的注册资料不会回写学员主体。

已有有效绑定、手机号冲突、邀请码过期/作废或并发激活都会拒绝覆盖，并进入现有解绑或纠正流程。

## 邀请码到期时间

管理端 `POST /admin-api/zsjos/partner-invitation/create` 与工作台学员入口 `POST /admin-api/zsjos/partner-invitation/student/create` 均支持可选 `expiresAt`，请求和响应使用 epoch 毫秒时间戳，业务时区为 `Asia/Shanghai`。未传或传 null 时按服务端生成时刻起 7 天计算，兼容旧客户端；指定时间必须晚于服务端当前时间，否则返回错误码 `1900000031`，且不会作废原邀请码或创建新邀请。

两个前端每次打开生成表单时默认填入 7 天后，允许选择具体日期和时间，清空或选择过去时间会阻止提交。列表和生成结果按 `YYYY-MM-DD HH:mm:ss` 展示到期时间，空值显示 `-`。服务端仍依据已保存的到期时间判断是否允许激活。已有邀请码期限不变，不支持永久有效或修改已有邀请码期限；无需数据库迁移。

## 运营预填与开通状态（2026-09-18）

学员邀请请求新增必填 `assignedOperatorUserId`。默认值来自当前编导已接收的有效学员服务关系；运营唯一且无未指派关系时预填，否则要求明确选择。运营可改选，候选沿用普通兼职邀请 API；生成和激活时均校验运营有效性。表单选择不回写学员运营，学员后续改派也不自动迁移兼职归属。

激活事务同时创建兼职、登录账号、`PartnerStudentLink`、运营归属和归属审计，再消费邀请码；任何失败回滚。已有 `assignedOperatorUserId` 为空的历史学员邀请码保留原行为，仅绑定学员，不推断历史运营。不批量修复历史账号，无数据库迁移。上线顺序为后端再工作台；旧版学员创建请求缺少运营将被必填校验拒绝，应刷新工作台。

`GET /admin-api/zsjos/partner-invitation/student/context?studentPersonId=...` 返回 `{opened, defaultOperatorUserId, operatorAssignmentConflict, invitation}`。`opened` 以有效学员兼职绑定为准；已开通不返回邀请码。未开通返回本租户该学员最近一次学员邀请（没有则为空），过期状态按服务端时间投影，不要求先运行过期任务。查询和创建均要求 `zsjos:partner-invitation:create-student`、学员对象可读以及当前编导已接收的有效服务关系；全量邀请列表权限不变。运营候选接口额外接受此创建权限，不授予全量邀请查询权限。

工作台未开通显示“开通兼职账号”，待注册显示“查看兼职邀请码”，过期/作废显示“重新生成兼职邀请码”及旧码失效说明；已注册并绑定显示“兼职账号已开通”，不等待首次登录。状态从服务端读取，在学员切换、页面重新进入、窗口恢复焦点及打开表单时刷新；关闭结果弹窗和刷新页面不丢失邀请码。加载失败显示错误和重试，不当作未开通。运营加载失败可重试，无候选或无效选中值不得提交。

验证入口：`PartnerInvitationServiceImplTest`、`PartnerInvitationControllerPermissionTest`；工作台 `test/student-partner-invitation.html` 与 `python frontend/workbench/test/student-partner-invitation.py` 使用隔离的合成传输，不修改真实账号。


### 运营学员详情兼职状态（2026-09-24）

Workbench 使用 `GET /admin-api/zsjos/media-students/{personId}/partner-context` 读取兼职状态，要求既有 `zsjos:media-student:query-my` 功能权限、`student/read` 对象权限，并复用媒体学员详情可见范围与租户隔离。无邀请资格的读者仅取得有效绑定的 `opened` 状态和 `canInviteStudent=false`，不返回邀请码或默认运营。

仅同时拥有 `zsjos:partner-invitation:create-student` 且为该学员 active/accepted 服务的当前编导时，返回 `canInviteStudent=true` 及原邀请上下文。页面以服务端资格显示创建/查看邀请码入口；加载、失败和重试独立保留，失败不视为未开通，也不开放绑定/邀请操作。切换学员和恢复焦点重新读取状态。

既有 `/zsjos/partner-invitation/student/context`、创建接口及 Admin 绑定状态接口权限与响应保持不变。Admin 不消费新增接口；不授予角色权限、不修改数据或历史邀请。上线顺序为后端后 Workbench。
