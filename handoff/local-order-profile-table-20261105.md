# 订单客户档案布局与客资提交时间
环境：local（环境变量及环境文件均未设置）。工作流：order-profile-table-20261105；owner：Codex。
分支：main；工作区：D:/ZSJ-OS；基线：216b75eaa84b7b757889be24e6a5483ed5a93593。
目标：客户档案复用订单信息表格，展示客资提交时间。
非目标：数据库修改、历史快照回填、部署、权限变更。集成分支/顺序：None。
所有权：SalesOrderDetailCards.tsx、services/api.ts、SalesOrderRespVO.java、SalesOrderServiceImpl.java、本记录。
依赖：现有 Lead createTime 与订单快照。验证：组件测试、TypeScript 检查、Java 编译、浏览器可用性检查。
契约：leadProfile.submittedAt 为提交订单时记录的 Lead createTime；旧快照缺失显示历史未记录。新增可选响应字段，Admin 可忽略，不改变已有字段。

交付：客户档案使用与下方相同的 InformationFields，保留复制按钮、来源标签与兼职归属；新增快照 submittedAt。11 项组件测试通过，npm run typecheck 通过，Maven 模块及依赖 compile 通过。未改数据库，未部署。浏览器仅完成连接与空间检查；当前空间由用户持有，未接管，桌面/手机实际视觉和真实账号接口仍未验证。Admin 对新增可选字段的实际接口兼容未验证。所有权释放。

续办登记：用户确认旧快照缺失时间时从关联 Lead.createTime 只读补充，替代此前缺失直接显示未记录的约定。环境仍为 local。所有权重新领取 SalesOrderServiceImpl.java、本记录，新增 SalesOrderLeadTimeCompatibilityTest.java。其余元数据与原登记一致。验证：针对快照优先、缺失补充、关联缺失的单元测试及 Maven 编译。

范围补充：领取 docs/api/zsjos-sales-order.md，按本次用户确认同步原始创建时间兼容例外。

续办交付：完成详情只读时间补充及 API 契约同步。Maven reactor 指定 SalesOrderLeadTimeCompatibilityTest、SalesOrderSnapshotTest 测试通过（包含模块编译）。未部署/重启，未执行数据库写入；真实账号接口验证未执行，线上生效需后端发布。所有权释放。
