# 兼职端 App API

独立兼职前端统一使用 `/part-api/zsjos/**`。响应均为 `CommonResult<T>`，成功时 `code=0`、业务数据在 `data`。该子路径只接受 `PARTNER(3)` Bearer token；ADMIN 和 MEMBER token 均拒绝。普通 `/app-api/**` 仍按 MEMBER 解析，`/admin-api/**` 仍按 ADMIN 解析。

当前 H5 运行面使用后端真实接口，不再提供前端本地替代数据；接口缺失、权限失败、空列表和网络失败均按真实状态展示。

## 认证与个人信息

| Method | Path | Request / Result |
| --- | --- | --- |
| POST | `/part-api/zsjos/auth/login` | `{mobile,password,platform}` / access token、refresh token、过期时间 |
| POST | `/part-api/zsjos/auth/activate` | `{mobile,password,confirmPassword,inviteCode,platform}` / 激活成功后直接返回 access token、refresh token、过期时间 |
| POST | `/part-api/zsjos/auth/logout` | Bearer token / `Boolean`；幂等且只撤销 PARTNER token |
| POST | `/part-api/zsjos/auth/refresh-token` | query: `refreshToken`, optional `clientId` / 新 token |
| GET | `/part-api/zsjos/auth/permission-info` | 当前用户、角色和权限 |
| GET | `/part-api/zsjos/profile/get` | Partner 主体资料；手机号只读 |
| PUT | `/part-api/zsjos/profile/update` | `{nickname,email,avatar,sex}` |
| PUT | `/part-api/zsjos/profile/update-password` | `{oldPassword,newPassword}` |
| GET | `/part-api/zsjos/partner/me` | 当前 Partner 主体资料 |

登录和刷新校验 `zsjos_partner_account` 与 `zsjos_partner` 均启用。首次登录前，管理员在 Vue Admin 的兼职管理页生成一次性邀请码，填写姓名、手机号和归属运营；归属运营来自 System 中启用的 `new_media_operator` 角色用户。邀请码由服务端生成四位大写英文字母加四位数字，默认 7 天后过期；同手机号生成新邀请码会使旧的待激活邀请码立即失效。H5 通过 `/auth/activate` 提交手机号、新密码、确认密码和邀请码，服务端匹配手机号与邀请码后创建 `zsjos_partner`、`zsjos_partner_account` 和当前归属记录，并把邀请码标记为已使用，不可复用。账号密码登录时，如果手机号存在有效未激活邀请而尚无 Partner 账号，接口返回“该手机号尚未激活，请先使用邀请码激活”。

`permission-info` 返回固定角色 `partner` 和十项门户能力用于客户端展示；服务端不使用这些字符串代替 `partnerId` 对象授权。
企业微信登录、绑定和消息回跳使用 Partner 专用 OAuth/票据链路，业务请求仍以 PARTNER Token 和 Partner Account ID 作为身份边界。

Token 恢复由 HTTP 客户端内部单航班完成。普通业务请求的 HTTP 401 和响应体 `code=401` 共用一次刷新；刷新请求使用原始 Axios，只携带 `tenant-id`、refresh token 和移动端 `clientId`。原请求最多重放一次，刷新失败时所有等待请求都会失败、统一清理登录状态，并带原目标地址返回登录页。

主动退出不进入刷新流程。服务端直接从持久化记录按预期 `PARTNER` 类型撤销 access token、关联 refresh token 和缓存，因此 token 已过期、已删除或重复退出均返回成功；ADMIN/MEMBER token 不会被该接口删除。客户端确认退出后以本地清理为最终结果，服务端 401、网络错误或审计失败均不阻止清理 token、clientId、用户资料和权限，并且直接进入登录页而不携带回跳地址。

## 首页统计与排行榜

| Method | Path | Request / Result |
| --- | --- | --- |
| GET | `/part-api/zsjos/partner/home-statistics` | query: `period=today|week|month|year|total` / `{period,leadCount,withdrawnAmount,validLeadCount,convertedLeadCount}` |
| GET | `/part-api/zsjos/partner/home-statistics/details` | query: `period`, `metric=lead_count|withdrawn_amount|valid_lead_count|converted_lead_count`, `pageNo`, `pageSize` / 真实客资或已打款提现分页 |
| GET | `/part-api/zsjos/partner/leaderboard/config` | 排行榜可用类型、默认周期、分页大小和脱敏策略 |
| GET | `/part-api/zsjos/partner/leaderboard` | query: `period=today|week|month|total`, `type=estimated_income|withdrawn_amount|lead_count|valid_lead_count`, `pageNo`, `pageSize` / 排名、Top3、本人名次和差距 |

该接口必须限定当前 Partner 本人的数据。时间边界使用北京时间，本周从周一开始，全年从当年 1 月 1 日开始，累计不限制时间。客资数统计周期内提交的客资；有效客资统计其中当前 `status in (valid, won)` 的记录；成交客资统计其中当前 `status=won` 的记录；已提现金额按提现实际支付时间 `paidAt` 汇总。金额返回两位小数，数量返回非负整数。

统计明细和排行榜均由服务端按 Partner 身份、租户和业务归属计算。用户可见客资编号只使用 `leadNo`；明细中的内部 `id` 只用于 H5 详情路由。

## System 公共参考数据

| Method | Path | Authentication |
| --- | --- | --- |
| GET | `/app-api/system/dict-data/type?type={type}` | 只携带 `tenant-id` |
| GET | `/app-api/system/area/tree` | 只携带 `tenant-id` |

地区提交使用节点的 `selectionCode`，不能使用树节点内部主键替代。字典或地区加载失败必须显示错误和重试入口，不得静默回退为静态选项或空数组。

## 客资

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/part-api/zsjos/lead/product/catalog` | `zsjos:lead:submit` |
| POST | `/part-api/zsjos/lead/attachment/upload` | `zsjos:lead:submit` |
| POST | `/part-api/zsjos/lead/create` | `zsjos:lead:submit` |
| GET | `/part-api/zsjos/lead/inbox/submitted/page` | `zsjos:lead:query-submitted` |
| GET | `/part-api/zsjos/lead/inbox/submitted/summary` | `zsjos:lead:query-submitted` / 当前 Partner 三类客资提醒数量 |
| GET | `/part-api/zsjos/lead/get?id={id}` | `zsjos:lead:query-submitted` |
| GET | `/part-api/zsjos/lead/{id}/partner-activity` | `zsjos:lead:query-submitted` / Partner 可见客资流转聚合 |
| GET | `/part-api/zsjos/lead/partner-filter-options` | `zsjos:lead:query-submitted` / Partner 列表筛选项 |
| PUT | `/part-api/zsjos/lead/{id}/submitter-supplement` | `zsjos:lead:submitter-supplement` |
| POST | `/part-api/zsjos/lead/{id}/urge` | `zsjos:lead:urge` |
| POST | `/part-api/zsjos/lead-complaint/lead/{leadId}` | `zsjos:lead-complaint:create` |
| GET | `/part-api/zsjos/lead-complaint/my-page` | `zsjos:lead-complaint:create` |
| GET | `/part-api/zsjos/lead/appeal/lead/{leadId}/list` | `zsjos:lead:appeal:create` |
| POST | `/part-api/zsjos/lead/appeal/lead/{leadId}/submit` | `zsjos:lead:appeal:create` |
| POST | `/part-api/zsjos/lead/appeal/attachment/upload` | `zsjos:lead:appeal:create` |

来源和分类不使用前端静态值。分别调用 `GET /app-api/system/dict-data/type?type=zsjos_lead_source_channel` 和 `GET /app-api/system/dict-data/type?type=zsjos_lead_category` 获取启用项。

兼职端首页的“客资跟进提醒”进入独立 `/lead/follow-up` 页面；该页面读取 `/lead/inbox/submitted/summary`，返回 `followUpPendingCount`、`unreachableCount` 和 `invalidCount`，并在页面内按三个分类分别分页，不跳转或复用“我的客资”主列表页面状态。分页接口支持服务端视图参数 `view=follow_up_pending|unreachable|invalid`：待跟进覆盖待首跟、待判定和有效后仍在跟进，未联系上取 Lead 与 Opportunity 合并后的最新跟进结果 `unreachable`，已判无效取当前 `lead.status=invalid`。三个统计允许同一客资重叠，均限定当前 Partner 的客资范围；前端不得自行拼接状态条件。

`availableActions` 的结构为 `{ code, enabled }[]`。H5 只消费启用的大写编码：`SUBMITTER_SUPPLEMENT`、`URGE`、`CREATE_COMPLAINT`、`CREATE_APPEAL`。补充资料先读取详情并提交省、市、分类和至少一个产品的完整替换载荷。所有用户可见客资编号只展示 `leadNo`；缺失时显示“客资编号暂未生成”，不得回退到 `id` 或 `leadId`。

H5 在 `/complaints` 提供本人投诉记录分页页，入口仅对具备 `zsjos:lead-complaint:create` 的账号展示，直接访问也由路由守卫校验。列表覆盖加载、空数据、错误重试和下拉刷新状态，并继续以 `leadNo` 展示客资编号。

`partner-activity` 只返回 Partner 可见的当前状态、时间线、跟进摘要、返现、投诉和订单投影；不得下发销售、主管、审核人、部门、派单规则或内部备注。成交返现订单区只展示与返现 `orderId` 精确匹配的真实订单。

客资提交幂等键在租户内唯一，但重放结果还必须属于当前 Partner；其他 Partner、ADMIN 或历史主体已占用同一键时返回“该提交请求已处理”，不得返回原客资、激活或重复复核结果。Partner 客资、投诉和申诉附件上传到 `zsjos/lead/partner/{partnerAccountId}` 身份目录，引用时同时校验 Partner Account ID 与目录；相同数字的 ADMIN 文件不能被 Partner 引用。

## 返现、银行卡与提现

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/part-api/zsjos/cashback/my-page` | `zsjos:cashback:my-query` |
| GET | `/part-api/zsjos/cashback/my-summary` | `zsjos:cashback:my-query` |
| GET | `/part-api/zsjos/withdrawal/my-summary` | `zsjos:withdrawal:apply` |
| GET/POST | `/part-api/zsjos/withdrawal/my-cards` | `zsjos:withdrawal:apply` |
| PUT | `/part-api/zsjos/withdrawal/my-cards/{id}` | `zsjos:withdrawal:apply` |
| DELETE | `/part-api/zsjos/withdrawal/my-cards/{id}` | `zsjos:withdrawal:apply` |
| PUT | `/part-api/zsjos/withdrawal/my-cards/{id}/default` | `zsjos:withdrawal:apply` |
| POST | `/part-api/zsjos/withdrawal/apply` | `zsjos:withdrawal:apply` |
| PUT | `/part-api/zsjos/withdrawal/{id}/cancel` | `zsjos:withdrawal:apply` |
| GET | `/part-api/zsjos/withdrawal/my-page` | `zsjos:withdrawal:my-query` |
| GET | `/part-api/zsjos/withdrawal/my/{id}` | `zsjos:withdrawal:my-query` |

银行卡编辑只能修改本人 Partner 名下卡片；`cardNumber` 仅在换卡号时传完整值，未传时保留原卡号并继续脱敏展示。提现列表和详情返回 Partner 专用响应，只包含申请自身的编号、状态、金额、收款快照、提交/审核/支付时间及拒绝原因；不暴露流程实例、内部申请人、审批人、支付人、证明文件或银行流水字段。H5 统一读取 `applicationAmount`、`bankNameSnapshot`、`submittedAt`。提现可传 `bankCardId` 复用本人银行卡，也可直接传卡信息并用 `saveCard=true` 保存。服务端重新校验卡片归属并写入快照。V063 将一级课程分类缺失的默认规则设为有效客资 10.00 元、成交金额的 10%；产品自身规则非空时优先。

## 系统反馈

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/part-api/zsjos/feedback/portal` | 三类反馈入口和本人最近反馈 |
| GET | `/part-api/zsjos/feedback/form?type={type}` | 后端动态表单、配置版本和可用状态 |
| POST | `/part-api/zsjos/feedback/{type}/create` | `type=requirement|bug|support`，提交动态字段、`configVersion`、幂等键 |
| GET | `/part-api/zsjos/feedback/my-page` | query: `feedbackType`, `status`, `keyword`, `pageNo`, `pageSize` |
| GET | `/part-api/zsjos/feedback/{id}` | 本人反馈详情、字段和值快照、回复、处理结果 |
| PUT | `/part-api/zsjos/feedback/{id}/read` | `{version,idempotencyKey}` |
| POST | `/part-api/zsjos/feedback/{id}/reply` | `{content,attachmentIds,version,idempotencyKey}` |
| POST | `/part-api/zsjos/feedback/file/upload` | multipart `file` / Infra 文件 ID、名称、类型、大小和访问 URL |

反馈状态为 `APPROVING`、`APPROVAL_REJECTED`、`WAITING`、`IN_PROGRESS`、`COMPLETED`。Partner 反馈保存 `submitter_subject_type=PARTNER_ACCOUNT` 与 `partner_id`，对象读取、已读和回复都按 Partner Account ID 与 Partner ID 同时校验。需求反馈如启用 BPM 审批，Partner H5 提交返回明确的暂不支持错误；前端不降级成本地数据。

## 个人站内消息

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/part-api/zsjos/messages/page` | 本人消息分页 |
| GET | `/part-api/zsjos/messages/groups` | 服务端分组：全部、客资、反馈、提现 |
| GET | `/part-api/zsjos/messages/{id}` | 本人消息详情 |
| PUT | `/part-api/zsjos/messages/read` | JSON `{ "ids": [id] }`，幂等已读 |
| GET | `/part-api/zsjos/messages/unread-count` | 本人未读数 |

消息接口要求 PARTNER 身份，并由 `NotifyMessageService` 按 `user_type=PARTNER` 与 Partner Account ID 逐条限制所有权。`page` 支持 `group=lead|feedback|withdrawal`，Controller 映射为服务端 `bizType` 查询。消息字段沿用 System 响应：`templateTitle`、`templateSummary`、`templateContent`、`templateType`、`readStatus`、`createTime`。

H5 消息列表点击后进入 `/messages/{id}` 详情页；详情加载成功后按需调用已读接口，已读失败不遮蔽已经取得的消息内容。服务端返回受支持的业务动作时，详情页展示“查看相关业务”入口并跳转到对应的已授权 H5 页面。

H5 登录恢复后先重新读取 `permission-info`，再校验目标路由权限。客资、收益、提现、投诉记录和银行卡入口均按服务端权限显示；无权直接访问时进入 `/unauthorized`，不通过前端静态角色推断权限。

业务通知的短信通道按 `{userType,userId}` 解析收件人。PARTNER 手机号由 ZSJOS 的 Partner Account Provider 在账号和主体均启用时提供，短信日志保持 `user_type=PARTNER`；不得将 Partner Account ID 传给 ADMIN 用户查询。未知用户类型或不可用 Partner 收件人失败关闭。

## 联调前置

V072 在维护窗口执行账号迁移：预检通过后复制原 BCrypt 哈希，为 enabled/disabled Partner 建独立账号，迁移站内信，撤销旧 `part_time_partner` 角色关系，禁用旧 System 账号并删除其 ADMIN Token。converted Partner 不建账号，历史 Flowable 快照不改写。迁移文件生成不等于授权执行；应用到现有数据库仍需单独确认精确环境、备份和维护窗口。
