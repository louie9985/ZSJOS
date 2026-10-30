# 公海池视图与筛选

公海池桌面支持「主从 / 表格」切换，沿用工作台全局收件箱视图偏好；移动端使用列表与详情抽屉。

表格显示客资编号、姓名、联系方式、公海状态、原负责人 A、协同销售 B、归属部门、轮次、归属开始/入池/指派/到期/最近跟进/下次跟进时间及成交订单状态。分类与渠道接口目前只返回记录值，因此列明确标注“记录值”，不使用现行字典标签冒充历史快照。

表格复用 BusinessTable 的列显隐、列宽、密度、全屏、刷新及服务端分页。行操作按 availableActions 显示指派/换派 B、退出公海、申请转给我；详情复用 LeadDetail 的跟进、成交、修订及其他已授权操作。没有批量或导出接口的操作不提供虚假入口。

两种视图共用关键词、归属我的、我跟进的、服务端状态分组、高级筛选和筛选模板。重置筛选同时清除关键词、关系、状态和高级条件。修改筛选或每页数量回到第一页。

高级筛选保持全部服务端 lead 字段，覆盖客资/人员信息、来源分类、产品 SKU、销售与有效性状态、提交人/负责人/组织、备注和时间等，以账号获得的目录为准。本次增加以下字段：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| publicSea.originalOwnerUserId | select | 当前公海原负责人 A |
| publicSea.collaboratorUserId | select | 当前公海协同销售 B |
| publicSea.frozenDeptId | select | 当前公海归属部门（精确部门） |
| publicSea.cycleNo | number | 当前公海轮次 |
| publicSea.ownershipStartedAt | date | 当前公海归属开始时间 |
| publicSea.enteredAt | date | 进入公海时间 |
| publicSea.assignedAt | date | 协同指派时间 |
| publicSea.dueAt | date | 公海到期时间 |

专属字段取同一客资、同一租户、未删除、有效状态的最新轮次，历史已退出轮次不匹配。选项继续来自服务端可见用户/部门，查询继续叠加原列表权限范围。无有效轮次时标量值为空；日期区间、相对时间、空值条件和时间差沿用高级筛选协议。这些是共享 lead 目录的增量字段，Admin 与 Workbench 均使用既有 select/number/date 渲染协议；无需数据库迁移或权限重新分配。

浏览器夹具：`test/public-sea-table.html`，执行 `python frontend/workbench/test/public-sea-table-browser.py`（工作目录仓库根目录，已有 Vite 服务端口 5174）。使用合成数据，不写真实业务库。后端专项测试为 PublicSeaFilterFieldsTest / AdvancedFilterFieldCatalogTest。
