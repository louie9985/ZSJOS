# 支付主体配置功能技术设计

> 本文保留历史设计/阶段总结。2026-09-18 已明确交易路由：真实 SKU 关联解析产品，同主体合并沿用该主体，不同主体合并使用当前租户默认主体；支付与退款统一使用订单快照，不再静默回退全局商户。当前实现契约见[支付主体 API](../api/payment-subject.md)，历史流程图不作为现行接口或配置字段依据。

## 1. 背景与目标

### 1.1 业务背景
- 系统对接通联支付，通联提供了两套收款主体配置（学校、公司）
- 不同产品需要使用不同的收款主体进行资金结算
- 需要支持财务主管灵活配置产品的收款主体

### 1.2 设计目标
1. **配置管理**：集中管理支付主体配置信息（商户号、密钥等）
2. **灵活关联**：产品可灵活配置收款主体，支持批量操作
3. **安全可靠**：私钥加密存储，订单保存主体快照支持退款
4. **权限控制**：默认仅财务主管可操作，可扩展其他角色

## 2. 数据模型设计

### 2.1 核心实体关系

```
┌─────────────────────┐         ┌──────────────────────────┐
│ zsjos_payment_subject│◄────────│ zsjos_product_payment_   │
│  (支付主体配置表)    │ 1     n │   subject                │
│                     │         │  (产品支付主体关联表)     │
│ - subject_code (PK) │         │                          │
│ - subject_name      │         │ - product_id             │
│ - cusid             │         │ - subject_code (FK)      │
│ - appid             │         └──────────┬───────────────┘
│ - merchant_private_ │                    │
│   key (加密)        │                    │ n
│ - platform_public_  │                    │
│   key               │                    ▼
│ - is_default        │         ┌──────────────────────────┐
│ - status            │         │ zsjos_product            │
└─────────────────────┘         │  (产品表)                │
         │                      │                          │
         │                      │ - payment_subject_code   │
         │                      │   (冗余字段)             │
         │                      └──────────────────────────┘
         │
         │ 1
         │
         ▼ n
┌─────────────────────┐
│ zsjos_payment_order │
│  (支付订单表)        │
│                     │
│ - subject_snapshot_ │
│   json (JSON)       │
│   (主体配置快照)     │
└─────────────────────┘
```

### 2.2 表结构详细设计

#### zsjos_payment_subject（支付主体配置表）

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | bigint | 主键 | PK, AUTO_INCREMENT |
| subject_code | varchar(64) | 主体编码 | UK, NOT NULL |
| subject_name | varchar(128) | 主体名称（公司全称） | NOT NULL |
| subject_type | varchar(32) | 主体类型 | DEFAULT 'TONGLIAN' |
| cusid | varchar(128) | 通联商户号 | NULL |
| appid | varchar(128) | 通联应用ID | NULL |
| merchant_private_key | text | 商户私钥（加密） | NULL |
| platform_public_key | text | 平台公钥 | NULL |
| notify_url | varchar(512) | 异步通知地址 | NULL |
| return_url | varchar(512) | 同步回调地址 | NULL |
| is_default | bit(1) | 是否默认主体 | DEFAULT 0 |
| status | tinyint | 状态 | 0=停用, 1=启用 |
| remark | varchar(500) | 备注 | NULL |
| creator | varchar(64) | 创建者 | DEFAULT '' |
| create_time | datetime | 创建时间 | NOT NULL |
| updater | varchar(64) | 更新者 | DEFAULT '' |
| update_time | datetime | 更新时间 | NOT NULL |
| deleted | bit(1) | 软删除标记 | DEFAULT 0 |
| tenant_id | bigint | 租户ID | DEFAULT 0 |

**索引设计**：
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_tenant_code` (`tenant_id`, `subject_code`, `deleted`)
- KEY `idx_tenant_status` (`tenant_id`, `status`)

**业务规则**：
1. 每个租户至少有一个默认主体（is_default=1）
2. subject_code 在租户内唯一
3. merchant_private_key 必须加密存储
4. 停用主体前需检查是否有产品关联

#### zsjos_product_payment_subject（产品支付主体关联表）

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | bigint | 主键 | PK, AUTO_INCREMENT |
| product_id | bigint | 产品ID | NOT NULL |
| subject_code | varchar(64) | 支付主体编码 | NOT NULL |
| creator | varchar(64) | 创建者 | DEFAULT '' |
| create_time | datetime | 创建时间 | NOT NULL |
| updater | varchar(64) | 更新者 | DEFAULT '' |
| update_time | datetime | 更新时间 | NOT NULL |
| deleted | bit(1) | 软删除标记 | DEFAULT 0 |
| tenant_id | bigint | 租户ID | DEFAULT 0 |

**索引设计**：
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_tenant_product_subject` (`tenant_id`, `product_id`, `deleted`)
- KEY `idx_tenant_subject` (`tenant_id`, `subject_code`)
- KEY `idx_product` (`product_id`)

**业务规则**：
1. 一个产品同时只能关联一个支付主体
2. 关联的 subject_code 必须存在于 zsjos_payment_subject
3. 修改关联时，更新 zsjos_product.payment_subject_code 冗余字段

#### zsjos_product 扩展字段

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| payment_subject_code | varchar(64) | 支付主体编码（冗余） | NULL |

**说明**：
- 冗余字段，从关联表同步，便于查询
- 为空时使用默认支付主体

#### zsjos_payment_order 扩展字段

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| subject_snapshot_json | json | 支付主体配置快照 | NULL |

**快照 JSON 结构**：
```json
{
  "subject_code": "school",
  "subject_name": "合肥中世健职业技能培训学校有限公司",
  "cusid": "8888888888888888",
  "appid": "xxxxxxxx",
  "merchant_private_key": "MII...(加密)",
  "platform_public_key": "MII...",
  "notify_url": "https://api.example.com/notify",
  "snapshot_time": "2026-09-15T12:00:00"
}
```

**说明**：
- 订单创建时保存支付主体完整配置
- 用于订单退款时使用原支付主体
- 即使主体配置变更，历史订单仍可正常退款

## 3. 业务流程设计

### 3.1 支付主体配置流程

```
┌─────────────┐
│ 财务主管     │
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│ 1. 进入支付主体管理页面  │
└──────┬──────────────────┘
       │
       ├─→ 新增主体
       │   ├── 填写主体信息（商户号、密钥等）
       │   ├── 私钥前端加密传输
       │   └── 后端再次加密存储
       │
       ├─→ 修改主体
       │   ├── 查询主体信息（私钥脱敏显示）
       │   ├── 修改字段
       │   └── 保存（私钥加密）
       │
       ├─→ 启用/停用主体
       │   ├── 检查是否有产品关联（停用时）
       │   └── 更新状态
       │
       └─→ 设置默认主体
           ├── 取消原默认主体
           └── 设置新默认主体
```

### 3.2 产品配置支付主体流程

```
┌─────────────┐
│ 财务主管     │
└──────┬──────┘
       │
       ▼
┌────────────────────────────┐
│ 1. 进入产品支付主体配置页面 │
└──────┬─────────────────────┘
       │
       ├─→ 单个产品配置
       │   ├── 查询产品信息
       │   ├── 选择支付主体
       │   ├── 保存关联关系
       │   └── 更新产品冗余字段
       │
       └─→ 批量配置
           ├── 勾选多个产品
           ├── 选择目标支付主体
           ├── 预览影响范围
           ├── 确认批量操作
           ├── 批量插入/更新关联表
           └── 批量更新产品冗余字段
```

### 3.3 支付订单创建流程（集成点）

```
┌─────────────┐
│ 用户下单     │
└──────┬──────┘
       │
       ▼
┌────────────────────────────┐
│ 1. 创建支付订单             │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 2. 查询产品支付主体配置     │
│    - product.payment_subject_code │
│    - 为空则使用默认主体     │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 3. 查询主体完整配置         │
│    - cusid, appid, keys... │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 4. 保存主体配置快照到订单   │
│    - subject_snapshot_json │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 5. 调用通联支付接口         │
│    - 使用主体配置签名       │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 6. 返回支付结果             │
└────────────────────────────┘
```

### 3.4 订单退款流程（使用快照）

```
┌─────────────┐
│ 发起退款     │
└──────┬──────┘
       │
       ▼
┌────────────────────────────┐
│ 1. 查询支付订单             │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 2. 读取主体配置快照         │
│    - subject_snapshot_json │
│    - 使用创建订单时的主体配置│
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 3. 解密私钥                 │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 4. 调用通联退款接口         │
│    - 使用快照中的主体配置   │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ 5. 返回退款结果             │
└────────────────────────────┘
```

## 4. 接口设计

### 4.1 支付主体管理接口

#### 4.1.1 创建支付主体
```
POST /admin-api/zsjos/payment-subject/create

Request:
{
  "subjectCode": "school",
  "subjectName": "合肥中世健职业技能培训学校有限公司",
  "subjectType": "TONGLIAN",
  "cusid": "8888888888888888",
  "appid": "xxxxxxxx",
  "merchantPrivateKey": "MII...(前端加密)",
  "platformPublicKey": "MII...",
  "notifyUrl": "https://api.example.com/notify",
  "returnUrl": "https://www.example.com/pay/return",
  "isDefault": false,
  "status": 1,
  "remark": ""
}

Response:
{
  "code": 0,
  "data": 123,  // 主体ID
  "msg": "success"
}
```

#### 4.1.2 更新支付主体
```
PUT /admin-api/zsjos/payment-subject/update

Request: 同 create，增加 id 字段

Response: 同 create
```

#### 4.1.3 删除支付主体
```
DELETE /admin-api/zsjos/payment-subject/delete?id=123

Response:
{
  "code": 0,
  "data": true,
  "msg": "success"
}

业务规则：
- 默认主体不可删除
- 有产品关联的主体不可删除（需先解除关联）
```

#### 4.1.4 查询支付主体详情
```
GET /admin-api/zsjos/payment-subject/get?id=123

Response:
{
  "code": 0,
  "data": {
    "id": 123,
    "subjectCode": "school",
    "subjectName": "合肥中世健职业技能培训学校有限公司",
    "merchantPrivateKey": "MII***********",  // 脱敏
    "...": "..."
  }
}
```

#### 4.1.5 查询支付主体分页列表
```
GET /admin-api/zsjos/payment-subject/page?pageNo=1&pageSize=10&subjectName=&status=

Response:
{
  "code": 0,
  "data": {
    "list": [...],
    "total": 2
  }
}
```

#### 4.1.6 启用/停用支付主体
```
PUT /admin-api/zsjos/payment-subject/update-status

Request:
{
  "id": 123,
  "status": 0  // 0=停用, 1=启用
}

业务规则：
- 默认主体不可停用
- 停用前检查是否有产品关联
```

#### 4.1.7 设置默认主体
```
PUT /admin-api/zsjos/payment-subject/set-default

Request:
{
  "id": 123
}

业务规则：
- 自动取消原默认主体
- 只能有一个默认主体
```

### 4.2 产品支付主体配置接口

#### 4.2.1 查询产品配置分页
```
GET /admin-api/zsjos/product-payment-subject/page?pageNo=1&pageSize=10&productName=&subjectCode=

Response:
{
  "code": 0,
  "data": {
    "list": [
      {
        "productId": 456,
        "productName": "课程A",
        "subjectCode": "school",
        "subjectName": "合肥中世健职业技能培训学校有限公司",
        "updateTime": "2026-09-15 12:00:00"
      }
    ],
    "total": 100
  }
}
```

#### 4.2.2 单个产品配置
```
PUT /admin-api/zsjos/product-payment-subject/update

Request:
{
  "productId": 456,
  "subjectCode": "school"
}

Response:
{
  "code": 0,
  "data": true
}

业务逻辑：
1. 更新/插入 zsjos_product_payment_subject
2. 更新 zsjos_product.payment_subject_code
```

#### 4.2.3 批量配置
```
PUT /admin-api/zsjos/product-payment-subject/batch-update

Request:
{
  "productIds": [456, 457, 458],
  "subjectCode": "company"
}

Response:
{
  "code": 0,
  "data": {
    "successCount": 3,
    "failCount": 0
  }
}

业务逻辑：
1. 批量更新/插入 zsjos_product_payment_subject
2. 批量更新 zsjos_product.payment_subject_code
```

#### 4.2.4 导出配置
```
GET /admin-api/zsjos/product-payment-subject/export?productName=&subjectCode=

Response:
Excel 文件，包含字段：
- 产品ID
- 产品名称
- 支付主体编码
- 支付主体名称
- 配置时间
```

## 5. 安全设计

### 5.1 私钥加密方案

#### 前端加密（传输层）
```
使用 RSA 公钥加密：
1. 后端提供加密公钥
2. 前端使用公钥加密 merchantPrivateKey
3. 传输加密后的密文
```

#### 后端加密（存储层）
```
使用 AES 对称加密：
1. 应用配置 AES 密钥（环境变量）
2. 后端解密前端传输的密文
3. 使用 AES 再次加密后存储到数据库
4. 使用时从数据库读取后 AES 解密
```

### 5.2 权限控制

#### 角色权限矩阵

| 操作 | 财务主管 | 财务专员 | 其他角色 |
|------|---------|---------|---------|
| 查看支付主体列表 | ✅ | ❌ | ❌ |
| 创建支付主体 | ✅ | ❌ | ❌ |
| 修改支付主体 | ✅ | ❌ | ❌ |
| 删除支付主体 | ✅ | ❌ | ❌ |
| 查看产品配置 | ✅ | ❌ | ❌ |
| 配置产品主体 | ✅ | ❌ | ❌ |
| 批量配置 | ✅ | ❌ | ❌ |

#### 权限实现
```java
@PreAuthorize("@ss.hasPermission('zsjos:payment-subject:create')")
public Long createPaymentSubject(PaymentSubjectSaveReqVO createReqVO) {
    // ...
}
```

### 5.3 审计日志

记录以下操作：
- 支付主体的增删改查
- 产品配置的单个/批量修改
- 默认主体的变更
- 主体状态的启用/停用

## 6. 性能优化

### 6.1 查询优化

#### 冗余字段设计
```sql
-- 在 zsjos_product 表冗余 payment_subject_code
-- 避免每次查询都 JOIN 关联表
SELECT payment_subject_code FROM zsjos_product WHERE id = 456;
```

#### 索引设计
```sql
-- 产品按主体查询
CREATE INDEX idx_tenant_subject ON zsjos_product_payment_subject(tenant_id, subject_code);

-- 主体按状态查询
CREATE INDEX idx_tenant_status ON zsjos_payment_subject(tenant_id, status);
```

### 6.2 缓存策略

#### 支付主体配置缓存
```java
@Cacheable(value = "payment:subject", key = "#subjectCode")
public PaymentSubjectDO getByCode(String subjectCode) {
    // 查询数据库
}

@CacheEvict(value = "payment:subject", key = "#subjectCode")
public void updateSubject(PaymentSubjectSaveReqVO updateReqVO) {
    // 更新数据库
}
```

#### 缓存失效策略
- 支付主体修改时清除缓存
- 缓存过期时间：1小时
- 使用 Redis 集中缓存

## 7. 异常处理

### 7.1 业务异常

| 异常码 | 异常信息 | 触发场景 |
|-------|---------|---------|
| PAYMENT_SUBJECT_NOT_EXISTS | 支付主体不存在 | 查询/修改不存在的主体 |
| PAYMENT_SUBJECT_CODE_DUPLICATE | 支付主体编码重复 | 创建时编码已存在 |
| DEFAULT_SUBJECT_CANNOT_DELETE | 默认主体不可删除 | 删除默认主体 |
| SUBJECT_HAS_PRODUCTS | 主体已关联产品，不可删除 | 删除有关联的主体 |
| DEFAULT_SUBJECT_CANNOT_DISABLE | 默认主体不可停用 | 停用默认主体 |
| PRODUCT_NOT_EXISTS | 产品不存在 | 配置不存在的产品 |
| SUBJECT_DISABLED | 支付主体已停用 | 使用已停用的主体 |

### 7.2 异常恢复

#### 批量操作异常
```java
// 批量配置失败部分回滚，记录失败产品ID
@Transactional(rollbackFor = Exception.class)
public BatchUpdateResult batchUpdate(List<Long> productIds, String subjectCode) {
    List<Long> successIds = new ArrayList<>();
    List<Long> failIds = new ArrayList<>();
    
    for (Long productId : productIds) {
        try {
            updateProductSubject(productId, subjectCode);
            successIds.add(productId);
        } catch (Exception e) {
            log.error("配置产品支付主体失败, productId={}", productId, e);
            failIds.add(productId);
        }
    }
    
    return new BatchUpdateResult(successIds.size(), failIds);
}
```

## 8. 测试用例

### 8.1 单元测试

#### 支付主体管理
- [ ] 创建支付主体 - 成功
- [ ] 创建支付主体 - 编码重复
- [ ] 修改支付主体 - 成功
- [ ] 修改支付主体 - 不存在
- [ ] 删除支付主体 - 成功
- [ ] 删除支付主体 - 默认主体
- [ ] 删除支付主体 - 有产品关联
- [ ] 启用/停用主体 - 成功
- [ ] 停用默认主体 - 失败
- [ ] 设置默认主体 - 成功

#### 产品配置
- [ ] 单个产品配置 - 成功
- [ ] 单个产品配置 - 产品不存在
- [ ] 单个产品配置 - 主体不存在
- [ ] 批量配置 - 全部成功
- [ ] 批量配置 - 部分失败

### 8.2 集成测试

- [ ] 订单创建时保存主体快照
- [ ] 订单退款时使用快照
- [ ] 多租户数据隔离
- [ ] 权限控制（非财务主管无权限）

### 8.3 性能测试

- [ ] 批量配置1000个产品的响应时间 < 5s
- [ ] 查询产品配置分页（10000条）< 1s
- [ ] 支付主体缓存命中率 > 95%

## 9. 部署清单

### 9.1 数据库迁移
- [x] V239__payment_subject_config.sql
- [x] V240__init_payment_subjects.sql
- [x] V241__product_payment_subject_association.sql
- [x] V242__payment_subject_menu.sql
- [x] V243__payment_subject_role_permissions.sql
- [x] V244__cleanup_duplicate_payment_menus.sql

### 9.2 后端代码
- [ ] PaymentSubjectController
- [ ] PaymentSubjectService
- [ ] ProductPaymentSubjectController
- [ ] ProductPaymentSubjectService
- [ ] 私钥加密工具类
- [ ] 支付订单快照逻辑

### 9.3 前端代码
- [ ] 支付主体管理页面
- [ ] 产品支付主体配置页面
- [ ] 菜单配置
- [ ] 路由配置

### 9.4 配置项
- [ ] AES 加密密钥（环境变量）
- [ ] RSA 公钥/私钥（前端加密）
- [ ] 通联实际配置（两套商户号、密钥等）

## 10. 监控告警

### 10.1 业务指标
- 支付主体配置变更次数
- 产品配置变更次数
- 批量配置操作次数
- 配置异常次数

### 10.2 系统指标
- 支付主体查询 QPS
- 产品配置查询 QPS
- 私钥解密耗时
- 缓存命中率

### 10.3 告警规则
- 支付主体查询失败率 > 1%
- 私钥解密失败次数 > 10/分钟
- 批量配置失败率 > 5%

---
**文档版本**：v1.0  
**编写日期**：2026-09-15  
**作者**：Claude  
**审核人**：待审核
