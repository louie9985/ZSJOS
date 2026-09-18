# 支付主体配置功能部署文档

## 📝 功能概述

本功能支持配置多个通联支付主体（公司收款主体），并允许财务主管为产品批量配置支付收款主体。

### 核心能力
- ✅ 支持多个通联支付主体配置（当前2个：学校、公司）
- ✅ 支付主体信息集中管理（商户号、密钥等）
- ✅ 产品关联支付主体，支持单个/批量配置
- ✅ 权限控制：默认仅财务主管可操作
- ✅ 支付订单保存主体快照，支持退款

## 🗂️ 数据库变更

### 迁移文件清单

| 文件 | 版本 | 说明 | 状态 |
|------|------|------|------|
| V239__payment_subject_config.sql | V239 | 支付主体配置表 + 产品字段扩展 | ✅ 已创建 |
| V240__init_payment_subjects.sql | V240 | 初始化两个支付主体数据 | ✅ 已创建 |
| V241__product_payment_subject_association.sql | V241 | 产品支付主体关联表 | ✅ 已创建 |
| V242__payment_subject_menus.sql | V242 | FMS 菜单、权限与财务主管授权（合并原 V242-V249）| ✅ 已创建 |

> 原 V243-V249 及 V248_FIX / V246_ROLLBACK 已删除：它们反复用固定 ID 硬插菜单（601960/601961 → 6850/6851 → 602180/602181 → 602190/602191 → 602200/602210），但 601960/601961 归 V058 所有、6850/6851 归 V048 所有，守卫式 INSERT 每次都变成空操作，因此菜单始终没建出来。所有内容现已合并进 V242。

### 新增数据表

#### 1. zsjos_payment_subject（支付主体配置表）
```sql
主要字段：
- subject_code: 主体编码（唯一标识，如 school, company）
- subject_name: 主体名称（公司全称）
- cusid: 通联商户号
- appid: 通联应用ID
- merchant_private_key: 商户私钥（加密存储）
- platform_public_key: 通联平台公钥
- is_default: 是否默认主体
- status: 启用状态
```

#### 2. zsjos_product_payment_subject（产品支付主体关联表）
```sql
主要字段：
- product_id: 产品ID
- subject_code: 支付主体编码
唯一约束：租户+产品ID（一个产品只能配置一个主体）
```

### 已有表字段扩展

#### zsjos_product（产品表）
```sql
新增字段：
- payment_subject_code: varchar(64) - 支付主体编码（冗余字段，便于查询）
```

#### zsjos_payment_order（支付订单表）
```sql
新增字段：
- subject_snapshot_json: json - 支付主体配置快照（用于退款）
```

## 🎨 前端菜单结构

```
FMS 财务管理（601894）
└── 设置管理（601951）
    ├── 支付主体管理（602200）
    │   ├── 查询（zsjos:payment-subject:query）
    │   ├── 创建（zsjos:payment-subject:create）
    │   ├── 修改（zsjos:payment-subject:update）
    │   └── 删除（zsjos:payment-subject:delete）
    └── 产品支付主体配置（602210）
        └── 配置修改（zsjos:product-payment-subject:configure）
```

页面节点本身不带权限码，路由由上面的操作权限控制（同 V033 的页面/权限拆分约定）。
产品支付配置的权限码是 `:configure`，与 `ProductPaymentSubjectController` 的 `@PreAuthorize` 一致；原 `:update`/`:batch-update`/`:export` 已作废。

## 🔐 权限配置

### 默认角色权限

**财务主管（finance_manager）**：
- ✅ 支付主体管理（增删改查）
- ✅ 产品支付主体配置（批量配置）

### 扩展其他角色
如需授权其他角色，在 system_role_menu 表中添加相应的 role_id 和 menu_id 关联。

## 🚀 部署步骤

### 1. 数据库迁移

统一走 `zsjos-db` 执行，不要手工按文件灌：它会校验版本连续性和已执行脚本的 SHA-256。

```bash
# 预览待执行
python script/sql/mysql/tools/zsjos_db.py plan prod

# 执行
python script/sql/mysql/tools/zsjos_db.py migrate prod
```

涉及的迁移为 V239 → V240 → V241 → V242。

### 2. 验证数据库变更
```sql
-- 检查表是否创建成功
SHOW TABLES LIKE 'zsjos_payment%';

-- 检查菜单是否创建（应该在 601951 下）
SELECT id, name, permission, parent_id FROM system_menu 
WHERE id IN (602200, 602201, 602202, 602203, 602204, 602210, 602211, 602212) AND deleted = b'0';

-- 检查财务主管是否有权限
SELECT rm.role_id, r.name, m.name, m.permission 
FROM system_role_menu rm
JOIN system_role r ON rm.role_id = r.id
JOIN system_menu m ON rm.menu_id = m.id
WHERE r.code = 'finance_manager' 
  AND m.permission LIKE 'zsjos:%payment-subject:%'
  AND rm.deleted = b'0';

-- 检查初始化的支付主体数据
SELECT subject_code, subject_name, is_default, status 
FROM zsjos_payment_subject WHERE deleted = b'0';
```

### 3. 后端代码部署
```bash
# 后端需要实现的接口（待开发）
# PaymentSubjectController - 支付主体管理
# ProductPaymentSubjectController - 产品支付主体配置

cd backend
mvn clean package -DskipTests
# 重启后端服务
```

### 4. 前端代码部署
```bash
# 前端需要开发的页面（待开发）
# zsjos/payment/subject/index.vue - 支付主体管理页面
# zsjos/payment/productSubject/index.vue - 产品支付主体配置页面

cd frontend
npm run build
# 部署前端资源
```

## ⚠️ 注意事项

### 安全性
1. **私钥加密**：merchant_private_key 字段需要在应用层加密存储
2. **权限控制**：严格限制支付主体配置的访问权限
3. **审计日志**：记录所有配置变更操作

### 数据一致性
1. **默认主体**：系统必须至少有一个默认主体（is_default=1）
2. **主体状态**：停用主体前需检查是否有产品正在使用
3. **订单快照**：创建支付订单时必须保存 subject_snapshot_json

### 迁移兼容性
1. **存量产品**：未配置支付主体的产品，使用默认主体
2. **租户隔离**：支付主体配置支持多租户，注意 tenant_id；V242 的 role_menu 授权按角色所属租户写入，不再固定 0
3. **菜单归属**：V242 在写入前会断言 602200-602212 的所有权，若被其他模块占用会直接 SIGNAL 阻断，不再静默跳过

## 📊 初始数据

系统预置了两个支付主体：

| 主体编码 | 主体名称 | 是否默认 |
|---------|---------|---------|
| school | 合肥中世健职业技能培训学校有限公司 | ✅ 是 |
| company | 安徽省中世健健康管理有限公司 | ❌ 否 |

**注意**：cusid、appid、merchant_private_key、platform_public_key 等字段需要后续手动配置通联提供的实际值。

## 🔧 后续开发任务

### 后端接口
1. **支付主体管理**
   - [ ] CRUD 接口
   - [ ] 启用/停用接口
   - [ ] 设置默认主体接口
   - [ ] 私钥加密/解密逻辑

2. **产品支付主体配置**
   - [ ] 查询产品配置列表接口
   - [ ] 单个产品配置接口
   - [ ] 批量配置接口
   - [ ] 配置导出接口

3. **支付集成**
   - [ ] 创建订单时保存主体快照
   - [ ] 支付请求使用产品配置的主体
   - [ ] 退款使用订单快照的主体配置

### 前端页面
1. **支付主体管理页面**
   - [ ] 列表展示（表格）
   - [ ] 新增/编辑表单（私钥输入需脱敏）
   - [ ] 启用/停用切换
   - [ ] 设置默认主体

2. **产品支付主体配置页面**
   - [ ] 产品列表（支持筛选）
   - [ ] 单个配置（下拉选择主体）
   - [ ] 批量配置（勾选产品 + 选择主体）
   - [ ] 配置状态展示
   - [ ] 导出功能

### 测试用例
- [ ] 支付主体 CRUD 测试
- [ ] 产品配置单个/批量操作测试
- [ ] 权限控制测试（非财务主管无权限）
- [ ] 多租户隔离测试
- [ ] 支付订单快照测试

## 📞 联系方式

如有问题，请联系：
- 技术负责人：[待填写]
- 产品负责人：[待填写]

---
**部署日期**：2026-09-15  
**文档版本**：v1.0  
**更新人**：Claude


## 支付主体路由修复发布说明（2026-09-18）

本节规定本次交易路由修复的发布要求；历史部署步骤不代表本次需要重跑初始化 SQL、菜单或角色授权。当前接口及路由规则以[支付主体 API](../api/payment-subject.md)为准。本次不改表、不增加依赖、不自动修复存量订单。

### 发布前检查

1. 在目标租户管理端确认唯一启用默认主体；本业务将学校设为默认，但程序只读取默认配置，不按主体名称或 `school` 编码推断。
2. 检查参与收款的产品关联和主体凭据。生成链接会验证商户号、应用 ID、RSA 密钥格式；必须另行确认通联授权、回调地址和实际密钥匹配。启用状态不等于支付账户可用。
3. 在实际目标数据库通过 UTF-8 客户端执行 `script/sql/mysql/tools/payment_subject_audit.sql`。前置条件是当前支付订单、支付主体、产品主体关联和网关事件表；默认仅审计租户 1，其他租户先在同一会话设置 `@payment_audit_tenant`。脚本只使用会话设置与 SELECT，可重复执行，不修改业务数据或权限，无需回滚。
4. 审计输出为计数，不包含私钥、令牌或完整快照。SQL 只检查字段完整性，不能验证密钥格式；记录中的微信下单商户比较也不等于完整对账，支付宝等未记录请求商户的交易需要通联平台证据。
5. 构建并加载更新后的后端，再做受控支付验收。本次未改变前端接口形状或布局，不要求为本修复重新发布前端。仅刷新页面不能加载后端改动。

### 存量订单处理

- 未向通联下单且仍为 `created`：通过现有取消链接操作作废，再按新规则重新生成；不要直接修改快照或删除订单。
- 已有通联请求号、状态等待确认：先根据实际收款商户查单，确认未付款并关闭原交易后才能重建。不能用当前产品主体猜测原商户。
- 已付款：保留实际收款事实和历史快照，原路退款沿用原商户；修改产品配置不会转移已收资金。
- 快照缺失、损坏或与实际请求商户不符：上线新代码后明确报错，不能依靠旧版全局回退继续处理。先核对网关记录和通联平台交易，备份并确认具体记录、证据来源、修复值及恢复方式，再另行执行受控定向修复。禁止批量使用当前产品配置覆盖历史快照。
- 密钥轮换或平台撤销旧密钥需要独立评审凭据恢复方案；本次不会自动改写历史签名凭据。

### 验证与回退边界

后端定向测试：`PaymentSubjectResolverTest`、`PaymentSubjectGatewayFactoryTest`、`PaymentSubjectCallbackTest`、`PaymentSubjectGatewayOperationsTest`、`PaymentSubjectTenantTest`，以及原有显示、取消、主体查询和控制器测试。网关测试仅向本机临时 HTTP 服务发送合成数据，不请求真实通联。

受控通道验收需覆盖：单产品非默认主体、同产品多 SKU、多产品同主体、多产品不同主体转默认、配置失效拒绝、微信和支付宝到账回调、查单、关单、原主体退款。检查实际请求商户和到账记录；不向日志写入密钥、令牌或完整支付载荷。

没有 schema 变更，但回退旧应用会重新引入全局商户回退及错误回调校验，不能将应用回退视为安全的数据修复。发布前处理或明确隔离异常历史订单；本地空支付库的审计结果不能替代目标环境存量审计。
