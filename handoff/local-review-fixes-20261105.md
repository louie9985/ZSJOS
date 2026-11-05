# 今日审查修复

环境 local。工作流 review-fixes-20261105，owner Codex。分支 main，工作区 D:/ZSJ-OS，基线 216b75eaa84b7b757889be24e6a5483ed5a93593。集成 None。
目标为修复管理端响应丢失重试、关联候选 N+1、converted 状态展示。非目标为数据库、部署、权限变更及其他审查建议。
所有权为 ExternalRepurchaseDialog.vue、SalesOrderServiceImpl.java、PartnerOwnershipService.java、OrderLeadCandidateRespVO.java、OrderLeadSelectionModal.tsx、SalesOrderAssociationTest.java、docs/api/zsjos-sales-order.md、本记录及本任务隔离验证文件。原关联任务已交付释放所有权，保留原工作区改动。
依赖现有身份核验、幂等服务及兼职归属。验证为后端关联/订单回归、双端类型检查、管理端隔离重试测试及可用浏览器核查。

## 交付

三项修复完成。管理端对输入未变的传输失败保存并重放完整请求/幂等键；输入变化新建意图，明确业务错误清除请求缓存。候选批量读取用户、兼职和归属，员工不存在保留归属姓名快照，并兼容空来源用户。converted 按既有状态语义显示有效（双端）。
验证：Maven reactor 订单服务/关联/快照/客户核验共 120 项通过，含编译。新增批量调用次数与缺失员工姓名快照、converted 断言。Workbench typecheck 与 11 项详情组件测试通过。Admin ts:check 仅既有 MES task/edit/index.vue:86 handleRefresh 未使用，本次文件未报错。
管理端实际组件脚本隔离测试三场景通过，复现脚本 frontend/admin/tmp/review-fixes-20261105/retry.test.cjs。ego-browser 空间34在本任务 Vite 隔离页通过真实弹窗点击，候选/档案各查询一次，模拟响应丢失后两次创建请求完全相同，第二次成功；候选弹窗实际显示有效。模拟 API 非真实账号验收；未调用业务数据库。浏览器空间已完成关闭，本任务隔离服务停止。Ego Lite 提示可更新，本次未升级。
真实账号/运行后端验收与部署未执行。API 文档已同步。无 SQL、提交或推送。所有权释放。
