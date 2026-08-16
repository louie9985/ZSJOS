# 兼职端 App API

独立兼职前端统一使用 `/app-api`。响应均为 `CommonResult<T>`，成功时 `code=0`、业务数据在 `data`；请求需携带租户头和登录后返回的 Bearer token。兼职账号仍是 System 管理用户，但仅 `/app-api/zsjos/**` 按 ADMIN token 解析，普通 `/app-api/**` 仍保持 MEMBER 语义。

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

提现可传 `bankCardId` 复用本人银行卡，也可直接传卡信息并用 `saveCard=true` 保存。服务端重新校验卡片归属并写入快照。V063 将一级课程分类缺失的默认规则设为有效客资 10.00 元、成交金额的 10%；产品自身规则非空时优先。

## 联调前置

执行 V063 后，每个已有租户创建 `part_time_partner` 角色并回填现有兼职账号。新建/重新启用兼职自动补角色，转员工自动移除。`zsjos:withdrawal:review` 只创建权限点，财务账号由管理员手工授权。还必须在本地 BPM 发布 `script/bpm/zsjos_partner_withdrawal/1.0.0/process.bpmn20.xml`，流程 Key 为 `zsjos_partner_withdrawal`，任务 Key 为 `financeReview`。
