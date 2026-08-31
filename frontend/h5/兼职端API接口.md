下面是兼职端前端完整接口清单。统一前缀：

```
/part-api
```

兼职业务接口统一使用：

```
/part-api/zsjos/**
```

请求头：

```
tenant-id: 租户编号
Authorization: Bearer {accessToken}
Content-Type: application/json
```

统一响应格式：

```
{
  "code": 0,
  "data": {},
  "msg": "成功"
}
```

分页参数统一为：

```
pageNo: 页码，从 1 开始
pageSize: 每页数量，最大 200
```

完整文档也已放在：

[partner-app-api.md](/D:/ZSJ-OS/docs/api/partner-app-api.md)

## 一、认证

### 1. 兼职登录

```
POST /part-api/zsjos/auth/login
```

请求：

```
{
  "mobile": "13800138000",
  "password": "Password123",
  "platform": "MOBILE"
}
```

字段：

```
mobile: 手机号
password: 密码
platform: PC 或 MOBILE，默认 MOBILE
```

返回：

```
{
  "code": 0,
  "data": {
    "userId": 100,
    "accessToken": "xxx",
    "refreshToken": "xxx",
    "expiresTime": "2026-08-16T20:00:00",
    "clientId": "zsjos-pc"
  }
}
```

只有已完成邀请码激活并且账号与兼职主体均启用的 PARTNER 账号可以通过该接口登录。若手机号存在有效未激活邀请码但尚未激活，接口返回明确提示，H5 会引导用户进入首次登录激活。

### 1.1 首次登录激活

```
POST /part-api/zsjos/auth/activate
```

请求：

```
{
  "mobile": "13800138000",
  "password": "Password123",
  "confirmPassword": "Password123",
  "inviteCode": "ABCD1234",
  "platform": "MOBILE"
}
```

规则：

```
inviteCode: 四位大写英文字母 + 四位数字；前端会自动转大写
password/confirmPassword: 8-20 位且同时包含字母和数字，必须一致
```

服务端按手机号和邀请码匹配管理员生成的待激活记录。邀请码默认 7 天过期，使用后立即失效且不可复用；同一手机号被创建新邀请码时，旧待激活邀请码会直接失效。激活成功会创建兼职主体、独立 Partner 登录账号和归属运营记录，并直接返回登录 Token。

### 1.2 企业微信授权地址

```
GET /part-api/zsjos/auth/wecom-authorize-url?redirectUri={redirectUri}
```

返回企业微信授权跳转地址。前端先跳转到该地址，企微授权后回调到 `redirectUri` 携带 `code` 和 `state`。

### 1.3 企业微信登录

```
POST /part-api/zsjos/auth/wecom-login
```

请求：

```
{
  "code": "CODE_FROM_WECOM",
  "state": "STATE_FROM_WECOM",
  "platform": "MOBILE"
}
```

兼职端本地、开发和生产运行面均使用后端真实接口。接口缺失、权限失败、空列表、网络失败和业务错误都展示真实状态与重试入口，前端不再提供本地替代数据。

`platform` 可选，默认 `MOBILE`。若当前企微账号未绑定兼职主体，接口返回明确的未绑定错误。

### 1.4 企业微信消息回跳

```
GET /wecom/click?ticket={ticket}
```

`ticket` 为短期票据，不包含系统 token。页面会先解析票据，再按业务详情优先级跳转；若当前未登录，会先进入登录流程，登录成功后回到目标业务页。

### 2. 退出登录

```
POST /part-api/zsjos/auth/logout
```

只需要携带 `Authorization`。接口按 `PARTNER` 主体类型幂等撤销：token 已过期、已删除或重复调用均成功，ADMIN/MEMBER token 不会被删除。

主动退出请求不刷新 token，也不展示“访问令牌已过期”。用户确认后，无论服务端成功、401、网络失败或其他异常，H5 都清空 access token、refresh token、clientId、用户资料和权限，并直接进入登录页且不携带回跳地址。用户取消确认时保持当前会话不变。

### 3. 刷新 Token

```
POST /part-api/zsjos/auth/refresh-token?refreshToken={refreshToken}&clientId={clientId}
```

`clientId` 可选。

### 4. 获取权限信息

```
GET /part-api/zsjos/auth/permission-info
```

返回当前用户、角色、菜单和权限列表。

兼职端主要权限：

```
zsjos:partner:self-query
zsjos:lead:submit
zsjos:lead:query-submitted
zsjos:lead:submitter-supplement
zsjos:lead:urge
zsjos:lead-complaint:create
zsjos:lead:appeal:create
zsjos:cashback:my-query
zsjos:withdrawal:my-query
zsjos:withdrawal:apply
```

## 二、个人资料

### 1. 获取账号资料

```
GET /part-api/zsjos/profile/get
```

返回账号昵称、手机号、邮箱、头像、性别、部门、岗位、角色等信息。

### 2. 修改账号资料

```
PUT /part-api/zsjos/profile/update
```

请求：

```
{
  "nickname": "张三",
  "email": "test@example.com",
  "mobile": "13800138000",
  "sex": 1,
  "avatar": "https://example.com/avatar.png"
}
```

### 3. 修改密码

```
PUT /part-api/zsjos/profile/update-password
```

请求：

```
{
  "oldPassword": "OldPassword123",
  "newPassword": "NewPassword123"
}
```

新密码要求 8-20 位，并且同时包含字母和数字。

### 4. 获取兼职主体信息

```
GET /part-api/zsjos/partner/me
```

返回：

```
{
  "id": 1,
  "partnerNo": "PT20260001",
  "name": "张三",
  "mobile": "13800138000",
  "status": "enabled",
  "channelId": "channel001",
  "enabledAt": "2026-08-16T10:00:00",
  "disabledAt": null
}
```

`status` 可能值：

```
enabled
disabled
converted
```

### 5. 绑定企业微信

```
POST /part-api/zsjos/profile/wecom-bind
```

请求：

```
{
  "code": "CODE_FROM_WECOM",
  "state": "STATE_FROM_WECOM"
}
```

### 6. 通知渠道偏好

```
PUT /part-api/zsjos/profile/notify-channel
```

请求：

```
{
  "wecomEnabled": true
}
```

`wecomEnabled` 仅控制是否接收企业微信推送，不影响账号密码登录和企业微信绑定状态。

## 三、字典接口

客资来源和客资分类不能在前端写死，使用系统字典接口。

### 1. 客资来源

```
GET /app-api/system/dict-data/type?type=zsjos_lead_source_channel
```

### 2. 客资分类

```
GET /app-api/system/dict-data/type?type=zsjos_lead_category
```

返回格式：

```
[
  {
    "label": "线上广告",
    "value": "online_ad",
    "colorType": "primary",
    "cssClass": ""
  }
]
```

当前字典选项由管理员维护，V063 不会自动添加业务选项。

字典请求使用只携带 `tenant-id` 的公共数据客户端，不携带兼职 ADMIN Token。加载失败必须展示错误和重试入口，不得静默返回空数组或前端静态选项。

## 三-A、地区接口

```
GET /app-api/system/area/tree
```

地区同样使用只携带 `tenant-id` 的公共数据客户端。前端提交节点的 `selectionCode`，不使用内部 `id` 替代；不存在 `/part-api/zsjos/lead/area-tree` 接口。

## 四、课程和产品

### 获取课程目录

```
GET /part-api/zsjos/lead/product/catalog
```

返回：

```
{
  "categoryTree": [],
  "spus": [],
  "skus": []
}
```

主要字段：

```
categoryTree: 课程分类树
spus: SPU 课程
skus: SKU 课程规格
```

SPU 主要字段：

```
categoryId
categoryName
categoryPath
level1CategoryId
level1CategoryName
level2CategoryId
level2CategoryName
spuRef
spuName
attrs
```

SKU 主要字段：

```
spuRef
skuRef
skuName
attrValues
price
```

## 五、客资附件

```
POST /part-api/zsjos/lead/attachment/upload
```

请求类型：

```
multipart/form-data
```

文件字段名：

```
file
```

返回：

```
{
  "infraFileId": 10001,
  "fileUrl": "https://...",
  "originalName": "image.png",
  "contentType": "image/png",
  "fileSize": 102400
}
```

创建客资时使用返回的 `infraFileId`。

## 六、提交客资

### 创建客资

```
POST /part-api/zsjos/lead/create
```

请求示例：

```
{
  "name": "李四",
  "mobile": "13900139000",
  "wechatId": "lisi_wx",
  "provinceCode": "110000",
  "cityCode": "110100",
  "intendedProducts": [
    {
      "spuRef": "SPU001",
      "skuRef": "SKU001",
      "spuUnknown": false,
      "skuUnknown": false,
      "primary": true
    }
  ],
  "sourceChannel": "online_ad",
  "leadCategory": "adult_education",
  "remark": "客户咨询课程",
  "attachments": [
    {
      "infraFileId": 10001
    }
  ],
  "dispatchMode": "auto",
  "idempotencyKey": "partner-submit-20260816-0001"
}
```

字段说明：

```
name: 客户姓名，必填
mobile: 手机号，可选
wechatId: 微信号，可选
provinceCode: 省编码，必填
cityCode: 市编码，必填
intendedProducts: 意向课程，至少 1 条
sourceChannel: 必须是启用的客资来源字典值
leadCategory: 必须是启用的客资分类字典值
remark: 备注，最多 1000 字
attachments: 最多 9 个附件
dispatchMode: 兼职端固定传 auto
idempotencyKey: 幂等键，必填
```

产品字段：

```
spuRef: SPU 编号
skuRef: SKU 编号
spuUnknown: 是否未知 SPU
skuUnknown: 是否未知 SKU
primary: 是否主意向课程
```

限制：

```
primary=true 必须且只能有一条
兼职账号只能使用 dispatchMode=auto
```

返回示例：

```
{
  "leadId": 10001,
  "leadNo": "KZ202608160001",
  "reviewId": null,
  "outcome": "activated",
  "assignmentStatus": "pending",
  "pendingAssigneeUserId": null
}
```

`outcome` 常见值：

```
activated
review_pending
duplicate_rejected
duplicate_auto_closed
```

## 七、本人提交客资列表

```
GET /part-api/zsjos/lead/inbox/submitted/page
```

查询参数：

```
pageNo
pageSize
keyword
status
assignmentStatus
sourceChannel
leadCategory
submittedAt
```

该接口服务端强制使用当前兼职账号作为提交人，不需要前端传 `audience`。

返回：

```
{
  "total": 1,
  "list": [
    {
      "id": 10001,
      "leadNo": "KZ202608160001",
      "submittedName": "李四",
      "submittedMobile": "13900139000",
      "sourceChannel": "online_ad",
      "leadCategory": "adult_education",
      "status": "valid",
      "assignmentStatus": "owned",
      "ownerUserName": "销售人员",
      "submittedAt": "2026-08-16T10:00:00",
      "intendedProducts": [],
      "attachments": [],
      "availableActions": [
        { "code": "SUBMITTER_SUPPLEMENT", "enabled": true },
        { "code": "URGE", "enabled": false }
      ]
    }
  ]
}
```

### 获取客资详情

```
GET /part-api/zsjos/lead/get?id={leadId}
```

只能查看本人有提交人历史权限的客资。

所有页面只把非空 `leadNo` 展示为客资编号；缺失时显示“客资编号暂未生成”，不能回退展示 `id` 或 `leadId`。现有后端的催办/投诉动作编码为 `SUBMITTER_URGE`、`SUBMITTER_COMPLAINT`，H5 兼容这两个编码，同时支持后续统一契约 `URGE`、`CREATE_COMPLAINT`、`CREATE_APPEAL`；前端不根据状态自行补显示写操作按钮。

### 获取 Partner 客资筛选项

```
GET /part-api/zsjos/lead/partner-filter-options
```

返回当前 Partner 可用的状态、主产品、申诉状态和订单审核状态筛选项。筛选项由后端生成，前端不维护静态生产选项。

### 获取客资处理进度

```
GET /part-api/zsjos/lead/{leadId}/partner-activity
```

返回 Partner 本人可见的当前状态、时间线、跟进摘要、返现、投诉和订单投影。接口不得下发销售、主管、审核人、部门、派单规则或内部备注；收益详情只展示与返现 `orderId` 精确匹配的真实订单。

### 补充本人客资

```
PUT /part-api/zsjos/lead/{id}/submitter-supplement
```

请求：

```
{
  "provinceCode": "110000",
  "cityCode": "110100",
  "leadCategory": "adult_education",
  "intendedProducts": [
    {
      "spuRef": "SPU001",
      "skuRef": "SKU001",
      "spuUnknown": false,
      "skuUnknown": false,
      "primary": true
    }
  ],
  "remark": "补充客户意向",
  "idempotencyKey": "supplement-10001-001"
}
```

可更新：

```
省市
客资分类
意向课程
备注
```

不能修改客户姓名、手机号和微信号。

### 催办客资

```
POST /part-api/zsjos/lead/{id}/urge
```

请求：

```
{
  "reason": "客户等待跟进，请尽快联系"
}
```

同一客资、同一提交人、同一自然日最多催办一次。

## 八、投诉

### 创建投诉

```
POST /part-api/zsjos/lead-complaint/lead/{leadId}
```

请求：

```
{
  "reason": "客户信息被错误处理",
  "evidenceFileIds": [10001, 10002],
  "idempotencyKey": "complaint-10001-001"
}
```

限制：

```
reason 最多 1000 字
evidenceFileIds 最多 9 个
idempotencyKey 必填
```

### 查询本人投诉历史

```
GET /part-api/zsjos/lead-complaint/my-page
```

查询参数：

```
pageNo
pageSize
status
```

返回投诉记录和处理结果。

## 九、申诉

### 查询客资申诉记录

```
GET /part-api/zsjos/lead/appeal/lead/{leadId}/list
```

### 上传申诉附件

```
POST /part-api/zsjos/lead/appeal/attachment/upload
```

请求类型：

```
multipart/form-data
file: 文件
```

### 提交申诉

```
POST /part-api/zsjos/lead/appeal/lead/{leadId}/submit
```

请求：

```
{
  "reason": "客户判定结果有误",
  "idempotencyKey": "appeal-10001-001",
  "attachments": [
    {
      "infraFileId": 10001
    }
  ]
}
```

限制：

```
reason 最多 1000 字
attachments 最多 9 个
idempotencyKey 必填
```

## 十、返现

### 查询本人返现分页

```
GET /part-api/zsjos/cashback/my-page
```

查询参数：

```
pageNo
pageSize
type
status
```

`type`：

```
valid
deal
```

`status`：

```
pending_settlement
available
withdrawing
withdrawn
cancelled
```

返回记录包括：

```
id
cashbackNo
type
status
leadId
orderId
orderItemId
productRefSnapshot
productNameSnapshot
baseAmount
rateSnapshot
amount
observationDaysSnapshot
generatedAt
availableAt
settledAt
cancelledAt
cancelReason
```

### 返现汇总

```
GET /part-api/zsjos/cashback/my-summary
```

返回：

```
{
  "totalAmount": 100.00,
  "pendingAmount": 20.00,
  "availableAmount": 80.00,
  "withdrawingAmount": 0.00,
  "withdrawnAmount": 50.00,
  "counts": {
    "pending_settlement": 1,
    "available": 2,
    "withdrawn": 3
  }
}
```

## 十一、提现和银行卡

### 提现汇总

```
GET /part-api/zsjos/withdrawal/my-summary
```

返回：

```
{
  "availableAmount": 80.00,
  "minimumAmount": 10.00,
  "selectableCount": 2,
  "canApply": true
}
```

V063 默认一级课程分类规则：

```
有效客资返现：10.00 元
成交返现比例：10%
```

最低提现金额由配置项控制，当前默认值为 `10.00`。

### 查询本人银行卡

```
GET /part-api/zsjos/withdrawal/my-cards
```

返回：

```
[
  {
    "id": 1,
    "accountName": "张三",
    "maskedCardNumber": "****1234",
    "bankName": "中国银行",
    "branchName": "某某支行",
    "defaultCard": true
  }
]
```

### 新增银行卡

```
POST /part-api/zsjos/withdrawal/my-cards
```

请求：

```
{
  "accountName": "张三",
  "cardNumber": "6217000000001234",
  "bankName": "中国银行",
  "branchName": "某某支行"
}
```

### 删除银行卡

```
DELETE /part-api/zsjos/withdrawal/my-cards/{id}
```

只能删除本人银行卡。

### 设置默认银行卡

```
PUT /part-api/zsjos/withdrawal/my-cards/{id}/default
```

### 修改银行卡

```
PUT /part-api/zsjos/withdrawal/my-cards/{id}
```

请求：

```
{
  "accountName": "张三",
  "cardNumber": "6217000000001234",
  "bankName": "中国银行",
  "branchName": "某某支行"
}
```

`cardNumber` 仅在换卡号时传完整值；不传时服务端保留原卡号。只能修改本人 Partner 名下银行卡。

### 提交提现申请

```
POST /part-api/zsjos/withdrawal/apply
```

使用已保存银行卡：

```
{
  "cashbackIds": [1001, 1002],
  "bankCardId": 1,
  "saveCard": false
}
```

直接传银行卡：

```
{
  "cashbackIds": [1001, 1002],
  "accountName": "张三",
  "cardNumber": "6217000000001234",
  "bankName": "中国银行",
  "branchName": "某某支行",
  "saveCard": true
}
```

字段：

```
cashbackIds: 要提现的返现记录 ID，必填
bankCardId: 已保存的本人银行卡 ID，可选
accountName/cardNumber/bankName/branchName: 不使用 bankCardId 时传
saveCard: 是否保存直接提交的银行卡
```

服务端会重新验证：

```
返现记录是否属于本人
返现状态是否为 available
银行卡是否属于本人
是否达到最低提现金额
返现是否已被其他提现锁定
```

### 查询本人提现分页

```
GET /part-api/zsjos/withdrawal/my-page
```

查询参数：

```
pageNo
pageSize
status
```

### 查询提现详情

```
GET /part-api/zsjos/withdrawal/my/{id}
```

列表和详情金额、银行快照和申请时间字段统一为：

```
applicationAmount
bankNameSnapshot
submittedAt
```

### 取消提现

```
PUT /part-api/zsjos/withdrawal/{id}/cancel
```

只有待财务审核状态允许取消。

## 十二、个人站内消息

```
GET /part-api/zsjos/messages/page
GET /part-api/zsjos/messages/groups
GET /part-api/zsjos/messages/{id}
PUT /part-api/zsjos/messages/read
GET /part-api/zsjos/messages/unread-count
```

`groups` 返回服务端维护的分组：全部、客资、反馈、提现。分页支持 `group=lead|feedback|withdrawal`，Controller 映射为服务端 `bizType` 查询。

已读请求体为 `{ "ids": [1, 2] }`。消息沿用 System 字段 `templateTitle`、`templateSummary`、`templateContent`、`templateType`、`readStatus`、`createTime`。消息接口要求 PARTNER 身份，并按 `user_type=PARTNER` 与当前 Partner Account ID 校验所有权。

普通业务请求的 HTTP 401 和业务 `code=401` 进入同一个单航班刷新流程。刷新请求不进入普通拦截器，原请求最多重放一次；刷新失败时全部等待请求结束、认证状态清空，并带原目标地址统一返回登录页。主动退出请求明确跳过刷新，清理后不保留回跳地址。

## 十三、首页统计与排行榜

### 首页统计

```
GET /part-api/zsjos/partner/home-statistics
```

查询参数：

```
period: today | week | month | year | total
```

返回：

```
period
leadCount
withdrawnAmount
validLeadCount
convertedLeadCount
```

### 首页统计明细

```
GET /part-api/zsjos/partner/home-statistics/details
```

查询参数：

```
period: today | week | month | year | total
metric: lead_count | withdrawn_amount | valid_lead_count | converted_lead_count
pageNo
pageSize
```

返回真实客资或已打款提现分页。客资明细只展示 `leadNo`，内部 ID 仅用于详情路由。

### 排行榜配置与数据

```
GET /part-api/zsjos/partner/leaderboard/config
GET /part-api/zsjos/partner/leaderboard
```

排行榜查询参数：

```
period: today | week | month | total
type: estimated_income | withdrawn_amount | lead_count | valid_lead_count
pageNo
pageSize
```

返回排名、Top3、本人名次、上一名差距和 Top10 差距。排行榜口径和脱敏策略由后端返回，前端不本地生成排名。

## 十四、系统反馈

```
GET /part-api/zsjos/feedback/portal
GET /part-api/zsjos/feedback/form?type={type}
POST /part-api/zsjos/feedback/{type}/create
GET /part-api/zsjos/feedback/my-page
GET /part-api/zsjos/feedback/{id}
PUT /part-api/zsjos/feedback/{id}/read
POST /part-api/zsjos/feedback/{id}/reply
POST /part-api/zsjos/feedback/file/upload
```

`type` 路径值为 `requirement`、`bug`、`support`，查询/响应中的反馈类型为 `REQUIREMENT`、`BUG`、`SUPPORT`。

反馈状态：

```
APPROVING
APPROVAL_REJECTED
WAITING
IN_PROGRESS
COMPLETED
```

创建反馈请求：

```
{
  "values": {
    "title": "页面无法保存"
  },
  "configVersion": 0,
  "idempotencyKey": "feedback-20260830-001"
}
```

已读和回复都需要携带当前详情返回的 `version` 与幂等键。附件上传使用 `multipart/form-data` 的 `file` 字段，返回文件 ID、名称、类型、大小和访问 URL。Partner 反馈由后端保存 `submitter_subject_type=PARTNER_ACCOUNT` 与 `partner_id`，对象读取、已读和回复同时校验 Partner Account ID 与 Partner ID。需求反馈如启用 BPM 审批，兼职端提交会返回明确的暂不支持错误。

## 十五、前端需要处理的状态

```
401：未登录、Token 过期，需要刷新或重新登录
403：没有 part_time_partner 角色或缺少功能权限
业务错误：CommonResult.code 非 0，展示 msg
分页空数据：data.list 为空数组，不能当成异常
上传失败：保留重试入口，不要提交无效 infraFileId
```

## 十六、字段适配与真实接口说明

### 已有接口字段适配

H5 按 ZSJOS Partner 响应 VO 使用真实字段：课程目录使用分类节点 `id/name`、分类路径对象数组、属性数组、SKU `attrValues` 和 `price`；客资详情使用处理时限、来源、微信号、关闭信息、商机及课程 SKU/属性/价格；申诉使用 `roundNo`、`reviewStage`、证据、处理人、裁决信息和 `canSubmitNextRound`；投诉使用双方证据、销售/处理人和处理时间；首页、排行榜、反馈、收益、提现、消息、个人资料和定位页使用后端返回字段。生产环境不使用这些字段的静态替代值。

详细请求参数、响应字段、权限、错误和数据范围要求见：

`docs/api/partner-app-api.md`
`frontend/h5/docs/real-data.md`
