# 兼职端 App API

独立兼职前端统一使用 `/app-api`。响应均为 `CommonResult<T>`，成功时 `code=0`、业务数据在 `data`。`/app-api/zsjos/**` 携带租户头和登录后返回的 Bearer token，并按 ADMIN token 解析；System 公共字典和地区接口只携带 `tenant-id`，不得携带 ADMIN token，避免普通 `/app-api/**` 的 MEMBER 解析边界误判。

当前 H5 有 37 个实际 HTTP 调用契约；原第 38 个企微登录能力按最新口径只保留入口路径，不形成后端调用。

## 认证与个人信息

| Method | Path | Request / Result |
| --- | --- | --- |
| POST | `/app-api/zsjos/auth/login` | `AuthLoginReqVO` / access token、refresh token、过期时间 |
| POST | `/app-api/zsjos/auth/logout` | Bearer token / `Boolean` |
| POST | `/app-api/zsjos/auth/refresh-token` | query: `refreshToken`, optional `clientId` / 新 token |
| GET | `/app-api/zsjos/auth/permission-info` | 当前用户、角色和权限 |
| GET | `/app-api/zsjos/profile/get` | System 账号资料 |
| PUT | `/app-api/zsjos/profile/update` | `UserProfileUpdateReqVO` |
| PUT | `/app-api/zsjos/profile/update-password` | `UserProfileUpdatePasswordReqVO` |
| GET | `/app-api/zsjos/partner/me` | 兼职主体资料，需 `zsjos:partner:self-query` |

登录和刷新会校验 `part_time_partner` 角色，普通后台账号不能从兼职端取得有效会话。
本期保留“企业微信登录”入口作为后续路径占位，点击只提示暂未开放，不发起 OAuth，也不调用 `/zsjos/auth/wecom-login`。

Token 恢复由 HTTP 客户端内部单航班完成。HTTP 401 和响应体 `code=401` 共用一次刷新；刷新请求使用原始 Axios，只携带 `tenant-id`、refresh token 和移动端 `clientId`。原请求最多重放一次，刷新失败时所有等待请求都会失败并统一清理登录状态。

## System 公共参考数据

| Method | Path | Authentication |
| --- | --- | --- |
| GET | `/app-api/system/dict-data/type?type={type}` | 只携带 `tenant-id` |
| GET | `/app-api/system/area/tree` | 只携带 `tenant-id` |

地区提交使用节点的 `selectionCode`，不能使用树节点内部主键替代。字典或地区加载失败必须显示错误和重试入口，不得静默回退为静态选项或空数组。

## 客资

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/app-api/zsjos/lead/product/catalog` | `zsjos:lead:submit` |
| POST | `/app-api/zsjos/lead/attachment/upload` | `zsjos:lead:submit` |
| POST | `/app-api/zsjos/lead/create` | `zsjos:lead:submit` |
| GET | `/app-api/zsjos/lead/inbox/submitted/page` | `zsjos:lead:query-submitted` |
| GET | `/app-api/zsjos/lead/get?id={id}` | `zsjos:lead:query-submitted` |
| PUT | `/app-api/zsjos/lead/{id}/submitter-supplement` | `zsjos:lead:submitter-supplement` |
| POST | `/app-api/zsjos/lead/{id}/urge` | `zsjos:lead:urge` |
| POST | `/app-api/zsjos/lead-complaint/lead/{leadId}` | `zsjos:lead-complaint:create` |
| GET | `/app-api/zsjos/lead-complaint/my-page` | `zsjos:lead-complaint:create` |
| GET | `/app-api/zsjos/lead/appeal/lead/{leadId}/list` | `zsjos:lead:appeal:create` |
| POST | `/app-api/zsjos/lead/appeal/lead/{leadId}/submit` | `zsjos:lead:appeal:create` |
| POST | `/app-api/zsjos/lead/appeal/attachment/upload` | `zsjos:lead:appeal:create` |

来源和分类不使用前端静态值。分别调用 `GET /app-api/system/dict-data/type?type=zsjos_lead_source_channel` 和 `GET /app-api/system/dict-data/type?type=zsjos_lead_category` 获取启用项。

`availableActions` 的结构为 `{ code, enabled }[]`。H5 只消费启用的大写编码：`SUBMITTER_SUPPLEMENT`、`URGE`、`CREATE_COMPLAINT`、`CREATE_APPEAL`。补充资料先读取详情并提交省、市、分类和至少一个产品的完整替换载荷。所有用户可见客资编号只展示 `leadNo`；缺失时显示“客资编号暂未生成”，不得回退到 `id` 或 `leadId`。

## 返现、银行卡与提现

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/app-api/zsjos/cashback/my-page` | `zsjos:cashback:my-query` |
| GET | `/app-api/zsjos/cashback/my-summary` | `zsjos:cashback:my-query` |
| GET | `/app-api/zsjos/withdrawal/my-summary` | `zsjos:withdrawal:apply` |
| GET/POST | `/app-api/zsjos/withdrawal/my-cards` | `zsjos:withdrawal:apply` |
| DELETE | `/app-api/zsjos/withdrawal/my-cards/{id}` | `zsjos:withdrawal:apply` |
| PUT | `/app-api/zsjos/withdrawal/my-cards/{id}/default` | `zsjos:withdrawal:apply` |
| POST | `/app-api/zsjos/withdrawal/apply` | `zsjos:withdrawal:apply` |
| PUT | `/app-api/zsjos/withdrawal/{id}/cancel` | `zsjos:withdrawal:apply` |
| GET | `/app-api/zsjos/withdrawal/my-page` | `zsjos:withdrawal:my-query` |
| GET | `/app-api/zsjos/withdrawal/my/{id}` | `zsjos:withdrawal:my-query` |

提现列表和详情统一读取 `applicationAmount`、`bankNameSnapshot`、`submittedAt`。提现可传 `bankCardId` 复用本人银行卡，也可直接传卡信息并用 `saveCard=true` 保存。服务端重新校验卡片归属并写入快照。V063 将一级课程分类缺失的默认规则设为有效客资 10.00 元、成交金额的 10%；产品自身规则非空时优先。

## 个人站内消息

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/app-api/zsjos/messages/page` | 本人消息分页 |
| GET | `/app-api/zsjos/messages/{id}` | 本人消息详情 |
| PUT | `/app-api/zsjos/messages/read` | JSON `{ "ids": [id] }`，幂等已读 |
| GET | `/app-api/zsjos/messages/unread-count` | 本人未读数 |

四个接口要求 ADMIN 身份和 `part_time_partner` 角色，并由 `NotifyMessageService` 按当前用户逐条限制所有权。消息字段沿用 System 响应：`templateTitle`、`templateSummary`、`templateContent`、`templateType`、`readStatus`、`createTime`。个人消息是登录用户公共能力，不复制为独立 ZSJOS 菜单权限。

## 联调前置

执行到 V071 后，每个租户的 `part_time_partner` 恰好拥有 10 个 ZSJOS 权限：`partner:self-query`、客资提交/本人查询/补充/催办/投诉/申诉、本人返现查询、本人提现查询/申请；不得拥有 `zsjos:lead:query`。新建/重新启用兼职自动补角色，转员工自动移除。提现流程仍需单独发布 BPM Key `zsjos_partner_withdrawal`。V071 只生成和验证；应用到现有数据库必须另行确认。
