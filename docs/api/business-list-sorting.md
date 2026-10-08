# 中世健业务列表排序

2026-10-08：员工工作台我的学员、订单管理、返现管理、提现管理支持服务端全局单字段排序。普通查询与高级筛选使用同一排序契约；Vue 管理端的既有调用不传新参数，保持原默认顺序。

## 请求及入口

请求可选 `sortField` 和 `sortOrder`，必须同时提供；方向为 `ascend` 或 `descend`。取消排序时同时省略两项。非法字段、方向或不完整参数返回参数错误。

| 场景 | 普通 GET | 高级筛选 POST |
| --- | --- | --- |
| 我的学员 | `/zsjos/student/my-page` | `/zsjos/student/my/search-page` |
| 订单管理分页 | `/zsjos/sales-order/management-page` | `/zsjos/sales-order/management-search-page` |
| 订单管理卡片 | `/zsjos/sales-order/management-cursor` | `/zsjos/sales-order/management-search-cursor` |
| 返现管理 | `/zsjos/cashback/page` | `/zsjos/cashback/search-page` |
| 提现管理 | `/zsjos/withdrawal/page` | `/zsjos/withdrawal/search-page` |

路径使用既有 ADMIN 前缀、认证及 tenant-id。列表/游标响应形状不变。其他个人、团队、合作方列表和导出不新增排序承诺。

## 字段与比较规则

- 学员：`name`、`leadNo`、`mobile`、`wechatId`、`className`、`courses`、`serviceStatus`、`orderNos`、`activatedAt`。
- 订单：共享订单表格所有 `dataIndex` 业务字段；地区渲染列使用 `orderRegion`、`leadRegion`。包括审批轮次、当前审批节点/结果、主管确认、人员快照、学员资料、产品、金额、备注、时间及审批意见；操作列不排序。实际白名单由 `OrderListSort` 明确声明，不能通过任意响应属性访问或 SQL 拼接扩展。
- 返现：`cashbackNo`、`beneficiaryName`、`partnerName`、`type`、`customer`、`source`、`productNameSnapshot`、`baseAmount`、`rateSnapshot`、`amount`、`status`、`generatedAt`、`availableAt`。
- 提现：`withdrawalNo`、`applicantName`、`partnerName`、`cashbackCount`、`applicationAmount`、`status`、`accountNameSnapshot`、`cardNumber`、`bankNameSnapshot`、`submittedAt`、`reviewedAt`。

金额、比例、数量按数值，时间按时间。文本使用 JDK 中文 Collator（拼音顺序），状态及审批节点按显示名称比较，不按编码或流程先后排列。相同主排序值以内部记录 ID 降序稳定排序，内部 ID 不作为用户编号显示。空值、不可见来源和不适用值在两个方向均排最后；有效返现的基数/比例按“不适用”处理。

学员多课程/状态/订单按完整显示顺序拼接比较；班级沿用页面当前显示的第一个非空班级。激活时间沿用可见服务关系的最大激活时间。订单字段沿用已有审批轮次快照投影，不以当前主数据替代历史标签或历史金额。动态姓名取既有公开 API，返现类型/状态标签复用高级筛选目录，提现状态复用服务端状态标签。未提供的审批值仍是空值，不因排序创建新的审批信息。

来源身份只使用当前用户有权看到的投影，银行卡使用该入口原本返回的完整/脱敏值；不能为了排序扩大权限。菜单权限、租户、逻辑删除、人员数据范围及高级筛选先于排序生效。

## 分页、性能与一致性

返现金额/基数/比例/时间、提现金额/时间、订单提交/生效时间采用数据库分页前排序，SQL 片段仅来自固定白名单。订单金额、付款时间可能来自轮次快照，因此与其他展示字段一起按授权投影排序。

复杂排序以每批 200 条读取现有授权投影，再全局比较并返回目标页；不会只排列当前页。复杂排序时间和内存随筛选结果规模增长，第一版接受该成本，不新增排序副本或依赖。数据库读取处于只读、可重复读事务，防止同一请求分批时因数据变化跳项；跨公开服务的数据不承诺分布式快照。依赖读取失败时整次查询失败，不返回假装完成的局部排序。实际生产数据量下的性能仍需部署环境验证。

显式排序的订单卡片使用版本化游标，包含用户/租户/状态/关键词/高级筛选上下文、排序字段方向、末行排序值及内部 ID。游标条件不匹配或损坏时返回“排序游标已失效，请刷新列表”。默认排序沿用旧游标。数据库和外部状态在两次请求之间变化时，列表保持实时读取语义；用户可刷新重新开始，不承诺跨请求冻结结果集。

## 工作台交互与验证

列头循环升序、降序、取消排序；学员与订单的表格/卡片共用排序状态和排序菜单。排序变化回到第一页、清理已加载卡片和跨页勾选，旧请求不能覆盖新选择；翻页/筛选/刷新保留排序，切换展示方式也保留。刷新浏览器或重新进入页面恢复默认排序。

验证入口：后端 `service/sorting/*Test`，前端 `src/services/businessListSort.test.ts`，真实页面浏览器夹具 `test/business-list-sorting.html` 与 `test/business-list-sorting-browser.py`。浏览器通过 `SORT_TEST_BASE` 指向任务自有 Vite 实例，数据仅为隔离测试数据，不写业务库。另验证既有 Admin 请求模块不传新增参数时仍使用原路径和响应。
