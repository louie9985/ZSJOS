# 教材邮寄地址统计
登记 local-textbook-address，负责人 Codex，local，main，D:\ZSJ-OS，基线 21631a8a98。集成 None。
范围为 Admin 礼品采购页入口、教材统计组件与 API、ZSJOS 教材地址只读控制器/服务和测试、礼品采购 API 文档与本记录。依赖已生效订单管理范围、订单明细绑定任务的正式提交及修订。按订单分页并展开地址分组，不修改采购、订单或档案。验证服务回归、Admin 组件与浏览器。

交付：按订单管理数据范围与三项配置权限展示教材地址，生效订单整单分页、同单按正式报名地址全文/姓名/手机分组并展开商品和数量。未交或缺地址的明细保留待补。无 SQL、权限分配、提交或部署。
验证：后端模块编译与 TextbookAddressServiceTest、TextbookAddressPermissionTest 通过。Edge 隔离夹具 1440/390 像素验证入口、展开、待补、失败重试与权限隐藏。Admin 全量 ts:check 唯一报错在无关 MES 编辑页第 86 行未使用 handleRefresh。真实账号、线上数据量性能及发布待验。
