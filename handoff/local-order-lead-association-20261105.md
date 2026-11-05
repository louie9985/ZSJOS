# 客户录单关联客资

环境：local（环境变量和环境文件未设置）。工作流：order-lead-association-20261105；owner：Codex。
分支：main；工作区：D:/ZSJ-OS；基线：216b75eaa84b7b757889be24e6a5483ed5a93593。集成分支/顺序：None。
目标：复购录单按客户匹配客资，唯一自动关联、多条选择、无候选沿用原流程；展示完整客户档案及当前状态，持久化独立关联与快照。
非目标：首购变更、客资状态/归属/返现变更、历史回填、数据库结构变更、部署。
用户确认：身份核验后允许录单弹窗完整读取同一客户全部候选档案，不授予普通客资页面访问或修改权。
所有权：order 下 SalesOrderService/Impl、SalesOrderSnapshot、RepurchaseCustomerService；order controller 及关联 VO；LeadMapper；ZsjosErrorCodeConstants；相关 order 测试；Workbench SalesOrderEntryModal、SalesOrderDetailCards、新增关联选择/客户档案组件及测试、services/api.ts；docs/api/zsjos-sales-order.md、docs/architecture/data-and-permission-flow.md、本记录。
保留现有未提交订单档案布局及提交时间兼容改动。相关旧工作流已交付释放所有权。
依赖：既有客户身份核验、录单权限和审批轮次 order_snapshot。关联只写快照，独立于首购 leadId。
验证：后端匹配/权限/快照与首购回归测试、编译；前端类型与组件测试；浏览器桌面/移动检查；Admin 增量响应兼容核查。

范围补充：Admin 已有同一客户录单接口消费者，领取 frontend/admin/src/api/zsjos/workbenchMenus.ts、views/zsjos/components/ExternalRepurchaseDialog.vue、新增 OrderLeadSelectionDialog.vue，执行双端兼容与类型检查。浏览器隔离验证文件归本任务 tmp/order-lead-association 目录。

实施结果：关联数据保存在审批轮次 order_snapshot.associatedLeadId + leadProfile，无主表/schema 变化。创建事务内锁定所选客资并核对 Person；补正重提保留原关联快照。首购交易 leadId 规则不变。候选保留关闭历史客资，唯一自动带入、多条必选、无候选继续；没有加入未确认的跳过关联选项。
双端：工作台复用提取的 OrderLeadProfile（保持现有档案布局与提交时间兼容改动），Admin 原有录单同步选择；弹窗完整档案及当前状态，限制正文高度并保留底部确认。用户确认的同客户读取例外已同步 API 与权限文档。
验证：后端四组测试共 118 项通过（4 关联、7 快照、90 订单服务、17 客户核验），覆盖首购/复购回归、零/一/多候选、无效选择、独立快照、核验失败与 tenant/Person 查询条件；加入行锁后回归仍通过。Workbench typecheck 与 18 项既有订单组件测试通过，git diff --check 通过。
Admin pnpm ts:check 唯一报错为既有 src/views/mes/pro/task/edit/index.vue:86 handleRefresh 未使用，本任务文件未报错。
浏览器：使用 ego-browser 空间 33 与本任务隔离 fixture，实际 DOM 显示完整客户档案和当前状态，未选择确认按钮 disabled=true。窗口最小化导致截图不可用，恢复窗口命令返回 No web contents in the target，后续点击也受视口限制；未完成桌面/手机视觉及提交交互验收。未绕过浏览器限制或升级浏览器（工具提示可更新）。Admin 浏览器未验证。运行中后端未重启，真实账号新接口/E2E 未验证；现有自动化不是线上验收证据。
未执行 SQL、部署、重启、提交或推送；未回填历史订单，保留工作区其他任务改动。

最终补充：候选核验保留多客户匹配、已有待处理订单、身份不一致的独立错误；补充对应断言后 SalesOrderAssociationTest 4 项再次通过（含模块编译）。本地代码交付，浏览器及真实账号验收缺口如上；所有权释放。

续办登记（死锁修复）：环境仍为 local，分支/工作区/基线引用原登记。用户授权修复关联新增 Lead 行锁造成的锁顺序反转。重新领取 SalesOrderServiceImpl.java、SalesOrderServiceImplTest.java、docs/api/zsjos-sales-order.md、本记录。范围为移除资料关联行锁、保留候选查询与快照、回归正常录单入口及首购。验证为订单服务/关联/快照/客户核验测试及编译、差异检查。受控 MySQL 双事务并发验证视现有测试设施可用性执行，缺口如实记录。

死锁修复交付：移除 resolveAssociation 新增 Lead FOR UPDATE，直接从按当前租户及 Person 查询的候选记录生成关联快照。关系和档案按提交事务内查询时点采集。API 文档同步时点语义。复用正常 createMatchedRepurchase 参数化测试，提供真实候选并断言生成关联快照且从不调用 Lead selectByIdForUpdate，覆盖销售/教务及有无历史生效订单四种组合，同时保留归属、幂等及 Lead 不改写断言。
验证：Maven reactor 指定 SalesOrderAssociationTest、SalesOrderSnapshotTest、SalesOrderServiceImplTest、RepurchaseCustomerServiceTest，118 项通过，含模块编译。git diff --check 通过。现有并发测试仅见 H2 模式，没有可直接复用的受控 MySQL 双事务测试环境，本轮未执行 MySQL 并发测试或真实接口验收。没有部署/重启。所有权释放。

续办登记（多候选开发数据）：用户明确授权直接修改前轮扫描的本地开发数据库。环境 local、分支/main 与原登记相同。目标为配置指向的 192.168.2.17:3306/ruoyi-vue-pro、租户1、源客资 KZ202610291728080050，对同一 Person 定向新增 KZTEST20261105MULTI01（closed）。领取 script/sql/mysql/fixtures/local-order-lead-association.sql、本记录。仅新增测试 Lead，不改原客资、客户或订单。源行身份和档案复制，无历史流程/任务/跟进关联复制。恢复按新增编号精确清理，未自动执行清理。验证事务内断言、重放不重复、候选2条、UTF-8文本HEX、待处理复购计数及备用单客资仍1条。

开发多候选数据交付：执行 script/sql/mysql/fixtures/local-order-lead-association.sql，在 192.168.2.17:3306/ruoyi-vue-pro、tenant1，为源 KZ202610291728080050 同一 Person 新增 KZTEST20261105MULTI01，状态 closed，备注明确本地测试。执行范围一行新增，事务内断言通过后提交；同一事务重放插入0行。候选验证为原 won 与新增 closed 共2条，待处理复购0，源 Lead 全行前后相等；备用 KZ202610301142520013 仍为1条候选。UTF-8连接及 remark HEX 字节校验通过。未执行实际订单提交。恢复需先查新增编号的业务引用后再定向清理，特别保留已形成的订单历史快照。本地 fixture 不进入 bootstrap/migration 链。所有权释放。

续办登记（合并提交确认）：local 环境，分支/工作区沿用原登记。用户要求客资选择与档案更新确认使用同一弹窗。领取两端 OrderProfileConfirm、OrderLeadSelection 组件及 SalesOrderEntryModal/ExternalRepurchaseDialog、相关前端测试、直接 API 文档和本记录。保留现有重试 pendingSubmission 等后续改动。验证双端类型检查、订单确认组件测试及浏览器隔离交互。

合并提交确认交付：工作台 OrderLeadSelectionModal 与 Admin OrderLeadSelectionDialog 转为内嵌选择内容，在既有 OrderProfileConfirm 弹窗展示，移除第二弹窗状态/Promise；一次确认直接携带关联客资与 profileUpdate 提交。保留 Admin 后续 pendingSubmission 请求重放逻辑及工作台 converted 状态兼容显示。多候选未选禁用提交，唯一候选自动带入。
验证：Workbench typecheck 通过，OrderProfileConfirmModal/SalesOrderEntryModal/SalesOrderDetailCards 20 项测试通过。Admin pnpm ts:check 只有已有 MES handleRefresh TS6133，当前修改文件无报错。ego-browser 空间35隔离fixture确认 dialogs=1、未选 disabled=true、选择后 false，选择第二条+微信更新一次提交返回 selected:2;fields:wechatId，桌面截图已查看。手机390 emulation检查无页面横向溢出，但跨调用截图恢复桌面尺寸，手机视觉验收未完整完成。Admin 实际浏览器及真实账号提交未验收。fixture 中请求为演示回调，不是数据库录单。空间已finish。API文档已同步。未部署。所有权释放。

续办登记（卡牌选择）：用户批准交互预览。local 环境与原分支工作区一致，领取两端 OrderLeadSelection 组件、Workbench 新增独立组件样式及相关测试、docs/api/zsjos-sales-order.md、本记录。目标为整卡选择、选中放大上浮蓝色光边、响应式与键盘操作。保留完整真实档案和合并确认流程。验证类型、组件测试、桌面与手机浏览器隔离预览。

卡牌交付：两端候选改为并列整卡点击单选，选中上浮6px、放大1.025并增加主题蓝色光边与“已选中”标记，640px以下纵向排列。原生radio提供键盘方向键与禁用状态；工作台复制按钮点击独立于整卡选择。完整档案复用既有展示，合并确认流程沿用。
验证：Workbench typecheck通过，已有20项组件测试通过；新增卡牌测试2项，修正仅依赖SSR属性顺序的断言后2项通过。Admin ts:check仅原MES handleRefresh未使用错误。ego-browser空间36隔离fixture验证整卡点击选2、键盘ArrowLeft选1、390px单列且无横向溢出，桌面和手机截图均已查看，选中蓝色光边和放大可见。浏览器空间finish。Admin真实浏览器和真实账号录单尚未验收。未部署。所有权释放。

续办登记（2026-11-06新测试样本）：local环境，现有main工作区。沿用用户明确的开发库多候选造数授权，源客资 KZ202610291629010044，为同一客户新增 closed 测试客资 KZTEST20261106MULTI01。领取 script/sql/mysql/fixtures/local-order-lead-association-20261106.sql、本记录。目标仍为192.168.2.17:3306/ruoyi-vue-pro租户1，一行新增，可重放。验证候选2条、活动复购0、身份唯一、源行一致及中文HEX。

2026-11-06造数交付：新脚本执行成功新增1行，重放0行，源行全部字段相等；同客户候选为 KZ202610291629010044(won)、KZTEST20261106MULTI01(closed)，待处理复购0，备注UTF-8 HEX验证通过。未执行录单操作。恢复按测试编号检查业务引用后定向清理。所有权释放。

续办登记（校验阶段选择）：local环境，原main工作区，用户要求历史客户复购在身份校验后选择客资，详情入口绑定原客资。领取 ExternalRepurchasePage.tsx、SalesOrderEntryModal.tsx、Admin ExternalRepurchaseDialog.vue、SalesOrderServiceImpl.java及对应订单服务测试、docs/api/zsjos-sales-order.md、本记录。保留现有其他任务的批量档案投影与重试改动。验证双端类型、组件/服务回归与浏览器可用性。

校验阶段选择交付：历史客户复购两端要求姓名/手机号/微信号完整，校验成功即查询并展示客资卡牌；唯一自动选，多条先选后录单，零候选继续。身份变化清空校验、候选与选择。Workbench通过associationChecked/selectedLeadId传递校验阶段选择，最终确认只核对档案。Admin提交幂等指纹包含关联选择，保留已有pendingSubmission重放。客资详情复购请求直接携带入口leadId，后端拒绝与contextLeadId不同的显式选择。学员复购保留其现有选择流程。API契约同步。
验证：后端关联/快照/订单/客户核验共120项通过，含模块编译。Workbench typecheck与22项组件测试通过；Admin类型检查仅已有MES handleRefresh TS6133。隔离浏览器实际ExternalRepurchasePage配合mock身份/候选API，验证校验后2卡、0弹窗、未选继续禁用、选第二卡继续启用、改手机号后0卡且继续按钮消失；空间37已finish。当前前端真实账号完整提交、Admin浏览器及后端部署未验收。git diff --check通过。未执行数据库写入或部署。所有权释放。

续办登记（复购页面压缩）：local环境，main既有工作区。用户批准横排身份表单、校验后摘要、卡牌默认关键字段并展开完整档案、合并提示及底部继续栏。领取 Workbench ExternalRepurchasePage.tsx、新增 external-repurchase.css、两端客资选择组件及卡牌样式/测试、API文档、本记录。Admin既有身份表单已横排，本轮同步卡牌折叠展示。验证类型与组件测试、实际页面隔离浏览器桌面/手机与交互。

复购页面压缩交付：去除720px窄列，身份三字段与校验按钮桌面横排，核验成功后摘要+修改。合并成功/选择提示，候选默认关键字段（姓名、编号、状态、提交时间、来源、负责人），原完整档案用原生details展开，展开不触发选中，保留卡牌光边与单选。候选自适应页面宽度，继续栏底部sticky显示选中编号。Admin同步卡牌摘要+完整档案折叠，维持既有对话框布局。
验证：Workbench typecheck与22项组件测试通过。Admin ts:check仍仅已有MES handleRefresh TS6133。ego-browser空间38基于实际页面+mock身份/候选API的隔离fixture验证默认details全部关闭、展开不改变选择、选中后继续启用、修改恢复表单并清空卡牌；1440宽输入框top均170、390宽单列且无横向溢出。桌面和手机截图已查看，底部操作栏可见。空间已finish。未做真实账号后端录单和Admin浏览器验收。git diff --check通过。所有权释放。

续办登记（默认全档案）：用户纠正卡牌不得增加展开操作，默认全部展示。local原工作区，领取两端选择组件、卡牌样式、既有卡牌测试、API文档、本记录。删除折叠和重复摘要，保留横排身份、全宽紧凑卡牌与底部操作栏。验证前端类型/组件及浏览器实际默认可见性。

默认全档案交付：移除两端details/summary、折叠样式及重复关键字段摘要，完整档案直接呈现；保留紧凑间距、全宽自适应卡牌和身份摘要/底部栏。新增既有卡牌测试断言无details/展开文案。Workbench类型检查及2项卡牌测试通过，Admin仍仅已有MES handleRefresh TS6133。隔离浏览器空间39实际组件验证2张卡各默认12字段、折叠容器0，空间已finish。未做真实账号订单提交。git diff --check通过。所有权释放。

续办登记（3与5候选测试）：沿用明确授权的开发库造数范围，local环境，现有main工作区。目标192.168.2.17:3306/ruoyi-vue-pro租户1，源KZ202610291544560042新增2条THREE测试客资，源KZ202610291428350032新增4条MANY测试客资，均closed。领取script/sql/mysql/fixtures/local-order-lead-cards-3-and-5.sql、本记录。验证候选3/5、待处理复购0、源行一致、重放0和中文HEX。恢复需按测试编号检查引用后定向清理。

3/5候选造数交付：脚本事务内新增6行并提交，重放新增0行。两客户分别3条、5条候选，待处理复购均0。源Lead全行前后一致，备注UTF-8 HEX校验通过。测试编号THREE01/02、MANY01..04，均KZTEST20261106前缀；原客资均won。未提交订单，保留给用户实际录单验证。git diff检查通过，所有权释放。
