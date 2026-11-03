# 邮寄地址调整
环境 local；分支 main；工作树 D:\ZSJ-OS；基线 c2909e4029182f4f205fdc350b00d8b6656751f1。
负责人 Codex；目标：教材地址字段、取消订单礼品地址输入、采购读取最新学员档案。非目标：数据库及历史数据批量修改、部署。
范围：StudentInfoFields、SalesOrderServiceImpl、GiftPurchaseService、StudentGiftAddressService 及对应测试；Workbench SalesOrderEntryModal；Admin mySalesOrder；docs/api 三份相关文档。本记录独占。依赖：既有档案及表单 API；集成 None。验证：后端定向测试/编译、前端类型检查、浏览器可用性检查。

交付：实现已完成；用户确认按最新档案读取，采用 personId 最新已提交表单及其最新修订，空值不回退。新增字段需配置发布，新发链接生效；历史快照不改。后端 compile 通过，StudentGiftAddressServiceTest / StudentInfoFieldsTest / StudentInfoImagesAndRulesTest 通过，Workbench typecheck 通过。Admin ts:check 被既有 mes/pro/task/edit/index.vue:86 handleRefresh 未使用阻塞。浏览器已确认 localhost 可用，但当前为 Admin，未完成真实录单/收集表/采购端到端验收；后端未重启、配置未发布，未操作真实数据或部署。所有权释放。

## 保存失败修复续办
环境仍为 local；负责人 Codex，恢复本任务所有权。扩展范围：StudentInfoVO、StudentArchiveVO、StudentInfoDraftPolicy、StudentInfoRequestValidationTest；修正 33 字段在 HTTP Bean Validation 入口被 32 上限拒绝的问题。验证使用真实 Jakarta Validator 覆盖配置、提交、草稿和修订请求的 33/34 边界，不写真实配置。

续办交付：已同步配置/提交/暂存/档案修订 VO 及草稿策略上限为 33。Maven 定向测试 18 项全部通过（请求边界 2、字段 9、材料规则 7），git diff --check 通过。未重启后端、未代保存或发布真实配置，运行环境仍需加载新类后重试；真实 HTTP 保存未验证。所有权释放。

## 采购地址快照修正续办
local / Codex 恢复所有权，用户明确修正为采购地址快照，覆盖此前实时查询方案。范围：SalesOrderServiceImpl、GiftPurchaseService、对应测试及三份 API 文档；新增 SalesOrderGiftPurchaseSnapshotTest。目标：历史地址读取采购行、新生成时冻结最新档案地址；非目标：自动回填历史空值、改变审批生成时机、数据库修改及服务重启。验证：采购投影、生成快照/幂等/无礼品/空地址及现有订单审批测试。

快照修正交付：移除采购查询实时覆盖地址，改在订单审批最终通过的自动生成节点冻结最新档案地址；现有采购直接显示存量快照。生成缺址保留空值、不自动回填。已验证 112 项测试：SalesOrderServiceImplTest 89、GiftPurchaseServiceTest 17、SalesOrderGiftPurchaseSnapshotTest 3、StudentGiftAddressServiceTest 3，全部通过（采购测试修正 Mock 后定向重跑）。git diff --check 通过。未重启、未部署、未修改数据库，运行时/真实采购数据未验收。文档已同步取值和生成时机。所有权释放。
