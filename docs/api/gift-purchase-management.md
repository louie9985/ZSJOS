# 中世健礼品采购管理

礼品采购位于 Admin `zsjos/gift-purchase/index.vue`。生效订单原有自动生成路径保留；新增按钮创建独立手工采购，不关联或修改订单。一张订单仍只对应一条采购记录；手工记录的 `orderId/orderNo` 为空，不用虚构订单号。Workbench 不新增采购页。

列表进入及重置后默认查询“未采购”，可查询全部或已采购。新建和原先没有状态字段的记录初始为未采购。`purchased` 是二态采购流程标记，通过“标记已采购”“标记未采购”命令改变，不是可配置业务分类或字典选择。

“来源”列及详情合并展示以下信息：

| 展示项 | 描述 | 历史证据 |
| --- | --- | --- |
| 采购来源 | 订单提交、手工创建或来源待确认；订单提交可附订单号 | 响应 `sourceType` 为 `ORDER/MANUAL/UNKNOWN` |
| 来源人 | 这笔采购的原始来源人，创建后不因编辑或状态操作改变 | 订单采购使用订单提交人的历史快照；手工采购使用原始 `CREATE` 操作人的 ID、姓名快照 |
| 标记已采购人 | 把当前记录标记为已采购的员工，不是最近编辑人 | 当前为已采购时读取最近一次 `PURCHASE` 操作的 ID、姓名及时间；普通编辑不覆盖，改回未采购后清空当前展示，重新采购读取新标记人 |

订单提交人使用现有订单审批快照及导入/修复证据读取契约，不回查当前员工姓名补造历史。手工创建的来源人由认证身份和创建操作记录自动确定，不再提供“录单销售”人员选择，也不允许客户端修改来源人。旧版曾保存的手选销售元数据保留原值，但不能充当手工创建人。

缺少创建或采购动作历史时明确展示“历史未记录”，不借用最近编辑人或当前员工资料。旧接口缺少 `orderId` 时，前端可凭已有 `orderNo` 展示订单提交来源；缺少来源证据则显示“来源待确认”，不能直接认定手工创建。新接口显式返回来源类型，只有存在手工创建证据才返回 `MANUAL`。

新响应字段：`sourceType`、`sourceUserId/sourceUserName`（来源人）、`purchasedByUserId/purchasedByName/purchasedAt`（当前采购标记）。原 `submitterUserId/submitterUserName` 和 `operatorName/operatedAt` 保留兼容，但本页面不再将其分别展示为录单销售及最近操作人；订单旧响应的提交人姓名仅在已确定订单来源时兼容读取。

生成时间由服务端创建时记录，前端使用既有日期工具格式化为 `YYYY-MM-DD HH:mm:ss`，兼容接口时间戳和日期字符串。编辑、采购状态切换不重写生成时间；本次不批量更改历史时间。

## 接口和权限

路径均以 `/admin-api/zsjos/gift-purchase` 为前缀，使用 ADMIN 认证、既有租户上下文（当前唯一租户为 1）及 `CommonResult`。

| 方法及路径 | 权限 | 行为 |
| --- | --- | --- |
| GET `/page` | `zsjos:gift-purchase:query` | 分页，学员姓名、订单号、礼品关键词及可选 `purchased` 布尔筛选；不传状态查全部 |
| GET `/get?id=` | `zsjos:gift-purchase:detail` 或 `update` | 详情；编辑权限也可加载编辑对象 |
| GET `/operations?id=` | `zsjos:gift-purchase:detail` | 操作历史，删除后仍保留；按操作记录 ID 倒序 |
| GET `/gift-options` | `zsjos:gift-purchase:create` 或 `update` | 复用礼品配置服务的选择来源，不授予配置修改权限 |
| POST `/create` | `zsjos:gift-purchase:create` | 独立手工新增，初始未采购，服务端产生时间与操作人 |
| PUT `/update` | `zsjos:gift-purchase:update` | 更新采购信息，订单关联、来源人和采购标记人不可通过编辑改写，不回写订单 |
| PUT `/purchase?id=` | `zsjos:gift-purchase:update` | 标记已采购，同状态重复命令不重复记操作 |
| PUT `/unpurchase?id=` | `zsjos:gift-purchase:update` | 标记未采购，同状态重复命令不重复记操作 |
| DELETE `/delete?id=` | `zsjos:gift-purchase:delete` | 单条逻辑删除，保留操作历史，不删除订单 |

保存请求包括 `id`（更新必填）、`studentName`（必填，100 字内）、`studentMobile`（32 字内）、`studentWechatId`（64 字内）、`giftItemCodes`（至少一项，最多 100 项）、`shippingAddress`（必填，1000 字内）。生成时间、状态、订单 ID、来源人、采购标记人均不接受客户端指定。允许清空可选联系方式。

新选礼品必须为当前启用的叶子节点，服务端冻结其名称和路径。未改变的旧礼品沿用快照，包括已停用/删除的历史选择；损坏快照明确提示并阻止编辑保存，不解释为空礼品。状态、删除命令仍可独立执行。

`zsjos_gift_purchase_operation` 以当前认证身份记录新增、订单自动生成、编辑、已采购、未采购和删除动作及发生时间、操作人 ID 和姓名快照；系统执行使用明确的系统来源。业务修改与操作记录同事务提交，已有采购行加锁串行修改。该表不设业务级联删除，不补造历史操作。列表、详情、操作历史和修改沿用 MyBatis 租户及逻辑删除机制；采购保留原有 Admin 管理范围，不按当前销售姓名推断访问权。

稳定错误：`1900157000` 记录不存在或已删除，`1900157001` 缺少礼品，`1900157002` 关联人员不可用；历史礼品损坏和新选择无效沿用订单礼品错误。权限拒绝由既有安全机制返回；前端保留错误反馈、重试、空态和加载态。

## 数据库与交付

在 V339 已写入两个版本账本后执行 `V340__gift_purchase_management.sql`，再发布新后端及 Admin。V340 仅放宽采购的订单关联字段、增加采购标记及手工录单销售快照，创建空操作表，补充 create/update/delete 按钮元数据。保留原 query/detail 权限；管理员通过角色管理自行配置新增按钮，迁移不写 `system_role_menu`。

迁移可重复执行，不覆盖已有业务数据、历史时间、快照或角色授权。所有前置检查、后置检查和账本登记共享失败路径；DDL 可能隐式提交，失败后检查实际结构及两个账本再重试。应用回滚时保留新增结构及手工记录，不能用删除表/数据来回滚；旧代码不能完整表达独立手工记录，回滚前应评估读取兼容性。

定向验证入口：

```text
mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=GiftPurchaseServiceTest,GiftPurchaseControllerPermissionTest,SalesOrderGiftSnapshotTest -Dsurefire.failIfNoSpecifiedTests=false test
python -B script/sql/mysql/tools/test_gift_purchase_management.py
```

数据库验证保留独立夹具库，覆盖首次、重复、部分执行、错误结构、前置失败、账本写入失败及恢复，检查中文 HEX、默认状态和操作记录保留。`--sync-local` 在检查、保存元数据备份后仅同步本地开发库并比较原业务签名和角色分配计数，不用于共享或生产数据库。生产发布、实际账号权限和真实业务操作验收需另行完成。


2026-10-21 来源口径修正仅调整 API 投影与展示，复用 V340 的创建/采购操作证据，不新增或重写迁移，不修改历史业务数据或真实角色授权。当前采购标记在撤销后仍可从操作历史查看。


采购候选及新选择校验统一展平服务端礼品树，保留全部父子关系；只允许启用的叶子节点，历史未变选择仍保留快照。
