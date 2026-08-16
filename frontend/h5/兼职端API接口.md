下面是兼职端前端完整接口清单。统一前缀：

```
/app-api
```

兼职业务接口统一使用：

```
/app-api/zsjos/**
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
POST /app-api/zsjos/auth/login
```

请求：

```
{
  "username": "partner001",
  "password": "Password123",
  "platform": "PC"
}
```

字段：

```
username: 账号，4-32 位，只允许字母、数字、下划线
password: 密码
platform: PC 或 MOBILE，默认 PC
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

只有拥有 `part_time_partner` 角色的账号可以通过该接口登录。

### 2. 退出登录

```
POST /app-api/zsjos/auth/logout
```

只需要携带 `Authorization`。

### 3. 刷新 Token

```
POST /app-api/zsjos/auth/refresh-token?refreshToken={refreshToken}&clientId={clientId}
```

`clientId` 可选。

### 4. 获取权限信息

```
GET /app-api/zsjos/auth/permission-info
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
GET /app-api/zsjos/profile/get
```

返回账号昵称、手机号、邮箱、头像、性别、部门、岗位、角色等信息。

### 2. 修改账号资料

```
PUT /app-api/zsjos/profile/update
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
PUT /app-api/zsjos/profile/update-password
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
GET /app-api/zsjos/partner/me
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

## 四、课程和产品

### 获取课程目录

```
GET /app-api/zsjos/lead/product/catalog
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
POST /app-api/zsjos/lead/attachment/upload
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
POST /app-api/zsjos/lead/create
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
GET /app-api/zsjos/lead/inbox/submitted/page
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
      "availableActions": []
    }
  ]
}
```

### 获取客资详情

```
GET /app-api/zsjos/lead/get?id={leadId}
```

只能查看本人有提交人历史权限的客资。

### 补充本人客资

```
PUT /app-api/zsjos/lead/{id}/submitter-supplement
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
POST /app-api/zsjos/lead/{id}/urge
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
POST /app-api/zsjos/lead-complaint/lead/{leadId}
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
GET /app-api/zsjos/lead-complaint/my-page
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
GET /app-api/zsjos/lead/appeal/lead/{leadId}/list
```

### 上传申诉附件

```
POST /app-api/zsjos/lead/appeal/attachment/upload
```

请求类型：

```
multipart/form-data
file: 文件
```

### 提交申诉

```
POST /app-api/zsjos/lead/appeal/lead/{leadId}/submit
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
GET /app-api/zsjos/cashback/my-page
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
GET /app-api/zsjos/cashback/my-summary
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
GET /app-api/zsjos/withdrawal/my-summary
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
GET /app-api/zsjos/withdrawal/my-cards
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
POST /app-api/zsjos/withdrawal/my-cards
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
DELETE /app-api/zsjos/withdrawal/my-cards/{id}
```

只能删除本人银行卡。

### 设置默认银行卡

```
PUT /app-api/zsjos/withdrawal/my-cards/{id}/default
```

### 提交提现申请

```
POST /app-api/zsjos/withdrawal/apply
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
GET /app-api/zsjos/withdrawal/my-page
```

查询参数：

```
pageNo
pageSize
status
```

### 查询提现详情

```
GET /app-api/zsjos/withdrawal/my/{id}
```

### 取消提现

```
PUT /app-api/zsjos/withdrawal/{id}/cancel
```

只有待财务审核状态允许取消。

## 十二、前端需要处理的状态

```
401：未登录、Token 过期，需要刷新或重新登录
403：没有 part_time_partner 角色或缺少功能权限
业务错误：CommonResult.code 非 0，展示 msg
分页空数据：data.list 为空数组，不能当成异常
上传失败：保留重试入口，不要提交无效 infraFileId
```